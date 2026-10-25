package com.invoice.modules.invoices.dto;

import com.invoice.modules.invoices.InvoiceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    String number,
    InvoiceStatus status,
    long totalPaise,
    LocalDate dueDate,
    LocalDate issueDate,
    UUID customerId,
    Instant createdAt,
    List<ItemResponse> items
) {
    public record ItemResponse(
        UUID id,
        String description,
        int qty,
        long unitPricePaise,
        long lineTotalPaise
    ) {}
}
