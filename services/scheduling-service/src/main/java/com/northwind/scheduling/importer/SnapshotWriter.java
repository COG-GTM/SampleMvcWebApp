package com.northwind.scheduling.importer;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.northwind.scheduling.domain.AccommodationCodes;
import com.northwind.scheduling.legacy.LegacyRows;

/** Writes snapshots and the initial seat inventory into the Scheduling DB (the service's own datasource). */
@Component
public class SnapshotWriter {

    private final JdbcTemplate jdbc;

    @Value("${scheduling.booking-id-floor:50000000}")
    private long bookingIdFloor;

    public SnapshotWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static Timestamp t(LocalDateTime v) {
        return v == null ? null : Timestamp.valueOf(v);
    }

    public int upsertCenters(List<LegacyRows.Center> rows, LocalDateTime now) {
        jdbc.batchUpdate("MERGE test_center_snapshot AS t USING (SELECT ? AS id) AS s ON t.test_center_id = s.id "
                + "WHEN MATCHED THEN UPDATE SET center_code=?, name=?, city=?, region=?, time_zone=?, is_active=?, source_version=?, synced_at=? "
                + "WHEN NOT MATCHED THEN INSERT (test_center_id, center_code, name, city, region, time_zone, is_active, source_version, synced_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?);", rows, 200, (ps, r) -> {
                    Object[] v = {r.code(), r.name(), r.city(), r.region(), r.timeZone(), r.active(), r.sourceVersion(), t(now)};
                    ps.setInt(1, r.id());
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(2 + i, v[i]);
                    }
                    ps.setInt(10, r.id());
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(11 + i, v[i]);
                    }
                });
        return rows.size();
    }

    public int upsertRooms(List<LegacyRows.Room> rows, LocalDateTime now) {
        jdbc.batchUpdate("MERGE room_snapshot AS t USING (SELECT ? AS id) AS s ON t.room_id = s.id "
                + "WHEN MATCHED THEN UPDATE SET test_center_id=?, room_name=?, seat_count=?, accommodation_seats=?, is_active=?, source_version=?, synced_at=? "
                + "WHEN NOT MATCHED THEN INSERT (room_id, test_center_id, room_name, seat_count, accommodation_seats, is_active, source_version, synced_at) "
                + "VALUES (?,?,?,?,?,?,?,?);", rows, 200, (ps, r) -> {
                    Object[] v = {r.centerId(), r.name(), r.seatCount(), r.accommodationSeats(), r.active(), r.sourceVersion(), t(now)};
                    ps.setInt(1, r.id());
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(2 + i, v[i]);
                    }
                    ps.setInt(9, r.id());
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(10 + i, v[i]);
                    }
                });
        return rows.size();
    }

    public int upsertExams(List<LegacyRows.Exam> rows, LocalDateTime now) {
        jdbc.batchUpdate("MERGE exam_snapshot AS t USING (SELECT ? AS id) AS s ON t.exam_id = s.id "
                + "WHEN MATCHED THEN UPDATE SET exam_code=?, title=?, owner_name=?, duration_minutes=?, passing_score=?, retake_wait_days=?, is_published=?, source_version=?, synced_at=? "
                + "WHEN NOT MATCHED THEN INSERT (exam_id, exam_code, title, owner_name, duration_minutes, passing_score, retake_wait_days, is_published, source_version, synced_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?);", rows, 200, (ps, r) -> {
                    Object[] v = {r.code(), r.title(), r.owner(), r.durationMinutes(), r.passingScore(), r.retakeWaitDays(),
                        r.published(), r.sourceVersion(), t(now)};
                    ps.setInt(1, r.id());
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(2 + i, v[i]);
                    }
                    ps.setInt(11, r.id());
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(12 + i, v[i]);
                    }
                });
        return rows.size();
    }

    public int upsertCandidates(List<LegacyRows.Candidate> rows, LocalDateTime now) {
        jdbc.batchUpdate("MERGE candidate_snapshot AS t USING (SELECT ? AS id) AS s ON t.candidate_id = s.id "
                + "WHEN MATCHED THEN UPDATE SET candidate_number=?, first_name=?, last_name=?, email=?, default_accommodation_code=?, source_version=?, synced_at=? "
                + "WHEN NOT MATCHED THEN INSERT (candidate_id, candidate_number, first_name, last_name, email, default_accommodation_code, source_version, synced_at) "
                + "VALUES (?,?,?,?,?,?,?,?);", rows, 500, (ps, r) -> {
                    Object[] v = {r.number(), r.firstName(), r.lastName(), r.email(), safeCode(r.defaultAccommodationCode()),
                        r.sourceVersion(), t(now)};
                    ps.setInt(1, r.id());
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(2 + i, v[i]);
                    }
                    ps.setInt(9, r.id());
                    for (int i = 0; i < v.length; i++) {
                        ps.setObject(10 + i, v[i]);
                    }
                });
        return rows.size();
    }

    /** Result history is replaced wholesale (it is only read for eligibility). */
    public int replaceResults(List<LegacyRows.Result> rows, LocalDateTime now) {
        jdbc.update("DELETE FROM exam_result_snapshot");
        jdbc.batchUpdate("INSERT INTO exam_result_snapshot (result_id, candidate_id, exam_id, session_id, session_starts_at, "
                + "score, passed, scored_at, source_version, synced_at) VALUES (?,?,?,?,?,?,?,?,?,?)", rows, 500, (ps, r) -> {
                    ps.setInt(1, r.id());
                    ps.setInt(2, r.candidateId());
                    ps.setInt(3, r.examId());
                    ps.setInt(4, r.sessionId());
                    ps.setTimestamp(5, t(r.sessionStartsAt()));
                    ps.setInt(6, r.score());
                    ps.setBoolean(7, r.passed());
                    ps.setTimestamp(8, t(r.scoredAt()));
                    ps.setLong(9, r.sourceVersion());
                    ps.setTimestamp(10, t(now));
                });
        return rows.size();
    }

    public int sessionCount() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM exam_session", Integer.class);
        return n == null ? 0 : n;
    }

    public Set<Integer> existingSessionIds() {
        return jdbc.queryForList("SELECT session_id FROM exam_session", Integer.class).stream().collect(Collectors.toSet());
    }

    /** Wipes the seat inventory (sessions, bookings, waitlist, capacity history). Snapshots are kept. */
    public void clearInventory() {
        jdbc.update("DELETE FROM capacity_change");
        jdbc.update("DELETE FROM waitlist_entry");
        jdbc.update("UPDATE booking SET rescheduled_from = NULL WHERE rescheduled_from IS NOT NULL");
        jdbc.update("DELETE FROM booking");
        jdbc.update("DELETE FROM exam_session");
        for (String table : List.of("waitlist_entry", "booking", "exam_session")) {
            jdbc.execute("DBCC CHECKIDENT ('" + table + "', RESEED, 0) WITH NO_INFOMSGS");
        }
    }

    /**
     * Raises the booking identity to {@code scheduling.booking-id-floor} (default 50000000) so bookings created
     * here never collide with legacy BookingIds; the M3 routing-gateway routes ids at or above the floor to this
     * service regardless of its allow-list (ownership by origin). Only ever raises the seed.
     */
    public void ensureBookingIdFloor() {
        if (bookingIdFloor <= 0) {
            return;
        }
        Long current = jdbc.queryForObject("SELECT CAST(IDENT_CURRENT('booking') AS BIGINT)", Long.class);
        if (current == null || current < bookingIdFloor) {
            jdbc.execute("DBCC CHECKIDENT ('booking', RESEED, " + bookingIdFloor + ") WITH NO_INFOMSGS");
        }
    }

    /** Inserts sessions keeping the legacy SessionId (so M1 transcripts and partner ids stay valid). */
    public int insertSessions(List<LegacyRows.Session> rows, LocalDateTime now) {
        if (rows.isEmpty()) {
            return 0;
        }
        jdbc.execute("SET IDENTITY_INSERT exam_session ON");
        try {
            jdbc.batchUpdate("INSERT INTO exam_session (session_id, exam_id, test_center_id, room_id, starts_at, capacity, "
                    + "accommodation_capacity, status, imported_at, last_modified) VALUES (?,?,?,?,?,?,?,?,?,?)", rows, 500, (ps, r) -> {
                        ps.setInt(1, r.id());
                        ps.setInt(2, r.examId());
                        ps.setInt(3, r.centerId());
                        ps.setInt(4, r.roomId());
                        ps.setTimestamp(5, t(r.startsAt()));
                        ps.setInt(6, r.capacity());
                        ps.setInt(7, r.accommodationCapacity());
                        ps.setString(8, r.status());
                        ps.setTimestamp(9, t(now));
                        ps.setTimestamp(10, t(now));
                    });
        } finally {
            jdbc.execute("SET IDENTITY_INSERT exam_session OFF");
        }
        return rows.size();
    }

    public int insertBookings(List<LegacyRows.Booking> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        jdbc.execute("SET IDENTITY_INSERT booking ON");
        try {
            jdbc.batchUpdate("INSERT INTO booking (booking_id, session_id, candidate_id, status, accommodation_code, channel, "
                    + "external_ref, booked_at, cancelled_at, cancel_reason, last_modified) VALUES (?,?,?,?,?,?,?,?,?,?,?)", rows, 1000,
                    (ps, r) -> {
                        ps.setInt(1, r.id());
                        ps.setInt(2, r.sessionId());
                        ps.setInt(3, r.candidateId());
                        ps.setString(4, r.status());
                        ps.setString(5, safeCode(r.accommodationCode()));
                        ps.setString(6, r.channel());
                        ps.setString(7, r.externalRef());
                        ps.setTimestamp(8, t(r.bookedAt()));
                        ps.setTimestamp(9, t(r.cancelledAt()));
                        ps.setString(10, r.cancelReason());
                        ps.setTimestamp(11, t(r.cancelledAt() != null ? r.cancelledAt() : r.bookedAt()));
                    });
        } finally {
            jdbc.execute("SET IDENTITY_INSERT booking OFF");
        }
        return rows.size();
    }

    public int insertWaitlist(List<LegacyRows.Waitlist> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        jdbc.execute("SET IDENTITY_INSERT waitlist_entry ON");
        try {
            jdbc.batchUpdate("INSERT INTO waitlist_entry (waitlist_entry_id, session_id, candidate_id, booking_id, position, added_at) "
                    + "VALUES (?,?,?,?,?,?)", rows, 500, (ps, r) -> {
                        ps.setInt(1, r.id());
                        ps.setInt(2, r.sessionId());
                        ps.setInt(3, r.candidateId());
                        ps.setInt(4, r.bookingId());
                        ps.setInt(5, r.position());
                        ps.setTimestamp(6, t(r.addedAt()));
                    });
        } finally {
            jdbc.execute("SET IDENTITY_INSERT waitlist_entry OFF");
        }
        return rows.size();
    }

    public void recordRun(ImportReport r) {
        jdbc.update("INSERT INTO snapshot_import_run (mode, centers, rooms, exams, candidates, results, sessions, bookings, "
                + "waitlist, started_at, finished_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)", r.mode(), r.centers(), r.rooms(),
                r.exams(), r.candidates(), r.results(), r.sessions(), r.bookings(), r.waitlist(), t(r.startedAt()),
                t(r.finishedAt()));
    }

    /** Legacy columns are free text; anything outside ET25/ET50/SR/RB is imported as "no accommodation". */
    static String safeCode(String code) {
        try {
            return AccommodationCodes.normalize(code);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
