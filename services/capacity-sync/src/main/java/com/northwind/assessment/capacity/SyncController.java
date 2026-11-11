package com.northwind.assessment.capacity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sync")
public class SyncController {

    @Autowired
    private CapacitySyncService syncService;

    /** Runs the re-sync now (blocking) and returns the outcome. */
    @RequestMapping(value = "/run", method = RequestMethod.POST)
    public SyncRun run() {
        return syncService.runSync("MANUAL");
    }

    @RequestMapping(value = "/last", method = RequestMethod.GET)
    public SyncRun last() {
        return syncService.getLastRun();
    }
}
