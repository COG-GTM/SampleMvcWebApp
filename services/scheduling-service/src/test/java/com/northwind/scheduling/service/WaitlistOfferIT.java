package com.northwind.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.northwind.scheduling.SqlServerTestBase;
import com.northwind.scheduling.domain.BookingStatus;
import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.SchedulingException;
import com.northwind.scheduling.importer.SnapshotImporter;
import com.northwind.scheduling.persistence.Rows;

/**
 * MBA-3829 waitlist auto-offer on Testcontainers SQL Server. Fixture session 163: capacity 3 (1 accommodation seat),
 * BOOKED 10 (ET25), 11, 12; WAITLISTED 13 at position 1. Each test adds NW00000914/915 at positions 2/3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SqlServerTestBase.FixtureLegacy.class)
class WaitlistOfferIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };

    @Autowired SchedulingService service;
    @Autowired WaitlistOfferPolicy policy;
    @Autowired SnapshotImporter importer;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;

    int w14;
    int w15;

    @BeforeEach
    void reset() {
        importer.run(SnapshotImporter.Mode.RESET);
        jdbc.update("DELETE FROM outbox_event");
        w14 = waitlist(163, "NW00000914");
        w15 = waitlist(163, "NW00000915");
    }

    int waitlist(int session, String cand) {
        var o = service.book(new SchedulingService.BookCommand(session, cand, null, null, "PORTAL"));
        assertThat(o.status()).isEqualTo(BookingStatus.WAITLISTED);
        return o.bookingId();
    }

    List<Map<String, Object>> offers(int session) {
        return jdbc.queryForList("SELECT offer_id, booking_id, position, status, offered_at, expires_at FROM seat_offer "
                + "WHERE session_id = ? ORDER BY offer_id", session);
    }

    List<Integer> pendingBookings(int session) {
        return jdbc.queryForList("SELECT booking_id FROM seat_offer WHERE session_id = ? AND status = 'PENDING' "
                + "ORDER BY position", Integer.class, session);
    }

    int pendingOfferId(int bookingId) {
        return jdbc.queryForObject("SELECT offer_id FROM seat_offer WHERE booking_id = ? AND status = 'PENDING'",
                Integer.class, bookingId);
    }

    List<Integer> waitlistOrder(int session) {
        return service.waitlist(session).stream().map(Rows.WaitlistEntry::bookingId).toList();
    }

    List<Integer> waitlistPositions(int session) {
        return service.waitlist(session).stream().map(Rows.WaitlistEntry::position).toList();
    }

    List<String> events() {
        return jdbc.queryForList("SELECT event_type + ':' + CONVERT(VARCHAR(12), aggregate_id) FROM outbox_event "
                + "ORDER BY event_seq", String.class);
    }

    ResponseEntity<Map<String, Object>> post(String url) {
        return http.exchange(url, HttpMethod.POST, null, MAP);
    }

    @Test
    void cancelOffersTheSeatToPositionOneAndHoldsIt() {
        LocalDateTime before = LocalDateTime.now().withNano(0);
        service.cancel(11, "test");

        assertThat(pendingBookings(163)).containsExactly(13);
        Map<String, Object> offer = offers(163).get(0);
        assertThat(offer).containsEntry("position", 1).containsEntry("status", "PENDING");
        LocalDateTime expires = ((java.sql.Timestamp) offer.get("expires_at")).toLocalDateTime();
        assertThat(Duration.between(before, expires)).isBetween(Duration.ofHours(24).minusMinutes(1),
                Duration.ofHours(24).plusMinutes(1));

        Rows.Session s = service.session(163);
        assertThat(s.seats().seatsAvailable()).isZero();
        assertThat(s.held()).isEqualTo(1);
        // the held seat is not bookable by others
        assertThat(service.book(new SchedulingService.BookCommand(163, "NW00000916", null, null, "PORTAL")).status())
                .isEqualTo(BookingStatus.WAITLISTED);
        assertThat(events()).containsSubsequence("BookingCancelled:11", "SeatOffered:13");

        Map<String, Object> booking = http.exchange("/api/v2/bookings/13", HttpMethod.GET, null, MAP).getBody();
        assertThat(booking).containsEntry("status", "WAITLISTED").containsEntry("offerStatus", "PENDING")
                .containsEntry("offerId", offer.get("offer_id"));
        Map<String, Object> session = http.exchange("/api/v2/sessions/163", HttpMethod.GET, null, MAP).getBody();
        assertThat(session).containsEntry("seatsAvailable", 0).containsEntry("heldSeats", 1);
        List<Map<String, Object>> wl = http.exchange("/api/v2/sessions/163/waitlist", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {
                }).getBody();
        assertThat(wl.get(0)).containsEntry("bookingId", 13).containsEntry("offerStatus", "PENDING");
        assertThat(wl.get(1)).containsEntry("bookingId", w14).containsEntry("offerStatus", null);
    }

    @Test
    void acceptBooksTheCandidateAndShiftsTheWaitlist() {
        service.cancel(11, "test");
        int offerId = pendingOfferId(13);

        ResponseEntity<Map<String, Object>> r = post("/api/v2/offers/" + offerId + "/accept");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> booking = (Map<String, Object>) r.getBody().get("booking");
        @SuppressWarnings("unchecked")
        Map<String, Object> offer = (Map<String, Object>) r.getBody().get("offer");
        assertThat(booking).containsEntry("status", "BOOKED").containsEntry("waitlistPosition", null)
                .containsEntry("offerStatus", "ACCEPTED");
        assertThat(offer).containsEntry("status", "ACCEPTED");

        assertThat(waitlistOrder(163)).containsExactly(w14, w15);
        assertThat(waitlistPositions(163)).containsExactly(1, 2);
        Rows.Session s = service.session(163);
        assertThat(s.occupied()).isEqualTo(3);
        assertThat(s.held()).isZero();
        assertThat(pendingBookings(163)).isEmpty();
        assertThat(events()).last().isEqualTo("SeatOfferAccepted:13");
        String payload = jdbc.queryForObject("SELECT TOP 1 payload FROM outbox_event WHERE event_type = 'SeatOfferAccepted'",
                String.class);
        assertThat(payload).contains("\"bookingId\":13").contains("\"bookingId\":" + w14).contains("\"bookingId\":" + w15);

        ResponseEntity<Map<String, Object>> again = post("/api/v2/offers/" + offerId + "/accept");
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody()).containsEntry("error", "OFFER_NOT_PENDING");
        assertThat(post("/api/v2/offers/99999/accept").getBody()).containsEntry("error", "OFFER_NOT_FOUND");
        assertThat(http.exchange("/api/v2/offers/" + offerId, HttpMethod.GET, null, MAP).getBody())
                .containsEntry("status", "ACCEPTED").containsEntry("bookingId", 13);
        Object center = http.exchange("/api/v2/sessions/163", HttpMethod.GET, null, MAP).getBody().get("centerCode");
        assertThat(http.exchange("/api/v2/internal/routing/resolve?offerId=" + offerId, HttpMethod.GET, null, MAP).getBody())
                .containsEntry("centerCode", center);
        assertThat(http.exchange("/api/v2/internal/routing/resolve?offerId=99999", HttpMethod.GET, null, MAP).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void declineMovesTheOfferToTheNextCandidate() {
        service.cancel(11, "test");
        int offerId = pendingOfferId(13);

        ResponseEntity<Map<String, Object>> r = post("/api/v2/offers/" + offerId + "/decline");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(service.booking(13).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(service.booking(13).cancelReason()).isEqualTo("Waitlist offer declined");
        assertThat(waitlistOrder(163)).containsExactly(w14, w15);
        assertThat(waitlistPositions(163)).containsExactly(1, 2);
        assertThat(pendingBookings(163)).containsExactly(w14);
        assertThat(service.session(163).seats().seatsAvailable()).isZero();
        assertThat(events()).containsSubsequence("SeatOfferDeclined:13", "SeatOffered:" + w14);
    }

    @Test
    void expiryMovesOnAndTheLastExpiryReleasesTheSeat() {
        service.cancel(11, "test");
        service.cancel(w15, "leaves");
        jdbc.update("UPDATE seat_offer SET expires_at = DATEADD(MINUTE, -1, SYSDATETIME()) WHERE status = 'PENDING'");
        int first = pendingOfferId(13);
        assertThat(post("/api/v2/offers/" + first + "/accept").getBody()).containsEntry("error", "OFFER_NOT_PENDING");

        assertThat(service.dueOfferIds()).containsExactly(first);
        assertThat(service.expireOffer(first)).isTrue();
        assertThat(service.expireOffer(first)).isFalse();
        assertThat(service.booking(13).status()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(pendingBookings(163)).containsExactly(w14);
        assertThat(service.waitlist(163)).extracting(Rows.WaitlistEntry::position).containsExactly(1);
        assertThat(events()).containsSubsequence("SeatOfferExpired:13", "SeatOffered:" + w14);

        jdbc.update("UPDATE seat_offer SET expires_at = DATEADD(MINUTE, -1, SYSDATETIME()) WHERE status = 'PENDING'");
        for (int id : service.dueOfferIds()) {
            service.expireOffer(id);
        }
        assertThat(pendingBookings(163)).isEmpty();
        assertThat(service.waitlist(163)).isEmpty();
        Rows.Session s = service.session(163);
        assertThat(s.held()).isZero();
        assertThat(s.seats().seatsAvailable()).isEqualTo(1);
        assertThat(service.book(new SchedulingService.BookCommand(163, "NW00000916", null, null, "PORTAL")).status())
                .isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void extraTimeCandidateIsSkippedAndKeepsPlaceWhileNoAccommodationSeatIsFree() {
        // session 300: capacity 3, 1 accommodation seat; 3 standard bookings, then ET25, ET50, standard waitlisted
        service.changeCapacity(300, 3, "test");
        for (String c : List.of("NW00000921", "NW00000922", "NW00000923")) {
            service.book(new SchedulingService.BookCommand(300, c, null, null, "PORTAL"));
        }
        int et25 = service.book(new SchedulingService.BookCommand(300, "NW00000940", "ET25", null, "PORTAL")).bookingId();
        int et50 = service.book(new SchedulingService.BookCommand(300, "NW00000926", "ET50", null, "PORTAL")).bookingId();
        int std = waitlist(300, "NW00000925");
        assertThat(service.booking(et25).accommodationCode()).isEqualTo("ET25");
        List<Integer> booked = jdbc.queryForList("SELECT booking_id FROM booking WHERE session_id = 300 AND status = 'BOOKED' "
                + "ORDER BY booking_id", Integer.class);

        service.cancel(booked.get(0), "test");   // ET25 at position 1 takes the seat and the accommodation seat
        assertThat(pendingBookings(300)).containsExactly(et25);
        service.cancel(booked.get(1), "test");   // no accommodation seat free: ET50 skipped, standard entry offered
        assertThat(pendingBookings(300)).containsExactly(et25, std);
        assertThat(waitlistOrder(300)).containsExactly(et25, et50, std);
        assertThat(waitlistPositions(300)).containsExactly(1, 2, 3);
    }

    @Test
    void capacityIncreaseOffersInPositionOrderAndDecreaseWithdrawsNewestOffers() {
        service.changeCapacity(163, 5, "bigger room");
        assertThat(pendingBookings(163)).containsExactly(13, w14);
        assertThat(service.session(163).seats().seatsAvailable()).isZero();

        service.changeCapacity(163, 4, "smaller again");
        assertThat(pendingBookings(163)).containsExactly(13);
        assertThat(waitlistOrder(163)).containsExactly(13, w14, w15);
        assertThat(events()).contains("SeatOfferWithdrawn:" + w14);

        service.changeCapacity(163, 3, "original");
        assertThat(pendingBookings(163)).isEmpty();
        assertThat(service.session(163).held()).isZero();
        assertThat(waitlistPositions(163)).containsExactly(1, 2, 3);
    }

    @Test
    void rescheduleOutOffersTheSeatAndCancellingAnOfferedBookingPassesItOn() {
        service.reschedule(11, 154);
        assertThat(pendingBookings(163)).containsExactly(13);
        List<String> ev = events();
        assertThat(ev).contains("SeatOffered:13");
        assertThat(ev.stream().filter(e -> e.startsWith("BookingRescheduled:")).findFirst())
                .hasValueSatisfying(r -> assertThat(ev.indexOf(r)).isLessThan(ev.indexOf("SeatOffered:13")));

        service.cancel(13, "changed my mind");
        assertThat(jdbc.queryForObject("SELECT status FROM seat_offer WHERE booking_id = 13", String.class))
                .isEqualTo("WITHDRAWN");
        assertThat(pendingBookings(163)).containsExactly(w14);
        assertThat(service.session(163).seats().seatsAvailable()).isZero();
    }

    @Test
    void closedSessionsMakeNoOffers() {
        jdbc.update("UPDATE exam_session SET status = 'CLOSED' WHERE session_id = 163");
        assertThat(policy.offerFreedSeats(163, "test")).isEmpty();
    }

    @Test
    void offerErrorsUseTheOfferVocabulary() {
        assertThatThrownBy(() -> service.acceptOffer(424242)).isInstanceOf(SchedulingException.class)
                .extracting(e -> ((SchedulingException) e).code()).isEqualTo(ErrorCode.OFFER_NOT_FOUND);
        ResponseEntity<Map<String, Object>> r = post("/api/v2/offers/424242/decline");
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody()).containsEntry("error", "OFFER_NOT_FOUND").containsEntry("legacyCode", null);
    }

    @RepeatedTest(3)
    void twoSeatsFreedConcurrentlyGiveTwoOffersInPositionOrderAndNoDoubleOffer() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Callable<Object>> jobs = List.of(
                    () -> { go.await(); return service.cancel(11, "c1"); },
                    () -> { go.await(); return service.cancel(12, "c2"); },
                    () -> { go.await(); return policy.ttl(); });
            List<Future<Object>> futures = jobs.stream().map(pool::submit).toList();
            go.countDown();
            for (Future<Object> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertThat(pendingBookings(163)).containsExactly(13, w14);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seat_offer", Integer.class)).isEqualTo(2);
        Rows.Session s = service.session(163);
        assertThat(s.held()).isEqualTo(2);
        assertThat(s.seats().seatsAvailable()).isZero();
    }

    @RepeatedTest(3)
    void concurrentCancelAndCapacityIncreaseNeverOfferOneBookingTwice() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Future<?> a = pool.submit(() -> { go.await(); return service.cancel(11, "c1"); });
            Future<?> b = pool.submit(() -> { go.await(); return service.changeCapacity(163, 4, "up"); });
            go.countDown();
            a.get();
            b.get();
        } finally {
            pool.shutdown();
        }
        assertThat(pendingBookings(163)).containsExactly(13, w14);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seat_offer", Integer.class)).isEqualTo(2);
        assertThat(service.session(163).seats().seatsAvailable()).isZero();
    }
}
