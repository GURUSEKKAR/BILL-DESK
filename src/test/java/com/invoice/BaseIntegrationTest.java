package com.invoice;

import com.invoice.modules.auth.dto.AuthResponse;
import com.invoice.modules.customers.dto.CustomerResponse;
import com.invoice.modules.dashboard.dto.DashboardSummary;
import com.invoice.modules.invoices.dto.InvoiceResponse;
import com.invoice.modules.payments.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all integration tests. Boots the full Spring context against a
 * PostgreSQL instance (started via {@code docker compose up -d postgres}).
 * <p>
 * Tables are truncated before each test method for isolation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.mail.host=localhost",
    "spring.mail.port=3025",
    "spring.mail.username=",
    "spring.mail.password=",
    "spring.mail.properties.mail.smtp.auth=false",
    "spring.mail.properties.mail.smtp.starttls.enable=false",
    "app.worker.enabled=false",
})
public abstract class BaseIntegrationTest {

    @LocalServerPort protected int port;
    @Autowired protected JdbcTemplate jdbcTemplate;

    protected RestClient restClient;

    @BeforeEach
    void setup() {
        restClient = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
        jdbcTemplate.execute(
            "TRUNCATE TABLE audit_log, payment_links, jobs, reminders, payments, " +
            "invoice_items, invoices, customers, invoice_number_seq, counters, businesses, users " +
            "RESTART IDENTITY CASCADE");
    }

    // -----------------------------------------------------------------
    // Generic HTTP helpers (catch error responses instead of parsing them)
    // -----------------------------------------------------------------

    protected <T> ResponseEntity<T> post(String uri, Object body, Class<T> type) {
        try {
            return restClient.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> postAuth(String uri, String token, Object body, Class<T> type) {
        try {
            return restClient.post().uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> postAuthWithKey(String uri, String token, Object body,
                                                    Class<T> type, String idempotencyKey) {
        try {
            var spec = restClient.post().uri(uri)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON);
            if (idempotencyKey != null) {
                spec.header("Idempotency-Key", idempotencyKey);
            }
            return spec.body(body)
                .retrieve()
                .toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> postAuthNoBody(String uri, String token, Class<T> type) {
        try {
            return restClient.post().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    protected <T> ResponseEntity<T> getAuth(String uri, String token, Class<T> type) {
        try {
            return restClient.get().uri(uri)
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .toEntity(type);
        } catch (RestClientResponseException e) {
            return new ResponseEntity<>(e.getStatusCode());
        }
    }

    // -----------------------------------------------------------------
    // Auth helpers
    // -----------------------------------------------------------------

    protected AuthResponse registerBusiness(String name, String email) {
        var body = Map.of("businessName", name, "email", email, "password", "password123");
        return post("/auth/register", body, AuthResponse.class).getBody();
    }

    // -----------------------------------------------------------------
    // Customer helpers
    // -----------------------------------------------------------------

    protected CustomerResponse createCustomer(String token, String name) {
        var body = Map.of("name", name);
        return postAuth("/customers", token, body, CustomerResponse.class).getBody();
    }

    // -----------------------------------------------------------------
    // Invoice helpers
    // -----------------------------------------------------------------

    protected InvoiceResponse createInvoice(String token, UUID customerId,
                                             String description, int qty,
                                             long unitPricePaise, LocalDate dueDate) {
        var body = new java.util.HashMap<String, Object>();
        body.put("customerId", customerId.toString());
        body.put("dueDate", dueDate.toString());
        body.put("counterId", defaultCounterId(token).toString());
        body.put("items", List.of(Map.of(
            "description", description,
            "qty", qty,
            "unitPricePaise", unitPricePaise)));
        return postAuth("/invoices", token, body, InvoiceResponse.class).getBody();
    }

    /** The default "C1" counter for the owner behind this token. */
    @SuppressWarnings("unchecked")
    protected UUID defaultCounterId(String token) {
        var counters = getAuth("/counters", token, List.class).getBody();
        Map<String, Object> first = (Map<String, Object>) counters.get(0);
        return UUID.fromString((String) first.get("id"));
    }

    protected ResponseEntity<InvoiceResponse> getInvoiceResponse(String token, UUID invoiceId) {
        return getAuth("/invoices/" + invoiceId, token, InvoiceResponse.class);
    }

    protected InvoiceResponse sendInvoice(String token, UUID invoiceId) {
        return postAuthNoBody("/invoices/" + invoiceId + "/send", token, InvoiceResponse.class).getBody();
    }

    protected ResponseEntity<InvoiceResponse> sendInvoiceResponse(String token, UUID invoiceId) {
        return postAuthNoBody("/invoices/" + invoiceId + "/send", token, InvoiceResponse.class);
    }

    protected ResponseEntity<InvoiceResponse> cancelInvoiceResponse(String token, UUID invoiceId) {
        return postAuthNoBody("/invoices/" + invoiceId + "/cancel", token, InvoiceResponse.class);
    }

    // -----------------------------------------------------------------
    // Payment helpers
    // -----------------------------------------------------------------

    protected ResponseEntity<PaymentResponse> markAsPaid(String token, UUID invoiceId,
                                                         long amountPaise, String method,
                                                         String idempotencyKey) {
        var body = Map.of("amountPaise", amountPaise, "method", method);
        return postAuthWithKey("/invoices/" + invoiceId + "/payments", token, body,
            PaymentResponse.class, idempotencyKey);
    }

    // -----------------------------------------------------------------
    // Dashboard helpers
    // -----------------------------------------------------------------

    protected ResponseEntity<DashboardSummary> getDashboard(String token) {
        return getAuth("/dashboard/summary", token, DashboardSummary.class);
    }

    // -----------------------------------------------------------------
    // Admin helpers
    // -----------------------------------------------------------------

    @SuppressWarnings("rawtypes")
    protected ResponseEntity<Map> runOverdue(String token) {
        return postAuthNoBody("/admin/run-overdue", token, Map.class);
    }
}
