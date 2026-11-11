package com.northwind.scheduling.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;

import com.northwind.scheduling.SqlServerTestBase;
import com.northwind.scheduling.importer.SnapshotImporter;

/**
 * MBA-3830 accommodation booking over /api/v2 (fixture: session 154 = capacity 20 / accommodation 2 / 9 booked,
 * session 163 = MHL-RN1 with its single accommodation seat taken, NW00000940 has profile default ET25).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SqlServerTestBase.FixtureLegacy.class)
class AccommodationBookingIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    @Autowired
    TestRestTemplate http;
    @Autowired
    SnapshotImporter importer;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        importer.run(SnapshotImporter.Mode.RESET);
    }

    Map<String, Object> get(String url) {
        return http.exchange(url, HttpMethod.GET, null, MAP).getBody();
    }

    ResponseEntity<Map<String, Object>> book(int session, String cand, String acc) {
        return http.exchange("/api/v2/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("sessionId", session, "candidateNumber", cand, "accommodationCode", acc)), MAP);
    }

    int bookingRows(int session) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM booking WHERE session_id = ?", Integer.class, session);
    }

    @Test
    void candidateProfileDefaultAndPerSessionAccommodationSeatsAreExposedForSearch() {
        assertThat(get("/api/v2/candidates/NW00000940")).containsEntry("defaultAccommodationCode", "ET25");
        List<Map<String, Object>> sessions = http.exchange("/api/v2/centers/SFO01/sessions?examCode=MHL-RN1&from=2026-11-12&to=2026-11-20",
                HttpMethod.GET, null, LIST).getBody();
        assertThat(sessions).extracting(s -> s.get("sessionId"), s -> s.get("accommodationAvailable"))
                .contains(org.assertj.core.groups.Tuple.tuple(154, 2), org.assertj.core.groups.Tuple.tuple(163, 0));
    }

    @Test
    void et50TakesTheLastAccommodationSeatThenTheNextEtGets409WithoutConsumingAGeneralSeat() {
        assertThat(book(154, "NW00000921", "ET25").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(get("/api/v2/sessions/154")).containsEntry("seatsAvailable", 10).containsEntry("accommodationAvailable", 1);

        ResponseEntity<Map<String, Object>> et50 = book(154, "NW00000922", "ET50");
        assertThat(et50.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(et50.getBody()).containsEntry("status", "BOOKED");
        assertThat(get("/api/v2/bookings/" + et50.getBody().get("bookingId"))).containsEntry("accommodationCode", "ET50");
        assertThat(get("/api/v2/sessions/154")).containsEntry("seatsAvailable", 9).containsEntry("bookedSeats", 11)
                .containsEntry("accommodationUsed", 2).containsEntry("accommodationAvailable", 0);

        int rows = bookingRows(154);
        ResponseEntity<Map<String, Object>> rejected = book(154, "NW00000923", "et25");
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody()).containsEntry("error", "NO_ACCOMMODATION_SEAT").containsEntry("legacyCode", 50003);
        assertThat(get("/api/v2/sessions/154")).containsEntry("seatsAvailable", 9).containsEntry("bookedSeats", 11)
                .containsEntry("accommodationAvailable", 0);
        assertThat(bookingRows(154)).isEqualTo(rows);
        assertThat((List<?>) http.exchange("/api/v2/candidates/NW00000923/bookings", HttpMethod.GET, null, LIST).getBody()).isEmpty();
    }

    @Test
    void srAndRbBookOnAnExhaustedSessionWithoutTouchingAccommodationSeats() {
        book(154, "NW00000921", "ET25");
        book(154, "NW00000922", "ET50");
        for (String[] c : new String[][] {{"NW00000924", "SR"}, {"NW00000925", "RB"}}) {
            ResponseEntity<Map<String, Object>> r = book(154, c[0], c[1]);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(get("/api/v2/bookings/" + r.getBody().get("bookingId"))).containsEntry("accommodationCode", c[1]);
        }
        assertThat(get("/api/v2/sessions/154")).containsEntry("seatsAvailable", 7).containsEntry("accommodationUsed", 2)
                .containsEntry("accommodationAvailable", 0);
        assertThat(get("/api/v2/sessions/155")).containsEntry("accommodationCapacity", 0);
        assertThat(book(155, "NW00000926", "SR").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(book(155, "NW00000927", "ET50").getBody()).containsEntry("error", "NO_ACCOMMODATION_SEAT");
    }

    @Test
    void etRescheduleToASessionWithoutAnAccommodationSeatIsRejectedAndLeavesTheOriginalIntact() {
        int id = (Integer) book(154, "NW00000940", "ET25").getBody().get("bookingId");
        Map<String, Object> before154 = get("/api/v2/sessions/154");
        Map<String, Object> before163 = get("/api/v2/sessions/163");
        int rows = jdbc.queryForObject("SELECT COUNT(*) FROM booking", Integer.class);

        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/bookings/" + id + "/reschedule", HttpMethod.POST,
                new HttpEntity<>(Map.of("newSessionId", 163)), MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("error", "NO_ACCOMMODATION_SEAT").containsEntry("legacyCode", 50003);

        assertThat(get("/api/v2/bookings/" + id)).containsEntry("status", "BOOKED").containsEntry("sessionId", 154)
                .containsEntry("accommodationCode", "ET25");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isEqualTo(rows);
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM booking WHERE booking_id = ?", String.class, id)).isNull();
        assertThat(get("/api/v2/sessions/154")).isEqualTo(before154);
        assertThat(get("/api/v2/sessions/163")).isEqualTo(before163);
    }

    @Test
    void capacityDecreaseBelowTheAccommodationAllocationIsRejectedAndChangesNothing() {
        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/sessions/154/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", 1, "reason", "smaller room")), MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("error", "INVALID_CAPACITY").containsEntry("legacyCode", 50005);
        assertThat(get("/api/v2/sessions/154")).containsEntry("capacity", 20).containsEntry("accommodationCapacity", 2)
                .containsEntry("bookedSeats", 9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM capacity_change WHERE session_id = 154", Integer.class)).isZero();

        assertThat(http.exchange("/api/v2/sessions/154/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", 2, "reason", "smaller room")), MAP).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/v2/sessions/154")).containsEntry("capacity", 2).containsEntry("accommodationCapacity", 2);
    }
}
