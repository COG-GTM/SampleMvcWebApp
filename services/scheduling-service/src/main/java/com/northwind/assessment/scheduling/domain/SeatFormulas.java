package com.northwind.assessment.scheduling.domain;

import java.util.Collection;

/**
 * The two seat formulas found in the legacy estate (ADR 0002, "two seat formulas").
 * scheduling-service uses {@link #seatsAvailable} only; {@link #capacitySyncSeatsAvailable} is kept
 * so the difference stays documented and tested.
 */
public final class SeatFormulas {

    private SeatFormulas() {
    }

    /** Seats held = BOOKED + ATTENDED (booking-api / portal {@code SeatCounter}, db/checks/seat-drift.sql). */
    public static int seatsHeld(Collection<BookingSlot> bookings) {
        return (int) bookings.stream().filter(b -> b.status().holdsSeat()).count();
    }

    /** ET25/ET50 bookings holding a seat (usp_BookSeat, booking-api {@code countAccommodationSeatsUsed}). */
    public static int accommodationSeatsUsed(Collection<BookingSlot> bookings) {
        return (int) bookings.stream().filter(BookingSlot::holdsAccommodationSeat).count();
    }

    /**
     * Formula A (procs, seed 049, seat-drift check): {@code Capacity - (BOOKED + ATTENDED)}, never below 0.
     * Accommodation seats are part of capacity.
     */
    public static int seatsAvailable(int capacity, Collection<BookingSlot> bookings) {
        return Math.max(0, capacity - seatsHeld(bookings));
    }

    /**
     * Formula A with waitlist auto-offer (ADR 0003): a PENDING seat offer holds its seat, so
     * {@code Capacity - (BOOKED + ATTENDED) - PENDING offers}, never below 0.
     */
    public static int seatsAvailable(int capacity, Collection<BookingSlot> bookings, int pendingOffers) {
        return Math.max(0, capacity - seatsHeld(bookings) - pendingOffers);
    }

    /** Formula A from a pre-computed count of seats held. */
    public static int seatsAvailable(int capacity, int seatsHeld) {
        return Math.max(0, capacity - seatsHeld);
    }

    /** Formula A from pre-computed counts of seats held and pending seat offers (ADR 0003). */
    public static int seatsAvailable(int capacity, int seatsHeld, int pendingOffers) {
        return Math.max(0, capacity - seatsHeld - pendingOffers);
    }

    /**
     * Formula B (services/capacity-sync {@code CapacitySyncService}): accommodation seats are carved out
     * of capacity and only standard (non ET) bookings are subtracted, floored at 0.
     */
    public static int capacitySyncSeatsAvailable(int capacity, int accommodationCapacity, Collection<BookingSlot> bookings) {
        int standardHeld = seatsHeld(bookings) - accommodationSeatsUsed(bookings);
        return Math.max(0, capacity - accommodationCapacity - standardHeld);
    }
}
