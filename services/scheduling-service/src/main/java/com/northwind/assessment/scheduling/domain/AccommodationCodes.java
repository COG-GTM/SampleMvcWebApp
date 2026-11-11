package com.northwind.assessment.scheduling.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Accommodation codes. Source: portal {@code AssessmentDelivery.Business/Rules/AccommodationCodes.cs},
 * db/schema/010_tables.sql ({@code Booking.AccommodationCode}), usp_BookSeat.
 * ET25/ET50 (extra time) need an accommodation seat; SR (separate room) and RB (rest breaks) do not.
 */
public final class AccommodationCodes {

    public static final String EXTRA_TIME_25 = "ET25";
    public static final String EXTRA_TIME_50 = "ET50";
    public static final String SEPARATE_ROOM = "SR";
    public static final String REST_BREAKS = "RB";

    public static final Set<String> ALL = Set.of(EXTRA_TIME_25, EXTRA_TIME_50, SEPARATE_ROOM, REST_BREAKS);

    private AccommodationCodes() {
    }

    /** Trims and upper-cases; blank becomes {@code null} (booking-api {@code normalizeAccommodation}). */
    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isKnown(String normalizedCode) {
        return normalizedCode == null || ALL.contains(normalizedCode);
    }

    public static boolean requiresAccommodationSeat(String code) {
        String c = normalize(code);
        return EXTRA_TIME_25.equals(c) || EXTRA_TIME_50.equals(c);
    }
}
