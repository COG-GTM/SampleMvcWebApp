package com.northwind.scheduling.domain;

/**
 * Seat inventory of one session, derived from booking rows instead of the legacy denormalised
 * ExamSession.SeatsAvailable counter (ADR 0002: ODD-01, ODD-05, ODD-13 cannot happen by construction).
 *
 * @param capacity              total seats incl. accommodation seats (ExamSession.Capacity)
 * @param accommodationCapacity seats usable for ET25/ET50 (ExamSession.AccommodationCapacity)
 * @param occupiedSeats         BOOKED + ATTENDED bookings
 * @param accommodationUsed     BOOKED + ATTENDED bookings with ET25/ET50 (booking-api
 *                              BookingDao.countAccommodationSeatsUsed, usp_BookSeat)
 * @param waitlisted            WAITLISTED bookings
 * @param heldSeats             seats held by PENDING waitlist offers (MBA-3829, ADR 0005)
 * @param accommodationHeld     held seats whose offered booking is ET25/ET50 (holds an accommodation seat too)
 */
public record SessionCapacity(int capacity, int accommodationCapacity, int occupiedSeats, int accommodationUsed,
                              int waitlisted, int heldSeats, int accommodationHeld) {

    public SessionCapacity {
        if (capacity < 0 || accommodationCapacity < 0 || occupiedSeats < 0 || accommodationUsed < 0 || waitlisted < 0
                || heldSeats < 0 || accommodationHeld < 0) {
            throw new IllegalArgumentException("seat counts cannot be negative");
        }
    }

    /** No pending offers. */
    public SessionCapacity(int capacity, int accommodationCapacity, int occupiedSeats, int accommodationUsed,
                           int waitlisted) {
        this(capacity, accommodationCapacity, occupiedSeats, accommodationUsed, waitlisted, 0, 0);
    }

    public int seatsAvailable() {
        return SeatFormula.STANDARD.seatsAvailable(this);
    }

    public int seatsAvailable(SeatFormula formula) {
        return formula.seatsAvailable(this);
    }

    /** Free and not held by a pending offer: what a new booking or a new offer can take. */
    public int accommodationAvailable() {
        return Math.max(0, accommodationCapacity - accommodationUsed - accommodationHeld);
    }

    public boolean isFull() {
        return seatsAvailable() == 0;
    }

    /** True when an extra-time booking cannot get an accommodation seat (usp_BookSeat state 3). */
    public boolean accommodationExhausted() {
        return accommodationUsed + accommodationHeld >= accommodationCapacity;
    }

    /** False only for ET25/ET50 once every accommodation seat is taken; SR, RB and no code never need one. */
    public boolean hasSeatFor(String accommodationCode) {
        return !AccommodationCodes.isExtraTime(accommodationCode) || !accommodationExhausted();
    }
}
