package com.northwind.scheduling.relay;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.northwind.scheduling.outbox.OutboxEvent;
import com.northwind.scheduling.outbox.OutboxPayload;

/**
 * Applies one outbox event to AssessmentDelivery as ad_sched_relay (MBA-3823), in one legacy transaction:
 * <ol>
 *   <li>{@code SESSION_CONTEXT(N'writer') = N'scheduling-relay'} so trg_Booking_SeatSync / trg_ExamSession_Update
 *       skip their side effects (HB-01, HB-04, HB-05; 032/033 return early);</li>
 *   <li>if dbo.RelayCheckpoint already has the event id the event is a no-op (replay after a crash);</li>
 *   <li>ExamSession Capacity/AccommodationCapacity/SeatsAvailable, Booking rows (new ones keep scheduling-service's
 *       BookingId via IDENTITY_INSERT), Waitlist rows, one AuditLog row and the checkpoint row.</li>
 * </ol>
 */
public class LegacyRelay {

    private static final Logger log = LoggerFactory.getLogger(LegacyRelay.class);
    public static final String WRITER = "scheduling-relay";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public LegacyRelay(JdbcTemplate legacyJdbc) {
        this.jdbc = legacyJdbc;
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(legacyJdbc.getDataSource()));
    }

    /** @return true if applied now, false if the checkpoint shows it was applied before or it has no legacy projection */
    public boolean apply(OutboxEvent e) {
        if (!e.type().projectsToLegacy()) {
            return false;
        }
        Boolean applied = tx.execute(status -> {
            jdbc.execute("EXEC sp_set_session_context @key = N'writer', @value = N'" + WRITER + "'");
            Integer done = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RelayCheckpoint WITH (UPDLOCK, HOLDLOCK) "
                    + "WHERE EventId = ?", Integer.class, e.eventId().toString());
            if (done != null && done > 0) {
                return false;
            }
            OutboxPayload p = e.payload();
            for (OutboxPayload.SessionState s : p.sessions()) {
                jdbc.update("UPDATE dbo.ExamSession SET Capacity = ?, AccommodationCapacity = ?, SeatsAvailable = ? "
                        + "WHERE SessionId = ? AND (Capacity <> ? OR AccommodationCapacity <> ? OR SeatsAvailable <> ?)",
                        s.capacity(), s.accommodationCapacity(), s.seatsAvailable(), s.sessionId(),
                        s.capacity(), s.accommodationCapacity(), s.seatsAvailable());
            }
            for (OutboxPayload.BookingState b : p.bookings()) {
                upsertBooking(b);
            }
            for (OutboxPayload.BookingState b : p.bookings()) {
                upsertWaitlist(b);
            }
            jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                    e.type().aggregateType(), e.aggregateId(), e.type().auditAction(),
                    truncate("outbox seq=" + e.seq() + " event=" + e.eventId() + " " + e.type()
                            + (p.detail() == null ? "" : " " + p.detail()), 1000),
                    truncate(e.actor(), 60));
            jdbc.update("INSERT INTO dbo.RelayCheckpoint (EventId, EventSeq, EventType) VALUES (?, ?, ?)",
                    e.eventId().toString(), e.seq(), e.type().name());
            return true;
        });
        if (Boolean.TRUE.equals(applied)) {
            log.debug("relayed outbox seq={} {} {}", e.seq(), e.type(), e.eventId());
        }
        return Boolean.TRUE.equals(applied);
    }

    private void upsertBooking(OutboxPayload.BookingState b) {
        int n = jdbc.update("UPDATE dbo.Booking SET SessionId = ?, Status = ?, AccommodationCode = ?, Channel = ?, "
                        + "ExternalRef = ?, CancelledAt = ?, CancelReason = ?, LastModified = GETDATE() WHERE BookingId = ?",
                b.sessionId(), b.status(), b.accommodationCode(), b.channel(), b.externalRef(), ts(b.cancelledAt()),
                b.cancelReason(), b.bookingId());
        if (n > 0) {
            return;
        }
        jdbc.update("SET IDENTITY_INSERT dbo.Booking ON; "
                        + "INSERT INTO dbo.Booking (BookingId, SessionId, CandidateId, Status, AccommodationCode, Channel, "
                        + "ExternalRef, BookedAt, CancelledAt, CancelReason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?); "
                        + "SET IDENTITY_INSERT dbo.Booking OFF;",
                b.bookingId(), b.sessionId(), b.candidateId(), b.status(), b.accommodationCode(), b.channel(),
                b.externalRef(), ts(b.bookedAt()), ts(b.cancelledAt()), b.cancelReason());
    }

    private void upsertWaitlist(OutboxPayload.BookingState b) {
        if (b.waitlistPosition() == null) {
            jdbc.update("DELETE FROM dbo.Waitlist WHERE BookingId = ?", b.bookingId());
            return;
        }
        int n = jdbc.update("UPDATE dbo.Waitlist SET SessionId = ?, CandidateId = ?, Position = ? WHERE BookingId = ?",
                b.sessionId(), b.candidateId(), b.waitlistPosition(), b.bookingId());
        if (n == 0) {
            jdbc.update("INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position, AddedAt) "
                            + "VALUES (?, ?, ?, ?, COALESCE(?, GETDATE()))",
                    b.sessionId(), b.candidateId(), b.bookingId(), b.waitlistPosition(), ts(b.waitlistAddedAt()));
        }
    }

    public List<String> checkpointedEventIds() {
        return jdbc.queryForList("SELECT CONVERT(VARCHAR(36), EventId) FROM dbo.RelayCheckpoint ORDER BY EventSeq",
                String.class);
    }

    static Timestamp ts(String iso) {
        return iso == null ? null : Timestamp.valueOf(LocalDateTime.parse(iso));
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
