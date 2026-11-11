package com.northwind.assessment.booking.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.northwind.assessment.booking.model.BookingInfo;
import com.northwind.assessment.booking.util.DateUtils;

/**
 * Booking table reads for the partner channel. MBA-3824 deleted the write SQL
 * (scheduling-service is the single writer, docs/adr/0003-single-writer-outbox-relay.md).
 */
@Repository
public class BookingDao {

    private static final String SELECT_BOOKING =
            "SELECT b.BookingId, b.SessionId, c.CandidateNumber, e.ExamCode, s.StartsAt, b.Status, "
          + "       b.AccommodationCode, b.Channel, b.ExternalRef, b.BookedAt, b.CancelledAt, b.CancelReason "
          + "FROM dbo.Booking b "
          + "JOIN dbo.Candidate c ON c.CandidateId = b.CandidateId "
          + "JOIN dbo.ExamSession s ON s.SessionId = b.SessionId "
          + "JOIN dbo.Exam e ON e.ExamId = s.ExamId ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public BookingInfo findById(int bookingId) {
        List<BookingInfo> list = jdbcTemplate.query(SELECT_BOOKING + "WHERE b.BookingId = ?", BOOKING_MAPPER, bookingId);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Every booking of the candidate, any channel (portal, partner, import). */
    public List<BookingInfo> findByCandidate(int candidateId) {
        return jdbcTemplate.query(SELECT_BOOKING + "WHERE b.CandidateId = ? ORDER BY b.BookedAt DESC, b.BookingId DESC",
                BOOKING_MAPPER, candidateId);
    }

    public int findCandidateId(int bookingId) {
        return jdbcTemplate.queryForObject("SELECT CandidateId FROM dbo.Booking WHERE BookingId = ?",
                Integer.class, bookingId);
    }


    private static final RowMapper<BookingInfo> BOOKING_MAPPER = new RowMapper<BookingInfo>() {
        public BookingInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            BookingInfo b = new BookingInfo();
            b.setBookingId(rs.getInt("BookingId"));
            b.setSessionId(rs.getInt("SessionId"));
            b.setCandidateNumber(rs.getString("CandidateNumber"));
            b.setExamCode(rs.getString("ExamCode"));
            b.setSessionStartsAt(DateUtils.formatIsoDateTime(rs.getTimestamp("StartsAt")));
            b.setStatus(rs.getString("Status"));
            b.setAccommodationCode(rs.getString("AccommodationCode"));
            b.setChannel(rs.getString("Channel"));
            b.setExternalRef(rs.getString("ExternalRef"));
            b.setBookedAt(DateUtils.formatIsoDateTime(rs.getTimestamp("BookedAt")));
            b.setCancelledAt(DateUtils.formatIsoDateTime(rs.getTimestamp("CancelledAt")));
            b.setCancelReason(rs.getString("CancelReason"));
            return b;
        }
    };
}
