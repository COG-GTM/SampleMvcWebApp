package com.northwind.assessment.scheduling.persistence;

import com.northwind.assessment.scheduling.persistence.Views.BookingView;
import com.northwind.assessment.scheduling.persistence.Views.RouteView;
import com.northwind.assessment.scheduling.persistence.Views.WaitlistView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** booking + waitlist_entry. */
@Repository
public class BookingRepository {

    private static final String BOOKING_VIEW =
            "SELECT b.booking_id, b.session_id, b.candidate_id, c.candidate_number, e.exam_code, s.starts_at, b.status, "
          + "       b.accommodation_code, b.channel, b.external_ref, b.booked_at, b.cancelled_at, b.cancel_reason, "
          + "       b.origin, b.legacy_booking_id, w.position "
          + "FROM booking b "
          + "JOIN exam_session s ON s.session_id = b.session_id "
          + "LEFT JOIN candidate_snapshot c ON c.candidate_id = b.candidate_id "
          + "LEFT JOIN exam_snapshot e ON e.exam_id = s.exam_id "
          + "LEFT JOIN waitlist_entry w ON w.booking_id = b.booking_id ";

    private final JdbcClient jdbc;

    public BookingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BookingView> find(long bookingId) {
        return jdbc.sql(BOOKING_VIEW + "WHERE b.booking_id = ?").param(bookingId).query(BookingRepository::mapView).optional();
    }

    public List<BookingView> byCandidate(int candidateId) {
        return jdbc.sql(BOOKING_VIEW + "WHERE b.candidate_id = ? ORDER BY b.booked_at DESC, b.booking_id DESC")
                .param(candidateId).query(BookingRepository::mapView).list();
    }

    /**
     * Scheduling booking id for an id a client holds: a Scheduling id, or an AssessmentDelivery {@code BookingId}
     * (imported bookings keep it, modern bookings get one on first relay and store it in legacy_booking_id),
     * so v1 / legacy URLs keep working.
     */
    public Optional<Long> resolve(long id) {
        return jdbc.sql("SELECT TOP 1 booking_id FROM booking WHERE booking_id = ? OR legacy_booking_id = ? "
                        + "ORDER BY CASE WHEN booking_id = ? THEN 0 ELSE 1 END")
                .params(id, id, id).query(Long.class).optional();
    }

    public void markAttended(long bookingId, LocalDateTime now) {
        jdbc.sql("UPDATE booking SET status = 'ATTENDED', last_modified = ? WHERE booking_id = ?")
                .params(now, bookingId).update();
    }

    /** Write-back of the AssessmentDelivery id assigned by the relay; first assignment wins. */
    public int setLegacyBookingId(long bookingId, int legacyBookingId) {
        return jdbc.sql("UPDATE booking SET legacy_booking_id = ? WHERE booking_id = ? AND legacy_booking_id IS NULL")
                .params(legacyBookingId, bookingId).update();
    }

    public Optional<Integer> sessionIdOf(long bookingId) {
        return jdbc.sql("SELECT session_id FROM booking WHERE booking_id = ?").param(bookingId).query(Integer.class).optional();
    }

    /** Next id from the MODERN range (booking_id_seq starts at 50,000,001). */
    public long nextModernId() {
        return jdbc.sql("SELECT NEXT VALUE FOR booking_id_seq").query(Long.class).single();
    }

    public void insertModern(long bookingId, int sessionId, int candidateId, String status, String accommodationCode,
                             String channel, String externalRef, LocalDateTime now) {
        jdbc.sql("INSERT INTO booking (booking_id, session_id, candidate_id, status, accommodation_code, channel, external_ref, "
                        + "booked_at, origin, legacy_booking_id, last_modified) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'MODERN', NULL, ?)")
                .params(bookingId, sessionId, candidateId, status, accommodationCode, channel, externalRef, now, now)
                .update();
    }

    public void markCancelled(long bookingId, String reason, LocalDateTime now) {
        jdbc.sql("UPDATE booking SET status = 'CANCELLED', cancelled_at = ?, cancel_reason = ?, last_modified = ? WHERE booking_id = ?")
                .params(now, reason, now, bookingId).update();
    }

    public void markWaitlisted(long bookingId, LocalDateTime now) {
        jdbc.sql("UPDATE booking SET status = 'WAITLISTED', last_modified = ? WHERE booking_id = ?")
                .params(now, bookingId).update();
    }

