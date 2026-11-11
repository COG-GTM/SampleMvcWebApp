package com.northwind.scheduling.characterization.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.northwind.scheduling.characterization.spi.Model.SeatDrift;
import com.northwind.scheduling.characterization.spi.SchedulingTarget;

/**
 * Drift the invariant tolerates: the baseline of the freshly provisioned database (captured once, before any
 * scenario runs) plus drift that scenarios explicitly declare as characterized legacy behaviour (with its HB id).
 * Anything else reported by the target's seat-drift check after a test fails that test.
 */
public final class DriftLedger {

    public record Allowed(int drift, String reason) {
    }

    private static Map<Integer, Integer> baseline;
    private static final Map<Integer, Allowed> characterized = new LinkedHashMap<>();
    private static final Map<Integer, Allowed> pending = new LinkedHashMap<>();

    private DriftLedger() {
    }

    public static synchronized void captureBaseline(SchedulingTarget target) {
        if (baseline == null) {
            baseline = new TreeMap<>();
            for (SeatDrift d : target.seatDriftCheck().findDrift()) {
                baseline.put(d.sessionId(), d.drift());
            }
        }
    }

    public static synchronized Map<Integer, Integer> baseline() {
        return baseline == null ? Map.of() : Map.copyOf(baseline);
    }

    /** Declared by the running scenario; becomes permanent once its after-test check passes. */
    public static synchronized void expect(int sessionId, int drift, String reason) {
        pending.put(sessionId, new Allowed(drift, reason));
    }

    static synchronized Map<Integer, Allowed> expectedNow() {
        Map<Integer, Allowed> all = new TreeMap<>();
        baseline().forEach((k, v) -> all.put(k, new Allowed(v, "seed baseline")));
        all.putAll(characterized);
        all.putAll(pending);
        return all;
    }

    static synchronized Map<Integer, Allowed> pending() {
        return Map.copyOf(pending);
    }

    static synchronized void commitPending() {
        characterized.putAll(pending);
        pending.clear();
    }

    /** After a failed check: tolerate what is there now, so one failure does not cascade into later tests. */
    static synchronized void quarantine(Map<Integer, Integer> actual, String testName) {
        pending.clear();
        actual.forEach((k, v) -> characterized.put(k, new Allowed(v, "quarantined after failing " + testName)));
        characterized.keySet().removeIf(k -> !actual.containsKey(k) && !baseline().containsKey(k));
    }

    public static synchronized Map<Integer, Allowed> characterized() {
        return Map.copyOf(characterized);
    }
}
