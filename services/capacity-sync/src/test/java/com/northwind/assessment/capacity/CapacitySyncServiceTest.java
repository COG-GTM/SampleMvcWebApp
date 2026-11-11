package com.northwind.assessment.capacity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import static org.mockito.Matchers.anyVararg;
import static org.mockito.Matchers.contains;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

public class CapacitySyncServiceTest {

    @Test
    public void lastRunIsNeverRunBeforeFirstSync() {
        SyncRun last = new CapacitySyncService().getLastRun();
        assertEquals("NEVER_RUN", last.getStatus());
        assertNull(last.getStartedAt());
        assertEquals(0, last.getSessionsChecked());
    }

    /** MBA-3823: with capacity.sync.enabled=false (the default) the run writes nothing but its audit row. */
    @Test
    public void disabledSyncSkipsWritesAndAuditsTheSkip() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CapacitySyncService svc = new CapacitySyncService();
        ReflectionTestUtils.setField(svc, "jdbc", jdbc);
        ReflectionTestUtils.setField(svc, "actor", "capacity-sync");
        ReflectionTestUtils.setField(svc, "enabled", false);

        SyncRun run = svc.runSync("MANUAL");

        assertEquals("SKIPPED", run.getStatus());
        assertEquals(run.getStatus(), svc.getLastRun().getStatus());
        verify(jdbc, never()).queryForList(startsWith("SELECT s.SessionId"));
        verify(jdbc, never()).update(startsWith("UPDATE dbo.ExamSession"), (Object[]) anyVararg());
        verify(jdbc).update(startsWith("INSERT INTO dbo.AuditLog"), eq("ExamSession"), eq(0), eq("CAPACITY_SYNC"),
                contains("status=SKIPPED"), eq("capacity-sync"));
    }
}
