package com.invoice.common.exception;

import java.time.Instant;

/**
 * Standard error envelope returned by every failed API call.
 */
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message
) {}
