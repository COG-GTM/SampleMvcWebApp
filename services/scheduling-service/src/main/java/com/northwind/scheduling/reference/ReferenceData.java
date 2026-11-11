package com.northwind.scheduling.reference;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reference-data replicas and the Change Tracking relay (contract section 5).
 *
 * PUT /reference/{entity}/{id} and the relay share {@link #upsert}: last-writer-wins by sourceVersion, so replays
 * and out-of-order delivery are harmless. The relay polls CHANGETABLE(CHANGES ...) on AssessmentDelivery; when there
 * is no saved version, the saved version is older than CHANGE_TRACKING_MIN_VALID_VERSION, or change tracking is not
 * enabled, it falls back to a full resync of the table.
 */
@Service
public class ReferenceData {

    private static final Logger LOG = LoggerFactory.getLogger(ReferenceData.class);

    public record Upsert(long sourceVersion, boolean deleted, Map<String, Object> data) {
    }

    public record Status(String entity, int rows, Long lastAppliedVersion, LocalDateTime lastAppliedAt, Long lagSeconds,
                         boolean stale) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SourceDatabase source;
    private final TaskScheduler scheduler;
    private final long pollMs;
    private final long staleAfterSeconds;

    public ReferenceData(JdbcTemplate jdbc, TransactionTemplate tx, SourceDatabase source, TaskScheduler scheduler,
                         @Value("${scheduling.reference.poll-ms:30000}") long pollMs,
                         @Value("${scheduling.reference.stale-after-seconds:300}") long staleAfterSeconds) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.source = source;
        this.scheduler = scheduler;
        this.pollMs = pollMs;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    @PostConstruct
    void schedule() {
        if (pollMs > 0) {
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    catchUp();
                } catch (RuntimeException e) {
                    LOG.warn("Reference relay poll failed: {}", e.getMessage());
                }
            }, Duration.ofMillis(pollMs));
        }
    }

    public boolean candidateExists(int candidateId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.RefCandidate WHERE CandidateId = ? AND Deleted = 0",
                Integer.class, candidateId);
        return n != null && n > 0;
    }

    public Optional<Integer> candidateIdByNumber(String number) {
        List<Integer> ids = jdbc.queryForList("SELECT CandidateId FROM dbo.RefCandidate WHERE CandidateNumber = ? AND Deleted = 0",
                Integer.class, number);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public Optional<Map<String, Object>> exam(String examCode) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT ExamId, RetakeWaitDays FROM dbo.RefExam WHERE ExamCode = ? AND Deleted = 0",
                examCode);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    // ------------------------------------------------------------------ upsert / read

    /** @return true when applied, false when ignored because a newer sourceVersion is already stored. */
    public boolean upsert(RefEntity e, int id, Upsert u) {
        return tx.execute(s -> apply(e, id, u, false));
    }

    private boolean apply(RefEntity e, int id, Upsert u, boolean force) {
        if (u.deleted()) {
            int n = jdbc.update("UPDATE dbo." + e.replicaTable + " SET Deleted = 1, SourceVersion = ?, AppliedAt = GETDATE() "
                    + "WHERE " + e.key + " = ? AND (? = 1 OR SourceVersion <= ?)", u.sourceVersion(), id, force, u.sourceVersion());
            if (n == 0 && !exists(e, id)) {
                n = jdbc.update("INSERT INTO dbo." + e.replicaTable + " (" + e.key + ", SourceVersion, Deleted) VALUES (?, ?, 1)",
                        id, u.sourceVersion());
            }
            return n > 0;
        }
        List<Object> args = new ArrayList<>();
        StringBuilder set = new StringBuilder();
        StringBuilder vals = new StringBuilder();
        for (RefEntity.Column c : e.columns) {
            set.append("t.").append(c.name()).append(" = ?, ");
            args.add(value(c, u.data()));
        }
        args.add(u.sourceVersion());
        List<Object> insertArgs = new ArrayList<>();
        insertArgs.add(id);
        for (RefEntity.Column c : e.columns) {
            vals.append(", ?");
            insertArgs.add(value(c, u.data()));
        }
        insertArgs.add(u.sourceVersion());
        String sql = "MERGE dbo." + e.replicaTable + " AS t USING (SELECT ? AS Id) AS s ON t." + e.key + " = s.Id "
                + "WHEN MATCHED AND (? = 1 OR t.SourceVersion <= ?) THEN UPDATE SET " + set
                + "t.SourceVersion = ?, t.Deleted = 0, t.AppliedAt = GETDATE() "
                + "WHEN NOT MATCHED THEN INSERT (" + e.key + ", " + e.columnList() + ", SourceVersion, Deleted) "
                + "VALUES (?" + vals + ", ?, 0);";
        List<Object> all = new ArrayList<>();
        all.add(id);
        all.add(force);
        all.add(u.sourceVersion());
        all.addAll(args);
        all.addAll(insertArgs);
        return jdbc.update(sql, all.toArray()) > 0;
    }

    private boolean exists(RefEntity e, int id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM dbo." + e.replicaTable + " WHERE " + e.key + " = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private static Object value(RefEntity.Column c, Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object v = data.containsKey(c.jsonName()) ? data.get(c.jsonName()) : data.get(c.name());
        if (v == null) {
            return null;
        }
        return switch (c.type()) {
            case 'I' -> v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString());
            case 'B' -> v instanceof Boolean b ? b : (v instanceof Number n ? n.intValue() != 0 : Boolean.parseBoolean(v.toString()));
            default -> v.toString();
        };
    }

    public Optional<Upsert> get(RefEntity e, int id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT " + e.columnList() + ", SourceVersion, Deleted FROM dbo."
                + e.replicaTable + " WHERE " + e.key + " = ?", id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> r = rows.get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        for (RefEntity.Column c : e.columns) {
            data.put(c.jsonName(), r.get(c.name()));
        }
        return Optional.of(new Upsert(((Number) r.get("SourceVersion")).longValue(), Boolean.TRUE.equals(r.get("Deleted")), data));
    }

    public List<Status> status() {
        List<Status> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (RefEntity e : RefEntity.values()) {
            Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM dbo." + e.replicaTable + " WHERE Deleted = 0", Integer.class);
            List<Map<String, Object>> st = jdbc.queryForList("SELECT LastVersion, LastAppliedAt, LastPolledAt FROM dbo.ReplicationState "
                    + "WHERE Entity = ?", e.path);
            Long version = null;
            LocalDateTime applied = null;
            LocalDateTime fresh = null;
            if (!st.isEmpty()) {
                version = ((Number) st.get(0).get("LastVersion")).longValue();
                applied = toLdt(st.get(0).get("LastAppliedAt"));
                fresh = later(applied, toLdt(st.get(0).get("LastPolledAt")));
            }
            List<Timestamp> lastPut = jdbc.queryForList("SELECT MAX(AppliedAt) FROM dbo." + e.replicaTable, Timestamp.class);
            LocalDateTime put = lastPut.isEmpty() || lastPut.get(0) == null ? null : lastPut.get(0).toLocalDateTime();
            applied = later(applied, put);
            fresh = later(fresh, put);
            Long lag = fresh == null ? null : Math.max(0, Duration.between(fresh, now).toSeconds());
            out.add(new Status(e.path, rows == null ? 0 : rows, version, applied, lag, lag == null || lag > staleAfterSeconds));
        }
        return out;
    }

    private static LocalDateTime toLdt(Object o) {
        return o instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) o;
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }

    // ------------------------------------------------------------------ relay

    /** Pull outstanding changes for all four entities from AssessmentDelivery. Serialised; returns rows applied. */
    public synchronized int catchUp() {
        JdbcTemplate src = source.jdbc();
        Long current = src.queryForObject("SELECT CHANGE_TRACKING_CURRENT_VERSION()", Long.class);
        int applied = 0;
        for (RefEntity e : RefEntity.values()) {
            applied += catchUp(src, e, current);
        }
        return applied;
    }

    /** Full resync of every entity regardless of saved versions (initial import, CT retention overrun). */
    public synchronized int resyncAll() {
        JdbcTemplate src = source.jdbc();
        Long current = src.queryForObject("SELECT CHANGE_TRACKING_CURRENT_VERSION()", Long.class);
        int applied = 0;
        for (RefEntity e : RefEntity.values()) {
            applied += fullResync(src, e, current == null ? 0L : current);
        }
        return applied;
    }

    private int catchUp(JdbcTemplate src, RefEntity e, Long current) {
        List<Long> saved = jdbc.queryForList("SELECT LastVersion FROM dbo.ReplicationState WHERE Entity = ?", Long.class, e.path);
        Long minValid = current == null ? null : src.queryForObject(
                "SELECT CHANGE_TRACKING_MIN_VALID_VERSION(OBJECT_ID('dbo." + e.sourceTable + "'))", Long.class);
        if (current == null || minValid == null || saved.isEmpty() || saved.get(0) < minValid) {
            return fullResync(src, e, current == null ? 0L : current);
        }
        long last = saved.get(0);
        if (last >= current) {
            touch(e, last, false);
            return 0;
        }
        List<Map<String, Object>> changes = src.queryForList("SELECT ct.SYS_CHANGE_VERSION AS ChangeVersion, "
                + "ct.SYS_CHANGE_OPERATION AS ChangeOp, ct." + e.key + " AS ChangeKey, t.* "
                + "FROM CHANGETABLE(CHANGES dbo." + e.sourceTable + ", ?) AS ct "
                + "LEFT JOIN dbo." + e.sourceTable + " t ON t." + e.key + " = ct." + e.key
                + " ORDER BY ct.SYS_CHANGE_VERSION", last);
        int n = tx.execute(s -> {
            int count = 0;
            for (Map<String, Object> c : changes) {
                int id = ((Number) c.get("ChangeKey")).intValue();
                long v = ((Number) c.get("ChangeVersion")).longValue();
                boolean deleted = "D".equals(c.get("ChangeOp")) || c.get(e.key) == null;
                if (apply(e, id, new Upsert(v, deleted, deleted ? null : c), false)) {
                    count++;
                }
            }
            return count;
        });
        touch(e, current, n > 0);
        return n;
    }

    private int fullResync(JdbcTemplate src, RefEntity e, long version) {
        List<Map<String, Object>> rows = src.queryForList("SELECT " + e.key + ", " + e.columnList() + " FROM dbo." + e.sourceTable);
        int n = tx.execute(s -> {
            int count = 0;
            for (Map<String, Object> r : rows) {
                if (apply(e, ((Number) r.get(e.key)).intValue(), new Upsert(version, false, r), true)) {
                    count++;
                }
            }
            return count;
        });
        touch(e, version, true);
        LOG.info("Reference full resync {}: {} rows at version {}", e.path, n, version);
        return n;
    }

    private void touch(RefEntity e, long version, boolean applied) {
        jdbc.update("MERGE dbo.ReplicationState AS t USING (SELECT ? AS Entity) AS s ON t.Entity = s.Entity "
                + "WHEN MATCHED THEN UPDATE SET LastVersion = ?, LastPolledAt = GETDATE()"
                + (applied ? ", LastAppliedAt = GETDATE() " : " ")
                + "WHEN NOT MATCHED THEN INSERT (Entity, LastVersion, LastAppliedAt, LastPolledAt) VALUES (?, ?, GETDATE(), GETDATE());",
                e.path, version, e.path, version);
    }
}
