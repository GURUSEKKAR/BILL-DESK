package com.invoice.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Mode 1 — counter login. A till signs in with the business login code, the
 * counter's code, and that counter's own password (distinct from the admin's).
 */
public record CounterLoginRequest(
    @NotBlank String businessLoginCode,
    @NotBlank String counterCode,
    @NotBlank String counterPassword
) {}
