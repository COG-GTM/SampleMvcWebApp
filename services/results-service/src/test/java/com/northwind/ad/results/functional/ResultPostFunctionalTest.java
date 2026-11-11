package com.northwind.ad.results.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

/** AC1 + AC2 (+ the missing-scoredAt rule) through HTTP against SQL Server 2022. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("rawtypes")
public class ResultPostFunctionalTest extends SqlServerTestSupport {

    @Test
    public void newResultIs201Created() {
        int bookingId = id(claimFreshBooking(), "BookingId");

        ResponseEntity<Map> r = postResult(bookingId, 720, "2026-11-02T09:15:00");

        assertEquals(HttpStatus.CREATED, r.getStatusCode());
        assertEquals("CREATED", r.getBody().get("status"));
        assertEquals(((Number) resultRow(bookingId).get("ResultId")).intValue(), ((Number) r.getBody().get("resultId")).intValue());
        verify(attendance).recordAttendance(bookingId);
    }

    @Test
    public void ac1IdenticalReplayIs200DuplicateAndWritesNothing() {
        int bookingId = id(claimFreshBooking(), "BookingId");
        ResponseEntity<Map> first = postResult(bookingId, 731, "2026-11-02T09:15:42");
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        Map<String, Object> resultBefore = resultRow(bookingId);
        Map<String, Object> bookingBefore = bookingRow(bookingId);
        int totalBefore = totalResults();
        int auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditLog", Integer.class);

        ResponseEntity<Map> replay = postResult(bookingId, 731, "2026-11-02T09:15:42");

        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertEquals("DUPLICATE", replay.getBody().get("status"));
        assertEquals(first.getBody().get("resultId"), replay.getBody().get("resultId"));
        assertEquals(bookingId, ((Number) replay.getBody().get("bookingId")).intValue());
        assertEquals(731, ((Number) replay.getBody().get("score")).intValue());
        assertNotNull(replay.getBody().get("passed"));
        assertEquals(resultBefore, resultRow(bookingId));
        assertEquals(bookingBefore, bookingRow(bookingId));
        assertEquals(totalBefore, totalResults());
        assertEquals(auditBefore, (int) jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditLog", Integer.class));
        verify(attendance, times(1)).recordAttendance(bookingId);
    }

    @Test
    public void ac1ReplayOfSeededResultIs200Duplicate() {
        Map<String, Object> seeded = claimSeededResults(1).get(0);
        int bookingId = id(seeded, "BookingId");
        Map<String, Object> bookingBefore = bookingRow(bookingId);

        ResponseEntity<Map> replay = postResult(bookingId, id(seeded, "Score"), (String) seeded.get("ScoredAt"));

        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertEquals("DUPLICATE", replay.getBody().get("status"));
        assertEquals(id(seeded, "ResultId"), ((Number) replay.getBody().get("resultId")).intValue());
        assertEquals(bookingBefore, bookingRow(bookingId));
        assertEquals(1, resultCount(bookingId));
    }

    @Test
    public void ac2DifferentScoreIs409WithBothScoresAndOneCommittedAudit() {
        int bookingId = id(claimFreshBooking(), "BookingId");
        assertEquals(HttpStatus.CREATED, postResult(bookingId, 640, "2026-11-03T10:00:00").getStatusCode());
        Map<String, Object> stored = resultRow(bookingId);
        int resultId = ((Number) stored.get("ResultId")).intValue();
        Map<String, Object> bookingBefore = bookingRow(bookingId);

        ResponseEntity<Map> r = postResult(bookingId, 655, "2026-11-03T10:00:00");

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("DUPLICATE_RESULT", r.getBody().get("error"));
        assertEquals(640, ((Number) r.getBody().get("storedScore")).intValue());
        assertEquals(655, ((Number) r.getBody().get("submittedScore")).intValue());
        assertEquals(resultId, ((Number) r.getBody().get("resultId")).intValue());
        assertEquals("2026-11-03T10:00:00", r.getBody().get("storedScoredAt"));
        assertEquals("2026-11-03T10:00:00", r.getBody().get("submittedScoredAt"));
        assertTrue(String.valueOf(r.getBody().get("message")).contains("640"));
        assertEquals(stored, resultRow(bookingId));
        assertEquals(bookingBefore, bookingRow(bookingId));
        assertEquals(1, auditCount(resultId, "DUPLICATE_REJECTED"));
        String detail = auditDetails(resultId, "DUPLICATE_REJECTED").get(0);
        assertTrue(detail, detail.contains("booking=" + bookingId) && detail.contains("storedScore=640") && detail.contains("submittedScore=655"));
    }

    @Test
    public void ac2SameScoreDifferentScoredAtIsAConflict() {
        int bookingId = id(claimFreshBooking(), "BookingId");
        assertEquals(HttpStatus.CREATED, postResult(bookingId, 700, "2026-11-03T10:00:00").getStatusCode());
        int resultId = ((Number) resultRow(bookingId).get("ResultId")).intValue();

        ResponseEntity<Map> r = postResult(bookingId, 700, "2026-11-03T10:00:01");

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("DUPLICATE_RESULT", r.getBody().get("error"));
        assertEquals(1, auditCount(resultId, "DUPLICATE_REJECTED"));
    }

    @Test
    public void ac2EachRejectedConflictIsAudited() {
        int bookingId = id(claimFreshBooking(), "BookingId");
        assertEquals(HttpStatus.CREATED, postResult(bookingId, 610, "2026-11-03T10:00:00").getStatusCode());
        int resultId = ((Number) resultRow(bookingId).get("ResultId")).intValue();

        assertEquals(HttpStatus.CONFLICT, postResult(bookingId, 611, "2026-11-03T10:00:00").getStatusCode());
        assertEquals(HttpStatus.CONFLICT, postResult(bookingId, 612, null).getStatusCode());

        assertEquals(2, auditCount(resultId, "DUPLICATE_REJECTED"));
        assertEquals(610, ((Number) resultRow(bookingId).get("Score")).intValue());
    }

    @Test
    public void missingScoredAtReplayWithSameScoreIsDuplicate() {
        int bookingId = id(claimFreshBooking(), "BookingId");
        ResponseEntity<Map> first = postResult(bookingId, 690, null);
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        Map<String, Object> stored = resultRow(bookingId);

        ResponseEntity<Map> replay = postResult(bookingId, 690, null);

        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertEquals("DUPLICATE", replay.getBody().get("status"));
        assertEquals(stored, resultRow(bookingId));
        assertEquals(0, auditCount(((Number) stored.get("ResultId")).intValue(), "DUPLICATE_REJECTED"));
    }

    @Test
    public void missingScoredAtWithDifferentScoreIsConflict() {
        int bookingId = id(claimFreshBooking(), "BookingId");
        assertEquals(HttpStatus.CREATED, postResult(bookingId, 690, "2026-11-04T08:00:00").getStatusCode());

        ResponseEntity<Map> r = postResult(bookingId, 691, null);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertFalse(r.getBody().containsKey("submittedScoredAt") && r.getBody().get("submittedScoredAt") != null);
    }

    @Test
    public void unknownBookingStays404() {
        ResponseEntity<Map> r = postResult(99999999, 700, null);
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals("BOOKING_NOT_FOUND", r.getBody().get("error"));
    }
}
