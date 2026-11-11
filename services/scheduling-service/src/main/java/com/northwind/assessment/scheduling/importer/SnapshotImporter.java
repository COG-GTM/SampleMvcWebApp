package com.northwind.assessment.scheduling.importer;

import com.northwind.assessment.scheduling.domain.BookingOrigin;
import com.northwind.assessment.scheduling.legacy.LegacyReferenceSource;
import com.northwind.assessment.scheduling.legacy.LegacyRows;
import com.northwind.assessment.scheduling.service.NotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Copies data owned by AssessmentDelivery into the Scheduling database.
 *
 * <ol>
 *   <li>Reference snapshots (TestCenter, Room, Exam, Candidate, Result): always refreshed.</li>
 *   <li>Bootstrap rows (ExamSession, Booking, Waitlist): upserted by legacy id, keeping legacy ids.
 *       A session scheduling-service has already written to ({@code locally_modified = 1}) is owned by
 *       scheduling-service and skipped together with its bookings and waitlist, so re-importing never
 *       undoes a modern booking, cancellation or capacity change.</li>
 * </ol>
 * Each session is imported in its own short transaction under the same row lock the booking path uses.
 */
@Service
public class SnapshotImporter {

    private static final Logger log = LoggerFactory.getLogger(SnapshotImporter.class);

    private static final String[] CENTER_COLS = {"center_id", "center_code", "name", "city", "region", "time_zone",
        "is_active", "source_version", "synced_at"};
    private static final String[] ROOM_COLS = {"room_id", "center_id", "room_name", "seat_count", "accommodation_seats",
        "is_active", "source_version", "synced_at"};
    private static final String[] EXAM_COLS = {"exam_id", "exam_code", "title", "duration_minutes", "passing_score",
        "retake_wait_days", "is_published", "source_version", "synced_at"};
    private static final String[] CANDIDATE_COLS = {"candidate_id", "candidate_number", "first_name", "last_name", "email",
        "default_accommodation_code", "source_version", "synced_at"};
    private static final String[] RESULT_COLS = {"result_id", "candidate_id", "exam_id", "session_id", "session_starts_at",
        "score", "passed", "source_version", "synced_at"};

    private static final String MERGE_SESSION =
            "MERGE exam_session WITH (HOLDLOCK) AS t "
          + "USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS s (session_id, exam_id, center_id, room_id, starts_at, capacity, "
          + "       accommodation_capacity, status, legacy_last_modified, now) ON t.session_id = s.session_id "
          + "WHEN MATCHED AND t.locally_modified = 0 THEN UPDATE SET t.exam_id = s.exam_id, t.center_id = s.center_id, "
          + "       t.room_id = s.room_id, t.starts_at = s.starts_at, t.capacity = s.capacity, "
          + "       t.accommodation_capacity = s.accommodation_capacity, t.status = s.status, "
          + "       t.legacy_last_modified = s.legacy_last_modified, t.imported_at = s.now, t.last_modified = s.now "
          + "WHEN NOT MATCHED THEN INSERT (session_id, exam_id, center_id, room_id, starts_at, capacity, accommodation_capacity, "
          + "       status, locally_modified, legacy_last_modified, imported_at, last_modified) "
          + "       VALUES (s.session_id, s.exam_id, s.center_id, s.room_id, s.starts_at, s.capacity, s.accommodation_capacity, "
          + "       s.status, 0, s.legacy_last_modified, s.now, s.now);";

    private static final String MERGE_BOOKING =
            "MERGE booking WITH (HOLDLOCK) AS t "
          + "USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS s (booking_id, session_id, candidate_id, status, accommodation_code, "
          + "       channel, external_ref, booked_at, cancelled_at, cancel_reason, last_modified) ON t.booking_id = s.booking_id "
          + "WHEN MATCHED AND t.origin = 'LEGACY_IMPORT' THEN UPDATE SET t.session_id = s.session_id, t.candidate_id = s.candidate_id, "
          + "       t.status = s.status, t.accommodation_code = s.accommodation_code, t.channel = s.channel, "
          + "       t.external_ref = s.external_ref, t.booked_at = s.booked_at, t.cancelled_at = s.cancelled_at, "
          + "       t.cancel_reason = s.cancel_reason, t.last_modified = s.last_modified "
          + "WHEN NOT MATCHED THEN INSERT (booking_id, session_id, candidate_id, status, accommodation_code, channel, external_ref, "
          + "       booked_at, cancelled_at, cancel_reason, origin, legacy_booking_id, last_modified) "
          + "       VALUES (s.booking_id, s.session_id, s.candidate_id, s.status, s.accommodation_code, s.channel, s.external_ref, "
          + "       s.booked_at, s.cancelled_at, s.cancel_reason, 'LEGACY_IMPORT', s.booking_id, s.last_modified);";

    private final LegacyReferenceSource source;
    private final SnapshotWriter writer;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SnapshotImporter(LegacyReferenceSource source, SnapshotWriter writer, JdbcTemplate jdbc, TransactionTemplate tx,
                            Clock clock) {
        this.source = source;
        this.writer = writer;
        this.jdbc = jdbc;
        this.tx = tx;
        this.clock = clock;
    }

