package com.northwind.assessment.scheduling.domain;

/**
 * A waitlist position. New entries go to MAX + 1 as in legacy (usp_BookSeat). Positions are compacted only when a
 * seat offer is accepted, declined or expires (ADR 0003); a plain cancellation leaves a gap, as in legacy.
 */
public record WaitlistSlot(long bookingId, int position) {
}
