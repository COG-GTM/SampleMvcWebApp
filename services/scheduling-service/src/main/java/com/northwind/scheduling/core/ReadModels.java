package com.northwind.scheduling.core;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.northwind.scheduling.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read models for the query endpoints, enriched from the reference replicas (LEFT JOINs: no FKs). */
@Service
public class ReadModels {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Session(int sessionId, int examId, String examCode, String examTitle, Integer durationMinutes,
                          int testCenterId, String centerCode, String centerName, String city, int roomId, String roomName,
                          LocalDateTime startsAt, int capacity, int accommodationCapacity, int seatsAvailable, String status,
                          int bookedSeats, int waitlistCount, LocalDateTime lastModified, @JsonIgnore String etag) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Booking(int bookingId, int sessionId, int candidateId, String candidateNumber, Integer examId,
                          String examCode, String examTitle, String centerName, LocalDateTime sessionStartsAt, String status,
                          String accommodationCode, String channel, String externalRef, LocalDateTime bookedAt,
                          LocalDateTime cancelledAt, String cancelReason, Integer waitlistPosition) {
    }

    public record WaitlistEntry(int waitlistId, int sessionId, int bookingId, int candidateId, String candidateNumber,
                                int position, LocalDateTime addedAt) {
    }

    public record RosterLine(String candidateNumber, String lastName, String firstName, String email, String bookingStatus,
                             String accommodationCode, String channel, LocalDateTime bookedAt, Integer waitlistPosition) {
    }

    public record Roster(int sessionId, String centerCode, String examCode, LocalDateTime startsAt, List<RosterLine> lines) {
    }

    public record CenterSummary(int testCenterId, int sessionCount, int openSessionCount) {
    }

    public record AuditRow(long auditId, LocalDateTime loggedAt, String actor, int sessionId, String examCode,
                           LocalDateTime sessionStartsAt, String roomName, Integer oldCapacity, Integer newCapacity,
                           Integer delta, int currentCapacity, int currentSeatsAvailable, String detail) {
    }

    public record AuditPage(int totalRows, List<AuditRow> rows) {
    }

    public record AuditEntry(long auditId, String entityName, int entityId, String action, String detail, String actor,
                             LocalDateTime loggedAt) {
    }

    public record SessionQuery(String status, String examCode, Integer examId, String centerCode, Integer testCenterId,
                               LocalDate from, LocalDate to, LocalDateTime startsAfter, Integer excludeSessionId,
                               Integer minSeatsAvailable, String sort, Integer limit, Integer offset) {
    }

    private static final String SESSION_SELECT = """
            SELECT s.SessionId, s.ExamId, e.ExamCode, e.Title, e.DurationMinutes, s.TestCenterId, tc.CenterCode, tc.Name AS CenterName,
                   tc.City, s.RoomId, r.RoomName, s.StartsAt, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, s.Status,
                   (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status = 'BOOKED') AS BookedSeats,
                   (SELECT COUNT(*) FROM dbo.Waitlist w WHERE w.SessionId = s.SessionId) AS WaitlistCount,
                   s.LastModified, s.RowVer
              FROM dbo.ExamSession s
              LEFT JOIN dbo.RefExam e ON e.ExamId = s.ExamId
              LEFT JOIN dbo.RefTestCenter tc ON tc.TestCenterId = s.TestCenterId
              LEFT JOIN dbo.RefRoom r ON r.RoomId = s.RoomId
            """;

    private static final RowMapper<Session> SESSION = (rs, i) -> new Session(rs.getInt("SessionId"), rs.getInt("ExamId"),
            rs.getString("ExamCode"), rs.getString("Title"), (Integer) rs.getObject("DurationMinutes"), rs.getInt("TestCenterId"),
            rs.getString("CenterCode"), rs.getString("CenterName"), rs.getString("City"), rs.getInt("RoomId"),
            rs.getString("RoomName"), ts(rs, "StartsAt"), rs.getInt("Capacity"), rs.getInt("AccommodationCapacity"),
            rs.getInt("SeatsAvailable"), rs.getString("Status"), rs.getInt("BookedSeats"), rs.getInt("WaitlistCount"),
            ts(rs, "LastModified"), ETags.of(rs.getBytes("RowVer")));

