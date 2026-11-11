package com.northwind.assessment.scheduling.persistence;

import com.northwind.assessment.scheduling.domain.BookingSlot;
import com.northwind.assessment.scheduling.domain.BookingStatus;
import com.northwind.assessment.scheduling.domain.PendingOffer;
import com.northwind.assessment.scheduling.domain.SeatKind;
import com.northwind.assessment.scheduling.domain.SessionCapacity;
import com.northwind.assessment.scheduling.domain.SessionStatus;
import com.northwind.assessment.scheduling.domain.WaitlistSlot;
import com.northwind.assessment.scheduling.persistence.Views.SessionView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** exam_session reads and the seat-inventory aggregate load (under a row lock). */
@Repository
public class SessionRepository {

    static final String SESSION_VIEW =
            "SELECT s.session_id, s.exam_id, e.exam_code, e.title, s.center_id, c.center_code, c.name AS center_name, "
          + "       r.room_name, s.starts_at, s.capacity, s.accommodation_capacity, s.status, "
          + "       (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED')) AS booked_seats, "
          + "       (SELECT COUNT(*) FROM booking b WHERE b.session_id = s.session_id AND b.status IN ('BOOKED','ATTENDED') "
          + "                                         AND b.accommodation_code IN ('ET25','ET50')) AS accommodation_used, "
          + "       (SELECT COUNT(*) FROM waitlist_entry w WHERE w.session_id = s.session_id) AS waitlist_count, "
          + "       (SELECT COUNT(*) FROM seat_offer o WHERE o.session_id = s.session_id AND o.status = 'PENDING') AS offered_seats "
          + "FROM exam_session s "
          + "LEFT JOIN exam_snapshot e ON e.exam_id = s.exam_id "
          + "LEFT JOIN test_center_snapshot c ON c.center_id = s.center_id "
          + "LEFT JOIN room_snapshot r ON r.room_id = s.room_id ";

    private static final int MAX_ROWS = 500;

    private final JdbcClient jdbc;

    public SessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SessionView> find(int sessionId) {
        return jdbc.sql(SESSION_VIEW + "WHERE s.session_id = ?").param(sessionId).query(SessionRepository::mapView).optional();
    }

    /** booking-api v1 search semantics: from inclusive, to exclusive, ordered by start, max 500 rows. */
    public List<SessionView> search(int centerId, Integer examId, LocalDateTime from, LocalDateTime to) {
        StringBuilder sql = new StringBuilder("SELECT TOP " + MAX_ROWS + " * FROM (" + SESSION_VIEW + ") x WHERE x.center_id = :centerId");
        Map<String, Object> params = new HashMap<>();
        params.put("centerId", centerId);
        if (examId != null) {
            sql.append(" AND x.exam_id = :examId");
            params.put("examId", examId);
        }
        if (from != null) {
            sql.append(" AND x.starts_at >= :from");
            params.put("from", from);
        }
        if (to != null) {
            sql.append(" AND x.starts_at < :to");
            params.put("to", to);
        }
        sql.append(" ORDER BY x.starts_at, x.session_id");
        return jdbc.sql(sql.toString()).params(params).query(SessionRepository::mapView).list();
    }

    /**
     * Loads the seat inventory of a session (bookings, waitlist, PENDING seat offers) with an update lock on its
     * row, so concurrent bookings, cancellations, capacity changes, offer decisions and imports of the same
     * session serialise (the legacy procs use
     * UPDLOCK on ExamSession the same way). Must run inside a transaction.
     */
    public Optional<SessionCapacity> lockForUpdate(int sessionId) {
        return load(sessionId, "WITH (UPDLOCK, ROWLOCK, HOLDLOCK) ");
    }

    /** Unlocked snapshot of the seat inventory, for reads (e.g. the waitlist offer states). */
    public Optional<SessionCapacity> snapshot(int sessionId) {
        return load(sessionId, "");
    }

    private Optional<SessionCapacity> load(int sessionId, String lockHint) {
        Optional<Object[]> row = jdbc.sql("SELECT exam_id, status, capacity, accommodation_capacity "
                        + "FROM exam_session " + lockHint + "WHERE session_id = ?")
                .param(sessionId)
                .query((rs, n) -> new Object[] {rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4)})
                .optional();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Object[] r = row.get();
        List<BookingSlot> bookings = jdbc.sql("SELECT booking_id, candidate_id, status, accommodation_code, booked_at "
                        + "FROM booking WHERE session_id = ?")
                .param(sessionId)
                .query((rs, n) -> new BookingSlot(rs.getLong(1), rs.getInt(2), BookingStatus.valueOf(rs.getString(3)),
                        rs.getString(4), rs.getTimestamp(5).toLocalDateTime()))
                .list();
        List<WaitlistSlot> waitlist = jdbc.sql("SELECT booking_id, position FROM waitlist_entry WHERE session_id = ?")
                .param(sessionId)
                .query((rs, n) -> new WaitlistSlot(rs.getLong(1), rs.getInt(2)))
                .list();
        List<PendingOffer> offers = jdbc.sql("SELECT offer_id, booking_id, seat_kind, offered_at, expires_at FROM seat_offer "
                        + "WHERE session_id = ? AND status = 'PENDING'")
                .param(sessionId)
                .query((rs, n) -> new PendingOffer(rs.getLong(1), rs.getLong(2), SeatKind.valueOf(rs.getString(3)),
                        rs.getTimestamp(4).toLocalDateTime(), rs.getTimestamp(5).toLocalDateTime()))
                .list();
        return Optional.of(new SessionCapacity(sessionId, (Integer) r[0], SessionStatus.valueOf((String) r[1]),
                (Integer) r[2], (Integer) r[3], bookings, waitlist, offers));
    }

    public Optional<Integer> examIdOf(int sessionId) {
        return jdbc.sql("SELECT exam_id FROM exam_session WHERE session_id = ?").param(sessionId).query(Integer.class).optional();
    }

    public void updateCapacity(int sessionId, int capacity, LocalDateTime now) {
        jdbc.sql("UPDATE exam_session SET capacity = ?, locally_modified = 1, last_modified = ? WHERE session_id = ?")
                .params(capacity, now, sessionId).update();
    }

    /** From the first write on, scheduling-service owns the session; re-imports skip it. */
    public void markLocallyModified(int sessionId, LocalDateTime now) {
        jdbc.sql("UPDATE exam_session SET locally_modified = 1, last_modified = ? WHERE session_id = ?")
                .params(now, sessionId).update();
    }

    public void insertCapacityChange(int sessionId, int oldCapacity, int newCapacity, List<Long> bumped, String actor,
                                     LocalDateTime now) {
        String ids = bumped.isEmpty() ? null : String.join(",", bumped.stream().map(String::valueOf).toList());
        jdbc.sql("INSERT INTO capacity_change (session_id, old_capacity, new_capacity, bumped_booking_ids, actor, changed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")
                .params(sessionId, oldCapacity, newCapacity, ids, actor, now).update();
    }

    static SessionView mapView(ResultSet rs, int n) throws SQLException {
        return new SessionView(rs.getInt("session_id"), rs.getInt("exam_id"), rs.getString("exam_code"),
                rs.getString("title"), rs.getInt("center_id"), rs.getString("center_code"), rs.getString("center_name"),
                rs.getString("room_name"), rs.getTimestamp("starts_at").toLocalDateTime(), rs.getInt("capacity"),
                rs.getInt("accommodation_capacity"), rs.getString("status"), rs.getInt("booked_seats"),
                rs.getInt("accommodation_used"), rs.getInt("waitlist_count"), rs.getInt("offered_seats"));
    }
}
