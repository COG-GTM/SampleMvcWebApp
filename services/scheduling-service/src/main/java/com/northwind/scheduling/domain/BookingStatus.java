package com.northwind.scheduling.domain;

/** Booking.Status values (legacy CK_Booking_Status, docs/SYSTEM_OVERVIEW.md). */
public enum BookingStatus {
    BOOKED, WAITLISTED, CANCELLED, ATTENDED;

    /** Holds a seat: BOOKED and ATTENDED (booking-api SeatCounter, SeatCounter.cs). */
    public boolean occupiesSeat() {
        return this == BOOKED || this == ATTENDED;
    }

    /** Blocks a second booking / can be cancelled: BOOKED and WAITLISTED (usp_BookSeat, usp_CancelBooking). */
    public boolean isActive() {
        return this == BOOKED || this == WAITLISTED;
    }
}