    private static final String BOOKING_SELECT = """
            SELECT b.BookingId, b.SessionId, b.CandidateId, c.CandidateNumber, s.ExamId, e.ExamCode, e.Title, tc.Name AS CenterName,
                   s.StartsAt, b.Status, b.AccommodationCode, b.Channel, b.ExternalRef, b.BookedAt, b.CancelledAt, b.CancelReason,
                   (SELECT TOP 1 w.Position FROM dbo.Waitlist w WHERE w.BookingId = b.BookingId ORDER BY w.WaitlistId) AS WaitlistPosition
              FROM dbo.Booking b
              LEFT JOIN dbo.ExamSession s ON s.SessionId = b.SessionId
              LEFT JOIN dbo.RefCandidate c ON c.CandidateId = b.CandidateId
              LEFT JOIN dbo.RefExam e ON e.ExamId = s.ExamId
              LEFT JOIN dbo.RefTestCenter tc ON tc.TestCenterId = s.TestCenterId
            """;

    private static final RowMapper<Booking> BOOKING = (rs, i) -> new Booking(rs.getInt("BookingId"), rs.getInt("SessionId"),
            rs.getInt("CandidateId"), rs.getString("CandidateNumber"), (Integer) rs.getObject("ExamId"), rs.getString("ExamCode"),
            rs.getString("Title"), rs.getString("CenterName"), ts(rs, "StartsAt"), rs.getString("Status"),
            rs.getString("AccommodationCode"), rs.getString("Channel"), rs.getString("ExternalRef"), ts(rs, "BookedAt"),
            ts(rs, "CancelledAt"), rs.getString("CancelReason"), (Integer) rs.getObject("WaitlistPosition"));

    private final JdbcTemplate jdbc;

