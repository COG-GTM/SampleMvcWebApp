package com.northwind.ad.results.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.northwind.ad.results.model.IngestOutcome;
import com.northwind.ad.results.model.StoredResult;
import com.northwind.ad.results.web.ApiException;

/**
 * Idempotent result ingestion. A booking has at most one result (UQ_Result_Booking):
 * <ul>
 * <li>no result yet: insert it, booking becomes ATTENDED -> CREATED</li>
 * <li>same score and scoredAt already stored (see {@link ResultClassifier}) -> DUPLICATE, nothing written</li>
 * <li>a different result already stored -> CONFLICT, stored row untouched, DUPLICATE_REJECTED audit row committed</li>
 * </ul>
 * Not transactional itself: the insert and the conflict audit each run in their own transaction
 * ({@link ResultStore}), so losing an insert race leaves no failed transaction behind and the
 * existing row can be re-read and classified the same way.
 */
@Service
public class ResultIngestService {

    private static final Logger LOG = LoggerFactory.getLogger(ResultIngestService.class);
    private static final int MAX_INSERT_ATTEMPTS = 2;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ResultStore store;

    /** REST variant, see {@link #ingest(int, int, Date, String, Integer)}. */
    public IngestOutcome ingest(int bookingId, int score, Date scoredAt, String sourceFile) {
        return ingest(bookingId, score, scoredAt, sourceFile, null);
    }

    /**
     * @param scoredAt   null when the caller sent none: a new result is stored with "now" and a
     *                   replay is matched on score only
     * @param sourceFile score file name, null for REST posts
     * @param lineNumber score file line, null for REST posts (audit detail only)
     */
    public IngestOutcome ingest(int bookingId, int score, Date scoredAt, String sourceFile, Integer lineNumber) {
        if (score < 0 || score > 1000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "score must be between 0 and 1000");
        }
        Map<String, Object> b = store.findBooking(bookingId);
        if (b == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        StoredResult existing = store.findByBooking(bookingId);
        if (existing != null) {
            return classify(existing, score, scoredAt, sourceFile, lineNumber);
        }
        String status = (String) b.get("Status");
        if (!"BOOKED".equals(status) && !"ATTENDED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "BOOKING_NOT_ACTIVE",
                    "Booking " + bookingId + " is " + status + ", cannot record a result");
        }

        int passingScore = ((Number) b.get("PassingScore")).intValue();
        boolean passed = score >= passingScore;
        Date effectiveScoredAt = scoredAt != null ? scoredAt : new Date();
        for (int attempt = 1; ; attempt++) {
            try {
                int resultId = store.insertResult(b, score, passed, effectiveScoredAt, sourceFile);
                LOG.info("Result {} for booking {} score {} passed={}", resultId, bookingId, score, passed);
                return IngestOutcome.created(resultId, bookingId, score, passed);
            } catch (DataIntegrityViolationException e) {
                // DuplicateKeyException on UQ_Result_Booking: a concurrent caller stored a result first
                existing = afterFailedInsert(bookingId, e);
            } catch (ConcurrencyFailureException e) {
                // deadlock victim / lock timeout while racing for the same booking
                existing = store.findByBooking(bookingId);
                if (existing == null && attempt < MAX_INSERT_ATTEMPTS) {
                    continue;
                }
                if (existing == null) {
                    throw e;
                }
            }
            LOG.info("Booking {} got a result from a concurrent caller (result {})", bookingId, existing.getResultId());
            return classify(existing, score, scoredAt, sourceFile, lineNumber);
        }
    }

    private StoredResult afterFailedInsert(int bookingId, DataAccessException e) {
        StoredResult existing = store.findByBooking(bookingId);
        if (existing == null) {
            throw e; // some other integrity problem, not a duplicate
        }
        return existing;
    }

    private IngestOutcome classify(StoredResult existing, int score, Date scoredAt, String sourceFile, Integer lineNumber) {
        if (ResultClassifier.isDuplicate(existing, score, scoredAt)) {
            LOG.info("Duplicate result for booking {} ignored (result {})", existing.getBookingId(), existing.getResultId());
            return IngestOutcome.duplicate(existing);
        }
        IngestOutcome conflict = IngestOutcome.conflict(existing, score, scoredAt);
        store.auditDuplicateRejected(conflict, sourceFile, lineNumber);
        LOG.warn("Conflicting result for booking {} rejected: stored {} submitted {}", existing.getBookingId(),
                existing.getScore(), score);
        return conflict;
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
