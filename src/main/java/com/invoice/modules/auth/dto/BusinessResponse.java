package com.invoice.modules.auth.dto;

import java.util.UUID;

/** Owner-facing view of their business settings. */
public record BusinessResponse(
    UUID id,
    String name,
    String loginCode
) {}
