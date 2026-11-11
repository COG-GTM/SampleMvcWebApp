package com.northwind.assessment.scheduling.relay;

import com.northwind.assessment.scheduling.outbox.OutboxEventType;
import com.northwind.assessment.scheduling.outbox.OutboxPayload;
import com.northwind.assessment.scheduling.outbox.OutboxPayload.BookingState;
import com.northwind.assessment.scheduling.outbox.OutboxPayload.SessionState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Applies one outbox event to AssessmentDelivery in one legacy transaction: the RelayCheckpoint row for the
 * event id is checked first under a key-range lock (a replay finds it and does nothing), then Booking / Waitlist /
 * ExamSession are written exactly as the payload says, then one AuditLog row and the checkpoint row. Modern bookings get a legacy identity BookingId on first relay,
 * remembered in dbo.RelayBookingMap inside the same transaction.
 */
@Component
public class LegacyProjector {

    public static final String ACTOR = "scheduling-relay";

    /** Outcome of applying one event. {@code legacyIds}: Scheduling booking id -> AssessmentDelivery BookingId. */
    public record Applied(long eventId, boolean replay, Map<Long, Integer> legacyIds) {
    }

    private final RelayConfig.LegacyRelayDb db;

    public LegacyProjector(RelayConfig.LegacyRelayDb db) {
        this.db = db;
    }

    public Applied apply(long eventId, OutboxPayload p) {
        return db.tx().execute(status -> applyInTransaction(eventId, p));
    }

    private Applied applyInTransaction(long eventId, OutboxPayload p) {
        JdbcClient jdbc = db.jdbc();
        OutboxEventType type = OutboxEventType.valueOf(p.eventType());
        Map<Long, Integer> legacyIds = new LinkedHashMap<>();
        boolean done = jdbc.sql("SELECT COUNT(*) FROM dbo.RelayCheckpoint WITH (UPDLOCK, HOLDLOCK) WHERE EventId = ?")
                .param(eventId).query(Integer.class).single() > 0;
        if (done) {
            for (BookingState b : p.bookings()) {
                existingLegacyId(b).ifPresent(id -> legacyIds.put(b.bookingId(), id));
            }
            return new Applied(eventId, true, legacyIds);
        }

        for (BookingState b : p.bookings()) {
            int legacyId = upsertBooking(b);
            legacyIds.put(b.bookingId(), legacyId);
            syncWaitlist(b, legacyId);
        }
        for (SessionState s : p.sessions()) {
            int n = jdbc.sql("UPDATE dbo.ExamSession SET Capacity = ?, AccommodationCapacity = ?, SeatsAvailable = ? "
                            + "WHERE SessionId = ?")
                    .params(s.capacity(), s.accommodationCapacity(), s.seatsAvailable(), s.sessionId()).update();
            if (n != 1) {
                throw new IllegalStateException("ExamSession " + s.sessionId() + " not found in AssessmentDelivery");
            }
        }

        Integer primaryBooking = null;
        Object detailBooking = p.detail().get("toBookingId");
        if (detailBooking instanceof Number num) {
            primaryBooking = legacyIds.get(num.longValue());
        } else if (type != OutboxEventType.CapacityChanged && !p.bookings().isEmpty()) {
            primaryBooking = legacyIds.get(p.bookings().get(0).bookingId());
        }
        int sessionId = p.sessions().get(0).sessionId();
        boolean bookingEntity = primaryBooking != null;
        jdbc.sql("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)")
                .params(bookingEntity ? "Booking" : "ExamSession", bookingEntity ? primaryBooking : sessionId,
                        type.auditAction(), auditDetail(eventId, p, legacyIds), ACTOR)
                .update();
        jdbc.sql("INSERT INTO dbo.RelayCheckpoint (EventId, EventType, SessionId, BookingId) VALUES (?, ?, ?, ?)")
                .params(eventId, type.name(), sessionId, primaryBooking).update();
        return new Applied(eventId, false, legacyIds);
    }

