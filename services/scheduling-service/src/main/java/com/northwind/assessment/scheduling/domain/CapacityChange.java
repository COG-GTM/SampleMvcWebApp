package com.northwind.assessment.scheduling.domain;

import java.util.List;

/** Result of {@link SessionCapacity#changeCapacity}: bookings moved to the waitlist, in bump order. */
public record CapacityChange(int oldCapacity, int newCapacity, List<WaitlistSlot> bumped) {

    public CapacityChange {
        bumped = List.copyOf(bumped);
    }

    public List<Long> bumpedBookingIds() {
        return bumped.stream().map(WaitlistSlot::bookingId).toList();
    }
}
