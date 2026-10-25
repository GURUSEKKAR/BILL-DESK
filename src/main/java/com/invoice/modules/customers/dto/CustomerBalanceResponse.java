package com.invoice.modules.customers.dto;

import java.util.UUID;

/**
 * Business-wide balance view for a customer. Deliberately contains NO invoice
 * details, so a cashier cannot see bills raised at other counters — only the
 * aggregate the customer owes and their credit status.
 */
public record CustomerBalanceResponse(
    UUID customerId,
    String name,
    long outstandingPaise,
    Long creditLimitPaise,
    Long availableCreditPaise
) {}
