package com.northwind.assessment.scheduling.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Retake eligibility. Sources: booking-api {@code RetakeEligibilityService.evaluate} and portal
 * {@code RetakeRules.cs} / {@code EligibilityService.cs}; both agree on the reasons and differ only in
 * how the wait is counted ({@link RetakeDayCounting}).
 *
 * <ul>
 *   <li>no scored attempt: eligible, {@code FIRST_ATTEMPT}</li>
 *   <li>any passed attempt: not eligible, {@code ALREADY_PASSED}</li>
 *   <li>otherwise earliest = date of the latest attempt + wait days; before it: not eligible,
 *       {@code RETAKE_WAIT_NOT_ELAPSED}; on or after it: eligible, {@code RETAKE_ALLOWED}</li>
 * </ul>
 */
public final class RetakeEligibilityPolicy {

    public static final String FIRST_ATTEMPT = "FIRST_ATTEMPT";
    public static final String ALREADY_PASSED = "ALREADY_PASSED";
    public static final String RETAKE_WAIT_NOT_ELAPSED = "RETAKE_WAIT_NOT_ELAPSED";
    public static final String RETAKE_ALLOWED = "RETAKE_ALLOWED";

    private final RetakeDayCounting dayCounting;

    public RetakeEligibilityPolicy(RetakeDayCounting dayCounting) {
        this.dayCounting = Objects.requireNonNull(dayCounting, "dayCounting");
    }

    public RetakeDayCounting dayCounting() {
        return dayCounting;
    }

    public EligibilityDecision evaluate(List<ExamAttempt> attempts, int retakeWaitDays, LocalDate onDate) {
        Objects.requireNonNull(onDate, "onDate");
        if (attempts == null || attempts.isEmpty()) {
            return new EligibilityDecision(true, FIRST_ATTEMPT, null);
        }
        if (attempts.stream().anyMatch(ExamAttempt::passed)) {
            return new EligibilityDecision(false, ALREADY_PASSED, null);
        }
        LocalDate last = attempts.stream().map(ExamAttempt::sessionDate).max(Comparator.naturalOrder()).orElseThrow();
        LocalDate earliest = dayCounting.add(last, retakeWaitDays);
        if (onDate.isBefore(earliest)) {
            return new EligibilityDecision(false, RETAKE_WAIT_NOT_ELAPSED, earliest);
        }
        return new EligibilityDecision(true, RETAKE_ALLOWED, earliest);
    }
}
