package com.northwind.scheduling.api;

import org.springframework.http.HttpStatus;

/**
 * A command rejection. {@code legacyState} is the RAISERROR state the legacy proc used for the same rejection
 * (message 50000, HB-14), so callers can map it exactly as they mapped the proc error.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final Integer legacyState;

    public ApiException(HttpStatus status, String error, String message, Integer legacyState) {
        super(message);
        this.status = status;
        this.error = error;
        this.legacyState = legacyState;
    }

    public static ApiException badRequest(String error, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, error, message, null);
    }

    public static ApiException notFound(String error, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, error, message, null);
    }

    public static ApiException conflict(String error, String message) {
        return new ApiException(HttpStatus.CONFLICT, error, message, null);
    }

    public static ApiException legacy(HttpStatus status, String error, int state, String message) {
        return new ApiException(status, error, message, state);
    }

    public HttpStatus status() {
        return status;
    }

    public String error() {
        return error;
    }

    public Integer legacyState() {
        return legacyState;
    }
}
