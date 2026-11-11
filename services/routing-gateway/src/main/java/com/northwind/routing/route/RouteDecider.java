package com.northwind.routing.route;

import com.northwind.routing.policy.AllowListPolicy;

import java.util.Locale;
import java.util.Set;

/**
 * Pure routing rule (MBA-3822):
 * <ol>
 *   <li>a booking id at or above {@code routing.modern-booking-id-floor} was created by scheduling-service and
 *       always goes modern, whatever the allow-list says (ownership by origin, so rollback never orphans it);</li>
 *   <li>otherwise the request goes modern only if its resolved centre is on {@code routing.modern-centers};</li>
 *   <li>anything unresolved goes to legacy.</li>
 * </ol>
 */
public final class RouteDecider {

    private RouteDecider() {
    }

    public static boolean modernOwned(Long bookingId, long floor) {
        return bookingId != null && bookingId >= floor;
    }

    public static RouteDecision decide(Set<String> allowList, String center, Long bookingId, long floor) {
        if (modernOwned(bookingId, floor)) {
            return RouteDecision.modern(center, "modern-owned booking");
        }
        if (center == null || center.isBlank()) {
            return RouteDecision.legacy(null, allowList.isEmpty() ? "allow-list empty" : "centre unresolved");
        }
        String c = center.trim().toUpperCase(Locale.ROOT);
        boolean listed = allowList.contains(AllowListPolicy.ALL) || allowList.contains(c);
        return listed ? RouteDecision.modern(c, "allow-listed") : RouteDecision.legacy(c, "not allow-listed");
    }
}
