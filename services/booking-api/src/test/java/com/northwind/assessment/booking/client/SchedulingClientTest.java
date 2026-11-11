package com.northwind.assessment.booking.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;

public class SchedulingClientTest {

    private static final String BASE = "http://sched.test/api/v1";

    private MockRestServiceServer server;
    private SchedulingClient client;

    @Before
    public void setUp() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        client = new SchedulingClient(rest, BASE + "/");
    }

    @Test
    public void bookPostsPartnerBodyWithActorAndIdempotencyKey() {
        server.expect(requestTo(BASE + "/bookings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Actor", "booking-api"))
                .andExpect(jsonPath("$.channel").value("PARTNER"))
                .andExpect(jsonPath("$.sessionId").value(7))
                .andExpect(jsonPath("$.candidateId").value(9))
                .andExpect(jsonPath("$.externalRef").value("REF-1"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"bookingId\":55,\"status\":\"BOOKED\"}"));

        JsonNode r = client.book(7, 9, null, "PARTNER", "REF-1", "booking-api");

        assertEquals(55, r.get("bookingId").asInt());
        server.verify();
    }

    @Test
    public void notFoundLookupsReturnNull() {
        server.expect(requestTo(BASE + "/sessions/404")).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"SESSION_NOT_FOUND\",\"message\":\"x\"}"));
        assertNull(client.getSession(404));
    }

    @Test
    public void errorBodyBecomesSchedulingException() {
        server.expect(requestTo(BASE + "/bookings/3/cancel")).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"NOT_CANCELLABLE\",\"message\":\"Booking 3 cannot be cancelled.\",\"legacyState\":4}"));
        try {
            client.cancel(3, "r", "booking-api");
            fail();
        } catch (SchedulingException e) {
            assertEquals(409, e.getHttpStatus());
            assertEquals("NOT_CANCELLABLE", e.getError());
            assertEquals("Booking 3 cannot be cancelled.", e.getMessage());
            assertEquals(Integer.valueOf(4), e.getLegacyState());
        }
    }

    @Test
    public void searchSkipsNullParameters() {
        server.expect(requestTo(BASE + "/sessions?examCode=AZ-104&sort=startsAt,sessionId&limit=500"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("examCode", "AZ-104");
        q.put("centerCode", null);
        q.put("sort", "startsAt,sessionId");
        q.put("limit", 500);
        assertEquals(0, client.searchSessions(q).size());
        server.verify();
    }

    @Test
    public void serverErrorIsUnavailable() {
        server.expect(requestTo(BASE + "/bookings/1")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        try {
            client.getBooking(1);
            fail();
        } catch (SchedulingException e) {
            assertEquals("SCHEDULING_UNAVAILABLE", e.getError());
        }
    }
}
