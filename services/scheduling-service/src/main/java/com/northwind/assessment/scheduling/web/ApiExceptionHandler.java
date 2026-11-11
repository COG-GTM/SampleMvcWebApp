package com.northwind.assessment.scheduling.web;

import com.northwind.assessment.scheduling.domain.RuleViolation;
import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.service.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Same error codes / HTTP statuses as booking-api v1 {@code ApiExceptionHandler}, plus {@code legacyCode}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SchedulingRuleException.class)
    ResponseEntity<ApiError> rule(SchedulingRuleException ex) {
        HttpStatus status = ex.violation().kind() == RuleViolation.Kind.INVALID ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(new ApiError(ex.violation().errorCode(), ex.getMessage(), ex.legacyCode()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(ex.code(), ex.getMessage(), null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class, HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> badInput(Exception ex) {
        String message = ex instanceof HttpMessageNotReadableException ? "Malformed JSON request body" : ex.getMessage();
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", message, null));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> db(DataAccessException ex) {
        log.error("Database error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("DB_ERROR", "A database error occurred", null));
    }
}
