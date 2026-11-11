package com.northwind.assessment.capacity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

public class CapacitySyncServiceTest {

    private static CapacitySyncService service(JdbcTemplate jdbc, boolean enabled) {
        CapacitySyncService s = new CapacitySyncService();
        ReflectionTestUtils.setField(s, "jdbc", jdbc);
        ReflectionTestUtils.setField(s, "actor", "capacity-sync");
        ReflectionTestUtils.setField(s, "enabled", enabled);
        return s;
    }

    @Test
    public void lastRunIsNeverRunBeforeFirstSync() {
        SyncRun last = new CapacitySyncService().getLastRun();
        assertEquals("NEVER_RUN", last.getStatus());
        assertNull(last.getStartedAt());
        assertEquals(0, last.getSessionsChecked());
    }

    @Test
    public void disabled_manualAndScheduledRuns_skipWithoutTouchingSeats_andAuditTheSkip() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CapacitySyncService s = service(jdbc, false);

        SyncRun manual = s.runSync("MANUAL");
        SyncRun scheduled = s.runSync("SCHEDULED");

        assertEquals("SKIPPED", manual.getStatus());
        assertEquals("SKIPPED", scheduled.getStatus());
        assertEquals(scheduled, s.getLastRun());
        verify(jdbc, never()).queryForList(anyString());
        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbc, org.mockito.Mockito.times(2)).update(eq(
                "INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)"),
                args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        List<Object> all = args.getAllValues();
        assertEquals("CAPACITY_SYNC", all.get(2));
        assertTrue(String.valueOf(all.get(3)), String.valueOf(all.get(3)).startsWith("trigger=MANUAL status=SKIPPED"));
        assertTrue(String.valueOf(all.get(3)).contains("capacity.sync.enabled=false"));
        assertTrue(String.valueOf(all.get(8)).startsWith("trigger=SCHEDULED status=SKIPPED"));
    }

    @Test
    public void enabled_recomputesAndWritesDifferingSessions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        Map<String, Object> row = new HashMap<String, Object>();
        row.put("SessionId", 7);
        row.put("Capacity", 20);
        row.put("AccommodationCapacity", 2);
        row.put("SeatsAvailable", 10);
        row.put("StandardBooked", 5);
        rows.add(row);
        when(jdbc.queryForList(anyString())).thenReturn(rows);
        when(jdbc.update(org.mockito.Matchers.startsWith("UPDATE dbo.ExamSession"), (Object[]) anyVararg())).thenReturn(1);

        SyncRun run = service(jdbc, true).runSync("MANUAL");

        assertEquals("COMPLETED", run.getStatus());
        assertEquals(1, run.getSessionsChecked());
        assertEquals(1, run.getSessionsUpdated());
        verify(jdbc).update("UPDATE dbo.ExamSession SET SeatsAvailable = ?, LastModified = GETDATE() "
                + "WHERE SessionId = ? AND Status = 'OPEN' AND SeatsAvailable = ?", new Object[] {13, 7, 10});
    }
}
