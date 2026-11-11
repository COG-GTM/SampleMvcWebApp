package com.northwind.assessment.scheduling.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/** MBA-3832 AC2/AC5: real API traffic shows up in /actuator/prometheus with the agreed names and tags. */
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "scheduling.metrics.outbox-refresh-ms=200",
        "scheduling.relay.enabled=false"})
class ObservabilityIT extends SqlServerTestBase {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    @Autowired
    TestRestTemplate http;

    @Autowired
    JdbcTemplate db;

    @Test
    void bookingTrafficIsExposedAsPrometheusSeries() {
        int sessionId = openSession("s.capacity - (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id "
                + "AND b.status IN ('BOOKED','ATTENDED')) > 3");
        String center = db.queryForObject("SELECT c.center_code FROM exam_session s JOIN test_center_snapshot c "
                + "ON c.center_id = s.center_id WHERE s.session_id = ?", String.class, sessionId);
        int target = db.queryForObject("SELECT TOP 1 s.session_id FROM exam_session s WHERE s.status = 'OPEN' "
                + "AND s.locally_modified = 0 AND s.starts_at > '2026-11-20' AND s.session_id <> ? AND s.exam_id = "
                + "(SELECT exam_id FROM exam_session WHERE session_id = ?) AND s.capacity - (SELECT COUNT(*) FROM booking b "
                + "WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED')) > 2 ORDER BY s.session_id",
                Integer.class, sessionId, sessionId);
        List<String> candidates = eligibleCandidates(sessionId, target, 2);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", "it-observability-1");
        ResponseEntity<Map<String, Object>> booked = http.exchange("/api/v2/bookings", HttpMethod.POST,
                new HttpEntity<>(bookingBody(sessionId, candidates.get(0)), headers), MAP);
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(booked.getHeaders().getFirst("X-Request-Id")).isEqualTo("it-observability-1");
        long bookingId = ((Number) booked.getBody().get("bookingId")).longValue();

        ResponseEntity<Map<String, Object>> second = http.exchange("/api/v2/bookings", HttpMethod.POST,
                new HttpEntity<>(bookingBody(sessionId, candidates.get(1))), MAP);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long secondId = ((Number) second.getBody().get("bookingId")).longValue();

        assertThat(http.exchange("/api/v2/bookings", HttpMethod.POST,
                new HttpEntity<>(bookingBody(sessionId, candidates.get(0))), MAP).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(http.exchange("/api/v2/bookings/" + secondId + "/reschedule", HttpMethod.POST,
                new HttpEntity<>(Map.of("newSessionId", target)), MAP).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.exchange("/api/v2/bookings/" + bookingId + "?reason=it", HttpMethod.DELETE, null, MAP).getStatusCode()).isEqualTo(HttpStatus.OK);
        int capacity = db.queryForObject("SELECT capacity FROM exam_session WHERE session_id = ?", Integer.class, sessionId);
        assertThat(http.exchange("/api/v2/sessions/" + sessionId + "/capacity", HttpMethod.PUT,
                new HttpEntity<>(Map.of("newCapacity", capacity + 1, "actor", "it")), MAP).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/api/v2/bookings/999999999", HttpMethod.DELETE, null, MAP).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String scrape = scrape();
        for (String op : List.of("book", "cancel", "reschedule", "capacity")) {
            assertThat(scrape).as(op).containsPattern(series("scheduling_booking_duration_seconds_bucket",
                    "centerCode", center, "operation", op, "outcome", "success"));
        }
        assertThat(scrape).contains("# TYPE scheduling_booking_duration_seconds histogram");
        assertThat(scrape).containsPattern(series("scheduling_booking_duration_seconds_count",
                "centerCode", center, "operation", "book", "outcome", "business_error"));
        assertThat(value(scrape, series("scheduling_booking_errors_total",
                "centerCode", center, "operation", "book", "outcome", "business_error"))).isGreaterThanOrEqualTo(1);
        assertThat(value(scrape, series("scheduling_booking_errors_total",
                "centerCode", "unknown", "operation", "cancel", "outcome", "business_error"))).isGreaterThanOrEqualTo(1);
        assertThat(scrape).doesNotContainPattern("scheduling_booking_errors_total\\{[^}]*outcome=\"success\"");

        long pending = db.queryForObject("SELECT COUNT_BIG(*) FROM outbox_event WHERE relayed_at IS NULL", Long.class);
        assertThat(pending).as("relay is disabled, so the events stay pending").isGreaterThanOrEqualTo(4);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String s = scrape();
            assertThat(value(s, Pattern.compile("^scheduling_outbox_pending\\{[^}]*} (\\S+)$", Pattern.MULTILINE)))
                    .isEqualTo(pending);
            assertThat(value(s, Pattern.compile("^scheduling_outbox_lag_seconds\\{[^}]*} (\\S+)$", Pattern.MULTILINE)))
                    .isGreaterThan(0);
        });
        assertThat(scrape).contains("# TYPE scheduling_outbox_pending gauge", "# TYPE scheduling_outbox_lag_seconds gauge");
    }

    private String scrape() {
        ResponseEntity<String> r = http.getForEntity("/actuator/prometheus", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        return r.getBody();
    }

    /** A sample line of {@code metric} carrying (at least) the given label pairs, in Prometheus' sorted label order. */
    private static Pattern series(String metric, String... labels) {
        StringBuilder re = new StringBuilder("^").append(Pattern.quote(metric)).append("\\{");
        for (int i = 0; i < labels.length; i += 2) {
            re.append("(?:[^}]*,)?").append(labels[i]).append("=\"").append(Pattern.quote(labels[i + 1])).append("\"");
        }
        return Pattern.compile(re.append("[^}]*} (\\S+)$").toString(), Pattern.MULTILINE);
    }

    private static double value(String scrape, Pattern sample) {
        Matcher m = sample.matcher(scrape);
        assertThat(m.find()).as(sample.pattern()).isTrue();
        return Double.parseDouble(m.group(1));
    }

    private static Map<String, Object> bookingBody(int sessionId, String candidate) {
        Map<String, Object> body = new HashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateNumber", candidate);
        return body;
    }

    private int openSession(String condition) {
        return db.queryForObject("SELECT TOP 1 s.session_id FROM exam_session s WHERE s.status = 'OPEN' AND s.locally_modified = 0 "
                + "AND s.starts_at > '2026-11-20' AND " + condition + " ORDER BY s.session_id DESC", Integer.class);
    }

    private List<String> eligibleCandidates(int sessionId, int other, int n) {
        return db.queryForList("SELECT TOP (?) c.candidate_number FROM candidate_snapshot c WHERE NOT EXISTS ("
                + "SELECT 1 FROM booking b WHERE b.candidate_id = c.candidate_id AND b.session_id IN (?, ?)) AND NOT EXISTS ("
                + "SELECT 1 FROM exam_result_snapshot r WHERE r.candidate_id = c.candidate_id AND r.exam_id = "
                + "(SELECT exam_id FROM exam_session WHERE session_id = ?)) ORDER BY NEWID()", String.class, n, sessionId, other,
                sessionId);
    }
}
