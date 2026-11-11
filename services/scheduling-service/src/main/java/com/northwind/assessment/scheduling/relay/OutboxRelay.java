package com.northwind.assessment.scheduling.relay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.assessment.scheduling.outbox.OutboxPayload;
import com.northwind.assessment.scheduling.persistence.BookingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drains {@code outbox_event} into AssessmentDelivery. For each pending event (event_id order): apply it in a
 * legacy transaction ({@link LegacyProjector}), then in a Scheduling transaction store the legacy BookingIds
 * back into {@code booking.legacy_booking_id} and stamp {@code relayed_at}. A crash between the two commits
 * leaves the event pending; the replay is a checkpoint no-op that still completes the write-back. A failing
 * event stops the batch (later events of the same session must not overtake it) and is retried next poll.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    public record PendingEvent(long eventId, String eventType, String payload) {
    }

    public record RunResult(int applied, int replayed, int failed) {
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final LegacyProjector projector;
    private final BookingRepository bookings;
    private final ObjectMapper mapper;
    private final Clock clock;

    public OutboxRelay(JdbcClient jdbc, TransactionTemplate tx, LegacyProjector projector, BookingRepository bookings,
                       ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.projector = projector;
        this.bookings = bookings;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Relays up to {@code batchSize} pending events. Rows still locked by an open transaction are skipped. */
    public synchronized RunResult runOnce(int batchSize) {
        List<PendingEvent> pending = jdbc.sql("SELECT TOP (?) event_id, event_type, payload FROM outbox_event WITH (READPAST) "
                        + "WHERE relayed_at IS NULL ORDER BY event_id")
                .param(batchSize)
                .query((rs, n) -> new PendingEvent(rs.getLong(1), rs.getString(2), rs.getString(3)))
                .list();
        int applied = 0;
        int replayed = 0;
        for (PendingEvent e : pending) {
            try {
                OutboxPayload payload = mapper.readValue(e.payload(), OutboxPayload.class);
                LegacyProjector.Applied a = projector.apply(e.eventId(), payload);
                complete(e.eventId(), a.legacyIds());
                if (a.replay()) {
                    replayed++;
                } else {
                    applied++;
                }
            } catch (Exception ex) {
                log.warn("relay: event {} ({}) failed, will retry: {}", e.eventId(), e.eventType(), ex.toString());
                markFailed(e.eventId(), ex);
                return new RunResult(applied, replayed, 1);
            }
        }
        return new RunResult(applied, replayed, 0);
    }

    /** Relays until nothing is pending (or an event fails). */
    public RunResult drain(int batchSize) {
        int applied = 0;
        int replayed = 0;
        while (true) {
            RunResult r = runOnce(batchSize);
            applied += r.applied();
            replayed += r.replayed();
            if (r.failed() > 0 || r.applied() + r.replayed() == 0) {
                return new RunResult(applied, replayed, r.failed());
            }
        }
    }

    private void complete(long eventId, Map<Long, Integer> legacyIds) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        tx.executeWithoutResult(s -> {
            legacyIds.forEach((bookingId, legacyId) -> {
                if (bookingId >= 50_000_000L) {
                    bookings.setLegacyBookingId(bookingId, legacyId);
                }
            });
            jdbc.sql("UPDATE outbox_event SET relayed_at = ?, attempts = attempts + 1, last_error = NULL WHERE event_id = ?")
                    .params(now, eventId).update();
        });
    }

    private void markFailed(long eventId, Exception ex) {
        String msg = String.valueOf(ex.getMessage());
        try {
            jdbc.sql("UPDATE outbox_event SET attempts = attempts + 1, last_error = ? WHERE event_id = ?")
                    .params(msg.length() > 1000 ? msg.substring(0, 1000) : msg, eventId).update();
        } catch (RuntimeException inner) {
            log.warn("relay: could not record failure of event {}: {}", eventId, inner.toString());
        }
    }
}
