package com.invoice.security;

import java.util.UUID;

/**
 * The effective data scope for a request.
 * <p>
 * {@code counterId == null} means "all counters in the business" — only an
 * OWNER can hold a business-wide scope. A CASHIER always resolves to exactly
 * their own counter.
 * <p>
 * Built exclusively by {@link ScopeResolver}; never constructed from raw
 * request input elsewhere.
 */
public record Scope(UUID businessId, UUID counterId) {

    public boolean isBusinessWide() {
        return counterId == null;
    }
}
