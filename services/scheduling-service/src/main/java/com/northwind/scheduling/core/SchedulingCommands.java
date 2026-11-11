package com.northwind.scheduling.core;

import com.northwind.scheduling.api.ApiException;
import com.northwind.scheduling.reference.ReferenceData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The four legacy procs as transactional service methods, plus attendance and session creation.
 *
 * Locking mirrors the procs: the session row (book, capacity) or the booking row (cancel) is read
 * WITH (UPDLOCK, HOLDLOCK) inside a READ COMMITTED transaction, so the last seat, waitlist positions and
 * capacity cuts serialise exactly as they did in AssessmentDelivery. Rejections carry the proc's RAISERROR
 * state and message text (message 50000, HB-14).
 *
 * Lock order for every command: ExamSession rows first (ascending SessionId when two are involved), then the
 * Booking row. That removes the crossing-reschedule deadlock (HB-15/MR-13) and cancel/attend-vs-book cycles that
 * the procs' order allowed; {@link #inTx} retries a 1205 victim as a backstop.
 */
@Service
public class SchedulingCommands {

    public record BookOutcome(int bookingId, String status, Integer waitlistPosition, Integer cancelledBookingId) {
    }

    public record CapacityOutcome(int sessionId, int oldCapacity, int newCapacity, int seatsAvailable,
                                  List<Integer> demotedBookingIds) {
    }

    public record AttendOutcome(int bookingId, boolean transitioned) {
    }

    public record NewSession(int examId, int testCenterId, int roomId, LocalDateTime startsAt, int capacity,
                             Integer accommodationCapacity, Integer seatsAvailable, String status) {
    }

    public record AttemptResult(int resultId, int score, boolean passed, LocalDateTime scoredAt) {
    }

    /** Thrown inside a book transaction when the candidate is not in the replica (contract section 5). */
    public static final class UnknownCandidate extends RuntimeException {
        final int candidateId;

        UnknownCandidate(int candidateId) {
            super("Candidate " + candidateId + " is not known to scheduling-service");
            this.candidateId = candidateId;
        }
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SchedulingCommands.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Demotion demotion;
    private final Audit audit;
    private final ReferenceData reference;
    private final boolean legacySeatAccounting;

    public SchedulingCommands(JdbcTemplate jdbc, TransactionTemplate tx, Demotion demotion, Audit audit,
                              ReferenceData reference,
                              @Value("${scheduling.legacy-seat-accounting:false}") boolean legacySeatAccounting) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.demotion = demotion;
        this.audit = audit;
        this.reference = reference;
        this.legacySeatAccounting = legacySeatAccounting;
    }

    static final int DEADLOCK_ATTEMPTS = 5;

