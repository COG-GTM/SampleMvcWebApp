package com.northwind.assessment.scheduling.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.assessment.scheduling.domain.SeatFormulas;
import com.northwind.assessment.scheduling.outbox.OutboxPayload.BookingState;
import com.northwind.assessment.scheduling.outbox.OutboxPayload.SessionState;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Appends an event to {@code outbox_event}. Must run inside the transaction of the change it describes
 * ({@link Propagation#MANDATORY}): the event commits or rolls back with the change. The payload is read back
 * from the Scheduling tables inside that transaction, so it is exactly the committed state.
 */
@Component
public class OutboxWriter {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public OutboxWriter(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long append(OutboxEventType type, int sessionId, Long bookingId, Collection<Integer> sessionIds,
                       Collection<Long> bookingIds, String actor, Map<String, Object> detail, LocalDateTime now) {
        LinkedHashSet<Integer> sids = new LinkedHashSet<>();
        sids.add(sessionId);
        sids.addAll(sessionIds);
        List<SessionState> sessions = sids.stream().map(this::sessionState).toList();
        List<BookingState> bookings = new LinkedHashSet<>(bookingIds).stream().map(this::bookingState).toList();
        OutboxPayload payload = new OutboxPayload(type.name(), now, actor, sessions, bookings, detail == null ? Map.of() : detail);
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise outbox payload", e);
        }
        return jdbc.sql("INSERT INTO outbox_event (event_type, session_id, booking_id, payload, created_at) "
                        + "OUTPUT INSERTED.event_id VALUES (?, ?, ?, ?, ?)")
                .params(type.name(), sessionId, bookingId, json, now)
                .query(Long.class).single();
    }

    SessionState sessionState(int sessionId) {
        return jdbc.sql("SELECT s.capacity, s.accommodation_capacity, s.status, "
                        + "(SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED')), "
                        + "(SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED') "
                        + "    AND b.accommodation_code IN ('ET25','ET50')), "
                        + "(SELECT COUNT(*) FROM seat_offer o WHERE o.session_id = s.session_id AND o.status = 'PENDING') "
                        + "FROM exam_session s WHERE s.session_id = ?")
                .param(sessionId)
                .query((rs, n) -> new SessionState(sessionId, rs.getInt(1), rs.getInt(2),
                        SeatFormulas.seatsAvailable(rs.getInt(1), rs.getInt(4), rs.getInt(6)), rs.getInt(4), rs.getInt(5),
                        rs.getString(3), rs.getInt(6)))
                .single();
    }

    BookingState bookingState(long bookingId) {
        return jdbc.sql("SELECT b.booking_id, b.origin, b.legacy_booking_id, b.session_id, b.candidate_id, b.status, "
                        + "b.accommodation_code, b.channel, b.external_ref, b.booked_at, b.cancelled_at, b.cancel_reason, w.position, o.expires_at "
                        + "FROM booking b LEFT JOIN waitlist_entry w ON w.booking_id = b.booking_id "
                        + "LEFT JOIN seat_offer o ON o.booking_id = b.booking_id AND o.status = 'PENDING' WHERE b.booking_id = ?")
                .param(bookingId)
                .query((rs, n) -> {
                    int legacy = rs.getInt(3);
                    Integer legacyId = rs.wasNull() ? null : legacy;
                    int pos = rs.getInt(13);
                    Integer position = rs.wasNull() ? null : pos;
                    return new BookingState(rs.getLong(1), rs.getString(2), legacyId, rs.getInt(4), rs.getInt(5),
                            rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), ldt(rs.getTimestamp(10)),
                            ldt(rs.getTimestamp(11)), rs.getString(12), position, ldt(rs.getTimestamp(14)));
                })
                .single();
    }

    private static LocalDateTime ldt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
