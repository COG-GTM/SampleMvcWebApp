package com.northwind.assessment.scheduling.observability;

import com.northwind.assessment.scheduling.config.DataSourceConfig.LegacyReadOnlySource;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Readiness indicators ({@code schedulingDb}, {@code legacySource}, grouped with {@code readinessState} in
 * application.yml) and the outbox backlog reader. Every probe query has a JDBC query timeout and the whole probe a
 * wall-clock timeout ({@code scheduling.health.timeout-ms}, default 2000).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class ObservabilityConfig {

    private static final Pattern SAFE_TABLE = Pattern.compile("[A-Za-z0-9_]+");

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService healthProbeExecutor() {
        AtomicInteger n = new AtomicInteger();
        return new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "health-probe-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.AbortPolicy());
    }

    /** Scheduling DB reachable (SELECT 1) and Flyway history at the latest migration on the classpath. */
    @Bean
    public SqlProbeHealthIndicator schedulingDbHealthIndicator(DataSource dataSource, ObjectProvider<Flyway> flyway,
                                                              ExecutorService healthProbeExecutor,
                                                              @Value("${scheduling.health.timeout-ms:2000}") long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        JdbcTemplate jdbc = probeTemplate(dataSource, timeout);
        Flyway fw = flyway.getIfAvailable();
        Supplier<String> expected = fw == null ? null : memoize(() -> latestVersion(fw));
        String table = fw == null ? null : fw.getConfiguration().getTable();
        return new SqlProbeHealthIndicator("Scheduling", () -> schedulingProbe(jdbc, table, expected), timeout,
                healthProbeExecutor);
    }

    /** AssessmentDelivery read-only source of the snapshot importer (JdbcLegacyReferenceSource). */
    @Bean
    public SqlProbeHealthIndicator legacySourceHealthIndicator(LegacyReadOnlySource legacy,
                                                              ExecutorService healthProbeExecutor,
                                                              @Value("${scheduling.health.timeout-ms:2000}") long timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        JdbcTemplate jdbc = probeTemplate(legacy.dataSource(), timeout);
        return new SqlProbeHealthIndicator("AssessmentDelivery (read-only)", () -> legacyProbe(jdbc), timeout,
                healthProbeExecutor);
    }

    @Bean
    public OutboxBacklogReader outboxBacklogReader(DataSource dataSource,
                                                   @Value("${scheduling.health.timeout-ms:2000}") long timeoutMs) {
        JdbcTemplate jdbc = probeTemplate(dataSource, Duration.ofMillis(timeoutMs));
        return () -> jdbc.queryForObject("SELECT COUNT_BIG(*), MIN(created_at) FROM outbox_event WHERE relayed_at IS NULL",
                (rs, i) -> {
                    Timestamp oldest = rs.getTimestamp(2);
                    return new OutboxBacklogReader.Backlog(rs.getLong(1), oldest == null ? null : oldest.toLocalDateTime());
                });
    }

    static Map<String, Object> schedulingProbe(JdbcTemplate jdbc, String flywayTable, Supplier<String> expectedVersion) {
        jdbc.queryForObject("SELECT 1", Integer.class);
        Map<String, Object> details = new LinkedHashMap<>();
        if (flywayTable == null) {
            details.put("flyway", "disabled");
            return details;
        }
        if (!SAFE_TABLE.matcher(flywayTable).matches()) {
            throw new IllegalStateException("unexpected Flyway table name " + flywayTable);
        }
        String applied = jdbc.query("SELECT TOP 1 version FROM " + flywayTable
                        + " WHERE success = 1 AND version IS NOT NULL ORDER BY installed_rank DESC",
                rs -> rs.next() ? rs.getString(1) : null);
        String expected = expectedVersion.get();
        if (!Objects.equals(applied, expected)) {
            throw new IllegalStateException("Flyway migrations not complete: applied=" + applied + ", expected=" + expected);
        }
        details.put("flywayVersion", applied);
        return details;
    }

    static Map<String, Object> legacyProbe(JdbcTemplate jdbc) {
        jdbc.queryForObject("SELECT 1", Integer.class);
        return Map.of("access", "read-only");
    }

    static String latestVersion(Flyway flyway) {
        return Arrays.stream(flyway.info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(MigrationVersion::getVersion)
                .orElse(null);
    }

    static JdbcTemplate probeTemplate(DataSource ds, Duration timeout) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.setQueryTimeout((int) Math.max(1, timeout.toSeconds()));
        return jdbc;
    }

    /** Caches the first non-null result (the classpath migrations do not change at runtime). */
    static Supplier<String> memoize(Supplier<String> s) {
        return new Supplier<>() {
            private volatile String value;

            @Override
            public String get() {
                String v = value;
                if (v == null) {
                    v = s.get();
                    value = v;
                }
                return v;
            }
        };
    }
}
