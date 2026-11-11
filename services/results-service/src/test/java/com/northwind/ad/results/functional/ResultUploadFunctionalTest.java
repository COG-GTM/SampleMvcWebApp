package com.northwind.ad.results.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import com.northwind.ad.results.service.FileHash;

/** AC3 + AC4 through HTTP multipart upload against SQL Server 2022. */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@SuppressWarnings({"rawtypes", "unchecked"})
public class ResultUploadFunctionalTest extends SqlServerTestSupport {

    private static final String HEADER = "candidateNumber,sessionId,score,scoredAt\n";

    private static String row(Map<String, Object> booking, int score, String scoredAt) {
        return booking.get("CandidateNumber") + "," + id(booking, "SessionId") + "," + score + "," + scoredAt + "\n";
    }

    @Test
    public void ac3ReuploadOfIdenticalFileIsAllDuplicatesAndInsertsNothing() throws Exception {
        List<Map<String, Object>> bookings = claimFreshBookings(3);
        StringBuilder csv = new StringBuilder(HEADER);
        for (int i = 0; i < bookings.size(); i++) {
            csv.append(row(bookings.get(i), 600 + i, "2026-11-05T14:0" + i + ":00"));
        }
        String fileName = "NW_SCORES_AC3_" + new Date().getTime() + ".csv";

        ResponseEntity<Map> first = upload(fileName, csv.toString());
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(3, ((Number) first.getBody().get("imported")).intValue());
        assertEquals(Boolean.FALSE, first.getBody().get("previouslyUploaded"));
        int totalAfterFirst = totalResults();

        ResponseEntity<Map> again = upload(fileName, csv.toString());

        Map body = again.getBody();
        assertEquals(HttpStatus.OK, again.getStatusCode());
        assertEquals(3, ((Number) body.get("rowsRead")).intValue());
        assertEquals(0, ((Number) body.get("imported")).intValue());
        assertEquals(3, ((Number) body.get("duplicates")).intValue());
        assertEquals(0, ((Number) body.get("conflicts")).intValue());
        assertEquals(0, ((Number) body.get("skipped")).intValue());
        assertTrue(((List) body.get("errors")).isEmpty());
        assertTrue(((List) body.get("conflictRows")).isEmpty());
        assertEquals(Boolean.TRUE, body.get("previouslyUploaded"));
        assertEquals(first.getBody().get("uploadId"), body.get("previousUploadId"));
        String sha = FileHash.sha256Hex(csv.toString().getBytes("UTF-8"));
        assertEquals(sha, body.get("sha256"));
        assertEquals(totalAfterFirst, totalResults());

        List<Map<String, Object>> uploads = jdbc.queryForList(
                "SELECT UploadId, FileName, Imported, Duplicates, Conflicts, Errors FROM dbo.ResultUpload WHERE Sha256 = ? ORDER BY UploadId", sha);
        assertEquals(2, uploads.size());
        assertEquals(fileName, uploads.get(0).get("FileName"));
        assertEquals(3, ((Number) uploads.get(0).get("Imported")).intValue());
        assertEquals(0, ((Number) uploads.get(1).get("Imported")).intValue());
        assertEquals(3, ((Number) uploads.get(1).get("Duplicates")).intValue());
        assertEquals(0, ((Number) uploads.get(1).get("Conflicts")).intValue());
        assertEquals(((Number) body.get("uploadId")).intValue(), ((Number) uploads.get(1).get("UploadId")).intValue());
    }

    @Test
    public void ac3ReuploadOfSeededScoresIsAllDuplicates() throws Exception {
        List<Map<String, Object>> seeded = claimSeededResults(2);
        StringBuilder csv = new StringBuilder(HEADER);
        for (Map<String, Object> s : seeded) {
            csv.append(row(s, id(s, "Score"), (String) s.get("ScoredAt")));
        }
        int before = totalResults();

        ResponseEntity<Map> r = upload("NW_SCORES_SEEDED_REPLAY.csv", csv.toString());

        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(0, ((Number) r.getBody().get("imported")).intValue());
        assertEquals(2, ((Number) r.getBody().get("duplicates")).intValue());
        assertEquals(0, ((Number) r.getBody().get("conflicts")).intValue());
        assertEquals(before, totalResults());
    }

