package com.invoice.modules.users.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String role,
    UUID counterId,
    boolean active,
    Instant createdAt
) {}
