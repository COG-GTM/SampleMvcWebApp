package com.northwind.scheduling.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.northwind.scheduling.contract.support.Db;
import com.northwind.scheduling.contract.support.Fixtures;
import com.northwind.scheduling.contract.support.Fixtures.Cand;
import com.northwind.scheduling.contract.support.Fixtures.Ref;
import com.northwind.scheduling.contract.support.Http;
import com.northwind.scheduling.contract.support.Http.Resp;
import com.northwind.scheduling.contract.support.LegacyEstate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins booking-api's partner-facing reads of ExamSession/Booking (SessionDao, BookingDao, SeatCounter) and its direct
 * partner write path (BookingDao.insertBooking + SessionDao.decrementSeatsAvailable + insertWaitlist), through the real
 * legacy jar. Phase 2 rewires these onto scheduling-service; the JSON shapes and values below are the parity target.
 * Inventory: BA-01..BA-14.
 */
@DisplayName("booking-api -> ExamSession/Booking/Waitlist: partner reads and writes")
class BookingApiContractTest {

    static Db db;
    static Http api;
    static Fixtures fx;
    static Ref ref;
    static int open1;
    static int cancelledSession;
    static int deliveredSession;
    static Cand alice;
    static int bBooked;
    static int bAttended;
    static int bWaitlisted;
    static int bCancelled;
    static final LocalDateTime S1 = LocalDateTime.of(2027, 3, 10, 9, 30);
    static final LocalDateTime S2 = LocalDateTime.of(2027, 3, 11, 14, 0);
    static final LocalDateTime S0 = LocalDateTime.of(2026, 1, 5, 8, 0);

    @BeforeAll
    static void up() {
        LegacyEstate estate = LegacyEstate.get();
        db = estate.db;
        api = estate.bookingApi;
        fx = new Fixtures(db);
        ref = fx.reference(650, 30);
        open1 = fx.session(ref, S1, 10, 2, 8, "OPEN");
        cancelledSession = fx.session(ref, S2, 10, 0, 10, "CANCELLED");
        deliveredSession = fx.session(ref, S0, 10, 0, 9, "DELIVERED");
        alice = fx.candidate();
        Cand bob = fx.candidate();
        Cand cara = fx.candidate();
        Cand dan = fx.candidate();
        bBooked = fx.booking(open1, alice, "BOOKED", LocalDateTime.of(2026, 10, 1, 10, 0), "ET25", "PARTNER", "PX-1");
        bAttended = fx.booking(open1, bob, "ATTENDED", LocalDateTime.of(2026, 10, 2, 10, 0));
        bWaitlisted = fx.booking(open1, cara, "WAITLISTED", LocalDateTime.of(2026, 10, 3, 10, 0));
        fx.waitlist(open1, cara, bWaitlisted, 1);
        bCancelled = fx.booking(open1, dan, "CANCELLED", LocalDateTime.of(2026, 10, 4, 10, 0));
        fx.booking(deliveredSession, alice, "ATTENDED", LocalDateTime.of(2025, 12, 1, 9, 0));
        fx.booking(cancelledSession, alice, "CANCELLED", LocalDateTime.of(2026, 10, 1, 10, 0));
    }

    static Map<String, Object> asMap(JsonNode n) {
        return Http.JSON.convertValue(n, Map.class);
    }

    @Test
    @DisplayName("GET /api/v1/sessions/{id}: SessionInfo shape and values; bookedSeats = BOOKED+ATTENDED (SeatCounter)")
    // seatsAvailable is 7, not the 8 the fixture inserted: the PARTNER BOOKED fixture row fired trg_Booking_SeatSync (HB-01).
    void sessionById() {
        Resp r = api.get("/api/v1/sessions/" + open1);
        assertThat(r.status()).isEqualTo(200);
        Map<String, Object> m = asMap(r.body());
        assertThat(m).containsOnlyKeys("sessionId", "examCode", "examTitle", "centerCode", "centerName", "roomName",
                "startsAt", "capacity", "accommodationCapacity", "seatsAvailable", "status", "bookedSeats");
        assertThat(m).containsEntry("sessionId", open1).containsEntry("examCode", ref.examCode())
                .containsEntry("centerCode", ref.centerCode()).containsEntry("startsAt", "2027-03-10T09:30:00")
                .containsEntry("capacity", 10).containsEntry("accommodationCapacity", 2)
                .containsEntry("seatsAvailable", 7).containsEntry("status", "OPEN").containsEntry("bookedSeats", 2);
        assertThat((String) m.get("examTitle")).startsWith("Contract Exam ");
        assertThat((String) m.get("centerName")).startsWith("Contract Centre ");
        assertThat((String) m.get("roomName")).startsWith("Room ");
    }

