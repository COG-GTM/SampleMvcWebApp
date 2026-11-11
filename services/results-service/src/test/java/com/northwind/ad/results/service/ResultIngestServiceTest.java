package com.northwind.ad.results.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMapOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import com.northwind.ad.results.model.IngestOutcome;
import com.northwind.ad.results.model.StoredResult;
import com.northwind.ad.results.web.ApiException;

@RunWith(MockitoJUnitRunner.class)
public class ResultIngestServiceTest {

    private static final int BOOKING = 7;
    private static final Date AT = CsvRowParser.parseDate("2026-09-10T12:30:00");

    @Mock
    private ResultStore store;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private ResultIngestService service;

    private Map<String, Object> booking;

    @Before
    public void setUp() {
        booking = new HashMap<String, Object>();
        booking.put("BookingId", BOOKING);
        booking.put("Status", "BOOKED");
        booking.put("PassingScore", 600);
        when(store.findBooking(BOOKING)).thenReturn(booking);
    }

    private static Map<String, Object> anyBooking() {
        return anyMapOf(String.class, Object.class);
    }

    @Test
    public void newResultIsCreated() {
        when(store.insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class))).thenReturn(42);
        IngestOutcome o = service.ingest(BOOKING, 612, AT, null);
        assertEquals(IngestOutcome.Status.CREATED, o.getStatus());
        assertEquals(42, o.getResultId());
        verify(store).insertResult(booking, 612, true, AT, null);
        verify(store, never()).auditDuplicateRejected(any(IngestOutcome.class), any(String.class), any(Integer.class));
    }

    @Test
    public void missingScoredAtIsStoredAsNow() {
        when(store.insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class))).thenReturn(42);
        long before = System.currentTimeMillis();
        service.ingest(BOOKING, 500, null, null);
        ArgumentCaptor<Date> at = ArgumentCaptor.forClass(Date.class);
        verify(store).insertResult(eq(booking), eq(500), eq(false), at.capture(), (String) isNull());
        assertNotNull(at.getValue());
        assertEquals(true, at.getValue().getTime() >= before);
    }

    @Test
    public void identicalReplayIsDuplicateAndWritesNothing() {
        booking.put("Status", "ATTENDED");
        when(store.findByBooking(BOOKING)).thenReturn(new StoredResult(42, BOOKING, 612, true, AT));
        IngestOutcome o = service.ingest(BOOKING, 612, AT, null);
        assertEquals(IngestOutcome.Status.DUPLICATE, o.getStatus());
        assertEquals(42, o.getResultId());
        verify(store, never()).insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class));
        verify(store, never()).auditDuplicateRejected(any(IngestOutcome.class), any(String.class), any(Integer.class));
    }

    @Test
    public void differentScoreIsConflictAndAudited() {
        when(store.findByBooking(BOOKING)).thenReturn(new StoredResult(42, BOOKING, 612, true, AT));
        IngestOutcome o = service.ingest(BOOKING, 640, AT, "f.csv", 4);
        assertEquals(IngestOutcome.Status.CONFLICT, o.getStatus());
        assertEquals(612, o.getScore());
        assertEquals(Integer.valueOf(640), o.getSubmittedScore());
        verify(store).auditDuplicateRejected(o, "f.csv", 4);
        verify(store, never()).insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class));
    }

    @Test
    public void existingResultIsClassifiedEvenWhenBookingNoLongerActive() {
        booking.put("Status", "CANCELLED");
        when(store.findByBooking(BOOKING)).thenReturn(new StoredResult(42, BOOKING, 612, true, AT));
        assertEquals(IngestOutcome.Status.DUPLICATE, service.ingest(BOOKING, 612, AT, null).getStatus());
    }

    @Test
    public void lostInsertRaceWithSameResultIsDuplicate() {
        when(store.findByBooking(BOOKING)).thenReturn(null, new StoredResult(43, BOOKING, 612, true, AT));
        when(store.insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class)))
                .thenThrow(new DuplicateKeyException("UQ_Result_Booking"));
        IngestOutcome o = service.ingest(BOOKING, 612, AT, null);
        assertEquals(IngestOutcome.Status.DUPLICATE, o.getStatus());
        assertEquals(43, o.getResultId());
    }

    @Test
    public void lostInsertRaceWithDifferentResultIsConflict() {
        when(store.findByBooking(BOOKING)).thenReturn(null, new StoredResult(43, BOOKING, 700, true, AT));
        when(store.insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class)))
                .thenThrow(new DataIntegrityViolationException("Violation of UNIQUE KEY constraint 'UQ_Result_Booking'"));
        IngestOutcome o = service.ingest(BOOKING, 612, AT, null);
        assertEquals(IngestOutcome.Status.CONFLICT, o.getStatus());
        verify(store).auditDuplicateRejected(o, null, null);
    }

    @Test
    public void integrityViolationWithoutExistingRowIsRethrown() {
        DataIntegrityViolationException fk = new DataIntegrityViolationException("FK");
        when(store.insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class))).thenThrow(fk);
        try {
            service.ingest(BOOKING, 612, AT, null);
            fail("expected rethrow");
        } catch (DataIntegrityViolationException e) {
            assertSame(fk, e);
        }
    }

    @Test
    public void deadlockIsRetriedOnce() {
        when(store.insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class)))
                .thenThrow(new DeadlockLoserDataAccessException("deadlock", null)).thenReturn(44);
        IngestOutcome o = service.ingest(BOOKING, 612, AT, null);
        assertEquals(IngestOutcome.Status.CREATED, o.getStatus());
        verify(store, times(2)).insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class));
    }

    @Test
    public void deadlockAfterConcurrentInsertIsClassified() {
        when(store.findByBooking(BOOKING)).thenReturn(null, new StoredResult(43, BOOKING, 612, true, AT));
        when(store.insertResult(anyBooking(), anyInt(), anyBoolean(), any(Date.class), any(String.class)))
                .thenThrow(new DeadlockLoserDataAccessException("deadlock", null));
        assertEquals(IngestOutcome.Status.DUPLICATE, service.ingest(BOOKING, 612, AT, null).getStatus());
    }

    @Test
    public void unknownBookingIs404() {
        when(store.findBooking(999)).thenReturn(null);
        try {
            service.ingest(999, 612, AT, null);
            fail();
        } catch (ApiException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        }
    }

    @Test
    public void cancelledBookingWithoutResultIs409() {
        booking.put("Status", "CANCELLED");
        try {
            service.ingest(BOOKING, 612, AT, null);
            fail();
        } catch (ApiException e) {
            assertEquals("BOOKING_NOT_ACTIVE", e.getError());
        }
    }

    @Test
    public void outOfRangeScoreIs400() {
        try {
            service.ingest(BOOKING, 1001, AT, null);
            fail();
        } catch (ApiException e) {
            assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        }
    }
}
