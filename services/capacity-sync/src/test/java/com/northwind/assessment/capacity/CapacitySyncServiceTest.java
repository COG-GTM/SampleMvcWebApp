package com.northwind.assessment.capacity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class CapacitySyncServiceTest {

    @Test
    public void lastRunIsNeverRunBeforeFirstSync() {
        SyncRun last = new CapacitySyncService().getLastRun();
        assertEquals("NEVER_RUN", last.getStatus());
        assertNull(last.getStartedAt());
        assertEquals(0, last.getSessionsChecked());
    }
}
