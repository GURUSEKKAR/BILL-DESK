package com.invoice;

import com.invoice.modules.invoices.InvoiceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the overdue detection job moves SENT invoices past their due date
 * to OVERDUE.
 */
class OverdueJobTest extends BaseIntegrationTest {

    @Test
    @SuppressWarnings("rawtypes")
    void overdueJobMarksPastDueSentInvoices() {
        var biz = registerBusiness("Test Co", "overdue@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");

        // Past due date
        var overdue = createInvoice(biz.accessToken(), customer.id(),
            "Old invoice", 1, 50000, LocalDate.now().minusDays(5));
        sendInvoice(biz.accessToken(), overdue.id());

        // Future due date — should NOT be marked overdue
        var future = createInvoice(biz.accessToken(), customer.id(),
            "Future invoice", 1, 30000, LocalDate.now().plusDays(10));
        sendInvoice(biz.accessToken(), future.id());

        // DRAFT invoice with past due date — should NOT be marked overdue
        var draft = createInvoice(biz.accessToken(), customer.id(),
            "Draft invoice", 1, 20000, LocalDate.now().minusDays(3));

        var response = runOverdue(biz.accessToken());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("markedOverdue")).isEqualTo(1);

        // Verify statuses
        assertThat(getInvoiceResponse(biz.accessToken(), overdue.id()).getBody().status())
            .isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(getInvoiceResponse(biz.accessToken(), future.id()).getBody().status())
            .isEqualTo(InvoiceStatus.SENT);
        assertThat(getInvoiceResponse(biz.accessToken(), draft.id()).getBody().status())
            .isEqualTo(InvoiceStatus.DRAFT);
    }

    @Test
    void alreadyPaidInvoiceNotAffectedByOverdueJob() {
        var biz = registerBusiness("Test Co", "overdue2@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");

        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Paid early", 1, 40000, LocalDate.now().minusDays(5));
        sendInvoice(biz.accessToken(), invoice.id());
        markAsPaid(biz.accessToken(), invoice.id(), 40000, "CASH", "k1");

        runOverdue(biz.accessToken());

        // Should still be PAID, not OVERDUE
        assertThat(getInvoiceResponse(biz.accessToken(), invoice.id()).getBody().status())
            .isEqualTo(InvoiceStatus.PAID);
    }
}
