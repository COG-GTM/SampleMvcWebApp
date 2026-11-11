package com.northwind.assessment.scheduling.domain;

import java.time.LocalDate;
import java.util.Objects;

/** A scored sitting of an exam: date of the session (not of scoring) and whether it passed. */
public record ExamAttempt(LocalDate sessionDate, boolean passed) {
    public ExamAttempt {
        Objects.requireNonNull(sessionDate, "sessionDate");
    }
}
