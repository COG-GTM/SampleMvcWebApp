package com.northwind.assessment.booking.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.northwind.assessment.booking.model.ExamAttempt;

/**
 * Read-only access to Result for retake checks. Results are written by results-service.
 */
@Repository
public class ResultDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** All scored attempts of a candidate on an exam, latest session first. */
    public List<ExamAttempt> findAttempts(int candidateId, int examId) {
        return jdbcTemplate.query(
                "SELECT r.ResultId, r.ExamId, s.StartsAt, r.Score, r.Passed "
              + "FROM dbo.Result r JOIN dbo.ExamSession s ON s.SessionId = r.SessionId "
              + "WHERE r.CandidateId = ? AND r.ExamId = ? "
              + "ORDER BY s.StartsAt DESC, r.ResultId DESC",
                new RowMapper<ExamAttempt>() {
                    public ExamAttempt mapRow(ResultSet rs, int rowNum) throws SQLException {
                        ExamAttempt a = new ExamAttempt();
                        a.setResultId(rs.getInt("ResultId"));
                        a.setExamId(rs.getInt("ExamId"));
                        a.setSessionDate(rs.getTimestamp("StartsAt"));
                        a.setScore(rs.getInt("Score"));
                        a.setPassed(rs.getBoolean("Passed"));
                        return a;
                    }
                }, candidateId, examId);
    }
}
