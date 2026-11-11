package com.northwind.ad.results.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Date;

import org.junit.Test;

import com.northwind.ad.results.model.StoredResult;

public class ResultClassifierTest {

    private static final Date STORED_AT = CsvRowParser.parseDate("2026-09-10T12:30:00");
    private final StoredResult stored = new StoredResult(10, 7, 612, true, STORED_AT);

    @Test
    public void sameScoreAndScoredAtIsDuplicate() {
        assertTrue(ResultClassifier.isDuplicate(stored, 612, CsvRowParser.parseDate("2026-09-10T12:30:00")));
    }

    @Test
    public void differentScoreIsConflict() {
        assertFalse(ResultClassifier.isDuplicate(stored, 613, STORED_AT));
    }

    @Test
    public void differentScoredAtIsConflict() {
        assertFalse(ResultClassifier.isDuplicate(stored, 612, CsvRowParser.parseDate("2026-09-10T12:30:01")));
        assertFalse(ResultClassifier.isDuplicate(stored, 612, CsvRowParser.parseDate("2026-09-10")));
    }

    @Test
    public void scoredAtComparedAtSecondPrecision() {
        assertTrue(ResultClassifier.isDuplicate(stored, 612, new Date(STORED_AT.getTime() + 400)));
        assertTrue(ResultClassifier.isDuplicate(stored, 612, new Date(STORED_AT.getTime() + 998)));
    }

    @Test
    public void scoredAtUsesDatetimeRounding() {
        // DATETIME rounds 12:29:59.999 up to 12:30:00.000, so that is the same second as the stored value
        assertTrue(ResultClassifier.isDuplicate(stored, 612, new Date(STORED_AT.getTime() - 1)));
        // and 12:30:00.999 is stored as 12:30:01.000
        assertFalse(ResultClassifier.isDuplicate(stored, 612, new Date(STORED_AT.getTime() + 999)));
        // a stored value already rounded by the column (e.g. .997) still matches its own second
        StoredResult rounded = new StoredResult(10, 7, 612, true, new Date(STORED_AT.getTime() + 997));
        assertTrue(ResultClassifier.isDuplicate(rounded, 612, new Date(STORED_AT.getTime() + 996)));
    }

    @Test
    public void datetimeSecondRoundsLikeSqlServer() {
        long base = STORED_AT.getTime();
        assertEquals(ResultClassifier.datetimeSecond(new Date(base)), ResultClassifier.datetimeSecond(new Date(base + 998)));
        assertEquals(ResultClassifier.datetimeSecond(new Date(base)) + 1, ResultClassifier.datetimeSecond(new Date(base + 999)));
    }

    @Test
    public void missingScoredAtComparesScoreOnly() {
        assertTrue(ResultClassifier.isDuplicate(stored, 612, null));
        assertFalse(ResultClassifier.isDuplicate(stored, 500, null));
    }
}
