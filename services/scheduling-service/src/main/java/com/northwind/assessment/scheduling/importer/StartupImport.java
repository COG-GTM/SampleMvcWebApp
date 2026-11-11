package com.northwind.assessment.scheduling.importer;

import com.northwind.assessment.scheduling.config.SchedulingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** {@code scheduling.import.on-startup=true}: import every center once the context is up. */
@Component
class StartupImport implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupImport.class);

    private final SchedulingProperties properties;
    private final SnapshotImporter importer;

    StartupImport(SchedulingProperties properties, SnapshotImporter importer) {
        this.properties = properties;
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.importer().onStartup()) {
            return;
        }
        try {
            importer.importSessions(null);
        } catch (RuntimeException e) {
            // keep serving what is already in Scheduling; POST /internal/import/sessions retries
            log.error("Startup import from AssessmentDelivery failed: {}", e.getMessage(), e);
        }
    }
}
