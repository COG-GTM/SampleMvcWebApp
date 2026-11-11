package com.northwind.assessment.scheduling.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.northwind.assessment.scheduling.reconciliation.ReconciliationService;
import com.northwind.assessment.scheduling.relay.OutboxRelay;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * MBA-3823 acceptance criteria against a real SQL Server: legacy logins denied, outbox events relayed into
 * AssessmentDelivery exactly as scheduling-service decided (trigger side effects skipped), replay without
 * duplicates, attendance, reconciliation at 0. The relay is driven explicitly (scheduling.relay.enabled=false).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SingleWriterRelayIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void relayProperties(DynamicPropertyRegistry registry) {
        registry.add("scheduling.relay.url", () -> jdbcUrl("AssessmentDelivery"));
        registry.add("scheduling.relay.enabled", () -> "false");
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate db;

    @Autowired
    OutboxRelay relay;

    @Autowired
    ReconciliationService reconciliation;

    @BeforeEach
    void drainEarlierEvents() {
        relay.drain(500);
    }

    @ParameterizedTest(name = "{0} is denied booking and seat writes")
    @CsvSource({"ad_portal,P0rtal!Dev2026", "ad_booking_api,B00king!Dev2026", "ad_results,Resu1ts!Dev2026",
        "ad_capacity_sync,Capac1ty!Dev2026", "ad_sched_import,Sch3d!Imp0rt2026"})
    void legacyLogin_forbiddenWrites_failWithPermissionError(String login, String password) throws SQLException {
        int sessionId = legacyInt("SELECT TOP 1 SessionId FROM dbo.ExamSession ORDER BY SessionId");
        int bookingId = legacyInt("SELECT TOP 1 BookingId FROM dbo.Booking ORDER BY BookingId");
        List<String> forbidden = List.of(
                "INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) VALUES (" + sessionId + ", 1, 'BOOKED', 'PARTNER')",
                "UPDATE dbo.Booking SET Status = 'CANCELLED' WHERE BookingId = " + bookingId,
                "DELETE FROM dbo.Booking WHERE BookingId = " + bookingId,
                "INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position) VALUES (" + sessionId + ", 1, " + bookingId + ", 99)",
                "DELETE FROM dbo.Waitlist WHERE BookingId = " + bookingId,
                "UPDATE dbo.ExamSession SET SeatsAvailable = 0 WHERE SessionId = " + sessionId,
                "UPDATE dbo.ExamSession SET Capacity = 1 WHERE SessionId = " + sessionId,
                "UPDATE dbo.ExamSession SET AccommodationCapacity = 0 WHERE SessionId = " + sessionId,
                "INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, SeatsAvailable) "
                        + "SELECT TOP 1 ExamId, TestCenterId, RoomId, DATEADD(DAY, 400, StartsAt), 99, 99 FROM dbo.ExamSession",
                "DELETE FROM dbo.ExamSession WHERE SessionId = " + sessionId,
                "EXEC dbo.usp_BookSeat @SessionId = " + sessionId + ", @CandidateId = 1",
                "EXEC dbo.usp_CancelBooking @BookingId = " + bookingId,
                "EXEC dbo.usp_ChangeCapacity @SessionId = " + sessionId + ", @NewCapacity = 1",
                "EXEC dbo.usp_RescheduleBooking @BookingId = " + bookingId + ", @NewSessionId = " + sessionId);
        try (Connection c = DriverManager.getConnection(jdbcUrl("AssessmentDelivery"), login, password)) {
            for (String sql : forbidden) {
                assertThatThrownBy(() -> {
                    try (Statement st = c.createStatement()) {
                        st.execute(sql);
                    }
                }).as(login + ": " + sql).isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).as(e.getMessage()).isIn(229, 230))
                        .hasMessageContaining("permission was denied");
            }
        }
    }

    @Test
    void relayLogin_canProjectButCannotRunLegacyProcs() throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl("AssessmentDelivery"), "ad_sched_relay", "Sch3d!Re1ay2026");
             Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.execute("EXEC dbo.usp_CancelBooking @BookingId = 1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission was denied");
            assertThatThrownBy(() -> st.execute("DELETE FROM dbo.Booking WHERE BookingId = -1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission was denied");
        }
    }

    @Test
    void bookCancelCapacityUpAndDown_legacyEqualsSchedulingExactly_triggerSideEffectsSkipped() throws SQLException {
        // no accommodation allocation: with one, the accommodation floor (PR #57) forbids reductions that would bump
        int sessionId = openSession("s.accommodation_capacity = 0 AND s.capacity - (SELECT COUNT(*) FROM booking b "
                + "WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED')) BETWEEN 3 AND 10");
        List<String> candidates = eligibleCandidates(sessionId, 2);

        // PARTNER booking: trg_Booking_SeatSync would decrement SeatsAvailable a second time
        ResponseEntity<Map<String, Object>> booked = book(sessionId, candidates.get(0), "SR", "PARTNER");
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long modernId = ((Number) booked.getBody().get("bookingId")).longValue();
        assertThat(lastEvent()).containsEntry("event_type", "BookingCreated");
        assertThat(relay.drain(100).failed()).isZero();

        Integer legacyId = db.queryForObject("SELECT legacy_booking_id FROM booking WHERE booking_id = ?", Integer.class, modernId);
        assertThat(legacyId).isNotNull().isLessThan(50_000_000);
        assertThat(legacyRow("SELECT SessionId, Status, AccommodationCode, Channel FROM dbo.Booking WHERE BookingId = " + legacyId))
                .containsExactly(String.valueOf(sessionId), "BOOKED", "SR", "PARTNER");
        assertSessionMatches(sessionId);
        assertThat(((Number) get("/api/v2/bookings/" + legacyId).get("bookingId")).longValue()).isEqualTo(modernId);

        // cancel through the legacy id (v1 URLs keep working)
        ResponseEntity<Map<String, Object>> cancelled = http.exchange("/api/v2/bookings/" + legacyId + "?reason=it", HttpMethod.DELETE,
                null, MAP);
        assertThat(cancelled.getBody()).containsEntry("status", "CANCELLED");
        relay.drain(100);
        assertThat(legacyRow("SELECT Status, CancelReason FROM dbo.Booking WHERE BookingId = " + legacyId))
                .containsExactly("CANCELLED", "it");
        assertSessionMatches(sessionId);

        // capacity increase, then a decrease below the seats held: scheduling bumps, the trigger must not bump again
        int capacity = ((Number) get("/api/v2/sessions/" + sessionId).get("capacity")).intValue();
        book(sessionId, candidates.get(1), null, "PORTAL");
        assertThat(changeCapacity(sessionId, capacity + 2).getStatusCode()).isEqualTo(HttpStatus.OK);
        relay.drain(100);
        assertSessionMatches(sessionId);
        int held = ((Number) get("/api/v2/sessions/" + sessionId).get("bookedSeats")).intValue();
        int accUsed = ((Number) get("/api/v2/sessions/" + sessionId).get("accommodationUsed")).intValue();
        int accCapacity = ((Number) get("/api/v2/sessions/" + sessionId).get("accommodationCapacity")).intValue();
        int lower = Math.max(Math.max(accCapacity, accUsed), held - 2);
        ResponseEntity<Map<String, Object>> decreased = changeCapacity(sessionId, lower);
        assertThat(decreased.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Number> bumped = (List<Number>) decreased.getBody().get("bumpedBookingIds");
        assertThat(lastEvent()).containsEntry("event_type", "CapacityChanged");
        relay.drain(100);
        assertSessionMatches(sessionId);
        for (Number b : bumped) {
            String ref = db.queryForObject("SELECT CAST(COALESCE(legacy_booking_id, booking_id) AS VARCHAR(12)) FROM booking "
                    + "WHERE booking_id = ?", String.class, b.longValue());
            assertThat(legacyRow("SELECT Status FROM dbo.Booking WHERE BookingId = " + ref)).containsExactly("WAITLISTED");
            assertThat(legacyInt("SELECT COUNT(*) FROM dbo.Waitlist WHERE BookingId = " + ref)).isEqualTo(1);
        }

        ReconciliationService.Report report = reconciliation.reconcile(false);
        assertThat(report.mismatches()).isEmpty();
        assertThat(report.seatDriftRows()).isEmpty();
        assertThat(report.relay().pendingEvents()).isZero();
    }

    @Test
    void relayKilledMidBatch_everyEventReplayedTwice_noDuplicates() throws SQLException {
        int sessionId = openSession("s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED')) BETWEEN 1 AND 3");
        for (String c : eligibleCandidates(sessionId, 5)) {
            book(sessionId, c, null, "PARTNER");
        }
        relay.drain(2);
        String counts = "SELECT (SELECT COUNT(*) FROM dbo.Booking), (SELECT COUNT(*) FROM dbo.Waitlist), "
                + "(SELECT COUNT(*) FROM dbo.AuditLog), (SELECT COUNT(*) FROM dbo.RelayBookingMap), (SELECT COUNT(*) FROM dbo.RelayCheckpoint)";
        List<String> before = legacyRow(counts);
        int events = db.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class);

        // simulate a crash after every legacy commit but before the Scheduling write-back: all events pending again
        db.update("UPDATE outbox_event SET relayed_at = NULL");
        OutboxRelay.RunResult first = relay.drain(7);
        db.update("UPDATE outbox_event SET relayed_at = NULL");
        OutboxRelay.RunResult second = relay.drain(500);

        assertThat(first.replayed()).isEqualTo(events);
        assertThat(second.replayed()).isEqualTo(events);
        assertThat(first.applied() + second.applied()).isZero();
        assertThat(legacyRow(counts)).isEqualTo(before);
        assertThat(reconciliation.reconcile(false).mismatches()).isEmpty();
    }

    @Test
    void attendance_schedulingFirstThenLegacyViaRelay_idempotent() throws SQLException {
        int sessionId = openSession("s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED')) > 2");
        long modernId = ((Number) book(sessionId, eligibleCandidates(sessionId, 1).get(0), null, "PORTAL").getBody()
                .get("bookingId")).longValue();
        relay.drain(100);
        int legacyId = db.queryForObject("SELECT legacy_booking_id FROM booking WHERE booking_id = ?", Integer.class, modernId);

        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/bookings/" + legacyId + "/attendance", HttpMethod.POST, null, MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).containsEntry("status", "ATTENDED");
        assertThat(lastEvent()).containsEntry("event_type", "AttendanceRecorded");
        assertThat(legacyRow("SELECT Status FROM dbo.Booking WHERE BookingId = " + legacyId)).containsExactly("BOOKED");
        relay.drain(100);
        assertThat(legacyRow("SELECT Status FROM dbo.Booking WHERE BookingId = " + legacyId)).containsExactly("ATTENDED");
        assertSessionMatches(sessionId);

        long eventsBefore = db.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class);
        assertThat(http.exchange("/api/v2/bookings/" + modernId + "/attendance", HttpMethod.POST, null, MAP).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class)).isEqualTo(eventsBefore);

        http.exchange("/api/v2/bookings/" + modernId + "?reason=x", HttpMethod.DELETE, null, MAP);
        long cancelledId = ((Number) book(sessionId, eligibleCandidates(sessionId, 1).get(0), null, "PORTAL").getBody()
                .get("bookingId")).longValue();
        http.exchange("/api/v2/bookings/" + cancelledId + "?reason=x", HttpMethod.DELETE, null, MAP);
        ResponseEntity<Map<String, Object>> conflict = http.exchange("/api/v2/bookings/" + cancelledId + "/attendance",
                HttpMethod.POST, null, MAP);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody()).containsEntry("error", "BOOKING_NOT_ACTIVE");
    }

    @Test
    void rejectedChange_writesNoOutboxEvent_andEventIdsIncrease() {
        int sessionId = openSession("s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED')) > 2");
        String candidate = eligibleCandidates(sessionId, 1).get(0);
        book(sessionId, candidate, null, "PORTAL");
        long events = db.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class);
        assertThat(book(sessionId, candidate, null, "PORTAL").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class)).isEqualTo(events);
        List<Long> ids = db.queryForList("SELECT event_id FROM outbox_event ORDER BY created_at, event_id", Long.class);
        assertThat(ids).isSorted();
    }

    private void assertSessionMatches(int sessionId) throws SQLException {
        Map<String, Object> s = get("/api/v2/sessions/" + sessionId);
        List<String> legacy = legacyRow("SELECT Capacity, SeatsAvailable, AccommodationCapacity, "
                + "(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status = 'WAITLISTED') "
                + "FROM dbo.ExamSession s WHERE SessionId = " + sessionId);
        int waitlisted = db.queryForObject("SELECT COUNT(*) FROM booking WHERE session_id = ? AND status = 'WAITLISTED'",
                Integer.class, sessionId);
        assertThat(legacy).containsExactly(String.valueOf(s.get("capacity")), String.valueOf(s.get("seatsAvailable")),
                String.valueOf(s.get("accommodationCapacity")), String.valueOf(waitlisted));
        assertThat(legacyInt("SELECT SeatsAvailable - (Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId "
                + "AND b.Status IN ('BOOKED','ATTENDED'))) FROM dbo.ExamSession s WHERE SessionId = " + sessionId)).isZero();
    }

    private Map<String, Object> lastEvent() {
        return db.queryForMap("SELECT TOP 1 event_id, event_type, payload FROM outbox_event ORDER BY event_id DESC");
    }

    private ResponseEntity<Map<String, Object>> book(int sessionId, String candidate, String accommodation, String channel) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateNumber", candidate);
        body.put("accommodationCode", accommodation);
        body.put("channel", channel);
        return http.exchange("/api/v2/bookings", HttpMethod.POST, new HttpEntity<>(body), MAP);
    }

    private ResponseEntity<Map<String, Object>> changeCapacity(int sessionId, int capacity) {
        return http.exchange("/api/v2/sessions/" + sessionId + "/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("newCapacity", capacity, "actor", "it")), MAP);
    }

    private Map<String, Object> get(String path) {
        ResponseEntity<Map<String, Object>> r = http.exchange(path, HttpMethod.GET, null, MAP);
        assertThat(r.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private int openSession(String condition) {
        return db.queryForObject("SELECT TOP 1 s.session_id FROM exam_session s WHERE s.status = 'OPEN' AND s.locally_modified = 0 "
                + "AND s.starts_at > '2026-11-20' AND " + condition + " ORDER BY s.session_id DESC",
                Integer.class);
    }

    private List<String> eligibleCandidates(int sessionId, int n) {
        return db.queryForList("SELECT TOP (?) c.candidate_number FROM candidate_snapshot c WHERE NOT EXISTS ("
                + "SELECT 1 FROM booking b WHERE b.candidate_id = c.candidate_id AND b.session_id = ?) AND NOT EXISTS ("
                + "SELECT 1 FROM exam_result_snapshot r WHERE r.candidate_id = c.candidate_id AND r.exam_id = "
                + "(SELECT exam_id FROM exam_session WHERE session_id = ?)) ORDER BY NEWID()", String.class, n, sessionId, sessionId);
    }

    private static int legacyInt(String sql) throws SQLException {
        return Integer.parseInt(legacyRow(sql).get(0));
    }

    private static List<String> legacyRow(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(jdbcUrl("AssessmentDelivery"), "sa", SA_PASSWORD);
             Statement st = c.createStatement(); var rs = st.executeQuery(sql)) {
            assertThat(rs.next()).as(sql).isTrue();
            List<String> out = new java.util.ArrayList<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                out.add(rs.getString(i));
            }
            return out;
        }
    }
}
