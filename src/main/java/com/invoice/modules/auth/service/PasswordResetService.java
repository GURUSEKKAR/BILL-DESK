package com.invoice.modules.auth.service;

import com.invoice.common.exception.ApiException;
import com.invoice.modules.auth.dto.ForgotPasswordRequest;
import com.invoice.modules.auth.dto.ResetPasswordRequest;
import com.invoice.modules.auth.entity.PasswordResetToken;
import com.invoice.modules.auth.entity.User;
import com.invoice.modules.auth.repository.PasswordResetTokenRepository;
import com.invoice.modules.auth.repository.UserRepository;
import com.invoice.modules.notifications.ResendEmailChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

/**
 * Handles the forgot-password / reset-password flow.
 *
 * Flow:
 * 1. User submits email → we generate a token, save it, email the link
 * 2. User clicks link → frontend shows reset form with the token
 * 3. User submits new password + token → we validate + update password
 *
 * Security:
 * - Token is 32 bytes of secure random (URL-safe base64)
 * - Expires in 1 hour
 * - One-time use (marked used_at after consumption)
 * - We always return 200 on /forgot-password even if email not found
 *   (prevents user enumeration)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResendEmailChannel emailChannel;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always return success — prevents email enumeration
        userRepository.findByEmail(request.email()).ifPresent(user -> {
            try {
                sendResetEmail(user);
            } catch (Exception e) {
                log.error("Failed to send password reset email to {}", request.email(), e);
            }
        });
    }

    private void sendResetEmail(User user) throws Exception {
        // Delete any existing tokens for this user
        tokenRepository.deleteByUserId(user.getId());

        // Generate secure token
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        PasswordResetToken prt = new PasswordResetToken();
        prt.setUserId(user.getId());
        prt.setToken(token);
        prt.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        tokenRepository.save(prt);

        String resetLink = baseUrl + "/reset-password?token=" + token;
        String subject = "Reset your InvoiceApp password";
        String body = """
            <html><body style="font-family: -apple-system, sans-serif; padding: 20px; color: #111;">
            <h2>Reset your password</h2>
            <p>Someone requested a password reset for your InvoiceApp account (<strong>%s</strong>).</p>
            <p>Click the button below to set a new password. This link expires in <strong>1 hour</strong>.</p>
            <p style="margin: 24px 0;">
              <a href="%s" style="background: #2563eb; color: white; padding: 12px 24px;
                 border-radius: 8px; text-decoration: none; font-weight: 600;">
                Reset Password
              </a>
            </p>
            <p style="color: #666; font-size: 13px;">
              If you didn't request this, you can safely ignore this email.
            </p>
            </body></html>
            """.formatted(user.getEmail(), resetLink);

        emailChannel.send(user.getEmail(), subject, body, "reset-" + prt.getId());
        log.info("Password reset email sent to {}", user.getEmail());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken prt = tokenRepository.findByToken(request.token())
            .orElseThrow(() -> ApiException.badRequest("Invalid or expired reset token"));

        if (prt.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Reset token has expired. Please request a new one.");
        }
        if (prt.getUsedAt() != null) {
            throw ApiException.badRequest("Reset token has already been used.");
        }

        User user = userRepository.findById(prt.getUserId())
            .orElseThrow(() -> ApiException.badRequest("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        prt.setUsedAt(Instant.now());
        tokenRepository.save(prt);

        log.info("Password reset successfully for user {}", user.getEmail());
    }
}
