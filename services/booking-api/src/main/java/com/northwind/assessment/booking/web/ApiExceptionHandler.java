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
        if (isPermissionDenied(ex)) {
            // MBA-3823: db/security/040_single_writer.sql denies booking/seat writes to ad_booking_api
            log.warn("Legacy write denied: {}", ex.getMostSpecificCause().getMessage());
            return body(HttpStatus.FORBIDDEN, "LEGACY_WRITE_DENIED", "Booking and capacity writes are owned by "
                    + "scheduling-service; send them through the routing gateway (:8000)");
        }
        log.error("Database error", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "DB_ERROR", ex.getMostSpecificCause().getMessage());
    }

    // SQL Server 229/230: The <permission> permission was denied on the object/column
    static boolean isPermissionDenied(DataAccessException ex) {
        Throwable t = ex.getMostSpecificCause();
        if (t instanceof java.sql.SQLException) {
            int code = ((java.sql.SQLException) t).getErrorCode();
            if (code == 229 || code == 230) {
                return true;
            }
        }
        return t != null && t.getMessage() != null && t.getMessage().contains("permission was denied");
    }

    static ResponseEntity<Map<String, Object>> body(HttpStatus status, String error, String message) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("error", error);
        m.put("message", message);
        return new ResponseEntity<Map<String, Object>>(m, status);
    }
}
