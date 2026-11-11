package com.northwind.scheduling.legacy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

/**
 * Reads AssessmentDelivery as login {@code ad_sched_import} (db/security/035_sched_import_login.sql:
 * SELECT on named tables only). SELECT statements only, all within AssessmentDelivery: the Scheduling DB
 * is written by {@code importer.SnapshotWriter} through a different connection, never joined across.
 */
@Component
public class JdbcLegacyReferenceSource implements LegacyReferenceSource {

    private final JdbcTemplate jdbc;

    public JdbcLegacyReferenceSource(@Value("${scheduling.legacy.url}") String url,
                                     @Value("${scheduling.legacy.username}") String user,
                                     @Value("${scheduling.legacy.password}") String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        this.jdbc = new JdbcTemplate(ds);
    }

    static LocalDateTime ts(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        // legacy DATETIME has 3ms ticks; booking-api prints whole seconds (DateUtils.ISO_DATE_TIME)
        return t == null ? null : t.toLocalDateTime().truncatedTo(ChronoUnit.SECONDS);
    }

    @Override
    public void ping() {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c -> {
            if (!c.isValid(3)) {
                throw new IllegalStateException("legacy connection is not valid");
            }
            return true;
        });
    }

    @Override
    public List<LegacyRows.Center> centers() {
        return jdbc.query("SELECT TestCenterId, CenterCode, Name, City, Region, TimeZone, IsActive, "
                + "CAST(BINARY_CHECKSUM(*) AS BIGINT) AS SourceVersion FROM dbo.TestCenter",
                (rs, i) -> new LegacyRows.Center(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getLong(8)));
    }

    @Override
    public List<LegacyRows.Room> rooms() {
        return jdbc.query("SELECT RoomId, TestCenterId, RoomName, SeatCount, AccommodationSeats, IsActive, "
                + "CAST(BINARY_CHECKSUM(*) AS BIGINT) AS SourceVersion FROM dbo.Room",
                (rs, i) -> new LegacyRows.Room(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4), rs.getInt(5),
                        rs.getBoolean(6), rs.getLong(7)));
    }

    @Override
    public List<LegacyRows.Exam> exams() {
        return jdbc.query("SELECT ExamId, ExamCode, Title, OwnerName, DurationMinutes, PassingScore, RetakeWaitDays, "
                + "IsPublished, CAST(BINARY_CHECKSUM(*) AS BIGINT) AS SourceVersion FROM dbo.Exam",
                (rs, i) -> new LegacyRows.Exam(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getBoolean(8), rs.getLong(9)));
    }

    @Override
    public List<LegacyRows.Candidate> candidates() {
        return jdbc.query("SELECT CandidateId, CandidateNumber, FirstName, LastName, Email, DefaultAccommodationCode, "
                + "CAST(BINARY_CHECKSUM(*) AS BIGINT) AS SourceVersion FROM dbo.Candidate",
                (rs, i) -> new LegacyRows.Candidate(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7)));
    }

    @Override
    public List<LegacyRows.Result> results() {
        return jdbc.query("SELECT r.ResultId, r.CandidateId, r.ExamId, r.SessionId, s.StartsAt, r.Score, r.Passed, "
                + "r.ScoredAt, CAST(BINARY_CHECKSUM(r.ResultId, r.Score, r.Passed, r.ScoredAt, r.SessionId) AS BIGINT) "
                + "FROM dbo.Result r JOIN dbo.ExamSession s ON s.SessionId = r.SessionId",
                (rs, i) -> new LegacyRows.Result(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        ts(rs, "StartsAt"), rs.getInt(6), rs.getBoolean(7), ts(rs, "ScoredAt"), rs.getLong(9)));
    }

    @Override
    public List<LegacyRows.Session> sessions() {
        return jdbc.query("SELECT SessionId, ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, "
                + "Status FROM dbo.ExamSession",
                (rs, i) -> new LegacyRows.Session(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        ts(rs, "StartsAt"), rs.getInt(6), rs.getInt(7), rs.getString(8)));
    }

    @Override
    public List<LegacyRows.Booking> bookings() {
        return jdbc.query("SELECT BookingId, SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef, "
                + "BookedAt, CancelledAt, CancelReason FROM dbo.Booking",
                (rs, i) -> new LegacyRows.Booking(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), ts(rs, "BookedAt"), ts(rs, "CancelledAt"),
                        rs.getString(10)));
    }

    @Override
    public List<LegacyRows.Waitlist> waitlist() {
        return jdbc.query("SELECT WaitlistId, SessionId, CandidateId, BookingId, Position, AddedAt FROM dbo.Waitlist",
                (rs, i) -> new LegacyRows.Waitlist(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                        ts(rs, "AddedAt")));
    }
}
