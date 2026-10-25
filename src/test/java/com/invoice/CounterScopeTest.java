package com.invoice;

import com.invoice.modules.invoices.dto.InvoiceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 scope & isolation suite — the real deliverable.
 * Every endpoint is probed as OWNER, CASHIER (own counter),
 * CASHIER (other counter), and other-business user.
 */
class CounterScopeTest extends Phase3BaseTest {

    private LocalDate due() { return LocalDate.now().plusDays(30); }

    // ── Cashier A requests Counter B's invoice -> 404 ───────────────────────
    @Test
    void cashierCannotReadOtherCounterInvoice() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);              // C1 (default)
        UUID cB = createCounter(owner, "Second", "C2");
        String cashierA = createCashierToken(owner, "a@shop.test", cA);
        String cashierB = createCashierToken(owner, "b@shop.test", cB);
        var cust = createCustomer(owner, "Cust");

        // Cashier B creates an invoice on counter B (counter from their scope)
        var invB = createInvoice(cashierB, cust.id(), null, 10000, due());

        // Cashier A tries to read it -> 404 (scoped out)
        var resp = getAuth("/invoices/" + invB.id(), cashierA, InvoiceResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Cashier passes ?counterId=<other> -> ignored, own data returned ─────
    @Test
    void cashierCounterParamIsIgnored() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        UUID cB = createCounter(owner, "Second", "C2");
        String cashierA = createCashierToken(owner, "a@shop.test", cA);
        var cust = createCustomer(owner, "Cust");

        // One invoice on A, one on B (owner creates B's)
        createInvoice(cashierA, cust.id(), null, 10000, due());
        createInvoice(owner, cust.id(), cB, 20000, due());

        // Cashier A lists with ?counterId=cB -> param ignored, only A's invoice
        @SuppressWarnings("unchecked")
        var page = getAuth("/invoices?counterId=" + cB, cashierA, Map.class).getBody();
        var items = (java.util.List<?>) page.get("items");
        assertThat(items).hasSize(1); // only counter A's invoice
    }

    // ── Cashier calls owner-only endpoints -> 403 ───────────────────────────
    @Test
    void cashierForbiddenFromOwnerEndpoints() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        String cashier = createCashierToken(owner, "a@shop.test", cA);

        assertThat(getAuth("/dashboard/by-counter", cashier, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postAuth("/counters", cashier, Map.of("name", "X", "code", "C9"), Map.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(postAuth("/users", cashier,
            Map.of("email", "x@shop.test", "password", "password123", "counterId", cA.toString()),
            Map.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── Owner sees all counters, and filtering by one counter works ─────────
    @Test
    void ownerSeesAllAndCanFilter() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        UUID cB = createCounter(owner, "Second", "C2");
        var cust = createCustomer(owner, "Cust");

        createInvoice(owner, cust.id(), cA, 10000, due());
        createInvoice(owner, cust.id(), cB, 20000, due());

        @SuppressWarnings("unchecked")
        var all = getAuth("/invoices", owner, Map.class).getBody();
        assertThat((java.util.List<?>) all.get("items")).hasSize(2);

        @SuppressWarnings("unchecked")
        var onlyB = getAuth("/invoices?counterId=" + cB, owner, Map.class).getBody();
        assertThat((java.util.List<?>) onlyB.get("items")).hasSize(1);
    }

    // ── Owner passes a counterId from another business -> 404 ───────────────
    @Test
    void ownerForeignCounterFilterIs404() {
        var ownerA = registerBusiness("ShopA", "a@shop.test").accessToken();
        var ownerB = registerBusiness("ShopB", "b@shop.test").accessToken();
        UUID bCounter = defaultCounterId(ownerB);

        var resp = getAuth("/invoices?counterId=" + bCounter, ownerA, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Business A's owner vs Business B's counter (create) -> 404 ──────────
    @Test
    void ownerCannotCreateOnForeignCounter() {
        var ownerA = registerBusiness("ShopA", "a@shop.test").accessToken();
        var ownerB = registerBusiness("ShopB", "b@shop.test").accessToken();
        UUID bCounter = defaultCounterId(ownerB);
        var custA = createCustomer(ownerA, "Cust");

        var resp = createInvoiceResp(ownerA, custA.id(), bCounter, 10000, due());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Owner creating an invoice without counterId -> 400 ──────────────────
    @Test
    void ownerMustSpecifyCounter() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        var cust = createCustomer(owner, "Cust");
        var resp = createInvoiceResp(owner, cust.id(), null, 10000, due());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Cashier reads customer balance: total across counters, no foreign invoices
    @Test
    void cashierReadsCustomerBalanceAcrossCounters() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        UUID cB = createCounter(owner, "Second", "C2");
        String cashierA = createCashierToken(owner, "a@shop.test", cA);
        var cust = createCustomer(owner, "Cust");

        // A bills 10000 (counter A), B bills 20000 (counter B), both SENT
        var invA = createInvoice(cashierA, cust.id(), null, 10000, due());
        sendInvoiceResp(cashierA, invA.id());
        var invB = createInvoice(owner, cust.id(), cB, 20000, due());
        sendInvoiceResp(owner, invB.id());

        // Cashier A's balance view shows business-wide outstanding (30000)...
        @SuppressWarnings("unchecked")
        var balance = getAuth("/customers/" + cust.id() + "/balance", cashierA, Map.class).getBody();
        assertThat(((Number) balance.get("outstandingPaise")).longValue()).isEqualTo(30000);

        // ...but cashier A still cannot read counter B's invoice
        assertThat(getAuth("/invoices/" + invB.id(), cashierA, InvoiceResponse.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── Deactivated/reassigned cashier: old token loses access on sensitive calls
    @Test
    void deactivatedCashierLosesAccessOnSensitiveCalls() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        String cashier = createCashierToken(owner, "a@shop.test", cA);
        var cust = createCustomer(owner, "Cust");

        // Works before deactivation
        var before = createInvoiceResp(cashier, cust.id(), null, 10000, due());
        assertThat(before.getStatusCode().is2xxSuccessful()).isTrue();

        // Owner deactivates the cashier
        UUID cashierId = findUserId(owner, "a@shop.test");
        var patch = patchAuth("/users/" + cashierId, owner, Map.of("active", false), Map.class);
        assertThat(patch.getStatusCode().is2xxSuccessful()).isTrue();

        // Old token can no longer create invoices (re-loaded user is inactive -> 401)
        var after = createInvoiceResp(cashier, cust.id(), null, 10000, due());
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("unchecked")
    private UUID findUserId(String ownerToken, String email) {
        var users = getAuth("/users", ownerToken, java.util.List.class).getBody();
        for (Object o : users) {
            var m = (Map<String, Object>) o;
            if (email.equals(m.get("email"))) return UUID.fromString((String) m.get("id"));
        }
        throw new IllegalStateException("user not found: " + email);
    }
}
