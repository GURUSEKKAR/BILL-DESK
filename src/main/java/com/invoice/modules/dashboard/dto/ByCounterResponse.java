package com.invoice.modules.dashboard.dto;

import java.util.List;

/**
 * Owner-only dashboard: per-counter rows plus top debtors across the business.
 */
public record ByCounterResponse(
    List<CounterSummary> counters,
    List<CustomerOutstanding> topDebtors
) {}