    /** Refreshes reference snapshots, then imports sessions/bookings/waitlist of one center (or all when null). */
    public ImportSummary importSessions(String centerCode) {
        long started = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now(clock);
        List<LegacyRows.TestCenter> centers = source.testCenters();
        Integer centerId = null;
        if (centerCode != null && !centerCode.isBlank()) {
            centerId = centers.stream().filter(c -> c.centerCode().equalsIgnoreCase(centerCode.trim()))
                    .map(LegacyRows.TestCenter::testCenterId).findFirst()
                    .orElseThrow(() -> new NotFoundException("CENTER_NOT_FOUND", "Test center " + centerCode + " not found"));
        }
        int nCenters = writer.upsertSnapshot("test_center_snapshot", CENTER_COLS, centers.stream().map(c -> snap(now,
                c.testCenterId(), c.centerCode(), c.name(), c.city(), c.region(), c.timeZone(), c.active())).toList());
        int nRooms = writer.upsertSnapshot("room_snapshot", ROOM_COLS, source.rooms().stream().map(r -> snap(now,
                r.roomId(), r.testCenterId(), r.roomName(), r.seatCount(), r.accommodationSeats(), r.active())).toList());
        int nExams = writer.upsertSnapshot("exam_snapshot", EXAM_COLS, source.exams().stream().map(e -> snap(now,
                e.examId(), e.examCode(), e.title(), e.durationMinutes(), e.passingScore(), e.retakeWaitDays(),
                e.published())).toList());
        int nCandidates = writer.upsertSnapshot("candidate_snapshot", CANDIDATE_COLS, source.candidates().stream().map(c ->
                snap(now, c.candidateId(), c.candidateNumber(), c.firstName(), c.lastName(), c.email(),
                        c.defaultAccommodationCode())).toList());
        int nResults = writer.upsertSnapshot("exam_result_snapshot", RESULT_COLS, source.results().stream().map(r ->
                snap(now, r.resultId(), r.candidateId(), r.examId(), r.sessionId(), r.sessionStartsAt(), r.score(),
                        r.passed())).toList());

        List<LegacyRows.ExamSession> sessions = source.sessions(centerId);
        Map<Integer, List<LegacyRows.Booking>> bookings = source.bookings(centerId).stream()
                .filter(b -> b.bookingId() < BookingOrigin.MODERN_ID_FLOOR)
                .collect(Collectors.groupingBy(LegacyRows.Booking::sessionId));
        Map<Integer, List<LegacyRows.Waitlist>> waitlist = source.waitlist(centerId).stream()
                .collect(Collectors.groupingBy(LegacyRows.Waitlist::sessionId));

        int imported = 0;
        int skipped = 0;
        int nBookings = 0;
        int nWaitlist = 0;
        for (LegacyRows.ExamSession s : sessions) {
            List<LegacyRows.Booking> sb = bookings.getOrDefault(s.sessionId(), List.of());
            List<LegacyRows.Waitlist> sw = waitlist.getOrDefault(s.sessionId(), List.of());
            Boolean done = tx.execute(status -> importSession(s, sb, sw, now));
            if (Boolean.TRUE.equals(done)) {
                imported++;
                nBookings += sb.size();
                nWaitlist += sw.size();
            } else {
                skipped++;
            }
        }
        ImportSummary summary = new ImportSummary(centerCode, nCenters, nRooms, nExams, nCandidates, nResults, imported,
                skipped, nBookings, nWaitlist, System.currentTimeMillis() - started);
        log.info("Import finished: {}", summary);
        return summary;
    }

    private boolean importSession(LegacyRows.ExamSession s, List<LegacyRows.Booking> bookings,
                                  List<LegacyRows.Waitlist> waitlist, LocalDateTime now) {
        List<Boolean> owned = jdbc.queryForList(
                "SELECT locally_modified FROM exam_session WITH (UPDLOCK, ROWLOCK, HOLDLOCK) WHERE session_id = ?",
                Boolean.class, s.sessionId());
        if (!owned.isEmpty() && Boolean.TRUE.equals(owned.get(0))) {
            return false;
        }
        jdbc.update(MERGE_SESSION, s.sessionId(), s.examId(), s.testCenterId(), s.roomId(), s.startsAt(), s.capacity(),
                s.accommodationCapacity(), s.status(), s.lastModified(), now);
        List<Object[]> rows = new ArrayList<>();
        for (LegacyRows.Booking b : bookings) {
            rows.add(new Object[] {b.bookingId(), b.sessionId(), b.candidateId(), b.status(), b.accommodationCode(),
                b.channel(), b.externalRef(), b.bookedAt(), b.cancelledAt(), b.cancelReason(),
                b.lastModified() == null ? now : b.lastModified()});
        }
        writer.batch(MERGE_BOOKING, rows);
        jdbc.update("DELETE FROM waitlist_entry WHERE session_id = ?", s.sessionId());
        List<Object[]> wl = new ArrayList<>();
        for (LegacyRows.Waitlist w : waitlist) {
            wl.add(new Object[] {w.sessionId(), w.bookingId(), w.candidateId(), w.position(), w.addedAt(), w.waitlistId()});
        }
        writer.batch("INSERT INTO waitlist_entry (session_id, booking_id, candidate_id, position, added_at, legacy_waitlist_id) "
                + "VALUES (?, ?, ?, ?, ?, ?)", wl);
        return true;
    }

    private static Object[] snap(LocalDateTime now, Object... values) {
        Object[] row = new Object[values.length + 2];
        System.arraycopy(values, 0, row, 0, values.length);
        row[values.length] = SnapshotWriter.sourceVersion(values);
        row[values.length + 1] = now;
        return row;
    }
}
