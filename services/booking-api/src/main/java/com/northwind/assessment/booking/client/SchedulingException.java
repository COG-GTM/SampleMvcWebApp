package com.northwind.assessment.booking.client;

/** Non-2xx answer from scheduling-service: {error, message, legacyState} plus the HTTP status. */
public class SchedulingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;
    private final String error;
    private final Integer legacyState;

    public SchedulingException(int httpStatus, String error, String message, Integer legacyState) {
        super(message);
        this.httpStatus = httpStatus;
        this.error = error;
        this.legacyState = legacyState;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getError() {
        return error;
    }

    public Integer getLegacyState() {
        return legacyState;
    }
}
