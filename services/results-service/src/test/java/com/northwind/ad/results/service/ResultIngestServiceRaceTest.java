package com.northwind.ad.results.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.northwind.ad.results.model.CsvRow;

/**
 * The unique-key race on UQ_Result_Booking: the pre-check saw no row, the INSERT lost to a
 * concurrent ingest. Must map to DUPLICATE / CONFLICT, never bubble up as a 500.
 */
@RunWith(MockitoJUnitRunner.class)
public class ResultIngestServiceRaceTest {

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private ResultIngestService service;

    private final CsvRow row = new CsvRowParser().parse("NW00000901,154,640,2026-11-10T09:00:00", 2);

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        Map<String, Object> booking = new HashMap<String, Object>();
        booking.put("BookingId", 42);
        booking.put("CandidateId", 7);
        booking.put("SessionId", 154);
        booking.put("Status", "BOOKED");
        booking.put("ExamId", 3);
        booking.put("PassingScore", 600);
        when(jdbc.queryForList(startsWith("SELECT b.BookingId"), eq(42)))
                .thenReturn(Collections.singletonList(booking));
        when(jdbc.queryForObject(startsWith("SET NOCOUNT ON; INSERT INTO dbo.Result"), eq(Integer.class),
                (Object[]) anyVararg()))
                .thenThrow(new DuplicateKeyException("Violation of UNIQUE KEY constraint 'UQ_Result_Booking'"));
    }

    @SuppressWarnings("unchecked")
    private void storedByWinner(int score) {
        Map<String, Object> stored = new HashMap<String, Object>();
        stored.put("ResultId", 99);
        stored.put("Score", score);
        stored.put("ScoredAt", new Timestamp(row.scoredAt.getTime()));
        stored.put("Passed", score >= 600);
        List<Map<String, Object>> none = Collections.emptyList();
        when(jdbc.queryForList(startsWith("SELECT ResultId, Score"), eq(42)))
                .thenReturn(none, Collections.singletonList(stored));
    }

    @Test
    public void lostRaceWithIdenticalScoreIsDuplicate() {
        storedByWinner(640);

        IngestOutcome o = service.ingest(42, 640, row.scoredAt, null);

        assertEquals(IngestOutcome.Status.DUPLICATE, o.getStatus());
        assertEquals(Integer.valueOf(99), o.getResultId());
        verify(jdbc, never()).update(startsWith("UPDATE dbo.Booking"), any(Object[].class));
        verify(jdbc, never()).update(startsWith("INSERT INTO dbo.AuditLog"), (Object[]) anyVararg());
    }

    @Test
    public void lostRaceWithDifferentScoreIsConflictAndAudited() {
        storedByWinner(910);

        IngestOutcome o = service.ingest(42, 640, row.scoredAt, "f.csv");

        assertEquals(IngestOutcome.Status.CONFLICT, o.getStatus());
        assertEquals(Integer.valueOf(910), o.getStoredScore());
        verify(jdbc).update(startsWith("INSERT INTO dbo.AuditLog"), eq("Result"), eq(99), eq("DUPLICATE_REJECTED"),
                anyString(), any());
        verify(jdbc, never()).update(startsWith("UPDATE dbo.Booking"), any(Object[].class));
    }
}
