package com.northwind.scheduling.domain;

/**
 * Reschedule = cancel + book, same exam only. Ported from db/procs/023_usp_RescheduleBooking.sql
 * (state 6 DIFFERENT_EXAM) and booking-api BookingService.reschedule (SAME_SESSION).
 * ADR 0002: same-session moves are rejected on every channel (ODD-08), exactly one seat moves (ODD-05),
 * and the new booking keeps channel, externalRef and accommodation code of the old one (ODD-10).
 */
public final class ReschedulePolicy {

    private ReschedulePolicy() {
    }

    public static void check(int bookingId, BookingStatus currentStatus, int currentSessionId, int currentExamId,
                             int newSessionId, int newExamId) {
        if (currentExamId != newExamId) {
            throw new SchedulingException(ErrorCode.DIFFERENT_EXAM,
                    "Booking " + bookingId + " cannot be moved to session " + newSessionId + " (different exam)");
        }
        if (currentSessionId == newSessionId) {
            throw new SchedulingException(ErrorCode.SAME_SESSION,
                    "Booking " + bookingId + " is already on session " + newSessionId);
        }
        BookingRules.checkCancellable(bookingId, currentStatus);
    }

    public static final String CANCEL_REASON = "Rescheduled";
}
