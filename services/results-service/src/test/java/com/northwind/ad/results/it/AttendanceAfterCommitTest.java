package com.northwind.ad.results.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Attendance is recorded only after the Result commits (docs/to-be/adr-001 section 2): a failed attendance call never
 * leaves an ATTENDED booking without its Result. 503 keeps the committed Result and a resend completes attendance;
 * a 409 from scheduling-service removes the Result again. Score-file rows still commit one by one (HB-19).
 */
public class AttendanceAfterCommitTest extends AbstractResultsIT {

    @After
    public void reset() {
        LegacyDbSchedulingStub.attendFailure = null;
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> post(int bookingId, int score) {
        Map<String, Object> req = new HashMap<String, Object>();
        req.put("bookingId", bookingId);
        req.put("score", score);
        req.put("scoredAt", "2026-11-10T09:00:00");
        return rest.postForEntity("/api/v1/results", req, Map.class);
    }

    private int ingestAudits(int bookingId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditLog WHERE EntityName = 'Result' AND Action = 'INGEST' "
                + "AND Detail LIKE ?", Integer.class, "booking=" + bookingId + " %");
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void attendanceOutageKeepsResultAndBookedAndResendCompletesIt() {
        Fixture f = newBooking();
        LegacyDbSchedulingStub.attendFailure = "503";

        ResponseEntity<Map> r = post(f.bookingId, 700);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertEquals("SCHEDULING_UNAVAILABLE", r.getBody().get("error"));
        assertEquals(1, resultCount(f.bookingId));
        assertEquals("BOOKED", booking(f.bookingId).get("Status"));

        LegacyDbSchedulingStub.attendFailure = null;
        ResponseEntity<Map> again = post(f.bookingId, 700);

        assertEquals(HttpStatus.OK, again.getStatusCode());
        assertEquals("DUPLICATE", again.getBody().get("status"));
        assertEquals("ATTENDED", booking(f.bookingId).get("Status"));
        assertEquals(1, resultCount(f.bookingId));
        assertEquals(1, ingestAudits(f.bookingId));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void attendanceRejectedRemovesTheCommittedResult() {
        Fixture f = newBooking();
        LegacyDbSchedulingStub.attendFailure = "BOOKING_NOT_ACTIVE";

        ResponseEntity<Map> r = post(f.bookingId, 700);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("BOOKING_NOT_ACTIVE", r.getBody().get("error"));
        assertEquals(0, resultCount(f.bookingId));
        assertEquals(0, ingestAudits(f.bookingId));
        assertEquals("BOOKED", booking(f.bookingId).get("Status"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void scoreFileRowsCommitOneByOneAndResendCompletesAttendance() {
        Fixture a = newBooking();
        Fixture b = newBooking();
        String file = "CandidateNumber,SessionId,Score,ScoredAt\n"
                + a.csvRow(710, "2026-11-10T09:00:00") + "\n" + b.csvRow(420, "2026-11-10T09:00:00") + "\n";
        LegacyDbSchedulingStub.attendFailure = "503";

        ResponseEntity<Map> first = upload("NWTEST_ATTEND_PENDING.csv", file);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(2, ((List) first.getBody().get("errors")).size());
        assertNull(first.getBody().get("uploadId"));
        assertEquals(1, resultCount(a.bookingId));
        assertEquals(1, resultCount(b.bookingId));
        assertEquals("BOOKED", booking(a.bookingId).get("Status"));
        assertEquals("BOOKED", booking(b.bookingId).get("Status"));

        LegacyDbSchedulingStub.attendFailure = null;
        ResponseEntity<Map> again = upload("NWTEST_ATTEND_PENDING.csv", file);

        assertEquals(0, ((Number) again.getBody().get("imported")).intValue());
        assertEquals(2, ((Number) again.getBody().get("duplicates")).intValue());
        assertEquals(0, ((List) again.getBody().get("errors")).size());
        assertNotNull(again.getBody().get("uploadId"));
        assertEquals("ATTENDED", booking(a.bookingId).get("Status"));
        assertEquals("ATTENDED", booking(b.bookingId).get("Status"));
    }

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> upload(final String fileName, String content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return rest.postForEntity("/api/v1/results/upload", form, Map.class);
    }
}
