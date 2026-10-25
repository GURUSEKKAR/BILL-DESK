package com.invoice.modules.customers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateCustomerRequest(
    @NotBlank String name,
    @Email String email,
    String phone,
    @PositiveOrZero Long creditLimitPaise
) {}
