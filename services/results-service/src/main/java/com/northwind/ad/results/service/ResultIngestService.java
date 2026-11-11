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
import org.springframework.transaction.annotation.Transactional;

import com.northwind.ad.results.web.ApiException;

@Service
public class ResultIngestService {

    private static final Logger LOG = LoggerFactory.getLogger(ResultIngestService.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${results.audit-actor:results-service}")
    private String actor;

    /**
     * Records one result and marks the booking ATTENDED. Idempotent per booking (UQ_Result_Booking):
     * resubmitting the stored score is a DUPLICATE no-op, a different score is a CONFLICT that leaves the
     * stored row as is and writes a DUPLICATE_REJECTED audit row.
     * @param scoredAt null for REST posts without scoredAt: stored as now, and a replay matches on score alone
     * @param sourceFile score file name, null for REST posts
     */
    @Transactional
    public IngestOutcome ingest(int bookingId, int score, Date scoredAt, String sourceFile) {
        return ingest(bookingId, score, scoredAt, sourceFile, true);
    }

    /**
     * @param insertAllowed false only re-checks a row (replayed score file): returns NOT_IMPORTED instead of inserting
     */
    @Transactional
    public IngestOutcome ingest(int bookingId, int score, Date scoredAt, String sourceFile, boolean insertAllowed) {
        if (score < 0 || score > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "score must be between 0 and 1000");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT b.BookingId, b.CandidateId, b.SessionId, b.Status, s.ExamId, e.PassingScore "
              + "FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId = b.SessionId "
              + "JOIN dbo.Exam e ON e.ExamId = s.ExamId WHERE b.BookingId = ?", bookingId);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        Map<String, Object> b = rows.get(0);
        String status = (String) b.get("Status");
        if (!"BOOKED".equals(status) && !"ATTENDED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_ACTIVE",
                    "Booking " + bookingId + " is " + status + ", cannot record a result");
        }
        Map<String, Object> existing = findStored(bookingId);
        if (existing != null) {
            return compareWithStored(existing, bookingId, score, scoredAt, sourceFile);
        }
        if (!insertAllowed) {
            return new IngestOutcome(IngestOutcome.Status.NOT_IMPORTED, bookingId, null, score, scoredAt,
                    null, null, null);
        }

        Date storedAt = scoredAt != null ? scoredAt : new Date();
        int passingScore = ((Number) b.get("PassingScore")).intValue();
        boolean passed = score >= passingScore;
        int resultId;
        try {
            resultId = jdbc.queryForObject(
                    "SET NOCOUNT ON; "
                  + "INSERT INTO dbo.Result (BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt, SourceFile) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                    Integer.class, bookingId, b.get("CandidateId"), b.get("ExamId"), b.get("SessionId"), score,
                    passed, new Timestamp(storedAt.getTime()), sourceFile);
        } catch (DuplicateKeyException e) {
            // lost a race with a concurrent ingest for the same booking; its row is committed by now
            LOG.info("Insert for booking {} lost the UQ_Result_Booking race, re-reading the stored result", bookingId);
            existing = findStored(bookingId);
            if (existing == null) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESULT",
                        "A result already exists for booking " + bookingId);
            }
            return compareWithStored(existing, bookingId, score, scoredAt, sourceFile);
        }
        jdbc.update("UPDATE dbo.Booking SET Status = 'ATTENDED', LastModified = GETDATE() "
                + "WHERE BookingId = ? AND Status = 'BOOKED'", bookingId);
        jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                "Result", resultId, "INGEST", "booking=" + bookingId + " score=" + score
                        + (sourceFile != null ? " file=" + sourceFile : ""), actor);
        LOG.info("Result {} for booking {} score {} passed={}", resultId, bookingId, score, passed);
        return new IngestOutcome(IngestOutcome.Status.CREATED, bookingId, resultId, score, storedAt,
                null, null, passed);
    }

    private Map<String, Object> findStored(int bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ResultId, Score, ScoredAt, Passed FROM dbo.Result WHERE BookingId = ?", bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private IngestOutcome compareWithStored(Map<String, Object> stored, int bookingId, int score, Date scoredAt,
                                            String sourceFile) {
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
        jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                "Result", resultId, "DUPLICATE_REJECTED", "booking=" + bookingId
                        + " stored=" + storedScore + "@" + format(storedScoredAt)
                        + " submitted=" + score + "@" + (scoredAt != null ? format(scoredAt) : "now")
                        + (sourceFile != null ? " file=" + sourceFile : ""), actor);
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
        List<Integer> ids = jdbc.queryForList(
                "SELECT TOP 1 b.BookingId FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId = b.CandidateId "
              + "WHERE c.CandidateNumber = ? AND b.SessionId = ? AND b.Status IN ('BOOKED','ATTENDED') "
              + "ORDER BY b.BookingId DESC", Integer.class, candidateNumber, sessionId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public Integer findCandidateId(String candidateNumber) {
        List<Integer> ids = jdbc.queryForList("SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = ?",
                Integer.class, candidateNumber);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public List<Map<String, Object>> findByCandidate(int candidateId) {
        return jdbc.queryForList(
                "SELECT r.ResultId AS resultId, r.BookingId AS bookingId, e.ExamCode AS examCode, e.Title AS examTitle, "
              + "       r.SessionId AS sessionId, CONVERT(VARCHAR(10), s.StartsAt, 23) AS sessionDate, "
              + "       r.Score AS score, r.Passed AS passed, CONVERT(VARCHAR(19), r.ScoredAt, 126) AS scoredAt, "
              + "       r.SourceFile AS sourceFile "
              + "FROM dbo.Result r JOIN dbo.Exam e ON e.ExamId = r.ExamId "
              + "JOIN dbo.ExamSession s ON s.SessionId = r.SessionId "
              + "WHERE r.CandidateId = ? ORDER BY s.StartsAt DESC, r.ResultId DESC", candidateId);
    }
}
