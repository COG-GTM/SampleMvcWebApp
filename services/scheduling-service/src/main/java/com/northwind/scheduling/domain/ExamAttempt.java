package com.northwind.scheduling.domain;

import java.time.LocalDate;

/** One scored attempt (legacy Result joined to ExamSession.StartsAt). */
public record ExamAttempt(LocalDate sessionDate, boolean passed) {
}
