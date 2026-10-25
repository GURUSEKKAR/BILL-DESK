package com.invoice;

import com.invoice.modules.invoices.dto.InvoiceResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Per-counter invoice numbering (incl. concurrency) and owner dashboard sums.
 */
class CounterNumberingAndDashboardTest extends Phase3BaseTest {

    private LocalDate due() { return LocalDate.now().plusDays(30); }

    // ── Per-counter numbers: C1-... and C2-... are independent sequences ────
    @Test
    void invoiceNumbersArePerCounter() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);              // C1
        UUID cB = createCounter(owner, "Second", "C2");
        var cust = createCustomer(owner, "Cust");

        var a1 = createInvoice(owner, cust.id(), cA, 10000, due());
        var b1 = createInvoice(owner, cust.id(), cB, 10000, due());
        var a2 = createInvoice(owner, cust.id(), cA, 10000, due());

        int year = LocalDate.now().getYear();
        assertThat(a1.number()).isEqualTo("C1-" + year + "-000001");
        assertThat(a2.number()).isEqualTo("C1-" + year + "-000002");
        assertThat(b1.number()).isEqualTo("C2-" + year + "-000001");
    }

    // ── 2 counters creating 50 invoices in parallel -> unique per-counter ───
    @Test
    void parallelInvoiceNumbersAreUniquePerCounter() throws Exception {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        UUID cB = createCounter(owner, "Second", "C2");
        var cust = createCustomer(owner, "Cust");

        int perCounter = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<String>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < perCounter; i++) {
            futures.add(pool.submit(() -> createInvoice(owner, cust.id(), cA, 10000, due()).number()));
            futures.add(pool.submit(() -> createInvoice(owner, cust.id(), cB, 10000, due()).number()));
        }
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        Set<String> numbers = new HashSet<>();
        for (Future<String> f : futures) numbers.add(f.get());

        // 100 invoices, all numbers unique
        assertThat(numbers).hasSize(perCounter * 2);
        long c1 = numbers.stream().filter(n -> n.startsWith("C1-")).count();
        long c2 = numbers.stream().filter(n -> n.startsWith("C2-")).count();
        assertThat(c1).isEqualTo(perCounter);
        assertThat(c2).isEqualTo(perCounter);
    }

    // ── Dashboard totals: sum of per-counter outstanding == owner's total ───
    @Test
    void dashboardByCounterSumsEqualBusinessTotal() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        UUID cB = createCounter(owner, "Second", "C2");
        var cust = createCustomer(owner, "Cust");

        // A: 10000 + 30000 SENT; B: 20000 SENT
        sendInvoiceResp(owner, createInvoice(owner, cust.id(), cA, 10000, due()).id());
        sendInvoiceResp(owner, createInvoice(owner, cust.id(), cA, 30000, due()).id());
        sendInvoiceResp(owner, createInvoice(owner, cust.id(), cB, 20000, due()).id());

        // Business-wide summary
        @SuppressWarnings("unchecked")
        var summary = getAuth("/dashboard/summary", owner, Map.class).getBody();
        long total = ((Number) summary.get("totalOutstandingPaise")).longValue();
        assertThat(total).isEqualTo(60000);

        // Per-counter breakdown
        @SuppressWarnings("unchecked")
        var byCounter = getAuth("/dashboard/by-counter", owner, Map.class).getBody();
        var rows = (List<Map<String, Object>>) byCounter.get("counters");
        long sumOfCounters = rows.stream()
            .mapToLong(r -> ((Number) r.get("outstandingPaise")).longValue()).sum();
        assertThat(sumOfCounters).isEqualTo(total);

        // Each counter's figure
        long aOut = rows.stream().filter(r -> r.get("counterCode").equals("C1"))
            .mapToLong(r -> ((Number) r.get("outstandingPaise")).longValue()).sum();
        long bOut = rows.stream().filter(r -> r.get("counterCode").equals("C2"))
            .mapToLong(r -> ((Number) r.get("outstandingPaise")).longValue()).sum();
        assertThat(aOut).isEqualTo(40000);
        assertThat(bOut).isEqualTo(20000);

        // Each counter box reports its distinct customer count (one customer here).
        rows.forEach(r ->
            assertThat(((Number) r.get("customerCount")).longValue()).isEqualTo(1));
    }

    // ── Per-counter customer count is distinct and counter-scoped ───────────
    @Test
    void perCounterCustomerCountIsDistinctAndScoped() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        UUID cB = createCounter(owner, "Second", "C2");
        var alice = createCustomer(owner, "Alice");
        var bob = createCustomer(owner, "Bob");

        // Counter A: Alice (2 invoices) + Bob -> 2 distinct customers
        createInvoice(owner, alice.id(), cA, 10000, due());
        createInvoice(owner, alice.id(), cA, 15000, due());
        createInvoice(owner, bob.id(), cA, 20000, due());
        // Counter B: only Alice -> 1 distinct customer
        createInvoice(owner, alice.id(), cB, 5000, due());

        @SuppressWarnings("unchecked")
        var byCounter = getAuth("/dashboard/by-counter", owner, Map.class).getBody();
        var rows = (List<Map<String, Object>>) byCounter.get("counters");
        long aCount = rows.stream().filter(r -> r.get("counterCode").equals("C1"))
            .mapToLong(r -> ((Number) r.get("customerCount")).longValue()).sum();
        long bCount = rows.stream().filter(r -> r.get("counterCode").equals("C2"))
            .mapToLong(r -> ((Number) r.get("customerCount")).longValue()).sum();
        assertThat(aCount).isEqualTo(2);
        assertThat(bCount).isEqualTo(1);
    }

    // ── Cashier dashboard limited to own counter ────────────────────────────
    @Test
    void cashierDashboardIsOwnCounterOnly() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        UUID cB = createCounter(owner, "Second", "C2");
        String cashierA = createCashierToken(owner, "a@shop.test", cA);
        var cust = createCustomer(owner, "Cust");

        var invA = createInvoice(cashierA, cust.id(), null, 10000, due());
        sendInvoiceResp(cashierA, invA.id());
        sendInvoiceResp(owner, createInvoice(owner, cust.id(), cB, 99999, due()).id());

        @SuppressWarnings("unchecked")
        var summary = getAuth("/dashboard/summary", cashierA, Map.class).getBody();
        // Only counter A's 10000 is visible to cashier A
        assertThat(((Number) summary.get("totalOutstandingPaise")).longValue()).isEqualTo(10000);
    }
}
