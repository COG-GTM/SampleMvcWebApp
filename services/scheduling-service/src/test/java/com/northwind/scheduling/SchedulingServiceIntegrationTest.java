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

    @Autowired
    com.northwind.scheduling.core.Audit audit;

    @Autowired
    com.northwind.scheduling.reference.LegacyImporter importer;

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
        assertThat(runs.get(0).get("actor").asText()).isEqualTo("capacity-sync");
    }

    @Test
    void reconcilerWithoutLegacyAccountingConvergesToZeroSeatDrift() throws Exception {
        int s1 = session(5, 1);
        int s2 = session(3, 0);
        book(s1, candidate(), "PORTAL", null);
        book(s1, candidate(), "PORTAL", "ET25");
        book(s2, candidate(), "PORTAL", null);
        jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = 0 WHERE SessionId = ?", s1);
        jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = 9, Status = 'CLOSED' WHERE SessionId = ?", s2);
        new com.northwind.scheduling.reconciler.SeatReconciler(jdbc, audit, "test", false).run("MANUAL", null, null);
        String check = java.nio.file.Files.readString(java.nio.file.Path.of("db/checks/seat-drift.sql"));
        String select = check.substring(check.indexOf("SELECT s.SessionId"), check.lastIndexOf("GO"));
        assertThat(jdbc.queryForList(select)).isEmpty();
        assertThat(seats(s1)).isEqualTo(3);
        assertThat(seats(s2)).isEqualTo(2);
    }

    @Test
    void importSeedsReconcilerRunsFromLegacyCapacitySyncAudit() {
        jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor, LoggedAt) VALUES "
                + "('ExamSession', 0, 'CAPACITY_SYNC', 'trigger=SCHEDULED status=COMPLETED checked=4242 updated=7', "
                + "'capacity-sync-legacy', '2026-11-01T02:00:00')");
        post("/api/v1/reconciler/runs?sessionIds=" + session(1, 0), null, null);
        assertThat(importer.seedReconcilerRuns(jdbc)).isPositive();
        assertThat(importer.seedReconcilerRuns(jdbc)).isZero();
        JsonNode runs = get("/api/v1/reconciler/runs?from=2026-11-01T00:00:00&to=2026-11-02T00:00:00");
        JsonNode seeded = null;
        for (JsonNode r : runs) {
            if (r.get("checked").asInt() == 4242) {
                seeded = r;
            }
        }
        assertThat(seeded).isNotNull();
        assertThat(seeded.get("trigger").asText()).isEqualTo("SCHEDULED");
        assertThat(seeded.get("status").asText()).isEqualTo("OK");
        assertThat(seeded.get("updated").asInt()).isEqualTo(7);
        assertThat(seeded.get("actor").asText()).isEqualTo("capacity-sync-legacy");
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
    void realDriverDeadlockVictimIsClassifiedForRetry() throws Exception {
        jdbc.execute("IF OBJECT_ID('dbo.DeadlockProbe') IS NULL CREATE TABLE dbo.DeadlockProbe (Id INT PRIMARY KEY, V INT)");
        jdbc.execute("DELETE FROM dbo.DeadlockProbe; INSERT INTO dbo.DeadlockProbe VALUES (0, 0), (1, 0), (2, 0)");
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.List<java.util.concurrent.Future<java.sql.SQLException>> results = new java.util.ArrayList<>();
        for (int[] order : new int[][] {{1, 2}, {2, 1}}) {
            results.add(pool.submit(() -> {
                try (Connection c = jdbc.getDataSource().getConnection(); Statement st = c.createStatement()) {
                    c.setAutoCommit(false);
                    st.executeUpdate("UPDATE dbo.DeadlockProbe SET V = V + 1 WHERE Id = " + order[0]);
                    barrier.await();
                    try {
                        // A multi-statement batch: the victim surfaces in mssql-jdbc's wrapped "Done status" form.
                        st.execute("UPDATE dbo.DeadlockProbe SET V = V WHERE Id = 0; "
                                + "UPDATE dbo.DeadlockProbe SET V = V + 1 WHERE Id = " + order[1]);
                        while (st.getMoreResults() || st.getUpdateCount() != -1) {
                            // drain
                        }
                        c.commit();
                        return null;
                    } catch (java.sql.SQLException e) {
                        c.rollback();
                        return e;
                    }
                }
            }));
        }
        java.util.List<java.sql.SQLException> victims = new java.util.ArrayList<>();
        for (var f : results) {
            java.sql.SQLException e = f.get(60, java.util.concurrent.TimeUnit.SECONDS);
            if (e != null) {
                victims.add(e);
            }
        }
        pool.shutdown();
        assertThat(victims).hasSize(1);
        java.sql.SQLException victim = victims.get(0);
        System.out.println("[deadlock-probe] errorCode=" + victim.getErrorCode() + " message=" + victim.getMessage());
        assertThat(com.northwind.scheduling.core.SchedulingCommands.isDeadlock(victim)).isTrue();
        assertThat(com.northwind.scheduling.core.SchedulingCommands.isDeadlock(
                jdbc.getExceptionTranslator().translate("probe", null, victim))).isTrue();
    }

    @Test
    void readsUnderBookCancelRescheduleLoadNeverFail() throws Exception {
        assertThat(jdbc).isInstanceOf(com.northwind.scheduling.core.RetryingJdbcTemplate.class);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        try {
            for (int it = 0; it < 3; it++) {
                int a = session(30, 0);
                int b = session(30, 0);
                List<java.util.concurrent.Callable<Integer>> work = new java.util.ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    int from = i % 2 == 0 ? a : b;
                    int to = from == a ? b : a;
                    int moving = book(from, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
                    int leaving = book(from, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
                    int arriving = candidate();
                    work.add(() -> post("/api/v1/bookings/" + moving + "/reschedule",
                            Map.of("newSessionId", to, "channel", "PORTAL"), null).getStatusCode().value());
                    work.add(() -> post("/api/v1/bookings/" + leaving + "/cancel", Map.of("reason", "load"), null)
                            .getStatusCode().value());
                    work.add(() -> book(to, arriving, "PORTAL", null).getStatusCode().value());
                    for (String path : List.of("/api/v1/sessions/" + from, "/api/v1/sessions/" + to,
                            "/api/v1/sessions/" + from + "/waitlist", "/api/v1/sessions/" + to + "/roster",
                            "/api/v1/bookings/" + moving, "/api/v1/bookings/" + leaving,
                            "/api/v1/bookings?sessionId=" + from)) {
                        work.add(() -> http.exchange(path, HttpMethod.GET, null, JsonNode.class).getStatusCode().value());
                    }
                }
                java.util.Collections.shuffle(work);
                for (java.util.concurrent.Future<Integer> f : pool.invokeAll(work)) {
                    assertThat(f.get()).isLessThan(500);
                }
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM dbo.ExamSession s WHERE s.SessionId IN (?, ?) AND s.Capacity
                               - (SELECT COUNT(*) FROM dbo.Booking x WHERE x.SessionId = s.SessionId AND x.Status IN ('BOOKED','ATTENDED'))
                               - s.SeatsAvailable <> 0""", Integer.class, a, b)).isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void crossingReschedulesWithBookAndCancelNeverFailOrDrift() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(16);
        try {
            for (int it = 0; it < 5; it++) {
                int a = session(30, 0);
                int b = session(30, 0);
                List<java.util.concurrent.Callable<Integer>> work = new java.util.ArrayList<>();
                for (int i = 0; i < 12; i++) {
                    int from = i % 2 == 0 ? a : b;
                    int to = from == a ? b : a;
                    int moving = book(from, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
                    int leaving = book(from, candidate(), "PORTAL", null).getBody().get("bookingId").asInt();
                    int arriving = candidate();
                    work.add(() -> post("/api/v1/bookings/" + moving + "/reschedule",
                            Map.of("newSessionId", to, "channel", "PORTAL"), null).getStatusCode().value());
                    work.add(() -> post("/api/v1/bookings/" + leaving + "/cancel", Map.of("reason", "load"), null)
                            .getStatusCode().value());
                    work.add(() -> book(to, arriving, "PORTAL", null).getStatusCode().value());
                }
                java.util.Collections.shuffle(work);
                for (java.util.concurrent.Future<Integer> f : pool.invokeAll(work)) {
                    assertThat(f.get()).isLessThan(500);
                }
                assertThat(jdbc.queryForObject("""
                        SELECT COUNT(*) FROM dbo.ExamSession s WHERE s.SessionId IN (?, ?) AND s.Capacity
                               - (SELECT COUNT(*) FROM dbo.Booking x WHERE x.SessionId = s.SessionId AND x.Status IN ('BOOKED','ATTENDED'))
                               - s.SeatsAvailable <> 0""", Integer.class, a, b)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Booking WHERE SessionId IN (?, ?) AND Status = 'BOOKED'",
                        Integer.class, a, b)).isEqualTo(24);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void importApiIsDisabledByDefault() {
        assertThat(post("/api/v1/admin/import?replace=true", null, null).getStatusCode().value()).isEqualTo(403);
    }
}
