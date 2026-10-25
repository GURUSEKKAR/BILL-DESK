package com.invoice.modules.dashboard.dto;

import java.util.UUID;

/**
 * Projection returned by the "outstanding per customer" aggregation query.
 * Used in a JPQL constructor expression, so the types must match the query result.
 */
public record CustomerOutstanding(UUID customerId, String customerName, Long owedPaise) {}
