package com.invoice.modules.invoices.dto;

import com.invoice.modules.invoices.InvoiceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight invoice view for list endpoints — no line items.
 */
public record InvoiceListItemResponse(
    UUID id,
    String number,
    InvoiceStatus status,
    long totalPaise,
    LocalDate dueDate,
    LocalDate issueDate,
    UUID customerId,
    Instant createdAt
) {}
