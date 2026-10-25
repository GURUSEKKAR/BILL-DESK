package com.invoice.modules.payments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreatePaymentRequest(
    @Positive(message = "amount must be positive") long amountPaise,
    @NotBlank String method
) {}
