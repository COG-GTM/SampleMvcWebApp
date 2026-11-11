package com.northwind.ad.results.service;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.northwind.ad.results.web.ApiException;

@Service
public class ResultIngestService {

    private static final Logger LOG = LoggerFactory.getLogger(ResultIngestService.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SchedulingClient scheduling;

    @Autowired
    private PlatformTransactionManager txManager;

    @Value("${results.audit-actor:results-service}")
    private String actor;

    /**
     * Records one result, then marks the booking ATTENDED through scheduling-service (POST /bookings/{id}/attendance)
     * only after the Result has committed, so a rollback can never leave an ATTENDED booking behind.
     * If the attendance call is rejected (404/409: the booking changed after our read) the committed Result and its
     * audit rows are deleted again and the error is returned: nothing is recorded, as before.
     * If scheduling-service does not answer (503 SCHEDULING_UNAVAILABLE) the Result stays committed and the booking
     * stays BOOKED; resending the same result (REST or score file) is a DUPLICATE that completes the attendance call,
     * or, if the booking was cancelled meanwhile, removes the unconfirmed Result and returns 409 BOOKING_NOT_ACTIVE.
     * Idempotent per booking (UQ_Result_Booking): resubmitting the stored score is a DUPLICATE no-op, a different score
     * is a CONFLICT that leaves the stored row as is and writes a DUPLICATE_REJECTED audit row.
     * @param scoredAt null for REST posts without scoredAt: stored as now, and a replay matches on score alone
     * @param sourceFile score file name, null for REST posts
     */
    public IngestOutcome ingest(int bookingId, int score, Date scoredAt, String sourceFile) {
        return ingest(bookingId, score, scoredAt, sourceFile, true);
    }

    /**
     * @param insertAllowed false only re-checks a row (replayed score file): returns NOT_IMPORTED instead of
     *                      inserting, and a conflict already audited with the same detail is not audited again
     */
    public IngestOutcome ingest(final int bookingId, final int score, final Date scoredAt, final String sourceFile,
                                final boolean insertAllowed) {
        if (score < 0 || score > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "score must be between 0 and 1000");
        }
        final BookingView b = scheduling.getBooking(bookingId);
        if (b == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        String status = b.getStatus();
        if (!"BOOKED".equals(status) && !"ATTENDED".equals(status)) {
            Map<String, Object> pending = findStored(bookingId);
            if (pending != null) {
                // only a Result whose attendance call got a 503 can sit on a booking that was cancelled meanwhile
                LOG.warn("Booking {} is {}: removing its unconfirmed result {}", bookingId, status, pending.get("ResultId"));
                removeResult(((Number) pending.get("ResultId")).intValue());
            }
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_ACTIVE",
                    "Booking " + bookingId + " is " + status + ", cannot record a result");
        }
        final boolean[] lostRace = new boolean[1];
        IngestOutcome outcome = tx().execute(new TransactionCallback<IngestOutcome>() {
            @Override
            public IngestOutcome doInTransaction(TransactionStatus txStatus) {
                return record(b, score, scoredAt, sourceFile, insertAllowed, lostRace);
            }
        });
        if (outcome.getStatus() == IngestOutcome.Status.CREATED) {
            attendCreated(outcome);
        } else if ("BOOKED".equals(status) && !lostRace[0] && outcome.getResultId() != null
                && (outcome.getStatus() == IngestOutcome.Status.DUPLICATE
                    || outcome.getStatus() == IngestOutcome.Status.CONFLICT)) {
            // a stored Result on a still-BOOKED booking: an earlier ingest committed but its attendance call failed
            // (not after a lost insert race: the concurrent winner is making that call itself)
            LOG.info("Completing attendance for booking {} from stored result {}", bookingId, outcome.getResultId());
            scheduling.markAttended(bookingId, outcome.getResultId(), outcome.getStoredScore(),
                    Boolean.TRUE.equals(outcome.getPassed()), outcome.getStoredScoredAt());
        }
        return outcome;
    }

