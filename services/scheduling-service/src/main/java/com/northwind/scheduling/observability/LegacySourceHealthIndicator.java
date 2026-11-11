package com.northwind.scheduling.observability;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import com.northwind.scheduling.legacy.LegacyReferenceSource;

/** Readiness component {@code legacySource}: the read-only AssessmentDelivery source (login ad_sched_import). */
@Component
public class LegacySourceHealthIndicator extends AbstractHealthIndicator {

    private final LegacyReferenceSource source;

    public LegacySourceHealthIndicator(LegacyReferenceSource source) {
        super("Legacy reference source check failed");
        this.source = source;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        source.ping();
        builder.up();
    }
}
