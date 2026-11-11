package com.northwind.scheduling.characterization.legacy;

import com.northwind.scheduling.characterization.target.SchedulingTarget;
import com.northwind.scheduling.characterization.target.SchedulingTargetProvider;

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
