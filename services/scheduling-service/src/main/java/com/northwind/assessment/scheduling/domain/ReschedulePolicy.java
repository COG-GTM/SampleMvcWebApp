package com.northwind.assessment.scheduling.domain;

/**
 * db/procs/023_usp_RescheduleBooking.sql and booking-api {@code BookingService.reschedule}: a reschedule
 * is cancel + book, only to another session of the same exam. The proc has no same-session check;
 * booking-api rejects it with {@code SAME_SESSION}, which is kept (rebooking the session you hold would
 * only lose your seat or waitlist position).
 */
public final class ReschedulePolicy {

    public static final String CANCEL_REASON = "Rescheduled";

    private ReschedulePolicy() {
    }

    public static void check(int fromSessionId, int fromExamId, int toSessionId, int toExamId) {
        if (fromExamId != toExamId) {
            throw new SchedulingRuleException(RuleViolation.DIFFERENT_EXAM,
                    "Session " + toSessionId + " is for a different exam than session " + fromSessionId);
        }
        if (fromSessionId == toSessionId) {
            throw new SchedulingRuleException(RuleViolation.SAME_SESSION,
                    "Booking is already on session " + toSessionId);
        }
    }
}
