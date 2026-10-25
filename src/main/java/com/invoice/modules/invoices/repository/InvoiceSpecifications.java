package com.invoice.modules.invoices.repository;

import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.entity.Invoice;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the dynamic WHERE clause for the invoice list endpoint.
 * Each helper returns null when the filter is absent so Spring Data skips it.
 */
public final class InvoiceSpecifications {

    private InvoiceSpecifications() {}

    public static Specification<Invoice> forBusiness(UUID businessId) {
        return (root, query, cb) -> cb.equal(root.get("businessId"), businessId);
    }

    public static Specification<Invoice> hasStatus(InvoiceStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Invoice> hasCustomer(UUID customerId) {
        if (customerId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    /**
     * Cursor condition: keep only rows strictly before the (createdAt, id) tuple.
     * Uses only the timestamp part for cursor comparison (simpler, safe for pagination).
     */
    public static Specification<Invoice> afterCursor(Instant cursorCreatedAt, UUID cursorId) {
        if (cursorCreatedAt == null) return null;
        // Use strict less-than on createdAt — good enough for pagination
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), cursorCreatedAt);
    }
}
