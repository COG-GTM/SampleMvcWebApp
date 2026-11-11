package com.northwind.assessment.gateway.routing;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable, validated snapshot of the routing allow-list. {@code modernWriteCenters} are routed to modern for
 * writes only (reads keep rendering from legacy); {@code *} in either list matches every center.
 */
public record AllowList(Set<String> modernCenters, Set<String> modernWriteCenters, Set<String> testCenters,
                        String coexistenceMode) {

    public AllowList {
        modernCenters = Set.copyOf(modernCenters);
        modernWriteCenters = Set.copyOf(modernWriteCenters);
        testCenters = Set.copyOf(testCenters);
    }

    /** Centers routed to modern for a read ({@code modern-centers}) or a write (plus {@code modern-write-centers}). */
    public Set<String> centersFor(boolean write) {
        if (!write || modernWriteCenters.isEmpty()) {
            return modernCenters;
        }
        Set<String> all = new LinkedHashSet<>(modernCenters);
        all.addAll(modernWriteCenters);
        return all;
    }

    /** Every booking write goes to scheduling-service ({@code *} in either list): its rules decide every booking. */
    public boolean allWritesModern() {
        return modernCenters.contains(RouteDecider.ALL_CENTERS) || modernWriteCenters.contains(RouteDecider.ALL_CENTERS);
    }

    public boolean isEmpty() {
        return modernCenters.isEmpty() && modernWriteCenters.isEmpty();
    }
}
