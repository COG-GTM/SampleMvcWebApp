package com.northwind.routing;

import static org.assertj.core.api.Assertions.assertThat;

import com.northwind.routing.translate.V1Shapes;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class V1ShapesTest {

    @Test
    void bookingResultBecomesV1BookingResponse() {
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("bookingId", 50000001);
        v2.put("status", "BOOKED");
        v2.put("waitlistPosition", null);
        v2.put("booking", Map.of("bookingId", 50000001));
        v2.put("session", Map.of("sessionId", 154));
        assertThat(V1Shapes.outcome(v2)).containsExactly(Map.entry("bookingId", 50000001), Map.entry("status", "BOOKED"));
    }

    @Test
    void sessionKeepsV1FieldsAndOrder() {
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("sessionId", 154);
        v2.put("examCode", "AZ-104");
        v2.put("centerCode", "BOS01");
        v2.put("startsAt", "2026-11-12T09:00");
        v2.put("capacity", 24);
        v2.put("waitlistCount", 0);
        Map<String, Object> v1 = V1Shapes.session(v2);
        assertThat(v1.keySet()).containsExactly("sessionId", "examCode", "examTitle", "centerCode", "centerName", "roomName",
                "startsAt", "capacity", "accommodationCapacity", "seatsAvailable", "status", "bookedSeats");
        assertThat(v1).containsEntry("startsAt", "2026-11-12T09:00:00");
    }

    @Test
    void bookingAndErrorShapes() {
        Map<String, Object> v2 = new LinkedHashMap<>();
        v2.put("bookingId", 7);
        v2.put("candidateName", "x");
        v2.put("bookedAt", "2026-11-10T08:15:30.123");
        assertThat(V1Shapes.booking(v2).keySet()).containsExactly("bookingId", "sessionId", "candidateNumber", "examCode",
                "sessionStartsAt", "status", "accommodationCode", "channel", "externalRef", "bookedAt", "cancelledAt", "cancelReason");
        assertThat(V1Shapes.booking(v2)).containsEntry("bookedAt", "2026-11-10T08:15:30");
        assertThat(V1Shapes.error(Map.of("error", "SESSION_FULL", "message", "full", "legacyCode", 50001)))
                .containsExactly(Map.entry("error", "SESSION_FULL"), Map.entry("message", "full"));
    }
}
