package com.northwind.scheduling.core;

import java.util.Set;

public final class Channels {
    public static final String PORTAL = "PORTAL";
    public static final String PARTNER = "PARTNER";
    public static final String IMPORT = "IMPORT";
    public static final Set<String> ALL = Set.of(PORTAL, PARTNER, IMPORT);
    /** Codes counted against ExamSession.AccommodationCapacity (020_usp_BookSeat.sql:44-52). */
    public static final Set<String> EXTRA_TIME = Set.of("ET25", "ET50");
    /** booking-api ACCOMMODATION_CODES. */
    public static final Set<String> ACCOMMODATION_CODES = Set.of("ET25", "ET50", "SR", "RB");

    private Channels() {
    }
}