    @Test
    @DisplayName("HB-13: GET /api/v1/sessions lists every status (no OPEN filter), ordered StartsAt, SessionId; bookedSeats null")
    void sessionSearchHasNoStatusFilter() {
        Resp r = api.get("/api/v1/sessions?examCode=" + ref.examCode());
        assertThat(r.status()).isEqualTo(200);
        List<Integer> ids = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        r.body().forEach(n -> {
            ids.add(n.get("sessionId").asInt());
            statuses.add(n.get("status").asText());
            assertThat(n.get("bookedSeats").isNull()).isTrue();
        });
        assertThat(ids).containsExactly(deliveredSession, open1, cancelledSession);
        assertThat(statuses).containsExactly("DELIVERED", "OPEN", "CANCELLED");
    }

    @Test
    @DisplayName("Session search: from/to are whole days, 'to' inclusive; centerCode filter applies")
    void sessionSearchDateWindow() {
        JsonNode one = api.get("/api/v1/sessions?examCode=" + ref.examCode() + "&from=2027-03-10&to=2027-03-10").body();
        assertThat(one).hasSize(1);
        assertThat(one.get(0).get("sessionId").asInt()).isEqualTo(open1);
        JsonNode none = api.get("/api/v1/sessions?centerCode=" + ref.centerCode() + "&from=2027-03-12").body();
        assertThat(none).isEmpty();
    }

