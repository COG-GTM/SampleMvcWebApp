package com.northwind.assessment.scheduling.service;

/** 404 with a booking-api style error code (SESSION_NOT_FOUND, BOOKING_NOT_FOUND, ...). */
public class NotFoundException extends RuntimeException {

    private final String code;

    public NotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
