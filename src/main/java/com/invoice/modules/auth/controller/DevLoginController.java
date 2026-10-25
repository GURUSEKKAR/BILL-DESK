package com.invoice.modules.auth.controller;

import com.invoice.modules.auth.dto.AuthResponse;
import com.invoice.modules.auth.entity.Business;
import com.invoice.modules.auth.entity.User;
import com.invoice.modules.auth.repository.BusinessRepository;
import com.invoice.modules.auth.repository.UserRepository;
import com.invoice.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * DEV ONLY — bypasses password check for easy local testing.
 * POST /auth/dev-login  { "email": "...", "newPassword": "..." }
 * Resets the password AND returns a valid token in one call.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class DevLoginController {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/dev-login")
    public AuthResponse devLogin(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String newPassword = body.getOrDefault("newPassword", "pass1234");

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found: " + email));

        // Reset the password to whatever they send
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        Business business = businessRepository.findById(user.getBusinessId())
            .orElseThrow(() -> new RuntimeException("Business not found"));

        JwtService.TokenPair tokens = jwtService.generateTokens(
            user.getId(), business.getId(), user.getRole().name(), user.getCounterId());
        return new AuthResponse(
            tokens.accessToken(), tokens.refreshToken(),
            business.getId(), business.getName(), business.getLoginCode(),
            user.getRole().name(), user.getCounterId(), null);
    }
}
