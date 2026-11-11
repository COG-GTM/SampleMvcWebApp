package com.northwind.assessment.scheduling.reconciliation;

import com.northwind.assessment.scheduling.config.SchedulingProperties;
import com.northwind.assessment.scheduling.domain.SeatFormulas;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Compares Scheduling (the single writer) with AssessmentDelivery (the projection) per session: Capacity,
 * SeatsAvailable, BOOKED / ATTENDED / WAITLISTED counts, accommodation seats used and the status of every
 * booking; plus db/checks/seat-drift.sql on the legacy side. Replaces capacity-sync's nightly rewrite
 * (MBA-3823). Reads legacy with the read-only importer login.
 */
@Service
public class ReconciliationService {

    static final int MAX_LISTED = 200;

    public record SessionCounts(int capacity, int seatsAvailable, int booked, int attended, int waitlisted, int accommodationUsed,
                                int seatsOffered) {
    }

    public record Mismatch(Integer sessionId, String bookingRef, String field, String scheduling, String legacy) {
    }

    public record RelayStatus(long pendingEvents, Long lastEventId, Long lastRelayedEventId, Long p95LagMs, Long maxLagMs,
                              long relayedEvents, String lastError) {
    }

    public record Report(LocalDateTime ranAt, int sessionsCompared, int bookingsCompared, int legacyOnlySessions,
                         List<Mismatch> mismatches, List<String> seatDriftColumns, List<List<String>> seatDriftRows,
                         RelayStatus relay, String reportFile) {
        public int mismatchCount() {
            return mismatches.size();
        }
    }

    private record BookingRow(String ref, int sessionId, String status, String accommodationCode) {
    }

    private final JdbcClient scheduling;
    private final JdbcClient legacy;
    private final SchedulingProperties props;
    private final Clock clock;

    public ReconciliationService(JdbcClient scheduling, @Qualifier("legacy") JdbcClient legacy, SchedulingProperties props,
                                 Clock clock) {
        this.scheduling = scheduling;
        this.legacy = legacy;
        this.props = props;
        this.clock = clock;
    }

    public Report reconcile(boolean writeReport) {
        LocalDateTime ranAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Map<Integer, SessionCounts> modern = schedulingSessions();
        Map<Integer, SessionCounts> old = legacySessions();
        List<Mismatch> mismatches = new ArrayList<>();
        int legacyOnly = 0;
        for (Integer id : new TreeSet<>(old.keySet())) {
            if (!modern.containsKey(id)) {
                // bookable only through scheduling-service, so a session it does not know is a mismatch
                legacyOnly++;
                mismatches.add(new Mismatch(id, null, "session", "missing", "present"));
            }
        }
        for (Map.Entry<Integer, SessionCounts> e : new java.util.TreeMap<>(modern).entrySet()) {
            SessionCounts m = e.getValue();
            SessionCounts l = old.get(e.getKey());
            if (l == null) {
                mismatches.add(new Mismatch(e.getKey(), null, "session", "present", "missing"));
                continue;
            }
            compare(mismatches, e.getKey(), "Capacity", m.capacity(), l.capacity());
            compare(mismatches, e.getKey(), "SeatsAvailable", m.seatsAvailable(), l.seatsAvailable());
            compare(mismatches, e.getKey(), "BOOKED", m.booked(), l.booked());
            compare(mismatches, e.getKey(), "ATTENDED", m.attended(), l.attended());
            compare(mismatches, e.getKey(), "WAITLISTED", m.waitlisted(), l.waitlisted());
            compare(mismatches, e.getKey(), "AccommodationSeatsUsed", m.accommodationUsed(), l.accommodationUsed());
            compare(mismatches, e.getKey(), "SeatsOffered", m.seatsOffered(), l.seatsOffered());
        }
        int bookingsCompared = compareBookings(modern, mismatches);
        List<String> driftColumns = new ArrayList<>();
        List<List<String>> driftRows = seatDrift(driftColumns);
        RelayStatus relay = relayStatus();
        Report report = new Report(ranAt, modern.size(), bookingsCompared, legacyOnly, mismatches, driftColumns, driftRows,
                relay, null);
        if (writeReport) {
            Path file = writeMarkdown(report);
            report = new Report(ranAt, modern.size(), bookingsCompared, legacyOnly, mismatches, driftColumns, driftRows,
                    relay, file.toAbsolutePath().normalize().toString());
        }
        return report;
    }

