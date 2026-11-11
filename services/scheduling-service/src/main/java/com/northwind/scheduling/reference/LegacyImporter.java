package com.northwind.scheduling.reference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Initial import from AssessmentDelivery: reference replicas, then ExamSession / Booking / Waitlist / the
 * Booking+ExamSession AuditLog rows, keeping the legacy identity values, and Result rows as eligibility attempts.
 * Runs at startup per scheduling.import.on-startup (if-empty | always | never).
 */
@Component
public class LegacyImporter implements SmartInitializingSingleton {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyImporter.class);

    public record ImportResult(int sessions, int bookings, int waitlistRows, int auditRows, int attempts, int referenceRows) {
    }

    private record Copy(String table, String identity, String select, String columns) {
    }

    private static final List<Copy> COPIES = List.of(
            new Copy("ExamSession", "SessionId",
                    "SELECT SessionId, ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, "
                            + "Status, LastModified FROM dbo.ExamSession ORDER BY SessionId",
                    "SessionId, ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, Status, LastModified"),
            new Copy("Booking", "BookingId",
                    "SELECT BookingId, SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef, BookedAt, CancelledAt, "
                            + "CancelReason, LastModified FROM dbo.Booking ORDER BY BookingId",
                    "BookingId, SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef, BookedAt, CancelledAt, CancelReason, LastModified"),
            new Copy("Waitlist", "WaitlistId",
                    "SELECT WaitlistId, SessionId, CandidateId, BookingId, Position, AddedAt FROM dbo.Waitlist ORDER BY WaitlistId",
                    "WaitlistId, SessionId, CandidateId, BookingId, Position, AddedAt"),
            new Copy("AuditLog", "AuditId",
                    "SELECT AuditId, EntityName, EntityId, Action, Detail, Actor, LoggedAt FROM dbo.AuditLog "
                            + "WHERE EntityName IN ('Booking','ExamSession') ORDER BY AuditId",
                    "AuditId, EntityName, EntityId, Action, Detail, Actor, LoggedAt"),
            new Copy("Attempt", null,
                    "SELECT ResultId, BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt FROM dbo.Result ORDER BY ResultId",
                    "ResultId, BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SourceDatabase source;
    private final ReferenceData reference;
    private final String mode;

    public LegacyImporter(JdbcTemplate jdbc, TransactionTemplate tx, SourceDatabase source, ReferenceData reference,
                          @Value("${scheduling.import.on-startup:if-empty}") String mode) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.source = source;
        this.reference = reference;
        this.mode = mode;
    }

    /** Runs before the web server starts, so no request can allocate identities the import is about to copy. */
    @Override
    public void afterSingletonsInstantiated() {
        if ("never".equalsIgnoreCase(mode)) {
            return;
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.ExamSession", Integer.class);
        if ("if-empty".equalsIgnoreCase(mode) && existing != null && existing > 0) {
            LOG.info("Scheduling database already holds {} sessions; initial import skipped", existing);
            return;
        }
        try {
            ImportResult r = importAll("always".equalsIgnoreCase(mode));
            LOG.info("Initial import from AssessmentDelivery: {}", r);
        } catch (RuntimeException e) {
            LOG.error("Initial import from AssessmentDelivery failed: {}", e.getMessage(), e);
        }
    }

    public synchronized ImportResult importAll(boolean replace) {
        int refRows = reference.resyncAll();
        JdbcTemplate src = source.jdbc();
        int[] counts = new int[COPIES.size()];
        tx.executeWithoutResult(s -> {
            if (replace) {
                for (String t : List.of("Waitlist", "Booking", "ExamSession", "AuditLog", "Attempt", "ReconcilerRun")) {
                    jdbc.update("DELETE FROM dbo." + t);
                }
            }
            for (int i = 0; i < COPIES.size(); i++) {
                counts[i] = copy(src, COPIES.get(i));
            }
            seedReconcilerRuns(src);
            jdbc.update("INSERT INTO dbo.ImportRun (Sessions, Bookings, WaitlistRows, AuditRows, Attempts) VALUES (?, ?, ?, ?, ?)",
                    counts[0], counts[1], counts[2], counts[3], counts[4]);
        });
        return new ImportResult(counts[0], counts[1], counts[2], counts[3], counts[4], refRows);
    }

    private int copy(JdbcTemplate src, Copy c) {
        List<Map<String, Object>> rows = src.queryForList(c.select());
        if (rows.isEmpty()) {
            return 0;
        }
        String[] cols = c.columns().split(",\\s*");
        String sql = "INSERT INTO dbo." + c.table() + " (" + c.columns() + ") VALUES ("
                + String.join(", ", java.util.Collections.nCopies(cols.length, "?")) + ")";
        List<Object[]> batch = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Object[] a = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) {
                a[i] = r.get(cols[i]);
            }
            batch.add(a);
        }
        if (c.identity() != null) {
            jdbc.execute("SET IDENTITY_INSERT dbo." + c.table() + " ON");
        }
        try {
            for (int from = 0; from < batch.size(); from += 1000) {
                jdbc.batchUpdate(sql, batch.subList(from, Math.min(batch.size(), from + 1000)));
            }
        } finally {
            if (c.identity() != null) {
                jdbc.execute("SET IDENTITY_INSERT dbo." + c.table() + " OFF");
            }
        }
        return rows.size();
    }

    private static final java.util.regex.Pattern SYNC_DETAIL = java.util.regex.Pattern.compile(
            "trigger=(\\S+) status=(\\S+) checked=(\\d+) updated=(\\d+)");

    /**
     * Pre-cutover capacity-sync runs exist only as CAPACITY_SYNC audit rows in {@code legacy}; rebuild run history from
     * them. Runs already present (same trigger, start time, counts, actor) are skipped, so re-imports are idempotent
     * and service-generated runs never block the legacy history.
     */
    public int seedReconcilerRuns(JdbcTemplate legacy) {
        List<Object[]> runs = new ArrayList<>();
        for (Map<String, Object> a : legacy.queryForList("SELECT Detail, Actor, LoggedAt FROM dbo.AuditLog "
                + "WHERE EntityName = 'ExamSession' AND Action = 'CAPACITY_SYNC' ORDER BY AuditId")) {
            java.util.regex.Matcher m = SYNC_DETAIL.matcher(String.valueOf(a.get("Detail")));
            if (!m.find()) {
                continue;
            }
            String status = "COMPLETED".equals(m.group(2)) ? "OK" : m.group(2);
            runs.add(new Object[] {m.group(1), status.length() > 10 ? status.substring(0, 10) : status, a.get("LoggedAt"),
                    a.get("LoggedAt"), Integer.valueOf(m.group(3)), Integer.valueOf(m.group(4)), a.get("Actor")});
        }
        int seeded = 0;
        for (Object[] r : runs) {
            seeded += jdbc.update("INSERT INTO dbo.ReconcilerRun (TriggerType, Status, StartedAt, FinishedAt, Checked, Updated, "
                    + "Skipped, Actor) SELECT ?, ?, ?, ?, ?, ?, 0, ? WHERE NOT EXISTS (SELECT 1 FROM dbo.ReconcilerRun x "
                    + "WHERE x.TriggerType = ? AND x.StartedAt = CAST(? AS DATETIME) AND x.Checked = ? AND x.Updated = ? "
                    + "AND ISNULL(x.Actor, '') = ISNULL(?, ''))",
                    r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[0], r[2], r[4], r[5], r[6]);
        }
        LOG.info("Seeded {} reconciler runs from legacy CAPACITY_SYNC audit rows", seeded);
        return seeded;
    }
}
