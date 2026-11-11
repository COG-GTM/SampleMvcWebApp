package com.northwind.assessment.gateway.routing;

import java.util.Set;

/** Immutable, validated snapshot of the routing allow-list. */
public record AllowList(Set<String> modernCenters, Set<String> testCenters, String coexistenceMode) {

    public AllowList {
        modernCenters = Set.copyOf(modernCenters);
        testCenters = Set.copyOf(testCenters);
    }

    public boolean isEmpty() {
        return modernCenters.isEmpty();
    }
}
