package com.northwind.scheduling.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.northwind.scheduling.domain.BookingStatus;
import com.northwind.scheduling.domain.CapacityChange;
import com.northwind.scheduling.domain.ExamAttempt;
import com.northwind.scheduling.domain.SessionStatus;

/**
 * All SQL against the Scheduling DB. Seat counts are computed from booking rows on read
 * (no SeatsAvailable counter, ADR 0002 ODD-01/ODD-13); writers serialise per session with
 * {@link #lockSession(int)}.
 */
@Repository
public class SchedulingRepository {

    private static final String SESSION_SELECT = """
            SELECT s.session_id, s.exam_id, e.exam_code, e.title AS exam_title, s.test_center_id, c.center_code,
                   c.name AS center_name, r.room_name, s.starts_at, s.capacity, s.accommodation_capacity, s.status,
                   ISNULL(x.occupied, 0) AS occupied, ISNULL(x.acc_used, 0) AS acc_used, ISNULL(x.waitlisted, 0) AS waitlisted
              FROM exam_session s
              JOIN exam_snapshot e ON e.exam_id = s.exam_id
              JOIN test_center_snapshot c ON c.test_center_id = s.test_center_id
              JOIN room_snapshot r ON r.room_id = s.room_id
             OUTER APPLY (SELECT SUM(CASE WHEN b.status IN ('BOOKED','ATTENDED') THEN 1 ELSE 0 END) AS occupied,
                                 SUM(CASE WHEN b.status IN ('BOOKED','ATTENDED') AND b.accommodation_code IN ('ET25','ET50')
                                          THEN 1 ELSE 0 END) AS acc_used,
                                 SUM(CASE WHEN b.status = 'WAITLISTED' THEN 1 ELSE 0 END) AS waitlisted
                            FROM booking b WHERE b.session_id = s.session_id) x
            """;

    private static final String BOOKING_SELECT = """
            SELECT b.booking_id, b.session_id, b.candidate_id, c.candidate_number,
                   c.first_name + N' ' + c.last_name AS candidate_name, s.exam_id, e.exam_code, e.title AS exam_title,
                   t.center_code, t.name AS center_name, s.starts_at, b.status, b.accommodation_code, b.channel,
                   b.external_ref, b.booked_at, b.cancelled_at, b.cancel_reason, b.rescheduled_from, w.position
              FROM booking b
              JOIN candidate_snapshot c ON c.candidate_id = b.candidate_id
              JOIN exam_session s ON s.session_id = b.session_id
              JOIN exam_snapshot e ON e.exam_id = s.exam_id
              JOIN test_center_snapshot t ON t.test_center_id = s.test_center_id
              LEFT JOIN waitlist_entry w ON w.booking_id = b.booking_id
            """;

    private final JdbcClient db;

    public SchedulingRepository(JdbcClient db) {
        this.db = db;
    }

    static LocalDateTime ldt(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    static Integer intOrNull(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    static Rows.Session session(ResultSet rs, int i) throws SQLException {
        return new Rows.Session(rs.getInt("session_id"), rs.getInt("exam_id"), rs.getString("exam_code"),
                rs.getString("exam_title"), rs.getInt("test_center_id"), rs.getString("center_code"),
                rs.getString("center_name"), rs.getString("room_name"), ldt(rs, "starts_at"), rs.getInt("capacity"),
                rs.getInt("accommodation_capacity"), SessionStatus.valueOf(rs.getString("status")), rs.getInt("occupied"),
                rs.getInt("acc_used"), rs.getInt("waitlisted"));
    }

    static Rows.Booking booking(ResultSet rs, int i) throws SQLException {
        return new Rows.Booking(rs.getInt("booking_id"), rs.getInt("session_id"), rs.getInt("candidate_id"),
                rs.getString("candidate_number"), rs.getString("candidate_name"), rs.getInt("exam_id"),
                rs.getString("exam_code"), rs.getString("exam_title"), rs.getString("center_code"),
                rs.getString("center_name"), ldt(rs, "starts_at"), BookingStatus.valueOf(rs.getString("status")),
                rs.getString("accommodation_code"), rs.getString("channel"), rs.getString("external_ref"),
                ldt(rs, "booked_at"), ldt(rs, "cancelled_at"), rs.getString("cancel_reason"),
                intOrNull(rs, "rescheduled_from"), intOrNull(rs, "position"));
    }

    // ------------------------------------------------------------------ reference data

    public List<Rows.Center> centers() {
        return db.sql("SELECT test_center_id, center_code, name, city, region, time_zone FROM test_center_snapshot "
                + "WHERE is_active = 1 ORDER BY center_code")
                .query((rs, i) -> new Rows.Center(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6))).list();
    }

    public Optional<Rows.Center> center(String code) {
        return db.sql("SELECT test_center_id, center_code, name, city, region, time_zone FROM test_center_snapshot "
                + "WHERE center_code = ?").param(code)
                .query((rs, i) -> new Rows.Center(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6))).optional();
    }

