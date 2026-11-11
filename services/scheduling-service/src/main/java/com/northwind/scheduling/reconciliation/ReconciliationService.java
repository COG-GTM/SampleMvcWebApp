package com.northwind.scheduling.reconciliation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import com.northwind.scheduling.reconciliation.Reconciliation.BookingMismatch;
import com.northwind.scheduling.reconciliation.Reconciliation.SeatDriftRow;
import com.northwind.scheduling.reconciliation.Reconciliation.SessionCounts;
import com.northwind.scheduling.reconciliation.Reconciliation.SessionMismatch;

/**
 * Compares Scheduling (the single writer) with AssessmentDelivery (the relay's projection) per session and per
 * booking, runs db/checks/seat-drift.sql, and writes reports/reconciliation-&lt;timestamp&gt;.md (MBA-3823).
 * Replaces capacity-sync's nightly SeatsAvailable rewrite (HB-03, MR-17) with a report. Legacy reads use the
 * read-only importer login.
 */
@Service
public class ReconciliationService {

    static final String SESSION_SQL_MODERN = "SELECT s.session_id AS id, s.capacity AS cap, "
            + "SUM(CASE WHEN b.status = 'BOOKED' THEN 1 ELSE 0 END) AS booked, "
            + "SUM(CASE WHEN b.status = 'ATTENDED' THEN 1 ELSE 0 END) AS attended, "
            + "SUM(CASE WHEN b.status = 'WAITLISTED' THEN 1 ELSE 0 END) AS waitlisted, "
            + "SUM(CASE WHEN b.status IN ('BOOKED','ATTENDED') AND b.accommodation_code IN ('ET25','ET50') THEN 1 ELSE 0 END) AS acc "
            + "FROM exam_session s LEFT JOIN booking b ON b.session_id = s.session_id GROUP BY s.session_id, s.capacity";
    static final String SESSION_SQL_LEGACY = "SELECT s.SessionId AS id, s.Capacity AS cap, s.SeatsAvailable AS seats, "
            + "SUM(CASE WHEN b.Status = 'BOOKED' THEN 1 ELSE 0 END) AS booked, "
            + "SUM(CASE WHEN b.Status = 'ATTENDED' THEN 1 ELSE 0 END) AS attended, "
            + "SUM(CASE WHEN b.Status = 'WAITLISTED' THEN 1 ELSE 0 END) AS waitlisted, "
            + "SUM(CASE WHEN b.Status IN ('BOOKED','ATTENDED') AND b.AccommodationCode IN ('ET25','ET50') THEN 1 ELSE 0 END) AS acc "
            + "FROM dbo.ExamSession s LEFT JOIN dbo.Booking b ON b.SessionId = s.SessionId "
            + "GROUP BY s.SessionId, s.Capacity, s.SeatsAvailable";
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JdbcTemplate scheduling;
    private final JdbcTemplate legacy;
    private final Clock clock;
    private final Path reportDir;
    private final Path seatDriftSql;

    @org.springframework.beans.factory.annotation.Autowired
    public ReconciliationService(JdbcTemplate scheduling, Clock clock,
                                 @Value("${scheduling.legacy.url}") String url,
                                 @Value("${scheduling.legacy.username}") String user,
                                 @Value("${scheduling.legacy.password}") String password,
                                 @Value("${scheduling.reconciliation.report-dir:reports}") String reportDir,
                                 @Value("${scheduling.reconciliation.seat-drift-sql:db/checks/seat-drift.sql}") String seatDriftSql) {
        this(scheduling, legacyJdbc(url, user, password), clock, resolve(reportDir), resolve(seatDriftSql));
    }

    public ReconciliationService(JdbcTemplate scheduling, JdbcTemplate legacy, Clock clock, Path reportDir,
                                 Path seatDriftSql) {
        this.scheduling = scheduling;
        this.legacy = legacy;
        this.clock = clock;
        this.reportDir = reportDir;
        this.seatDriftSql = seatDriftSql;
    }

