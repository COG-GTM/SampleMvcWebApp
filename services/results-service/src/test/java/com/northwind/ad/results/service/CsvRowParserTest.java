package com.northwind.ad.results.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.northwind.ad.results.model.CsvRow;

public class CsvRowParserTest {

    private final CsvRowParser parser = new CsvRowParser();

    @Test
    public void parsesWellFormedRow() {
        CsvRow row = parser.parse("NW00000042,117,742,2016-03-14T16:30:00", 2);
        assertEquals("NW00000042", row.candidateNumber);
        assertEquals(117, row.sessionId);
        assertEquals(742, row.score);
        assertEquals(2, row.lineNumber);
    }

    @Test
    public void rejectsRowWithMissingField() {
        try {
            parser.parse("NW00000042,117,742", 3);
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("expected 4 fields"));
        }
    }
}
