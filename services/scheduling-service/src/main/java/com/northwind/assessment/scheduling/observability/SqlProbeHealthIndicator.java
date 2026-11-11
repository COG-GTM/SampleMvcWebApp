package com.northwind.assessment.scheduling.observability;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Readiness check that runs a SQL probe on a small bounded executor and gives up after {@code timeout}, so the
 * readiness endpoint never hangs on a stalled connection: a probe that throws, times out, or cannot be scheduled
 * because earlier probes are still stuck reports DOWN.
 */
public class SqlProbeHealthIndicator implements HealthIndicator {

    private final String database;
    private final Callable<Map<String, Object>> probe;
    private final Duration timeout;
    private final ExecutorService executor;

    public SqlProbeHealthIndicator(String database, Callable<Map<String, Object>> probe, Duration timeout,
                                   ExecutorService executor) {
        this.database = database;
        this.probe = probe;
        this.timeout = timeout;
        this.executor = executor;
    }

    @Override
    public Health health() {
        Future<Map<String, Object>> future;
        try {
            future = executor.submit(probe);
        } catch (RejectedExecutionException e) {
            return down("earlier probes are still blocked");
        }
        try {
            Map<String, Object> details = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return Health.up().withDetail("database", database).withDetails(details).build();
        } catch (TimeoutException e) {
            future.cancel(true);
            return down("no answer within " + timeout.toMillis() + " ms");
        } catch (ExecutionException e) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(e.getCause() == null ? e : e.getCause());
            log.warn("{} readiness probe failed: {}", database, cause.toString());
            return down(describe(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return down("interrupted");
        }
    }

    /** Readiness is unauthenticated: never echo driver messages (user, host); only our own probe checks' messages. */
    static String describe(Throwable cause) {
        if (cause instanceof SQLException sql) {
            return sql.getClass().getSimpleName() + " (SQLState " + sql.getSQLState() + ", error " + sql.getErrorCode() + ")";
        }
        if (cause instanceof IllegalStateException) {
            return cause.getMessage();
        }
        return cause.getClass().getSimpleName();
    }

    private static final Logger log = LoggerFactory.getLogger(SqlProbeHealthIndicator.class);

    private Health down(String error) {
        return Health.down().withDetail("database", database).withDetail("error", error).build();
    }
}
