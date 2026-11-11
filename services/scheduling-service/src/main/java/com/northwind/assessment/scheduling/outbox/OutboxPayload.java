package com.northwind.assessment.scheduling.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * JSON body of an outbox event: the state scheduling-service decided, after the change, for every session and
 * booking the change touched. The relay copies it into AssessmentDelivery as-is (state transfer, not a delta),
 * so applying an event never depends on legacy rules or triggers and replaying it cannot double-count.
 */
public record OutboxPayload(String eventType, LocalDateTime occurredAt, String actor, List<SessionState> sessions,
                            List<BookingState> bookings, Map<String, Object> detail) {

    /**
     * {@code seatsAvailable} is the scheduling-service formula: max(0, capacity - BOOKED - ATTENDED - PENDING
     * offers) (ADR 0003); {@code seatsOffered} is the number of seats held by PENDING seat offers.
     */
    public record SessionState(int sessionId, int capacity, int accommodationCapacity, int seatsAvailable,
                               int seatsHeld, int accommodationSeatsUsed, String status, int seatsOffered) {
    }

    /**
     * {@code legacyBookingId} is the AssessmentDelivery id when already known (imported bookings, or modern
     * bookings relayed before); {@code waitlistPosition} null means "not on the waitlist";
     * {@code offerExpiresAt} is set while the waitlisted booking holds a PENDING seat offer (ADR 0003).
     */
    public record BookingState(long bookingId, String origin, Integer legacyBookingId, int sessionId, int candidateId,
                               String status, String accommodationCode, String channel, String externalRef,
                               LocalDateTime bookedAt, LocalDateTime cancelledAt, String cancelReason,
                               Integer waitlistPosition, LocalDateTime offerExpiresAt) {
    }
}
