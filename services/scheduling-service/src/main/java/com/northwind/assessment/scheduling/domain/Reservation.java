package com.northwind.assessment.scheduling.domain;

/** Outcome of {@link SessionCapacity#reserve}: BOOKED, or WAITLISTED at {@code waitlistPosition}. */
public record Reservation(BookingStatus status, Integer waitlistPosition, String accommodationCode) {

    public static Reservation booked(String accommodationCode) {
        return new Reservation(BookingStatus.BOOKED, null, accommodationCode);
    }

    public static Reservation waitlisted(int position, String accommodationCode) {
        return new Reservation(BookingStatus.WAITLISTED, position, accommodationCode);
    }
}
