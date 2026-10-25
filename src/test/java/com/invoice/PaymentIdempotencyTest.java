package com.invoice;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that marking an invoice paid twice (same idempotency key) does not
 * create a duplicate payment.
 * This is the #3 "Done when" criterion for Phase 1.
 */
class PaymentIdempotencyTest extends BaseIntegrationTest {

    @Test
    void sameIdempotencyKeyReturnsSamePayment() {
        var biz = registerBusiness("Test Co", "pay@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");
        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().plusDays(30));
        sendInvoice(biz.accessToken(), invoice.id());

        // First payment with key "dup-key"
        var first = markAsPaid(biz.accessToken(), invoice.id(),
            50000, "BANK_TRANSFER", "dup-key");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second call with the same key -> returns the same payment
        var second = markAsPaid(biz.accessToken(), invoice.id(),
            50000, "BANK_TRANSFER", "dup-key");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody().id()).isEqualTo(first.getBody().id());

        // Only one payment row in the database
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payments WHERE invoice_id = ?",
            Long.class, invoice.id());
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void noKeySecondAttemptIsRejected() {
        var biz = registerBusiness("Test Co", "pay@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");
        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().plusDays(30));
        sendInvoice(biz.accessToken(), invoice.id());

        // First payment — no idempotency key
        var first = markAsPaid(biz.accessToken(), invoice.id(),
            50000, "BANK_TRANSFER", null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second attempt without a key -> 409 (invoice is already PAID)
        var second = markAsPaid(biz.accessToken(), invoice.id(),
            50000, "BANK_TRANSFER", null);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Still only one payment
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payments WHERE invoice_id = ?",
            Long.class, invoice.id());
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void differentKeysOnSameInvoiceRejected() {
        var biz = registerBusiness("Test Co", "pay@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");
        var invoice = createInvoice(biz.accessToken(), customer.id(),
            "Consulting", 1, 50000, LocalDate.now().plusDays(30));
        sendInvoice(biz.accessToken(), invoice.id());

        // First payment with key "k1"
        var first = markAsPaid(biz.accessToken(), invoice.id(),
            50000, "BANK_TRANSFER", "k1");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second payment with a different key -> 409 (already paid)
        var second = markAsPaid(biz.accessToken(), invoice.id(),
            50000, "CASH", "k2");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
