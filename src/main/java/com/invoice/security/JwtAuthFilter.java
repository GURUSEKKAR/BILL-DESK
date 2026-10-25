package com.invoice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Extracts the Bearer access token from the Authorization header, validates it,
 * and populates the SecurityContext with an {@link AuthUser} so that
 * {@link AuthContext#businessId()} works downstream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                if ("ACCESS".equals(claims.get("type"))) {
                    UUID userId = UUID.fromString(claims.getSubject());
                    UUID businessId = UUID.fromString(claims.get("businessId", String.class));
                    // Role defaults to OWNER for backward-compatibility with
                    // any pre-Phase-3 token that lacks the claim.
                    String roleClaim = claims.get("role", String.class);
                    com.invoice.modules.auth.entity.Role role =
                        roleClaim != null
                            ? com.invoice.modules.auth.entity.Role.valueOf(roleClaim)
                            : com.invoice.modules.auth.entity.Role.OWNER;
                    String counterClaim = claims.get("counterId", String.class);
                    UUID counterId = counterClaim != null ? UUID.fromString(counterClaim) : null;
                    AuthUser principal = new AuthUser(userId, businessId, role, counterId);
                    var auth = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                log.debug("JWT validation failed: {}", e.getMessage());
                // leave context empty — SecurityConfig rejects unauthenticated requests
            }
        }
        filterChain.doFilter(request, response);
    }
}
