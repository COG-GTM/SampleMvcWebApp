package com.northwind.scheduling.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** SCHEDULING_IMPORT_ON_STARTUP=true: MERGE import once the app is up; a missing legacy DB only logs. */
@Component
@ConditionalOnProperty(name = "scheduling.import.on-startup", havingValue = "true")
public class StartupImport implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupImport.class);
    private final SnapshotImporter importer;

    public StartupImport(SnapshotImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            importer.run(SnapshotImporter.Mode.MERGE);
        } catch (RuntimeException e) {
            log.warn("Start-up snapshot import failed (service keeps running, retry with "
                    + "POST /api/v2/internal/import/sessions): {}", e.getMessage());
        }
    }
}