    private IngestOutcome record(BookingView b, int score, Date scoredAt, String sourceFile, boolean insertAllowed,
                                 boolean[] lostRace) {
        int bookingId = b.getBookingId();
        Map<String, Object> existing = findStored(bookingId);
        if (existing != null) {
            return compareWithStored(existing, bookingId, score, scoredAt, sourceFile, insertAllowed);
        }
        if (!insertAllowed) {
            return new IngestOutcome(IngestOutcome.Status.NOT_IMPORTED, bookingId, null, score, scoredAt,
                    null, null, null);
        }

        Date storedAt = scoredAt != null ? scoredAt : new Date();
        int passingScore = passingScore(b);
        boolean passed = score >= passingScore;
        int resultId;
        try {
            resultId = jdbc.queryForObject(
                    "SET NOCOUNT ON; "
              + "INSERT INTO dbo.Result (BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt, SourceFile, "
              + "SessionStartsAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                Integer.class, bookingId, b.getCandidateId(), b.getExamId(), b.getSessionId(), score,
                passed, new Timestamp(storedAt.getTime()), sourceFile, startsAt(b));
        } catch (DuplicateKeyException e) {
            // lost a race with a concurrent ingest for the same booking; its row is committed by now
            LOG.info("Insert for booking {} lost the UQ_Result_Booking race, re-reading the stored result", bookingId);
            lostRace[0] = true;
            existing = findStored(bookingId);
            if (existing == null) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESULT",
                        "A result already exists for booking " + bookingId);
            }
            return compareWithStored(existing, bookingId, score, scoredAt, sourceFile, true);
        }
        jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                "Result", resultId, "INGEST", "booking=" + bookingId + " score=" + score
                        + (sourceFile != null ? " file=" + sourceFile : ""), actor);
        LOG.info("Result {} for booking {} score {} passed={}", resultId, bookingId, score, passed);
        return new IngestOutcome(IngestOutcome.Status.CREATED, bookingId, resultId, score, storedAt,
                null, null, passed);
    }

    private void attendCreated(IngestOutcome created) {
        final int bookingId = created.getBookingId();
        final int resultId = created.getResultId();
        try {
            scheduling.markAttended(bookingId, resultId, created.getSubmittedScore(),
                    Boolean.TRUE.equals(created.getPassed()), created.getSubmittedScoredAt());
        } catch (ApiException e) {
            if (SchedulingClient.UNAVAILABLE.equals(e.getError())) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, SchedulingClient.UNAVAILABLE,
                        "result " + resultId + " for booking " + bookingId + " is recorded but scheduling-service did not "
                                + "confirm attendance; resend the same result to complete it");
            }
            LOG.warn("Attendance for booking {} rejected ({}), removing result {}", bookingId, e.getError(), resultId);
            removeResult(resultId);
            throw e;
        }
    }

    /** Compensates an unconfirmed Result: deletes it with every audit row that references it (INGEST, DUPLICATE_REJECTED). */
    private void removeResult(final int resultId) {
        tx().execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus txStatus) {
                jdbc.update("DELETE FROM dbo.AuditLog WHERE EntityName = 'Result' AND EntityId = ?", resultId);
                jdbc.update("DELETE FROM dbo.Result WHERE ResultId = ?", resultId);
            }
        });
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    // Exam is reference data that stays in AssessmentDelivery (inventory RS-01: Ref-local)
    private int passingScore(BookingView b) {
        if (b.getExamId() == null) {
            throw new IllegalStateException("scheduling-service returned booking " + b.getBookingId() + " without examId");
        }
        return jdbc.queryForObject("SELECT PassingScore FROM dbo.Exam WHERE ExamId = ?", Integer.class, b.getExamId());
    }

    static Timestamp startsAt(BookingView b) {
        String s = b.getSessionStartsAt();
        if (s == null || s.isEmpty()) {
            return null;
        }
        s = s.replace('T', ' ');
        return Timestamp.valueOf(s.length() == 16 ? s + ":00" : s);
    }

    // serialises concurrent replays of the same result until commit, so only one of them writes the audit row
    private boolean lockAndCheckAudited(int resultId, String detail) {
        Integer lock = jdbc.queryForObject("DECLARE @r INT; EXEC @r = sp_getapplock @Resource = ?, "
                + "@LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000; SELECT @r",
                Integer.class, "ResultAudit:" + resultId);
        if (lock == null || lock < 0) {
            throw new IllegalStateException("could not lock audit for result " + resultId + " (" + lock + ")");
        }
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.AuditLog WHERE EntityName = 'Result' AND EntityId = ? "
                + "AND Action = 'DUPLICATE_REJECTED' AND Detail = ?", Integer.class, resultId, detail) > 0;
    }

    private Map<String, Object> findStored(int bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ResultId, Score, ScoredAt, Passed FROM dbo.Result WHERE BookingId = ?", bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private IngestOutcome compareWithStored(Map<String, Object> stored, int bookingId, int score, Date scoredAt,
                                            String sourceFile, boolean audit) {
        int resultId = ((Number) stored.get("ResultId")).intValue();
        int storedScore = ((Number) stored.get("Score")).intValue();
        Date storedScoredAt = (Date) stored.get("ScoredAt");
        Boolean passed = (Boolean) stored.get("Passed");
        boolean same = storedScore == score && (scoredAt == null || sameSecond(storedScoredAt, scoredAt));
        if (same) {
            LOG.info("Duplicate result for booking {} (result {}) ignored", bookingId, resultId);
            return new IngestOutcome(IngestOutcome.Status.DUPLICATE, bookingId, resultId, score, scoredAt,
                    storedScore, storedScoredAt, passed);
        }
        String detail = "booking=" + bookingId
                + " stored=" + storedScore + "@" + format(storedScoredAt)
                + " submitted=" + score + "@" + (scoredAt != null ? format(scoredAt) : "now")
                + (sourceFile != null ? " file=" + sourceFile : "");
        if (audit || !lockAndCheckAudited(resultId, detail)) {
            jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                    "Result", resultId, "DUPLICATE_REJECTED", detail, actor);
        }
        LOG.warn("Conflicting result for booking {} rejected: stored {} submitted {}", bookingId, storedScore, score);
        return new IngestOutcome(IngestOutcome.Status.CONFLICT, bookingId, resultId, score, scoredAt,
                storedScore, storedScoredAt, passed);
    }

    // DATETIME keeps 1/300 s; submitted times are whole seconds (CsvRowParser formats)
    private static boolean sameSecond(Date a, Date b) {
        return a.getTime() / 1000L == b.getTime() / 1000L;
    }

    static String format(Date d) {
        return d == null ? null : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(d);
    }

    /** Latest seat-holding booking of a candidate on a session, or null. Used by the score file import. */
    public Integer findBookingId(String candidateNumber, int sessionId) {
        return scheduling.findLatestActiveBookingId(candidateNumber, sessionId);
    }

    public Integer findCandidateId(String candidateNumber) {
        List<Integer> ids = jdbc.queryForList("SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = ?",
                Integer.class, candidateNumber);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public List<Map<String, Object>> findByCandidate(int candidateId) {
        return jdbc.queryForList(
                "SELECT r.ResultId AS resultId, r.BookingId AS bookingId, e.ExamCode AS examCode, e.Title AS examTitle, "
              + "       r.SessionId AS sessionId, CONVERT(VARCHAR(10), r.SessionStartsAt, 23) AS sessionDate, "
              + "       r.Score AS score, r.Passed AS passed, CONVERT(VARCHAR(19), r.ScoredAt, 126) AS scoredAt, "
              + "       r.SourceFile AS sourceFile "
              + "FROM dbo.Result r JOIN dbo.Exam e ON e.ExamId = r.ExamId "
              + "WHERE r.CandidateId = ? ORDER BY r.SessionStartsAt DESC, r.ResultId DESC", candidateId);
    }
}
