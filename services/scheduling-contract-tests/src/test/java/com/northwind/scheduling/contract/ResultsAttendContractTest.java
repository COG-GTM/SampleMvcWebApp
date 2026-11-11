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
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins results-service's cross-boundary write: ResultIngestService.ingest reads Booking/ExamSession/Exam and runs
 * UPDATE dbo.Booking SET Status='ATTENDED' ... WHERE BookingId=? AND Status='BOOKED'. Phase 2 replaces that UPDATE
 * with POST /bookings/{id}/attendance on scheduling-service; these assertions must still hold afterwards.
 * Exercised through the real legacy results-service jar over HTTP. Inventory: RS-01..RS-06.
 */
@DisplayName("results-service -> Booking: ingest marks ATTENDED")
class ResultsAttendContractTest {

    static LegacyEstate estate;
    static Db db;
    static Http rs;
    static Fixtures fx;
    static Ref ref;
    static int pastSession;
    static final LocalDateTime BOOKED_AT = LocalDateTime.now().minusDays(20).truncatedTo(ChronoUnit.SECONDS);

    @BeforeAll
    static void up() {
        estate = LegacyEstate.get();
        db = estate.db;
        rs = estate.resultsService;
        fx = new Fixtures(db);
        ref = fx.reference(650, 30);
        pastSession = fx.session(ref, LocalDateTime.now().minusDays(2).withHour(9).withMinute(0).withSecond(0).withNano(0),
                10, 2, 7, "DELIVERED");
    }

    static Resp post(int bookingId, int score) {
        return rs.postJson("/api/v1/results", Map.of("bookingId", bookingId, "score", score, "scoredAt", "2026-11-01T10:00:00"));
    }

    static Map<String, Object> booking(int id) {
        return db.one("SELECT Status, LastModified, Channel, SessionId FROM dbo.Booking WHERE BookingId = ?", id);
    }

    static int seats(int sessionId) {
        return db.count("SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = ?", sessionId);
    }

    @Test
    @DisplayName("BOOKED booking: 201 CREATED, Booking -> ATTENDED, LastModified bumped, seats/waitlist untouched, INGEST audit")
    void bookedBecomesAttended() {
        Cand c = fx.candidate();
        int b = fx.booking(pastSession, c, "BOOKED", BOOKED_AT);
        int seatsBefore = seats(pastSession);
        int waitBefore = db.count("SELECT COUNT(*) FROM dbo.Waitlist WHERE SessionId = ?", pastSession);

        Resp r = post(b, 700);

        assertThat(r.status()).isEqualTo(201);
        assertThat(r.body().get("status").asText()).isEqualTo("CREATED");
        assertThat(r.body().get("bookingId").asInt()).isEqualTo(b);
        assertThat(r.body().get("score").asInt()).isEqualTo(700);
        assertThat(r.body().get("passed").asBoolean()).isTrue();
        assertThat(r.body().get("resultId").asInt()).isPositive();
        Map<String, Object> row = booking(b);
        assertThat(row.get("Status")).isEqualTo("ATTENDED");
        assertThat((LocalDateTime) row.get("LastModified")).isAfter(BOOKED_AT);
        assertThat(row.get("Channel")).isEqualTo("PORTAL");
        assertThat(seats(pastSession)).as("ATTENDED transition does not touch SeatsAvailable").isEqualTo(seatsBefore);
        assertThat(db.count("SELECT COUNT(*) FROM dbo.Waitlist WHERE SessionId = ?", pastSession)).isEqualTo(waitBefore);
        assertThat(db.one("SELECT CandidateId, ExamId, SessionId, Score, Passed, SourceFile FROM dbo.Result WHERE BookingId = ?", b))
                .containsEntry("CandidateId", c.candidateId()).containsEntry("ExamId", ref.examId())
                .containsEntry("SessionId", pastSession).containsEntry("Score", 700).containsEntry("Passed", true)
                .containsEntry("SourceFile", null);
        int resultId = r.body().get("resultId").asInt();
        assertThat(db.one("SELECT Actor, Detail FROM dbo.AuditLog WHERE EntityName = 'Result' AND EntityId = ? AND Action = 'INGEST'", resultId))
                .containsEntry("Actor", "results-service").containsEntry("Detail", "booking=" + b + " score=700");
    }

