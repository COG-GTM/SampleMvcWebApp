package com.northwind.assessment.scheduling.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/** The part of a booking the seat rules care about. */
public record BookingSlot(long bookingId, int candidateId, BookingStatus status, String accommodationCode,
                          LocalDateTime bookedAt) {

    public BookingSlot {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(bookedAt, "bookedAt");
        accommodationCode = AccommodationCodes.normalize(accommodationCode);
    }

    public boolean holdsAccommodationSeat() {
        return status.holdsSeat() && AccommodationCodes.requiresAccommodationSeat(accommodationCode);
    }

    BookingSlot withStatus(BookingStatus newStatus) {
        return new BookingSlot(bookingId, candidateId, newStatus, accommodationCode, bookedAt);
    }
}
