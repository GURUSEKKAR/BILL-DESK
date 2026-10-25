package com.invoice;

import com.invoice.modules.auth.dto.AuthResponse;
import com.invoice.modules.invoices.dto.InvoiceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mode-1 (counter) login: business login code + counter code + counter password.
 */
class CounterLoginTest extends Phase3BaseTest {

    private LocalDate due() { return LocalDate.now().plusDays(30); }

    /** Register returns the business login code; owner is OWNER with no counter. */
    @Test
    void registerReturnsLoginCodeAndOwnerRole() {
        var owner = registerBusiness("Shop", "owner@shop.test");
        assertThat(owner.role()).isEqualTo("OWNER");
        assertThat(owner.counterId()).isNull();
        assertThat(owner.businessLoginCode()).isNotBlank();
    }

    /** An admin-chosen business code is honoured (normalized to uppercase). */
    @Test
    void registerWithCustomBusinessCode() {
        var body = Map.of(
            "businessName", "My Shop", "email", "custom@shop.test",
            "password", "password123", "businessCode", "myshop1");
        var resp = post("/auth/register", body, com.invoice.modules.auth.dto.AuthResponse.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().businessLoginCode()).isEqualTo("MYSHOP1");

        // Counter login then works with that code (case-insensitive).
        String ownerToken = resp.getBody().accessToken();
        createCounterWithPassword(ownerToken, "Front", "C2", "till-pass-1");
        var session = post("/auth/counter-login", Map.of(
            "businessLoginCode", "myshop1",   // lower-case still matches
            "counterCode", "C2",
            "counterPassword", "till-pass-1"), com.invoice.modules.auth.dto.AuthResponse.class);
        assertThat(session.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(session.getBody().role()).isEqualTo("CASHIER");
    }

    /** A duplicate business code is rejected with 409. */
    @Test
    void duplicateBusinessCodeRejected() {
        post("/auth/register", Map.of(
            "businessName", "A", "email", "a@shop.test",
            "password", "password123", "businessCode", "DUP123"),
            com.invoice.modules.auth.dto.AuthResponse.class);
        var second = post("/auth/register", Map.of(
            "businessName", "B", "email", "b@shop.test",
            "password", "password123", "businessCode", "dup123"),
            com.invoice.modules.auth.dto.AuthResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Owner can change the business code; the new code works for counter login. */
    @Test
    void ownerCanChangeBusinessCode() {
        var owner = registerBusiness("Shop", "owner@shop.test");
        String token = owner.accessToken();
        createCounterWithPassword(token, "Front", "C2", "till-pass-1");

        var patch = patchAuth("/business", token, Map.of("loginCode", "NEWCODE"), Map.class);
        assertThat(patch.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(patch.getBody().get("loginCode")).isEqualTo("NEWCODE");

        // Old code no longer works, new code does.
        assertThat(post("/auth/counter-login", Map.of(
            "businessLoginCode", owner.businessLoginCode(),
            "counterCode", "C2", "counterPassword", "till-pass-1"),
            com.invoice.modules.auth.dto.AuthResponse.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/auth/counter-login", Map.of(
            "businessLoginCode", "NEWCODE",
            "counterCode", "C2", "counterPassword", "till-pass-1"),
            com.invoice.modules.auth.dto.AuthResponse.class).getStatusCode().is2xxSuccessful())
            .isTrue();
    }

    /** A cashier cannot read or change business settings. */
    @Test
    void cashierCannotAccessBusinessSettings() {
        var owner = registerBusiness("Shop", "owner@shop.test");
        String token = owner.accessToken();
        UUID counterId = createCounterWithPassword(token, "Front", "C2", "till-pass-1");
        var session = post("/auth/counter-login", Map.of(
            "businessLoginCode", owner.businessLoginCode(),
            "counterCode", "C2", "counterPassword", "till-pass-1"),
            com.invoice.modules.auth.dto.AuthResponse.class).getBody();

        assertThat(getAuth("/business", session.accessToken(), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patchAuth("/business", session.accessToken(),
            Map.of("loginCode", "HACK01"), Map.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** Counter login with the right code + password yields a CASHIER session. */
    @Test
    void counterLoginSucceedsAndIsScoped() {
        var owner = registerBusiness("Shop", "owner@shop.test");
        String ownerToken = owner.accessToken();
        UUID counterId = createCounterWithPassword(ownerToken, "Front", "C2", "till-pass-1");

        var resp = post("/auth/counter-login", Map.of(
            "businessLoginCode", owner.businessLoginCode(),
            "counterCode", "C2",
            "counterPassword", "till-pass-1"), AuthResponse.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();

        var session = resp.getBody();
        assertThat(session.role()).isEqualTo("CASHIER");
        assertThat(session.counterId()).isEqualTo(counterId);
        assertThat(session.counterCode()).isEqualTo("C2");

        // The counter session can create an invoice on its own counter (no counterId in body).
        var cust = createCustomer(ownerToken, "Cust");
        var created = createInvoiceResp(session.accessToken(), cust.id(), null, 10000, due());
        assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(created.getBody().number()).startsWith("C2-");
    }

    @Test
    void counterLoginWrongPasswordIs401() {
        var owner = registerBusiness("Shop", "owner@shop.test");
        createCounterWithPassword(owner.accessToken(), "Front", "C2", "till-pass-1");

        var resp = post("/auth/counter-login", Map.of(
            "businessLoginCode", owner.businessLoginCode(),
            "counterCode", "C2",
            "counterPassword", "wrong"), AuthResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void counterLoginWithoutPasswordSetIsRejected() {
        var owner = registerBusiness("Shop", "owner@shop.test");
        // Default C1 counter has no password set.
        var resp = post("/auth/counter-login", Map.of(
            "businessLoginCode", owner.businessLoginCode(),
            "counterCode", "C1",
            "counterPassword", "anything"), AuthResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void counterLoginUnknownBusinessCodeIs401() {
        registerBusiness("Shop", "owner@shop.test");
        var resp = post("/auth/counter-login", Map.of(
            "businessLoginCode", "ZZZZZZ",
            "counterCode", "C1",
            "counterPassword", "x"), AuthResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Deactivating a counter kills its login and existing counter sessions. */
    @Test
    void deactivatedCounterLoginIsRejected() {
        var owner = registerBusiness("Shop", "owner@shop.test");
        String ownerToken = owner.accessToken();
        UUID counterId = createCounterWithPassword(ownerToken, "Front", "C2", "till-pass-1");

        // Log in first — works.
        var session = post("/auth/counter-login", Map.of(
            "businessLoginCode", owner.businessLoginCode(),
            "counterCode", "C2",
            "counterPassword", "till-pass-1"), AuthResponse.class).getBody();
        var cust = createCustomer(ownerToken, "Cust");
        assertThat(createInvoiceResp(session.accessToken(), cust.id(), null, 10000, due())
            .getStatusCode().is2xxSuccessful()).isTrue();

        // Owner deactivates the counter.
        patchAuth("/counters/" + counterId, ownerToken, Map.of("active", false), Map.class);

        // New counter login rejected...
        assertThat(post("/auth/counter-login", Map.of(
            "businessLoginCode", owner.businessLoginCode(),
            "counterCode", "C2",
            "counterPassword", "till-pass-1"), AuthResponse.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        // ...and the old counter session loses write access (counter inactive).
        assertThat(createInvoiceResp(session.accessToken(), cust.id(), null, 10000, due())
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("unchecked")
    private UUID createCounterWithPassword(String ownerToken, String name, String code, String password) {
        var resp = postAuth("/counters", ownerToken,
            Map.of("name", name, "code", code, "password", password), Map.class);
        return UUID.fromString((String) resp.getBody().get("id"));
    }
}
