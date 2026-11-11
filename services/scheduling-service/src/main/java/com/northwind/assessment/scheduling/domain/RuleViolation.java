package com.northwind.assessment.scheduling.domain;

/**
 * A broken scheduling rule. {@link #errorCode()} is the booking-api v1 error code for the same
 * condition and {@link #legacyCode()} the matching {@code usp_*} RAISERROR state (50000 + state).
 */
public enum RuleViolation {
    SESSION_NOT_OPEN(Kind.CONFLICT, 50001),
    DUPLICATE_BOOKING(Kind.CONFLICT, 50002),
    NO_ACCOMMODATION_SEAT(Kind.CONFLICT, 50003),
    NOT_CANCELLABLE(Kind.CONFLICT, 50004),
    CAPACITY_BELOW_ACCOMMODATION_FLOOR(Kind.CONFLICT, 50005),
    DIFFERENT_EXAM(Kind.CONFLICT, 50006),
    SAME_SESSION(Kind.CONFLICT, null),
    NOT_ELIGIBLE(Kind.CONFLICT, null),
    BOOKING_NOT_ACTIVE(Kind.CONFLICT, null),
    OFFER_NOT_PENDING(Kind.CONFLICT, null),
    OFFER_EXPIRED(Kind.CONFLICT, null),
    VALIDATION(Kind.INVALID, null);

    public enum Kind { INVALID, CONFLICT }

    private final Kind kind;
    private final Integer legacyCode;

    RuleViolation(Kind kind, Integer legacyCode) {
        this.kind = kind;
        this.legacyCode = legacyCode;
    }

    public String errorCode() {
        return name();
    }

    public Kind kind() {
        return kind;
    }

    public Integer legacyCode() {
        return legacyCode;
    }
}
