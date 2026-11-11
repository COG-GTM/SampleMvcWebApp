package com.northwind.scheduling;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Service-level integration tests against SQL Server 2022 (Testcontainers): HTTP API -> commands -> Scheduling DB. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SchedulingServiceIntegrationTest {

    @Container
    static final MSSQLServerContainer<?> SQL = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    private static final AtomicInteger IDS = new AtomicInteger(1000);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        try (Connection c = DriverManager.getConnection(SQL.getJdbcUrl(), SQL.getUsername(), SQL.getPassword());
             Statement st = c.createStatement()) {
            st.execute("IF DB_ID('Scheduling') IS NULL CREATE DATABASE Scheduling");
            st.execute("IF DB_ID('AssessmentDelivery') IS NULL CREATE DATABASE AssessmentDelivery");
        }
        String base = SQL.getJdbcUrl();
        r.add("spring.datasource.url", () -> base + ";databaseName=Scheduling");
        r.add("spring.datasource.username", SQL::getUsername);
        r.add("spring.datasource.password", SQL::getPassword);
        r.add("scheduling.source.url", () -> base + ";databaseName=AssessmentDelivery");
        r.add("scheduling.source.username", SQL::getUsername);
        r.add("scheduling.source.password", SQL::getPassword);
        r.add("scheduling.import.on-startup", () -> "never");
        r.add("scheduling.reference.poll-ms", () -> "0");
        r.add("scheduling.reconciler.cron", () -> "-");
        r.add("scheduling.legacy-seat-accounting", () -> "true");
    }

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate jdbc;

    static int examId;
    static int centerId;
    static int roomId;

    @BeforeAll
    static void ids() {
        examId = IDS.incrementAndGet();
        centerId = IDS.incrementAndGet();
        roomId = IDS.incrementAndGet();
    }

    // ------------------------------------------------------------------ helpers

    private void ref(String entity, int id, long version, Map<String, Object> data) {
        ResponseEntity<String> r = http.exchange("/api/v1/reference/" + entity + "/" + id, HttpMethod.PUT,
                json(Map.of("sourceVersion", version, "data", data), null), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as(r.getBody()).isTrue();
    }

    private void ensureReference() {
        ref("exams", examId, 1, Map.of("ExamCode", "IT-" + examId, "Title", "IT exam", "DurationMinutes", 60, "PassingScore", 700,
                "RetakeWaitDays", 14, "IsPublished", true));
        ref("test-centers", centerId, 1, Map.of("CenterCode", "IT" + centerId, "Name", "IT centre", "City", "X", "Region", "NA-EAST",
                "TimeZone", "UTC", "IsActive", true));
        ref("rooms", roomId, 1, Map.of("TestCenterId", centerId, "RoomName", "R", "SeatCount", 100, "AccommodationSeats", 10,
                "IsActive", true));
    }

    private int candidate() {
        int id = IDS.incrementAndGet();
        ref("candidates", id, 1, Map.of("CandidateNumber", String.format("NW%08d", id), "FirstName", "A", "LastName", "B",
                "Email", id + "@example.invalid"));
        return id;
    }

    private int session(int capacity, int accCap) {
        ensureReference();
        Map<String, Object> body = new HashMap<>();
        body.put("examId", examId);
        body.put("testCenterId", centerId);
        body.put("roomId", roomId);
        body.put("startsAt", LocalDateTime.of(2031, 1, 6, 9, 0).plusMinutes(IDS.incrementAndGet()));
        body.put("capacity", capacity);
        body.put("accommodationCapacity", accCap);
        body.put("seatsAvailable", capacity);
        body.put("status", "OPEN");
        return post("/api/v1/sessions", body, null).getBody().get("sessionId").asInt();
    }

    private static HttpEntity<Object> json(Object body, String idemKey) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (idemKey != null) {
            h.set("Idempotency-Key", idemKey);
        }
        return new HttpEntity<>(body, h);
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String idemKey) {
        return http.exchange(path, HttpMethod.POST, json(body, idemKey), JsonNode.class);
    }

    private ResponseEntity<JsonNode> book(int sessionId, int candidateId, String channel, String acc) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateId", candidateId);
        body.put("channel", channel);
        body.put("accommodationCode", acc);
        return post("/api/v1/bookings", body, null);
    }

    private JsonNode get(String path) {
        return http.getForObject(path, JsonNode.class);
    }

    private int seats(int sessionId) {
        return get("/api/v1/sessions/" + sessionId).get("seatsAvailable").asInt();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void bookConfirmsThenWaitlistsAndRejectsDuplicates() {
        int s = session(1, 0);
        int c1 = candidate();
        ResponseEntity<JsonNode> first = book(s, c1, "PORTAL", null);
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getBody().get("status").asText()).isEqualTo("BOOKED");
        ResponseEntity<JsonNode> second = book(s, candidate(), "PORTAL", null);
        assertThat(second.getBody().get("status").asText()).isEqualTo("WAITLISTED");
        assertThat(second.getBody().get("waitlistPosition").asInt()).isEqualTo(1);
        assertThat(seats(s)).isZero();
        ResponseEntity<JsonNode> dup = book(s, c1, "PORTAL", null);
        assertThat(dup.getStatusCode().value()).isEqualTo(409);
        assertThat(dup.getBody().get("legacyState").asInt()).isEqualTo(2);
        assertThat(dup.getBody().get("message").asText()).isEqualTo("Candidate " + c1 + " already has an active booking on session " + s + ".");
    }

    @Test
    void closedSessionAndAccommodationCapUseLegacyStates() {
        int s = session(5, 0);
        assertThat(book(s, candidate(), "PORTAL", "ET25").getBody().get("legacyState").asInt()).isEqualTo(3);
        post("/api/v1/sessions/" + s + "/capacity", Map.of("newCapacity", -1), null);
        ResponseEntity<JsonNode> bad = post("/api/v1/sessions/" + s + "/capacity", Map.of("newCapacity", -1), null);
        assertThat(bad.getBody().get("legacyState").asInt()).isEqualTo(5);
    }

    @Test
    void partnerBookingTakesTwoSeats() {
        int s = session(4, 0);
        assertThat(book(s, candidate(), "PARTNER", null).getBody().get("status").asText()).isEqualTo("BOOKED");
        assertThat(seats(s)).as("HB-01 double decrement").isEqualTo(2);
    }

    @Test
    void cancelReleasesSeatOrDeletesWaitlistRowWithoutPromotion() {
        int s = session(1, 0);
        int booked = book(s, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
        int w1 = book(s, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
        book(s, candidate(), "PORTAL", null);
        assertThat(post("/api/v1/bookings/" + w1 + "/cancel", Map.of("reason", "x"), null).getStatusCode().value()).isEqualTo(200);
        assertThat(post("/api/v1/bookings/" + booked + "/cancel", Map.of(), null).getStatusCode().value()).isEqualTo(200);
        assertThat(seats(s)).isEqualTo(1);
        JsonNode wl = get("/api/v1/sessions/" + s + "/waitlist");
        assertThat(wl).hasSize(1);
        assertThat(wl.get(0).get("position").asInt()).as("HB-06 gap kept").isEqualTo(2);
        ResponseEntity<JsonNode> again = post("/api/v1/bookings/" + booked + "/cancel", Map.of(), null);
        assertThat(again.getBody().get("legacyState").asInt()).isEqualTo(4);
    }

    @Test
    void capacityCutDemotesNewestBookedFirst() {
        int s = session(3, 0);
        int b1 = book(s, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
        int b2 = book(s, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
        int b3 = book(s, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
        jdbc.update("UPDATE dbo.Booking SET BookedAt = DATEADD(MINUTE, -10, GETDATE()) WHERE BookingId = ?", b3);
        jdbc.update("UPDATE dbo.Booking SET BookedAt = DATEADD(MINUTE, -3, GETDATE()) WHERE BookingId = ?", b1);
        jdbc.update("UPDATE dbo.Booking SET BookedAt = DATEADD(MINUTE, -2, GETDATE()) WHERE BookingId = ?", b2);
        JsonNode r = post("/api/v1/sessions/" + s + "/capacity", Map.of("newCapacity", 1), null).getBody();
        assertThat(r.get("demotedBookingIds")).extracting(JsonNode::asInt).containsExactly(b1, b2);
        assertThat(get("/api/v1/bookings/" + b3).get("status").asText()).isEqualTo("BOOKED");
        assertThat(jdbc.queryForList("SELECT BookingId FROM dbo.Waitlist WHERE SessionId = ? ORDER BY Position", Integer.class, s))
                .containsExactly(b1, b2);
        assertThat(seats(s)).isZero();
    }

    @Test
    void rescheduleAcrossExamsIsRejectedWithState6() {
        int s = session(2, 0);
        int b = book(s, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
        int other = examId;
        examId = IDS.incrementAndGet();
        int s2 = session(2, 0);
        examId = other;
        ResponseEntity<JsonNode> r = post("/api/v1/bookings/" + b + "/reschedule", Map.of("newSessionId", s2, "channel", "PORTAL"), null);
        assertThat(r.getBody().get("legacyState").asInt()).isEqualTo(6);
        ResponseEntity<JsonNode> unknown = post("/api/v1/bookings/999999/reschedule", Map.of("newSessionId", s, "channel", "PORTAL"), null);
        assertThat(unknown.getBody().get("legacyState").asInt()).isEqualTo(6);
    }

    @Test
    void unknownCandidateSurfacesLegacyForeignKeyMessage() {
        ResponseEntity<JsonNode> r = book(session(2, 0), 987_654_321, "PORTAL", null);
        assertThat(r.getStatusCode().value()).isEqualTo(404);
        assertThat(r.getBody().get("legacyState").asInt()).isZero();
        assertThat(r.getBody().get("message").asText()).contains("FK_Booking_Candidate");
    }

    @Test
    void idempotencyKeyReplaysAndRejectsReuse() {
        int s = session(5, 0);
        int c = candidate();
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("sessionId", s, "candidateId", c, "channel", "PORTAL");
        ResponseEntity<JsonNode> a = post("/api/v1/bookings", body, key);
        ResponseEntity<JsonNode> b = post("/api/v1/bookings", body, key);
        assertThat(b.getStatusCode()).isEqualTo(a.getStatusCode());
        assertThat(b.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(b.getBody().get("bookingId").asInt()).isEqualTo(a.getBody().get("bookingId").asInt());
        assertThat(seats(s)).isEqualTo(4);
        ResponseEntity<JsonNode> reuse = post("/api/v1/bookings", Map.of("sessionId", s, "candidateId", candidate(), "channel", "PORTAL"), key);
        assertThat(reuse.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void referenceUpsertIsMonotonicBySourceVersion() {
        int c = candidate();
        ref("candidates", c, 5, Map.of("CandidateNumber", "NW77777777", "FirstName", "New", "LastName", "B", "Email", "n@x"));
        ResponseEntity<String> stale = http.exchange("/api/v1/reference/candidates/" + c, HttpMethod.PUT,
                json(Map.of("sourceVersion", 3, "data", Map.of("CandidateNumber", "NW77777777", "FirstName", "Old", "LastName", "B",
                        "Email", "o@x")), null), String.class);
        assertThat(stale.getStatusCode().value()).isEqualTo(204);
        assertThat(get("/api/v1/reference/candidates/" + c).toString()).contains("New");
    }

    @Test
    void reconcilerAppliesCapacitySyncFormulaAndKeepsHistory() {
        int s = session(5, 1);
        book(s, candidate(), "PORTAL", null);
        book(s, candidate(), "PORTAL", "ET25");
        // seats = 5 - 2 = 3; capacity-sync expects max(0, 5 - 1 - 1 standard) = 3 -> no change
        JsonNode run = post("/api/v1/reconciler/runs?sessionIds=" + s, null, null).getBody();
        assertThat(run.get("checked").asInt()).isEqualTo(1);
        assertThat(run.get("updated").asInt()).isZero();
        jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = 0 WHERE SessionId = ?", s);
        run = post("/api/v1/reconciler/runs?sessionIds=" + s, null, null).getBody();
        assertThat(run.get("updated").asInt()).isEqualTo(1);
        assertThat(seats(s)).isEqualTo(3);
        List<JsonNode> runs = List.of(get("/api/v1/reconciler/runs").elements().next());
        assertThat(runs).isNotEmpty();
    }

    @Test
    void attendanceIsIdempotent() {
        int s = session(2, 0);
        int b = book(s, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
        assertThat(post("/api/v1/bookings/" + b + "/attendance", Map.of(), null).getBody().get("transitioned").asBoolean()).isTrue();
        JsonNode again = post("/api/v1/bookings/" + b + "/attendance", Map.of(), null).getBody();
        assertThat(again.get("transitioned").asBoolean()).isFalse();
        assertThat(again.get("status").asText()).isEqualTo("ATTENDED");
        assertThat(get("/api/v1/sessions/" + s).get("bookedSeats").asInt()).isEqualTo(1);
    }

    @Test
    void capacityAuditFiltersOptionalAndToIsExclusive() {
        int s = session(5, 0);
        post("/api/v1/sessions/" + s + "/capacity", Map.of("newCapacity", 7), null);
        jdbc.update("UPDATE dbo.AuditLog SET LoggedAt = '2026-01-10T00:00:00' WHERE EntityName = 'ExamSession' AND EntityId = ? "
                + "AND Action = 'CAPACITY'", s);
        assertThat(get("/api/v1/sessions/capacity-audit?pageSize=500").get("totalRows").asInt()).isPositive();
        assertThat(get("/api/v1/sessions/capacity-audit?sessionId=" + s + "&from=2026-01-01T00:00:00&to=2026-01-10T00:00:00")
                .get("totalRows").asInt()).isZero();
        JsonNode hit = get("/api/v1/sessions/capacity-audit?sessionId=" + s + "&from=2026-01-10T00:00:00&to=2026-01-10T00:00:01");
        assertThat(hit.get("totalRows").asInt()).isEqualTo(1);
    }

    @Test
    void importApiIsDisabledByDefault() {
        assertThat(post("/api/v1/admin/import?replace=true", null, null).getStatusCode().value()).isEqualTo(403);
    }
}
