package com.invoice.modules.invoices;

/**
 * Invoice lifecycle states.
 *
 * <pre>
 * DRAFT ──→ SENT ──→ PAID
 *  │         │
 *  │         ├──→ OVERDUE ──→ PAID
 *  │         │
 *  └──→ CANCELLED  ←── (from SENT, OVERDUE)
 *
 * PAID and CANCELLED are terminal.
 * </pre>
 */
public enum InvoiceStatus {
    DRAFT,
    SENT,
    PAID,
    OVERDUE,
    CANCELLED
}
