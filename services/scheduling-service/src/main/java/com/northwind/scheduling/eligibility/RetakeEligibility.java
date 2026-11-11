package com.northwind.scheduling.eligibility;

import com.northwind.scheduling.api.ApiException;
import com.northwind.scheduling.reference.ReferenceData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Retake eligibility with both legacy policies kept side by side (contract section 7):
 * PORTAL_CALENDAR_DAYS = AssessmentDelivery.Business/Rules/RetakeRules.cs (calendar days from the latest attempt),
 * PARTNER_WORKING_DAYS = booking-api RetakeEligibilityService (working days from the first row of a latest-first list).
 */
@Service
public class RetakeEligibility {

    public static final String PORTAL = "PORTAL_CALENDAR_DAYS";
    public static final String PARTNER = "PARTNER_WORKING_DAYS";

    public record Decision(boolean eligible, String reason, String earliestRetakeDate, String policy) {
    }

    private record Attempt(LocalDate sessionDate, boolean passed) {
    }

    private static final DateTimeFormatter PORTAL_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final JdbcTemplate jdbc;
    private final ReferenceData reference;

    public RetakeEligibility(JdbcTemplate jdbc, ReferenceData reference) {
        this.jdbc = jdbc;
        this.reference = reference;
    }

    public Decision check(String candidateNumber, String examCode, LocalDate date, String policy) {
        String p = policy == null || policy.isBlank() ? PORTAL : policy.trim().toUpperCase(Locale.ROOT);
        if (!PORTAL.equals(p) && !PARTNER.equals(p)) {
            throw ApiException.badRequest("VALIDATION", "Unknown policy " + policy);
        }
        String number = candidateNumber == null ? "" : candidateNumber.trim().toUpperCase(Locale.ROOT);
        String code = examCode == null ? "" : examCode.trim().toUpperCase(Locale.ROOT);
        int candidateId = reference.candidateIdByNumber(number).orElseThrow(() ->
                ApiException.notFound("CANDIDATE_NOT_FOUND", "Candidate number " + candidateNumber + " was not found."));
        Map<String, Object> exam = reference.exam(code).orElseThrow(() ->
                ApiException.notFound("EXAM_NOT_FOUND", "Exam " + examCode + " was not found."));
        int examId = ((Number) exam.get("ExamId")).intValue();
        int waitDays = exam.get("RetakeWaitDays") == null ? 30 : ((Number) exam.get("RetakeWaitDays")).intValue();
        // ResultDao.findAttempts order: StartsAt DESC, ResultId DESC
        List<Attempt> attempts = jdbc.query("SELECT s.StartsAt, a.Passed FROM dbo.Attempt a "
                        + "JOIN dbo.ExamSession s ON s.SessionId = a.SessionId WHERE a.CandidateId = ? AND a.ExamId = ? "
                        + "ORDER BY s.StartsAt DESC, a.ResultId DESC",
                (rs, i) -> new Attempt(rs.getTimestamp(1).toLocalDateTime().toLocalDate(), rs.getBoolean(2)),
                candidateId, examId);
        LocalDate on = date == null ? LocalDate.now() : date;
        return PORTAL.equals(p) ? portal(attempts, waitDays, on) : partner(attempts, waitDays, on);
    }

    static Decision portal(List<Attempt> attempts, int waitDays, LocalDate date) {
        if (attempts.isEmpty()) {
            return new Decision(true, "No previous attempts", null, PORTAL);
        }
        if (attempts.stream().anyMatch(Attempt::passed)) {
            return new Decision(false, "Candidate has already passed this exam", null, PORTAL);
        }
        LocalDate last = attempts.stream().map(Attempt::sessionDate).max(LocalDate::compareTo).orElseThrow();
        LocalDate earliest = last.plusDays(waitDays);
        if (date.isBefore(earliest)) {
            return new Decision(false, "Retake wait period applies until " + PORTAL_DATE.format(earliest),
                    earliest.toString(), PORTAL);
        }
        return new Decision(true, "Retake wait period has elapsed", earliest.toString(), PORTAL);
    }

    static Decision partner(List<Attempt> attempts, int waitDays, LocalDate date) {
        if (attempts.isEmpty()) {
            return new Decision(true, "FIRST_ATTEMPT", null, PARTNER);
        }
        if (attempts.stream().anyMatch(Attempt::passed)) {
            return new Decision(false, "ALREADY_PASSED", null, PARTNER);
        }
        LocalDate earliest = addWorkingDays(attempts.get(0).sessionDate(), waitDays);
        if (date.isBefore(earliest)) {
            return new Decision(false, "RETAKE_WAIT_NOT_ELAPSED", earliest.toString(), PARTNER);
        }
        return new Decision(true, "RETAKE_ALLOWED", earliest.toString(), PARTNER);
    }

    /** booking-api DateUtils.addWorkingDays: skips Saturday and Sunday, no holiday calendar. */
    static LocalDate addWorkingDays(LocalDate d, int days) {
        LocalDate c = d;
        int added = 0;
        while (added < days) {
            c = c.plusDays(1);
            if (c.getDayOfWeek() != DayOfWeek.SATURDAY && c.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return c;
    }
}