    public ReadModels(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    static LocalDateTime ts(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    public Optional<Session> session(int sessionId) {
        List<Session> l = jdbc.query(SESSION_SELECT + " WHERE s.SessionId = ?", SESSION, sessionId);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public Session requireSession(int sessionId) {
        return session(sessionId).orElseThrow(() ->
                ApiException.notFound("SESSION_NOT_FOUND", "Session " + sessionId + " not found"));
    }

    public List<Session> sessions(SessionQuery q) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (q.status() != null) {
            where.append(" AND s.Status = ?");
            args.add(q.status().toUpperCase(Locale.ROOT));
        }
        if (q.examCode() != null) {
            where.append(" AND e.ExamCode = ?");
            args.add(q.examCode().trim().toUpperCase(Locale.ROOT));
        }
        if (q.examId() != null) {
            where.append(" AND s.ExamId = ?");
            args.add(q.examId());
        }
        if (q.centerCode() != null) {
            where.append(" AND tc.CenterCode = ?");
            args.add(q.centerCode().trim().toUpperCase(Locale.ROOT));
        }
        if (q.testCenterId() != null) {
            where.append(" AND s.TestCenterId = ?");
            args.add(q.testCenterId());
        }
        if (q.from() != null) {
            where.append(" AND s.StartsAt >= ?");
            args.add(q.from().atStartOfDay());
        }
        if (q.to() != null) {
            where.append(" AND s.StartsAt < ?");
            args.add(q.to().plusDays(1).atStartOfDay());
        }
        if (q.startsAfter() != null) {
            where.append(" AND s.StartsAt > ?");
            args.add(q.startsAfter());
        }
        if (q.excludeSessionId() != null) {
            where.append(" AND s.SessionId <> ?");
            args.add(q.excludeSessionId());
        }
        if (q.minSeatsAvailable() != null) {
            where.append(" AND s.SeatsAvailable >= ?");
            args.add(q.minSeatsAvailable());
        }
        String order = switch (q.sort() == null ? "startsAt" : q.sort()) {
            case "sessionId" -> " ORDER BY s.SessionId";
            case "-startsAt" -> " ORDER BY s.StartsAt DESC, s.SessionId DESC";
            case "seatsAvailable" -> " ORDER BY s.SeatsAvailable, s.StartsAt, s.SessionId";
            case "-seatsAvailable" -> " ORDER BY s.SeatsAvailable DESC, s.StartsAt, s.SessionId";
            default -> " ORDER BY s.StartsAt, s.SessionId";
        };
        int limit = q.limit() == null ? 200 : Math.max(1, Math.min(q.limit(), 5000));
        int offset = q.offset() == null ? 0 : Math.max(0, q.offset());
        args.add(offset);
        args.add(limit);
        return jdbc.query(SESSION_SELECT + where + order + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", SESSION, args.toArray());
    }

    public List<CenterSummary> summaryByTestCenter() {
        return jdbc.query("SELECT TestCenterId, COUNT(*), SUM(CASE WHEN Status = 'OPEN' THEN 1 ELSE 0 END) FROM dbo.ExamSession "
                + "GROUP BY TestCenterId ORDER BY TestCenterId", (rs, i) -> new CenterSummary(rs.getInt(1), rs.getInt(2), rs.getInt(3)));
    }

    public Optional<Booking> booking(int bookingId) {
        List<Booking> l = jdbc.query(BOOKING_SELECT + " WHERE b.BookingId = ?", BOOKING, bookingId);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
    }

    public Booking requireBooking(int bookingId) {
        return booking(bookingId).orElseThrow(() ->
                ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found"));
    }

    public List<Booking> bookings(Integer candidateId, String candidateNumber, Integer sessionId, String status, String sort,
                                  Integer limit) {
        if (candidateId == null && candidateNumber == null && sessionId == null) {
            throw ApiException.badRequest("VALIDATION", "candidateId, candidateNumber or sessionId is required");
        }
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (candidateId != null) {
            where.append(" AND b.CandidateId = ?");
            args.add(candidateId);
        }
        if (candidateNumber != null) {
            where.append(" AND c.CandidateNumber = ?");
            args.add(candidateNumber.trim().toUpperCase(Locale.ROOT));
        }
        if (sessionId != null) {
            where.append(" AND b.SessionId = ?");
            args.add(sessionId);
        }
        if (status != null) {
            // OpenAPI: status is a form-style array (status=BOOKED,ATTENDED)
            List<String> statuses = Arrays.stream(status.split(",")).map(String::trim).filter(v -> !v.isEmpty())
                    .map(v -> v.toUpperCase(Locale.ROOT)).toList();
            where.append(" AND b.Status IN (").append(String.join(", ", Collections.nCopies(statuses.size(), "?"))).append(")");
            args.addAll(statuses);
        }
        String order = switch (sort == null ? "-bookedAt" : sort) {
            case "bookingId" -> " ORDER BY b.BookingId";
            case "bookingId,desc" -> " ORDER BY b.BookingId DESC";
            case "bookedAt" -> " ORDER BY b.BookedAt, b.BookingId";
            case "sessionStartsAt" -> " ORDER BY s.StartsAt, b.BookingId";
            case "sessionStartsAt,desc" -> " ORDER BY s.StartsAt DESC, b.BookingId DESC";
            default -> " ORDER BY b.BookedAt DESC, b.BookingId DESC";
        };
        int top = limit == null ? 1000 : Math.max(1, Math.min(limit, 10000));
        return jdbc.query(BOOKING_SELECT.replaceFirst("SELECT", "SELECT TOP (" + top + ")") + where + order, BOOKING, args.toArray());
    }

    public List<WaitlistEntry> waitlist(int sessionId) {
        requireSession(sessionId);
        return jdbc.query("SELECT w.WaitlistId, w.SessionId, w.BookingId, w.CandidateId, c.CandidateNumber, w.Position, w.AddedAt "
                        + "FROM dbo.Waitlist w LEFT JOIN dbo.RefCandidate c ON c.CandidateId = w.CandidateId "
                        + "WHERE w.SessionId = ? ORDER BY w.Position, w.WaitlistId",
                (rs, i) -> new WaitlistEntry(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getString(5), rs.getInt(6),
                        ts(rs, "AddedAt")), sessionId);
    }

    public Roster roster(int sessionId) {
        Session s = requireSession(sessionId);
        List<RosterLine> lines = jdbc.query("""
                SELECT c.CandidateNumber, c.LastName, c.FirstName, c.Email, b.Status, b.AccommodationCode, b.Channel, b.BookedAt,
                       (SELECT TOP 1 w.Position FROM dbo.Waitlist w WHERE w.BookingId = b.BookingId ORDER BY w.WaitlistId) AS Pos
                  FROM dbo.Booking b LEFT JOIN dbo.RefCandidate c ON c.CandidateId = b.CandidateId
                 WHERE b.SessionId = ? AND b.Status IN ('BOOKED','ATTENDED','WAITLISTED')
                 ORDER BY CASE WHEN b.Status = 'WAITLISTED' THEN 1 ELSE 0 END, Pos, c.LastName, c.FirstName, b.BookingId""",
                (rs, i) -> new RosterLine(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), ts(rs, "BookedAt"), (Integer) rs.getObject("Pos")), sessionId);
        return new Roster(sessionId, s.centerCode(), s.examCode(), s.startsAt(), lines);
    }

    private static final Pattern CAPACITY_DETAIL = Pattern.compile("^\\s*(-?\\d+)\\s*->\\s*(-?\\d+)\\s*$");

    /** usp_GetCapacityAudit (db/procs/030) equivalent: CAPACITY rows on ExamSession, newest first. */
    public AuditPage capacityAudit(Integer testCenterId, Integer sessionId, LocalDate from, LocalDate to, int page, int pageSize) {
        String where = " WHERE a.EntityName = 'ExamSession' AND a.Action = 'CAPACITY'"
                + " AND (? IS NULL OR s.TestCenterId = ?) AND (? IS NULL OR a.EntityId = ?)"
                + " AND (? IS NULL OR a.LoggedAt >= ?) AND (? IS NULL OR a.LoggedAt < ?)";
        LocalDateTime f = from == null ? null : from.atStartOfDay();
        LocalDateTime t = to == null ? null : to.plusDays(1).atStartOfDay();
        Object[] filter = {testCenterId, testCenterId, sessionId, sessionId, f, f, t, t};
        String fromClause = " FROM dbo.AuditLog a JOIN dbo.ExamSession s ON s.SessionId = a.EntityId"
                + " LEFT JOIN dbo.RefExam e ON e.ExamId = s.ExamId LEFT JOIN dbo.RefRoom r ON r.RoomId = s.RoomId";
        Integer total = jdbc.queryForObject("SELECT COUNT(*)" + fromClause + where, Integer.class, filter);
        List<Object> args = new ArrayList<>(List.of(filter));
        int size = Math.max(1, Math.min(pageSize, 500));
        args.add(Math.max(0, page - 1) * size);
        args.add(size);
        List<AuditRow> rows = jdbc.query("SELECT a.AuditId, a.LoggedAt, a.Actor, s.SessionId, e.ExamCode, s.StartsAt, r.RoomName, "
                + "a.Detail, s.Capacity, s.SeatsAvailable" + fromClause + where
                + " ORDER BY a.LoggedAt DESC, a.AuditId DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY", (rs, i) -> {
            String detail = rs.getString("Detail");
            Integer oldC = null;
            Integer newC = null;
            if (detail != null) {
                Matcher m = CAPACITY_DETAIL.matcher(detail);
                if (m.matches()) {
                    oldC = Integer.valueOf(m.group(1));
                    newC = Integer.valueOf(m.group(2));
                }
            }
            return new AuditRow(rs.getLong("AuditId"), ts(rs, "LoggedAt"), rs.getString("Actor"), rs.getInt("SessionId"),
                    rs.getString("ExamCode"), ts(rs, "StartsAt"), rs.getString("RoomName"), oldC, newC,
                    oldC == null ? null : newC - oldC, rs.getInt("Capacity"), rs.getInt("SeatsAvailable"), detail);
        }, args.toArray());
        return new AuditPage(total == null ? 0 : total, rows);
    }

    public List<AuditEntry> audit(String entityName, int entityId) {
        return jdbc.query("SELECT AuditId, EntityName, EntityId, Action, Detail, Actor, LoggedAt FROM dbo.AuditLog "
                        + "WHERE EntityName = ? AND EntityId = ? ORDER BY AuditId",
                (rs, i) -> new AuditEntry(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), ts(rs, "LoggedAt")), entityName, entityId);
    }
}
