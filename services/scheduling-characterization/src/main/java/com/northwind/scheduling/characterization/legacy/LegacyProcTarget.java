package com.northwind.scheduling.characterization.legacy;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import com.microsoft.sqlserver.jdbc.SQLServerError;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import com.northwind.scheduling.characterization.target.Capability;
import com.northwind.scheduling.characterization.target.ErrorCondition;
import com.northwind.scheduling.characterization.target.Fixtures;
import com.northwind.scheduling.characterization.target.Model.AuditEntry;
import com.northwind.scheduling.characterization.target.Model.BookingState;
import com.northwind.scheduling.characterization.target.Model.ExamSessionState;
import com.northwind.scheduling.characterization.target.Model.WaitlistEntry;
import com.northwind.scheduling.characterization.target.Outcome;
import com.northwind.scheduling.characterization.target.RawStorageAccess;
import com.northwind.scheduling.characterization.target.SchedulingError;
import com.northwind.scheduling.characterization.target.SchedulingTarget;
import com.northwind.scheduling.characterization.target.SeatDriftCheck;

/**
 * Legacy target: EXECs the real procs in db/procs over JDBC as the shared {@code ad_app} login, so the triggers in
 * db/triggers fire exactly as they do for the portal and the Java services.
 */
public final class LegacyProcTarget implements SchedulingTarget {

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection c) throws SQLException;
    }

    private final LegacyDatabase db;
    private final LinkedBlockingQueue<Connection> pool = new LinkedBlockingQueue<>();
    private final LegacyFixtures fixtures;
    private final LegacyRawStorage raw;
    private final SeatDriftCheck driftCheck;

    public LegacyProcTarget(LegacyDatabase db) {
        this.db = db;
        this.fixtures = new LegacyFixtures(this);
        this.raw = new LegacyRawStorage(this);
        this.driftCheck = new LegacySeatDriftSqlCheck(this, db.repoRootPath().resolve("db/checks/seat-drift.sql"));
    }

    @Override
    public String name() {
        return "legacy";
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.RAW_STORAGE, Capability.LEGACY_SQL_SEMANTICS);
    }

    // ---- connections ----------------------------------------------------------------------------

    public <T> T withConnection(SqlWork<T> work) {
        Connection c = pool.poll();
        try {
            if (c == null || c.isClosed()) {
                c = db.connect();
            }
            T result = work.run(c);
            if (!c.getAutoCommit()) {
                c.rollback();
                c.setAutoCommit(true);
            }
            pool.offer(c);
            c = null;
            return result;
        } catch (SQLException e) {
            throw new LegacySqlException(e);
        } finally {
            if (c != null) {
                // connection state after an error is not trusted for reuse
                try {
                    c.close();
                } catch (SQLException ignored) {
                    // nothing to do
                }
            }
        }
    }

    /** Runs an operation; a SQLServerException becomes an error Outcome. The connection is discarded on error. */
    private Outcome op(SqlWork<Outcome> work) {
        try {
            return withConnection(work);
        } catch (LegacySqlException e) {
            return Outcome.failed(toError(e.getCause()));
        }
    }

    static SchedulingError toError(SQLException e) {
        int number = e.getErrorCode();
        Integer state = null;
        String message = e.getMessage();
        if (e instanceof SQLServerException se) {
            SQLServerError err = se.getSQLServerError();
            if (err != null) {
                number = err.getErrorNumber();
                state = err.getErrorState();
                message = err.getErrorMessage();
            }
        }
        ErrorCondition condition = number == 50000 && state != null
                ? ErrorCondition.fromLegacy(state, message) : ErrorCondition.SYSTEM_ERROR;
        return new SchedulingError(condition, condition.legacyCode(), message, number, state);
    }

    // ---- operations -----------------------------------------------------------------------------

    @Override
    public Outcome book(int sessionId, int candidateId, String accommodationCode) {
        return op(c -> {
            try (CallableStatement cs = c.prepareCall("{call dbo.usp_BookSeat(?, ?, ?, ?, ?)}")) {
                cs.setInt(1, sessionId);
                cs.setInt(2, candidateId);
                setNullableString(cs, 3, accommodationCode);
                cs.registerOutParameter(4, Types.INTEGER);
                cs.registerOutParameter(5, Types.VARCHAR);
                cs.execute();
                return Outcome.booked(cs.getInt(4), cs.getString(5));
            }
        });
    }

    @Override
    public Outcome cancel(int bookingId, String reason, String actor) {
        return op(c -> {
            try (CallableStatement cs = c.prepareCall("{call dbo.usp_CancelBooking(?, ?, ?)}")) {
                cs.setInt(1, bookingId);
                setNullableString(cs, 2, reason);
                setNullableString(cs, 3, actor);
                cs.execute();
                return Outcome.ok();
            }
        });
    }

    @Override
    public Outcome changeCapacity(int sessionId, int newCapacity, String actor) {
        return op(c -> {
            try (CallableStatement cs = c.prepareCall("{call dbo.usp_ChangeCapacity(?, ?, ?)}")) {
                cs.setInt(1, sessionId);
                cs.setInt(2, newCapacity);
                setNullableString(cs, 3, actor);
                cs.execute();
                return Outcome.ok();
            }
        });
    }

    @Override
    public Outcome reschedule(int bookingId, int newSessionId) {
        return op(c -> {
            try (CallableStatement cs = c.prepareCall("{call dbo.usp_RescheduleBooking(?, ?, ?, ?)}")) {
                cs.setInt(1, bookingId);
                cs.setInt(2, newSessionId);
                cs.registerOutParameter(3, Types.INTEGER);
                cs.registerOutParameter(4, Types.VARCHAR);
                cs.execute();
                return Outcome.booked(cs.getInt(3), cs.getString(4));
            }
        });
    }

    @Override
    public void markAttended(int bookingId) {
        // ResultIngestService: UPDATE dbo.Booking SET Status = 'ATTENDED', LastModified = GETDATE() WHERE BookingId = ? AND Status = 'BOOKED'
        update("UPDATE dbo.Booking SET Status = 'ATTENDED', LastModified = GETDATE() WHERE BookingId = ? AND Status = 'BOOKED'", bookingId);
    }

    @Override
    public int reconcileSeats(Collection<Integer> sessionIds) {
        if (sessionIds.isEmpty()) {
            return 0;
        }
        String in = sessionIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        // CapacitySyncService.SELECT_OPEN_SESSIONS, restricted to the given sessions
        String select = "SELECT s.SessionId, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, "
                + "       (SELECT COUNT(*) FROM dbo.Booking b "
                + "         WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED') "
                + "           AND (b.AccommodationCode IS NULL OR b.AccommodationCode NOT IN ('ET25','ET50'))) AS StandardBooked "
                + "FROM dbo.ExamSession s WHERE s.Status = 'OPEN' AND s.SessionId IN (" + in + ") ORDER BY s.SessionId";
        List<int[]> rows = query(select, rs -> new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)});
        int updated = 0;
        for (int[] r : rows) {
            int expected = Math.max(0, r[1] - r[2] - r[4]);
            if (expected != r[3]) {
                // compare-and-set exactly as CapacitySyncService; rows counted via OUTPUT INTO because the update count
                // a modern driver reports for a trigger-bearing table is not the statement's own row count
                updated += queryInt("SET NOCOUNT ON; DECLARE @t TABLE (SessionId INT); "
                        + "UPDATE dbo.ExamSession SET SeatsAvailable = ?, LastModified = GETDATE() OUTPUT INSERTED.SessionId INTO @t "
                        + "WHERE SessionId = ? AND Status = 'OPEN' AND SeatsAvailable = ?; SELECT COUNT(*) FROM @t;",
                        expected, r[0], r[3]);
            }
        }
        update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                "ExamSession", 0, "CAPACITY_SYNC",
                "trigger=characterization status=COMPLETED checked=" + rows.size() + " updated=" + updated, "capacity-sync");
        return updated;
    }

    // ---- reads ----------------------------------------------------------------------------------

    @Override
    public ExamSessionState session(int sessionId) {
        List<ExamSessionState> l = query("SELECT SessionId, ExamId, Capacity, AccommodationCapacity, SeatsAvailable, Status, LastModified "
                + "FROM dbo.ExamSession WHERE SessionId = ?", rs -> new ExamSessionState(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                rs.getInt(4), rs.getInt(5), rs.getString(6), ts(rs, 7)), sessionId);
        return l.isEmpty() ? null : l.get(0);
    }

    private static final String BOOKING_COLS = "SELECT BookingId, SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef, "
            + "BookedAt, CancelledAt, CancelReason FROM dbo.Booking ";

    private static BookingState mapBooking(ResultSet rs) throws SQLException {
        return new BookingState(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), ts(rs, 8), ts(rs, 9), rs.getString(10));
    }

    @Override
    public BookingState booking(int bookingId) {
        List<BookingState> l = query(BOOKING_COLS + "WHERE BookingId = ?", LegacyProcTarget::mapBooking, bookingId);
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    public List<BookingState> bookings(int sessionId) {
        return query(BOOKING_COLS + "WHERE SessionId = ? ORDER BY BookingId", LegacyProcTarget::mapBooking, sessionId);
    }

    @Override
    public List<WaitlistEntry> waitlist(int sessionId) {
        return query("SELECT SessionId, CandidateId, BookingId, Position FROM dbo.Waitlist WHERE SessionId = ? ORDER BY Position, WaitlistId",
                rs -> new WaitlistEntry(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)), sessionId);
    }

    @Override
    public List<AuditEntry> audit(String entityName, int entityId) {
        return query("SELECT EntityName, EntityId, Action, Detail, Actor FROM dbo.AuditLog WHERE EntityName = ? AND EntityId = ? ORDER BY AuditId",
                rs -> new AuditEntry(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5)),
                entityName, entityId);
    }

    @Override
    public Fixtures fixtures() {
        return fixtures;
    }

    @Override
    public SeatDriftCheck seatDriftCheck() {
        return driftCheck;
    }

    @Override
    public Optional<RawStorageAccess> rawStorage() {
        return Optional.of(raw);
    }

    @Override
    public void close() {
        Connection c;
        while ((c = pool.poll()) != null) {
            try {
                c.close();
            } catch (SQLException ignored) {
                // closing
            }
        }
        db.close();
    }

    // ---- JDBC helpers ---------------------------------------------------------------------------

    @FunctionalInterface
    interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, args);
                List<T> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(mapper.map(rs));
                    }
                }
                return out;
            }
        });
    }

    int update(String sql, Object... args) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, args);
                return ps.executeUpdate();
            }
        });
    }

    /** Executes a batch whose (last) result set is a single int, e.g. {@code INSERT ...; SELECT SCOPE_IDENTITY()}. */
    int queryInt(String sql, Object... args) {
        return withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                bind(ps, args);
                boolean isResult = ps.execute();
                Integer value = null;
                while (true) {
                    if (isResult) {
                        try (ResultSet rs = ps.getResultSet()) {
                            if (rs.next()) {
                                value = rs.getInt(1);
                            }
                        }
                    } else if (ps.getUpdateCount() == -1) {
                        break;
                    }
                    isResult = ps.getMoreResults();
                }
                if (value == null) {
                    throw new SQLException("No result for: " + sql);
                }
                return value;
            }
        });
    }

    static void bind(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            if (a == null) {
                ps.setNull(i + 1, Types.VARCHAR);
            } else if (a instanceof LocalDateTime t) {
                ps.setTimestamp(i + 1, Timestamp.valueOf(t));
            } else {
                ps.setObject(i + 1, a);
            }
        }
    }

    private static void setNullableString(CallableStatement cs, int idx, String v) throws SQLException {
        if (v == null) {
            cs.setNull(idx, Types.VARCHAR);
        } else {
            cs.setString(idx, v);
        }
    }

    private static LocalDateTime ts(ResultSet rs, int idx) throws SQLException {
        Timestamp t = rs.getTimestamp(idx);
        return t == null ? null : t.toLocalDateTime();
    }

    /** Unchecked wrapper for SQL errors outside the error-mapped operations. */
    public static final class LegacySqlException extends RuntimeException {
        LegacySqlException(SQLException cause) {
            super(cause.getMessage(), cause);
        }

        @Override
        public synchronized SQLException getCause() {
            return (SQLException) super.getCause();
        }
    }
}
