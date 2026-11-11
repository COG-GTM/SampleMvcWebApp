package com.northwind.assessment.booking.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.northwind.assessment.booking.model.BookingRequest;
import com.northwind.assessment.booking.model.BookingResponse;
import com.northwind.assessment.booking.web.ApiException;

/** booking-api's v1 writes are forwarded to scheduling-service through the routing layer (MBA-3823). */
public class SchedulingClientTest {

    private MockRestServiceServer server;
    private SchedulingClient client;
    private BookingService service;

    @Before
    public void setUp() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.createServer(rest);
        client = new SchedulingClient(rest, "http://gateway:8000/");
        service = new BookingService();
        ReflectionTestUtils.setField(service, "scheduling", client);
        ReflectionTestUtils.setField(service, "channel", "PARTNER");
        ReflectionTestUtils.setField(service, "relayWaitMs", 2000L);
    }

    @Test
    public void book_postsPartnerBookingToV2_andAnswersWithTheRelayedLegacyId() {
        server.expect(requestTo("http://gateway:8000/api/v2/bookings")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string(
                        "{\"sessionId\":42,\"candidateNumber\":\"C000123\",\"accommodationCode\":\"ET25\",\"externalRef\":\"P-9\",\"channel\":\"PARTNER\"}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"bookingId\":50000007,\"status\":\"BOOKED\",\"sessionId\":42}"));
        server.expect(requestTo("http://gateway:8000/api/v2/bookings/50000007")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"bookingId\":50000007,\"legacyBookingId\":null}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://gateway:8000/api/v2/bookings/50000007")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"bookingId\":50000007,\"legacyBookingId\":3601}", MediaType.APPLICATION_JSON));
        BookingRequest req = new BookingRequest();
        req.setSessionId(42);
        req.setCandidateNumber(" C000123 ");
        req.setAccommodationCode("et25");
        req.setExternalRef("P-9");

        BookingResponse r = service.book(req);

        assertEquals(3601, r.getBookingId());
        assertEquals("BOOKED", r.getStatus());
        server.verify();
    }

    @Test
    public void book_relaySlowerThanWait_answersWithSchedulingId() {
        ReflectionTestUtils.setField(service, "relayWaitMs", 0L);
        server.expect(requestTo("http://gateway:8000/api/v2/bookings")).andRespond(withStatus(HttpStatus.CREATED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"bookingId\":50000008,\"status\":\"WAITLISTED\"}"));
        BookingRequest req = new BookingRequest();
        req.setSessionId(42);
        req.setCandidateNumber("C1");
        assertEquals(50000008, service.book(req).getBookingId());
    }

    @Test
    public void schedulingRuleError_passesThroughWithV1CodeAndStatus() {
        server.expect(requestTo("http://gateway:8000/api/v2/bookings")).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"DUPLICATE_BOOKING\",\"message\":\"already booked\",\"legacyCode\":50002}"));
        try {
            client.book(1, "C1", null, null, "PARTNER");
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(HttpStatus.CONFLICT, e.getStatus());
            assertEquals("DUPLICATE_BOOKING", e.getError());
            assertEquals("already booked", e.getMessage());
        }
    }

    @Test
    public void cancelAndReschedule_callV2() {
        server.expect(requestTo("http://gateway:8000/api/v2/bookings/77?reason=Moved"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"bookingId\":77,\"status\":\"CANCELLED\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://gateway:8000/api/v2/bookings/77/reschedule")).andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{\"newSessionId\":9}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"bookingId\":50000009,\"status\":\"BOOKED\"}"));
        Map<String, Object> c = client.cancel(77, "Moved");
        Map<String, Object> r = client.reschedule(77, 9);
        assertEquals("CANCELLED", c.get("status"));
        assertEquals(50000009, ((Number) r.get("bookingId")).intValue());
        server.verify();
    }

    @Test
    public void serverError_becomesBadGateway() {
        server.expect(requestTo("http://gateway:8000/api/v2/bookings/5")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        try {
            client.find(5);
            fail("expected ApiException");
        } catch (ApiException e) {
            assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
        }
    }
}