    /** Runs one command transaction, re-running the whole transaction if SQL Server picks it as a 1205 victim. */
    <T> T inTx(TransactionCallback<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(work);
            } catch (RuntimeException e) {
                if (attempt >= DEADLOCK_ATTEMPTS || !isDeadlock(e)) {
                    throw e;
                }
                LOG.info("Deadlock victim, retrying transaction (attempt {} of {})", attempt + 1, DEADLOCK_ATTEMPTS);
                try {
                    Thread.sleep(10L * attempt + java.util.concurrent.ThreadLocalRandom.current().nextInt(20));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    /**
     * True if SQL Server chose this transaction as a 1205 deadlock victim. Walks causes, SQLException next-exceptions
     * and suppressed exceptions, because mssql-jdbc can report the victim in a wrapped form ("An error occurred during
     * the current command (Done status 0). Transaction ... was deadlocked ...") whose getErrorCode() is not 1205.
     */
    public static boolean isDeadlock(Throwable e) {
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Deque<Throwable> todo = new java.util.ArrayDeque<>();
        todo.push(e);
        while (!todo.isEmpty()) {
            Throwable t = todo.pop();
            if (!seen.add(t)) {
                continue;
            }
            if (t instanceof com.microsoft.sqlserver.jdbc.SQLServerException se && se.getSQLServerError() != null
                    && se.getSQLServerError().getErrorNumber() == 1205) {
                return true;
            }
            if (t instanceof java.sql.SQLException sql) {
                if (sql.getErrorCode() == 1205 || mentionsDeadlockVictim(sql.getMessage())) {
                    return true;
                }
                if (sql.getNextException() != null) {
                    todo.push(sql.getNextException());
                }
            }
            if ("DeadlockLoserDataAccessException".equals(t.getClass().getSimpleName())
                    || ((t instanceof CannotAcquireLockException || t instanceof PessimisticLockingFailureException)
                    && mentionsDeadlockVictim(t.getMessage()))) {
                return true;
            }
            if (t.getCause() != null) {
                todo.push(t.getCause());
            }
            for (Throwable s : t.getSuppressed()) {
                todo.push(s);
            }
        }
        return false;
    }

    private static boolean mentionsDeadlockVictim(String message) {
        return message != null && message.toLowerCase(java.util.Locale.ROOT).contains("deadlocked");
    }

    /** UPDLOCK/HOLDLOCK the given session rows in ascending SessionId order. */
    private void lockSessions(int... sessionIds) {
        java.util.Arrays.stream(sessionIds).distinct().sorted().forEach(id -> jdbc.queryForList(
                "SELECT SessionId FROM dbo.ExamSession WITH (UPDLOCK, HOLDLOCK) WHERE SessionId = ?", Integer.class, id));
    }

    // ---------------------------------------------------------------- book (020_usp_BookSeat.sql)

    public BookOutcome book(int sessionId, int candidateId, String accommodationCode, String channel,
                            String externalRef, String actor) {
        try {
            return inTx(s -> bookInTx(sessionId, candidateId, accommodationCode, channel, externalRef, actor));
        } catch (UnknownCandidate e) {
            // Replica miss: ask the relay for an immediate catch-up, then retry once (contract section 5).
            try {
                reference.catchUp();
            } catch (RuntimeException relayDown) {
                LOG.warn("Reference catch-up failed on candidate {} miss: {}", candidateId, relayDown.getMessage());
            }
            try {
                return inTx(s -> bookInTx(sessionId, candidateId, accommodationCode, channel, externalRef, actor));
            } catch (UnknownCandidate again) {
                // usp_BookSeat's CATCH re-raised the FK violation as Msg 50000 with its state (0) and message.
                throw ApiException.legacy(HttpStatus.NOT_FOUND, "CANDIDATE_NOT_FOUND", 0,
                        "The INSERT statement conflicted with the FOREIGN KEY constraint \"FK_Booking_Candidate\". "
                                + "The conflict occurred in database \"AssessmentDelivery\", table \"dbo.Candidate\", column 'CandidateId'.");
            }
        }
    }

    private BookOutcome bookInTx(int sessionId, int candidateId, String acc, String channel, String externalRef,
                                 String actor) {
        boolean partner = Channels.PARTNER.equals(channel);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT SeatsAvailable, AccommodationCapacity, Status FROM dbo.ExamSession WITH (UPDLOCK, HOLDLOCK) "
                        + "WHERE SessionId = ?", sessionId);
        if (rows.isEmpty()) {
            throw ApiException.legacy(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", 1,
                    "Session " + sessionId + " is not open for booking.");
        }
        Map<String, Object> s = rows.get(0);
        if (!"OPEN".equals(s.get("Status"))) {
            throw ApiException.legacy(HttpStatus.CONFLICT, "SESSION_NOT_OPEN", 1,
                    "Session " + sessionId + " is not open for booking.");
        }
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = ? AND CandidateId = ? "
                + "AND Status IN ('BOOKED','WAITLISTED')", Integer.class, sessionId, candidateId);
        if (active != null && active > 0) {
            throw ApiException.legacy(HttpStatus.CONFLICT, "DUPLICATE_BOOKING", 2,
                    "Candidate " + candidateId + " already has an active booking on session " + sessionId + ".");
        }
        if (acc != null && Channels.EXTRA_TIME.contains(acc)) {
            Integer used = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = ? "
                    + "AND Status IN ('BOOKED','ATTENDED') AND AccommodationCode IN ('ET25','ET50')", Integer.class, sessionId);
            if (used != null && used >= ((Number) s.get("AccommodationCapacity")).intValue()) {
                throw ApiException.legacy(HttpStatus.CONFLICT, "NO_ACCOMMODATION_SEAT", 3,
                        "No accommodation seat available on session " + sessionId + ".");
            }
        }
        // The legacy FK on Booking.CandidateId fired at INSERT time, after the three checks above.
        if (!reference.candidateExists(candidateId)) {
            throw new UnknownCandidate(candidateId);
        }
        int seats = ((Number) s.get("SeatsAvailable")).intValue();
        String status = seats > 0 ? "BOOKED" : "WAITLISTED";
        int bookingId = jdbc.queryForObject("INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, "
                + "Channel, ExternalRef) OUTPUT INSERTED.BookingId VALUES (?, ?, ?, ?, ?, ?)", Integer.class,
                sessionId, candidateId, status, acc, channel, externalRef);
        Integer position = null;
        if (seats > 0) {
            jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable - ?, LastModified = GETDATE() "
                    + "WHERE SessionId = ?", seatsTaken(channel), sessionId);
        } else {
            position = jdbc.queryForObject("SELECT ISNULL(MAX(Position), 0) + 1 FROM dbo.Waitlist WHERE SessionId = ?",
                    Integer.class, sessionId);
            jdbc.update("INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position) VALUES (?, ?, ?, ?)",
                    sessionId, candidateId, bookingId, position);
        }
        String detail = "session=" + sessionId + " status=" + status;
        if (partner) {
            detail += " channel=PARTNER" + (externalRef != null ? " ref=" + externalRef : "");
        }
        audit.write("Booking", bookingId, "BOOK", detail, actor);
        return new BookOutcome(bookingId, status, position, null);
    }

    /**
     * Seats one BOOKED booking takes. PORTAL and IMPORT take one. PARTNER takes two while
     * scheduling.legacy-seat-accounting=true: booking-api's decrementSeatsAvailable plus trg_Booking_SeatSync
     * (HB-01, contract section 6).
     */
    int seatsTaken(String channel) {
        return Channels.PARTNER.equals(channel) && legacySeatAccounting ? 2 : 1;
    }

    // ---------------------------------------------------------------- cancel (021_usp_CancelBooking.sql)

    public void cancel(int bookingId, String reason, String actor) {
        inTx(s -> {
            cancelInTx(bookingId, reason, actor);
            return null;
        });
    }

    private void cancelInTx(int bookingId, String reason, String actor) {
        // Session before booking (class comment): the proc's booking-then-session order lets cancel-vs-book deadlock
        // over JDBC. A reschedule has already locked both sessions in order, so this re-lock is a no-op there.
        List<Integer> owning = jdbc.queryForList("SELECT SessionId FROM dbo.Booking WHERE BookingId = ?", Integer.class, bookingId);
        if (!owning.isEmpty()) {
            lockSessions(owning.get(0));
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT Status, SessionId FROM dbo.Booking WITH (UPDLOCK, HOLDLOCK) WHERE BookingId = ?", bookingId);
        if (rows.isEmpty()) {
            throw ApiException.legacy(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", 4,
                    "Booking " + bookingId + " cannot be cancelled.");
        }
        String status = (String) rows.get(0).get("Status");
        int sessionId = ((Number) rows.get(0).get("SessionId")).intValue();
        if (!"BOOKED".equals(status) && !"WAITLISTED".equals(status)) {
            throw ApiException.legacy(HttpStatus.CONFLICT, "NOT_CANCELLABLE", 4,
                    "Booking " + bookingId + " cannot be cancelled.");
        }
        jdbc.update("UPDATE dbo.Booking SET Status = 'CANCELLED', CancelledAt = GETDATE(), CancelReason = ?, "
                + "LastModified = GETDATE() WHERE BookingId = ?", reason, bookingId);
        if ("BOOKED".equals(status)) {
            // One seat back for every channel, including PARTNER (HB-02).
            jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable + 1, LastModified = GETDATE() "
                    + "WHERE SessionId = ?", sessionId);
        } else {
            // Nobody is promoted and the position gap stays (HB-06).
            jdbc.update("DELETE FROM dbo.Waitlist WHERE BookingId = ?", bookingId);
        }
        audit.write("Booking", bookingId, "CANCEL", reason, actor);
    }

    // ---------------------------------------------------------------- change capacity (022 + 033 demotion)

    public CapacityOutcome changeCapacity(int sessionId, int newCapacity, String actor, String ifMatch) {
        return inTx(st -> {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT Capacity, AccommodationCapacity, RowVer "
                    + "FROM dbo.ExamSession WITH (UPDLOCK, HOLDLOCK) WHERE SessionId = ?", sessionId);
            if (rows.isEmpty()) {
                throw ApiException.legacy(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", 5,
                        "Invalid capacity " + newCapacity + " for session " + sessionId + ".");
            }
            int oldCapacity = ((Number) rows.get(0).get("Capacity")).intValue();
            int accCap = ((Number) rows.get(0).get("AccommodationCapacity")).intValue();
            // Only < 0 and < AccommodationCapacity are rejected; there is no upper bound (HB-23).
            if (newCapacity < 0 || newCapacity < accCap) {
                throw ApiException.legacy(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CAPACITY", 5,
                        "Invalid capacity " + newCapacity + " for session " + sessionId + ".");
            }
            if (ifMatch != null && !ifMatch.isBlank()
                    && !ETags.matches(ifMatch, ETags.of((byte[]) rows.get(0).get("RowVer")))) {
                throw new ApiException(HttpStatus.PRECONDITION_FAILED, "VERSION_MISMATCH",
                        "If-Match does not match the current session version", null);
            }
            jdbc.update("UPDATE dbo.ExamSession SET Capacity = ?, "
                    + "SeatsAvailable = CASE WHEN SeatsAvailable + (? - Capacity) < 0 THEN 0 "
                    + "ELSE SeatsAvailable + (? - Capacity) END, LastModified = GETDATE() WHERE SessionId = ?",
                    newCapacity, newCapacity, newCapacity, sessionId);
            List<Integer> demoted = demotion.afterCapacityChange(sessionId, oldCapacity, newCapacity);
            audit.write("ExamSession", sessionId, "CAPACITY", oldCapacity + " -> " + newCapacity, actor);
            Integer seats = jdbc.queryForObject("SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = ?",
                    Integer.class, sessionId);
            return new CapacityOutcome(sessionId, oldCapacity, newCapacity, seats, demoted);
        });
    }

    // ---------------------------------------------------------------- reschedule (023_usp_RescheduleBooking.sql)

    /**
     * PORTAL reschedule. The same-exam guard reads without locks before the transaction, exactly like the proc,
     * then cancel + book run in one transaction. Both session rows are locked up front in SessionId order, so two
     * reschedules crossing between the same sessions serialise instead of deadlocking as the proc could (HB-15).
     */
    public BookOutcome reschedulePortal(int bookingId, int newSessionId, String actor) {
        List<Map<String, Object>> old = jdbc.queryForList("SELECT b.CandidateId, b.AccommodationCode, s.ExamId "
                + "FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId = b.SessionId WHERE b.BookingId = ?", bookingId);
        List<Integer> newExam = jdbc.queryForList("SELECT ExamId FROM dbo.ExamSession WHERE SessionId = ?",
                Integer.class, newSessionId);
        String msg = "Booking " + bookingId + " cannot be moved to session " + newSessionId + ".";
        if (old.isEmpty()) {
            throw ApiException.legacy(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", 6, msg);
        }
        if (newExam.isEmpty()) {
            throw ApiException.legacy(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", 6, msg);
        }
        if (((Number) old.get(0).get("ExamId")).intValue() != newExam.get(0)) {
            throw ApiException.legacy(HttpStatus.CONFLICT, "DIFFERENT_EXAM", 6, msg);
        }
        int candidateId = ((Number) old.get(0).get("CandidateId")).intValue();
        String acc = (String) old.get(0).get("AccommodationCode");
        int fromSession = sourceSession(bookingId);
        BookOutcome b = inTx(s -> {
            lockSessions(fromSession, newSessionId);
            cancelInTx(bookingId, "Rescheduled", actor);
            return bookInTx(newSessionId, candidateId, acc, Channels.PORTAL, null, actor);
        });
        return new BookOutcome(b.bookingId(), b.status(), b.waitlistPosition(), bookingId);
    }

    /** PARTNER reschedule: booking-api BookingService.reschedule (DIFFERENT_EXAM, SAME_SESSION, cancel, partner book). */
    public BookOutcome reschedulePartner(int bookingId, int newSessionId, String actor) {
        List<Map<String, Object>> old = jdbc.queryForList("SELECT b.CandidateId, b.SessionId, b.AccommodationCode, "
                + "b.ExternalRef, s.ExamId FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId = b.SessionId "
                + "WHERE b.BookingId = ?", bookingId);
        if (old.isEmpty()) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        List<Integer> newExam = jdbc.queryForList("SELECT ExamId FROM dbo.ExamSession WHERE SessionId = ?",
                Integer.class, newSessionId);
        if (newExam.isEmpty()) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session " + newSessionId + " not found");
        }
        Map<String, Object> o = old.get(0);
        if (((Number) o.get("ExamId")).intValue() != newExam.get(0)) {
            throw ApiException.conflict("DIFFERENT_EXAM", "Booking " + bookingId + " cannot be moved to session "
                    + newSessionId + " (different exam)");
        }
        if (((Number) o.get("SessionId")).intValue() == newSessionId) {
            throw ApiException.conflict("SAME_SESSION", "Booking " + bookingId + " is already on session " + newSessionId);
        }
        int candidateId = ((Number) o.get("CandidateId")).intValue();
        int fromSession = ((Number) o.get("SessionId")).intValue();
        BookOutcome b = inTx(s -> {
            lockSessions(fromSession, newSessionId);
            cancelInTx(bookingId, "Rescheduled", actor);
            return bookInTx(newSessionId, candidateId, (String) o.get("AccommodationCode"), Channels.PARTNER,
                    (String) o.get("ExternalRef"), actor);
        });
        return new BookOutcome(b.bookingId(), b.status(), b.waitlistPosition(), bookingId);
    }

    private int sourceSession(int bookingId) {
        return jdbc.queryForObject("SELECT SessionId FROM dbo.Booking WHERE BookingId = ?", Integer.class, bookingId);
    }

    // ---------------------------------------------------------------- attendance (results-service)

    /** BOOKED -> ATTENDED; ATTENDED is a no-op; anything else 409 (ResultIngestService.java:62-65,96-97, HB-18). */
    public AttendOutcome markAttended(int bookingId, AttemptResult result) {
        return inTx(s -> {
            List<Integer> owning = jdbc.queryForList("SELECT SessionId FROM dbo.Booking WHERE BookingId = ?", Integer.class,
                    bookingId);
            if (!owning.isEmpty()) {
                lockSessions(owning.get(0));
            }
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT b.Status, b.CandidateId, b.SessionId, e.ExamId "
                    + "FROM dbo.Booking b WITH (UPDLOCK, HOLDLOCK) JOIN dbo.ExamSession e ON e.SessionId = b.SessionId "
                    + "WHERE b.BookingId = ?", bookingId);
            if (rows.isEmpty()) {
                throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
            }
            Map<String, Object> r = rows.get(0);
            String status = (String) r.get("Status");
            boolean transitioned;
            if ("BOOKED".equals(status)) {
                jdbc.update("UPDATE dbo.Booking SET Status = 'ATTENDED', LastModified = GETDATE() WHERE BookingId = ?",
                        bookingId);
                transitioned = true;
            } else if ("ATTENDED".equals(status)) {
                transitioned = false;
            } else {
                throw ApiException.conflict("BOOKING_NOT_ACTIVE", "Booking " + bookingId + " is " + status);
            }
            if (result != null) {
                jdbc.update("""
                        MERGE dbo.Attempt AS t
                        USING (SELECT ? AS ResultId) AS src ON t.ResultId = src.ResultId
                        WHEN MATCHED THEN UPDATE SET Score = ?, Passed = ?, ScoredAt = ?
                        WHEN NOT MATCHED THEN INSERT (ResultId, BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt)
                             VALUES (?, ?, ?, ?, ?, ?, ?, ?);""",
                        result.resultId(), result.score(), result.passed(), result.scoredAt(),
                        result.resultId(), bookingId, r.get("CandidateId"), r.get("ExamId"), r.get("SessionId"),
                        result.score(), result.passed(), result.scoredAt());
            }
            return new AttendOutcome(bookingId, transitioned);
        });
    }

    // ---------------------------------------------------------------- session creation (admin / scheduling ops)

    public int createSession(NewSession n) {
        if (n.capacity() < 0) {
            throw ApiException.badRequest("VALIDATION", "capacity must be >= 0");
        }
        String status = n.status() == null ? "OPEN" : n.status();
        return inTx(s -> jdbc.queryForObject("INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, "
                + "Capacity, AccommodationCapacity, SeatsAvailable, Status) OUTPUT INSERTED.SessionId "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", Integer.class, n.examId(), n.testCenterId(), n.roomId(), n.startsAt(),
                n.capacity(), n.accommodationCapacity() == null ? 0 : n.accommodationCapacity(),
                n.seatsAvailable() == null ? n.capacity() : n.seatsAvailable(), status));
    }
}
