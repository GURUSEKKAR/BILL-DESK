package com.invoice;

import com.invoice.modules.auth.dto.AuthResponse;
import com.invoice.modules.customers.dto.CustomerResponse;
import com.invoice.modules.invoices.dto.InvoiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for Phase 3 (multi-counter) integration tests.
 * Worker/scheduler disabled; tests drive HTTP endpoints directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "app.worker.enabled=false",
    "app.reminder.poll-interval-seconds=999999",
    "spring.mail.host=localhost",
    "spring.mail.port=3025",
    "spring.mail.username=",
    "spring.mail.password=",
    "spring.mail.properties.mail.smtp.auth=false",
    "spring.mail.properties.mail.smtp.starttls.enable=false",
})
public abstract class Phase3BaseTest {

    @LocalServerPort protected int port;
    @Autowired protected JdbcTemplate jdbcTemplate;

    protected RestClient restClient;

    @BeforeEach
    void setup() {
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
        jdbcTemplate.execute(
            "TRUNCATE TABLE audit_log, payment_links, jobs, reminders, payments, " +
            "invoice_items, invoices, customers, invoice_number_seq, counters, businesses, users " +
            "RESTART IDENTITY CASCADE");
    }

    // ---- HTTP helpers ----

    protected <T> ResponseEntity<T> post(String uri, Object body, Class<T> type) {
        try {
            return restClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> postAuth(String uri, String token, Object body, Class<T> type) {
        try {
            return restClient.post().uri(uri).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> postAuthWithKey(String uri, String token, Object body,
                                                    Class<T> type, String key) {
        try {
            var spec = restClient.post().uri(uri).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
            if (key != null) spec.header("Idempotency-Key", key);
            return spec.body(body).retrieve().toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> postAuthNoBody(String uri, String token, Class<T> type) {
        try {
            return restClient.post().uri(uri).header("Authorization", "Bearer " + token)
                .retrieve().toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> getAuth(String uri, String token, Class<T> type) {
        try {
            return restClient.get().uri(uri).header("Authorization", "Bearer " + token)
                .retrieve().toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> patchAuth(String uri, String token, Object body, Class<T> type) {
        try {
            return restClient.patch().uri(uri).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    // ---- Domain helpers ----

    /** Register a business; returns the OWNER's auth response. */
    protected AuthResponse registerBusiness(String name, String email) {
        var body = Map.of("businessName", name, "email", email, "password", "password123");
        return post("/auth/register", body, AuthResponse.class).getBody();
    }

    @SuppressWarnings("unchecked")
    protected UUID defaultCounterId(String ownerToken) {
        var counters = getAuth("/counters", ownerToken, List.class).getBody();
        return UUID.fromString((String) ((Map<String, Object>) counters.get(0)).get("id"));
    }

    /** OWNER creates a counter, returns its id. */
    @SuppressWarnings("unchecked")
    protected UUID createCounter(String ownerToken, String name, String code) {
        var resp = postAuth("/counters", ownerToken, Map.of("name", name, "code", code), Map.class);
        return UUID.fromString((String) resp.getBody().get("id"));
    }

    /** OWNER creates a cashier pinned to a counter; returns a login token for them. */
    protected String createCashierToken(String ownerToken, String email, UUID counterId) {
        postAuth("/users", ownerToken,
            Map.of("email", email, "password", "password123", "counterId", counterId.toString()),
            Map.class);
        var login = post("/auth/login", Map.of("email", email, "password", "password123"),
            AuthResponse.class);
        return login.getBody().accessToken();
    }

    protected CustomerResponse createCustomer(String token, String name) {
        return postAuth("/customers", token, Map.of("name", name), CustomerResponse.class).getBody();
    }

    protected CustomerResponse createCustomerWithCredit(String token, String name, long creditLimitPaise) {
        var body = new HashMap<String, Object>();
        body.put("name", name);
        body.put("creditLimitPaise", creditLimitPaise);
        return postAuth("/customers", token, body, CustomerResponse.class).getBody();
    }

    /** Create an invoice. counterId may be null (cashier) or explicit (owner). */
    protected ResponseEntity<InvoiceResponse> createInvoiceResp(String token, UUID customerId,
                                                                UUID counterId, long unitPricePaise,
                                                                LocalDate dueDate) {
        var body = new HashMap<String, Object>();
        body.put("customerId", customerId.toString());
        body.put("dueDate", dueDate.toString());
        if (counterId != null) body.put("counterId", counterId.toString());
        body.put("items", List.of(Map.of("description", "Work", "qty", 1, "unitPricePaise", unitPricePaise)));
        return postAuth("/invoices", token, body, InvoiceResponse.class);
    }

    protected InvoiceResponse createInvoice(String token, UUID customerId, UUID counterId,
                                            long unitPricePaise, LocalDate dueDate) {
        return createInvoiceResp(token, customerId, counterId, unitPricePaise, dueDate).getBody();
    }

    protected ResponseEntity<InvoiceResponse> sendInvoiceResp(String token, UUID invoiceId) {
        return postAuthNoBody("/invoices/" + invoiceId + "/send", token, InvoiceResponse.class);
    }
}
