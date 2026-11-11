package com.northwind.assessment.scheduling.legacy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * SELECT-only reads from AssessmentDelivery through the {@code ad_sched_import} login (db_datareader).
 * Every query runs inside AssessmentDelivery; nothing here joins with, or writes to, another database.
 */
@Component
public class JdbcLegacyReferenceSource implements LegacyReferenceSource {


    private final JdbcClient jdbc;

    public JdbcLegacyReferenceSource(@Qualifier("legacy") JdbcClient legacyJdbcClient) {
        this.jdbc = legacyJdbcClient;
    }

    @Override
    public List<LegacyRows.TestCenter> testCenters() {
        return jdbc.sql("SELECT TestCenterId, CenterCode, Name, City, Region, TimeZone, IsActive FROM dbo.TestCenter")
                .query((rs, n) -> new LegacyRows.TestCenter(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getBoolean(7)))
                .list();
    }

    @Override
    public List<LegacyRows.Room> rooms() {
        return jdbc.sql("SELECT RoomId, TestCenterId, RoomName, SeatCount, AccommodationSeats, IsActive FROM dbo.Room")
                .query((rs, n) -> new LegacyRows.Room(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getInt(4),
                        rs.getInt(5), rs.getBoolean(6)))
                .list();
    }

    @Override
    public List<LegacyRows.Exam> exams() {
        return jdbc.sql("SELECT ExamId, ExamCode, Title, DurationMinutes, PassingScore, RetakeWaitDays, IsPublished FROM dbo.Exam")
                .query((rs, n) -> new LegacyRows.Exam(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getBoolean(7)))
                .list();
    }

    @Override
    public List<LegacyRows.Candidate> candidates() {
        return jdbc.sql("SELECT CandidateId, CandidateNumber, FirstName, LastName, Email, DefaultAccommodationCode FROM dbo.Candidate")
                .query((rs, n) -> new LegacyRows.Candidate(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)))
                .list();
    }

    @Override
    public List<LegacyRows.Result> results() {
        return jdbc.sql("SELECT r.ResultId, r.CandidateId, r.ExamId, r.SessionId, s.StartsAt, r.Score, r.Passed "
                        + "FROM dbo.Result r JOIN dbo.ExamSession s ON s.SessionId = r.SessionId")
                .query((rs, n) -> new LegacyRows.Result(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        ts(rs, 5), rs.getInt(6), rs.getBoolean(7)))
                .list();
    }

    @Override
    public List<LegacyRows.ExamSession> sessions(Integer testCenterId) {
        return jdbc.sql("SELECT s.SessionId, s.ExamId, s.TestCenterId, s.RoomId, s.StartsAt, s.Capacity, "
                        + "s.AccommodationCapacity, s.Status, s.LastModified FROM dbo.ExamSession s" + centerFilter(testCenterId))
                .params(centerParams(testCenterId))
                .query((rs, n) -> new LegacyRows.ExamSession(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        ts(rs, 5), rs.getInt(6), rs.getInt(7), rs.getString(8), ts(rs, 9)))
                .list();
    }

    @Override
    public List<LegacyRows.Booking> bookings(Integer testCenterId) {
        return jdbc.sql("SELECT b.BookingId, b.SessionId, b.CandidateId, b.Status, b.AccommodationCode, b.Channel, "
                        + "b.ExternalRef, b.BookedAt, b.CancelledAt, b.CancelReason, b.LastModified "
                        + "FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId = b.SessionId" + centerFilter(testCenterId))
                .params(centerParams(testCenterId))
                .query((rs, n) -> new LegacyRows.Booking(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), ts(rs, 8), ts(rs, 9), rs.getString(10),
                        ts(rs, 11)))
                .list();
    }

    @Override
    public List<LegacyRows.Waitlist> waitlist(Integer testCenterId) {
        return jdbc.sql("SELECT w.WaitlistId, w.SessionId, w.CandidateId, w.BookingId, w.Position, w.AddedAt "
                        + "FROM dbo.Waitlist w JOIN dbo.ExamSession s ON s.SessionId = w.SessionId" + centerFilter(testCenterId))
                .params(centerParams(testCenterId))
                .query((rs, n) -> new LegacyRows.Waitlist(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), ts(rs, 6)))
                .list();
    }

    private static String centerFilter(Integer testCenterId) {
        return testCenterId == null ? "" : " WHERE s.TestCenterId = :centerId";
    }

    private static Map<String, Object> centerParams(Integer testCenterId) {
        return testCenterId == null ? Map.of() : Map.of("centerId", testCenterId);
    }

    private static LocalDateTime ts(ResultSet rs, int col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }
}
