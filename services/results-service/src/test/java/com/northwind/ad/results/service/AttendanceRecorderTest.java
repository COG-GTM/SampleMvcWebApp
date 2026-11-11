package com.northwind.ad.results.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.Test;
import org.mockito.Matchers;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.northwind.ad.results.web.ApiException;

public class AttendanceRecorderTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RestTemplate rest = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.createServer(rest);

    @Test
    public void postsAttendanceToSchedulingServiceAndNeverWritesBooking() {
        server.expect(requestTo("http://sched:8090/api/v2/bookings/50000007/attendance"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
        new AttendanceRecorder(jdbc, "http://sched:8090/", rest).recordAttended(50000007);
        server.verify();
        verifyZeroInteractions(jdbc);
    }

    @Test
    public void mapsSchedulingConflictToBookingNotActive() {
        server.expect(requestTo("http://sched:8090/api/v2/bookings/12/attendance"))
                .andRespond(withStatus(HttpStatus.CONFLICT));
        assertError(new AttendanceRecorder(jdbc, "http://sched:8090", rest), 12, "BOOKING_NOT_ACTIVE");
    }

    @Test
    public void mapsSchedulingNotFoundAndOutage() {
        server.expect(requestTo("http://sched:8090/api/v2/bookings/13/attendance"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://sched:8090/api/v2/bookings/14/attendance"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        AttendanceRecorder r = new AttendanceRecorder(jdbc, "http://sched:8090", rest);
        assertError(r, 13, "BOOKING_NOT_FOUND");
        assertError(r, 14, "SCHEDULING_UNAVAILABLE");
    }

    @Test
    public void emptyBaseUrlKeepsLegacyDirectUpdate() {
        new AttendanceRecorder(jdbc, "", rest).recordAttended(42);
        verify(jdbc).update(Matchers.startsWith("UPDATE dbo.Booking SET Status = 'ATTENDED'"), Matchers.eq(42));
    }

    private static void assertError(AttendanceRecorder r, int bookingId, String error) {
        try {
            r.recordAttended(bookingId);
            fail("expected " + error);
        } catch (ApiException e) {
            assertEquals(error, e.getError());
        }
    }
}
