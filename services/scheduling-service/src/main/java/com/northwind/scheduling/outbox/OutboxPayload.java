package com.northwind.scheduling.outbox;

import java.util.List;

/** JSON body of outbox_event.payload: resulting state, not a delta (timestamps are ISO local date-times). */
public record OutboxPayload(String type, String detail, List<BookingState> bookings, List<SessionState> sessions) {

    public OutboxPayload {
        bookings = bookings == null ? List.of() : List.copyOf(bookings);
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
    }

    /** waitlistPosition null = no waitlist row for this booking */
    public record BookingState(int bookingId, int sessionId, int candidateId, String status, String accommodationCode,
                               String channel, String externalRef, String bookedAt, String cancelledAt,
                               String cancelReason, Integer waitlistPosition, String waitlistAddedAt) {
    }

    public record SessionState(int sessionId, int capacity, int accommodationCapacity, int seatsAvailable) {
    }
}
