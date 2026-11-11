package com.northwind.scheduling.domain;

import java.time.LocalDate;

/**
 * Retake decision. {@code reason} is the booking-api code (FIRST_ATTEMPT, ALREADY_PASSED,
 * RETAKE_WAIT_NOT_ELAPSED, RETAKE_ALLOWED); {@code message} is the portal wording (RetakeRules.cs).
 */
public record EligibilityDecision(boolean eligible, String reason, String message, LocalDate earliestRetakeDate) {
}
