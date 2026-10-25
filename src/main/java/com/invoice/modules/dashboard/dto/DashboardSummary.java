package com.invoice.modules.dashboard.dto;

import java.util.List;
import java.util.Map;

/**
 * Dashboard summary for the caller's scope.
 * <p>
 * For a CASHIER this is their counter only. For an OWNER it is the whole
 * business, or a single counter when {@code ?counterId=} is supplied.
 */
public record DashboardSummary(
    long totalOutstandingPaise,
    long totalOverduePaise,
    long totalPaidPaise,
    long invoiceCount,
    long overdueCount,
    // Phase 3 "today" figures (IST day boundary)
    long salesTodayPaise,
    long collectedTodayPaise,
    long creditGivenTodayPaise,
    Map<String, Long> collectedTodayByMethod,
    List<CustomerOutstanding> outstandingPerCustomer
) {}
