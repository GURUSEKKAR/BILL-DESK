package com.invoice.modules.counters.dto;

import java.time.Instant;
import java.util.UUID;

public record CounterResponse(
    UUID id,
    String name,
    String code,
    boolean active,
    boolean loginEnabled,   // true once a counter password is set
    Instant createdAt
) {}
