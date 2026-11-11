-- Neither cancelling a BOOKED seat nor raising capacity promotes WAITLISTED bookings; cancelling a waitlisted
-- booking deletes its Waitlist row and leaves a gap in Position. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @s INT = (SELECT TOP 1 SessionId FROM dbo.Waitlist GROUP BY SessionId HAVING COUNT(*) >= 3 ORDER BY SessionId);
SELECT @s AS SessionId, Capacity, SeatsAvailable FROM dbo.ExamSession WHERE SessionId=@s;
DECLARE @b INT = (SELECT MIN(BookingId) FROM dbo.Booking WHERE SessionId=@s AND Status='BOOKED');
EXEC dbo.usp_CancelBooking @BookingId=@b, @Reason=N'repro', @Actor='repro';
DECLARE @cap INT = (SELECT Capacity + 2 FROM dbo.ExamSession WHERE SessionId=@s);
EXEC dbo.usp_ChangeCapacity @SessionId=@s, @NewCapacity=@cap, @Actor='repro';
SELECT Capacity, SeatsAvailable, (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@s AND Status='WAITLISTED') AS StillWaitlisted
  FROM dbo.ExamSession WHERE SessionId=@s;
DECLARE @w INT = (SELECT BookingId FROM dbo.Waitlist WHERE SessionId=@s AND Position = 2);
EXEC dbo.usp_CancelBooking @BookingId=@w, @Reason=N'repro', @Actor='repro';
SELECT BookingId, Position FROM dbo.Waitlist WHERE SessionId=@s ORDER BY Position;
ROLLBACK;
