package com.northwind.assessment.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OutboxMetricsTest {

    private static final ZoneId ZONE = ZoneId.of("America/Chicago");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 9, 0, 0);
    private final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicReference<Object> next = new AtomicReference<>();
    private int reads;

    private final OutboxMetrics metrics = new OutboxMetrics(registry, () -> {
        reads++;
        Object n = next.get();
        if (n instanceof RuntimeException e) {
            throw e;
        }
        return (OutboxBacklogReader.Backlog) n;
    }, clock);

    private double gauge(String name) {
        return registry.find(name).gauge().value();
    }

    @Test
    void beforeFirstRefresh_gaugesAreZero() {
        assertThat(gauge(OutboxMetrics.PENDING)).isZero();
        assertThat(gauge(OutboxMetrics.LAG)).isZero();
        assertThat(registry.find(OutboxMetrics.LAG).gauge().getId().getBaseUnit()).isEqualTo("seconds");
    }

    @Test
    void pendingAndLagOfOldestEvent() {
        next.set(new OutboxBacklogReader.Backlog(3, NOW.minusSeconds(42).minusNanos(500_000_000)));
        metrics.refresh();

        assertThat(gauge(OutboxMetrics.PENDING)).isEqualTo(3);
        assertThat(gauge(OutboxMetrics.LAG)).isEqualTo(42.5);
    }

    @Test
    void noPendingEvents_lagIsZero() {
        next.set(new OutboxBacklogReader.Backlog(0, null));
        metrics.refresh();
        assertThat(gauge(OutboxMetrics.LAG)).isZero();
    }

    @Test
    void clockSkew_neverNegative() {
        next.set(new OutboxBacklogReader.Backlog(1, NOW.plusSeconds(5)));
        metrics.refresh();
        assertThat(gauge(OutboxMetrics.LAG)).isZero();
    }

    @Test
    void scrapesDoNotReadTheDatabase() {
        next.set(new OutboxBacklogReader.Backlog(1, NOW.minusSeconds(1)));
        metrics.refresh();
        for (int i = 0; i < 5; i++) {
            gauge(OutboxMetrics.PENDING);
            gauge(OutboxMetrics.LAG);
        }
        assertThat(reads).isEqualTo(1);
    }

    @Test
    void failedRefresh_keepsLastSnapshotAndCountsFailure() {
        next.set(new OutboxBacklogReader.Backlog(2, NOW.minusSeconds(10)));
        metrics.refresh();
        next.set(new IllegalStateException("connection refused"));
        metrics.refresh();
        metrics.refresh();

        assertThat(gauge(OutboxMetrics.PENDING)).isEqualTo(2);
        assertThat(gauge(OutboxMetrics.LAG)).isEqualTo(10);
        assertThat(registry.find(OutboxMetrics.REFRESH_FAILURES).counter().count()).isEqualTo(2);

        next.set(new OutboxBacklogReader.Backlog(0, null));
        metrics.refresh();
        assertThat(gauge(OutboxMetrics.PENDING)).isZero();
    }
}
