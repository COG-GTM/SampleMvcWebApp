package com.northwind.scheduling.domain;

import java.util.Locale;

/**
 * The two seat formulas found in the legacy estate (ODD-03, HB-05/HB-06):
 * <ul>
 * <li>{@link #STANDARD}: Capacity - (BOOKED + ATTENDED), the formula of db/procs/024 seat-drift,
 * booking-api SeatCounter and SeatCounter.cs. Chosen by ADR 0002.</li>
 * <li>{@link #CAPACITY_SYNC}: Capacity - AccommodationCapacity - active standard (non ET) bookings,
 * floored at 0, from services/capacity-sync CapacitySyncService. Kept only for comparison.</li>
 * </ul>
 */
public enum SeatFormula {
    STANDARD, CAPACITY_SYNC;

    public static SeatFormula parse(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    public int seatsAvailable(SessionCapacity s) {
        return switch (this) {
            case STANDARD -> Math.max(0, s.capacity() - s.occupiedSeats());
            case CAPACITY_SYNC -> Math.max(0,
                    s.capacity() - s.accommodationCapacity() - (s.occupiedSeats() - s.accommodationUsed()));
        };
    }
}
