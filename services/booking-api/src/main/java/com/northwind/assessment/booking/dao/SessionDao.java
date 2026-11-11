package com.northwind.assessment.booking.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.northwind.assessment.booking.model.SessionInfo;
import com.northwind.assessment.booking.util.DateUtils;

@Repository
public class SessionDao {

    private static final String SELECT_SESSION =
            "SELECT s.SessionId, e.ExamId, e.ExamCode, e.Title, c.CenterCode, c.Name AS CenterName, r.RoomName, "
          + "       s.StartsAt, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, s.Status "
          + "FROM dbo.ExamSession s "
          + "JOIN dbo.Exam e ON e.ExamId = s.ExamId "
          + "JOIN dbo.TestCenter c ON c.TestCenterId = s.TestCenterId "
          + "JOIN dbo.Room r ON r.RoomId = s.RoomId ";

    // partners page through by date range, 500 is plenty for one call
    private static final int MAX_ROWS = 500;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<SessionInfo> find(String examCode, String centerCode, Date from, Date to) {
        StringBuilder sql = new StringBuilder("SELECT TOP " + MAX_ROWS + " * FROM (" + SELECT_SESSION + ") x WHERE 1=1");
        List<Object> args = new ArrayList<Object>();
        if (examCode != null && examCode.length() > 0) {
            sql.append(" AND x.ExamCode = ?");
            args.add(examCode);
        }
        if (centerCode != null && centerCode.length() > 0) {
            sql.append(" AND x.CenterCode = ?");
            args.add(centerCode);
        }
        if (from != null) {
            sql.append(" AND x.StartsAt >= ?");
            args.add(new Timestamp(DateUtils.truncate(from).getTime()));
        }
        if (to != null) {
            // 'to' is inclusive of the whole day
            sql.append(" AND x.StartsAt < ?");
            args.add(new Timestamp(DateUtils.addDays(to, 1).getTime()));
        }
        sql.append(" ORDER BY x.StartsAt, x.SessionId");
        return jdbcTemplate.query(sql.toString(), args.toArray(), SESSION_MAPPER);
    }

    public SessionInfo findById(int sessionId) {
        List<SessionInfo> list = jdbcTemplate.query(SELECT_SESSION + "WHERE s.SessionId = ?", SESSION_MAPPER, sessionId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Exam id of a session, or null. */
    public Integer findExamId(int sessionId) {
        List<Integer> ids = jdbcTemplate.queryForList(
                "SELECT ExamId FROM dbo.ExamSession WHERE SessionId = ?", Integer.class, sessionId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public Date findStartsAt(int sessionId) {
        List<Date> d = jdbcTemplate.queryForList(
                "SELECT StartsAt FROM dbo.ExamSession WHERE SessionId = ?", Date.class, sessionId);
        return d.isEmpty() ? null : d.get(0);
    }

    public int readSeatsAvailable(int sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = ?", Integer.class, sessionId);
    }

    private static final RowMapper<SessionInfo> SESSION_MAPPER = new RowMapper<SessionInfo>() {
        public SessionInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            SessionInfo s = new SessionInfo();
            s.setSessionId(rs.getInt("SessionId"));
            s.setExamCode(rs.getString("ExamCode"));
            s.setExamTitle(rs.getString("Title"));
            s.setCenterCode(rs.getString("CenterCode"));
            s.setCenterName(rs.getString("CenterName"));
            s.setRoomName(rs.getString("RoomName"));
            s.setStartsAt(DateUtils.formatIsoDateTime(rs.getTimestamp("StartsAt")));
            s.setCapacity(rs.getInt("Capacity"));
            s.setAccommodationCapacity(rs.getInt("AccommodationCapacity"));
            s.setSeatsAvailable(rs.getInt("SeatsAvailable"));
            s.setStatus(rs.getString("Status"));
            return s;
        }
    };
}
