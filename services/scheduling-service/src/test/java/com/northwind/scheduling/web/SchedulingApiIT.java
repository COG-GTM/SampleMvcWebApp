package com.northwind.scheduling.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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

/** /api/v2 (+ /api/v1 facade) end to end on Testcontainers SQL Server; inventory reset before each test. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SqlServerTestBase.FixtureLegacy.class)
class SchedulingApiIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() {
    };

    @LocalServerPort
    int port;
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
                new HttpEntity<>(Map.of("sessionId", session, "candidateNumber", cand, "accommodationCode", acc == null ? "" : acc)),
                MAP);
    }

    HttpResponse<String> attendance(String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            b.header("X-Service-Token", token);
        }
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void flywayCreatesSchedulingDbAndImporterLoadsSnapshots() throws java.io.IOException {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo("Scheduling");
        try (var migrations = java.nio.file.Files.list(java.nio.file.Path.of("src/main/resources/db/migration"))) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                    .isEqualTo((int) migrations.filter(f -> f.getFileName().toString().matches("V\\d+__.*\\.sql")).count());
        }
        List<Map<String, Object>> centers = http.exchange("/api/v2/centers", HttpMethod.GET, null, LIST).getBody();
        assertThat(centers).extracting(c -> c.get("centerCode")).containsExactly("AMS01", "SFO01");
        List<Map<String, Object>> sessions = http.exchange("/api/v2/centers/SFO01/sessions?examCode=MHL-RN1&from=2026-11-12&to=2026-11-20",
                HttpMethod.GET, null, LIST).getBody();
        assertThat(sessions).extracting(s -> s.get("sessionId")).containsExactly(154, 263, 163);
        assertThat(http.getForEntity("/api/v2/centers/NOPE/sessions", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidate_snapshot WHERE source_version IS NOT NULL AND synced_at IS NOT NULL",
                Integer.class)).isEqualTo(40);
    }

    @Test
    void bookingOpenSessionDropsSeatsByExactlyOne() {
        assertThat(get("/api/v2/sessions/154")).containsEntry("seatsAvailable", 11).containsEntry("bookedSeats", 9);
        ResponseEntity<Map<String, Object>> r = book(154, "NW00000920", null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody()).containsEntry("status", "BOOKED");
        assertThat(get("/api/v2/sessions/154")).containsEntry("seatsAvailable", 10).containsEntry("bookedSeats", 10);

        ResponseEntity<Map<String, Object>> dup = book(154, "NW00000920", null);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody()).containsEntry("error", "DUPLICATE_BOOKING").containsEntry("legacyCode", 50002);
        assertThat(book(263, "NW00000921", null).getBody()).containsEntry("legacyCode", 50001);
        assertThat(book(154, "NW00000921", "XX").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(book(154, "NW09999999", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void attendanceNeedsTheServiceTokenAndWritesNoOutboxEventWithoutIt() throws Exception {
        int id = ((Number) book(154, "NW00000920", null).getBody().get("bookingId")).intValue();
        String url = "/api/v2/bookings/" + id + "/attendance";
        String countSql = "SELECT COUNT(*) FROM outbox_event WHERE event_type = 'AttendanceRecorded'";
        int before = jdbc.queryForObject(countSql, Integer.class);

        // java.net.http: HttpURLConnection cannot read a 401 answer to a POST ("cannot retry due to server authentication")
        HttpResponse<String> none = attendance(url, null);
        assertThat(none.statusCode()).isEqualTo(401);
        assertThat(none.body()).contains("\"error\":\"UNAUTHORIZED\"", "\"legacyCode\":null");
        assertThat(attendance(url, "wrong-token").statusCode()).isEqualTo(401);
        assertThat(attendance(url, "").statusCode()).isEqualTo(401);
        assertThat(get("/api/v2/bookings/" + id)).containsEntry("status", "BOOKED");
        assertThat(jdbc.queryForObject(countSql, Integer.class)).isEqualTo(before);

        HttpResponse<String> ok = attendance(url, "test-attendance-token");
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).contains("\"status\":\"ATTENDED\"");
        assertThat(jdbc.queryForObject(countSql, Integer.class)).isEqualTo(before + 1);
    }

    @Test
    void fullSessionWaitlistsWithNextPosition() {
        ResponseEntity<Map<String, Object>> r = book(163, "NW00000920", null);
        assertThat(r.getBody()).containsEntry("status", "WAITLISTED").containsEntry("waitlistPosition", 2);
        List<Map<String, Object>> wl = http.exchange("/api/v2/sessions/163/waitlist", HttpMethod.GET, null, LIST).getBody();
        assertThat(wl).extracting(w -> w.get("position")).containsExactly(1, 2);
        assertThat(wl.get(1)).containsEntry("candidateNumber", "NW00000920").containsKey("addedAt");
    }

    @Test
    void extraTimeOverAccommodationCapacityIsRejectedWith50003ButSrRbAreNot() {
        // session 163: accommodation capacity 1, already one ET25 booking
        ResponseEntity<Map<String, Object>> et = book(163, "NW00000920", "ET50");
        assertThat(et.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(et.getBody()).containsEntry("error", "NO_ACCOMMODATION_SEAT").containsEntry("legacyCode", 50003);
        assertThat(book(163, "NW00000921", "SR").getBody()).containsEntry("status", "WAITLISTED");
        assertThat(book(154, "NW00000922", "RB").getBody()).containsEntry("status", "BOOKED");
        assertThat(book(155, "NW00000923", "et25").getBody()).containsEntry("legacyCode", 50003);
    }

    @Test
    void capacityDecreaseBelowActiveBookingsDemotesNewestToWaitlist() {
        ResponseEntity<Map<String, Object>> r = http.exchange("/api/v2/sessions/154/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", 6, "reason", "room maintenance")), MAP);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new java.util.HashSet<Object>((List<?>) r.getBody().get("demotedBookingIds"))).isEqualTo(java.util.Set.of(7, 8, 9));
        assertThat(get("/api/v2/sessions/154")).containsEntry("capacity", 6).containsEntry("bookedSeats", 6)
                .containsEntry("waitlistCount", 3).containsEntry("seatsAvailable", 0);
        assertThat(get("/api/v2/bookings/7")).containsEntry("status", "WAITLISTED").containsEntry("waitlistPosition", 1);
        assertThat(get("/api/v2/bookings/9")).containsEntry("waitlistPosition", 3);

        Map<String, Object> raised = http.exchange("/api/v2/sessions/154/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", 12)), MAP).getBody();
        assertThat(((Map<?, ?>) raised.get("session")).get("seatsAvailable")).isEqualTo(6);
        assertThat(get("/api/v2/sessions/154")).containsEntry("waitlistCount", 3); // no auto-promotion (ODD-09)

        ResponseEntity<Map<String, Object>> neg = http.exchange("/api/v2/sessions/154/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", -1)), MAP);
        assertThat(neg.getBody()).containsEntry("legacyCode", 50005);
        ResponseEntity<Map<String, Object>> floor = http.exchange("/api/v2/sessions/154/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", 1)), MAP);
        assertThat(floor.getBody()).containsEntry("error", "INVALID_CAPACITY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM capacity_change WHERE session_id = 154", Integer.class)).isEqualTo(2);
    }

    @Test
    void cancelFreesSeatAndRescheduleMovesOneSeatSameExamOnly() {
        int id = (Integer) book(154, "NW00000920", "ET25").getBody().get("bookingId");
        ResponseEntity<Map<String, Object>> moved = http.exchange("/api/v2/bookings/" + id + "/reschedule", HttpMethod.POST,
                new HttpEntity<>(Map.of("newSessionId", 300)), MAP);
        assertThat(moved.getBody()).containsEntry("status", "BOOKED");
        int newId = (Integer) moved.getBody().get("bookingId");
        assertThat(get("/api/v2/bookings/" + id)).containsEntry("status", "CANCELLED");
        assertThat(get("/api/v2/bookings/" + newId)).containsEntry("accommodationCode", "ET25").containsEntry("rescheduledFrom", id);
        assertThat(get("/api/v2/sessions/154")).containsEntry("seatsAvailable", 11);
        assertThat(get("/api/v2/sessions/300")).containsEntry("seatsAvailable", 9);

        assertThat(http.exchange("/api/v2/bookings/" + newId + "/reschedule", HttpMethod.POST,
                new HttpEntity<>(Map.of("newSessionId", 155)), MAP).getBody()).containsEntry("legacyCode", 50006);
        assertThat(http.exchange("/api/v2/bookings/" + newId + "/reschedule", HttpMethod.POST,
                new HttpEntity<>(Map.of("newSessionId", 300)), MAP).getBody()).containsEntry("error", "SAME_SESSION");

        Map<String, Object> cancelled = http.exchange("/api/v2/bookings/" + newId, HttpMethod.DELETE, null, MAP).getBody();
        assertThat(cancelled).containsEntry("status", "CANCELLED");
        assertThat(get("/api/v2/sessions/300")).containsEntry("seatsAvailable", 10);
        assertThat(http.exchange("/api/v2/bookings/" + newId, HttpMethod.DELETE, null, MAP).getBody())
                .containsEntry("legacyCode", 50004);
        List<Map<String, Object>> mine = http.exchange("/api/v2/candidates/NW00000920/bookings", HttpMethod.GET, null, LIST).getBody();
        assertThat(mine).hasSize(2);
    }

    @Test
    void eligibilityFollowsCalendarDayRetakeRule() {
        assertThat(get("/api/v2/candidates/NW00000939/eligibility?examCode=MHL-RN1&date=2026-12-06"))
                .containsEntry("eligible", false).containsEntry("earliestRetakeDate", "2026-12-07");
        assertThat(get("/api/v2/candidates/NW00000939/eligibility?examCode=MHL-RN1&date=2026-12-07"))
                .containsEntry("eligible", true).containsEntry("dayCounting", "calendar");
        assertThat(get("/api/v2/candidates/NW00000938/eligibility?examCode=MHL-RN1&date=2026-12-07"))
                .containsEntry("eligible", false);
        assertThat(get("/api/v2/candidates/NW00000901/eligibility?examCode=MHL-RN1")).containsEntry("eligible", true);
        assertThat(http.getForEntity("/api/v2/candidates/NW00000901/eligibility?examCode=NOPE", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        // booking inside the retake window is refused
        assertThat(book(163, "NW00000939", null).getBody()).containsEntry("error", "NOT_ELIGIBLE");
    }

    @Test
    void routingResolveAndV1FacadeAndImportTrigger() {
        assertThat(get("/api/v2/internal/routing/resolve?centerCode=SFO01")).containsEntry("target", "legacy");
        assertThat(get("/api/v2/internal/routing/resolve?sessionId=300")).containsEntry("centerCode", "AMS01");
        assertThat(get("/api/v2/internal/routing/resolve?centerId=2")).containsEntry("centerCode", "AMS01");
        assertThat(http.getForEntity("/api/v2/internal/routing/resolve?centerId=99", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        Map<String, Object> v1 = http.exchange("/api/v1/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("sessionId", 154, "candidateNumber", "NW00000930", "externalRef", "P-1")), MAP).getBody();
        assertThat(v1).containsOnlyKeys("bookingId", "status").containsEntry("status", "BOOKED");
        assertThat(((Number) v1.get("bookingId")).longValue()).isGreaterThanOrEqualTo(50_000_000L); // modern id floor
        assertThat(get("/api/v1/bookings/" + v1.get("bookingId"))).containsEntry("channel", "PARTNER");
        Map<String, Object> run = http.exchange("/api/v2/internal/import/sessions", HttpMethod.POST, null, MAP).getBody();
        assertThat(run).containsEntry("mode", "MERGE").containsEntry("centers", 2).containsEntry("sessions", 0);
        assertThat(get("/api/v1/bookings/" + v1.get("bookingId"))).containsEntry("status", "BOOKED"); // merge keeps inventory
    }

    @Test
    void rootAndAppRedirectToSpa() throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        for (String path : List.of("/", "/app")) {
            java.net.http.HttpResponse<Void> r = client.send(java.net.http.HttpRequest.newBuilder(
                    java.net.URI.create("http://localhost:" + port + path)).build(), java.net.http.HttpResponse.BodyHandlers.discarding());
            assertThat(r.statusCode()).as(path).isEqualTo(302);
            assertThat(r.headers().firstValue("Location")).as(path).hasValueSatisfying(l -> assertThat(l).endsWith("/app/"));
        }
    }
}
