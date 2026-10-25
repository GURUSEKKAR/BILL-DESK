package com.invoice.modules.customers.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
    UUID id,
    String name,
    String email,
    String phone,
    Long creditLimitPaise,
    Instant createdAt
) {}
