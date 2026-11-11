package com.northwind.ad.results.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.northwind.ad.results.service.ScoreFileImporter;

/** POST /api/v1/results/upload against SQL Server (MBA-3827). */
public class ScoreFileIdempotencyTest extends AbstractResultsIT {

    private static final String HEADER = "candidateNumber,sessionId,score,scoredAt";

    @Autowired
    private ScoreFileImporter importer;

    @SuppressWarnings("rawtypes")
    private ResponseEntity<Map> upload(final String fileName, String content) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return rest.postForEntity("/api/v1/results/upload", form, Map.class);
    }

    private static String csv(String... rows) {
        StringBuilder sb = new StringBuilder(HEADER).append("\r\n");
        for (String r : rows) {
            sb.append(r).append("\r\n");
        }
        return sb.toString();
    }

    private static int num(Map<?, ?> body, String key) {
        return ((Number) body.get(key)).intValue();
    }

    private int uploadsWithFileName(String fileName) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ResultUpload WHERE FileName = ?", Integer.class, fileName);
    }

    private int resultsFromFile(String fileName) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Result WHERE SourceFile = ?", Integer.class, fileName);
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void reUploadOfSameFileImportsNothingAndReportsDuplicates() {
        Fixture a = newBooking();
        Fixture b = newBooking();
        Fixture c = newBooking();
        String file = csv(a.csvRow(720, "2026-11-10T09:00:00"), b.csvRow(455, "2026-11-10 09:05:00"),
                c.csvRow(901, "2026-11-10"));

        ResponseEntity<Map> first = upload("NWTEST_REPLAY.csv", file);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(3, num(first.getBody(), "imported"));
        assertEquals(0, num(first.getBody(), "duplicates"));
        assertEquals(Boolean.FALSE, first.getBody().get("alreadyUploaded"));
        int uploadId = num(first.getBody(), "uploadId");
        String sha = (String) first.getBody().get("sha256");
        assertEquals(64, sha.length());
        Map<String, Object> rowA = result(a.bookingId);
        int auditsA = auditCount(((Number) rowA.get("ResultId")).intValue());

        ResponseEntity<Map> again = upload("NWTEST_REPLAY.csv", file);

        assertEquals(HttpStatus.OK, again.getStatusCode());
        Map body = again.getBody();
        assertEquals(3, num(body, "rowsRead"));
        assertEquals(0, num(body, "imported"));
        assertEquals(3, num(body, "duplicates"));
        assertEquals(0, num(body, "conflicts"));
        assertEquals(3, num(body, "skipped"));
        assertTrue(((List) body.get("errors")).isEmpty());
        assertEquals(Boolean.TRUE, body.get("alreadyUploaded"));
        assertEquals(uploadId, num(body, "uploadId"));
        assertEquals(sha, body.get("sha256"));
        assertEquals(3, resultsFromFile("NWTEST_REPLAY.csv"));
        assertEquals(1, uploadsWithFileName("NWTEST_REPLAY.csv"));
        assertEquals(rowA, result(a.bookingId));
        assertEquals(auditsA, auditCount(((Number) rowA.get("ResultId")).intValue()));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void mixedFileInsertsNewSkipsDuplicatesAndReportsConflictsPerLine() {
        Fixture dup = newBooking();
        Fixture conflict = newBooking();
        Fixture fresh = newBooking();
        Fixture afterBadRow = newBooking();
        upload("NWTEST_MIXED_A.csv", csv(dup.csvRow(600, "2026-11-10T08:00:00"),
                conflict.csvRow(610, "2026-11-10T08:00:00")));
        int conflictResultId = ((Number) result(conflict.bookingId).get("ResultId")).intValue();
        Map<String, Object> conflictRowBefore = result(conflict.bookingId);

        String mixed = csv(
                fresh.csvRow(777, "2026-11-10T08:30:00"),           // line 2: new
                dup.csvRow(600, "2026-11-10T08:00:00"),             // line 3: duplicate
                conflict.csvRow(999, "2026-11-10T08:00:00"),        // line 4: conflict
                "NW00000001,1,abc,2026-11-10",                      // line 5: bad score
                afterBadRow.csvRow(333, "2026-11-10T08:45:00"),     // line 6: new, after the bad row
                "NW00000999,999999,500,2026-11-10");                // line 7: no booking
        ResponseEntity<Map> r = upload("NWTEST_MIXED_B.csv", mixed);

        assertEquals(HttpStatus.OK, r.getStatusCode());
        Map body = r.getBody();
        assertEquals(6, num(body, "rowsRead"));
        assertEquals(2, num(body, "imported"));
        assertEquals(1, num(body, "duplicates"));
        assertEquals(1, num(body, "conflicts"));
        assertEquals(4, num(body, "skipped"));
        List conflicts = (List) body.get("conflictDetails");
        assertEquals(1, conflicts.size());
        Map c = (Map) conflicts.get(0);
        assertEquals(4, num(c, "line"));
        assertEquals(conflict.bookingId, num(c, "bookingId"));
        assertEquals(610, num(c, "storedScore"));
        assertEquals(999, num(c, "submittedScore"));
        List errors = (List) body.get("errors");
        assertEquals(2, errors.size());
        assertEquals(5, num((Map) errors.get(0), "line"));
        assertEquals(7, num((Map) errors.get(1), "line"));

        assertEquals(777, ((Number) result(fresh.bookingId).get("Score")).intValue());
        assertEquals(333, ((Number) result(afterBadRow.bookingId).get("Score")).intValue());
        assertEquals("ATTENDED", booking(afterBadRow.bookingId).get("Status"));
        assertEquals(1, resultCount(dup.bookingId));
        assertEquals(conflictRowBefore, result(conflict.bookingId));
        assertEquals(1, auditCount(conflictResultId, "DUPLICATE_REJECTED"));
        assertEquals(1, uploadsWithFileName("NWTEST_MIXED_B.csv"));
        Map<String, Object> upload = jdbc.queryForMap("SELECT RowsRead, Imported, Duplicates, Conflicts, Errors "
                + "FROM dbo.ResultUpload WHERE FileName = 'NWTEST_MIXED_B.csv'");
        assertEquals(6, upload.get("RowsRead"));
        assertEquals(2, upload.get("Imported"));
        assertEquals(1, upload.get("Duplicates"));
        assertEquals(1, upload.get("Conflicts"));
        assertEquals(2, upload.get("Errors"));

        // replaying the same file reports the conflict again but does not audit it a second time
        ResponseEntity<Map> replay = upload("NWTEST_MIXED_B.csv", mixed);
        assertEquals(Boolean.TRUE, replay.getBody().get("alreadyUploaded"));
        assertEquals(1, num(replay.getBody(), "conflicts"));
        assertEquals(1, auditCount(conflictResultId, "DUPLICATE_REJECTED"));
        assertEquals(conflictRowBefore, result(conflict.bookingId));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void replayedFileNeverInsertsRowsThatFailedTheFirstTime() {
        Fixture ok = newBooking();
        Fixture later = newBooking();
        // first upload: 'later' has no booking yet on the file's session id, so its row fails
        String file = csv(ok.csvRow(500, "2026-11-10T07:00:00"),
                later.candidateNumber + ",999999,400,2026-11-10T07:00:00");
        ResponseEntity<Map> first = upload("NWTEST_REPLAY_ERR.csv", file);
        assertEquals(1, num(first.getBody(), "imported"));
        assertEquals(1, ((List) first.getBody().get("errors")).size());

        ResponseEntity<Map> again = upload("NWTEST_REPLAY_ERR.csv", file);

        assertEquals(0, num(again.getBody(), "imported"));
        assertEquals(1, num(again.getBody(), "duplicates"));
        assertEquals(1, ((List) again.getBody().get("errors")).size());
        assertEquals(1, resultsFromFile("NWTEST_REPLAY_ERR.csv"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void replayAuditsAConflictThatWasNotAuditedOnTheFirstUpload() {
        Fixture f = newBooking();
        jdbc.update("UPDATE dbo.Booking SET Status = 'CANCELLED' WHERE BookingId = ?", f.bookingId);
        String file = csv(f.csvRow(999, "2026-11-10T07:30:00"));
        ResponseEntity<Map> first = upload("NWTEST_LATE_BOOKING.csv", file);
        assertEquals(1, ((List) first.getBody().get("errors")).size());

        // the booking becomes active and gets a different score through the REST API
        jdbc.update("UPDATE dbo.Booking SET Status = 'BOOKED' WHERE BookingId = ?", f.bookingId);
        Map<String, Object> post = new java.util.HashMap<String, Object>();
        post.put("bookingId", f.bookingId);
        post.put("score", 610);
        post.put("scoredAt", "2026-11-10T07:30:00");
        assertEquals(HttpStatus.CREATED, rest.postForEntity("/api/v1/results", post, Map.class).getStatusCode());
        int resultId = ((Number) result(f.bookingId).get("ResultId")).intValue();

        ResponseEntity<Map> replay = upload("NWTEST_LATE_BOOKING.csv", file);
        assertEquals(Boolean.TRUE, replay.getBody().get("alreadyUploaded"));
        assertEquals(1, num(replay.getBody(), "conflicts"));
        assertEquals(1, auditCount(resultId, "DUPLICATE_REJECTED"));

        upload("NWTEST_LATE_BOOKING.csv", file);
        assertEquals(1, auditCount(resultId, "DUPLICATE_REJECTED"));
        assertEquals(610, ((Number) result(f.bookingId).get("Score")).intValue());
    }

    @Test
    public void concurrentMergeMovesRowsFromDuplicatesThenErrorsWithoutGoingNegative() {
        String sha = "ab" + String.format("%062d", System.nanoTime());
        // recorded by the concurrent winner: 1 pre-existing duplicate, 2 rows whose booking was missing at the time
        jdbc.update("INSERT INTO dbo.ResultUpload (FileName, Sha256, RowsRead, Imported, Duplicates, Conflicts, Errors, "
                + "Actor) VALUES ('NWTEST_MERGE.csv', ?, 4, 0, 1, 1, 2, 'test')", sha);

        ReflectionTestUtils.invokeMethod(importer, "mergeIntoRecordedUpload", sha, 2);

        Map<String, Object> u = jdbc.queryForMap(
                "SELECT RowsRead, Imported, Duplicates, Conflicts, Errors FROM dbo.ResultUpload WHERE Sha256 = ?", sha);
        assertEquals(2, u.get("Imported"));
        assertEquals(0, u.get("Duplicates"));
        assertEquals(1, u.get("Conflicts"));
        assertEquals(1, u.get("Errors"));
        assertEquals(4, ((Integer) u.get("Imported")) + ((Integer) u.get("Duplicates"))
                + ((Integer) u.get("Conflicts")) + ((Integer) u.get("Errors")));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void sameRowsInADifferentFileAreRowLevelDuplicates() {
        Fixture a = newBooking();
        upload("NWTEST_CRLF.csv", csv(a.csvRow(688, "2026-11-10T06:00:00")));

        // same rows, LF line endings and another name: different SHA-256, still nothing inserted
        ResponseEntity<Map> r = upload("NWTEST_LF.csv", HEADER + "\n" + a.csvRow(688, "2026-11-10T06:00:00") + "\n");

        assertEquals(Boolean.FALSE, r.getBody().get("alreadyUploaded"));
        assertEquals(0, num(r.getBody(), "imported"));
        assertEquals(1, num(r.getBody(), "duplicates"));
        assertEquals(1, resultCount(a.bookingId));
        assertEquals(0, resultsFromFile("NWTEST_LF.csv"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void concurrentIdenticalUploadsImportEachRowOnce() throws Exception {
        final List<Fixture> fixtures = new ArrayList<Fixture>();
        List<String> rows = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            Fixture f = newBooking();
            fixtures.add(f);
            rows.add(f.csvRow(500 + i, "2026-11-10T05:00:00"));
        }
        final String file = csv(rows.toArray(new String[0]));
        final CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<ResponseEntity<Map>>> futures = new ArrayList<Future<ResponseEntity<Map>>>();
        for (int i = 0; i < 4; i++) {
            futures.add(pool.submit(new Callable<ResponseEntity<Map>>() {
                @Override
                public ResponseEntity<Map> call() throws Exception {
                    go.await();
                    return upload("NWTEST_CONCURRENT.csv", file);
                }
            }));
        }
        go.countDown();
        int imported = 0;
        for (Future<ResponseEntity<Map>> fu : futures) {
            ResponseEntity<Map> r = fu.get(60, TimeUnit.SECONDS);
            assertEquals(String.valueOf(r.getBody()), HttpStatus.OK, r.getStatusCode());
            assertEquals(0, num(r.getBody(), "conflicts"));
            assertTrue(((List) r.getBody().get("errors")).isEmpty());
            imported += num(r.getBody(), "imported");
        }
        pool.shutdown();
        assertEquals(5, imported);
        for (Fixture f : fixtures) {
            assertEquals(1, resultCount(f.bookingId));
        }
        assertEquals(1, uploadsWithFileName("NWTEST_CONCURRENT.csv"));
        Map<String, Object> stored = jdbc.queryForMap(
                "SELECT Imported, Duplicates FROM dbo.ResultUpload WHERE FileName = 'NWTEST_CONCURRENT.csv'");
        assertEquals(5, stored.get("Imported"));
        assertEquals(0, stored.get("Duplicates"));
        assertFalse(fixtures.isEmpty());
    }
}
