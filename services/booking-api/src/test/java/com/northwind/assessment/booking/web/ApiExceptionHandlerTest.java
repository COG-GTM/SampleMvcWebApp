package com.northwind.assessment.booking.web;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.northwind.assessment.booking.client.SchedulingException;

public class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    public void systemErrorMapsToLegacyDbError() {
        ResponseEntity<Map<String, Object>> r = handler.handleScheduling(
                new SchedulingException(500, "SYSTEM_ERROR", "Transaction was deadlocked", null));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertEquals("DB_ERROR", r.getBody().get("error"));
        assertEquals("Transaction was deadlocked", r.getBody().get("message"));
    }

    @Test
    public void unavailableStays503() {
        ResponseEntity<Map<String, Object>> r = handler.handleScheduling(
                new SchedulingException(503, "SCHEDULING_UNAVAILABLE", "scheduling-service unreachable: timeout", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertEquals("SCHEDULING_UNAVAILABLE", r.getBody().get("error"));
    }
}
