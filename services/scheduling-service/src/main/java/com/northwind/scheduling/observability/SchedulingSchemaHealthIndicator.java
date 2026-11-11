package com.northwind.scheduling.observability;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Readiness component {@code schedulingSchema}: DOWN until Flyway has migrated the Scheduling DB with nothing
 * pending. State is captured once by the migration strategy, so the probe itself does not hit the database
 * ({@code db} covers reachability).
 */
public class SchedulingSchemaHealthIndicator extends AbstractHealthIndicator {

    private volatile boolean migrated;
    private volatile int pending = -1;
    private volatile String version;

    public SchedulingSchemaHealthIndicator() {
        super("Scheduling schema check failed");
    }

    void migrate(Flyway flyway) {
        flyway.migrate();
        MigrationInfo[] left = flyway.info().pending();
        MigrationInfo current = flyway.info().current();
        version = current == null || current.getVersion() == null ? null : current.getVersion().getVersion();
        pending = left.length;
        migrated = true;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (!migrated) {
            builder.down().withDetail("flyway", "migration not finished");
        } else if (pending > 0) {
            builder.down().withDetail("flyway", "pending migrations").withDetail("pending", pending)
                    .withDetail("version", String.valueOf(version));
        } else {
            builder.up().withDetail("version", String.valueOf(version));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true", matchIfMissing = true)
    static class Config {
        @Bean
        SchedulingSchemaHealthIndicator schedulingSchemaHealthIndicator() {
            return new SchedulingSchemaHealthIndicator();
        }

        @Bean
        FlywayMigrationStrategy schedulingFlywayMigrationStrategy(SchedulingSchemaHealthIndicator schema) {
            return schema::migrate;
        }
    }
}