    private static void compare(List<Mismatch> out, int sessionId, String field, int scheduling, int legacy) {
        if (scheduling != legacy) {
            out.add(new Mismatch(sessionId, null, field, String.valueOf(scheduling), String.valueOf(legacy)));
        }
    }

    Map<Integer, SessionCounts> schedulingSessions() {
        Map<Integer, SessionCounts> out = new HashMap<>();
        scheduling.sql("SELECT s.session_id, s.capacity, "
                        + "SUM(CASE WHEN b.status = 'BOOKED' THEN 1 ELSE 0 END), "
                        + "SUM(CASE WHEN b.status = 'ATTENDED' THEN 1 ELSE 0 END), "
                        + "SUM(CASE WHEN b.status = 'WAITLISTED' THEN 1 ELSE 0 END), "
                        + "SUM(CASE WHEN b.status IN ('BOOKED','ATTENDED') AND b.accommodation_code IN ('ET25','ET50') THEN 1 ELSE 0 END), "
                        + "(SELECT COUNT(*) FROM seat_offer o WHERE o.session_id = s.session_id AND o.status = 'PENDING') "
                        + "FROM exam_session s LEFT JOIN booking b ON b.session_id = s.session_id GROUP BY s.session_id, s.capacity")
                .query(rs -> {
                    int cap = rs.getInt(2);
                    int booked = rs.getInt(3);
                    int attended = rs.getInt(4);
                    int offered = rs.getInt(7);
                    out.put(rs.getInt(1), new SessionCounts(cap, SeatFormulas.seatsAvailable(cap, booked + attended, offered),
                            booked, attended, rs.getInt(5), rs.getInt(6), offered));
                });
        return out;
    }

