package com.northwind.assessment.booking.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Counts seats actually taken on a session (BOOKED + ATTENDED).
 * Same rule as the portal's SessionSeatHelper - keep the two in step.
 */
@Component
public class SeatCounter {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public int countBookedSeats(int sessionId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = ? AND Status IN ('BOOKED','ATTENDED')",
                Integer.class, sessionId);
        return n == null ? 0 : n.intValue();
    }

    public int countWaitlisted(int sessionId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = ? AND Status = 'WAITLISTED'",
                Integer.class, sessionId);
        return n == null ? 0 : n.intValue();
    }
}
