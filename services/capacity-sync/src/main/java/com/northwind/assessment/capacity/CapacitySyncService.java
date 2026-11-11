package com.northwind.assessment.capacity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Recomputes SeatsAvailable for every OPEN session and writes back the ones that differ.
 *
 * Written 2016 after the "phantom full sessions" incident; runs nightly and can be
 * kicked off by hand from /sync/run.
 *
 * MBA-3823: disabled by default ({@code capacity.sync.enabled=false}). scheduling-service is the only writer of
 * seat counts and this login is denied UPDATE on ExamSession.SeatsAvailable (db/security/040_single_writer.sql);
 * drift is now detected by scheduling-service's reconciliation report. A disabled run only records its skip.
 */
@Service
public class CapacitySyncService {

    private static final Logger LOG = LoggerFactory.getLogger(CapacitySyncService.class);
    private static final String TS = "yyyy-MM-dd'T'HH:mm:ss";

    // accommodation seats are held back for ET candidates, so only standard
    // bookings are counted against the general pool
    private static final String SELECT_OPEN_SESSIONS =
            "SELECT s.SessionId, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, "
          + "       (SELECT COUNT(*) FROM dbo.Booking b "
          + "         WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED') "
          + "           AND (b.AccommodationCode IS NULL OR b.AccommodationCode NOT IN ('ET25','ET50'))) AS StandardBooked "
          + "FROM dbo.ExamSession s WHERE s.Status = 'OPEN' ORDER BY s.SessionId";

    @Autowired
    private JdbcTemplate jdbc;

    @Value("${capacity.sync.audit-actor:capacity-sync}")
    private String actor;

    @Value("${capacity.sync.enabled:false}")
    private boolean enabled;

    private volatile SyncRun lastRun = new SyncRun();

    public synchronized SyncRun runSync(String trigger) {
        SyncRun run = new SyncRun();
        run.setTrigger(trigger);
        run.setStatus("RUNNING");
        run.setStartedAt(new SimpleDateFormat(TS).format(new Date()));
        if (!enabled) {
            run.setStatus("SKIPPED");
            run.setMessage("capacity.sync.enabled=false: scheduling-service owns SeatsAvailable, see GET /internal/reconciliation");
            run.setFinishedAt(run.getStartedAt());
            audit(run);
            lastRun = run;
            LOG.info("Capacity sync ({}) skipped: capacity.sync.enabled=false", trigger);
            return run;
        }
        try {
            List<Map<String, Object>> sessions = jdbc.queryForList(SELECT_OPEN_SESSIONS);
            int updated = 0;
            for (Map<String, Object> s : sessions) {
                int sessionId = ((Number) s.get("SessionId")).intValue();
                int capacity = ((Number) s.get("Capacity")).intValue();
                int accCap = ((Number) s.get("AccommodationCapacity")).intValue();
                int current = ((Number) s.get("SeatsAvailable")).intValue();
                int booked = ((Number) s.get("StandardBooked")).intValue();

                int expected = capacity - accCap - booked;
                if (expected < 0) {
                    expected = 0;
                }
                if (expected != current) {
                    // only if nobody booked/cancelled since we read the row, otherwise next run picks it up
                    int n = jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = ?, LastModified = GETDATE() "
                            + "WHERE SessionId = ? AND Status = 'OPEN' AND SeatsAvailable = ?",
                            expected, sessionId, current);
                    if (n == 1) {
                        LOG.debug("Session {} SeatsAvailable {} -> {}", sessionId, current, expected);
                        updated++;
                    }
                }
            }
            run.setSessionsChecked(sessions.size());
            run.setSessionsUpdated(updated);
            run.setStatus("COMPLETED");
        } catch (RuntimeException e) {
            LOG.error("Capacity sync failed", e);
            run.setStatus("FAILED");
            run.setMessage(e.getMessage());
        }
        run.setFinishedAt(new SimpleDateFormat(TS).format(new Date()));
        audit(run);
        lastRun = run;
        LOG.info("Capacity sync ({}) {}: checked={} updated={}", trigger, run.getStatus(),
                run.getSessionsChecked(), run.getSessionsUpdated());
        return run;
    }

    public SyncRun getLastRun() {
        return lastRun;
    }

    private void audit(SyncRun run) {
        try {
            jdbc.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                    "ExamSession", 0, "CAPACITY_SYNC",
                    "trigger=" + run.getTrigger() + " status=" + run.getStatus() + " checked=" + run.getSessionsChecked()
                            + " updated=" + run.getSessionsUpdated()
                            + (run.getMessage() != null ? " reason=" + run.getMessage() : ""), actor);
        } catch (RuntimeException e) {
            LOG.warn("Could not write audit row for capacity sync: {}", e.getMessage());
        }
    }
}
