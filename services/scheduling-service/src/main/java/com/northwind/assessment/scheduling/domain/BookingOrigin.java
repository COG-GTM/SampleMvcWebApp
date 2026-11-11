package com.northwind.assessment.scheduling.domain;

/** Where a booking row was created (migration integration contract, item 2). */
public enum BookingOrigin {
    LEGACY_IMPORT, MODERN;

    /** First id of the disjoint range used for bookings created by scheduling-service. */
    public static final int MODERN_ID_FLOOR = 50_000_000;

    public static BookingOrigin ofBookingId(long bookingId) {
        return bookingId >= MODERN_ID_FLOOR ? MODERN : LEGACY_IMPORT;
    }
}
