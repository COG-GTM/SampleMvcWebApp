package com.northwind.scheduling.reconciler;

import com.northwind.scheduling.core.Audit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Internal seat reconciler: services/capacity-sync CapacitySyncService.runSync absorbed (contract section 6).
 * With scheduling.legacy-seat-accounting=true it keeps capacity-sync's formula,
 * max(0, Capacity - AccommodationCapacity - standard BOOKED/ATTENDED bookings) on OPEN sessions (HB-03, pinned by the
 * characterization suite). With the runtime default (false) HB-03 is intentionally removed: every session is set to
 * the seat-drift.sql definition, Capacity - (BOOKED + ATTENDED), so a run always converges to zero drift.
 * Both modes keep the compare-and-set update, so a booking that lands between read and write wins and the next
 * run picks the session up. Every run, including failures, gets a ReconcilerRun row and a CAPACITY_SYNC audit row.
 */
@Service
public class SeatReconciler {

    private static final Logger LOG = LoggerFactory.getLogger(SeatReconciler.class);

    public record Run(int runId, String trigger, String status, LocalDateTime startedAt, LocalDateTime finishedAt,
                      int checked, int updated, int skipped, String error, String actor) {
    }

    public static final class AlreadyRunning extends RuntimeException {
        AlreadyRunning() {
            super("A reconciler run is already in progress");
        }
    }

    private static final String SELECT_OPEN = "SELECT s.SessionId, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, "
            + "(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED') "
            + "AND (b.AccommodationCode IS NULL OR b.AccommodationCode NOT IN ('ET25','ET50'))) AS StandardBooked "
            + "FROM dbo.ExamSession s WHERE s.Status = 'OPEN'";

    private static final String SELECT_ALL = "SELECT s.SessionId, s.Capacity, s.SeatsAvailable, "
            + "(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) "
            + "AS ActiveBookings FROM dbo.ExamSession s WHERE 1 = 1";

    private final JdbcTemplate jdbc;
    private final Audit audit;
    private final String actor;
    private final boolean legacyFormula;
    private final AtomicBoolean running = new AtomicBoolean();

    public SeatReconciler(JdbcTemplate jdbc, Audit audit, @Value("${scheduling.reconciler.actor:capacity-sync}") String actor,
                          @Value("${scheduling.legacy-seat-accounting:false}") boolean legacyFormula) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.actor = actor;
        this.legacyFormula = legacyFormula;
    }

    @Scheduled(cron = "${scheduling.reconciler.cron:-}")
    public void scheduled() {
        try {
            run("SCHEDULED", null, null);
        } catch (AlreadyRunning e) {
            LOG.info("Scheduled reconciler run skipped: {}", e.getMessage());
        }
    }

    /**
     * @param sessionIds restrict the run to these sessions (null = all OPEN sessions)
     * @param auditActor overrides the configured actor for the audit row (null = configured)
     */
    public Run run(String trigger, Collection<Integer> sessionIds, String auditActor) {
        if (!running.compareAndSet(false, true)) {
            throw new AlreadyRunning();
        }
        try {
            return doRun(trigger, sessionIds, auditActor == null ? actor : auditActor);
        } finally {
            running.set(false);
        }
    }

    private Run doRun(String trigger, Collection<Integer> sessionIds, String auditActor) {
        LocalDateTime started = LocalDateTime.now();
        int checked = 0;
        int updated = 0;
        int skipped = 0;
        String status = "OK";
        String error = null;
        try {
            String sql = legacyFormula ? SELECT_OPEN : SELECT_ALL;
            if (sessionIds != null) {
                if (sessionIds.isEmpty()) {
                    sql += " AND 1 = 0";
                } else {
                    sql += " AND s.SessionId IN (" + sessionIds.stream().map(id -> Integer.toString(id))
                            .collect(Collectors.joining(",")) + ")";
                }
            }
            List<Map<String, Object>> sessions = jdbc.queryForList(sql + " ORDER BY s.SessionId");
            for (Map<String, Object> s : sessions) {
                int sessionId = num(s, "SessionId");
                int current = num(s, "SeatsAvailable");
                int expected = legacyFormula
                        ? Math.max(0, num(s, "Capacity") - num(s, "AccommodationCapacity") - num(s, "StandardBooked"))
                        : num(s, "Capacity") - num(s, "ActiveBookings");
                if (expected != current) {
                    int n = jdbc.update("UPDATE dbo.ExamSession SET SeatsAvailable = ?, LastModified = GETDATE() "
                            + "WHERE SessionId = ? AND SeatsAvailable = ?" + (legacyFormula ? " AND Status = 'OPEN'" : ""),
                            expected, sessionId, current);
                    if (n == 1) {
                        updated++;
                    } else {
                        skipped++;
                    }
                }
            }
            checked = sessions.size();
        } catch (RuntimeException e) {
            LOG.error("Seat reconciler run failed", e);
            status = "FAILED";
            error = e.getMessage();
        }
        LocalDateTime finished = LocalDateTime.now();
        try {
            audit.write("ExamSession", 0, "CAPACITY_SYNC", "trigger=" + trigger + " status="
                    + ("OK".equals(status) ? "COMPLETED" : status) + " checked=" + checked + " updated=" + updated, auditActor);
        } catch (RuntimeException e) {
            LOG.warn("Could not write audit row for reconciler run: {}", e.getMessage());
        }
        int runId = jdbc.queryForObject("INSERT INTO dbo.ReconcilerRun (TriggerType, Status, StartedAt, FinishedAt, Checked, "
                + "Updated, Skipped, Error, Actor) OUTPUT INSERTED.RunId VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Integer.class,
                trigger, status, started, finished, checked, updated, skipped, error, auditActor);
        LOG.info("Seat reconciler ({}) {}: checked={} updated={} skipped={}", trigger, status, checked, updated, skipped);
        return new Run(runId, trigger, status, started, finished, checked, updated, skipped, error, auditActor);
    }

    public List<Run> runs(LocalDateTime from, LocalDateTime to, int max) {
        return jdbc.query("SELECT TOP (?) RunId, TriggerType, Status, StartedAt, FinishedAt, Checked, Updated, Skipped, Error, Actor "
                        + "FROM dbo.ReconcilerRun WHERE (? IS NULL OR StartedAt >= ?) AND (? IS NULL OR StartedAt < ?) "
                        + "ORDER BY RunId DESC",
                (rs, i) -> new Run(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4).toLocalDateTime(),
                        rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toLocalDateTime(),
                        rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getString(9), rs.getString(10)),
                max, from, from, to, to);
    }

    private static int num(Map<String, Object> m, String k) {
        return ((Number) m.get(k)).intValue();
    }
}
