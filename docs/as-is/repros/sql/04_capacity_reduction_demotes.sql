-- usp_ChangeCapacity + trg_ExamSession_Update: reducing capacity below the BOOKED count silently demotes the
-- latest BOOKED bookings to WAITLISTED, appends Waitlist rows, and recomputes SeatsAvailable as Capacity - BOOKED
-- (ATTENDED ignored), overriding the proc's own clamp. No AuditLog row per demoted booking. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @a BIGINT = (SELECT ISNULL(MAX(AuditId),0) FROM dbo.AuditLog);
-- an OPEN session with at least 4 BOOKED bookings
DECLARE @s INT = (SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.AccommodationCapacity = 0
                    AND (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='BOOKED') >= 4 ORDER BY s.SessionId);
DECLARE @booked INT = (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@s AND Status='BOOKED');
SELECT @s AS SessionId, Capacity, SeatsAvailable, @booked AS Booked FROM dbo.ExamSession WHERE SessionId=@s;
DECLARE @new INT = @booked - 2;
EXEC dbo.usp_ChangeCapacity @SessionId=@s, @NewCapacity=@new, @Actor='repro';
SELECT Capacity, SeatsAvailable FROM dbo.ExamSession WHERE SessionId=@s;
SELECT Status, COUNT(*) AS N FROM dbo.Booking WHERE SessionId=@s GROUP BY Status;
SELECT w.BookingId, w.Position, b.Status, b.BookedAt FROM dbo.Waitlist w JOIN dbo.Booking b ON b.BookingId=w.BookingId WHERE w.SessionId=@s;
SELECT EntityName, EntityId, Action, Detail, Actor FROM dbo.AuditLog WHERE AuditId > @a;
-- DELIVERED session with ATTENDED + BOOKED rows: demotion and recount consider BOOKED only, so shrinking below BOOKED+ATTENDED demotes nobody
DECLARE @d INT = (SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='DELIVERED'
                    AND EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='ATTENDED')
                    AND (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status='BOOKED') >= 1 ORDER BY s.SessionId);
DECLARE @dcap INT = (SELECT COUNT(*) - 1 FROM dbo.Booking WHERE SessionId=@d AND Status IN ('BOOKED','ATTENDED'));  -- one seat fewer than occupied
SELECT @d AS DeliveredSessionId, Capacity, SeatsAvailable,
       (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@d AND Status='BOOKED') AS Booked,
       (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@d AND Status='ATTENDED') AS Attended FROM dbo.ExamSession WHERE SessionId=@d;
EXEC dbo.usp_ChangeCapacity @SessionId=@d, @NewCapacity=@dcap, @Actor='repro';
SELECT Capacity, SeatsAvailable,
       Capacity - (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@d AND Status IN ('BOOKED','ATTENDED')) AS SeatDriftCheckExpects
  FROM dbo.ExamSession WHERE SessionId=@d;
ROLLBACK;
