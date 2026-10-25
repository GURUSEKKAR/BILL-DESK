package com.invoice.modules.payments.controller;

import com.invoice.modules.payments.dto.CreatePaymentRequest;
import com.invoice.modules.payments.dto.PaymentResponse;
import com.invoice.modules.payments.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/invoices")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Records a payment and flips the invoice to PAID in one transaction.
     * Send an {@code Idempotency-Key} header to prevent double payment.
     */
    @PostMapping("/{id}/payments")
    public PaymentResponse markAsPaid(
            @PathVariable("id") UUID invoiceId,
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return paymentService.markAsPaid(invoiceId, request, idempotencyKey);
    }
}
