package com.invoice.security;

import com.invoice.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Single entry point for reading the authenticated user's identity.
 * <p>
 * <b>This is the tenant-scoping helper.</b> Every service calls
 * {@link #businessId()} to get the current tenant. Reading the business id
 * from request bodies or path variables is forbidden — it must always come
 * from the JWT via this class, so cross-tenant leaks are impossible by
 * construction.
 */
public final class AuthContext {

    private AuthContext() {}

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return principal;
    }

    public static UUID businessId() {
        return current().getBusinessId();
    }

    public static UUID userId() {
        return current().getUserId();
    }

    public static com.invoice.modules.auth.entity.Role role() {
        return current().getRole();
    }

    /** CASHIER: their pinned counter. OWNER: null (all counters). */
    public static UUID counterId() {
        return current().getCounterId();
    }
}
