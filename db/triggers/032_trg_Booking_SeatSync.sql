/*
    trg_Booking_SeatSync - keep ExamSession.SeatsAvailable right for bookings
    that are loaded straight into dbo.Booking (bulk import files from the 2013
    migration) instead of going through usp_BookSeat.
    usp_BookSeat already adjusts the counter for portal bookings.
    2013-04  migration cutover
    2026-11  MBA-3823: skipped for the scheduling-service outbox relay, which writes the exact
             SeatsAvailable that scheduling-service decided (session context writer = scheduling-relay).
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.trg_Booking_SeatSync') IS NOT NULL DROP TRIGGER dbo.trg_Booking_SeatSync;
GO
CREATE TRIGGER dbo.trg_Booking_SeatSync ON dbo.Booking
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;

    IF CAST(SESSION_CONTEXT(N'writer') AS NVARCHAR(64)) = N'scheduling-relay' RETURN;

    UPDATE s
       SET s.SeatsAvailable = s.SeatsAvailable - x.Cnt
      FROM dbo.ExamSession s
      JOIN (SELECT SessionId, COUNT(*) AS Cnt
              FROM inserted
             WHERE Status = 'BOOKED' AND Channel <> 'PORTAL'
             GROUP BY SessionId) x ON x.SessionId = s.SessionId;
END
GO
