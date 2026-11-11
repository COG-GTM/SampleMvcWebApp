package com.northwind.assessment.scheduling.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
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

/** End-to-end over HTTP against a real SQL Server: the acceptance criteria of MBA-3821. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchedulingApiIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate db;

    @Test
    void startupImport_loadsAllSeededCentersAndTheirSessions() {
        List<Map<String, Object>> centers = getList("/api/v2/centers");
        assertThat(centers).hasSizeGreaterThanOrEqualTo(20);
        for (String table : List.of("test_center_snapshot", "room_snapshot", "exam_snapshot", "candidate_snapshot")) {
            assertThat(db.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE source_version IS NOT NULL AND synced_at IS NOT NULL",
                    Integer.class)).as(table).isPositive();
        }
        List<Map<String, Object>> sessions = getList("/api/v2/centers/BOS01/sessions");
        assertThat(sessions).isNotEmpty();
        assertThat(sessions.get(0)).containsKeys("sessionId", "examCode", "examTitle", "centerCode", "centerName", "roomName",
                "startsAt", "capacity", "accommodationCapacity", "seatsAvailable", "status", "bookedSeats",
                "accommodationUsed", "waitlistCount");
        Integer legacyCount = db.queryForObject("SELECT COUNT(*) FROM booking WHERE origin = 'LEGACY_IMPORT' "
                + "AND legacy_booking_id = booking_id AND booking_id < 50000000", Integer.class);
        assertThat(legacyCount).isPositive();
    }

    @Test
    void usp_BookSeat_openSessionWithSeats_booksAndSeatsDropByExactlyOne() {
        int sessionId = openSession("s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED')) > 2");
        int before = seats(sessionId);
        String candidate = eligibleCandidates(sessionId, 1).get(0);

        ResponseEntity<Map<String, Object>> r = book(sessionId, candidate, null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).containsEntry("status", "BOOKED").containsEntry("channel", "PORTAL")
                .containsEntry("waitlistPosition", null).containsEntry("seatsAvailable", before - 1);
        assertThat(((Number) r.getBody().get("bookingId")).longValue()).isGreaterThanOrEqualTo(50_000_000L);
        assertThat(seats(sessionId)).isEqualTo(before - 1);
        Map<String, Object> booking = get("/api/v2/bookings/" + r.getBody().get("bookingId"));
        assertThat(booking).containsEntry("origin", "MODERN").containsEntry("legacyBookingId", null);

        ResponseEntity<Map<String, Object>> dup = book(sessionId, candidate, null);
        assertError(dup, HttpStatus.CONFLICT, "DUPLICATE_BOOKING", 50002);
    }

    @Test
    void usp_BookSeat_fullSession_waitlistsWithNextPosition() {
        int sessionId = openSession("s.capacity <= (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED'))");
        int waitlistBefore = ((Number) get("/api/v2/sessions/" + sessionId).get("waitlistCount")).intValue();
        int maxPosition = db.queryForObject("SELECT COALESCE(MAX(position), 0) FROM waitlist_entry WHERE session_id = ?",
                Integer.class, sessionId);

        ResponseEntity<Map<String, Object>> r = book(sessionId, eligibleCandidates(sessionId, 1).get(0), null);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).containsEntry("status", "WAITLISTED").containsEntry("waitlistPosition", maxPosition + 1)
                .containsEntry("seatsAvailable", 0);
        List<Map<String, Object>> waitlist = getList("/api/v2/sessions/" + sessionId + "/waitlist");
        assertThat(waitlist).hasSize(waitlistBefore + 1);
        assertThat(waitlist.get(waitlist.size() - 1)).containsEntry("bookingId", r.getBody().get("bookingId"))
                .containsKeys("position", "candidateNumber", "candidateName", "addedAt");
    }

    @Test
    void usp_BookSeat_extraTimeBeyondAccommodationCapacity_rejectedWith50003_butSrAndRbSucceed() {
        int sessionId = openSession("s.accommodation_capacity BETWEEN 1 AND 3 AND s.capacity - (SELECT COUNT(*) FROM booking b "
                + "WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED')) >= 8");
        Map<String, Object> session = get("/api/v2/sessions/" + sessionId);
        int free = ((Number) session.get("accommodationCapacity")).intValue() - ((Number) session.get("accommodationUsed")).intValue();
        List<String> candidates = eligibleCandidates(sessionId, free + 3);
        for (int i = 0; i < free; i++) {
            assertThat(book(sessionId, candidates.get(i), i % 2 == 0 ? "ET25" : "ET50").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        assertError(book(sessionId, candidates.get(free), "ET50"), HttpStatus.CONFLICT, "NO_ACCOMMODATION_SEAT", 50003);
        assertThat(book(sessionId, candidates.get(free + 1), "SR").getBody()).containsEntry("status", "BOOKED");
        assertThat(book(sessionId, candidates.get(free + 2), "RB").getBody()).containsEntry("status", "BOOKED");
    }

    @Test
    void trg_ExamSession_Update_capacityDecreaseBumpsNewestBookedToWaitlist_andFloorRejectsWith50005() {
        int sessionId = openSession("s.accommodation_capacity = 0 AND (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status = 'BOOKED') >= 3");
        Map<String, Object> session = get("/api/v2/sessions/" + sessionId);
        int capacity = ((Number) session.get("capacity")).intValue();
        int held = ((Number) session.get("bookedSeats")).intValue();
        int waitlist = ((Number) session.get("waitlistCount")).intValue();
        List<Long> newest = db.queryForList("SELECT TOP 2 booking_id FROM booking WHERE session_id = ? AND status = 'BOOKED' "
                + "ORDER BY booked_at DESC, booking_id DESC", Long.class, sessionId);

        ResponseEntity<Map<String, Object>> r = putCapacity(sessionId, held - 2);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Number> bumped = (List<Number>) r.getBody().get("bumpedBookingIds");
        assertThat(bumped.stream().map(Number::longValue).toList()).containsExactlyInAnyOrderElementsOf(newest);
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) r.getBody().get("session");
        assertThat(after).containsEntry("capacity", held - 2).containsEntry("seatsAvailable", 0)
                .containsEntry("waitlistCount", waitlist + 2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM capacity_change WHERE session_id = ?", Integer.class, sessionId)).isEqualTo(1);

        int floorSessionId = openSession("s.accommodation_capacity >= 1");
        int floor = ((Number) get("/api/v2/sessions/" + floorSessionId).get("accommodationCapacity")).intValue();
        assertError(putCapacity(floorSessionId, floor - 1), HttpStatus.CONFLICT, "CAPACITY_BELOW_ACCOMMODATION_FLOOR", 50005);

        ResponseEntity<Map<String, Object>> raised = putCapacity(sessionId, capacity);
        assertThat(raised.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) raised.getBody().get("bumpedBookingIds")).isEmpty();
    }

    /**
     * Regression for run-463f5cbfbee6 (BOS01 session #198 23 -> 9): on a session with accommodation seats, a cut to
     * exactly the seats held is at or above the allocation, so legacy usp_ChangeCapacity accepts it.
     */
    @Test
    void usp_ChangeCapacity_reductionOnAccommodationSession_floorIsAllocationOnly_notPlusStandardSeats() {
        int sessionId = openSession("s.accommodation_capacity >= 1 AND NOT EXISTS (SELECT 1 FROM booking b "
                + "WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED') AND b.accommodation_code IN ('ET25','ET50')) "
                + "AND (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED')) "
                + "BETWEEN s.accommodation_capacity AND s.capacity - 1");
        Map<String, Object> session = get("/api/v2/sessions/" + sessionId);
        int held = ((Number) session.get("bookedSeats")).intValue();
        int outboxBefore = db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE session_id = ? AND event_type = 'CapacityChanged'",
                Integer.class, sessionId);

        ResponseEntity<Map<String, Object>> r = putCapacity(sessionId, held);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((List<?>) r.getBody().get("bumpedBookingIds")).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> after = (Map<String, Object>) r.getBody().get("session");
        assertThat(after).containsEntry("capacity", held).containsEntry("seatsAvailable", 0);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE session_id = ? AND event_type = 'CapacityChanged'",
                Integer.class, sessionId)).isEqualTo(outboxBefore + 1);
    }

    @Test
    void usp_RescheduleBooking_sameExamCancelsAndBooks_differentExamRejectedWith50006() {
        Map<String, Object> pair = db.queryForMap("SELECT TOP 1 a.session_id AS a, b.session_id AS b FROM exam_session a "
                + "JOIN exam_session b ON b.exam_id = a.exam_id AND b.session_id > a.session_id "
                + "WHERE a.status = 'OPEN' AND b.status = 'OPEN' AND a.capacity > 25 AND b.capacity > 25 "
                + "AND a.starts_at > '2026-11-20' ORDER BY a.session_id");
        int from = ((Number) pair.get("a")).intValue();
        int to = ((Number) pair.get("b")).intValue();
        int other = db.queryForObject("SELECT TOP 1 session_id FROM exam_session WHERE status = 'OPEN' AND exam_id <> "
                + "(SELECT exam_id FROM exam_session WHERE session_id = ?)", Integer.class, from);
        String candidate = eligibleCandidates(from, 1, to).get(0);
        long bookingId = ((Number) book(from, candidate, null).getBody().get("bookingId")).longValue();

        assertError(reschedule(bookingId, other), HttpStatus.CONFLICT, "DIFFERENT_EXAM", 50006);
        ResponseEntity<Map<String, Object>> moved = reschedule(bookingId, to);

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(moved.getBody()).containsEntry("sessionId", to).containsEntry("candidateNumber", candidate);
        assertThat(get("/api/v2/bookings/" + bookingId)).containsEntry("status", "CANCELLED").containsEntry("cancelReason", "Rescheduled");
        List<Map<String, Object>> mine = getList("/api/v2/candidates/" + candidate + "/bookings");
        assertThat(mine).extracting(b -> b.get("bookingId")).contains(moved.getBody().get("bookingId"));
    }

    @Test
    void legacyParity_blankCancelReasonIsNull_rescheduleRefFollowsRequestChannel_negativeCapacityHasNoLegacyCode() {
        Map<String, Object> pair = db.queryForMap("SELECT TOP 1 a.session_id AS a, b.session_id AS b FROM exam_session a "
                + "JOIN exam_session b ON b.exam_id = a.exam_id AND b.session_id > a.session_id "
                + "WHERE a.status = 'OPEN' AND b.status = 'OPEN' AND a.capacity > 25 AND b.capacity > 25 "
                + "AND a.starts_at > '2026-11-20' ORDER BY a.session_id DESC");
        int from = ((Number) pair.get("a")).intValue();
        int to = ((Number) pair.get("b")).intValue();
        List<String> candidates = eligibleCandidates(from, 3, to);

        // usp_CancelBooking @Reason = NULL (portal): a blank or whitespace reason is stored as NULL
        long portal = ((Number) book(from, candidates.get(0), null).getBody().get("bookingId")).longValue();
        ResponseEntity<Map<String, Object>> cancelled = http.exchange("/api/v2/bookings/" + portal + "?reason=   ",
                HttpMethod.DELETE, null, MAP);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody()).containsEntry("status", "CANCELLED").containsEntry("cancelReason", null);

        // usp_RescheduleBooking (portal, no channel sent = PORTAL) inserted the replacement without ExternalRef
        long partnerA = partnerBooking(from, candidates.get(1), "PREF-A");
        Map<String, Object> movedByPortal = reschedule(partnerA, to).getBody();
        Map<String, Object> viaPortal = get("/api/v2/bookings/" + movedByPortal.get("bookingId"));
        assertThat(viaPortal).containsEntry("externalRef", null).containsEntry("channel", "PORTAL");
        assertThat(get("/api/v2/bookings/" + partnerA)).containsEntry("cancelReason", "Rescheduled");

        // booking-api v1 reschedule re-booked through the partner path and copied the ExternalRef
        long partnerB = partnerBooking(from, candidates.get(2), "PREF-B");
        ResponseEntity<Map<String, Object>> movedByPartner = http.exchange("/api/v2/bookings/" + partnerB + "/reschedule",
                HttpMethod.POST, new HttpEntity<>(Map.of("newSessionId", to, "channel", "PARTNER")), MAP);
        assertThat(movedByPartner.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> viaPartner = get("/api/v2/bookings/" + movedByPartner.getBody().get("bookingId"));
        assertThat(viaPartner).containsEntry("externalRef", "PREF-B").containsEntry("channel", "PARTNER");

        // AdminService.ChangeCapacity rejected a negative capacity before usp_ChangeCapacity: no legacy code
        assertError(putCapacity(from, -1), HttpStatus.BAD_REQUEST, "VALIDATION", null);
    }

    private long partnerBooking(int sessionId, String candidate, String externalRef) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateNumber", candidate);
        body.put("externalRef", externalRef);
        body.put("channel", "PARTNER");
        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/bookings", HttpMethod.POST, new HttpEntity<>(body), MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) r.getBody().get("bookingId")).longValue();
    }

    @Test
    void usp_CancelBooking_cancelsOnce_thenNotCancellable() {
        int sessionId = openSession("s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED')) > 2");
        long bookingId = ((Number) book(sessionId, eligibleCandidates(sessionId, 1).get(0), null).getBody().get("bookingId")).longValue();
        int seats = seats(sessionId);

        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/bookings/" + bookingId + "?reason=Changed plans",
                HttpMethod.DELETE, null, MAP);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("status", "CANCELLED").containsEntry("cancelReason", "Changed plans");
        assertThat(seats(sessionId)).isEqualTo(seats + 1);
        assertError(http.exchange("/api/v2/bookings/" + bookingId, HttpMethod.DELETE, null, MAP), HttpStatus.CONFLICT,
                "NOT_CANCELLABLE", 50004);
        assertError(http.exchange("/api/v2/bookings/999999999", HttpMethod.DELETE, null, MAP), HttpStatus.NOT_FOUND,
                "BOOKING_NOT_FOUND", null);
    }

    @Test
    void retakeEligibility_insideOnAndOutsideWindow_followsCalendarDays() {
        Map<String, Object> row = db.queryForMap("SELECT TOP 1 c.candidate_number, e.exam_code, e.retake_wait_days, "
                + "CAST(MAX(r.session_starts_at) AS date) AS last_attempt FROM exam_result_snapshot r "
                + "JOIN candidate_snapshot c ON c.candidate_id = r.candidate_id JOIN exam_snapshot e ON e.exam_id = r.exam_id "
                + "GROUP BY c.candidate_number, e.exam_code, e.retake_wait_days HAVING MAX(CAST(r.passed AS int)) = 0 "
                + "ORDER BY c.candidate_number");
        String base = "/api/v2/candidates/" + row.get("candidate_number") + "/eligibility?examCode=" + row.get("exam_code") + "&date=";
        LocalDate earliest = ((java.sql.Date) row.get("last_attempt")).toLocalDate()
                .plusDays(((Number) row.get("retake_wait_days")).longValue());

        assertThat(get(base + earliest.minusDays(1))).containsEntry("eligible", false)
                .containsEntry("earliestRetakeDate", earliest.toString());
        assertThat(get(base + earliest)).containsEntry("eligible", true);
        assertThat(get(base + earliest.plusDays(10))).containsEntry("eligible", true);
    }

    @Test
    void routingResolve_contract() {
        int sessionId = db.queryForObject("SELECT TOP 1 session_id FROM exam_session ORDER BY session_id", Integer.class);
        ResponseEntity<Map<String, Object>> s = http.exchange("/internal/routing/resolve?sessionId=" + sessionId, HttpMethod.GET, null, MAP);
        assertThat(s.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(s.getHeaders().getCacheControl()).isEqualTo("max-age=60");
        assertThat(s.getBody()).containsKeys("centerCode", "centerId").containsEntry("sessionId", sessionId)
                .containsEntry("bookingId", null).containsEntry("bookingOrigin", null);

        long legacyBooking = db.queryForObject("SELECT TOP 1 booking_id FROM booking WHERE origin = 'LEGACY_IMPORT'", Long.class);
        Map<String, Object> b = get("/internal/routing/resolve?bookingId=" + legacyBooking);
        assertThat(b).containsEntry("bookingOrigin", "LEGACY_IMPORT");

        ResponseEntity<Map<String, Object>> c = http.exchange("/internal/routing/resolve?centerCode=BOS01", HttpMethod.GET, null, MAP);
        assertThat(c.getHeaders().getCacheControl()).isEqualTo("max-age=60");
        assertThat(c.getBody()).containsEntry("centerCode", "BOS01").containsEntry("sessionId", null);
        assertThat(get("/internal/routing/resolve?centerId=" + c.getBody().get("centerId"))).containsEntry("centerCode", "BOS01");

        ResponseEntity<Map<String, Object>> missing = http.exchange("/internal/routing/resolve?bookingId=987654321", HttpMethod.GET, null, MAP);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("error", "NOT_FOUND").containsKey("message");
        assertThat(http.exchange("/internal/routing/resolve?centerCode=BOS01&sessionId=1", HttpMethod.GET, null, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.exchange("/internal/routing/resolve?sessionId=4294967297", HttpMethod.GET, null, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.exchange("/internal/routing/resolve?centerId=4294967297", HttpMethod.GET, null, MAP)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void reimport_isIdempotent_keepsModernWrites_andNeverWritesToAssessmentDelivery() throws SQLException {
        int legacyBookings = legacyCount("SELECT COUNT(*) FROM dbo.Booking");
        int sessionId = openSession("s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED')) > 2");
        long modern = ((Number) book(sessionId, eligibleCandidates(sessionId, 1).get(0), null).getBody().get("bookingId")).longValue();
        String center = (String) get("/api/v2/sessions/" + sessionId).get("centerCode");
        int bookingsBefore = db.queryForObject("SELECT COUNT(*) FROM booking", Integer.class);

        Map<String, Object> first = post("/internal/import/sessions?centerCode=" + center);
        Map<String, Object> second = post("/internal/import/sessions?centerCode=" + center);

        assertThat(second).containsEntry("sessionsImported", first.get("sessionsImported"))
                .containsEntry("bookings", first.get("bookings"));
        assertThat(((Number) first.get("sessionsSkippedLocallyModified")).intValue()).isPositive();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM booking", Integer.class)).isEqualTo(bookingsBefore);
        assertThat(get("/api/v2/bookings/" + modern)).containsEntry("status", "BOOKED");
        assertThat(legacyCount("SELECT COUNT(*) FROM dbo.Booking")).isEqualTo(legacyBookings);

        try (Connection c = DriverManager.getConnection(jdbcUrl("AssessmentDelivery"), "ad_sched_import", "Sch3d!Imp0rt2026");
             Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.executeUpdate("UPDATE dbo.ExamSession SET Capacity = Capacity WHERE SessionId = 1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission was denied");
        }
    }

    @Test
    void validationErrors_useBookingApiCodes() {
        assertError(http.postForEntity("/api/v2/bookings", Map.of("candidateNumber", "NW00000901"), (Class<Map<String, Object>>) (Class<?>) Map.class),
                HttpStatus.BAD_REQUEST, "VALIDATION", null);
        assertError(book(999999, "NW00000901", null), HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", null);
        assertError(book(1, "NOPE", null), HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND", null);
        assertThat(http.exchange("/api/v2/centers/NOPE/sessions", HttpMethod.GET, null, MAP).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        int closed = db.queryForObject("SELECT TOP 1 session_id FROM exam_session WHERE status <> 'OPEN'", Integer.class);
        assertError(book(closed, eligibleCandidates(closed, 1).get(0), null), HttpStatus.CONFLICT, "SESSION_NOT_OPEN", 50001);
    }

    // ---- helpers

    private int openSession(String condition) {
        return db.queryForObject("SELECT TOP 1 s.session_id FROM exam_session s WHERE s.status = 'OPEN' AND s.locally_modified = 0 "
                + "AND s.starts_at > '2026-11-20' AND " + condition + " ORDER BY s.session_id", Integer.class);
    }

    /** Candidates with no booking on the sessions and no result for their exam (always retake-eligible). */
    private List<String> eligibleCandidates(int sessionId, int n, int... alsoFree) {
        int other = alsoFree.length > 0 ? alsoFree[0] : sessionId;
        return db.queryForList("SELECT TOP (?) c.candidate_number FROM candidate_snapshot c WHERE NOT EXISTS ("
                + "SELECT 1 FROM booking b WHERE b.candidate_id = c.candidate_id AND b.session_id IN (?, ?)) AND NOT EXISTS ("
                + "SELECT 1 FROM exam_result_snapshot r WHERE r.candidate_id = c.candidate_id AND r.exam_id = "
                + "(SELECT exam_id FROM exam_session WHERE session_id = ?)) ORDER BY NEWID()", String.class, n, sessionId, other,
                sessionId);
    }

    private int seats(int sessionId) {
        return ((Number) get("/api/v2/sessions/" + sessionId).get("seatsAvailable")).intValue();
    }

    private int legacyCount(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl("AssessmentDelivery"), "sa", SA_PASSWORD);
             Statement st = c.createStatement(); var rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private ResponseEntity<Map<String, Object>> book(int sessionId, String candidate, String accommodation) {
        java.util.HashMap<String, Object> body = new java.util.HashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateNumber", candidate);
        body.put("accommodationCode", accommodation);
        return http.exchange("/api/v2/bookings", HttpMethod.POST, new HttpEntity<>(body), MAP);
    }

    private ResponseEntity<Map<String, Object>> reschedule(long bookingId, int newSessionId) {
        return http.exchange("/api/v2/bookings/" + bookingId + "/reschedule", HttpMethod.POST,
                new HttpEntity<>(Map.of("newSessionId", newSessionId)), MAP);
    }

    private ResponseEntity<Map<String, Object>> putCapacity(int sessionId, int capacity) {
        return http.exchange("/api/v2/sessions/" + sessionId + "/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("newCapacity", capacity, "actor", "it")), MAP);
    }

    private Map<String, Object> get(String url) {
        ResponseEntity<Map<String, Object>> r = http.exchange(url, HttpMethod.GET, null, MAP);
        assertThat(r.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private Map<String, Object> post(String url) {
        ResponseEntity<Map<String, Object>> r = http.exchange(url, HttpMethod.POST, null, MAP);
        assertThat(r.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private List<Map<String, Object>> getList(String url) {
        ResponseEntity<List<Map<String, Object>>> r = http.exchange(url, HttpMethod.GET, null, LIST);
        assertThat(r.getStatusCode()).as(url).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private static void assertError(ResponseEntity<Map<String, Object>> r, HttpStatus status, String code, Integer legacyCode) {
        assertThat(r.getStatusCode()).isEqualTo(status);
        assertThat(r.getBody()).containsEntry("error", code).containsEntry("legacyCode", legacyCode).containsKey("message");
    }
}
