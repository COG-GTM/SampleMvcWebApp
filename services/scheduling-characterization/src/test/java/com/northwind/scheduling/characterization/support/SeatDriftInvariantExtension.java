package com.northwind.scheduling.characterization.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.northwind.scheduling.characterization.spi.Model.SeatDrift;
import com.northwind.scheduling.characterization.spi.SchedulingTarget;
import com.northwind.scheduling.characterization.spi.SchedulingTargets;

/**
 * Runs the target's seat-drift check (legacy: db/checks/seat-drift.sql) after every test and fails the test unless
 * every reported row is exactly the seed baseline or drift a scenario declared via
 * {@link CharacterizationTest#expectDrift(int, int, String)}; declared drift that does not appear also fails.
 */
public final class SeatDriftInvariantExtension implements BeforeAllCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) {
        DriftLedger.captureBaseline(SchedulingTargets.current());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        SchedulingTarget target = SchedulingTargets.current();
        Map<Integer, Integer> actual = new TreeMap<>();
        for (SeatDrift d : target.seatDriftCheck().findDrift()) {
            actual.put(d.sessionId(), d.drift());
        }
        Map<Integer, DriftLedger.Allowed> expected = DriftLedger.expectedNow();
        List<String> problems = new ArrayList<>();
        Set<Integer> ids = new TreeSet<>(actual.keySet());
        ids.addAll(expected.keySet());
        for (int id : ids) {
            Integer a = actual.get(id);
            DriftLedger.Allowed e = expected.get(id);
            if (e == null) {
                problems.add("session " + id + ": unexpected drift " + a);
            } else if (a == null) {
                problems.add("session " + id + ": expected drift " + e.drift() + " (" + e.reason() + ") but none reported");
            } else if (a != e.drift()) {
                problems.add("session " + id + ": drift " + a + ", expected " + e.drift() + " (" + e.reason() + ")");
            }
        }
        if (problems.isEmpty() || context.getExecutionException().isPresent()) {
            if (problems.isEmpty()) {
                DriftLedger.commitPending();
            } else {
                DriftLedger.quarantine(actual, context.getDisplayName());
            }
            return;
        }
        DriftLedger.quarantine(actual, context.getDisplayName());
        throw new AssertionError("Seat-drift invariant (" + target.seatDriftCheck().description() + ") violated after "
                + context.getDisplayName() + ":\n  " + String.join("\n  ", problems));
    }
}
