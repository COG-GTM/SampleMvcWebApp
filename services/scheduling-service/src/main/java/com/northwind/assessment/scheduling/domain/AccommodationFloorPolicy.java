package com.northwind.assessment.scheduling.domain;

/**
 * How far capacity may be lowered relative to the accommodation seats (ADR 0002, "accommodation floor").
 */
public enum AccommodationFloorPolicy {

    /**
     * db/procs/022_usp_ChangeCapacity.sql on assessment-delivery-demo: reject a new capacity below the
     * allocated {@code AccommodationCapacity} (RAISERROR state 5). Default.
     *
     * <p>On a session with an accommodation allocation, the allocated seats stay reserved for ET25/ET50
     * candidates, so a reduction may not eat into them with standard bookings: the floor is the allocation
     * plus the standard (non ET25/ET50) seats already held.
     */
    ALLOCATED {
        @Override
        public void check(int newCapacity, int accommodationCapacity, int accommodationSeatsUsed, int seatsHeld) {
            int floor = accommodationCapacity;
            if (accommodationCapacity > 0) {
                int standardSeatsHeld = seatsHeld - accommodationSeatsUsed;
                floor += standardSeatsHeld;
            }
            if (newCapacity < floor) {
                throw new SchedulingRuleException(RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR,
                        "New capacity " + newCapacity + " is below the accommodation floor " + floor);
            }
        }
    },

    /**
     * Pending PR #51: only reject a new capacity below the ET25/ET50 seats already held; the
     * allocation itself may exceed capacity.
     */
    BOOKED_EXTRA_TIME {
        @Override
        public void check(int newCapacity, int accommodationCapacity, int accommodationSeatsUsed, int seatsHeld) {
            if (newCapacity < accommodationSeatsUsed) {
                throw new SchedulingRuleException(RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR,
                        "New capacity " + newCapacity + " is below the " + accommodationSeatsUsed
                                + " accommodation seats already booked");
            }
        }
    };

    public abstract void check(int newCapacity, int accommodationCapacity, int accommodationSeatsUsed, int seatsHeld);
}
