package com.invoice.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
    @NotBlank String businessName,
    /**
     * Optional admin-chosen business code (what cashiers type at counter login).
     * 3–20 chars, letters/digits/hyphen. If omitted, one is generated.
     */
    @Pattern(regexp = "^$|^[A-Za-z0-9-]{3,20}$",
             message = "business code must be 3–20 letters, digits or hyphens")
    String businessCode
) {}
