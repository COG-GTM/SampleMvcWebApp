package com.northwind.scheduling.core;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Former trg_ExamSession_Update capacity-cut branch (db/triggers/033_trg_ExamSession_Update.sql), now explicit.
 * Called in the same transaction, after the Capacity column was lowered, by change-capacity and by the
 * direct-write compatibility API. Keeps the trigger's quirks: only BOOKED rows are counted (ATTENDED and
 * accommodation bookings are ignored), the newest bookings go first (BookedAt DESC, BookingId DESC), they are
 * appended to the waitlist oldest-first, and SeatsAvailable is recounted as Capacity - BOOKED (HB-05).
 */
@Component
public class Demotion {

    private static final String SQL = """
            SET NOCOUNT ON;
            DECLARE @b TABLE (BookingId INT PRIMARY KEY, CandidateId INT, BookedAt DATETIME);
            ;WITH bk AS (
                SELECT b.BookingId, b.CandidateId, b.BookedAt,
                       ROW_NUMBER() OVER (ORDER BY b.BookedAt DESC, b.BookingId DESC) AS rn,
                       COUNT(*) OVER () AS cnt
                  FROM dbo.Booking b
                 WHERE b.SessionId = ? AND b.Status = 'BOOKED'
            )
            INSERT INTO @b (BookingId, CandidateId, BookedAt)
            SELECT BookingId, CandidateId, BookedAt FROM bk WHERE rn <= cnt - ?;
            IF EXISTS (SELECT 1 FROM @b)
            BEGIN
                UPDATE b SET b.Status = 'WAITLISTED', b.LastModified = GETDATE()
                  FROM dbo.Booking b JOIN @b t ON t.BookingId = b.BookingId;
                DECLARE @maxPos INT = (SELECT ISNULL(MAX(Position), 0) FROM dbo.Waitlist WHERE SessionId = ?);
                INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position)
                SELECT ?, t.CandidateId, t.BookingId, @maxPos + ROW_NUMBER() OVER (ORDER BY t.BookedAt, t.BookingId)
                  FROM @b t;
                UPDATE dbo.ExamSession
                   SET SeatsAvailable = Capacity - (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId = ? AND Status = 'BOOKED')
                 WHERE SessionId = ?;
            END
            SELECT BookingId FROM @b ORDER BY BookingId;
            """;

    private final JdbcTemplate jdbc;

    public Demotion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return demoted booking ids; empty when the capacity did not go down or nobody had to move. */
    public List<Integer> afterCapacityChange(int sessionId, int oldCapacity, int newCapacity) {
        if (newCapacity >= oldCapacity) {
            return List.of();
        }
        return jdbc.queryForList(SQL, Integer.class, sessionId, newCapacity, sessionId, sessionId, sessionId, sessionId);
    }
}
