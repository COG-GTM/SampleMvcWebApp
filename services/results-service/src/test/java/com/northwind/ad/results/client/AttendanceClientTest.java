package com.northwind.ad.results.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.northwind.ad.results.web.ApiException;

public class AttendanceClientTest {

    private MockRestServiceServer server;
    private AttendanceClient client;

    @Before
    public void setUp() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.createServer(rest);
        client = new AttendanceClient(rest, "http://gateway:8000");
    }

    @Test
    public void recordAttendance_postsToSchedulingWithTheLegacyBookingId() {
        server.expect(requestTo("http://gateway:8000/api/v2/bookings/1234/attendance")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"bookingId\":1234,\"status\":\"ATTENDED\"}", MediaType.APPLICATION_JSON));
        assertEquals("ATTENDED", client.recordAttendance(1234));
        server.verify();
    }

    @Test
    public void notActiveInScheduling_isAConflictWithTheSameCode() {
        server.expect(requestTo("http://gateway:8000/api/v2/bookings/9/attendance")).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"BOOKING_NOT_ACTIVE\",\"message\":\"Booking 9 is CANCELLED\"}"));
        try {
            client.recordAttendance(9);
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals("BOOKING_NOT_ACTIVE", e.getError());
            assertEquals(HttpStatus.CONFLICT, e.getStatus());
        }
    }
}
