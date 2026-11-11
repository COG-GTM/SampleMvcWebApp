package com.northwind.scheduling.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Retake eligibility, ported from booking-api RetakeEligibilityService.evaluate and
 * AssessmentDelivery.Business/Rules/RetakeRules.cs (identical apart from day counting, ODD-04):
 * no attempts = eligible; any pass = blocked; otherwise blocked until last attempt + wait days.
 */
public final class RetakeEligibilityPolicy {
    public static final String FIRST_ATTEMPT = "FIRST_ATTEMPT";
    public static final String ALREADY_PASSED = "ALREADY_PASSED";
    public static final String RETAKE_WAIT_NOT_ELAPSED = "RETAKE_WAIT_NOT_ELAPSED";
    public static final String RETAKE_ALLOWED = "RETAKE_ALLOWED";

    private static final DateTimeFormatter PORTAL_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final RetakeDayCounting counting;

    public RetakeEligibilityPolicy(RetakeDayCounting counting) {
        this.counting = counting;
    }

    public RetakeDayCounting counting() {
        return counting;
    }

    public EligibilityDecision evaluate(List<ExamAttempt> attempts, int waitDays, LocalDate onDate) {
        if (attempts == null || attempts.isEmpty()) {
            return new EligibilityDecision(true, FIRST_ATTEMPT, "No previous attempts", null);
        }
        if (attempts.stream().anyMatch(ExamAttempt::passed)) {
            return new EligibilityDecision(false, ALREADY_PASSED, "Candidate has already passed this exam", null);
        }
        LocalDate last = attempts.stream().map(ExamAttempt::sessionDate).max(Comparator.naturalOrder()).orElseThrow();
        LocalDate earliest = counting.earliestRetake(last, waitDays);
        if (onDate.isBefore(earliest)) {
            return new EligibilityDecision(false, RETAKE_WAIT_NOT_ELAPSED,
                    "Retake wait period applies until " + PORTAL_DATE.format(earliest), earliest);
        }
        return new EligibilityDecision(true, RETAKE_ALLOWED, "Retake wait period has elapsed", earliest);
    }
}
