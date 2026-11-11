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
 */
public record SessionCapacity(int capacity, int accommodationCapacity, int occupiedSeats, int accommodationUsed,
                              int waitlisted) {

    public SessionCapacity {
        if (capacity < 0 || accommodationCapacity < 0 || occupiedSeats < 0 || accommodationUsed < 0 || waitlisted < 0) {
            throw new IllegalArgumentException("seat counts cannot be negative");
        }
    }

    public int seatsAvailable() {
        return SeatFormula.STANDARD.seatsAvailable(this);
    }

    public int seatsAvailable(SeatFormula formula) {
        return formula.seatsAvailable(this);
    }

    public int accommodationAvailable() {
        return Math.max(0, accommodationCapacity - accommodationUsed);
    }

    public boolean isFull() {
        return seatsAvailable() == 0;
    }

    /** True when an extra-time booking cannot get an accommodation seat (usp_BookSeat state 3). */
    public boolean accommodationExhausted() {
        return accommodationUsed >= accommodationCapacity;
    }

    /** False only for ET25/ET50 once every accommodation seat is taken; SR, RB and no code never need one. */
    public boolean hasSeatFor(String accommodationCode) {
        return !AccommodationCodes.isExtraTime(accommodationCode) || !accommodationExhausted();
    }
}
