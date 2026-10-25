package com.invoice.modules.invoices;

import com.invoice.common.exception.ApiException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for which status transitions are legal.
 * Every transition in the system goes through {@link #validate} so the
 * rules can never be bypassed.
 */
public final class InvoiceStateMachine {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> ALLOWED = Map.of(
        InvoiceStatus.DRAFT,    EnumSet.of(InvoiceStatus.SENT, InvoiceStatus.CANCELLED),
        InvoiceStatus.SENT,     EnumSet.of(InvoiceStatus.PAID, InvoiceStatus.OVERDUE, InvoiceStatus.CANCELLED),
        InvoiceStatus.OVERDUE,  EnumSet.of(InvoiceStatus.PAID, InvoiceStatus.CANCELLED),
        InvoiceStatus.PAID,     EnumSet.noneOf(InvoiceStatus.class),
        InvoiceStatus.CANCELLED,EnumSet.noneOf(InvoiceStatus.class)
    );

    private InvoiceStateMachine() {}

    public static void validate(InvoiceStatus from, InvoiceStatus to) {
        Set<InvoiceStatus> allowed = ALLOWED.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw ApiException.conflict("Illegal transition: " + from + " → " + to);
        }
    }

    public static boolean isAllowed(InvoiceStatus from, InvoiceStatus to) {
        Set<InvoiceStatus> allowed = ALLOWED.get(from);
        return allowed != null && allowed.contains(to);
    }
}
