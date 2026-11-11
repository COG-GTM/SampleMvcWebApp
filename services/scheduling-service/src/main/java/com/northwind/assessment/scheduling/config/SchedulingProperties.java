package com.northwind.assessment.scheduling.config;

import com.northwind.assessment.scheduling.domain.AccommodationFloorPolicy;
import com.northwind.assessment.scheduling.domain.RetakeDayCounting;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties("scheduling")
public record SchedulingProperties(Datasource datasource, Legacy legacy, @Name("import") Import importer, Rules rules) {

    public SchedulingProperties {
        datasource = datasource == null ? new Datasource(true) : datasource;
        legacy = legacy == null ? new Legacy(null, null, null) : legacy;
        importer = importer == null ? new Import(false) : importer;
        rules = rules == null ? new Rules(null, null) : rules;
    }

    public record Datasource(boolean createDatabase) {
    }

    /** Read-only connection to AssessmentDelivery used by the snapshot importer only. */
    public record Legacy(String url, String username, String password) {
    }

    public record Import(boolean onStartup) {
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
