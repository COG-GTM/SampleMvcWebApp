package com.northwind.assessment.scheduling.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.northwind.assessment.scheduling.reconciliation.ReconciliationService;
import com.northwind.assessment.scheduling.relay.OutboxRelay;
import com.northwind.assessment.scheduling.service.SeatOfferExpiryJob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * MBA-3829 acceptance criteria AC1-AC6 against a real SQL Server, through the HTTP API: seat offers, the held seat,
 * accept with position compaction, decline / expiry progression, the ET25 skip, two seats at once, concurrency,
 * and the relay into dbo.Booking / dbo.Waitlist with reconciliation at 0. The relay is drained explicitly and the
 * expiry job is triggered by hand after backdating {@code expires_at}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WaitlistOfferIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

    @DynamicPropertySource
    static void offerProperties(DynamicPropertyRegistry registry) {
        registry.add("scheduling.relay.url", () -> jdbcUrl("AssessmentDelivery"));
        registry.add("scheduling.relay.enabled", () -> "false");
        registry.add("scheduling.waitlist.expiry-enabled", () -> "false");
        registry.add("scheduling.waitlist.offer-ttl", () -> "PT24H");
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate db;

    @Autowired
    OutboxRelay relay;

    @Autowired
    ReconciliationService reconciliation;

    @Autowired
    SeatOfferExpiryJob expiryJob;

    @Autowired
    Clock clock;

    @BeforeEach
    void drainEarlierEvents() {
        relay.drain(500);
    }

    /** A full OPEN session: {@code booked} our own BOOKED bookings, then waitlisted bookings at positions 1..n. */
    private record Setup(int sessionId, List<Long> booked, List<Long> waitlisted, List<String> waitlistedCandidates) {
    }

    private Setup fullSessionWithWaitlist(String condition, String... waitlistCodes) {
        int sessionId = openSession(condition);
        int free = ((Number) get("/api/v2/sessions/" + sessionId).get("seatsAvailable")).intValue();
        List<String> candidates = eligibleCandidates(sessionId, free + waitlistCodes.length);
        List<Long> booked = new ArrayList<>();
        for (int i = 0; i < free; i++) {
            ResponseEntity<Map<String, Object>> r = book(sessionId, candidates.get(i), null);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(r.getBody()).containsEntry("status", "BOOKED");
            booked.add(id(r.getBody()));
        }
        List<Long> waitlisted = new ArrayList<>();
        List<String> waitlistedCandidates = new ArrayList<>();
        for (int i = 0; i < waitlistCodes.length; i++) {
            String candidate = candidates.get(free + i);
            ResponseEntity<Map<String, Object>> r = book(sessionId, candidate, waitlistCodes[i]);
            assertThat(r.getStatusCode()).as(r.toString()).isEqualTo(HttpStatus.CREATED);
            assertThat(r.getBody()).containsEntry("status", "WAITLISTED");
            waitlisted.add(id(r.getBody()));
            waitlistedCandidates.add(candidate);
        }
        assertThat(seatsAvailable(sessionId)).isZero();
        return new Setup(sessionId, booked, waitlisted, waitlistedCandidates);
    }

    private Setup standardSetup(int minFree, int maxFree, int waitlist) {
        return fullSessionWithWaitlist("s.accommodation_capacity = 0 AND " + freeSeats(minFree, maxFree),
                new String[waitlist]);
    }

    @Test
    void ac1_cancelOnFullSession_offersPositionOne_seatHeld_notBookableByOthers() throws SQLException {
        Setup s = standardSetup(1, 4, 3);
        cancel(s.booked().get(0));

        Map<String, Object> offer = pendingOffer(s.waitlisted().get(0));
        assertThat(offer).containsEntry("status", "PENDING").containsEntry("seatKind", "STANDARD")
                .containsEntry("waitlistPosition", 1);
        assertThat(pendingOfferCount(s.sessionId())).isEqualTo(1);
        Map<String, Object> session = get("/api/v2/sessions/" + s.sessionId());
        assertThat(session).containsEntry("seatsAvailable", 0).containsEntry("offeredSeats", 1);
        assertThat(eventTypes(s.sessionId(), 2)).containsExactly("BookingCancelled", "SeatOffered");

        // the held seat is not bookable: a new booker is waitlisted behind position 3
        String other = eligibleCandidates(s.sessionId(), 1).get(0);
        ResponseEntity<Map<String, Object>> late = book(s.sessionId(), other, null);
        assertThat(late.getBody()).containsEntry("status", "WAITLISTED").containsEntry("waitlistPosition", 4);

        List<Map<String, Object>> waitlist = list("/api/v2/sessions/" + s.sessionId() + "/waitlist");
        assertThat(waitlist.get(0)).containsEntry("offerState", "OFFERED").containsEntry("position", 1);
        assertThat(waitlist.get(0).get("offerExpiresAt")).isNotNull();
        assertThat(waitlist.get(1)).containsEntry("offerState", "WAITING");

        // My bookings shows the pending offer on the waitlisted booking
        Map<String, Object> mine = list("/api/v2/candidates/" + s.waitlistedCandidates().get(0) + "/bookings").stream()
                .filter(b -> id(b) == s.waitlisted().get(0)).findFirst().orElseThrow();
        assertThat(mine.get("pendingOffer")).isNotNull();

        // relayed: legacy shows the held seat (SeatsAvailable 0, OfferExpiresAt set) and reconciles with no drift
        assertThat(relay.drain(100).failed()).isZero();
        String legacyRef = legacyRef(s.waitlisted().get(0));
        assertThat(legacyRow("SELECT CASE WHEN OfferExpiresAt IS NULL THEN 'none' ELSE 'held' END, Position FROM dbo.Waitlist "
                + "WHERE BookingId = " + legacyRef)).containsExactly("held", "1");
        assertThat(legacyInt("SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = " + s.sessionId())).isZero();
        assertReconciled();
    }

    @Test
    void ac2_acceptBeforeExpiry_booked_entryRemoved_positionsShift_legacyMatchesAfterRelay() throws SQLException {
        Setup s = standardSetup(1, 4, 3);
        cancel(s.booked().get(0));
        long offerId = id(pendingOffer(s.waitlisted().get(0)), "offerId");

        ResponseEntity<Map<String, Object>> accepted = post("/api/v2/offers/" + offerId + "/accept");
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accepted.getBody()).containsEntry("status", "ACCEPTED");
        assertThat(get("/api/v2/bookings/" + s.waitlisted().get(0))).containsEntry("status", "BOOKED")
                .containsEntry("waitlistPosition", null);
        List<Map<String, Object>> waitlist = list("/api/v2/sessions/" + s.sessionId() + "/waitlist");
        assertThat(waitlist).extracting(w -> id(w)).containsExactly(s.waitlisted().get(1), s.waitlisted().get(2));
        assertThat(waitlist).extracting(w -> w.get("position")).containsExactly(1, 2);
        assertThat(seatsAvailable(s.sessionId())).isZero();
        assertThat(pendingOfferCount(s.sessionId())).isZero();
        assertThat(eventTypes(s.sessionId(), 1)).containsExactly("SeatOfferAccepted");

        // a second accept is a conflict
        ResponseEntity<Map<String, Object>> again = post("/api/v2/offers/" + offerId + "/accept");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody()).containsEntry("error", "OFFER_NOT_PENDING");

        assertThat(relay.drain(100).failed()).isZero();
        String acceptedRef = legacyRef(s.waitlisted().get(0));
        assertThat(legacyRow("SELECT Status FROM dbo.Booking WHERE BookingId = " + acceptedRef)).containsExactly("BOOKED");
        assertThat(legacyInt("SELECT COUNT(*) FROM dbo.Waitlist WHERE BookingId = " + acceptedRef)).isZero();
        assertThat(legacyRow("SELECT Position FROM dbo.Waitlist WHERE BookingId = " + legacyRef(s.waitlisted().get(1))))
                .containsExactly("1");
        assertThat(legacyRow("SELECT Position FROM dbo.Waitlist WHERE BookingId = " + legacyRef(s.waitlisted().get(2))))
                .containsExactly("2");
        assertReconciled();
    }

    @Test
    void ac3_declineThenExpiry_offerMovesOn_andWithNobodyLeftTheSeatBecomesAvailable() throws SQLException {
        Setup s = standardSetup(1, 4, 2);
        cancel(s.booked().get(0));
        long first = id(pendingOffer(s.waitlisted().get(0)), "offerId");

        // decline (through the /bookings alias): position 1 leaves, position 2 moves up and gets the offer
        ResponseEntity<Map<String, Object>> declined = post("/api/v2/bookings/" + s.waitlisted().get(0) + "/offer/decline");
        assertThat(declined.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(declined.getBody()).containsEntry("status", "DECLINED").containsEntry("offerId", (int) first);
        assertThat(get("/api/v2/bookings/" + s.waitlisted().get(0))).containsEntry("status", "CANCELLED");
        Map<String, Object> second = pendingOffer(s.waitlisted().get(1));
        assertThat(second).containsEntry("waitlistPosition", 1);
        assertThat(eventTypes(s.sessionId(), 2)).containsExactly("SeatOfferDeclined", "SeatOffered");
        assertThat(seatsAvailable(s.sessionId())).isZero();

        // expiry: accepting at/after expires_at is rejected before the job runs, then the job expires it
        // backdate expires_at instead of moving the clock: outbox created_at must stay monotonic for the other ITs
        long secondId = id(second, "offerId");
        java.time.LocalDateTime now = java.time.LocalDateTime.now(clock);
        db.update("UPDATE seat_offer SET offered_at = ?, expires_at = ? WHERE offer_id = ?",
                java.sql.Timestamp.valueOf(now.minusHours(25)), java.sql.Timestamp.valueOf(now.minusMinutes(1)), secondId);
        ResponseEntity<Map<String, Object>> late = post("/api/v2/offers/" + secondId + "/accept");
        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(late.getBody()).containsEntry("error", "OFFER_EXPIRED");
        assertThat(get("/api/v2/offers/" + secondId)).containsEntry("status", "PENDING");

        assertThat(expiryJob.runOnce()).isGreaterThanOrEqualTo(1);
        assertThat(get("/api/v2/offers/" + secondId)).containsEntry("status", "EXPIRED");
        assertThat(get("/api/v2/bookings/" + s.waitlisted().get(1))).containsEntry("status", "CANCELLED");
        assertThat(eventTypes(s.sessionId(), 1)).containsExactly("SeatOfferExpired");
        // nobody left: the seat is plainly available again
        assertThat(seatsAvailable(s.sessionId())).isEqualTo(1);
        assertThat(list("/api/v2/sessions/" + s.sessionId() + "/waitlist")).isEmpty();

        assertThat(relay.drain(100).failed()).isZero();
        assertThat(legacyInt("SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = " + s.sessionId())).isEqualTo(1);
        assertReconciled();
    }

    @Test
    void ac4_et25AtPositionOneWithoutAccommodationSeat_isSkippedAndKept_nextStandardCandidateOffered() throws SQLException {
        // one free accommodation seat; waitlist: ET25, ET50, standard
        Setup s = fullSessionWithWaitlist("s.accommodation_capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED') AND b.accommodation_code IN ('ET25','ET50')) = 1 AND " + freeSeats(1, 4),
                "ET25", "ET50", null);
        // a capacity increase frees one seat: ET25 at position 1 gets it as an accommodation seat and accepts
        int capacity = ((Number) get("/api/v2/sessions/" + s.sessionId()).get("capacity")).intValue();
        assertThat(changeCapacity(s.sessionId(), capacity + 1).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> accOffer = pendingOffer(s.waitlisted().get(0));
        assertThat(accOffer).containsEntry("seatKind", "ACCOMMODATION");
        assertThat(post("/api/v2/offers/" + id(accOffer, "offerId") + "/accept").getStatusCode()).isEqualTo(HttpStatus.OK);

        // now ET50 is position 1 and no accommodation seat is free: a standard cancellation skips it
        cancel(s.booked().get(0));
        List<Map<String, Object>> waitlist = list("/api/v2/sessions/" + s.sessionId() + "/waitlist");
        assertThat(waitlist).extracting(w -> id(w)).containsExactly(s.waitlisted().get(1), s.waitlisted().get(2));
        assertThat(waitlist.get(0)).containsEntry("position", 1).containsEntry("offerState", "SKIPPED")
                .containsEntry("accommodationCode", "ET50");
        assertThat((String) waitlist.get(0).get("skipReason")).contains("ET50");
        assertThat(waitlist.get(1)).containsEntry("position", 2).containsEntry("offerState", "OFFERED");
        assertThat(pendingOffer(s.waitlisted().get(2))).containsEntry("seatKind", "STANDARD");
        assertThat(offerCount("booking_id = " + s.waitlisted().get(1))).isZero();
        assertThat(seatsAvailable(s.sessionId())).isZero();

        assertThat(relay.drain(100).failed()).isZero();
        assertReconciled();
    }

    @Test
    void ac5_twoSeatsFreeAtOnce_twoOffersInPositionOrder() throws SQLException {
        Setup s = standardSetup(1, 4, 3);
        int capacity = ((Number) get("/api/v2/sessions/" + s.sessionId()).get("capacity")).intValue();
        assertThat(changeCapacity(s.sessionId(), capacity + 2).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(pendingOffer(s.waitlisted().get(0))).containsEntry("waitlistPosition", 1);
        assertThat(pendingOffer(s.waitlisted().get(1))).containsEntry("waitlistPosition", 2);
        assertThat(offerCount("booking_id = " + s.waitlisted().get(2))).isZero();
        assertThat(pendingOfferCount(s.sessionId())).isEqualTo(2);
        assertThat(seatsAvailable(s.sessionId())).isZero();
        assertThat(eventTypes(s.sessionId(), 3)).containsExactly("CapacityChanged", "SeatOffered", "SeatOffered");
        assertThat(relay.drain(100).failed()).isZero();
        assertReconciled();
    }

    @Test
    void concurrency_parallelCancelsAndCapacityIncrease_exactlyOneOfferPerFreedSeat_noCandidateTwice() throws Exception {
        Setup s = standardSetup(3, 6, 6);
        int capacity = ((Number) get("/api/v2/sessions/" + s.sessionId()).get("capacity")).intValue();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            long bookingId = s.booked().get(i);
            tasks.add(() -> http.exchange("/api/v2/bookings/" + bookingId, HttpMethod.DELETE, null, MAP).getStatusCode().value());
        }
        tasks.add(() -> changeCapacity(s.sessionId(), capacity + 1).getStatusCode().value());
        assertThat(runTogether(tasks)).containsOnly(200);

        // 3 cancellations + 1 new seat = 4 freed seats -> positions 1-4 hold exactly one offer each
        assertThat(pendingOfferCount(s.sessionId())).isEqualTo(4);
        assertThat(db.queryForList("SELECT booking_id FROM seat_offer WHERE session_id = ? AND status = 'PENDING' ORDER BY waitlist_position",
                Long.class, s.sessionId())).containsExactlyElementsOf(s.waitlisted().subList(0, 4));
        assertThat(db.queryForList("SELECT booking_id FROM seat_offer WHERE session_id = ? GROUP BY booking_id HAVING COUNT(*) > 1",
                Long.class, s.sessionId())).isEmpty();
        assertThat(seatsAvailable(s.sessionId())).isZero();

        // accept and decline racing on the same offer: exactly one wins, the other gets 409
        long offerId = id(pendingOffer(s.waitlisted().get(0)), "offerId");
        List<Integer> statuses = runTogether(List.of(
                () -> post("/api/v2/offers/" + offerId + "/accept").getStatusCode().value(),
                () -> post("/api/v2/offers/" + offerId + "/decline").getStatusCode().value()));
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String status = (String) get("/api/v2/offers/" + offerId).get("status");
        int expectedPending = "ACCEPTED".equals(status) ? 3 : 4;
        assertThat(pendingOfferCount(s.sessionId())).isEqualTo(expectedPending);
        Map<String, Object> session = get("/api/v2/sessions/" + s.sessionId());
        assertThat(((Number) session.get("bookedSeats")).intValue() + ((Number) session.get("offeredSeats")).intValue())
                .isEqualTo(((Number) session.get("capacity")).intValue());
        assertThat(db.queryForList("SELECT candidate_id FROM seat_offer WHERE session_id = ? AND status = 'PENDING' "
                + "GROUP BY candidate_id HAVING COUNT(*) > 1", Integer.class, s.sessionId())).isEmpty();

        assertThat(relay.drain(200).failed()).isZero();
        assertReconciled();
    }

    @Test
    void ac6_fullOfferCycle_reconciliationReportsZeroMismatches() throws SQLException {
        Setup s = standardSetup(2, 5, 3);
        cancel(s.booked().get(0));
        relay.drain(100);
        assertReconciled(); // held seat, no false drift
        post("/api/v2/offers/" + id(pendingOffer(s.waitlisted().get(0)), "offerId") + "/decline");
        relay.drain(100);
        assertReconciled();
        post("/api/v2/offers/" + id(pendingOffer(s.waitlisted().get(1)), "offerId") + "/accept");
        // cancelling a waitlisted booking that holds an offer withdraws it and re-offers the seat
        cancel(s.booked().get(1));
        assertThat(pendingOffer(s.waitlisted().get(2))).containsEntry("waitlistPosition", 1);
        cancel(s.waitlisted().get(2));
        assertThat(db.queryForObject("SELECT status FROM seat_offer WHERE booking_id = ?", String.class, s.waitlisted().get(2)))
                .isEqualTo("WITHDRAWN");
        assertThat(seatsAvailable(s.sessionId())).isEqualTo(1);
        assertThat(relay.drain(100).failed()).isZero();
        assertReconciled();
    }

    @Test
    void errors_unknownOffer404_andExistingErrorShape() {
        ResponseEntity<Map<String, Object>> r = post("/api/v2/offers/987654321/accept");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).containsEntry("error", "OFFER_NOT_FOUND").containsKeys("message", "legacyCode");
        assertThat(post("/api/v2/offers/987654321/decline").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.exchange("/api/v2/offers/987654321", HttpMethod.GET, null, MAP).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- helpers

    private void assertReconciled() {
        ReconciliationService.Report report = reconciliation.reconcile(false);
        assertThat(report.mismatches()).isEmpty();
        assertThat(report.seatDriftRows()).isEmpty();
        assertThat(report.relay().pendingEvents()).isZero();
    }

    private static List<Integer> runTogether(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> t : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return t.call();
                }));
            }
            start.countDown();
            List<Integer> out = new ArrayList<>();
            for (Future<Integer> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private Map<String, Object> pendingOffer(long bookingId) {
        List<Map<String, Object>> rows = db.queryForList("SELECT offer_id FROM seat_offer WHERE booking_id = ? AND status = 'PENDING'",
                bookingId);
        assertThat(rows).as("pending offer for booking " + bookingId).hasSize(1);
        return get("/api/v2/offers/" + rows.get(0).get("offer_id"));
    }

    private int pendingOfferCount(int sessionId) {
        return offerCount("session_id = " + sessionId + " AND status = 'PENDING'");
    }

    private int offerCount(String where) {
        return db.queryForObject("SELECT COUNT(*) FROM seat_offer WHERE " + where, Integer.class);
    }

    private List<String> eventTypes(int sessionId, int last) {
        List<String> types = db.queryForList("SELECT TOP (?) event_type FROM outbox_event WHERE session_id = ? ORDER BY event_id DESC",
                String.class, last, sessionId);
        java.util.Collections.reverse(types);
        return types;
    }

    private int seatsAvailable(int sessionId) {
        return ((Number) get("/api/v2/sessions/" + sessionId).get("seatsAvailable")).intValue();
    }

    private String legacyRef(long bookingId) {
        return db.queryForObject("SELECT CAST(COALESCE(legacy_booking_id, booking_id) AS VARCHAR(12)) FROM booking "
                + "WHERE booking_id = ?", String.class, bookingId);
    }

    private static long id(Map<String, Object> body) {
        return id(body, "bookingId");
    }

    private static long id(Map<String, Object> body, String key) {
        return ((Number) body.get(key)).longValue();
    }

    private static String freeSeats(int min, int max) {
        return "s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED')) "
                + "BETWEEN " + min + " AND " + max + " AND NOT EXISTS (SELECT 1 FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status = 'WAITLISTED')";
    }

    private void cancel(long bookingId) {
        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/bookings/" + bookingId + "?reason=it", HttpMethod.DELETE,
                null, MAP);
        assertThat(r.getStatusCode()).as(r.toString()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map<String, Object>> post(String path) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(Map.of("actor", "it")), MAP);
    }

    private ResponseEntity<Map<String, Object>> book(int sessionId, String candidate, String accommodation) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateNumber", candidate);
        body.put("accommodationCode", accommodation);
        body.put("channel", "PORTAL");
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

    private List<Map<String, Object>> list(String path) {
        ResponseEntity<List<Map<String, Object>>> r = http.exchange(path, HttpMethod.GET, null, LIST);
        assertThat(r.getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    private int openSession(String condition) {
        return db.queryForObject("SELECT TOP 1 s.session_id FROM exam_session s WHERE s.status = 'OPEN' AND s.locally_modified = 0 "
                + "AND s.starts_at > '2026-11-20' AND " + condition + " ORDER BY NEWID()", Integer.class);
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
            List<String> out = new ArrayList<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                out.add(rs.getString(i));
            }
            return out;
        }
    }
}
