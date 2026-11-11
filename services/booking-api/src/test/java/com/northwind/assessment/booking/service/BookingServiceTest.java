package com.northwind.assessment.booking.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.assessment.booking.client.SchedulingClient;
import com.northwind.assessment.booking.client.SchedulingException;
import com.northwind.assessment.booking.dao.BookingDao;
import com.northwind.assessment.booking.dao.CandidateDao;
import com.northwind.assessment.booking.dao.SessionDao;
import com.northwind.assessment.booking.model.BookingInfo;
import com.northwind.assessment.booking.model.BookingRequest;
import com.northwind.assessment.booking.model.BookingResponse;
import com.northwind.assessment.booking.model.Candidate;
import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.web.ApiException;

/** Partner path: pre-checks keep legacy order/messages, every write is one scheduling-service call. */
public class BookingServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private BookingService service;
    private BookingDao bookingDao;
    private SessionDao sessionDao;
    private CandidateDao candidateDao;
    private RetakeEligibilityService eligibility;
    private SchedulingClient scheduling;

    @Before
    public void setUp() throws Exception {
        service = new BookingService();
        bookingDao = mock(BookingDao.class);
        sessionDao = mock(SessionDao.class);
        candidateDao = mock(CandidateDao.class);
        eligibility = mock(RetakeEligibilityService.class);
        scheduling = mock(SchedulingClient.class);
        ReflectionTestUtils.setField(service, "bookingDao", bookingDao);
        ReflectionTestUtils.setField(service, "sessionDao", sessionDao);
        ReflectionTestUtils.setField(service, "candidateDao", candidateDao);
        ReflectionTestUtils.setField(service, "retakeEligibilityService", eligibility);
        ReflectionTestUtils.setField(service, "scheduling", scheduling);
        ReflectionTestUtils.setField(service, "channel", "PARTNER");
        ReflectionTestUtils.setField(service, "auditActor", "booking-api");

        Candidate c = new Candidate();
        c.setCandidateId(9);
        c.setCandidateNumber("C-9");
        when(candidateDao.findByNumber("C-9")).thenReturn(c);
        when(sessionDao.findRaw(7)).thenReturn(session(7, 1, "OPEN"));
        when(sessionDao.findRaw(8)).thenReturn(session(8, 1, "OPEN"));
        when(sessionDao.findRaw(20)).thenReturn(session(20, 2, "OPEN"));
        when(eligibility.check(anyInt(), anyInt(), any(Date.class))).thenReturn(new EligibilityResult(true, null, null));
    }

    private JsonNode session(int id, int examId, String status) throws Exception {
        return json.readTree("{\"sessionId\":" + id + ",\"examId\":" + examId + ",\"examCode\":\"AZ-104\",\"status\":\""
                + status + "\",\"accommodationCapacity\":1,\"startsAt\":\"2030-01-01T09:00:00\"}");
    }

    private BookingRequest request(String acc) {
        BookingRequest r = new BookingRequest();
        r.setSessionId(7);
        r.setCandidateNumber("C-9");
        r.setAccommodationCode(acc);
        r.setExternalRef("REF-1");
        return r;
    }

    private BookingInfo booking(int id, int sessionId, String status) {
        BookingInfo b = new BookingInfo();
        b.setBookingId(id);
        b.setSessionId(sessionId);
        b.setCandidateNumber("C-9");
        b.setStatus(status);
        return b;
    }

    private void expectConflict(String error, String message) {
        try {
            service.book(request(null));
            fail();
        } catch (ApiException e) {
            assertEquals(error, e.getError());
            assertEquals(message, e.getMessage());
        }
    }

    @Test
    public void bookIsOneServiceCallOnThePartnerChannel() throws Exception {
        when(scheduling.book(7, 9, "ET25", "PARTNER", "REF-1", "booking-api"))
                .thenReturn(json.readTree("{\"bookingId\":55,\"status\":\"BOOKED\"}"));
        BookingResponse r = service.book(request(" et25 "));
        assertEquals(55, r.getBookingId());
        assertEquals("BOOKED", r.getStatus());
    }

    @Test
    public void closedSessionIsRejectedBeforeAnyWrite() throws Exception {
        when(sessionDao.findRaw(7)).thenReturn(session(7, 1, "CLOSED"));
        expectConflict("SESSION_NOT_OPEN", "Session 7 is not open for booking");
        verify(scheduling, never()).book(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void ineligibleCandidateIsRejectedBeforeAnyWrite() {
        when(eligibility.check(anyInt(), anyInt(), any(Date.class)))
                .thenReturn(new EligibilityResult(false, "COOLDOWN", "2030-02-01"));
        expectConflict("NOT_ELIGIBLE",
                "Candidate C-9 is not eligible to book AZ-104 (COOLDOWN), earliest retake date 2030-02-01");
    }

    @Test
    public void serviceRejectionAfterPrecheckKeepsThePartnerMessage() {
        when(scheduling.book(anyInt(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new SchedulingException(409, "DUPLICATE_BOOKING",
                        "Candidate 9 already has an active booking on session 7.", 2));
        expectConflict("DUPLICATE_BOOKING", "Candidate C-9 already has an active booking on session 7");
    }

    @Test
    public void cancelOfCancelledBookingKeepsThePartnerMessage() {
        when(bookingDao.findById(5)).thenReturn(booking(5, 7, "CANCELLED"));
        try {
            service.cancel(5, null);
            fail();
        } catch (ApiException e) {
            assertEquals("NOT_CANCELLABLE", e.getError());
            assertEquals("Booking 5 is CANCELLED", e.getMessage());
        }
        verify(scheduling, never()).cancel(anyInt(), anyString(), anyString());
    }

    @Test
    public void cancelGoesThroughTheServiceWithDefaultReason() {
        when(bookingDao.findById(5)).thenReturn(booking(5, 7, "BOOKED"));
        service.cancel(5, " ");
        verify(scheduling).cancel(5, "Cancelled by partner", "booking-api");
    }

    @Test
    public void rescheduleIsOnePartnerServiceCall() throws Exception {
        when(bookingDao.findById(5)).thenReturn(booking(5, 7, "BOOKED"));
        when(scheduling.reschedule(5, 8, "PARTNER", "booking-api"))
                .thenReturn(json.readTree("{\"bookingId\":56,\"status\":\"WAITLISTED\",\"cancelledBookingId\":5}"));
        BookingResponse r = service.reschedule(5, 8);
        assertEquals(56, r.getBookingId());
        assertEquals("WAITLISTED", r.getStatus());
        verify(scheduling, never()).cancel(anyInt(), anyString(), anyString());
    }

    @Test
    public void rescheduleToDifferentExamKeepsThePartnerMessage() {
        when(bookingDao.findById(5)).thenReturn(booking(5, 7, "BOOKED"));
        try {
            service.reschedule(5, 20);
            fail();
        } catch (ApiException e) {
            assertEquals("DIFFERENT_EXAM", e.getError());
            assertEquals("Booking 5 cannot be moved to session 20 (different exam)", e.getMessage());
        }
        verify(scheduling, never()).reschedule(anyInt(), anyInt(), anyString(), anyString());
    }
}
