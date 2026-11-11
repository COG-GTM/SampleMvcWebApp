package com.northwind.assessment.capacity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NightlySyncJob {

    @Autowired
    private CapacitySyncService syncService;

    // 02:00 server time, after the portal's 01:00 session close-out
    @Scheduled(cron = "0 0 2 * * *")
    public void nightly() {
        syncService.runSync("SCHEDULED");
    }
}
