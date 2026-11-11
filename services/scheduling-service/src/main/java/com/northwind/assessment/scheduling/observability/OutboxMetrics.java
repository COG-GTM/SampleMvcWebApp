package com.northwind.assessment.scheduling.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code scheduling_outbox_pending} (events with {@code relayed_at IS NULL}) and {@code scheduling_outbox_lag_seconds}
 * (age of the oldest pending event, 0 when none). The backlog is read on a schedule
 * ({@code scheduling.metrics.outbox-refresh-ms}, default 5 s) and cached, so a Prometheus scrape never touches the
 * database. When a refresh fails the last snapshot is kept (the lag keeps growing from the cached oldest event) and
 * {@code scheduling_outbox_refresh_failures_total} is incremented.
 */
@Component
public class OutboxMetrics {

    public static final String PENDING = "scheduling.outbox.pending";
    public static final String LAG = "scheduling.outbox.lag";
    public static final String REFRESH_FAILURES = "scheduling.outbox.refresh.failures";

    private static final Logger log = LoggerFactory.getLogger(OutboxMetrics.class);

    private final OutboxBacklogReader reader;
    private final Clock clock;
    private final AtomicReference<OutboxBacklogReader.Backlog> snapshot =
            new AtomicReference<>(OutboxBacklogReader.Backlog.EMPTY);
    private final Counter refreshFailures;
    private volatile boolean failing;

    public OutboxMetrics(MeterRegistry registry, OutboxBacklogReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
        Gauge.builder(PENDING, this, OutboxMetrics::pending)
                .description("Outbox events not yet relayed to AssessmentDelivery")
                .register(registry);
        Gauge.builder(LAG, this, OutboxMetrics::lagSeconds)
                .description("Age of the oldest outbox event not yet relayed (0 when none)")
                .baseUnit("seconds")
                .register(registry);
        this.refreshFailures = Counter.builder(REFRESH_FAILURES)
                .description("Failed reads of the outbox backlog for the outbox gauges")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${scheduling.metrics.outbox-refresh-ms:5000}", initialDelay = 0)
    public void refresh() {
        try {
            snapshot.set(reader.read());
            if (failing) {
                log.info("outbox metrics: backlog read recovered");
                failing = false;
            }
        } catch (RuntimeException e) {
            refreshFailures.increment();
            if (!failing) {
                log.warn("outbox metrics: backlog read failed, keeping last snapshot: {}", e.toString());
                failing = true;
            }
        }
    }

    double pending() {
        return snapshot.get().pending();
    }

    double lagSeconds() {
        OutboxBacklogReader.Backlog b = snapshot.get();
        if (b.pending() == 0 || b.oldestCreatedAt() == null) {
            return 0;
        }
        Duration age = Duration.between(b.oldestCreatedAt(), LocalDateTime.now(clock));
        return Math.max(0, age.toMillis() / 1000.0);
    }
}
