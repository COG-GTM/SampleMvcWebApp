package com.northwind.assessment.booking.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.northwind.assessment.booking.model.Candidate;

@Repository
public class CandidateDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Candidate findByNumber(String candidateNumber) {
        List<Candidate> list = jdbcTemplate.query(
                "SELECT CandidateId, CandidateNumber, FirstName, LastName, DefaultAccommodationCode "
              + "FROM dbo.Candidate WHERE CandidateNumber = ?",
                new RowMapper<Candidate>() {
                    public Candidate mapRow(ResultSet rs, int rowNum) throws SQLException {
                        Candidate c = new Candidate();
                        c.setCandidateId(rs.getInt("CandidateId"));
                        c.setCandidateNumber(rs.getString("CandidateNumber"));
                        c.setFirstName(rs.getString("FirstName"));
                        c.setLastName(rs.getString("LastName"));
                        c.setDefaultAccommodationCode(rs.getString("DefaultAccommodationCode"));
                        return c;
                    }
                }, candidateNumber);
        return list.isEmpty() ? null : list.get(0);
    }

    public Integer findExamIdByCode(String examCode) {
        List<Integer> ids = jdbcTemplate.queryForList("SELECT ExamId FROM dbo.Exam WHERE ExamCode = ?",
                Integer.class, examCode);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
