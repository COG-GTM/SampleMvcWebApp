package com.northwind.assessment.scheduling.domain;

/** Which kind of seat a waitlist offer holds: ET25/ET50 candidates need an accommodation seat. */
public enum SeatKind {
    STANDARD, ACCOMMODATION;

    public static SeatKind forAccommodationCode(String code) {
        return AccommodationCodes.requiresAccommodationSeat(code) ? ACCOMMODATION : STANDARD;
    }
}
