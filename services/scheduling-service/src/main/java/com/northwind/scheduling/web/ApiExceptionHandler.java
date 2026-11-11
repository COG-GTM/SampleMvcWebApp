package com.northwind.scheduling.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.northwind.scheduling.domain.SchedulingException;

/**
 * Structured errors as booking-api's ApiExceptionHandler ({error, message}); /api/v2 adds
 * {@code legacyCode} (50000 + proc state) so callers can map to the portal's numbers (ADR 0002, ODD-11/12).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(SchedulingException.class)
    public ResponseEntity<Map<String, Object>> rule(SchedulingException ex, jakarta.servlet.http.HttpServletRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", ex.code().name());
        m.put("message", ex.getMessage());
        if (req.getRequestURI().startsWith("/api/v2")) {
            m.put("legacyCode", ex.code().legacyCode());
        }
        return ResponseEntity.status(ex.code().httpStatus()).body(m);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> badInput(Exception ex) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", "BAD_REQUEST");
        m.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(m);
    }
}
