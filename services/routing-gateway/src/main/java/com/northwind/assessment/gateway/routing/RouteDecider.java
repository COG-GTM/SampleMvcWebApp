package com.northwind.assessment.gateway.routing;

import java.util.Locale;
import java.util.Set;

/**
 * The whole strangler routing rule, with no Spring or I/O in it:
 * <ol>
 *   <li>a booking created on the modern side (id in the modern range, or {@code bookingOrigin=MODERN}) always goes
 *       to modern, whatever the allow-list says, so in-flight modern bookings are never orphaned;</li>
 *   <li>otherwise a request for a center on the allow-list (or any center when it holds {@code *}) goes to modern;</li>
 *   <li>everything else (unknown center, center not on the list) goes to legacy.</li>
 * </ol>
 */
public final class RouteDecider {

    public static final long DEFAULT_MODERN_BOOKING_ID_FLOOR = 50_000_000L;
    public static final String ORIGIN_MODERN = "MODERN";
    public static final String ALL_CENTERS = "*";

    private final long modernBookingIdFloor;

    public RouteDecider() {
        this(DEFAULT_MODERN_BOOKING_ID_FLOOR);
    }

    public RouteDecider(long modernBookingIdFloor) {
        this.modernBookingIdFloor = modernBookingIdFloor;
    }

    public boolean isModernBookingId(Long bookingId) {
        return bookingId != null && bookingId >= modernBookingIdFloor;
    }

    /**
     * @param centerCode    resolved {@code TestCenter.CenterCode}, or null when it could not be resolved
     * @param allowList     {@code routing.modern-centers}, upper-case center codes or {@code *}
     * @param bookingId     booking id from the request, or null
     * @param bookingOrigin {@code bookingOrigin} from the resolve endpoint, or null
     */
    public RouteDecision decide(String centerCode, Set<String> allowList, Long bookingId, String bookingOrigin) {
        String center = centerCode == null || centerCode.isBlank() ? null : centerCode.trim().toUpperCase(Locale.ROOT);
        if (isModernBookingId(bookingId)) {
            return RouteDecision.modern(center, "modern-booking-id");
        }
        if (ORIGIN_MODERN.equalsIgnoreCase(bookingOrigin)) {
            return RouteDecision.modern(center, "modern-booking-origin");
        }
        if (center == null) {
            return RouteDecision.legacy(null, "no-center");
        }
        if (allowList != null && allowList.contains(center)) {
            return RouteDecision.modern(center, "allow-listed");
        }
        if (allowList != null && allowList.contains(ALL_CENTERS)) {
            return RouteDecision.modern(center, "all-centers");
        }
        return RouteDecision.legacy(center, "not-allow-listed");
    }
}