    @Test
    public void ac4MixedFileImportsNewSkipsDuplicatesReportsConflictsAndKeepsGoodRows() throws Exception {
        List<Map<String, Object>> fresh = claimFreshBookings(2);
        List<Map<String, Object>> seeded = claimSeededResults(2);
        Map<String, Object> dup = seeded.get(0);
        Map<String, Object> conflict = seeded.get(1);
        int conflictStored = id(conflict, "Score");
        int conflictSubmitted = conflictStored + 7;
        String csv = HEADER
                + row(fresh.get(0), 701, "2026-11-06T09:00:00")                        // line 2 new
                + row(dup, id(dup, "Score"), (String) dup.get("ScoredAt"))             // line 3 duplicate
                + "NW-BROKEN,not-a-session,abc\n"                                      // line 4 malformed
                + row(conflict, conflictSubmitted, (String) conflict.get("ScoredAt"))  // line 5 conflict
                + row(fresh.get(1), 702, "2026-11-06T09:05:00");                       // line 6 new, after the bad rows
        Map<String, Object> dupBefore = resultRow(id(dup, "BookingId"));
        Map<String, Object> conflictBefore = resultRow(id(conflict, "BookingId"));
        int before = totalResults();

        ResponseEntity<Map> r = upload("NW_SCORES_MIXED.csv", csv);

        Map body = r.getBody();
        assertEquals(HttpStatus.OK, r.getStatusCode());
        assertEquals(5, ((Number) body.get("rowsRead")).intValue());
        assertEquals(2, ((Number) body.get("imported")).intValue());
        assertEquals(1, ((Number) body.get("duplicates")).intValue());
        assertEquals(1, ((Number) body.get("conflicts")).intValue());
        assertEquals(1, ((Number) body.get("skipped")).intValue());
        List<Map<String, Object>> errors = (List<Map<String, Object>>) body.get("errors");
        assertEquals(1, errors.size());
        assertEquals(4, ((Number) errors.get(0).get("line")).intValue());
        List<Map<String, Object>> conflicts = (List<Map<String, Object>>) body.get("conflictRows");
        assertEquals(1, conflicts.size());
        assertEquals(5, ((Number) conflicts.get(0).get("line")).intValue());
        assertEquals(id(conflict, "BookingId"), ((Number) conflicts.get(0).get("bookingId")).intValue());
        assertEquals(conflictStored, ((Number) conflicts.get(0).get("storedScore")).intValue());
        assertEquals(conflictSubmitted, ((Number) conflicts.get(0).get("submittedScore")).intValue());
        assertEquals(Boolean.FALSE, body.get("previouslyUploaded"));

        assertEquals(before + 2, totalResults());
        for (int i = 0; i < 2; i++) {
            Map<String, Object> stored = resultRow(id(fresh.get(i), "BookingId"));
            assertNotNull(stored);
            assertEquals(701 + i, ((Number) stored.get("Score")).intValue());
            assertEquals("NW_SCORES_MIXED.csv", stored.get("SourceFile"));
            assertEquals("ATTENDED", bookingRow(id(fresh.get(i), "BookingId")).get("Status"));
        }
        assertEquals(dupBefore, resultRow(id(dup, "BookingId")));
        assertEquals(conflictBefore, resultRow(id(conflict, "BookingId")));
        assertEquals(0, auditCount(id(dup, "ResultId"), "DUPLICATE_REJECTED"));
        List<String> audit = auditDetails(id(conflict, "ResultId"), "DUPLICATE_REJECTED");
        assertEquals(1, audit.size());
        assertTrue(audit.get(0), audit.get(0).contains("file=NW_SCORES_MIXED.csv") && audit.get(0).contains("line=5"));

        Map<String, Object> upload = jdbc.queryForMap(
                "SELECT RowsRead, Imported, Duplicates, Conflicts, Errors FROM dbo.ResultUpload WHERE UploadId = ?",
                ((Number) body.get("uploadId")).intValue());
        assertEquals(5, ((Number) upload.get("RowsRead")).intValue());
        assertEquals(2, ((Number) upload.get("Imported")).intValue());
        assertEquals(1, ((Number) upload.get("Duplicates")).intValue());
        assertEquals(1, ((Number) upload.get("Conflicts")).intValue());
        assertEquals(1, ((Number) upload.get("Errors")).intValue());
        assertFalse(errors.toString().contains("DUPLICATE"));
    }
}
