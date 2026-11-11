package com.northwind.assessment.scheduling.persistence;

import com.northwind.assessment.scheduling.domain.SeatKind;
import com.northwind.assessment.scheduling.domain.SeatOfferStatus;
import com.northwind.assessment.scheduling.persistence.Views.OfferView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** seat_offer (V3__seat_offer.sql). State changes run under the owning session's row lock. */
@Repository
public class SeatOfferRepository {

    private static final String OFFER_VIEW =
            "SELECT o.offer_id, o.session_id, o.booking_id, o.candidate_id, c.candidate_number, o.waitlist_position, "
          + "       o.status, o.seat_kind, o.offered_at, o.expires_at, o.decided_at, e.exam_code, s.starts_at, t.center_code "
          + "FROM seat_offer o "
          + "JOIN exam_session s ON s.session_id = o.session_id "
          + "LEFT JOIN candidate_snapshot c ON c.candidate_id = o.candidate_id "
          + "LEFT JOIN exam_snapshot e ON e.exam_id = s.exam_id "
          + "LEFT JOIN test_center_snapshot t ON t.center_id = s.center_id ";

    private final JdbcClient jdbc;

    public SeatOfferRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insertPending(int sessionId, long bookingId, Integer waitlistEntryId, int candidateId, int position,
                              SeatKind kind, LocalDateTime offeredAt, LocalDateTime expiresAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO seat_offer (session_id, booking_id, waitlist_entry_id, candidate_id, waitlist_position, "
                        + "status, seat_kind, offered_at, expires_at) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)")
                .params(sessionId, bookingId, waitlistEntryId, candidateId, position, kind.name(), offeredAt, expiresAt)
                .update(keys, "offer_id");
        return keys.getKey().longValue();
    }

    /** PENDING -> {@code status}; returns false when the offer was no longer pending. */
    public boolean decide(long offerId, SeatOfferStatus status, String decidedBy, LocalDateTime now) {
        return jdbc.sql("UPDATE seat_offer SET status = ?, decided_at = ?, decided_by = ? "
                        + "WHERE offer_id = ? AND status = 'PENDING'")
                .params(status.name(), now, decidedBy, offerId).update() == 1;
    }

    public Optional<OfferView> find(long offerId) {
        return jdbc.sql(OFFER_VIEW + "WHERE o.offer_id = ?").param(offerId).query(SeatOfferRepository::map).optional();
    }

    public Optional<OfferView> pendingForBooking(long bookingId) {
        return jdbc.sql(OFFER_VIEW + "WHERE o.booking_id = ? AND o.status = 'PENDING'").param(bookingId)
                .query(SeatOfferRepository::map).optional();
    }

    /** Most recent offer of each of the candidate's bookings, newest first. */
    public List<OfferView> byCandidate(int candidateId) {
        return jdbc.sql(OFFER_VIEW + "WHERE o.candidate_id = ? ORDER BY o.offered_at DESC, o.offer_id DESC")
                .param(candidateId).query(SeatOfferRepository::map).list();
    }

    public List<OfferView> pendingForSession(int sessionId) {
        return jdbc.sql(OFFER_VIEW + "WHERE o.session_id = ? AND o.status = 'PENDING' ORDER BY o.waitlist_position")
                .param(sessionId).query(SeatOfferRepository::map).list();
    }

    /** Sessions holding a PENDING offer whose expires_at has passed (the expiry job's work list). */
    public List<Integer> sessionsWithExpiredOffers(LocalDateTime now) {
        return jdbc.sql("SELECT DISTINCT session_id FROM seat_offer WHERE status = 'PENDING' AND expires_at <= ? ORDER BY session_id")
                .param(Timestamp.valueOf(now)).query(Integer.class).list();
    }

    public Optional<Integer> sessionIdOf(long offerId) {
        return jdbc.sql("SELECT session_id FROM seat_offer WHERE offer_id = ?").param(offerId).query(Integer.class).optional();
    }

    static OfferView map(ResultSet rs, int n) throws SQLException {
        Timestamp decided = rs.getTimestamp("decided_at");
        Timestamp starts = rs.getTimestamp("starts_at");
        return new OfferView(rs.getLong("offer_id"), rs.getInt("session_id"), rs.getLong("booking_id"),
                rs.getInt("candidate_id"), rs.getString("candidate_number"), rs.getInt("waitlist_position"),
                rs.getString("status"), rs.getString("seat_kind"), rs.getTimestamp("offered_at").toLocalDateTime(),
                rs.getTimestamp("expires_at").toLocalDateTime(), decided == null ? null : decided.toLocalDateTime(),
                rs.getString("exam_code"), starts == null ? null : starts.toLocalDateTime(), rs.getString("center_code"));
    }
}
