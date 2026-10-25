package com.invoice.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
            .body(new ApiError(Instant.now(), ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                e -> e.getField(),
                e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid",
                (a, b) -> a
            ));
        return ResponseEntity.badRequest().body(Map.of(
            "timestamp", Instant.now(),
            "status", 400,
            "error", "Bad Request",
            "errors", errors
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
            .body(new ApiError(Instant.now(), 400, "Bad Request",
                "Invalid parameter: " + ex.getName()));
    }

    /**
     * Method-security (@PreAuthorize) denials and other access-denied cases map
     * to 403 Forbidden rather than the generic 500.
     */
    @ExceptionHandler({
        org.springframework.security.authorization.AuthorizationDeniedException.class,
        org.springframework.security.access.AccessDeniedException.class
    })
    public ResponseEntity<ApiError> handleAccessDenied(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new ApiError(Instant.now(), 403, "Forbidden", "Access denied"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ApiError(Instant.now(), 500, "Internal Server Error",
                "An unexpected error occurred"));
    }
}
