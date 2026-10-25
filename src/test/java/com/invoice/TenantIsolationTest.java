package com.invoice;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that two businesses cannot see each other's data.
 * This is the #1 "Done when" criterion for Phase 1.
 */
class TenantIsolationTest extends BaseIntegrationTest {

    @Test
    void businessBCannotReadBusinessACustomer() {
        var bizA = registerBusiness("Business A", "a@tenant.test");
        var bizB = registerBusiness("Business B", "b@tenant.test");

        var customerA = createCustomer(bizA.accessToken(), "Alice");

        // B tries to read A's customer -> 404, not 200
        var response = getAuth("/customers/" + customerA.id(), bizB.accessToken(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void businessBCannotReadBusinessAInvoice() {
        var bizA = registerBusiness("Business A", "a@tenant.test");
        var bizB = registerBusiness("Business B", "b@tenant.test");

        var customer = createCustomer(bizA.accessToken(), "Alice");
        var invoice = createInvoice(bizA.accessToken(), customer.id(),
            "Consulting", 1, 10000, LocalDate.now().plusDays(30));

        var response = getAuth("/invoices/" + invoice.id(), bizB.accessToken(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void businessBCannotCreateInvoiceForBusinessACustomer() {
        var bizA = registerBusiness("Business A", "a@tenant.test");
        var bizB = registerBusiness("Business B", "b@tenant.test");

        var customerA = createCustomer(bizA.accessToken(), "Alice");

        // B tries to create an invoice referencing A's customer -> should fail
        var body = new HashMap<>(Map.of(
            "dueDate", LocalDate.now().plusDays(30).toString(),
            "items", List.of(Map.of(
                "description", "Hacking", "qty", 1, "unitPricePaise", 99999))));
        body.put("customerId", customerA.id().toString());
        body.put("counterId", defaultCounterId(bizB.accessToken()).toString());

        var response = postAuth("/invoices", bizB.accessToken(), body, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void businessBCannotPayBusinessAInvoice() {
        var bizA = registerBusiness("Business A", "a@tenant.test");
        var bizB = registerBusiness("Business B", "b@tenant.test");

        var customer = createCustomer(bizA.accessToken(), "Alice");
        var invoice = createInvoice(bizA.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().plusDays(30));
        sendInvoice(bizA.accessToken(), invoice.id());

        var response = markAsPaid(bizB.accessToken(), invoice.id(),
            50000, "BANK_TRANSFER", "cross-tenant-key");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void businessesSeeOnlyOwnDataInDashboard() {
        var bizA = registerBusiness("Business A", "a@tenant.test");
        var bizB = registerBusiness("Business B", "b@tenant.test");

        var customerA = createCustomer(bizA.accessToken(), "Alice");
        createInvoice(bizA.accessToken(), customerA.id(),
            "Work", 1, 100000, LocalDate.now().plusDays(30));

        // B's dashboard should show zero — no invoices in B's business
        var dashB = getDashboard(bizB.accessToken());
        assertThat(dashB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dashB.getBody().totalOutstandingPaise()).isZero();
        assertThat(dashB.getBody().invoiceCount()).isZero();
    }
}
