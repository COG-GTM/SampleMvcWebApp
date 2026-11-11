package com.northwind.scheduling.characterization.spi;

/**
 * ServiceLoader SPI ({@code META-INF/services/com.northwind.scheduling.characterization.spi.SchedulingTargetProvider}).
 * To add a target: implement {@link SchedulingTarget}, add a provider whose {@link #name()} is the value of
 * {@code -Dtarget=...}, and register it in the services file.
 */
public interface SchedulingTargetProvider {

    String name();

    SchedulingTarget create();
}
