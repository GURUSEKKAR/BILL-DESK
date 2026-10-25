package com.invoice.modules.counters.dto;

import jakarta.validation.constraints.Size;

/** All fields optional — partial update. */
public record UpdateCounterRequest(
    @Size(max = 100) String name,
    Boolean active,
    /** Set/rotate the counter login password. */
    @Size(min = 4, max = 100) String password
) {}
