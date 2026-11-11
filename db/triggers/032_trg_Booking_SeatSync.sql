/*
    trg_Booking_SeatSync - keep ExamSession.SeatsAvailable right for bookings
    that are loaded straight into dbo.Booking (bulk import files from the 2013
    migration) instead of going through usp_BookSeat.
    usp_BookSeat already adjusts the counter for portal bookings.
    2013-04  migration cutover

    MBA-3823: returns early for writes made by the scheduling-service relay
    (SESSION_CONTEXT writer = scheduling-relay). The relay projects exactly the
    seat count scheduling-service decided, so the extra non-PORTAL decrement
    (docs/as-is HB-01 / HB-04, MR-01) must not fire on top of it. Every other
    writer keeps the legacy behaviour.
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

    IF CONVERT(NVARCHAR(64), SESSION_CONTEXT(N'writer')) = N'scheduling-relay' RETURN;

    UPDATE s
       SET s.SeatsAvailable = s.SeatsAvailable - x.Cnt
      FROM dbo.ExamSession s
      JOIN (SELECT SessionId, COUNT(*) AS Cnt
              FROM inserted
             WHERE Status = 'BOOKED' AND Channel <> 'PORTAL'
             GROUP BY SessionId) x ON x.SessionId = s.SessionId;
END
GO
