package com.invoice;

import com.invoice.modules.invoices.dto.InvoiceResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3b: credit-limit check with customer-row locking.
 */
class CreditLimitTest extends Phase3BaseTest {

    private LocalDate due() { return LocalDate.now().plusDays(30); }

    // ── Sending beyond the credit limit is rejected ─────────────────────────
    @Test
    void sendBeyondCreditLimitIsRejected() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        var cust = createCustomerWithCredit(owner, "Cust", 15000); // limit 15000

        // First 10000 invoice sends fine (outstanding 0 + 10000 <= 15000)
        var i1 = createInvoice(owner, cust.id(), cA, 10000, due());
        assertThat(sendInvoiceResp(owner, i1.id()).getStatusCode().is2xxSuccessful()).isTrue();

        // Second 10000 would make 20000 > 15000 -> rejected
        var i2 = createInvoice(owner, cust.id(), cA, 10000, due());
        assertThat(sendInvoiceResp(owner, i2.id()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ── 2 counters billing the same customer in parallel: only one passes ───
    @Test
    void parallelCreditBillingOnlyOnePasses() throws Exception {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        UUID cB = createCounter(owner, "Second", "C2");
        // Limit allows exactly one 10000 invoice.
        var cust = createCustomerWithCredit(owner, "Cust", 10000);

        var iA = createInvoice(owner, cust.id(), cA, 10000, due());
        var iB = createInvoice(owner, cust.id(), cB, 10000, due());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch gate = new CountDownLatch(1);
        Callable<Integer> sendA = () -> { gate.await(); return sendStatus(owner, iA.id()); };
        Callable<Integer> sendB = () -> { gate.await(); return sendStatus(owner, iB.id()); };
        Future<Integer> fA = pool.submit(sendA);
        Future<Integer> fB = pool.submit(sendB);
        gate.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        int a = fA.get(), b = fB.get();
        // Exactly one 2xx and one 409 (the customer-row lock serializes them).
        long ok = List.of(a, b).stream().filter(s -> s >= 200 && s < 300).count();
        long conflict = List.of(a, b).stream().filter(s -> s == 409).count();
        assertThat(ok).isEqualTo(1);
        assertThat(conflict).isEqualTo(1);
    }

    private int sendStatus(String token, UUID invoiceId) {
        ResponseEntity<InvoiceResponse> r = sendInvoiceResp(token, invoiceId);
        return r.getStatusCode().value();
    }
}
