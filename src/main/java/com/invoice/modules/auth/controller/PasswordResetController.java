package com.invoice.modules.auth.controller;

import com.invoice.modules.auth.dto.ForgotPasswordRequest;
import com.invoice.modules.auth.dto.ResetPasswordRequest;
import com.invoice.modules.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * POST /auth/forgot-password
     * Sends a password reset email. Always returns 200 (prevents user enumeration).
     */
    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.forgotPassword(request);
        return Map.of("message",
            "If that email is registered, a reset link has been sent.");
    }

    /**
     * POST /auth/reset-password
     * Validates token and updates the password.
     */
    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return Map.of("message", "Password updated successfully. You can now sign in.");
    }
}
