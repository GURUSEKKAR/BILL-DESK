package com.invoice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Generates and validates JWT access + refresh tokens.
 * <p>
 * Access tokens last 15 minutes and are sent on every request.
 * Refresh tokens last 7 days and are only used at {@code /auth/refresh}.
 * Both carry the user id and the business id (the tenant boundary).
 */
@Slf4j
@Service
public class JwtService {

    private final String secret;
    private final long accessExpirationMinutes;
    private final long refreshExpirationDays;
    private SecretKey key;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-expiration-minutes}") long accessExpirationMinutes,
            @Value("${app.jwt.refresh-expiration-days}") long refreshExpirationDays) {
        this.secret = secret;
        this.accessExpirationMinutes = accessExpirationMinutes;
        this.refreshExpirationDays = refreshExpirationDays;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record TokenPair(String accessToken, String refreshToken) {}

    public TokenPair generateTokens(UUID userId, UUID businessId, String role, UUID counterId) {
        return new TokenPair(
            buildToken(userId, businessId, role, counterId, "ACCESS", Duration.ofMinutes(accessExpirationMinutes)),
            buildToken(userId, businessId, role, counterId, "REFRESH", Duration.ofDays(refreshExpirationDays))
        );
    }

    private String buildToken(UUID userId, UUID businessId, String role, UUID counterId,
                              String type, Duration ttl) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
            .subject(userId.toString())
            .claim("businessId", businessId.toString())
            .claim("role", role)
            .claim("type", type)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .signWith(key);
        if (counterId != null) {
            builder.claim("counterId", counterId.toString());
        }
        return builder.compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
