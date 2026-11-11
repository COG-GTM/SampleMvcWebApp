package com.northwind.ad.results.service;

import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedHashMap;
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
     * Records one result and marks the booking ATTENDED.
     * @param sourceFile score file name, null for REST posts
     */
    @Transactional
    public Map<String, Object> ingest(int bookingId, int score, Date scoredAt, String sourceFile) {
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
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.Result WHERE BookingId = ?",
                Integer.class, bookingId);
        if (existing != null && existing.intValue() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESULT",
                    "A result already exists for booking " + bookingId);
        }

        int passingScore = ((Number) b.get("PassingScore")).intValue();
        boolean passed = score >= passingScore;
        int resultId;
        try {
            resultId = jdbc.queryForObject(
                    "SET NOCOUNT ON; "
                  + "INSERT INTO dbo.Result (BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt, SourceFile) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                    Integer.class, bookingId, b.get("CandidateId"), b.get("ExamId"), b.get("SessionId"), score,
                    passed, new Timestamp(scoredAt.getTime()), sourceFile);
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESULT",
                    "A result already exists for booking " + bookingId);
        }
        jdbc.update("UPDATE dbo.Booking SET Status = 'ATTENDED', LastModified = GETDATE() "
                + "WHERE BookingId = ? AND Status = 'BOOKED'", bookingId);
        jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                "Result", resultId, "INGEST", "booking=" + bookingId + " score=" + score
                        + (sourceFile != null ? " file=" + sourceFile : ""), actor);
        LOG.info("Result {} for booking {} score {} passed={}", resultId, bookingId, score, passed);

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("resultId", resultId);
        out.put("bookingId", bookingId);
        out.put("score", score);
        out.put("passed", passed);
        return out;
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
