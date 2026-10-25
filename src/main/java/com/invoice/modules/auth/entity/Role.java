package com.invoice.modules.auth.entity;

/**
 * User role within a business.
 * <ul>
 *   <li>{@code OWNER}   — sees all counters; can manage counters and users.</li>
 *   <li>{@code CASHIER} — pinned to a single counter; sees only that counter.</li>
 * </ul>
 */
public enum Role {
    OWNER,
    CASHIER
}
