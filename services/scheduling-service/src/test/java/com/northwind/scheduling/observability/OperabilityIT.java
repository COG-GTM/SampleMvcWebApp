package com.northwind.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.scheduling.SqlServerTestBase;
import com.northwind.scheduling.importer.SnapshotImporter;

/** MBA-3832: probes, Prometheus booking meters and JSON logs on Testcontainers SQL Server. */
@AutoConfigureObservability
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SqlServerTestBase.FixtureLegacy.class)
@ExtendWith(OutputCaptureExtension.class)
class OperabilityIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
    };
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    TestRestTemplate http;
    @Autowired
    SnapshotImporter importer;

    @BeforeEach
    void reset() {
        importer.run(SnapshotImporter.Mode.RESET);
    }

    private ResponseEntity<Map<String, Object>> book(int session, String cand) {
        return http.exchange("/api/v2/bookings", HttpMethod.POST,
                new HttpEntity<>(Map.of("sessionId", session, "candidateNumber", cand)), MAP);
    }

    @Test
    @SuppressWarnings("unchecked")
    void livenessAndReadinessAreUpWithAllReadinessComponents() {
        ResponseEntity<Map<String, Object>> live = http.exchange("/actuator/health/liveness", HttpMethod.GET, null, MAP);
        assertThat(live.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(live.getBody()).containsEntry("status", "UP");
        ResponseEntity<Map<String, Object>> ready = http.exchange("/actuator/health/readiness", HttpMethod.GET, null, MAP);
        assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ready.getBody()).containsEntry("status", "UP");
        assertThat((Map<String, Object>) ready.getBody().get("components"))
                .containsOnlyKeys("readinessState", "db", "schedulingSchema", "legacySource");
    }

    @Test
    void bookingCommandsArePublishedToPrometheus() {
        ResponseEntity<Map<String, Object>> ok = book(154, "NW00000920");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        int bookingId = (Integer) ok.getBody().get("bookingId");
        assertThat(book(154, "NW00000920").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);   // DUPLICATE_BOOKING
        assertThat(http.exchange("/api/v2/bookings/" + bookingId + "/reschedule", HttpMethod.POST,
                new HttpEntity<>(Map.of("newSessionId", 163)), MAP).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        int moved = (Integer) book(155, "NW00000921").getBody().get("bookingId");
        assertThat(http.exchange("/api/v2/bookings/" + moved, HttpMethod.DELETE, null, MAP).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/v2/sessions/154/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", 19, "reason", "room change")), MAP).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/v2/sessions/154/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("capacity", -1)), MAP).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> scrape = http.getForEntity("/actuator/prometheus", String.class);
        assertThat(scrape.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = scrape.getBody();
        assertThat(body).contains("# TYPE scheduling_booking_duration_seconds histogram",
                "# TYPE scheduling_booking_errors_total counter");
        assertThat(sample(body, "scheduling_booking_duration_seconds_count", "operation=\"book\"", "outcome=\"success\"",
                "centerCode=\"SFO01\"")).isEqualTo(2.0);
        assertThat(sample(body, "scheduling_booking_duration_seconds_count", "operation=\"book\"",
                "outcome=\"business_error\"", "centerCode=\"SFO01\"")).isEqualTo(1.0);
        assertThat(sample(body, "scheduling_booking_duration_seconds_count", "operation=\"reschedule\"",
                "outcome=\"success\"", "centerCode=\"SFO01\"")).isEqualTo(1.0);
        assertThat(sample(body, "scheduling_booking_duration_seconds_count", "operation=\"cancel\"",
                "outcome=\"success\"", "centerCode=\"SFO01\"")).isEqualTo(1.0);
        assertThat(sample(body, "scheduling_booking_duration_seconds_count", "operation=\"capacity\"",
                "outcome=\"success\"", "centerCode=\"SFO01\"")).isEqualTo(1.0);
        assertThat(sample(body, "scheduling_booking_duration_seconds_bucket", "operation=\"book\"",
                "outcome=\"success\"", "le=\"+Inf\"")).isEqualTo(2.0);
        assertThat(sample(body, "scheduling_booking_errors_total", "operation=\"book\"", "outcome=\"business_error\"",
                "code=\"DUPLICATE_BOOKING\"", "centerCode=\"SFO01\"")).isEqualTo(1.0);
        assertThat(sample(body, "scheduling_booking_errors_total", "operation=\"capacity\"", "outcome=\"business_error\"",
                "code=\"INVALID_CAPACITY\"")).isEqualTo(1.0);
        assertThat(body).contains("http_server_requests_seconds_count");
    }

    @Test
    void logsAreJsonWithTraceAndBookingContextAndNoCandidatePii(CapturedOutput output) throws Exception {
        ResponseEntity<Map<String, Object>> ok = book(154, "NW00000920");
        assertThat(ok.getHeaders().getFirst(RequestLoggingFilter.TRACE_HEADER)).matches("[0-9a-f]{16,32}");
        String bookingId = String.valueOf(ok.getBody().get("bookingId"));
        http.exchange("/api/v2/bookings/" + bookingId, HttpMethod.GET, null, MAP);

        List<JsonNode> lines = Arrays.stream(output.getOut().split("\\R")).filter(l -> l.startsWith("{"))
                .map(OperabilityIT::parse).toList();
        assertThat(lines).isNotEmpty().allSatisfy(n -> assertThat(n.has("@timestamp") && n.has("level")).isTrue());

        JsonNode op = lines.stream().filter(n -> "book".equals(n.path("operation").asText())
                && bookingId.equals(n.path("bookingId").asText())).findFirst().orElseThrow();
        assertThat(op.path("outcome").asText()).isEqualTo("success");
        assertThat(op.path("centerCode").asText()).isEqualTo("SFO01");
        assertThat(op.path("sessionId").asText()).isEqualTo("154");
        assertThat(op.path("candidateNumber").asText()).isEqualTo("NW00000920");
        assertThat(op.path("traceId").asText()).isNotBlank();
        assertThat(op.path("service").asText()).isEqualTo("scheduling-service");

        JsonNode post = lines.stream().filter(n -> "/api/v2/bookings".equals(n.path("path").asText())
                && op.path("traceId").asText().equals(n.path("traceId").asText())).findFirst().orElseThrow();
        assertThat(post.path("status").asInt()).isEqualTo(201);
        assertThat(post.path("bookingId").asText()).isEqualTo(bookingId);
        assertThat(post.path("centerCode").asText()).isEqualTo("SFO01");

        JsonNode get = lines.stream().filter(n -> ("/api/v2/bookings/" + bookingId).equals(n.path("path").asText()))
                .findFirst().orElseThrow();
        assertThat(get.path("bookingId").asText()).isEqualTo(bookingId);
        assertThat(get.path("traceId").asText()).isNotBlank().isNotEqualTo(post.path("traceId").asText());

        // fixture candidate NW00000920 is First20 Last20 <c20@example.test>: none of it may be logged
        assertThat(output.getOut()).doesNotContain("First20", "Last20", "c20@example.test");
    }

    private static JsonNode parse(String line) {
        try {
            return JSON.readTree(line);
        } catch (Exception e) {
            throw new AssertionError("not JSON: " + line, e);
        }
    }

    /** Value of the single Prometheus sample of {@code name} carrying all {@code labels}; summed over the rest. */
    static double sample(String scrape, String name, String... labels) {
        return scrape.lines().filter(l -> l.startsWith(name + "{"))
                .filter(l -> Arrays.stream(labels).allMatch(l::contains))
                .mapToDouble(l -> Double.parseDouble(l.substring(l.lastIndexOf(' ') + 1))).sum();
    }
}
