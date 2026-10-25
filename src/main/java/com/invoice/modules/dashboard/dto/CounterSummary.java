package com.invoice.modules.dashboard.dto;

import java.util.Map;
import java.util.UUID;

/**
 * One row in the owner's per-counter dashboard table / box.
 */
public record CounterSummary(
    UUID counterId,
    String counterCode,
    String counterName,
    long salesTodayPaise,
    long collectedTodayPaise,
    long creditGivenTodayPaise,
    Map<String, Long> collectedTodayByMethod,
    long outstandingPaise,
    long overduePaise,
    long customerCount
) {}