    private Optional<Integer> existingLegacyId(BookingState b) {
        if ("LEGACY_IMPORT".equals(b.origin())) {
            return Optional.of(Math.toIntExact(b.bookingId()));
        }
        Optional<Integer> mapped = db.jdbc().sql("SELECT LegacyBookingId FROM dbo.RelayBookingMap WHERE ModernBookingId = ?")
                .param(b.bookingId()).query(Integer.class).optional();
        return mapped.isPresent() ? mapped : Optional.ofNullable(b.legacyBookingId());
    }

    private int upsertBooking(BookingState b) {
        JdbcClient jdbc = db.jdbc();
        Optional<Integer> existing = existingLegacyId(b);
        if (existing.isPresent()) {
            int n = jdbc.sql("UPDATE dbo.Booking SET SessionId = ?, Status = ?, AccommodationCode = ?, CancelledAt = ?, "
                            + "CancelReason = ?, LastModified = GETDATE() WHERE BookingId = ?")
                    .params(b.sessionId(), b.status(), b.accommodationCode(), b.cancelledAt(), b.cancelReason(), existing.get())
                    .update();
            if (n != 1) {
                throw new IllegalStateException("Booking " + existing.get() + " (scheduling " + b.bookingId()
                        + ") not found in AssessmentDelivery");
            }
            return existing.get();
        }
        // dbo.Booking has triggers, so OUTPUT needs INTO (SQL Server error 334)
        int legacyId = jdbc.sql("SET NOCOUNT ON; DECLARE @ids TABLE (BookingId INT); "
                        + "INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, "
                        + "ExternalRef, BookedAt, CancelledAt, CancelReason) OUTPUT INSERTED.BookingId INTO @ids "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?); SELECT BookingId FROM @ids")
                .params(b.sessionId(), b.candidateId(), b.status(), b.accommodationCode(), b.channel(), b.externalRef(),
                        b.bookedAt(), b.cancelledAt(), b.cancelReason())
                .query(Integer.class).single();
        jdbc.sql("INSERT INTO dbo.RelayBookingMap (ModernBookingId, LegacyBookingId) VALUES (?, ?)")
                .params(b.bookingId(), legacyId).update();
        return legacyId;
    }

    private void syncWaitlist(BookingState b, int legacyId) {
        JdbcClient jdbc = db.jdbc();
        if (b.waitlistPosition() == null) {
            jdbc.sql("DELETE FROM dbo.Waitlist WHERE BookingId = ?").param(legacyId).update();
            return;
        }
        // OfferExpiresAt (db/schema/012): set while the entry holds a PENDING seat offer, so legacy readers and the
        // seat-drift check see the held seat (ADR 0003).
        int n = jdbc.sql("UPDATE dbo.Waitlist SET SessionId = ?, Position = ?, OfferExpiresAt = ? WHERE BookingId = ?")
                .params(b.sessionId(), b.waitlistPosition(), b.offerExpiresAt(), legacyId).update();
        if (n == 0) {
            jdbc.sql("INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position, OfferExpiresAt) VALUES (?, ?, ?, ?, ?)")
                    .params(b.sessionId(), b.candidateId(), legacyId, b.waitlistPosition(), b.offerExpiresAt()).update();
        }
    }

    static String auditDetail(long eventId, OutboxPayload p, Map<Long, Integer> legacyIds) {
        StringBuilder sb = new StringBuilder("outbox event=").append(eventId).append(' ').append(p.eventType());
        for (SessionState s : p.sessions()) {
            sb.append(" session=").append(s.sessionId()).append(" capacity=").append(s.capacity())
                    .append(" seats=").append(s.seatsAvailable());
            if (s.seatsOffered() > 0) {
                sb.append(" offered=").append(s.seatsOffered());
            }
        }
        for (BookingState b : p.bookings()) {
            sb.append(" booking=").append(legacyIds.get(b.bookingId())).append('/').append(b.bookingId())
                    .append(' ').append(b.status());
        }
        if (p.actor() != null) {
            sb.append(" by=").append(p.actor());
        }
        return sb.length() > 1000 ? sb.substring(0, 997) + "..." : sb.toString();
    }
}