    public Reconciliation reconcile(boolean writeReport) {
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Map<Integer, SessionCounts> modern = sessions(scheduling, SESSION_SQL_MODERN, true);
        Map<Integer, SessionCounts> old = sessions(legacy, SESSION_SQL_LEGACY, false);
        List<SessionMismatch> sm = compareSessions(modern, old);
        Map<Integer, Object[]> mb = new TreeMap<>();
        scheduling.query("SELECT COALESCE(legacy_booking_id, booking_id) AS id, session_id, status, accommodation_code FROM booking",
                rs -> { mb.put(rs.getInt("id"), new Object[] {rs.getInt("session_id"), rs.getString("status"), blank(rs.getString("accommodation_code"))}); });
        Map<Integer, Object[]> lb = new TreeMap<>();
        legacy.query("SELECT BookingId, SessionId, Status, AccommodationCode FROM dbo.Booking",
                rs -> { lb.put(rs.getInt("BookingId"), new Object[] {rs.getInt("SessionId"), rs.getString("Status"), blank(rs.getString("AccommodationCode"))}); });
        List<BookingMismatch> bm = compareBookings(mb, lb);
        List<SeatDriftRow> drift = seatDrift();
        Path file = writeReport ? reportDir.resolve("reconciliation-" + FILE_TS.format(now) + ".md") : null;
        Reconciliation r = new Reconciliation(now.toString(), union(modern, old).size(),
                union(mb, lb).size(), sm, bm, drift, file == null ? null : file.toString());
        if (file != null) {
            try {
                Files.createDirectories(reportDir);
                Files.writeString(file, ReconciliationReport.markdown(r, seatDriftSql), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("cannot write " + file, e);
            }
        }
        return r;
    }

    static List<SessionMismatch> compareSessions(Map<Integer, SessionCounts> modern, Map<Integer, SessionCounts> old) {
        List<SessionMismatch> out = new ArrayList<>();
        for (Integer id : new TreeSet<>(union(modern, old))) {
            SessionCounts m = modern.get(id);
            SessionCounts l = old.get(id);
            if (m == null || l == null) {
                out.add(new SessionMismatch(id, "session", m == null ? null : 1, l == null ? null : 1));
                continue;
            }
            field(out, id, "capacity", m.capacity(), l.capacity());
            field(out, id, "seatsAvailable", m.seatsAvailable(), l.seatsAvailable());
            field(out, id, "booked", m.booked(), l.booked());
            field(out, id, "attended", m.attended(), l.attended());
            field(out, id, "waitlisted", m.waitlisted(), l.waitlisted());
            field(out, id, "accommodationUsed", m.accommodationUsed(), l.accommodationUsed());
        }
        return out;
    }

    private static String blank(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    static List<BookingMismatch> compareBookings(Map<Integer, Object[]> modern, Map<Integer, Object[]> old) {
        List<BookingMismatch> out = new ArrayList<>();
        for (Integer id : new TreeSet<>(union(modern, old))) {
            Object[] m = modern.get(id);
            Object[] l = old.get(id);
            if (m == null || l == null || !Objects.equals(m[0], l[0]) || !Objects.equals(m[1], l[1])
                    || !Objects.equals(m[2], l[2])) {
                out.add(new BookingMismatch(id, m == null ? null : (Integer) m[0], m == null ? null : (String) m[1],
                        m == null ? null : (String) m[2], l == null ? null : (Integer) l[0],
                        l == null ? null : (String) l[1], l == null ? null : (String) l[2]));
            }
        }
        return out;
    }

    /** Runs db/checks/seat-drift.sql (its USE/GO lines are sqlcmd syntax and are dropped). */
    List<SeatDriftRow> seatDrift() {
        String sql;
        try {
            sql = Files.readString(seatDriftSql, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + seatDriftSql, e);
        }
        String query = sql.lines().filter(l -> !l.trim().matches("(?i)(GO|USE\\s+\\w+;?)"))
                .collect(Collectors.joining("\n"));
        return legacy.query(query, (rs, i) -> new SeatDriftRow(rs.getInt("SessionId"), rs.getString("ExamCode"),
                rs.getString("CenterCode"), String.valueOf(rs.getTimestamp("StartsAt").toLocalDateTime()),
                rs.getInt("Capacity"), rs.getInt("SeatsAvailable"), rs.getInt("ActiveBookings"), rs.getInt("Drift")));
    }

    private static Map<Integer, SessionCounts> sessions(JdbcTemplate jdbc, String sql, boolean derivedSeats) {
        Map<Integer, SessionCounts> out = new TreeMap<>();
        jdbc.query(sql, rs -> {
            int cap = rs.getInt("cap");
            int booked = rs.getInt("booked");
            int attended = rs.getInt("attended");
            int seats = derivedSeats ? Math.max(0, cap - booked - attended) : rs.getInt("seats");
            out.put(rs.getInt("id"), new SessionCounts(cap, seats, booked, attended, rs.getInt("waitlisted"),
                    rs.getInt("acc")));
        });
        return out;
    }

    private static void field(List<SessionMismatch> out, int id, String name, int m, int l) {
        if (m != l) {
            out.add(new SessionMismatch(id, name, m, l));
        }
    }

    private static Set<Integer> union(Map<Integer, ?> a, Map<Integer, ?> b) {
        Set<Integer> s = new TreeSet<>(a.keySet());
        s.addAll(b.keySet());
        return s;
    }

    /** relative paths: as given (run scripts start in the repo root), else from the module dir (../../) */
    /** Relative paths are resolved against the repository root (nearest ancestor holding db/checks), else the cwd. */
    static Path resolve(String p) {
        Path path = Path.of(p);
        if (path.isAbsolute()) {
            return path;
        }
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("db").resolve("checks"))) {
                return dir.resolve(p).normalize();
            }
        }
        return path;
    }

    private static JdbcTemplate legacyJdbc(String url, String user, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return new JdbcTemplate(ds);
    }
}
