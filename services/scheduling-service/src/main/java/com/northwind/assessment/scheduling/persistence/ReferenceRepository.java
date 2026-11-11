package com.northwind.assessment.scheduling.persistence;

import com.northwind.assessment.scheduling.domain.ExamAttempt;
import com.northwind.assessment.scheduling.persistence.Views.CandidateView;
import com.northwind.assessment.scheduling.persistence.Views.CenterView;
import com.northwind.assessment.scheduling.persistence.Views.ExamView;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads of the reference snapshots. */
@Repository
public class ReferenceRepository {

    private static final String CENTER_COLS =
            "SELECT center_id, center_code, name, city, region, time_zone, is_active AS active FROM test_center_snapshot ";
    private static final String EXAM_COLS =
            "SELECT exam_id, exam_code, title, duration_minutes, retake_wait_days FROM exam_snapshot ";
    private static final String CANDIDATE_COLS =
            "SELECT candidate_id, candidate_number, first_name, last_name, default_accommodation_code FROM candidate_snapshot ";

    private final JdbcClient jdbc;

    public ReferenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CenterView> centers() {
        return jdbc.sql(CENTER_COLS + "ORDER BY center_code").query(CenterView.class).list();
    }

    public Optional<CenterView> centerByCode(String centerCode) {
        return jdbc.sql(CENTER_COLS + "WHERE center_code = ?").param(centerCode).query(CenterView.class).optional();
    }

    public Optional<CenterView> centerById(int centerId) {
        return jdbc.sql(CENTER_COLS + "WHERE center_id = ?").param(centerId).query(CenterView.class).optional();
    }

    public List<ExamView> exams() {
        return jdbc.sql(EXAM_COLS + "WHERE is_published = 1 ORDER BY exam_code").query(ExamView.class).list();
    }

    public Optional<ExamView> examByCode(String examCode) {
        return jdbc.sql(EXAM_COLS + "WHERE exam_code = ?").param(examCode).query(ExamView.class).optional();
    }

    public Optional<ExamView> examById(int examId) {
        return jdbc.sql(EXAM_COLS + "WHERE exam_id = ?").param(examId).query(ExamView.class).optional();
    }

    public Optional<CandidateView> candidateByNumber(String candidateNumber) {
        return jdbc.sql(CANDIDATE_COLS + "WHERE candidate_number = ?").param(candidateNumber)
                .query(CandidateView.class).optional();
    }

    /** Scored attempts of a candidate on an exam (exam_result_snapshot). */
    public List<ExamAttempt> attempts(int candidateId, int examId) {
        return jdbc.sql("SELECT session_starts_at, passed FROM exam_result_snapshot WHERE candidate_id = ? AND exam_id = ?")
                .params(candidateId, examId)
                .query((rs, n) -> new ExamAttempt(rs.getTimestamp(1).toLocalDateTime().toLocalDate(), rs.getBoolean(2)))
                .list();
    }
}
