package com.northwind.assessment.booking.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.northwind.assessment.booking.dao.ResultDao;
import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.model.ExamAttempt;
import com.northwind.assessment.booking.util.DateUtils;

/**
 * Retake rules (exam owner policy, 2015):
 *  - a candidate who has passed an exam may not book it again
 *  - after a fail the candidate waits Exam.RetakeWaitDays from the date of the last attempt
 */
@Service
public class RetakeEligibilityService {

    public static final String REASON_FIRST_ATTEMPT = "FIRST_ATTEMPT";
    public static final String REASON_ALREADY_PASSED = "ALREADY_PASSED";
    public static final String REASON_RETAKE_WAIT = "RETAKE_WAIT_NOT_ELAPSED";
    public static final String REASON_RETAKE_ALLOWED = "RETAKE_ALLOWED";

    @Autowired
    private ResultDao resultDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public EligibilityResult check(int candidateId, int examId, Date onDate) {
        List<ExamAttempt> attempts = resultDao.findAttempts(candidateId, examId);
        return evaluate(attempts, retakeWaitDays(examId), onDate);
    }

    /** attempts must be ordered latest first (see ResultDao). */
    EligibilityResult evaluate(List<ExamAttempt> attempts, int waitDays, Date onDate) {
        if (attempts == null || attempts.isEmpty()) {
            return new EligibilityResult(true, REASON_FIRST_ATTEMPT, null);
        }
        for (ExamAttempt a : attempts) {
            if (a.isPassed()) {
                return new EligibilityResult(false, REASON_ALREADY_PASSED, null);
            }
        }
        ExamAttempt last = attempts.get(0);
        // 30 day wait per exam owner policy
        Date earliest = DateUtils.addWorkingDays(last.getSessionDate(), waitDays);
        String earliestStr = DateUtils.formatIsoDate(earliest);
        if (DateUtils.isBefore(onDate, earliest)) {
            return new EligibilityResult(false, REASON_RETAKE_WAIT, earliestStr);
        }
        return new EligibilityResult(true, REASON_RETAKE_ALLOWED, earliestStr);
    }

    private int retakeWaitDays(int examId) {
        Integer days = jdbcTemplate.queryForObject("SELECT RetakeWaitDays FROM dbo.Exam WHERE ExamId = ?",
                Integer.class, examId);
        return days == null ? 30 : days.intValue();
    }
}
