package com.invoice.modules.auth.dto;

import java.util.UUID;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    UUID businessId,
    String businessName,
    String businessLoginCode,
    String role,
    UUID counterId,
    String counterCode
) {}
