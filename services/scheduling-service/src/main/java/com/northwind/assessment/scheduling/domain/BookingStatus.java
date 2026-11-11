package com.northwind.assessment.scheduling.domain;

/** Legacy {@code CK_Booking_Status}. */
public enum BookingStatus {
    BOOKED, WAITLISTED, CANCELLED, ATTENDED;

    /**
     * BOOKED and ATTENDED hold a seat (db/checks/seat-drift.sql, booking-api {@code SeatCounter},
     * portal {@code SeatCounter.HoldsSeat}).
     */
    public boolean holdsSeat() {
        return this == BOOKED || this == ATTENDED;
    }

    /** "Active" for the duplicate rule and for cancellation (usp_BookSeat, usp_CancelBooking). */
    public boolean isActive() {
        return this == BOOKED || this == WAITLISTED;
    }
}
