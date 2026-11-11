package com.northwind.ad.results.it;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test double for the three scheduling-service calls results-service makes, served by the test app itself and
 * backed by the seeded dbo.Booking (contract semantics of docs/to-be/scheduling-service-openapi.yaml). Lets the
 * results-service ITs keep their legacy fixtures; the real service is exercised by scheduling-contract-tests and
 * the e2e check. {@link #down} simulates an outage (503).
 */
@RestController
@RequestMapping(LegacyDbSchedulingStub.BASE)
public class LegacyDbSchedulingStub {

    public static final String BASE = "/test-scheduling/api/v1";

    public static volatile boolean down;
    /** Only POST /attendance fails: 503 when "503", or the given 4xx error code (e.g. BOOKING_NOT_ACTIVE). */
    public static volatile String attendFailure;

    @Autowired
    private JdbcTemplate jdbc;

    private static final String SELECT = "SELECT b.BookingId AS bookingId, b.SessionId AS sessionId, "
            + "b.CandidateId AS candidateId, c.CandidateNumber AS candidateNumber, s.ExamId AS examId, "
            + "CONVERT(VARCHAR(19), s.StartsAt, 126) AS sessionStartsAt, b.Status AS status "
            + "FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId = b.SessionId "
            + "JOIN dbo.Candidate c ON c.CandidateId = b.CandidateId ";

    @RequestMapping(value = "/bookings/{id}", method = RequestMethod.GET)
    public ResponseEntity<?> get(@PathVariable int id) {
        if (down) {
            return unavailable();
        }
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + "WHERE b.BookingId = ?", id);
        return rows.isEmpty() ? error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND") : ResponseEntity.ok(rows.get(0));
    }

    @RequestMapping(value = "/bookings", method = RequestMethod.GET)
    public ResponseEntity<?> search(@RequestParam String candidateNumber, @RequestParam int sessionId) {
        if (down) {
            return unavailable();
        }
        return ResponseEntity.ok(jdbc.queryForList(SELECT + "WHERE c.CandidateNumber = ? AND b.SessionId = ? "
                + "AND b.Status IN ('BOOKED','ATTENDED') ORDER BY b.BookingId DESC", candidateNumber, sessionId));
    }

    @RequestMapping(value = "/bookings/{id}/attendance", method = RequestMethod.POST)
    public ResponseEntity<?> attend(@PathVariable int id) {
        if (down || "503".equals(attendFailure)) {
            return unavailable();
        }
        if (attendFailure != null) {
            return error(HttpStatus.CONFLICT, attendFailure);
        }
        List<String> st = jdbc.queryForList("SELECT Status FROM dbo.Booking WHERE BookingId = ?", String.class, id);
        if (st.isEmpty()) {
            return error(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND");
        }
        if (!"BOOKED".equals(st.get(0)) && !"ATTENDED".equals(st.get(0))) {
            return error(HttpStatus.CONFLICT, "BOOKING_NOT_ACTIVE");
        }
        jdbc.update("UPDATE dbo.Booking SET Status = 'ATTENDED', LastModified = GETDATE() "
                + "WHERE BookingId = ? AND Status = 'BOOKED'", id);
        return get(id);
    }

    private static ResponseEntity<?> unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "UNAVAILABLE");
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", code);
        body.put("message", code);
        return ResponseEntity.status(status).body(body);
    }
}
