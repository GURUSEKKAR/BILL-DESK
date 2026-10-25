package com.invoice.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when an API request fails for an expected business reason
 * (not found, illegal state transition, conflict, etc.).
 * {@link GlobalExceptionHandler} converts these into JSON error responses.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " not found");
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
