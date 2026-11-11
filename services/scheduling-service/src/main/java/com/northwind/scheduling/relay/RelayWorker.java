package com.northwind.scheduling.relay;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.northwind.scheduling.outbox.OutboxEvent;
import com.northwind.scheduling.outbox.OutboxRepository;

/**
 * Polls outbox_event in event_seq order and hands each pending event to {@link LegacyRelay} (MBA-3823). An event is
 * marked relayed in Scheduling only after its legacy transaction committed; a crash in between leaves it pending and
 * the replay is a checkpointed no-op. A failing event stops the batch so later events never overtake it.
 */
@Component
@ConditionalOnProperty(name = "scheduling.relay.enabled", havingValue = "true")
public class RelayWorker {

    private static final Logger log = LoggerFactory.getLogger(RelayWorker.class);

    private final OutboxRepository outbox;
    private final LegacyRelay relay;
    private final int batchSize;
    private final AtomicLong applied = new AtomicLong();
    private final AtomicLong skipped = new AtomicLong();
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong lagMillis = new AtomicLong();

    public RelayWorker(OutboxRepository outbox, LegacyRelay relay,
                       @Value("${scheduling.relay.batch-size:200}") int batchSize,
                       ObjectProvider<MeterRegistry> meters) {
        this.outbox = outbox;
        this.relay = relay;
        this.batchSize = batchSize;
        meters.ifAvailable(r -> {
            Gauge.builder("scheduling.outbox.pending", pending, AtomicLong::get)
                    .description("Outbox events not yet relayed to AssessmentDelivery").register(r);
            Gauge.builder("scheduling.outbox.lag", lagMillis, v -> v.get() / 1000.0).baseUnit("seconds")
                    .description("Age of the oldest unrelayed outbox event").register(r);
        });
    }

    @Scheduled(fixedDelayString = "${scheduling.relay.poll-ms:250}", initialDelayString = "${scheduling.relay.poll-ms:250}")
    public void poll() {
        try {
            relayPending();
            refreshGauges();
        } catch (RuntimeException e) {
            log.warn("relay poll failed: {}", e.getMessage());
        }
    }

    /** One pass over the pending events; returns how many were processed (applied or replay no-ops). */
    public synchronized int relayPending() {
        int processed = 0;
        for (OutboxEvent e : outbox.pending(batchSize)) {
            try {
                if (relay.apply(e)) {
                    applied.incrementAndGet();
                } else {
                    skipped.incrementAndGet();
                }
            } catch (RuntimeException ex) {
                log.error("relay of outbox seq={} {} failed: {}", e.seq(), e.type(), ex.getMessage());
                outbox.markFailed(e, ex.getMessage());
                break;
            }
            outbox.markRelayed(e);
            processed++;
        }
        return processed;
    }

    public long appliedCount() {
        return applied.get();
    }

    public long skippedCount() {
        return skipped.get();
    }

    void refreshGauges() {
        Map<String, Object> st = outbox.stats();
        pending.set(st.get("pending") == null ? 0 : ((Number) st.get("pending")).longValue());
        Object oldest = st.get("oldestPendingAt");
        LocalDateTime at = oldest instanceof Timestamp t ? t.toLocalDateTime()
                : oldest instanceof LocalDateTime l ? l : null;
        lagMillis.set(at == null ? 0 : Math.max(0, Duration.between(at, LocalDateTime.now(ZoneOffset.UTC)).toMillis()));
    }
}