    Map<Integer, SessionCounts> legacySessions() {
        Map<Integer, SessionCounts> out = new HashMap<>();
        legacy.sql("SELECT s.SessionId, s.Capacity, s.SeatsAvailable, "
                        + "SUM(CASE WHEN b.Status = 'BOOKED' THEN 1 ELSE 0 END), "
                        + "SUM(CASE WHEN b.Status = 'ATTENDED' THEN 1 ELSE 0 END), "
                        + "SUM(CASE WHEN b.Status = 'WAITLISTED' THEN 1 ELSE 0 END), "
                        + "SUM(CASE WHEN b.Status IN ('BOOKED','ATTENDED') AND b.AccommodationCode IN ('ET25','ET50') THEN 1 ELSE 0 END), "
                        + "(SELECT COUNT(*) FROM dbo.Waitlist w WHERE w.SessionId = s.SessionId AND w.OfferExpiresAt IS NOT NULL) "
                        + "FROM dbo.ExamSession s LEFT JOIN dbo.Booking b ON b.SessionId = s.SessionId "
                        + "GROUP BY s.SessionId, s.Capacity, s.SeatsAvailable")
                .query(rs -> {
                    out.put(rs.getInt(1), new SessionCounts(rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6),
                            rs.getInt(7), rs.getInt(8)));
                });
        return out;
    }

    /** Per-booking status of every booking on a Scheduling session, keyed by the AssessmentDelivery BookingId. */
    private int compareBookings(Map<Integer, SessionCounts> modernSessions, List<Mismatch> out) {
        Map<String, BookingRow> modern = new LinkedHashMap<>();
        List<Long> unrelayed = new ArrayList<>();
        scheduling.sql("SELECT booking_id, origin, legacy_booking_id, session_id, status, accommodation_code FROM booking "
                        + "ORDER BY booking_id")
                .query(rs -> {
                    long id = rs.getLong(1);
                    int legacyId = rs.getInt(3);
                    boolean hasLegacy = !rs.wasNull();
                    if (!hasLegacy && !"LEGACY_IMPORT".equals(rs.getString(2))) {
                        unrelayed.add(id);
                        return;
                    }
                    String ref = String.valueOf(hasLegacy ? legacyId : id);
                    modern.put(ref, new BookingRow(ref, rs.getInt(4), rs.getString(5), rs.getString(6)));
                });
        Map<String, BookingRow> old = new HashMap<>();
        legacy.sql("SELECT BookingId, SessionId, Status, AccommodationCode FROM dbo.Booking")
                .query(rs -> {
                    if (modernSessions.containsKey(rs.getInt(2))) {
                        String ref = String.valueOf(rs.getInt(1));
                        old.put(ref, new BookingRow(ref, rs.getInt(2), rs.getString(3), rs.getString(4)));
                    }
                });
        for (Long id : unrelayed) {
            out.add(new Mismatch(null, "scheduling:" + id, "booking", "present", "not relayed yet"));
        }
        for (BookingRow m : modern.values()) {
            BookingRow l = old.remove(m.ref());
            if (l == null) {
                out.add(new Mismatch(m.sessionId(), m.ref(), "booking", m.status(), "missing"));
                continue;
            }
            if (m.sessionId() != l.sessionId()) {
                out.add(new Mismatch(m.sessionId(), m.ref(), "SessionId", String.valueOf(m.sessionId()), String.valueOf(l.sessionId())));
            }
            if (!m.status().equals(l.status())) {
                out.add(new Mismatch(m.sessionId(), m.ref(), "Status", m.status(), l.status()));
            }
            if (!Objects.equals(blank(m.accommodationCode()), blank(l.accommodationCode()))) {
                out.add(new Mismatch(m.sessionId(), m.ref(), "AccommodationCode", m.accommodationCode(), l.accommodationCode()));
            }
        }
        for (BookingRow l : old.values()) {
            out.add(new Mismatch(l.sessionId(), l.ref(), "booking", "missing", l.status()));
        }
        return modern.size() + unrelayed.size();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    List<List<String>> seatDrift(List<String> columns) {
        String sql;
        try (InputStream in = getClass().getResourceAsStream("/reconciliation/seat-drift.sql")) {
            sql = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<List<String>> rows = new ArrayList<>();
        legacy.sql(sql).query(rs -> {
            ResultSetMetaData md = rs.getMetaData();
            if (columns.isEmpty()) {
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    columns.add(md.getColumnLabel(i));
                }
            }
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                row.add(String.valueOf(rs.getObject(i)));
            }
            rows.add(row);
        });
        if (columns.isEmpty()) {
            columns.addAll(List.of("SessionId", "ExamCode", "CenterCode", "StartsAt", "Capacity", "SeatsAvailable",
                    "ActiveBookings", "OfferedSeats", "Drift"));
        }
        return rows;
    }

    public RelayStatus relayStatus() {
        return scheduling.sql("SELECT "
                        + "(SELECT COUNT(*) FROM outbox_event WHERE relayed_at IS NULL), "
                        + "(SELECT MAX(event_id) FROM outbox_event), "
                        + "(SELECT MAX(event_id) FROM outbox_event WHERE relayed_at IS NOT NULL), "
                        + "(SELECT TOP 1 PERCENTILE_DISC(0.95) WITHIN GROUP (ORDER BY DATEDIFF(MILLISECOND, created_at, relayed_at)) OVER () "
                        + "   FROM outbox_event WHERE relayed_at IS NOT NULL), "
                        + "(SELECT MAX(DATEDIFF(MILLISECOND, created_at, relayed_at)) FROM outbox_event WHERE relayed_at IS NOT NULL), "
                        + "(SELECT COUNT(*) FROM outbox_event WHERE relayed_at IS NOT NULL), "
                        + "(SELECT TOP 1 last_error FROM outbox_event WHERE relayed_at IS NULL AND last_error IS NOT NULL ORDER BY event_id)")
                .query((rs, n) -> new RelayStatus(rs.getLong(1), (Long) rs.getObject(2, Long.class),
                        (Long) rs.getObject(3, Long.class), (Long) rs.getObject(4, Long.class), (Long) rs.getObject(5, Long.class),
                        rs.getLong(6), rs.getString(7)))
                .single();
    }

    Path writeMarkdown(Report r) {
        Path dir = Path.of(props.reconciliation().reportDir());
        Path file = dir.resolve("reconciliation-" + r.ranAt().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".md");
        try {
            Files.createDirectories(dir);
            Files.writeString(file, ReportFormatter.markdown(r), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }
}
