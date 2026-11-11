package com.northwind.ad.results.web;

import java.util.HashMap;
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
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

// copied from booking-api (2015) - TODO move to a shared ad-common jar
@ControllerAdvice
public class RestErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RestErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException e) {
        return error(e.getStatus(), e.getError(), e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> db(DataAccessException e) {
        LOG.error("DB failure", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "DB_ERROR", e.getMostSpecificCause().getMessage());
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String code, String msg) {
        Map<String, String> m = new HashMap<String, String>();
        m.put("error", code);
        m.put("message", msg);
        return new ResponseEntity<Map<String, String>>(m, status);
    }
}
