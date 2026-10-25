package com.invoice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Today" on the dashboard is an IST calendar day. Midnight IST == 18:30 UTC.
 * <p>
 * We fix the clock just after IST midnight (18:35 UTC on 2026-06-15, i.e.
 * 00:05 IST on 2026-06-16) and insert two payments: one at 18:25 UTC (still the
 * PREVIOUS IST day) and one at 18:40 UTC (the CURRENT IST day). Only the second
 * must count toward "collected today".
 */
@Import(DashboardIstBoundaryTest.FixedClockConfig.class)
class DashboardIstBoundaryTest extends Phase3BaseTest {

    /** 2026-06-15 18:35:00 UTC == 2026-06-16 00:05 IST. */
    static final Instant FIXED = Instant.parse("2026-06-15T18:35:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED, ZoneOffset.UTC);
        }
    }

    @Test
    void collectedTodayUsesIstDayBoundary() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID cA = defaultCounterId(owner);
        var cust = createCustomer(owner, "Cust");

        // A sent invoice we can attach payments to.
        var inv = createInvoice(owner, cust.id(), cA, 100000, LocalDate.now().plusDays(30));
        sendInvoiceResp(owner, inv.id());
        UUID invId = inv.id();
        UUID bizId = UUID.fromString(ownerBusinessId(owner));

        // Payment BEFORE IST midnight (18:25 UTC = 23:55 IST previous day) -> excluded
        insertPayment(invId, bizId, cA, 40000, "CASH", Instant.parse("2026-06-15T18:25:00Z"));
        // Payment AFTER IST midnight (18:40 UTC = 00:10 IST current day) -> included
        insertPayment(invId, bizId, cA, 25000, "UPI", Instant.parse("2026-06-15T18:40:00Z"));

        @SuppressWarnings("unchecked")
        var summary = getAuth("/dashboard/summary", owner, Map.class).getBody();
        long collectedToday = ((Number) summary.get("collectedTodayPaise")).longValue();

        // Only the 25000 paid after IST midnight counts.
        assertThat(collectedToday).isEqualTo(25000);

        @SuppressWarnings("unchecked")
        var byMethod = (Map<String, Object>) summary.get("collectedTodayByMethod");
        assertThat(byMethod).containsKey("UPI");
        assertThat(byMethod).doesNotContainKey("CASH");
    }

    @SuppressWarnings("unchecked")
    private String ownerBusinessId(String token) {
        var me = getAuth("/counters", token, java.util.List.class).getBody();
        // businessId isn't exposed on /counters; fetch from a fresh register response instead.
        // Simpler: query the DB for the single business.
        return jdbcTemplate.queryForObject("SELECT id::text FROM businesses LIMIT 1", String.class);
    }

    private void insertPayment(UUID invoiceId, UUID businessId, UUID counterId,
                               long amount, String method, Instant paidAt) {
        jdbcTemplate.update(
            "INSERT INTO payments (id, invoice_id, business_id, counter_id, amount_paise, method, paid_at) " +
            "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?)",
            invoiceId, businessId, counterId, amount, method, java.sql.Timestamp.from(paidAt));
    }
}
