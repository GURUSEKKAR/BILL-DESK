package com.invoice;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the dashboard aggregation numbers match a manual calculation on
 * seed-like data.
 * This is the #4 "Done when" criterion for Phase 1.
 */
class DashboardAccuracyTest extends BaseIntegrationTest {

    @Test
    void dashboardMatchesManualCalculation() {
        var biz = registerBusiness("Test Co", "dash@test.com");
        var alice = createCustomer(biz.accessToken(), "Alice");
        var bob = createCustomer(biz.accessToken(), "Bob");

        // inv1: Alice, 500000 paise, SENT (outstanding)
        var inv1 = createInvoice(biz.accessToken(), alice.id(),
            "Consulting", 10, 50000, LocalDate.now().plusDays(30));
        sendInvoice(biz.accessToken(), inv1.id());

        // inv2: Bob, 150000 paise, SENT (outstanding)
        var inv2 = createInvoice(biz.accessToken(), bob.id(),
            "Design", 5, 30000, LocalDate.now().plusDays(15));
        sendInvoice(biz.accessToken(), inv2.id());

        // inv3: Alice, 1500000 paise, SENT then PAID
        var inv3 = createInvoice(biz.accessToken(), alice.id(),
            "Development", 20, 75000, LocalDate.now().plusDays(60));
        sendInvoice(biz.accessToken(), inv3.id());
        markAsPaid(biz.accessToken(), inv3.id(), 1500000, "BANK_TRANSFER", "k1");

        // inv4: Bob, 200000 paise, stays DRAFT (not counted in outstanding)
        createInvoice(biz.accessToken(), bob.id(),
            "Draft work", 2, 100000, LocalDate.now().plusDays(10));

        var dash = getDashboard(biz.accessToken());
        assertThat(dash.getStatusCode().is2xxSuccessful()).isTrue();
        var summary = dash.getBody();

        // Outstanding = inv1 (500000) + inv2 (150000) = 650000
        assertThat(summary.totalOutstandingPaise()).isEqualTo(650000);
        // Overdue = 0 (nothing past due date)
        assertThat(summary.totalOverduePaise()).isZero();
        // Paid = inv3 (1500000)
        assertThat(summary.totalPaidPaise()).isEqualTo(1500000);
        // Invoice count (outstanding) = 2
        assertThat(summary.invoiceCount()).isEqualTo(2);
        // Overdue count = 0
        assertThat(summary.overdueCount()).isZero();

        // Outstanding per customer
        var perCustomer = summary.outstandingPerCustomer();
        assertThat(perCustomer).hasSize(2);

        var aliceRow = perCustomer.stream()
            .filter(c -> c.customerId().equals(alice.id())).findFirst().orElseThrow();
        assertThat(aliceRow.customerName()).isEqualTo("Alice");
        assertThat(aliceRow.owedPaise()).isEqualTo(500000);

        var bobRow = perCustomer.stream()
            .filter(c -> c.customerId().equals(bob.id())).findFirst().orElseThrow();
        assertThat(bobRow.customerName()).isEqualTo("Bob");
        assertThat(bobRow.owedPaise()).isEqualTo(150000);
    }

    @Test
    void dashboardReflectsOverdueAfterJob() {
        var biz = registerBusiness("Test Co", "dash2@test.com");
        var customer = createCustomer(biz.accessToken(), "Alice");

        // Invoice with past due date -> will become OVERDUE
        var inv = createInvoice(biz.accessToken(), customer.id(),
            "Old work", 1, 100000, LocalDate.now().minusDays(10));
        sendInvoice(biz.accessToken(), inv.id());

        // Before overdue job: outstanding = 100000, overdue = 0
        var before = getDashboard(biz.accessToken()).getBody();
        assertThat(before.totalOutstandingPaise()).isEqualTo(100000);
        assertThat(before.totalOverduePaise()).isZero();

        runOverdue(biz.accessToken());

        // After overdue job: outstanding = 100000 (same), overdue = 100000
        var after = getDashboard(biz.accessToken()).getBody();
        assertThat(after.totalOutstandingPaise()).isEqualTo(100000);
        assertThat(after.totalOverduePaise()).isEqualTo(100000);
        assertThat(after.overdueCount()).isEqualTo(1);
    }
}
