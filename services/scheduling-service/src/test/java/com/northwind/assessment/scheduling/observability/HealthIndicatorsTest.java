package com.northwind.assessment.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class HealthIndicatorsTest {

    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private final ExecutorService executor = new ObservabilityConfig().healthProbeExecutor();

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    private static DataSource unreachable() throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("Login failed for user 'sched_app' at db.internal:1433", "28000", 18456));
        return ds;
    }

    @SuppressWarnings("unchecked")
    private static JdbcTemplate scheduling(String appliedVersion) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        when(jdbc.query(anyString(), any(ResultSetExtractor.class))).thenReturn(appliedVersion);
        return jdbc;
    }

    private SqlProbeHealthIndicator indicator(java.util.concurrent.Callable<Map<String, Object>> probe) {
        return new SqlProbeHealthIndicator("Scheduling", probe, TIMEOUT, executor);
    }

    @Test
    void schedulingDb_upWhenReachableAndFlywayAtLatest() {
        Health h = indicator(() -> ObservabilityConfig.schedulingProbe(scheduling("2"), "flyway_schema_history", () -> "2"))
                .health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("flywayVersion", "2").containsEntry("database", "Scheduling");
    }

    @Test
    void schedulingDb_downWhileMigrationsAreBehind() {
        Health h = indicator(() -> ObservabilityConfig.schedulingProbe(scheduling("1"), "flyway_schema_history", () -> "2"))
                .health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails().get("error")).asString().contains("applied=1, expected=2");
    }

    @Test
    void schedulingDb_downBeforeAnyMigration() {
        Health h = indicator(() -> ObservabilityConfig.schedulingProbe(scheduling(null), "flyway_schema_history", () -> "2"))
                .health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void schedulingDb_flywayDisabled_onlyConnectivity() {
        Health h = indicator(() -> ObservabilityConfig.schedulingProbe(scheduling(null), null, null)).health();
        assertThat(h.getStatus()).isEqualTo(Status.UP);
        assertThat(h.getDetails()).containsEntry("flyway", "disabled");
    }

    @Test
    void schedulingDb_rejectsUnsafeFlywayTableName() {
        Health h = indicator(() -> ObservabilityConfig.schedulingProbe(scheduling("2"), "x;DROP TABLE y", () -> "2")).health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void schedulingDb_downWithFailingDataSource() throws Exception {
        JdbcTemplate jdbc = ObservabilityConfig.probeTemplate(unreachable(), TIMEOUT);
        Health h = indicator(() -> ObservabilityConfig.schedulingProbe(jdbc, "flyway_schema_history", () -> "2")).health();
        assertThat(h.getStatus()).isEqualTo(Status.DOWN);
        assertThat(h.getDetails().get("error")).asString()
                .isEqualTo("SQLException (SQLState 28000, error 18456)")
                .doesNotContain("sched_app").doesNotContain("db.internal");
    }

    @Test
    void legacySource_upAndDownWithFailingDataSource() throws Exception {
        JdbcTemplate ok = mock(JdbcTemplate.class);
        when(ok.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        assertThat(new SqlProbeHealthIndicator("AssessmentDelivery (read-only)", () -> ObservabilityConfig.legacyProbe(ok),
                TIMEOUT, executor).health().getStatus()).isEqualTo(Status.UP);

        JdbcTemplate failing = ObservabilityConfig.probeTemplate(unreachable(), TIMEOUT);
        Health down = new SqlProbeHealthIndicator("AssessmentDelivery (read-only)",
                () -> ObservabilityConfig.legacyProbe(failing), TIMEOUT, executor).health();
        assertThat(down.getStatus()).isEqualTo(Status.DOWN);
        assertThat(down.getDetails()).containsEntry("database", "AssessmentDelivery (read-only)");
    }

    @Test
    void hangingProbe_isDownWithinTheTimeout_andSaturationStaysDown() throws Exception {
        CountDownLatch never = new CountDownLatch(1);
        SqlProbeHealthIndicator hanging = indicator(() -> {
            while (true) {
                try {
                    never.await();
                } catch (InterruptedException ignored) {
                    // a socket read does not react to interrupts either
                }
            }
        });
        for (int i = 0; i < 4; i++) {
            long start = System.nanoTime();
            Health h = hanging.health();
            assertThat(h.getStatus()).isEqualTo(Status.DOWN);
            assertThat(h.getDetails().get("error")).asString().contains("no answer within 300 ms");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        }
        Health saturated = hanging.health();
        assertThat(saturated.getStatus()).isEqualTo(Status.DOWN);
        assertThat(saturated.getDetails().get("error")).asString().contains("still blocked");
        never.countDown();
    }

    @Test
    void probeTemplate_hasAQueryTimeoutOfAtLeastOneSecond() throws Exception {
        assertThat(ObservabilityConfig.probeTemplate(unreachable(), Duration.ofMillis(300)).getQueryTimeout()).isEqualTo(1);
        assertThat(ObservabilityConfig.probeTemplate(unreachable(), Duration.ofSeconds(3)).getQueryTimeout()).isEqualTo(3);
    }

    @Test
    void memoize_cachesFirstNonNull() {
        int[] calls = {0};
        var s = ObservabilityConfig.memoize(() -> ++calls[0] < 2 ? null : "2");
        assertThat(s.get()).isNull();
        assertThat(s.get()).isEqualTo("2");
        assertThat(s.get()).isEqualTo("2");
        assertThat(calls[0]).isEqualTo(2);
    }
}
