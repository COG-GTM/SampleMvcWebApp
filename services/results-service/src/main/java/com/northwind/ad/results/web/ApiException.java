package com.northwind.ad.results.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final Map<String, Object> details;

    public ApiException(HttpStatus status, String error, String message) {
        this(status, error, message, null);
    }

    /** @param details extra fields added to the error body next to error/message */
    public ApiException(HttpStatus status, String error, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.error = error;
        this.details = details == null ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(details));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