    @Test
    @DisplayName("Unknown session: 404 SESSION_NOT_FOUND {error,message}")
    void sessionNotFound() {
        Resp r = api.get("/api/v1/sessions/99999999");
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.body().get("error").asText()).isEqualTo("SESSION_NOT_FOUND");
        assertThat(r.body().get("message").asText()).isEqualTo("Session 99999999 not found");
    }

    @Test
    @DisplayName("GET /api/v1/bookings/{id}: BookingInfo shape and values (channel and externalRef exposed)")
    void bookingById() {
        Resp r = api.get("/api/v1/bookings/" + bBooked);
        assertThat(r.status()).isEqualTo(200);
        Map<String, Object> m = asMap(r.body());
        assertThat(m).containsOnlyKeys("bookingId", "sessionId", "candidateNumber", "examCode", "sessionStartsAt", "status",
                "accommodationCode", "channel", "externalRef", "bookedAt", "cancelledAt", "cancelReason");
        assertThat(m).containsEntry("bookingId", bBooked).containsEntry("sessionId", open1)
                .containsEntry("candidateNumber", alice.candidateNumber()).containsEntry("examCode", ref.examCode())
                .containsEntry("sessionStartsAt", "2027-03-10T09:30:00").containsEntry("status", "BOOKED")
                .containsEntry("accommodationCode", "ET25").containsEntry("channel", "PARTNER")
                .containsEntry("externalRef", "PX-1").containsEntry("bookedAt", "2026-10-01T10:00:00")
                .containsEntry("cancelledAt", null).containsEntry("cancelReason", null);

        Map<String, Object> c = asMap(api.get("/api/v1/bookings/" + bCancelled).body());
        assertThat(c).containsEntry("status", "CANCELLED").containsEntry("channel", "PORTAL")
                .containsEntry("cancelledAt", "2026-10-04T11:00:00").containsEntry("cancelReason", "fixture");
    }

    @Test
    @DisplayName("GET /api/v1/candidates/{n}/bookings: all statuses, ordered BookedAt DESC, BookingId DESC")
    void candidateBookings() {
        JsonNode list = api.get("/api/v1/candidates/" + alice.candidateNumber() + "/bookings").body();
        List<String> seq = new ArrayList<>();
        list.forEach(n -> seq.add(n.get("status").asText() + "@" + n.get("bookedAt").asText()));
        assertThat(seq).as("BookedAt tie broken by BookingId DESC: the CANCELLED row was inserted after the BOOKED one")
                .containsExactly("CANCELLED@2026-10-01T10:00:00", "BOOKED@2026-10-01T10:00:00", "ATTENDED@2025-12-01T09:00:00");
    }

    @Test
    @DisplayName("HB-01/HB-02: partner POST decrements twice (DAO + trigger 032), partner DELETE releases once")
    void partnerBookAndCancelSeatMath() {
        int s = fx.session(ref, LocalDateTime.of(2027, 4, 1, 9, 0), 5, 0, 5, "OPEN");
        Cand c = fx.candidate();
        Resp r = api.postJson("/api/v1/bookings", Map.of("sessionId", s, "candidateNumber", c.candidateNumber(),
                "externalRef", "PX-" + Fixtures.next()));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("status").asText()).isEqualTo("BOOKED");
        int bookingId = r.body().get("bookingId").asInt();
        assertThat(db.one("SELECT Channel, Status, AccommodationCode FROM dbo.Booking WHERE BookingId = ?", bookingId))
                .containsEntry("Channel", "PARTNER").containsEntry("Status", "BOOKED").containsEntry("AccommodationCode", null);
        assertThat(db.count("SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = ?", s)).isEqualTo(3);

        assertThat(api.delete("/api/v1/bookings/" + bookingId).status()).isEqualTo(200);
        assertThat(db.count("SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = ?", s)).isEqualTo(4);
        assertThat(db.scalar("SELECT Status FROM dbo.Booking WHERE BookingId = ?", bookingId)).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Partner booking on a full session: WAITLISTED with Waitlist.Position = MAX(Position)+1, seats untouched")
    void partnerWaitlist() {
        int s = fx.session(ref, LocalDateTime.of(2027, 4, 2, 9, 0), 1, 0, 0, "OPEN");
        Cand first = fx.candidate();
        int existing = fx.booking(s, first, "WAITLISTED", LocalDateTime.of(2026, 10, 1, 9, 0));
        fx.waitlist(s, first, existing, 4);
        Cand c = fx.candidate();
        Resp r = api.postJson("/api/v1/bookings", Map.of("sessionId", s, "candidateNumber", c.candidateNumber()));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("status").asText()).isEqualTo("WAITLISTED");
        int bookingId = r.body().get("bookingId").asInt();
        assertThat(db.count("SELECT Position FROM dbo.Waitlist WHERE BookingId = ?", bookingId)).isEqualTo(5);
        assertThat(db.count("SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = ?", s)).isZero();
    }

    @Test
    @DisplayName("Partner errors: SESSION_NOT_OPEN 409, DUPLICATE_BOOKING 409, CANDIDATE_NOT_FOUND 404, bad accommodation 400")
    void partnerErrors() {
        Cand c = fx.candidate();
        assertThat(api.postJson("/api/v1/bookings", Map.of("sessionId", cancelledSession, "candidateNumber", c.candidateNumber()))
                .body().get("error").asText()).isEqualTo("SESSION_NOT_OPEN");
        assertThat(api.postJson("/api/v1/bookings", Map.of("sessionId", open1, "candidateNumber", alice.candidateNumber()))
                .body().get("error").asText()).isEqualTo("DUPLICATE_BOOKING");
        Resp nf = api.postJson("/api/v1/bookings", Map.of("sessionId", open1, "candidateNumber", "NW99999999"));
        assertThat(nf.status()).isEqualTo(404);
        assertThat(nf.body().get("error").asText()).isEqualTo("CANDIDATE_NOT_FOUND");
        Resp acc = api.postJson("/api/v1/bookings", Map.of("sessionId", open1, "candidateNumber", c.candidateNumber(),
                "accommodationCode", "XX"));
        assertThat(acc.status()).isEqualTo(400);
        assertThat(acc.body().get("error").asText()).isEqualTo("VALIDATION");
    }
}
