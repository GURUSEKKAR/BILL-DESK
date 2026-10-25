package com.invoice;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Phase 3 (V4) migration landed correctly on a database that
 * already contains V1 + V2 data: every business gets a default C1 counter and
 * the invoice_number_seq is re-keyed to (business, counter, year).
 */
class MigrationV4Test extends Phase3BaseTest {

    @Test
    void everyBusinessGetsDefaultCounter() {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();

        @SuppressWarnings("unchecked")
        var counters = getAuth("/counters", owner, List.class).getBody();
        assertThat(counters).hasSize(1);
        @SuppressWarnings("unchecked")
        var c = (Map<String, Object>) counters.get(0);
        assertThat(c.get("code")).isEqualTo("C1");
        assertThat(c.get("name")).isEqualTo("Main");
        assertThat(c.get("active")).isEqualTo(true);
    }

    @Test
    void invoiceNumberSeqIsKeyedByCounter() {
        // Primary key columns of invoice_number_seq must be (business_id, counter_id, year)
        List<String> pkCols = jdbcTemplate.queryForList(
            """
            SELECT a.attname
            FROM pg_index i
            JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
            WHERE i.indrelid = 'invoice_number_seq'::regclass AND i.indisprimary
            ORDER BY a.attname
            """,
            String.class);
        assertThat(pkCols).containsExactlyInAnyOrder("business_id", "counter_id", "year");
    }

    @Test
    void newColumnsExistOnScopedTables() {
        assertThat(columnExists("invoices", "counter_id")).isTrue();
        assertThat(columnExists("invoices", "created_by")).isTrue();
        assertThat(columnExists("payments", "counter_id")).isTrue();
        assertThat(columnExists("payments", "received_by")).isTrue();
        assertThat(columnExists("audit_log", "counter_id")).isTrue();
        assertThat(columnExists("users", "role")).isTrue();
        assertThat(columnExists("users", "counter_id")).isTrue();
        assertThat(columnExists("users", "business_id")).isTrue();
        assertThat(columnExists("customers", "credit_limit_paise")).isTrue();
    }

    private boolean columnExists(String table, String column) {
        Integer n = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            Integer.class, table, column);
        return n != null && n > 0;
    }
}
