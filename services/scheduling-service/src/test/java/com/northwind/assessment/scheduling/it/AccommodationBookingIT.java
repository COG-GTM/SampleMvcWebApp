package com.northwind.assessment.scheduling.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** MBA-3830 (A5): extra-time candidates reserve an accommodation-capable seat. Over HTTP against SQL Server. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccommodationBookingIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };
    private static final String ET_HELD = "(SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
            + "AND b.status IN ('BOOKED','ATTENDED') AND b.accommodation_code IN ('ET25','ET50'))";
    private static final String FREE_SEATS = "s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
            + "AND b.status IN ('BOOKED','ATTENDED'))";

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate db;

    @Test
    void ac1_candidateSnapshotDefaultIsExposed_sessionsReportAccommodationSeatsLeft_blankCodeBooksNoAccommodation() {
        String et25 = db.queryForObject("SELECT TOP 1 candidate_number FROM candidate_snapshot "
                + "WHERE default_accommodation_code = 'ET25' ORDER BY candidate_number", String.class);
        assertThat(get("/api/v2/candidates/" + et25)).containsEntry("defaultAccommodationCode", "ET25");

        String centerCode = db.queryForObject("SELECT TOP 1 c.center_code FROM test_center_snapshot c JOIN exam_session s "
                + "ON s.center_id = c.center_id WHERE s.accommodation_capacity > 0 ORDER BY c.center_code", String.class);
        List<Map<String, Object>> sessions = getList("/api/v2/centers/" + centerCode + "/sessions?from=2026-01-01");
        assertThat(sessions).isNotEmpty().allSatisfy(s -> assertThat(num(s, "accommodationSeatsAvailable"))
                .isEqualTo(Math.max(0, num(s, "accommodationCapacity") - num(s, "accommodationUsed"))));

        // ADR 0002 6a: the API books exactly what it is sent; the UI is what pre-fills the default.
        int sessionId = openSession("s.accommodation_capacity >= 1 AND " + ET_HELD + " < s.accommodation_capacity AND "
                + FREE_SEATS + " >= 3");
        String candidate = db.queryForObject("SELECT TOP 1 c.candidate_number FROM candidate_snapshot c "
                + "WHERE c.default_accommodation_code IN ('ET25','ET50') AND NOT EXISTS (SELECT 1 FROM booking b "
                + "WHERE b.candidate_id = c.candidate_id AND b.session_id = ?) AND NOT EXISTS (SELECT 1 FROM exam_result_snapshot r "
                + "WHERE r.candidate_id = c.candidate_id AND r.exam_id = (SELECT exam_id FROM exam_session WHERE session_id = ?)) "
                + "ORDER BY c.candidate_number", String.class, sessionId, sessionId);
        Map<String, Object> before = session(sessionId);
        ResponseEntity<Map<String, Object>> r = book(sessionId, candidate, "  ");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).containsEntry("status", "BOOKED").containsEntry("accommodationCode", null);
        Map<String, Object> after = session(sessionId);
        assertThat(num(after, "accommodationUsed")).isEqualTo(num(before, "accommodationUsed"));
        assertThat(num(after, "seatsAvailable")).isEqualTo(num(before, "seatsAvailable") - 1);
    }

    @Test
    void ac2_ac3_ac4_lastAccommodationSeat_thenNoAccommodationSeat409_generalSeatNotConsumed_srRbUnconstrained() {
        int sessionId = openSession("s.accommodation_capacity = 2 AND " + ET_HELD + " < 2 AND " + FREE_SEATS + " >= 6");
        List<String> candidates = eligibleCandidates(sessionId, 6);
        if (num(session(sessionId), "accommodationUsed") == 0) {
            assertThat(book(sessionId, candidates.get(5), "ET25").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        Map<String, Object> before = session(sessionId);
        assertThat(before).containsEntry("accommodationCapacity", 2).containsEntry("accommodationUsed", 1)
                .containsEntry("accommodationSeatsAvailable", 1);

        // AC2: ET50 takes the last accommodation seat; both counters drop.
        ResponseEntity<Map<String, Object>> et50 = book(sessionId, candidates.get(0), "et50");
        assertThat(et50.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(et50.getBody()).containsEntry("status", "BOOKED").containsEntry("accommodationCode", "ET50");
        long et50Id = num(et50.getBody(), "bookingId");
        assertThat(get("/api/v2/bookings/" + et50Id)).containsEntry("accommodationCode", "ET50").containsEntry("status", "BOOKED");
        Map<String, Object> full = session(sessionId);
        assertThat(num(full, "seatsAvailable")).isEqualTo(num(before, "seatsAvailable") - 1);
        assertThat(full).containsEntry("accommodationUsed", 2).containsEntry("accommodationSeatsAvailable", 0);

        // AC3: another ET booking is rejected with 409 NO_ACCOMMODATION_SEAT / 50003; nothing is consumed or written.
        int bookingsBefore = count("SELECT COUNT(*) FROM booking WHERE session_id = ?", sessionId);
        int outboxBefore = count("SELECT COUNT(*) FROM outbox_event WHERE session_id = ?", sessionId);
        for (String code : List.of("ET25", "ET50")) {
            ResponseEntity<Map<String, Object>> rejected = book(sessionId, candidates.get(1), code);
            assertError(rejected, "NO_ACCOMMODATION_SEAT", 50003);
        }
        assertThat(session(sessionId)).containsEntry("seatsAvailable", full.get("seatsAvailable"))
                .containsEntry("bookedSeats", full.get("bookedSeats")).containsEntry("accommodationUsed", 2);
        assertThat(count("SELECT COUNT(*) FROM booking WHERE session_id = ?", sessionId)).isEqualTo(bookingsBefore);
        assertThat(count("SELECT COUNT(*) FROM outbox_event WHERE session_id = ?", sessionId)).isEqualTo(outboxBefore);

        // AC4: SR and RB take a general seat only.
        assertThat(book(sessionId, candidates.get(2), "SR").getBody()).containsEntry("status", "BOOKED").containsEntry("accommodationCode", "SR");
        assertThat(book(sessionId, candidates.get(3), "RB").getBody()).containsEntry("status", "BOOKED").containsEntry("accommodationCode", "RB");
        Map<String, Object> afterSrRb = session(sessionId);
        assertThat(num(afterSrRb, "seatsAvailable")).isEqualTo(num(full, "seatsAvailable") - 2);
        assertThat(afterSrRb).containsEntry("accommodationUsed", 2).containsEntry("accommodationSeatsAvailable", 0);

        // Cancelling the ET50 booking frees its accommodation seat again.
        assertThat(http.exchange("/api/v2/bookings/" + et50Id, HttpMethod.DELETE, null, MAP).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(session(sessionId)).containsEntry("accommodationSeatsAvailable", 1);
        assertThat(book(sessionId, candidates.get(1), "ET25").getBody()).containsEntry("accommodationCode", "ET25");
    }

    @Test
    void ac5_etRescheduleToSessionWithoutAccommodationSeat_rejected409_originalIntact_thenSucceedsWhereSeatFree() {
        Map<String, Object> trio = db.queryForMap("SELECT TOP 1 sa.session_id AS a, sb.session_id AS b, sc.session_id AS c "
                + "FROM exam_session sa JOIN exam_session sb ON sb.exam_id = sa.exam_id AND sb.session_id <> sa.session_id "
                + "JOIN exam_session sc ON sc.exam_id = sa.exam_id AND sc.session_id NOT IN (sa.session_id, sb.session_id) "
                + "WHERE sa.status = 'OPEN' AND sb.status = 'OPEN' AND sc.status = 'OPEN' "
                + "AND sa.locally_modified = 0 AND sb.locally_modified = 0 AND sc.locally_modified = 0 "
                + "AND sa.starts_at > '2026-11-20' AND sb.starts_at > '2026-11-20' AND sc.starts_at > '2026-11-20' "
                + "AND sa.accommodation_capacity >= 1 AND sb.accommodation_capacity = 0 AND sc.accommodation_capacity >= 1 "
                + "AND " + etHeld("sa") + " < sa.accommodation_capacity AND " + etHeld("sc") + " < sc.accommodation_capacity "
                + "AND " + freeSeats("sa") + " >= 2 AND " + freeSeats("sb") + " >= 2 AND " + freeSeats("sc") + " >= 2 "
                + "ORDER BY sa.session_id");
        int a = ((Number) trio.get("a")).intValue();
        int b = ((Number) trio.get("b")).intValue();
        int c = ((Number) trio.get("c")).intValue();
        String candidate = db.queryForList("SELECT TOP 1 c.candidate_number FROM candidate_snapshot c WHERE NOT EXISTS ("
                + "SELECT 1 FROM booking bk WHERE bk.candidate_id = c.candidate_id AND bk.session_id IN (?, ?, ?)) AND NOT EXISTS ("
                + "SELECT 1 FROM exam_result_snapshot r WHERE r.candidate_id = c.candidate_id AND r.exam_id = "
                + "(SELECT exam_id FROM exam_session WHERE session_id = ?)) ORDER BY NEWID()", String.class, a, b, c, a).get(0);
        long bookingId = num(book(a, candidate, "ET25").getBody(), "bookingId");
        Map<String, Object> aBefore = session(a);
        Map<String, Object> bBefore = session(b);
        int outboxBefore = count("SELECT COUNT(*) FROM outbox_event", null);

        assertError(reschedule(bookingId, b), "NO_ACCOMMODATION_SEAT", 50003);

        assertThat(get("/api/v2/bookings/" + bookingId)).containsEntry("status", "BOOKED").containsEntry("sessionId", a)
                .containsEntry("accommodationCode", "ET25").containsEntry("cancelReason", null);
        assertThat(session(a)).containsEntry("seatsAvailable", aBefore.get("seatsAvailable"))
                .containsEntry("accommodationUsed", aBefore.get("accommodationUsed"));
        assertThat(session(b)).containsEntry("seatsAvailable", bBefore.get("seatsAvailable"))
                .containsEntry("bookedSeats", bBefore.get("bookedSeats"));
        assertThat(count("SELECT COUNT(*) FROM booking bk JOIN candidate_snapshot cs ON cs.candidate_id = bk.candidate_id "
                + "WHERE bk.session_id = " + b + " AND cs.candidate_number = ?", candidate)).isZero();
        assertThat(count("SELECT COUNT(*) FROM outbox_event", null)).isEqualTo(outboxBefore);

        // The reschedule keeps the booking's code: to a session with a free accommodation seat it moves the seat too.
        Map<String, Object> cBefore = session(c);
        ResponseEntity<Map<String, Object>> moved = reschedule(bookingId, c);
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(moved.getBody()).containsEntry("sessionId", c).containsEntry("accommodationCode", "ET25");
        assertThat(num(session(c), "accommodationUsed")).isEqualTo(num(cBefore, "accommodationUsed") + 1);
        assertThat(num(session(a), "accommodationUsed")).isEqualTo(num(aBefore, "accommodationUsed") - 1);
    }

    @Test
    void ac6_capacityDecreaseBelowAccommodationCapacity_rejected409_50005_nothingChanges() {
        int sessionId = openSession("s.accommodation_capacity >= 2");
        Map<String, Object> before = session(sessionId);
        int accommodationCapacity = num(before, "accommodationCapacity");
        int changes = count("SELECT COUNT(*) FROM capacity_change WHERE session_id = ?", sessionId);

        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/sessions/" + sessionId + "/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("newCapacity", accommodationCapacity - 1, "actor", "it")), MAP);

        assertError(r, "CAPACITY_BELOW_ACCOMMODATION_FLOOR", 50005);
        assertThat(session(sessionId)).containsEntry("capacity", before.get("capacity"))
                .containsEntry("accommodationCapacity", accommodationCapacity)
                .containsEntry("seatsAvailable", before.get("seatsAvailable"));
        assertThat(count("SELECT COUNT(*) FROM capacity_change WHERE session_id = ?", sessionId)).isEqualTo(changes);
    }

    private static String etHeld(String alias) {
        return ET_HELD.replace("s.session_id", alias + ".session_id");
    }

    private static String freeSeats(String alias) {
        return FREE_SEATS.replace("s.capacity", alias + ".capacity").replace("s.session_id", alias + ".session_id");
    }

    private int openSession(String condition) {
        return db.queryForObject("SELECT TOP 1 s.session_id FROM exam_session s WHERE s.status = 'OPEN' AND s.locally_modified = 0 "
                + "AND s.starts_at > '2026-11-20' AND " + condition + " ORDER BY s.session_id DESC", Integer.class);
    }

    private List<String> eligibleCandidates(int sessionId, int n) {
        return db.queryForList("SELECT TOP (?) c.candidate_number FROM candidate_snapshot c WHERE NOT EXISTS ("
                + "SELECT 1 FROM booking b WHERE b.candidate_id = c.candidate_id AND b.session_id = ?) AND NOT EXISTS ("
                + "SELECT 1 FROM exam_result_snapshot r WHERE r.candidate_id = c.candidate_id AND r.exam_id = "
                + "(SELECT exam_id FROM exam_session WHERE session_id = ?)) ORDER BY NEWID()", String.class, n, sessionId, sessionId);
    }

    private int count(String sql, Object arg) {
        return arg == null ? db.queryForObject(sql, Integer.class) : db.queryForObject(sql, Integer.class, arg);
    }

    private static int num(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).intValue();
    }

    private Map<String, Object> session(int sessionId) {
        return get("/api/v2/sessions/" + sessionId);
    }

    private ResponseEntity<Map<String, Object>> book(int sessionId, String candidate, String accommodation) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateNumber", candidate);
        body.put("accommodationCode", accommodation);
        return http.exchange("/api/v2/bookings", HttpMethod.POST, new HttpEntity<>(body), MAP);
    }

    private ResponseEntity<Map<String, Object>> reschedule(long bookingId, int newSessionId) {
        return http.exchange("/api/v2/bookings/" + bookingId + "/reschedule", HttpMethod.POST,
                new HttpEntity<>(Map.of("newSessionId", newSessionId)), MAP);
    }

    private Map<String, Object> get(String url) {
        ResponseEntity<Map<String, Object>> r = http.exchange(url, HttpMethod.GET, null, MAP);
        assertThat(r.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private List<Map<String, Object>> getList(String url) {
        ResponseEntity<List<Map<String, Object>>> r = http.exchange(url, HttpMethod.GET, null, LIST);
        assertThat(r.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private static void assertError(ResponseEntity<Map<String, Object>> r, String code, Integer legacyCode) {
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody()).containsEntry("error", code).containsEntry("legacyCode", legacyCode).containsKey("message");
    }
}
