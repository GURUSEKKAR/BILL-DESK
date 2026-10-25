package com.invoice;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the invoice state machine rejects illegal transitions.
 * This is the #2 "Done when" criterion for Phase 1.
 */
class InvoiceStateMachineTest extends BaseIntegrationTest {

    @Test
    void paidToSentIsRejected() {
        var biz = registerBusiness("Test Co", "sm@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");
        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().plusDays(30));

        sendInvoice(biz.accessToken(), invoice.id());
        markAsPaid(biz.accessToken(), invoice.id(), 50000, "BANK_TRANSFER", "k1");

        // PAID -> SENT is illegal
        var response = sendInvoiceResponse(biz.accessToken(), invoice.id());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void draftToPaidIsRejected() {
        var biz = registerBusiness("Test Co", "sm@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");
        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().plusDays(30));

        // DRAFT -> PAID is illegal (must go through SENT first)
        var response = markAsPaid(biz.accessToken(), invoice.id(),
            50000, "BANK_TRANSFER", "k1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelledToSentIsRejected() {
        var biz = registerBusiness("Test Co", "sm@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");
        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().plusDays(30));

        // DRAFT -> CANCELLED is legal
        var cancelResp = cancelInvoiceResponse(biz.accessToken(), invoice.id());
        assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // CANCELLED -> SENT is illegal
        var sendResp = sendInvoiceResponse(biz.accessToken(), invoice.id());
        assertThat(sendResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void overdueToPaidIsAllowed() {
        var biz = registerBusiness("Test Co", "sm@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");
        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().minusDays(5));

        sendInvoice(biz.accessToken(), invoice.id());
        runOverdue(biz.accessToken()); // SENT -> OVERDUE

        // OVERDUE -> PAID is legal
        var response = markAsPaid(biz.accessToken(), invoice.id(),
            50000, "BANK_TRANSFER", "k1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void draftToSentSucceeds() {
        var biz = registerBusiness("Test Co", "sm@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");
        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().plusDays(30));

        var response = sendInvoiceResponse(biz.accessToken(), invoice.id());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).hasToString("SENT");
    }
}
