package com.invoice.modules.invoices.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record InvoiceItemRequest(
    @NotBlank String description,
    @Positive(message = "qty must be at least 1") int qty,
    @PositiveOrZero(message = "unit price must be non-negative") long unitPricePaise
) {}
