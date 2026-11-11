package com.northwind.scheduling.api;

import com.microsoft.sqlserver.jdbc.SQLServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.sql.SQLException;

@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.error(), e.getMessage(), e.legacyState(), null));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiError> validation(Exception e) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION", e.getMessage(), null, null));
    }

    /** Deadlock victims (1205) and other SQL failures roll back and surface as SYSTEM_ERROR, as the procs did. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> data(DataAccessException e) {
        Throwable root = e.getMostSpecificCause();
        Integer number = null;
        if (root instanceof SQLServerException se && se.getSQLServerError() != null) {
            number = se.getSQLServerError().getErrorNumber();
        } else if (root instanceof SQLException sql) {
            number = sql.getErrorCode();
        }
        LOG.warn("SQL failure {}: {}", number, root.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("SYSTEM_ERROR", root.getMessage(), null, number));
    }
}
