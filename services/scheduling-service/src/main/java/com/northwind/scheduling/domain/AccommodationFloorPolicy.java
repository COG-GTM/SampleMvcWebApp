package com.northwind.scheduling.domain;

import java.util.Locale;

/**
 * How far capacity may be lowered relative to accommodation seats (ODD-06, HB-08).
 * <ul>
 * <li>{@link #ALLOCATED} (default): capacity may not go below the session's AccommodationCapacity,
 * exactly db/procs/022_usp_ChangeCapacity.sql (state 5) - transcripts CP-05/CP-06.</li>
 * <li>{@link #BOOKED}: capacity may go down to the extra-time seats actually in use; the
 * accommodation allocation is clamped to the new capacity. Opt-in, not legacy behaviour.</li>
 * </ul>
 */
public enum AccommodationFloorPolicy {
    ALLOCATED, BOOKED;

    public static AccommodationFloorPolicy parse(String value) {
        if (value == null || value.isBlank()) {
            return ALLOCATED;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public int minimumCapacity(SessionCapacity s) {
        return this == ALLOCATED ? s.accommodationCapacity() : s.accommodationUsed();
    }

    public int accommodationCapacityAfter(SessionCapacity s, int newCapacity) {
        return this == ALLOCATED ? s.accommodationCapacity() : Math.min(s.accommodationCapacity(), newCapacity);
    }
}
