package com.northwind.scheduling.outbox;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.scheduling.notification.CandidateCapacityImpactNotified;

/**
 * Transactional outbox (MBA-3823, V2__outbox.sql). {@link #append} runs inside the caller's transaction on the
 * Scheduling database, so the event commits or rolls back with the change it describes. The payload is the
 * resulting state of every affected booking and session, which makes the relay a state projection: applying an
 * event again writes the same values.
 */
@Repository
public class OutboxRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ObjectMapper json;

    public OutboxRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.json = json;
    }

    /** Appends one event; returns its sequence number. */
    public long append(OutboxEventType type, int aggregateId, Collection<Integer> bookingIds,
                       Collection<Integer> sessionIds, String actor, String detail) {
        OutboxPayload payload = new OutboxPayload(type.name(), detail, bookingStates(bookingIds), sessionStates(sessionIds));
        String body;
        try {
            body = json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload for " + type, e);
        }
        Long seq = jdbc.queryForObject("SET NOCOUNT ON; INSERT INTO outbox_event (event_type, aggregate_type, aggregate_id, "
                + "payload, actor) VALUES (?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS BIGINT);", Long.class,
                type.name(), type.aggregateType(), aggregateId, body, actor);
        return seq == null ? -1 : seq;
    }

    /**
     * MBA-3831: one CandidateCapacityImpactNotified event; the outbox event_id is the notification's eventId, so the
     * NotificationPort can deduplicate relay retries on it.
     */
    public long appendNotification(CandidateCapacityImpactNotified n, String actor) {
        OutboxEventType type = OutboxEventType.CandidateCapacityImpactNotified;
        OutboxPayload payload = new OutboxPayload(type.name(), n.impact() + " " + n.previousStatus() + "->" + n.newStatus(),
                List.of(), List.of(), n);
        String body;
        try {
            body = json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("outbox payload for " + type, e);
        }
        Long seq = jdbc.queryForObject("SET NOCOUNT ON; INSERT INTO outbox_event (event_id, event_type, aggregate_type, "
                + "aggregate_id, payload, actor) VALUES (?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS BIGINT);",
                Long.class, n.eventId().toString(), type.name(), type.aggregateType(), n.bookingId(), body, actor);
        return seq == null ? -1 : seq;
    }

    /** All sessions in one event: SeatsAvailable for every session as scheduling-service derives it. */
    public long appendInventoryBaseline(String actor) {
        List<Integer> ids = jdbc.queryForList("SELECT session_id FROM exam_session ORDER BY session_id", Integer.class);
        return append(OutboxEventType.InventoryBaselined, 0, List.of(), ids, actor, "sessions=" + ids.size());
    }

    public List<OutboxEvent> pending(int limit) {
        return jdbc.query("SELECT TOP (?) event_seq, event_id, event_type, aggregate_id, payload, actor, created_at "
                + "FROM outbox_event WHERE relayed_at IS NULL ORDER BY event_seq", (rs, i) -> new OutboxEvent(
                rs.getLong("event_seq"), UUID.fromString(rs.getString("event_id")),
                OutboxEventType.valueOf(rs.getString("event_type")), rs.getInt("aggregate_id"),
                read(rs.getString("payload")), rs.getString("actor"), rs.getTimestamp("created_at").toLocalDateTime()),
                limit);
    }

    /** Marks an event relayed and stores the legacy BookingIds the relay assigned (same id, IDENTITY_INSERT). */
    public void markRelayed(OutboxEvent e) {
        jdbc.update("UPDATE outbox_event SET relayed_at = SYSUTCDATETIME(), attempts = attempts + 1, last_error = NULL "
                + "WHERE event_seq = ? AND relayed_at IS NULL", e.seq());
        for (OutboxPayload.BookingState b : e.payload().bookings()) {
            jdbc.update("UPDATE booking SET legacy_booking_id = ? WHERE booking_id = ? AND legacy_booking_id IS NULL",
                    b.bookingId(), b.bookingId());
        }
    }

    public void markFailed(OutboxEvent e, String error) {
        String msg = error == null ? "unknown" : error.length() > 1000 ? error.substring(0, 1000) : error;
        jdbc.update("UPDATE outbox_event SET attempts = attempts + 1, last_error = ? WHERE event_seq = ?", msg, e.seq());
    }

    public Map<String, Object> stats() {
        return jdbc.queryForMap("SELECT COUNT(*) AS total, "
                + "SUM(CASE WHEN relayed_at IS NULL THEN 1 ELSE 0 END) AS pending, "
                + "MAX(event_seq) AS lastSeq, MAX(CASE WHEN relayed_at IS NOT NULL THEN event_seq END) AS lastRelayedSeq, "
                + "MIN(CASE WHEN relayed_at IS NULL THEN created_at END) AS oldestPendingAt FROM outbox_event");
    }

    private OutboxPayload read(String body) {
        try {
            return json.readValue(body, OutboxPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("unreadable outbox payload", e);
        }
    }

    private List<OutboxPayload.BookingState> bookingStates(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return named.query("SELECT b.booking_id, b.session_id, b.candidate_id, b.status, b.accommodation_code, b.channel, "
                + "b.external_ref, b.booked_at, b.cancelled_at, b.cancel_reason, w.position, w.added_at "
                + "FROM booking b LEFT JOIN waitlist_entry w ON w.booking_id = b.booking_id "
                + "WHERE b.booking_id IN (:ids) ORDER BY b.booking_id",
                new MapSqlParameterSource("ids", new ArrayList<>(new LinkedHashSet<>(ids))),
                (rs, i) -> new OutboxPayload.BookingState(rs.getInt("booking_id"), rs.getInt("session_id"),
                        rs.getInt("candidate_id"), rs.getString("status"), rs.getString("accommodation_code"),
                        rs.getString("channel"), rs.getString("external_ref"), ts(rs.getTimestamp("booked_at")),
                        ts(rs.getTimestamp("cancelled_at")), rs.getString("cancel_reason"),
                        (Integer) rs.getObject("position"), ts(rs.getTimestamp("added_at"))));
    }

    private List<OutboxPayload.SessionState> sessionStates(Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Integer, OutboxPayload.SessionState> out = new LinkedHashMap<>();
        // chunked: SQL Server allows ~2100 parameters per statement
        List<Integer> all = new ArrayList<>(new LinkedHashSet<>(ids));
        for (int from = 0; from < all.size(); from += 1000) {
            List<Integer> chunk = all.subList(from, Math.min(all.size(), from + 1000));
            named.query("SELECT s.session_id, s.capacity, s.accommodation_capacity, "
                    + "(SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED')) AS occupied "
                    + "FROM exam_session s WHERE s.session_id IN (:ids) ORDER BY s.session_id",
                    new MapSqlParameterSource("ids", chunk), rs -> {
                        int capacity = rs.getInt("capacity");
                        int occupied = rs.getInt("occupied");
                        int id = rs.getInt("session_id");
                        out.put(id, new OutboxPayload.SessionState(id, capacity, rs.getInt("accommodation_capacity"),
                                Math.max(0, capacity - occupied)));
                    });
        }
        return new ArrayList<>(out.values());
    }

    private static String ts(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toString();
    }
}
