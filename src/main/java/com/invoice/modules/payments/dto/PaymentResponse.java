package com.invoice.modules.payments.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID invoiceId,
    long amountPaise,
    String method,
    Instant paidAt
) {}
