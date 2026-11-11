package com.northwind.assessment.gateway.routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Guard for {@code routing.modern-centers}. Until the outbox relay (M4) is live, writes made by scheduling-service
 * never reach {@code AssessmentDelivery}, so only test/demo centers ({@code routing.test-centers}) may be
 * allow-listed. Any other center on the list (including {@code *}) requires {@code routing.coexistence-mode=relay}.
 */
public final class AllowListPolicy {

    public static final String MODE_RELAY = "relay";

    private AllowListPolicy() {
    }

    public static Set<String> normalize(Collection<String> codes) {
        Set<String> out = new LinkedHashSet<>();
        if (codes != null) {
            for (String c : codes) {
                if (c != null && !c.isBlank()) {
                    out.add(c.trim().toUpperCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    /** @return human-readable violations; empty when the allow-list is acceptable */
    public static List<String> violations(Set<String> modernCenters, Set<String> testCenters, String coexistenceMode) {
        List<String> errors = new ArrayList<>();
        if (modernCenters.isEmpty() || MODE_RELAY.equalsIgnoreCase(trim(coexistenceMode))) {
            return errors;
        }
        Set<String> notTest = new LinkedHashSet<>(modernCenters);
        notTest.removeAll(testCenters);
        if (!notTest.isEmpty()) {
            errors.add("routing.modern-centers contains " + notTest + " which are not test/demo centers "
                    + "(routing.test-centers=" + testCenters + "); allow-listing a production center requires "
                    + "routing.coexistence-mode=relay (set by the M4 outbox relay), current mode is '"
                    + trim(coexistenceMode) + "'");
        }
        return errors;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
