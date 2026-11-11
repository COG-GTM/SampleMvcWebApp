/*
    trg_ExamSession_Update - maintains ExamSession.LastModified.
    (Not every caller remembers to set it - the Java services in particular.)
    2014-11

    MBA-3823: for writes made by the scheduling-service relay
    (SESSION_CONTEXT writer = scheduling-relay) only the LastModified stamp runs.
    The newest-first demotion, waitlist bump and SeatsAvailable recompute
    (docs/as-is HB-05 / HB-16, MR-01) are skipped because the relay already
    writes the bookings, waitlist rows and seat count scheduling-service decided.
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.trg_ExamSession_Update') IS NOT NULL DROP TRIGGER dbo.trg_ExamSession_Update;
GO
CREATE TRIGGER dbo.trg_ExamSession_Update ON dbo.ExamSession
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF TRIGGER_NESTLEVEL(@@PROCID) > 1 RETURN;
    IF NOT EXISTS (SELECT 1 FROM inserted) RETURN;

    UPDATE s
       SET s.LastModified = GETDATE()
      FROM dbo.ExamSession s
      JOIN inserted i ON i.SessionId = s.SessionId;

    IF CONVERT(NVARCHAR(64), SESSION_CONTEXT(N'writer')) = N'scheduling-relay' RETURN;

    IF UPDATE(Capacity)
    BEGIN
        DECLARE @b TABLE (BookingId INT PRIMARY KEY, SessionId INT, CandidateId INT, BookedAt DATETIME);

        ;WITH sess AS (
            SELECT i.SessionId, i.Capacity
              FROM inserted i
              JOIN deleted d ON d.SessionId = i.SessionId
             WHERE i.Capacity < d.Capacity
        ), bk AS (
            SELECT b.BookingId, b.SessionId, b.CandidateId, b.BookedAt, x.Capacity,
                   ROW_NUMBER() OVER (PARTITION BY b.SessionId ORDER BY b.BookedAt DESC, b.BookingId DESC) AS rn,
                   COUNT(*) OVER (PARTITION BY b.SessionId) AS cnt
              FROM dbo.Booking b
              JOIN sess x ON x.SessionId = b.SessionId
             WHERE b.Status = 'BOOKED'
        )
        INSERT INTO @b (BookingId, SessionId, CandidateId, BookedAt)
        SELECT BookingId, SessionId, CandidateId, BookedAt
          FROM bk
         WHERE rn <= cnt - Capacity;

        IF EXISTS (SELECT 1 FROM @b)
        BEGIN
            UPDATE b
               SET b.Status = 'WAITLISTED', b.LastModified = GETDATE()
              FROM dbo.Booking b
              JOIN @b t ON t.BookingId = b.BookingId;

            DECLARE @p TABLE (SessionId INT PRIMARY KEY, MaxPos INT);
            INSERT INTO @p (SessionId, MaxPos)
            SELECT w.SessionId, MAX(w.Position)
              FROM dbo.Waitlist w
             WHERE w.SessionId IN (SELECT SessionId FROM @b)
             GROUP BY w.SessionId;

            INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position)
            SELECT t.SessionId, t.CandidateId, t.BookingId,
                   ISNULL(p.MaxPos, 0) + ROW_NUMBER() OVER (PARTITION BY t.SessionId ORDER BY t.BookedAt, t.BookingId)
              FROM @b t
              LEFT JOIN @p p ON p.SessionId = t.SessionId;

            UPDATE s
               SET s.SeatsAvailable = s.Capacity
                   - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status = 'BOOKED')
              FROM dbo.ExamSession s
             WHERE s.SessionId IN (SELECT SessionId FROM @b);
        END
    END
END
GO
