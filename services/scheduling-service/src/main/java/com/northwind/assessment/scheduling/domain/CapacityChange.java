package com.northwind.assessment.scheduling.domain;

import java.util.List;

/**
 * Result of {@link SessionCapacity#changeCapacity}: bookings moved to the waitlist, in bump order, and pending
 * seat offers withdrawn because the reduced capacity no longer has a seat for them.
 */
public record CapacityChange(int oldCapacity, int newCapacity, List<WaitlistSlot> bumped,
                             List<PendingOffer> withdrawnOffers) {

    public CapacityChange {
        bumped = List.copyOf(bumped);
        withdrawnOffers = List.copyOf(withdrawnOffers);
    }

    public CapacityChange(int oldCapacity, int newCapacity, List<WaitlistSlot> bumped) {
        this(oldCapacity, newCapacity, bumped, List.of());
    }

    public List<Long> bumpedBookingIds() {
        return bumped.stream().map(WaitlistSlot::bookingId).toList();
    }
}
