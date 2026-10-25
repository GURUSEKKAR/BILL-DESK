package com.invoice.modules.invoices.controller;

import com.invoice.common.PageResult;
import com.invoice.modules.invoices.InvoiceStatus;
import com.invoice.modules.invoices.dto.*;
import com.invoice.modules.invoices.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @PostMapping
    public InvoiceResponse create(@Valid @RequestBody CreateInvoiceRequest request) {
        return invoiceService.create(request);
    }

    @GetMapping
    public PageResult<InvoiceListItemResponse> list(
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID counterId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return invoiceService.list(status, customerId, counterId, cursor, limit);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable UUID id) {
        return invoiceService.getById(id);
    }

    /** DRAFT → SENT. Also schedules reminders and enqueues the invoice email. */
    @PostMapping("/{id}/send")
    public InvoiceResponse send(@PathVariable UUID id) {
        return invoiceService.send(id);
    }

    /** DRAFT/SENT/OVERDUE → CANCELLED. */
    @PostMapping("/{id}/cancel")
    public InvoiceResponse cancel(@PathVariable UUID id) {
        return invoiceService.cancel(id);
    }
}
