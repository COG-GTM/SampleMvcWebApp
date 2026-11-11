package com.northwind.ad.results.it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

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
 * scheduling-service down during ingest (docs/to-be/adr-001-retake-eligibility-and-attendance.md): the row
 * records nothing (no Result, booking stays BOOKED), other rows still commit (HB-19), and the file is not
 * registered so re-sending the same bytes imports the failed rows.
 */
public class SchedulingUnavailableTest extends AbstractResultsIT {

    @After
    public void up() {
        LegacyDbSchedulingStub.down = false;
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void restPostReturns503AndRecordsNothing() {
        Fixture f = newBooking();
        LegacyDbSchedulingStub.down = true;

        Map<String, Object> req = new java.util.HashMap<String, Object>();
        req.put("bookingId", f.bookingId);
        req.put("score", 700);
        ResponseEntity<Map> r = rest.postForEntity("/api/v1/results", req, Map.class);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertEquals("SCHEDULING_UNAVAILABLE", r.getBody().get("error"));
        assertEquals(0, resultCount(f.bookingId));
        assertEquals("BOOKED", booking(f.bookingId).get("Status"));
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void scoreFileDuringOutageIsNotRegisteredAndCanBeResent() {
        Fixture a = newBooking();
        Fixture b = newBooking();
        String file = "CandidateNumber,SessionId,Score,ScoredAt\n"
                + a.csvRow(710, "2026-11-10T09:00:00") + "\n" + b.csvRow(420, "2026-11-10T09:00:00") + "\n";
        LegacyDbSchedulingStub.down = true;

        ResponseEntity<Map> first = upload("NWTEST_OUTAGE.csv", file);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(0, ((Number) first.getBody().get("imported")).intValue());
        assertEquals(2, ((List) first.getBody().get("errors")).size());
        assertNull(first.getBody().get("uploadId"));
        assertEquals(0, resultCount(a.bookingId));
        assertEquals("BOOKED", booking(a.bookingId).get("Status"));

        LegacyDbSchedulingStub.down = false;
        ResponseEntity<Map> again = upload("NWTEST_OUTAGE.csv", file);

        assertFalse((Boolean) again.getBody().get("alreadyUploaded"));
        assertEquals(2, ((Number) again.getBody().get("imported")).intValue());
        assertEquals("ATTENDED", booking(a.bookingId).get("Status"));
        assertEquals("ATTENDED", booking(b.bookingId).get("Status"));
        assertEquals(1, resultCount(b.bookingId));
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
