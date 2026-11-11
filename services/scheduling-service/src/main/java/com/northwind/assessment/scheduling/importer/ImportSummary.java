package com.northwind.assessment.scheduling.importer;

public record ImportSummary(String centerCode, int testCenters, int rooms, int exams, int candidates, int results,
                            int sessionsImported, int sessionsSkippedLocallyModified, int bookings, int waitlistEntries,
                            long durationMs) {
}
