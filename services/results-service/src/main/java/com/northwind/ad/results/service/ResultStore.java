package com.northwind.ad.results.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.northwind.ad.results.model.IngestOutcome;
import com.northwind.ad.results.model.StoredResult;
import com.northwind.ad.results.model.UploadReport;

/**
 * JDBC access for result ingestion. Each write method is its own transaction, so a rejected
 * duplicate can be audited even though the request ends in a 409.
 */
@Repository
public class ResultStore {

    private static final RowMapper<StoredResult> STORED_RESULT = new RowMapper<StoredResult>() {
        @Override
        public StoredResult mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp scoredAt = rs.getTimestamp("ScoredAt");
            return new StoredResult(rs.getInt("ResultId"), rs.getInt("BookingId"), rs.getInt("Score"),
                    rs.getBoolean("Passed"), scoredAt == null ? null : new Date(scoredAt.getTime()));
        }
    };

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${results.audit-actor:results-service}")
    private String actor;

    /** BookingId, CandidateId, SessionId, Status, ExamId, PassingScore of a booking, or null. */
    public Map<String, Object> findBooking(int bookingId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT b.BookingId, b.CandidateId, b.SessionId, b.Status, s.ExamId, e.PassingScore "
              + "FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId = b.SessionId "
              + "JOIN dbo.Exam e ON e.ExamId = s.ExamId WHERE b.BookingId = ?", bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The result already stored for a booking, or null. */
    public StoredResult findByBooking(int bookingId) {
        List<StoredResult> rows = jdbc.query(
                "SELECT ResultId, BookingId, Score, Passed, ScoredAt FROM dbo.Result WHERE BookingId = ?",
                STORED_RESULT, bookingId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Inserts the result and writes the INGEST audit row, all or nothing (Booking is scheduling-service's, MBA-3823).
     * Throws DuplicateKeyException when another caller stored a result for the booking first (UQ_Result_Booking).
     */
    @Transactional
    public int insertResult(Map<String, Object> booking, int score, boolean passed, Date scoredAt, String sourceFile) {
        int bookingId = ((Number) booking.get("BookingId")).intValue();
        Integer resultId = jdbc.queryForObject(
                "SET NOCOUNT ON; "
              + "INSERT INTO dbo.Result (BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt, SourceFile) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                Integer.class, bookingId, booking.get("CandidateId"), booking.get("ExamId"), booking.get("SessionId"),
                score, passed, new Timestamp(scoredAt.getTime()), sourceFile);
        jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                "Result", resultId, "INGEST", "booking=" + bookingId + " score=" + score
                        + (sourceFile != null ? " file=" + sourceFile : ""), actor);
        return resultId.intValue();
    }

    /** Records a rejected conflicting result. Commits on its own, whatever the caller does next. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void auditDuplicateRejected(IngestOutcome conflict, String sourceFile, Integer lineNumber) {
        String detail = "booking=" + conflict.getBookingId()
                + " storedScore=" + conflict.getScore()
                + " submittedScore=" + conflict.getSubmittedScore()
                + " storedScoredAt=" + IngestOutcome.format(conflict.getStoredScoredAt())
                + " submittedScoredAt=" + IngestOutcome.format(conflict.getSubmittedScoredAt())
                + (sourceFile != null ? " file=" + sourceFile : "")
                + (lineNumber != null ? " line=" + lineNumber : "");
        jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                "Result", conflict.getResultId(), "DUPLICATE_REJECTED", detail, actor);
    }

    /** Most recent earlier upload of a file with this content, or null. */
    public Integer findLatestUploadId(String sha256) {
        List<Integer> ids = jdbc.queryForList(
                "SELECT TOP 1 UploadId FROM dbo.ResultUpload WHERE Sha256 = ? ORDER BY UploadId DESC",
                Integer.class, sha256);
        return ids.isEmpty() ? null : ids.get(0);
    }

    @Transactional
    public int recordUpload(UploadReport report) {
        return jdbc.queryForObject(
                "SET NOCOUNT ON; "
              + "INSERT INTO dbo.ResultUpload (FileName, Sha256, RowsRead, Imported, Duplicates, Conflicts, Errors) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                Integer.class, report.getFileName(), report.getSha256(), report.getRowsRead(), report.getImported(),
                report.getDuplicates(), report.getConflicts(), report.getSkipped());
    }
}
