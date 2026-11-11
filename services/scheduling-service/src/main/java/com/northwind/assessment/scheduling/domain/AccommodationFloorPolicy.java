package com.northwind.assessment.scheduling.domain;

/**
 * How far capacity may be lowered relative to the accommodation seats (ADR 0002, "accommodation floor").
 */
public enum AccommodationFloorPolicy {

    /**
     * db/procs/022_usp_ChangeCapacity.sql on assessment-delivery-demo: reject a new capacity below the
     * allocated {@code AccommodationCapacity} (RAISERROR state 5). Default. Bookings held above the new
     * capacity do not raise the floor: trg_ExamSession_Update moves the newest BOOKED ones to the waitlist.
     */
    ALLOCATED {
        @Override
        public void check(int newCapacity, int accommodationCapacity, int accommodationSeatsUsed) {
            if (newCapacity < accommodationCapacity) {
                throw new SchedulingRuleException(RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR,
                        "New capacity " + newCapacity + " is below the accommodation capacity " + accommodationCapacity);
            }
        }
    },

    /**
     * Pending PR #51: only reject a new capacity below the ET25/ET50 seats already held; the
     * allocation itself may exceed capacity.
     */
    BOOKED_EXTRA_TIME {
        @Override
        public void check(int newCapacity, int accommodationCapacity, int accommodationSeatsUsed) {
            if (newCapacity < accommodationSeatsUsed) {
                throw new SchedulingRuleException(RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR,
                        "New capacity " + newCapacity + " is below the " + accommodationSeatsUsed
                                + " accommodation seats already booked");
            }
        }
    };

    public abstract void check(int newCapacity, int accommodationCapacity, int accommodationSeatsUsed);
}
