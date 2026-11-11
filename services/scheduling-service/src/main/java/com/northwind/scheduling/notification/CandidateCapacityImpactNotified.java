package com.northwind.scheduling.notification;

import java.time.LocalDateTime;
import java.util.UUID;

import com.northwind.scheduling.domain.CapacityImpact;

/**
 * Outbox event CandidateCapacityImpactNotified (MBA-3831): one per candidate affected by
 * {@code PUT /api/v2/sessions/{sessionId}/capacity}. Schema and examples: docs/events.md,
 * docs/schemas/candidate-capacity-impact-notified.schema.json.
 *
 * @param impact STATUS_CHANGED (the booking's status changed with the capacity change) or SEAT_CONFIRMED
 *               (capacity was reduced, the booking stays BOOKED)
 */
public record CandidateCapacityImpactNotified(UUID eventId, int sessionId, String centerCode, String examCode,
                                              LocalDateTime startsAt, int oldCapacity, int newCapacity,
                                              String candidateNumber, int bookingId, String previousStatus,
                                              String newStatus, CapacityImpact.Kind impact) {
}
