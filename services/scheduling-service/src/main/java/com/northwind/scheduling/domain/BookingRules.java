package com.northwind.scheduling.domain;

/**
 * Booking decision, ported from db/procs/020_usp_BookSeat.sql and booking-api BookingService.book.
 * Order of checks follows booking-api (the M1 api transcripts BK-xx/AC-xx): session OPEN (state 1),
 * duplicate active booking (state 2), extra-time accommodation seat (state 3), retake eligibility,
 * then BOOKED when a seat is free, otherwise WAITLISTED with the next position.
 * One booking always takes exactly one seat (ADR 0002 ODD-01: no trigger double decrement).
 */
public final class BookingRules {

    private BookingRules() {
    }

    public enum Outcome { BOOKED, WAITLISTED }

    public static Outcome decide(int sessionId, String candidateNumber, SessionStatus status, SessionCapacity seats,
                                 boolean candidateHasActiveBooking, String accommodationCode,
                                 EligibilityDecision eligibility, String examCode) {
        if (!status.acceptsBookings()) {
            throw new SchedulingException(ErrorCode.SESSION_NOT_OPEN, "Session " + sessionId + " is not open for booking");
        }
        if (candidateHasActiveBooking) {
            throw new SchedulingException(ErrorCode.DUPLICATE_BOOKING,
                    "Candidate " + candidateNumber + " already has an active booking on session " + sessionId);
        }
        if (!seats.hasSeatFor(accommodationCode)) {
            throw new SchedulingException(ErrorCode.NO_ACCOMMODATION_SEAT,
                    "No accommodation seat available on session " + sessionId);
        }
        if (eligibility != null && !eligibility.eligible()) {
            throw new SchedulingException(ErrorCode.NOT_ELIGIBLE, "Candidate " + candidateNumber
                    + " is not eligible to book " + examCode + " (" + eligibility.reason() + ")"
                    + (eligibility.earliestRetakeDate() == null ? ""
                            : ", earliest retake date " + eligibility.earliestRetakeDate()));
        }
        return seats.isFull() ? Outcome.WAITLISTED : Outcome.BOOKED;
    }

    /** Next waitlist position: MAX(Position) + 1 (usp_BookSeat, booking-api BookingDao.insertWaitlist). */
    public static int nextWaitlistPosition(Integer currentMaxPosition) {
        return currentMaxPosition == null ? 1 : currentMaxPosition + 1;
    }

    /**
     * Cancellation, ported from db/procs/021_usp_CancelBooking.sql: only BOOKED/WAITLISTED can be
     * cancelled (state 4). The freed seat is NOT offered to the waitlist (ODD-09 kept, ADR 0002).
     */
    public static void checkCancellable(int bookingId, BookingStatus status) {
        if (!status.isActive()) {
            throw new SchedulingException(ErrorCode.NOT_CANCELLABLE, "Booking " + bookingId + " is " + status);
        }
    }
}
