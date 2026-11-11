package com.northwind.assessment.scheduling.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;

/**
 * MBA-3832 AC1/AC5: pausing the SQL Server container flips readiness to DOWN (503) while liveness stays UP, and
 * readiness recovers when the database is back. Uses its own container so the shared one of the other ITs is
 * never paused.
 */
@AutoConfigureObservability(tracing = false)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "scheduling.health.timeout-ms=1000",
        "spring.datasource.hikari.connection-timeout=2000",
        "spring.datasource.hikari.validation-timeout=1000"})
class ReadinessIT {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    static final MSSQLServerContainer<?> OWN_SQL = SqlServerTestBase.startWithLegacyDatabase();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> SqlServerTestBase.jdbcUrl(OWN_SQL, "Scheduling"));
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> SqlServerTestBase.SA_PASSWORD);
        registry.add("scheduling.legacy.url", () -> SqlServerTestBase.jdbcUrl(OWN_SQL, "AssessmentDelivery"));
    }

    @AfterAll
    static void stopContainer() {
        OWN_SQL.stop();
    }

    @Autowired
    TestRestTemplate http;

    @Test
    void readinessFlipsWhileTheDatabaseIsPaused_livenessStaysUp() {
        ResponseEntity<Map<String, Object>> ready = health("readiness");
        assertThat(ready.getStatusCode().value()).isEqualTo(200);
        assertThat(ready.getBody()).containsEntry("status", "UP");
        assertThat(components(ready)).containsKeys("readinessState", "schedulingDb", "legacySource");
        assertThat(component(ready, "schedulingDb").get("details")).asString().contains("flywayVersion=2");
        assertThat(health("liveness").getBody()).containsEntry("status", "UP");

        var docker = OWN_SQL.getDockerClient();
        docker.pauseContainerCmd(OWN_SQL.getContainerId()).exec();
        try {
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                ResponseEntity<Map<String, Object>> down = health("readiness");
                assertThat(down.getStatusCode().value()).isEqualTo(503);
                assertThat(down.getBody()).containsEntry("status", "DOWN");
                assertThat(component(down, "schedulingDb")).containsEntry("status", "DOWN");
                assertThat(component(down, "legacySource")).containsEntry("status", "DOWN");
            });
            long start = System.nanoTime();
            ResponseEntity<Map<String, Object>> live = health("liveness");
            assertThat(live.getStatusCode().value()).isEqualTo(200);
            assertThat(live.getBody()).containsEntry("status", "UP");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            ResponseEntity<String> scrape = http.getForEntity("/actuator/prometheus", String.class);
            assertThat(scrape.getStatusCode().value()).isEqualTo(200);
            assertThat(scrape.getBody()).contains("scheduling_outbox_pending");
        } finally {
            docker.unpauseContainerCmd(OWN_SQL.getContainerId()).exec();
        }

        await().atMost(Duration.ofSeconds(90)).pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(health("readiness").getStatusCode().value()).isEqualTo(200));
    }

    private ResponseEntity<Map<String, Object>> health(String group) {
        return http.exchange("/actuator/health/" + group, HttpMethod.GET, null, MAP);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> components(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("components");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> component(ResponseEntity<Map<String, Object>> r, String name) {
        return (Map<String, Object>) components(r).get(name);
    }
}
