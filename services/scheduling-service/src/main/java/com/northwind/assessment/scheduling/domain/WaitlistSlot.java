package com.northwind.assessment.scheduling.domain;

/** A waitlist position. Positions are never compacted, as in legacy (usp_BookSeat uses MAX + 1). */
public record WaitlistSlot(long bookingId, int position) {
}
