package com.northwind.scheduling.reconciliation;

import java.util.List;

/** Result of comparing Scheduling with AssessmentDelivery (MBA-3823). */
public record Reconciliation(String generatedAt, int sessionsCompared, int bookingsCompared,
                             List<SessionMismatch> sessionMismatches, List<BookingMismatch> bookingMismatches,
                             List<SeatDriftRow> seatDrift, String reportFile) {

    public int mismatches() {
        return sessionMismatches.size() + bookingMismatches.size();
    }

    /** one row per differing field: capacity, seatsAvailable, booked, attended, waitlisted, accommodationUsed */
    public record SessionMismatch(int sessionId, String field, Integer scheduling, Integer legacy) {
    }

    /** status/session differences; missing side = null */
    public record BookingMismatch(int bookingId, Integer schedulingSessionId, String schedulingStatus,
                                  String schedulingAccommodation, Integer legacySessionId, String legacyStatus,
                                  String legacyAccommodation) {
    }

    /** db/checks/seat-drift.sql row */
    public record SeatDriftRow(int sessionId, String examCode, String centerCode, String startsAt, int capacity,
                               int seatsAvailable, int activeBookings, int drift) {
    }

    public record SessionCounts(int capacity, int seatsAvailable, int booked, int attended, int waitlisted,
                                int accommodationUsed) {
    }
}
