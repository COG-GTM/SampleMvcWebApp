package com.northwind.scheduling.domain;

/**
 * Error vocabulary of the scheduling API. Codes are the booking-api ones (ApiException in
 * services/booking-api) so partner callers see the same names; {@link #legacyState()} is the
 * RAISERROR state the portal procs use (all procs raise 50000, the state carries the code - ODD-11),
 * reported as {@code legacyCode = 50000 + state} (e.g. NO_ACCOMMODATION_SEAT = 50003). ADR 0002, ODD-11/12.
 */
public enum ErrorCode {
    VALIDATION(400, 0),
    BAD_REQUEST(400, 0),
    /** MBA-3823: attendance without a valid X-Service-Token. */
    UNAUTHORIZED(401, 0),
    CANDIDATE_NOT_FOUND(404, 0),
    SESSION_NOT_FOUND(404, 0),
    BOOKING_NOT_FOUND(404, 0),
    EXAM_NOT_FOUND(404, 0),
    CENTER_NOT_FOUND(404, 0),
    /** usp_BookSeat state 1. */
    SESSION_NOT_OPEN(409, 1),
    /** usp_BookSeat state 2. */
    DUPLICATE_BOOKING(409, 2),
    /** usp_BookSeat state 3 (legacy 50003). */
    NO_ACCOMMODATION_SEAT(409, 3),
    /** usp_CancelBooking state 4. */
    NOT_CANCELLABLE(409, 4),
    /** usp_ChangeCapacity state 5. */
    INVALID_CAPACITY(409, 5),
    /** usp_RescheduleBooking state 6. */
    DIFFERENT_EXAM(409, 6),
    /** booking-api only (ODD-08). */
    SAME_SESSION(409, 0),
    /** booking-api RetakeEligibilityService / portal EligibilityService. */
    NOT_ELIGIBLE(409, 0),
    /** MBA-3829 waitlist offers (OfferError schema, kept out of ApiError like UNAUTHORIZED). */
    OFFER_NOT_FOUND(404, 0),
    /** The offer is no longer PENDING (accepted, declined, expired or withdrawn), or its expiry has passed. */
    OFFER_NOT_PENDING(409, 0);

    private final int httpStatus;
    private final int legacyState;

    ErrorCode(int httpStatus, int legacyState) {
        this.httpStatus = httpStatus;
        this.legacyState = legacyState;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public int legacyState() {
        return legacyState;
    }

    /** 50000 + proc state, or null when the rule never existed as a proc error. */
    public Integer legacyCode() {
        return legacyState == 0 ? null : 50000 + legacyState;
    }
}
