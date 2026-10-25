package com.invoice.security;

import com.invoice.modules.auth.entity.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The authenticated principal stored in the Spring Security context.
 * <p>
 * Carries the user id and — critically — the business id that every
 * tenant-scoped query must use. The business id comes from the JWT,
 * never from the request body or URL.
 * <p>
 * Phase 3 adds the {@link Role} and, for CASHIER users, the counter id they
 * are pinned to. OWNER users have a {@code null} counter id (all counters).
 * The role is exposed as a Spring Security authority ({@code ROLE_OWNER} /
 * {@code ROLE_CASHIER}) so method-level {@code @PreAuthorize} checks work.
 */
@Getter
public class AuthUser implements UserDetails {

    private final UUID userId;
    private final UUID businessId;
    private final Role role;
    private final UUID counterId;

    public AuthUser(UUID userId, UUID businessId, Role role, UUID counterId) {
        this.userId = userId;
        this.businessId = businessId;
        this.role = role;
        this.counterId = counterId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return ""; }
    @Override public String getUsername() { return userId != null ? userId.toString() : "anonymous"; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