    public List<Rows.Exam> exams() {
        return db.sql("SELECT exam_id, exam_code, title, duration_minutes, retake_wait_days FROM exam_snapshot "
                + "WHERE is_published = 1 ORDER BY exam_code")
                .query((rs, i) -> new Rows.Exam(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5)))
                .list();
    }

    public Optional<Rows.Exam> exam(String code) {
        return db.sql("SELECT exam_id, exam_code, title, duration_minutes, retake_wait_days FROM exam_snapshot "
                + "WHERE exam_code = ?").param(code)
                .query((rs, i) -> new Rows.Exam(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5)))
                .optional();
    }

    public Optional<Rows.Candidate> candidate(String number) {
        return db.sql("SELECT candidate_id, candidate_number, first_name, last_name, email, default_accommodation_code "
                + "FROM candidate_snapshot WHERE candidate_number = ?").param(number)
                .query((rs, i) -> new Rows.Candidate(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6))).optional();
    }

    /** Scored attempts, latest first (booking-api ResultDao.findAttempts ordering). */
    public List<ExamAttempt> attempts(int candidateId, int examId) {
        return db.sql("SELECT session_starts_at, passed FROM exam_result_snapshot WHERE candidate_id = ? AND exam_id = ? "
                + "ORDER BY session_starts_at DESC, result_id DESC").params(candidateId, examId)
                .query((rs, i) -> new ExamAttempt(rs.getTimestamp(1).toLocalDateTime().toLocalDate(), rs.getBoolean(2)))
                .list();
    }

    // ------------------------------------------------------------------ sessions

    /** Filters like booking-api SessionDao.find: {@code to} is inclusive of the whole day, max 500 rows. */
    public List<Rows.Session> sessions(String centerCode, String examCode, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT TOP 500 * FROM (" + SESSION_SELECT + ") q WHERE 1=1");
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (centerCode != null && !centerCode.isBlank()) {
            sql.append(" AND q.center_code = ?");
            args.add(centerCode);
        }
        if (examCode != null && !examCode.isBlank()) {
            sql.append(" AND q.exam_code = ?");
            args.add(examCode);
        }
        if (from != null) {
            sql.append(" AND q.starts_at >= ?");
            args.add(Timestamp.valueOf(from.atStartOfDay()));
        }
        if (to != null) {
            sql.append(" AND q.starts_at < ?");
            args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
        }
        sql.append(" ORDER BY q.starts_at, q.session_id");
        return db.sql(sql.toString()).params(args).query(SchedulingRepository::session).list();
    }

    public Optional<Rows.Session> session(int sessionId) {
        return db.sql(SESSION_SELECT + " WHERE s.session_id = ?").param(sessionId).query(SchedulingRepository::session)
                .optional();
    }

    /** Takes an update lock on the session row for the rest of the transaction; false when it does not exist. */
    public boolean lockSession(int sessionId) {
        return db.sql("SELECT session_id FROM exam_session WITH (UPDLOCK, HOLDLOCK) WHERE session_id = ?")
                .param(sessionId).query(Integer.class).optional().isPresent();
    }

    public Optional<Integer> sessionExamId(int sessionId) {
        return db.sql("SELECT exam_id FROM exam_session WHERE session_id = ?").param(sessionId).query(Integer.class)
                .optional();
    }

    public Optional<String> sessionCenterCode(int sessionId) {
        return db.sql("SELECT c.center_code FROM exam_session s JOIN test_center_snapshot c ON c.test_center_id = s.test_center_id "
                + "WHERE s.session_id = ?").param(sessionId).query(String.class).optional();
    }

    public void updateCapacity(int sessionId, int capacity, int accommodationCapacity, LocalDateTime now) {
        db.sql("UPDATE exam_session SET capacity = ?, accommodation_capacity = ?, last_modified = ? WHERE session_id = ?")
                .params(capacity, accommodationCapacity, Timestamp.valueOf(now), sessionId).update();
    }

    public List<CapacityChange.BookedSeat> bookedSeats(int sessionId) {
        return db.sql("SELECT booking_id, booked_at FROM booking WHERE session_id = ? AND status = 'BOOKED'")
                .param(sessionId)
                .query((rs, i) -> new CapacityChange.BookedSeat(rs.getInt(1), rs.getTimestamp(2).toLocalDateTime()))
                .list();
    }

    public int insertCapacityChange(CapacityChange.Plan plan, String reason, String actor, LocalDateTime now) {
        return db.sql("INSERT INTO capacity_change (session_id, old_capacity, new_capacity, old_accommodation_capacity, "
                + "new_accommodation_capacity, demoted_bookings, reason, changed_by, changed_at) "
                + "OUTPUT INSERTED.capacity_change_id VALUES (?,?,?,?,?,?,?,?,?)")
                .params(plan.sessionId(), plan.oldCapacity(), plan.newCapacity(), plan.oldAccommodationCapacity(),
                        plan.newAccommodationCapacity(), plan.demotions().size(), reason, actor, Timestamp.valueOf(now))
                .query(Integer.class).single();
    }

    public List<Rows.CapacityChangeRow> capacityChanges(int sessionId) {
        return db.sql("SELECT capacity_change_id, session_id, old_capacity, new_capacity, demoted_bookings, reason, "
                + "changed_by, changed_at FROM capacity_change WHERE session_id = ? ORDER BY changed_at DESC, capacity_change_id DESC")
                .param(sessionId)
                .query((rs, i) -> new Rows.CapacityChangeRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getString(6), rs.getString(7), ldt(rs, "changed_at"))).list();
    }

    // ------------------------------------------------------------------ bookings

    public Optional<Rows.Booking> booking(int bookingId) {
        return db.sql(BOOKING_SELECT + " WHERE b.booking_id = ?").param(bookingId).query(SchedulingRepository::booking)
                .optional();
    }

    /** Latest first, like booking-api BookingDao.findByCandidate. */
    public List<Rows.Booking> bookingsForCandidate(int candidateId) {
        return db.sql(BOOKING_SELECT + " WHERE b.candidate_id = ? ORDER BY b.booked_at DESC, b.booking_id DESC")
                .param(candidateId).query(SchedulingRepository::booking).list();
    }

    public boolean hasActiveBooking(int sessionId, int candidateId) {
        return db.sql("SELECT COUNT(*) FROM booking WHERE session_id = ? AND candidate_id = ? AND status IN ('BOOKED','WAITLISTED')")
                .params(sessionId, candidateId).query(Integer.class).single() > 0;
    }

    public int insertBooking(int sessionId, int candidateId, BookingStatus status, String accommodationCode,
                             String channel, String externalRef, LocalDateTime now, Integer rescheduledFrom) {
        return db.sql("INSERT INTO booking (session_id, candidate_id, status, accommodation_code, channel, external_ref, "
                + "booked_at, rescheduled_from, last_modified) OUTPUT INSERTED.booking_id VALUES (?,?,?,?,?,?,?,?,?)")
                .params(sessionId, candidateId, status.name(), accommodationCode, channel, externalRef,
                        Timestamp.valueOf(now), rescheduledFrom, Timestamp.valueOf(now))
                .query(Integer.class).single();
    }

    public void markCancelled(int bookingId, String reason, LocalDateTime now) {
        db.sql("UPDATE booking SET status = 'CANCELLED', cancelled_at = ?, cancel_reason = ?, last_modified = ? "
                + "WHERE booking_id = ?").params(Timestamp.valueOf(now), reason, Timestamp.valueOf(now), bookingId).update();
    }

    public void markAttended(int bookingId, LocalDateTime now) {
        db.sql("UPDATE booking SET status = 'ATTENDED', last_modified = ? WHERE booking_id = ? AND status = 'BOOKED'")
                .params(Timestamp.valueOf(now), bookingId).update();
    }

    public void markWaitlisted(int bookingId, LocalDateTime now) {
        db.sql("UPDATE booking SET status = 'WAITLISTED', last_modified = ? WHERE booking_id = ?")
                .params(Timestamp.valueOf(now), bookingId).update();
    }

    // ------------------------------------------------------------------ waitlist

    public Integer maxWaitlistPosition(int sessionId) {
        return db.sql("SELECT MAX(position) FROM waitlist_entry WHERE session_id = ?").param(sessionId)
                .query(Integer.class).optional().orElse(null);
    }

    public void insertWaitlist(int bookingId, int position, LocalDateTime now) {
        db.sql("INSERT INTO waitlist_entry (session_id, candidate_id, booking_id, position, added_at) "
                + "SELECT session_id, candidate_id, booking_id, ?, ? FROM booking WHERE booking_id = ?")
                .params(position, Timestamp.valueOf(now), bookingId).update();
    }

    public void deleteWaitlistEntry(int bookingId) {
        db.sql("DELETE FROM waitlist_entry WHERE booking_id = ?").param(bookingId).update();
    }

    /** Only WAITLISTED bookings are listed (a cancelled one has had its row removed - usp_CancelBooking). */
    public List<Rows.WaitlistEntry> waitlist(int sessionId) {
        return db.sql("""
                SELECT w.position, w.booking_id, c.candidate_number, c.first_name + N' ' + c.last_name, b.accommodation_code, w.added_at
                  FROM waitlist_entry w
                  JOIN booking b ON b.booking_id = w.booking_id
                  JOIN candidate_snapshot c ON c.candidate_id = w.candidate_id
                 WHERE w.session_id = ? AND b.status = 'WAITLISTED'
                 ORDER BY w.position, w.waitlist_entry_id""").param(sessionId)
                .query((rs, i) -> new Rows.WaitlistEntry(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), ldt(rs, "added_at"))).list();
    }
}
