package com.invoice.modules.invoices.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateInvoiceRequest(
    @NotNull UUID customerId,
    @NotNull LocalDate dueDate,
    /**
     * Target counter. Required when an OWNER creates an invoice; ignored for a
     * CASHIER (whose counter is fixed by their assignment).
     */
    UUID counterId,
    @NotEmpty(message = "invoice must have at least one item")
    @Valid
    List<InvoiceItemRequest> items
) {}
