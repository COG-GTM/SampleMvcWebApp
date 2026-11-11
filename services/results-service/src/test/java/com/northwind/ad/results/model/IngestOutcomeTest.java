package com.northwind.ad.results.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;

import org.junit.Test;

import com.northwind.ad.results.service.CsvRowParser;

public class IngestOutcomeTest {

    private final StoredResult stored = new StoredResult(10, 7, 612, true,
            CsvRowParser.parseDate("2026-09-10T12:30:00"));

    @Test
    public void createdAndDuplicateBodies() {
        Map<String, Object> created = IngestOutcome.created(11, 8, 500, false).toBody();
        assertEquals("{status=CREATED, resultId=11, bookingId=8, score=500, passed=false}", created.toString());
        Map<String, Object> dup = IngestOutcome.duplicate(stored).toBody();
        assertEquals("{status=DUPLICATE, resultId=10, bookingId=7, score=612, passed=true}", dup.toString());
    }

    @Test
    public void conflictDetailsCarryBothScores() {
        IngestOutcome c = IngestOutcome.conflict(stored, 640, CsvRowParser.parseDate("2026-09-10T12:31:00"));
        Map<String, Object> d = c.conflictDetails();
        assertEquals(7, d.get("bookingId"));
        assertEquals(10, d.get("resultId"));
        assertEquals(612, d.get("storedScore"));
        assertEquals(640, d.get("submittedScore"));
        assertEquals("2026-09-10T12:30:00", d.get("storedScoredAt"));
        assertEquals("2026-09-10T12:31:00", d.get("submittedScoredAt"));
        assertEquals("A different result already exists for booking 7: stored score 612, submitted score 640",
                c.conflictMessage());
    }

    @Test
    public void conflictWithoutSubmittedScoredAt() {
        IngestOutcome c = IngestOutcome.conflict(stored, 640, null);
        assertNull(c.conflictDetails().get("submittedScoredAt"));
    }
}
