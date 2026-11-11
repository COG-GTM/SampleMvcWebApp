package com.northwind.ad.results.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** POST /api/v1/results against SQL Server (MBA-3827). */
public class ResultPostIdempotencyTest extends AbstractResultsIT {

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> post(int bookingId, int score, String scoredAt) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("bookingId", bookingId);
        body.put("score", score);
        if (scoredAt != null) {
            body.put("scoredAt", scoredAt);
        }
        return rest.postForEntity("/api/v1/results", body, Map.class);
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void identicalReplayReturns200DuplicateAndChangesNothing() {
        Fixture f = newBooking();
        ResponseEntity<Map> first = post(f.bookingId, 740, "2026-11-10T09:30:00");
        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals("CREATED", first.getBody().get("status"));
        int resultId = ((Number) first.getBody().get("resultId")).intValue();
        Map<String, Object> rowBefore = result(f.bookingId);
        Map<String, Object> bookingBefore = booking(f.bookingId);
        assertEquals("ATTENDED", bookingBefore.get("Status"));
        int auditsBefore = auditCount(resultId);

        ResponseEntity<Map> replay = post(f.bookingId, 740, "2026-11-10T09:30:00");

        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertEquals("DUPLICATE", replay.getBody().get("status"));
        assertEquals(resultId, ((Number) replay.getBody().get("resultId")).intValue());
        assertEquals(1, resultCount(f.bookingId));
        assertEquals(rowBefore, result(f.bookingId));
        assertEquals(bookingBefore, booking(f.bookingId));
        assertEquals(auditsBefore, auditCount(resultId));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void replayWithoutScoredAtMatchesOnScore() {
        Fixture f = newBooking();
        assertEquals(HttpStatus.CREATED, post(f.bookingId, 512, null).getStatusCode());
        Map<String, Object> rowBefore = result(f.bookingId);

        ResponseEntity<Map> replay = post(f.bookingId, 512, null);

        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertEquals("DUPLICATE", replay.getBody().get("status"));
        assertEquals(rowBefore, result(f.bookingId));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void differentScoreReturns409WithBothScoresAndAudits() {
        Fixture f = newBooking();
        ResponseEntity<Map> first = post(f.bookingId, 700, "2026-11-10T10:00:00");
        int resultId = ((Number) first.getBody().get("resultId")).intValue();
        Map<String, Object> rowBefore = result(f.bookingId);
        Map<String, Object> bookingBefore = booking(f.bookingId);

        ResponseEntity<Map> conflict = post(f.bookingId, 650, "2026-11-10T10:00:00");

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("DUPLICATE_RESULT", conflict.getBody().get("error"));
        assertEquals(700, ((Number) conflict.getBody().get("storedScore")).intValue());
        assertEquals(650, ((Number) conflict.getBody().get("submittedScore")).intValue());
        assertEquals(resultId, ((Number) conflict.getBody().get("resultId")).intValue());
        assertEquals(rowBefore, result(f.bookingId));
        assertEquals(bookingBefore, booking(f.bookingId));
        assertEquals(1, auditCount(resultId, "DUPLICATE_REJECTED"));
        String detail = jdbc.queryForObject("SELECT Detail FROM dbo.AuditLog WHERE EntityName = 'Result' "
                + "AND EntityId = ? AND Action = 'DUPLICATE_REJECTED'", String.class, resultId);
        assertTrue(detail, detail.contains("stored=700") && detail.contains("submitted=650"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void sameScoreDifferentScoredAtIsAConflict() {
        Fixture f = newBooking();
        ResponseEntity<Map> first = post(f.bookingId, 800, "2026-11-10T10:00:00");
        int resultId = ((Number) first.getBody().get("resultId")).intValue();

        ResponseEntity<Map> conflict = post(f.bookingId, 800, "2026-11-11T10:00:00");

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("DUPLICATE_RESULT", conflict.getBody().get("error"));
        assertEquals("2026-11-10T10:00:00", conflict.getBody().get("storedScoredAt"));
        assertEquals("2026-11-11T10:00:00", conflict.getBody().get("submittedScoredAt"));
        assertEquals("2026-11-10T10:00:00", result(f.bookingId).get("ScoredAt"));
        assertEquals(1, auditCount(resultId, "DUPLICATE_REJECTED"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void concurrentIdenticalPostsStoreOneRowAndNever500() throws Exception {
        int callers = 12;
        for (int round = 0; round < 5; round++) {
            final Fixture f = newBooking();
            final CountDownLatch go = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(callers);
            List<Future<ResponseEntity<Map>>> futures = new ArrayList<Future<ResponseEntity<Map>>>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(new Callable<ResponseEntity<Map>>() {
                    @Override
                    public ResponseEntity<Map> call() throws Exception {
                        go.await();
                        return post(f.bookingId, 610, "2026-11-10T11:00:00");
                    }
                }));
            }
            go.countDown();
            int created = 0;
            int duplicate = 0;
            for (Future<ResponseEntity<Map>> fu : futures) {
                ResponseEntity<Map> r = fu.get(60, TimeUnit.SECONDS);
                if (r.getStatusCode() == HttpStatus.CREATED) {
                    created++;
                } else {
                    assertEquals("round " + round + ": " + r.getBody(), HttpStatus.OK, r.getStatusCode());
                    assertEquals("DUPLICATE", r.getBody().get("status"));
                    duplicate++;
                }
            }
            pool.shutdown();
            assertEquals(1, created);
            assertEquals(callers - 1, duplicate);
            assertEquals(1, resultCount(f.bookingId));
            int resultId = ((Number) result(f.bookingId).get("ResultId")).intValue();
            assertEquals(1, auditCount(resultId, "INGEST"));
            assertEquals(0, auditCount(resultId, "DUPLICATE_REJECTED"));
        }
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void concurrentConflictingPostsKeepOneScoreAndNever500() throws Exception {
        final Fixture f = newBooking();
        final CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<Future<ResponseEntity<Map>>>();
        for (final int score : new int[] {300, 900}) {
            futures.add(pool.submit(new Callable<ResponseEntity<Map>>() {
                @Override
                public ResponseEntity<Map> call() throws Exception {
                    go.await();
                    return post(f.bookingId, score, "2026-11-10T12:00:00");
                }
            }));
        }
        go.countDown();
        List<HttpStatus> statuses = new ArrayList<HttpStatus>();
        for (Future<ResponseEntity<Map>> fu : futures) {
            statuses.add(fu.get(60, TimeUnit.SECONDS).getStatusCode());
        }
        pool.shutdown();
        assertTrue(statuses.toString(), statuses.contains(HttpStatus.CREATED) && statuses.contains(HttpStatus.CONFLICT));
        assertEquals(1, resultCount(f.bookingId));
        int resultId = ((Number) result(f.bookingId).get("ResultId")).intValue();
        assertEquals(1, auditCount(resultId, "DUPLICATE_REJECTED"));
    }
}
