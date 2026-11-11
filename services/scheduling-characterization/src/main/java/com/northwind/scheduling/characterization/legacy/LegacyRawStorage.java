package com.northwind.scheduling.characterization.legacy;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.northwind.scheduling.characterization.target.RawStorageAccess;

/** Direct row writes against the legacy tables (the triggers in db/triggers fire as for any other writer). */
final class LegacyRawStorage implements RawStorageAccess {

    private final LegacyProcTarget t;

    LegacyRawStorage(LegacyProcTarget t) {
        this.t = t;
    }

    @Override
    public int insertBooking(int sessionId, int candidateId, String status, String channel, String accommodationCode) {
        // SCOPE_IDENTITY rather than OUTPUT: OUTPUT without INTO fails on trigger tables (HB-16)
        if (channel == null) {
            return t.queryInt("SET NOCOUNT ON; INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode) "
                    + "VALUES (?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);", sessionId, candidateId, status, accommodationCode);
        }
        return t.queryInt("SET NOCOUNT ON; INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel) "
                + "VALUES (?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);", sessionId, candidateId, status, accommodationCode, channel);
    }

    @Override
    public List<Integer> insertBookings(List<int[]> sessionAndCandidate, String status, String channel) {
        StringBuilder sql = new StringBuilder("SET NOCOUNT ON; DECLARE @t TABLE (BookingId INT); "
                + "INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) OUTPUT INSERTED.BookingId INTO @t VALUES ");
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < sessionAndCandidate.size(); i++) {
            sql.append(i == 0 ? "" : ", ").append("(?, ?, ?, ?)");
            args.add(sessionAndCandidate.get(i)[0]);
            args.add(sessionAndCandidate.get(i)[1]);
            args.add(status);
            args.add(channel);
        }
        sql.append("; SELECT BookingId FROM @t ORDER BY BookingId;");
        return t.withConnection(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                LegacyProcTarget.bind(ps, args.toArray());
                List<Integer> ids = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ids.add(rs.getInt(1));
                    }
                }
                return ids;
            }
        });
    }

    @Override
    public void updateBookingStatus(int bookingId, String status) {
        t.update("UPDATE dbo.Booking SET Status = ?, LastModified = GETDATE() WHERE BookingId = ?", status, bookingId);
    }

    @Override
    public void deleteBooking(int bookingId) {
        t.update("DELETE FROM dbo.Waitlist WHERE BookingId = ?; DELETE FROM dbo.Booking WHERE BookingId = ?", bookingId, bookingId);
    }

    @Override
    public void setBookedAt(int bookingId, LocalDateTime bookedAt) {
        t.update("UPDATE dbo.Booking SET BookedAt = ? WHERE BookingId = ?", bookedAt, bookingId);
    }

    @Override
    public void adjustSeatsAvailable(int sessionId, int delta) {
        t.update("UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable + ?, LastModified = GETDATE() WHERE SessionId = ?", delta, sessionId);
    }

    @Override
    public void updateCapacity(int sessionId, int capacity) {
        t.update("UPDATE dbo.ExamSession SET Capacity = ? WHERE SessionId = ?", capacity, sessionId);
    }

    @Override
    public void updateCapacities(Map<Integer, Integer> capacityBySession) {
        StringBuilder cases = new StringBuilder("UPDATE dbo.ExamSession SET Capacity = CASE SessionId ");
        List<Object> args = new ArrayList<>();
        StringBuilder in = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : capacityBySession.entrySet()) {
            cases.append("WHEN ? THEN ? ");
            args.add(e.getKey());
            args.add(e.getValue());
            in.append(in.length() == 0 ? "" : ",").append(e.getKey());
        }
        cases.append("END WHERE SessionId IN (").append(in).append(")");
        t.update(cases.toString(), args.toArray());
    }

    @Override
    public void setSessionStatus(int sessionId, String status) {
        t.update("UPDATE dbo.ExamSession SET Status = ? WHERE SessionId = ?", status, sessionId);
    }

    @Override
    public void setLastModified(int sessionId, LocalDateTime value) {
        t.update("UPDATE dbo.ExamSession SET LastModified = ? WHERE SessionId = ?", value, sessionId);
    }

    @Override
    public int partnerBook(int sessionId, int candidateId, String accommodationCode) {
        // booking-api BookingService.book -> SessionDao.readSeatsAvailable / BookingDao.insertBooking /
        // SessionDao.decrementSeatsAvailable / BookingDao.insertWaitlist / BookingDao.audit, Channel from booking.channel=PARTNER
        int seats = t.queryInt("SELECT SeatsAvailable FROM dbo.ExamSession WHERE SessionId = ?", sessionId);
        String status = seats > 0 ? "BOOKED" : "WAITLISTED";
        int bookingId = t.queryInt("SET NOCOUNT ON; INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef) "
                + "VALUES (?, ?, ?, ?, ?, ?); SELECT CAST(SCOPE_IDENTITY() AS INT);",
                sessionId, candidateId, status, accommodationCode, "PARTNER", "CHR-" + sessionId + "-" + candidateId);
        if (seats > 0) {
            t.update("UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable - 1, LastModified = GETDATE() WHERE SessionId = ?", sessionId);
        } else {
            int pos = t.queryInt("SELECT ISNULL(MAX(Position), 0) + 1 FROM dbo.Waitlist WHERE SessionId = ?", sessionId);
            t.update("INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position) VALUES (?, ?, ?, ?)",
                    sessionId, candidateId, bookingId, pos);
        }
        t.update("INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES (?, ?, ?, ?, ?)",
                "Booking", bookingId, "BOOK", "session=" + sessionId + " status=" + status + " channel=PARTNER", "booking-api");
        return bookingId;
    }
}
