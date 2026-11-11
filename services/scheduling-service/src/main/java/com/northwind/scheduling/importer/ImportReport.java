package com.northwind.scheduling.importer;

import java.time.LocalDateTime;

/** Row counts copied by one importer run. */
public record ImportReport(String mode, int centers, int rooms, int exams, int candidates, int results, int sessions,
                           int bookings, int waitlist, LocalDateTime startedAt, LocalDateTime finishedAt) {
}
