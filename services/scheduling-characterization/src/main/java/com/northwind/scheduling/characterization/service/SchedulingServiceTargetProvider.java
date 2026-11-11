package com.northwind.scheduling.characterization.service;

import com.northwind.scheduling.characterization.spi.SchedulingTarget;
import com.northwind.scheduling.characterization.spi.SchedulingTargetProvider;

/** {@code -Dtarget=scheduling-service}: the suite against services/scheduling-service over its HTTP API. */
public final class SchedulingServiceTargetProvider implements SchedulingTargetProvider {

    @Override
    public String name() {
        return "scheduling-service";
    }

    @Override
    public SchedulingTarget create() {
        return SchedulingServiceTarget.start();
    }
}