    public void insertWaitlistEntry(int sessionId, long bookingId, int candidateId, int position, LocalDateTime now) {
        jdbc.sql("INSERT INTO waitlist_entry (session_id, booking_id, candidate_id, position, added_at) VALUES (?, ?, ?, ?, ?)")
                .params(sessionId, bookingId, candidateId, position, now).update();
    }

    public void deleteWaitlistEntry(long bookingId) {
        jdbc.sql("DELETE FROM waitlist_entry WHERE booking_id = ?").param(bookingId).update();
    }

    public int candidateIdOf(long bookingId) {
        return jdbc.sql("SELECT candidate_id FROM booking WHERE booking_id = ?").param(bookingId).query(Integer.class).single();
    }

    public List<WaitlistView> waitlist(int sessionId) {
        return jdbc.sql("SELECT w.position, w.booking_id, c.candidate_number, c.first_name, c.last_name, w.added_at, "
                        + "       b.accommodation_code "
                        + "FROM waitlist_entry w JOIN booking b ON b.booking_id = w.booking_id "
                        + "LEFT JOIN candidate_snapshot c ON c.candidate_id = w.candidate_id "
                        + "WHERE w.session_id = ? ORDER BY w.position")
                .param(sessionId)
                .query((rs, n) -> new WaitlistView(rs.getInt(1), rs.getLong(2), rs.getString(3),
                        rs.getString(4) == null ? null : rs.getString(4) + " " + rs.getString(5),
                        rs.getTimestamp(6).toLocalDateTime(), rs.getString(7)))
                .list();
    }

    /** Accepted seat offer (ADR 0003): the waitlisted booking now holds a seat. */
    public void markBooked(long bookingId, LocalDateTime now) {
        jdbc.sql("UPDATE booking SET status = 'BOOKED', last_modified = ? WHERE booking_id = ? AND status = 'WAITLISTED'")
                .params(now, bookingId).update();
    }

    public void updateWaitlistPosition(long bookingId, int position) {
        jdbc.sql("UPDATE waitlist_entry SET position = ? WHERE booking_id = ?").params(position, bookingId).update();
    }

    public Optional<Integer> waitlistEntryIdOf(long bookingId) {
        return jdbc.sql("SELECT waitlist_entry_id FROM waitlist_entry WHERE booking_id = ?").param(bookingId)
                .query(Integer.class).optional();
    }

    public Optional<RouteView> routeOfBooking(long bookingId) {
        return jdbc.sql("SELECT TOP 1 s.center_id, c.center_code, s.session_id, b.booking_id, b.origin FROM booking b "
                        + "JOIN exam_session s ON s.session_id = b.session_id "
                        + "JOIN test_center_snapshot c ON c.center_id = s.center_id "
                        + "WHERE b.booking_id = ? OR b.legacy_booking_id = ? ORDER BY CASE WHEN b.booking_id = ? THEN 0 ELSE 1 END")
                .params(bookingId, bookingId, bookingId)
                .query((rs, n) -> new RouteView(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getLong(4), rs.getString(5)))
                .optional();
    }

    public Optional<RouteView> routeOfSession(int sessionId) {
        return jdbc.sql("SELECT s.center_id, c.center_code, s.session_id FROM exam_session s "
                        + "JOIN test_center_snapshot c ON c.center_id = s.center_id WHERE s.session_id = ?")
                .param(sessionId)
                .query((rs, n) -> new RouteView(rs.getInt(1), rs.getString(2), rs.getInt(3), null, null))
                .optional();
    }

    static BookingView mapView(ResultSet rs, int n) throws SQLException {
        int legacyId = rs.getInt("legacy_booking_id");
        Integer legacy = rs.wasNull() ? null : legacyId;
        int pos = rs.getInt("position");
        Integer position = rs.wasNull() ? null : pos;
        return new BookingView(rs.getLong("booking_id"), rs.getInt("session_id"), rs.getInt("candidate_id"),
                rs.getString("candidate_number"), rs.getString("exam_code"), ldt(rs.getTimestamp("starts_at")),
                rs.getString("status"), rs.getString("accommodation_code"), rs.getString("channel"),
                rs.getString("external_ref"), ldt(rs.getTimestamp("booked_at")), ldt(rs.getTimestamp("cancelled_at")),
                rs.getString("cancel_reason"), rs.getString("origin"), legacy, position);
    }

    private static LocalDateTime ldt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
