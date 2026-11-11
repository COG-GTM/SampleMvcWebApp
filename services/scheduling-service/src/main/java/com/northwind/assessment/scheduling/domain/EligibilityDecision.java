package com.northwind.assessment.scheduling.domain;

import java.time.LocalDate;

public record EligibilityDecision(boolean eligible, String reason, LocalDate earliestRetakeDate) {
}
