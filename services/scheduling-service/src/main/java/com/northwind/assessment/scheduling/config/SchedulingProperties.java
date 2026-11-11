package com.northwind.assessment.scheduling.config;

import com.northwind.assessment.scheduling.domain.AccommodationFloorPolicy;
import com.northwind.assessment.scheduling.domain.RetakeDayCounting;
import com.northwind.assessment.scheduling.domain.WaitlistOfferPolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties("scheduling")
public record SchedulingProperties(Datasource datasource, Legacy legacy, @Name("import") Import importer, Rules rules,
                                   Relay relay, Reconciliation reconciliation, Waitlist waitlist) {

    public SchedulingProperties {
        datasource = datasource == null ? new Datasource(true) : datasource;
        legacy = legacy == null ? new Legacy(null, null, null) : legacy;
        importer = importer == null ? new Import(false) : importer;
        rules = rules == null ? new Rules(null, null) : rules;
        relay = relay == null ? new Relay(false, null, null, null, 200, 100) : relay;
        reconciliation = reconciliation == null ? new Reconciliation(null) : reconciliation;
        waitlist = waitlist == null ? new Waitlist(null, true, 0) : waitlist;
    }

    /**
     * Waitlist auto-offer (MBA-3829, ADR 0003): how long a seat offer stays PENDING (ISO-8601, default PT24H) and
     * the expiry job (on by default, every {@code expiryPollMs}).
     */
    public record Waitlist(Duration offerTtl, boolean expiryEnabled, long expiryPollMs) {
        public Waitlist {
            offerTtl = offerTtl == null ? WaitlistOfferPolicy.DEFAULT_TTL : offerTtl;
            expiryPollMs = expiryPollMs <= 0 ? 60_000 : expiryPollMs;
        }
    }

    public record Datasource(boolean createDatabase) {
    }

    /** Read-only connection to AssessmentDelivery used by the snapshot importer only. */
    public record Legacy(String url, String username, String password) {
    }

    public record Import(boolean onStartup) {
    }

    /**
     * Outbox relay (MBA-3823): login ad_sched_relay to AssessmentDelivery, the only legacy writer of bookings,
     * waitlist and seat columns (db/security/040_single_writer.sql).
     */
    public record Relay(boolean enabled, String url, String username, String password, long pollIntervalMs, int batchSize) {
        public Relay {
            pollIntervalMs = pollIntervalMs <= 0 ? 200 : pollIntervalMs;
            batchSize = batchSize <= 0 ? 100 : batchSize;
        }
    }

    /** Where {@code reconcile} writes {@code reconciliation-<timestamp>.md}. */
    public record Reconciliation(String reportDir) {
        public Reconciliation {
            reportDir = reportDir == null || reportDir.isBlank() ? "reports" : reportDir;
        }
    }

    public record Rules(String retakeDays, String accommodationFloor) {

        public RetakeDayCounting retakeDayCounting() {
            return "working".equalsIgnoreCase(retakeDays) ? RetakeDayCounting.WORKING_DAYS : RetakeDayCounting.CALENDAR;
        }

        public AccommodationFloorPolicy accommodationFloorPolicy() {
            return "booked-extra-time".equalsIgnoreCase(accommodationFloor)
                    ? AccommodationFloorPolicy.BOOKED_EXTRA_TIME : AccommodationFloorPolicy.ALLOCATED;
        }
    }
}
