package com.northwind.scheduling.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.northwind.scheduling.SqlServerTestBase;
import com.northwind.scheduling.importer.SnapshotImporter;

/**
 * MBA-3833: exact key sets of the booking-api v1 shapes the routing gateway's facade must keep producing
 * (the routing_gateway contracts assert values; JSON contract bodies cannot assert "no other keys").
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SqlServerTestBase.FixtureLegacy.class)
class LegacyV1ShapesIT extends SqlServerTestBase {

    static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    @Autowired
    TestRestTemplate http;
    @Autowired
    SnapshotImporter importer;

    @BeforeEach
    void reset() {
        importer.run(SnapshotImporter.Mode.RESET);
    }

    ResponseEntity<Map<String, Object>> post(String url, Map<String, Object> body) {
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body), MAP);
    }

    Map<String, Object> get(String url) {
        return http.exchange(url, HttpMethod.GET, null, MAP).getBody();
    }

    @Test
    void v1BookingAnswersExactlyBookingIdAndStatus() {
        ResponseEntity<Map<String, Object>> booked = post("/api/v1/bookings",
                Map.of("sessionId", 154, "candidateNumber", "NW00000930"));
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(booked.getBody()).containsOnlyKeys("bookingId", "status").containsEntry("status", "BOOKED");
        assertThat(booked.getBody().get("bookingId")).isInstanceOf(Integer.class);

        ResponseEntity<Map<String, Object>> waitlisted = post("/api/v1/bookings",
                Map.of("sessionId", 163, "candidateNumber", "NW00000931"));
        assertThat(waitlisted.getBody()).containsOnlyKeys("bookingId", "status").containsEntry("status", "WAITLISTED");

        Map<String, Object> rescheduled = post("/api/v1/bookings/" + booked.getBody().get("bookingId") + "/reschedule",
                Map.of("newSessionId", 163)).getBody();
        assertThat(rescheduled).containsOnlyKeys("bookingId", "status");
    }

    @Test
    void v1EligibilityAnswersExactlyEligibleReasonEarliestRetakeDate() {
        assertThat(get("/api/v1/candidates/NW00000939/eligibility?examCode=MHL-RN1&date=2026-12-06"))
                .containsOnlyKeys("eligible", "reason", "earliestRetakeDate")
                .containsEntry("eligible", false).containsEntry("reason", "RETAKE_WAIT_NOT_ELAPSED")
                .containsEntry("earliestRetakeDate", "2026-12-07");
        assertThat(get("/api/v1/candidates/NW00000901/eligibility?examCode=MHL-RN1"))
                .containsOnlyKeys("eligible", "reason", "earliestRetakeDate")
                .containsEntry("eligible", true).containsEntry("earliestRetakeDate", null);
    }

    @Test
    void v1ErrorsKeepBookingApiShapeWithoutLegacyCode() {
        ResponseEntity<Map<String, Object>> r = post("/api/v1/bookings",
                Map.of("sessionId", 155, "candidateNumber", "NW00000940", "accommodationCode", "ET25"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsOnlyKeys("error", "message").containsEntry("error", "NO_ACCOMMODATION_SEAT");
    }
}
