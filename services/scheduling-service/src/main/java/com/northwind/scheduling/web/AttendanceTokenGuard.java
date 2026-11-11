package com.northwind.scheduling.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.SchedulingException;

/**
 * MBA-3823: attendance writes ATTENDED into legacy through the relay, so only a caller holding the service credential
 * (results-service) may record it. Fails closed: with scheduling.attendance.service-token unset every call is refused.
 */
@Component
public class AttendanceTokenGuard {

    public static final String HEADER = "X-Service-Token";

    private final byte[] expected;

    public AttendanceTokenGuard(@Value("${scheduling.attendance.service-token:}") String token) {
        this.expected = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    public void check(String presented) {
        if (expected == null || presented == null
                || !MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {
            throw new SchedulingException(ErrorCode.UNAUTHORIZED, HEADER + " missing or invalid");
        }
    }
}
