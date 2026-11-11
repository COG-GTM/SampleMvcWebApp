package com.northwind.assessment.booking.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.northwind.assessment.booking.client.SchedulingException;

@ControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
        return body(ex.getStatus(), ex.getError(), ex.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadInput(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDb(DataAccessException ex) {
        log.error("Database error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "DB_ERROR", ex.getMostSpecificCause().getMessage());
    }

    /** scheduling-service unreachable or answering with something BookingService did not translate */
    @ExceptionHandler(SchedulingException.class)
    public ResponseEntity<Map<String, Object>> handleScheduling(SchedulingException ex) {
        log.error("scheduling-service error {} {}: {}", ex.getHttpStatus(), ex.getError(), ex.getMessage());
        HttpStatus status = ex.getHttpStatus() >= 500 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.valueOf(ex.getHttpStatus());
        return body(status, ex.getError(), ex.getMessage());
    }

    static ResponseEntity<Map<String, Object>> body(HttpStatus status, String error, String message) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("error", error);
        m.put("message", message);
        return new ResponseEntity<Map<String, Object>>(m, status);
    }
}
