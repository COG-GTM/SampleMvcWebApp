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
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.northwind.assessment.booking.dao.CandidateDao;
import com.northwind.assessment.booking.dao.SessionDao;
import com.northwind.assessment.booking.model.BookingRequest;
import com.northwind.assessment.booking.model.Candidate;
import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.model.SessionInfo;
import com.northwind.assessment.booking.web.ApiException;

/** Retake rules are checked against the live dbo.Result before booking-api forwards to scheduling-service. */
public class BookingServiceRetakeTest {

    private final SchedulingClient scheduling = mock(SchedulingClient.class);
    private final RetakeEligibilityService retake = mock(RetakeEligibilityService.class);
    private final BookingService service = new BookingService();

    @Before
    public void setUp() {
        CandidateDao candidates = mock(CandidateDao.class);
        SessionDao sessions = mock(SessionDao.class);
        Candidate c = new Candidate();
        c.setCandidateId(7);
        c.setCandidateNumber("NW00000007");
        when(candidates.findByNumber("NW00000007")).thenReturn(c);
        when(sessions.findExamId(42)).thenReturn(3);
        when(sessions.findStartsAt(42)).thenReturn(new Date());
        SessionInfo s = new SessionInfo();
        s.setExamCode("CTA-NET1");
        when(sessions.findById(42)).thenReturn(s);
        ReflectionTestUtils.setField(service, "scheduling", scheduling);
        ReflectionTestUtils.setField(service, "candidateDao", candidates);
        ReflectionTestUtils.setField(service, "sessionDao", sessions);
        ReflectionTestUtils.setField(service, "retakeEligibilityService", retake);
        ReflectionTestUtils.setField(service, "channel", "PARTNER");
        ReflectionTestUtils.setField(service, "relayWaitMs", 0L);
    }

    @Test
    public void passedCandidate_isRefusedWithoutCallingScheduling() {
        when(retake.check(anyInt(), anyInt(), any(Date.class)))
                .thenReturn(new EligibilityResult(false, "ALREADY_PASSED", null));
        try {
            service.book(request());
            fail("expected NOT_ELIGIBLE");
        } catch (ApiException e) {
            assertEquals("NOT_ELIGIBLE", e.getError());
        }
        verify(scheduling, never()).book(anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void eligibleCandidate_isForwarded() {
        when(retake.check(anyInt(), anyInt(), any(Date.class))).thenReturn(new EligibilityResult(true, null, null));
        Map<String, Object> booked = new HashMap<String, Object>();
        booked.put("bookingId", 3600);
        booked.put("legacyBookingId", 3600);
        booked.put("status", "BOOKED");
        when(scheduling.book(42, "NW00000007", null, null, "PARTNER")).thenReturn(booked);
        assertEquals(3600, service.book(request()).getBookingId());
    }

    private static BookingRequest request() {
        BookingRequest r = new BookingRequest();
        r.setSessionId(42);
        r.setCandidateNumber("NW00000007");
        return r;
    }
}
