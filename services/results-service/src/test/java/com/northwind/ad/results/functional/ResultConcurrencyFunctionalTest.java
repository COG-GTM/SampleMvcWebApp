package com.northwind.ad.results.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

/** AC5: concurrent posts for one booking hit UQ_Result_Booking and are classified, never a 500. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings("rawtypes")
public class ResultConcurrencyFunctionalTest extends SqlServerTestSupport {

    private static final int CALLERS = 8;
    private static final int ROUNDS = 5;

    private List<ResponseEntity<Map>> race(final int bookingId, final int[] scores) throws Exception {
        final CountDownLatch ready = new CountDownLatch(scores.length);
        final CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(scores.length);
        try {
            List<Future<ResponseEntity<Map>>> futures = new ArrayList<Future<ResponseEntity<Map>>>();
            for (final int score : scores) {
                futures.add(pool.submit(new Callable<ResponseEntity<Map>>() {
                    @Override
                    public ResponseEntity<Map> call() throws Exception {
                        ready.countDown();
                        go.await();
                        return postResult(bookingId, score, "2026-11-07T11:30:00");
                    }
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            go.countDown();
            List<ResponseEntity<Map>> out = new ArrayList<ResponseEntity<Map>>();
            for (Future<ResponseEntity<Map>> f : futures) {
                out.add(f.get(60, TimeUnit.SECONDS));
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void ac5ConcurrentIdenticalPostsCreateExactlyOneRow() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            int bookingId = id(claimFreshBooking(), "BookingId");
            int[] scores = new int[CALLERS];
            java.util.Arrays.fill(scores, 744);

            List<ResponseEntity<Map>> responses = race(bookingId, scores);

            int created = 0;
            Object resultId = null;
            for (ResponseEntity<Map> r : responses) {
                assertTrue("unexpected " + r.getStatusCode() + " " + r.getBody(),
                        r.getStatusCode() == HttpStatus.CREATED || r.getStatusCode() == HttpStatus.OK);
                if (r.getStatusCode() == HttpStatus.CREATED) {
                    created++;
                    assertEquals("CREATED", r.getBody().get("status"));
                } else {
                    assertEquals("DUPLICATE", r.getBody().get("status"));
                }
                if (resultId == null) {
                    resultId = r.getBody().get("resultId");
                }
                assertEquals(resultId, r.getBody().get("resultId"));
            }
            assertEquals(1, created);
            assertEquals(1, resultCount(bookingId));
            assertEquals(0, auditCount(((Number) resultId).intValue(), "DUPLICATE_REJECTED"));
        }
    }

    @Test
    public void ac5ConcurrentConflictingPostsGive409NotA500() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            int bookingId = id(claimFreshBooking(), "BookingId");
            int[] scores = new int[CALLERS];
            for (int i = 0; i < CALLERS; i++) {
                scores[i] = 600 + i;
            }

            List<ResponseEntity<Map>> responses = race(bookingId, scores);

            int created = 0;
            int conflicts = 0;
            for (ResponseEntity<Map> r : responses) {
                assertTrue("unexpected " + r.getStatusCode() + " " + r.getBody(),
                        r.getStatusCode() == HttpStatus.CREATED || r.getStatusCode() == HttpStatus.CONFLICT);
                if (r.getStatusCode() == HttpStatus.CREATED) {
                    created++;
                } else {
                    conflicts++;
                    assertEquals("DUPLICATE_RESULT", r.getBody().get("error"));
                }
            }
            assertEquals(1, created);
            assertEquals(CALLERS - 1, conflicts);
            assertEquals(1, resultCount(bookingId));
            int resultId = ((Number) resultRow(bookingId).get("ResultId")).intValue();
            assertEquals(CALLERS - 1, auditCount(resultId, "DUPLICATE_REJECTED"));
        }
    }
}
