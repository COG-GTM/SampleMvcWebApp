package com.northwind.ad.results.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
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

    @Mock
    private SchedulingClient scheduling;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager txManager;

    @InjectMocks
    private ResultIngestService service;

    private final CsvRow row = new CsvRowParser().parse("NW00000901,154,640,2026-11-10T09:00:00", 2);

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        BookingView booking = new BookingView();
        booking.setBookingId(42);
        booking.setCandidateId(7);
        booking.setSessionId(154);
        booking.setStatus("BOOKED");
        booking.setExamId(3);
        booking.setSessionStartsAt("2026-11-10T09:00:00");
        when(scheduling.getBooking(42)).thenReturn(booking);
        when(jdbc.queryForObject(startsWith("SELECT PassingScore"), eq(Integer.class), eq(3))).thenReturn(600);
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
        verify(scheduling, never()).markAttended(anyInt(), anyInt(), anyInt(), anyBoolean(), any(Date.class));
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
        verify(scheduling, never()).markAttended(anyInt(), anyInt(), anyInt(), anyBoolean(), any(Date.class));
    }
}
