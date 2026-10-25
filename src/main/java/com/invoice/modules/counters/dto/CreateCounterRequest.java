package com.invoice.modules.counters.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCounterRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 16) String code,
    /** Optional counter login password. If set, enables Mode-1 counter login. */
    @Size(min = 4, max = 100) String password
) {}
