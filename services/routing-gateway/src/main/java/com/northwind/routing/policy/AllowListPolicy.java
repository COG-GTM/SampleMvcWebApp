package com.northwind.routing.policy;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Coexistence guard (MBA-3822). Until M4 (relay of writes between the two databases) is live, only the
 * demo/test centres in {@code routing.test-centers} may be allow-listed; any other centre is refused unless
 * {@code routing.coexistence-mode=relay}. See services/routing-gateway/README.md.
 */
public final class AllowListPolicy {

    public static final String RELAY = "relay";

    private AllowListPolicy() {
    }

    /** Normalised (trimmed, upper-case, de-duplicated) allow-list, or {@link IllegalStateException} if not permitted. */
    public static Set<String> validate(Collection<String> modernCenters, Collection<String> testCenters, String coexistenceMode) {
        Set<String> listed = normalise(modernCenters);
        if (listed.isEmpty() || RELAY.equalsIgnoreCase(trim(coexistenceMode))) {
            return Set.copyOf(listed);
        }
        Set<String> allowed = normalise(testCenters);
        Set<String> refused = new LinkedHashSet<>(listed);
        refused.removeAll(allowed);
        if (!refused.isEmpty()) {
            throw new IllegalStateException("routing.modern-centers contains " + refused
                    + " which are not test/demo centres " + allowed
                    + "; only test centres may be allow-listed until M4 sets routing.coexistence-mode=relay");
        }
        return Set.copyOf(listed);
    }

    static Set<String> normalise(Collection<String> codes) {
        Set<String> out = new LinkedHashSet<>();
        if (codes != null) {
            for (String c : codes) {
                String t = trim(c);
                if (!t.isEmpty()) {
                    out.add(t.toUpperCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
