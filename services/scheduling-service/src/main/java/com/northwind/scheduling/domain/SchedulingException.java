package com.northwind.scheduling.domain;

/** A rule violation with an {@link ErrorCode}; the web layer maps it to HTTP. */
public class SchedulingException extends RuntimeException {
    private final ErrorCode code;

    public SchedulingException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