    @Test
    @DisplayName("Replay with same score: 200 DUPLICATE, no second Result, Booking stays ATTENDED (idempotent)")
    void replayIsIdempotent() {
        Cand c = fx.candidate();
        int b = fx.booking(pastSession, c, "BOOKED", BOOKED_AT);
        assertThat(post(b, 640).status()).isEqualTo(201);
        LocalDateTime lm = (LocalDateTime) booking(b).get("LastModified");

        Resp r = post(b, 640);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("status").asText()).isEqualTo("DUPLICATE");
        assertThat(r.body().get("passed").asBoolean()).isFalse();
        assertThat(db.count("SELECT COUNT(*) FROM dbo.Result WHERE BookingId = ?", b)).isEqualTo(1);
        assertThat(booking(b)).containsEntry("Status", "ATTENDED").containsEntry("LastModified", lm);
    }

    @Test
    @DisplayName("Replay with different score: 409 DUPLICATE_RESULT, stored result kept, DUPLICATE_REJECTED audit")
    void conflictingReplay() {
        Cand c = fx.candidate();
        int b = fx.booking(pastSession, c, "BOOKED", BOOKED_AT);
        assertThat(post(b, 700).status()).isEqualTo(201);

        Resp r = post(b, 500);

        assertThat(r.status()).isEqualTo(409);
        assertThat(r.body().get("error").asText()).isEqualTo("DUPLICATE_RESULT");
        assertThat(db.one("SELECT Score FROM dbo.Result WHERE BookingId = ?", b)).containsEntry("Score", 700);
        assertThat(booking(b).get("Status")).isEqualTo("ATTENDED");
        assertThat(db.count("SELECT COUNT(*) FROM dbo.AuditLog a JOIN dbo.Result r ON r.ResultId = a.EntityId "
                + "WHERE a.EntityName = 'Result' AND r.BookingId = ? AND a.Action = 'DUPLICATE_REJECTED'", b)).isEqualTo(1);
    }

    @Test
    @DisplayName("CANCELLED booking: 409 BOOKING_NOT_ACTIVE, no Result, status unchanged")
    void cancelledRejected() {
        Cand c = fx.candidate();
        int b = fx.booking(pastSession, c, "CANCELLED", BOOKED_AT);
        Resp r = post(b, 700);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.body().get("error").asText()).isEqualTo("BOOKING_NOT_ACTIVE");
        assertThat(booking(b).get("Status")).isEqualTo("CANCELLED");
        assertThat(db.count("SELECT COUNT(*) FROM dbo.Result WHERE BookingId = ?", b)).isZero();
    }

    @Test
    @DisplayName("WAITLISTED booking: 409 BOOKING_NOT_ACTIVE, waitlist row kept")
    void waitlistedRejected() {
        Cand c = fx.candidate();
        int b = fx.booking(pastSession, c, "WAITLISTED", BOOKED_AT);
        fx.waitlist(pastSession, c, b, 1);
        Resp r = post(b, 700);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.body().get("error").asText()).isEqualTo("BOOKING_NOT_ACTIVE");
        assertThat(booking(b).get("Status")).isEqualTo("WAITLISTED");
        assertThat(db.count("SELECT COUNT(*) FROM dbo.Waitlist WHERE BookingId = ?", b)).isEqualTo(1);
    }

    @Test
    @DisplayName("HB-18: future OPEN session is accepted and marked ATTENDED (no StartsAt or session-status check)")
    void futureSessionAccepted() {
        int future = fx.session(ref, LocalDateTime.now().plusDays(30).withHour(9).withMinute(0).withSecond(0).withNano(0),
                10, 2, 9, "OPEN");
        Cand c = fx.candidate();
        int b = fx.booking(future, c, "BOOKED", BOOKED_AT);
        Resp r = post(b, 700);
        assertThat(r.status()).isEqualTo(201);
        assertThat(booking(b).get("Status")).isEqualTo("ATTENDED");
        assertThat(seats(future)).isEqualTo(9);
    }

    @Test
    @DisplayName("Session status is not checked: BOOKED booking on a CANCELLED session is still accepted")
    void cancelledSessionAccepted() {
        int s = fx.session(ref, LocalDateTime.now().minusDays(3).withNano(0), 10, 0, 10, "CANCELLED");
        Cand c = fx.candidate();
        int b = fx.booking(s, c, "BOOKED", BOOKED_AT);
        assertThat(post(b, 300).status()).isEqualTo(201);
        assertThat(booking(b).get("Status")).isEqualTo("ATTENDED");
    }

    @Test
    @DisplayName("ATTENDED booking without a result qualifies: 201, UPDATE matches 0 rows, status stays ATTENDED")
    void attendedWithoutResultQualifies() {
        Cand c = fx.candidate();
        int b = fx.booking(pastSession, c, "ATTENDED", BOOKED_AT);
        LocalDateTime lm = (LocalDateTime) booking(b).get("LastModified");
        assertThat(post(b, 700).status()).isEqualTo(201);
        assertThat(booking(b)).containsEntry("Status", "ATTENDED").containsEntry("LastModified", lm);
    }

    @Test
    @DisplayName("Unknown booking: 404 BOOKING_NOT_FOUND; out-of-range score: 400 VALIDATION before any read")
    void notFoundAndValidation() {
        Resp r = post(99_999_999, 700);
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.body().get("error").asText()).isEqualTo("BOOKING_NOT_FOUND");

        Cand c = fx.candidate();
        int b = fx.booking(pastSession, c, "BOOKED", BOOKED_AT);
        Resp bad = post(b, 1001);
        assertThat(bad.status()).isEqualTo(400);
        assertThat(bad.body().get("error").asText()).isEqualTo("VALIDATION");
        assertThat(booking(b).get("Status")).isEqualTo("BOOKED");
    }

    @Test
    @DisplayName("HB-18: Passed is frozen at ingest against the PassingScore of that moment")
    void passedFrozenAtIngest() {
        Ref own = fx.reference(650, 30);
        int s = fx.session(own, LocalDateTime.now().minusDays(1).withNano(0), 10, 0, 9, "DELIVERED");
        Cand c = fx.candidate();
        int b = fx.booking(s, c, "BOOKED", BOOKED_AT);
        assertThat(post(b, 650).body().get("passed").asBoolean()).as("score >= PassingScore passes").isTrue();
        db.update("UPDATE dbo.Exam SET PassingScore = 900 WHERE ExamId = ?", own.examId());
        assertThat(db.one("SELECT Passed FROM dbo.Result WHERE BookingId = ?", b)).containsEntry("Passed", true);
    }

    @Test
    @DisplayName("HB-19: score file commits row by row, returns 200 with per-row errors; only qualifying rows -> ATTENDED")
    void scoreFilePartialSuccess() {
        Cand ok = fx.candidate();
        Cand cancelled = fx.candidate();
        Cand waitlisted = fx.candidate();
        int okB = fx.booking(pastSession, ok, "BOOKED", BOOKED_AT);
        int cancelledB = fx.booking(pastSession, cancelled, "CANCELLED", BOOKED_AT);
        int waitB = fx.booking(pastSession, waitlisted, "WAITLISTED", BOOKED_AT);
        String file = "candidateNumber,sessionId,score,scoredAt\n"
                + ok.candidateNumber() + "," + pastSession + ",720,2026-11-01T10:00:00\n"
                + cancelled.candidateNumber() + "," + pastSession + ",720,2026-11-01\n"
                + waitlisted.candidateNumber() + "," + pastSession + ",720,2026-11-01 10:00:00\n"
                + ok.candidateNumber() + "," + pastSession + ",notanumber,2026-11-01\n";

        Resp r = rs.postFile("/api/v1/results/upload", "contract-" + Fixtures.next() + ".csv", file);

        assertThat(r.status()).isEqualTo(200);
        JsonNode rep = r.body();
        assertThat(rep.get("rowsRead").asInt()).isEqualTo(4);
        assertThat(rep.get("imported").asInt()).isEqualTo(1);
        assertThat(rep.get("errors")).hasSize(3);
        assertThat(rep.get("errors").get(0).get("line").asInt()).isEqualTo(3);
        assertThat(rep.get("errors").get(0).get("message").asText())
                .isEqualTo("no active booking for " + cancelled.candidateNumber() + " on session " + pastSession);
        assertThat(booking(okB).get("Status")).isEqualTo("ATTENDED");
        assertThat(booking(cancelledB).get("Status")).isEqualTo("CANCELLED");
        assertThat(booking(waitB).get("Status")).isEqualTo("WAITLISTED");
        assertThat(db.one("SELECT SourceFile FROM dbo.Result WHERE BookingId = ?", okB).get("SourceFile").toString())
                .startsWith("contract-");
    }

    @Test
    @DisplayName("Score file resolves candidate+session to the newest BOOKED/ATTENDED booking (BookingId DESC)")
    void scoreFileBookingResolution() {
        Cand c = fx.candidate();
        int older = fx.booking(pastSession, c, "CANCELLED", BOOKED_AT.minusDays(1));
        int current = fx.booking(pastSession, c, "BOOKED", BOOKED_AT);
        String file = "candidateNumber,sessionId,score,scoredAt\n" + c.candidateNumber() + "," + pastSession + ",700,2026-11-01\n";
        Resp r = rs.postFile("/api/v1/results/upload", "contract-" + Fixtures.next() + ".csv", file);
        assertThat(r.body().get("imported").asInt()).isEqualTo(1);
        assertThat(booking(current).get("Status")).isEqualTo("ATTENDED");
        assertThat(booking(older).get("Status")).isEqualTo("CANCELLED");
        assertThat(db.count("SELECT BookingId FROM dbo.Result WHERE CandidateId = ?", c.candidateId())).isEqualTo(current);
    }

    @Test
    @DisplayName("Re-uploading the identical file is a replay: alreadyUploaded=true, nothing re-imported")
    void scoreFileReplay() {
        Cand c = fx.candidate();
        int b = fx.booking(pastSession, c, "BOOKED", BOOKED_AT);
        String name = "contract-" + Fixtures.next() + ".csv";
        String file = "candidateNumber,sessionId,score,scoredAt\n" + c.candidateNumber() + "," + pastSession + ",700,2026-11-01\n";
        assertThat(rs.postFile("/api/v1/results/upload", name, file).body().get("imported").asInt()).isEqualTo(1);

        JsonNode again = rs.postFile("/api/v1/results/upload", name, file).body();

        assertThat(again.get("alreadyUploaded").asBoolean()).isTrue();
        assertThat(again.get("imported").asInt()).isZero();
        assertThat(db.count("SELECT COUNT(*) FROM dbo.Result WHERE BookingId = ?", b)).isEqualTo(1);
        assertThat(booking(b).get("Status")).isEqualTo("ATTENDED");
    }

    @Test
    @DisplayName("Seat-drift invariant (db/checks/seat-drift.sql) counts ATTENDED like BOOKED, so ingest causes no drift")
    void attendedKeepsSeatDriftZero() {
        int s = fx.session(ref, LocalDateTime.now().minusDays(1).withNano(0), 3, 0, 1, "DELIVERED");
        Cand a = fx.candidate();
        Cand b2 = fx.candidate();
        int ba = fx.booking(s, a, "BOOKED", BOOKED_AT);
        fx.booking(s, b2, "BOOKED", BOOKED_AT);
        String drift = "SELECT (s.Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId "
                + "AND b.Status IN ('BOOKED','ATTENDED'))) - s.SeatsAvailable AS Drift FROM dbo.ExamSession s WHERE s.SessionId = ?";
        assertThat(db.count(drift, s)).isZero();
        assertThat(post(ba, 700).status()).isEqualTo(201);
        assertThat(db.count(drift, s)).isZero();
    }
}
