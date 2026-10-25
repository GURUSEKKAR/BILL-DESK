package com.invoice.modules.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Owner updates business name and/or login code. All fields optional. */
public record UpdateBusinessRequest(
    @Size(min = 1, max = 100) String name,
    @Pattern(regexp = "^$|^[A-Za-z0-9-]{3,20}$",
             message = "business code must be 3–20 letters, digits or hyphens")
    String loginCode
) {}
