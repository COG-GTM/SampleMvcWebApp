package com.northwind.scheduling.characterization.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/** Selects the target from {@code -Dtarget=<name>} (default {@code legacy}); one instance per JVM. */
public final class SchedulingTargets {

    public static final String PROPERTY = "target";
    private static SchedulingTarget current;

    private SchedulingTargets() {
    }

    public static synchronized SchedulingTarget current() {
        if (current == null) {
            String wanted = System.getProperty(PROPERTY, "legacy").trim();
            List<String> known = new ArrayList<>();
            for (SchedulingTargetProvider p : ServiceLoader.load(SchedulingTargetProvider.class)) {
                known.add(p.name());
                if (p.name().equalsIgnoreCase(wanted)) {
                    current = p.create();
                }
            }
            if (current == null) {
                throw new IllegalStateException("No SchedulingTargetProvider named '" + wanted + "'; known: " + known);
            }
            SchedulingTarget t = current;
            Runtime.getRuntime().addShutdownHook(new Thread(t::close, "scheduling-target-close"));
        }
        return current;
    }
}
