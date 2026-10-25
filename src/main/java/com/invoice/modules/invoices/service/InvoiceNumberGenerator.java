package com.invoice.modules.invoices.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Generates the next invoice number sequence value per business per year.
 * <p>
 * Uses a PostgreSQL {@code INSERT ... ON CONFLICT ... RETURNING} statement that
 * is atomic: two concurrent calls will never get the same number.
 */
@Repository
@RequiredArgsConstructor
public class InvoiceNumberGenerator {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @return the sequence value to use for the next invoice (1-based), unique
     *         per (business, counter, year).
     */
    public int nextNumber(UUID businessId, UUID counterId, int year) {
        return jdbcTemplate.queryForObject(
            """
            INSERT INTO invoice_number_seq (business_id, counter_id, year, next_val)
            VALUES (?, ?, ?, 1)
            ON CONFLICT (business_id, counter_id, year)
            DO UPDATE SET next_val = invoice_number_seq.next_val + 1
            RETURNING next_val
            """,
            Integer.class,
            businessId, counterId, year
        );
    }
}
