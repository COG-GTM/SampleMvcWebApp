package com.northwind.scheduling.characterization.legacy;

import com.northwind.scheduling.characterization.spi.SchedulingTarget;
import com.northwind.scheduling.characterization.spi.SchedulingTargetProvider;

/** {@code -Dtarget=legacy}. */
public final class LegacyProcTargetProvider implements SchedulingTargetProvider {
    @Override
    public String name() {
        return "legacy";
    }

    @Override
    public SchedulingTarget create() {
        return new LegacyProcTarget(LegacyDatabase.start());
    }
}
