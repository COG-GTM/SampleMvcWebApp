package com.northwind.assessment.booking.web;

import static org.junit.Assert.assertEquals;

import java.sql.SQLException;
import java.util.Map;

import org.junit.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;

public class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    public void permissionDeniedFromSingleWriterDenyMapsTo403() {
        SQLException denied = new SQLException(
                "The INSERT permission was denied on the object 'Booking', database 'AssessmentDelivery', schema 'dbo'.",
                "S0005", 229);
        ResponseEntity<Map<String, Object>> r = handler.handleDb(
                new UncategorizedSQLException("insertBooking", "INSERT INTO dbo.Booking ...", denied));
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals("LEGACY_WRITE_DENIED", r.getBody().get("error"));
    }

    @Test
    public void columnPermissionDeniedAlsoMapsTo403() {
        SQLException denied = new SQLException("The UPDATE permission was denied on the column 'SeatsAvailable' "
                + "of the object 'ExamSession'", "S0005", 230);
        assertEquals(HttpStatus.FORBIDDEN, handler.handleDb(
                new UncategorizedSQLException("decrement", "UPDATE dbo.ExamSession ...", denied)).getStatusCode());
    }

    @Test
    public void otherDatabaseErrorsStay500() {
        ResponseEntity<Map<String, Object>> r = handler.handleDb(new DataIntegrityViolationException("dup"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertEquals("DB_ERROR", r.getBody().get("error"));
    }
}
