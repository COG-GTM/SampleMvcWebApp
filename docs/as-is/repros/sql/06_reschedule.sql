-- usp_RescheduleBooking: same-session "reschedule" is accepted; moving into a full session cancels the seat and
-- returns WAITLISTED. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @nb INT, @st VARCHAR(12);
DECLARE @b INT = (SELECT TOP 1 b.BookingId FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId=b.SessionId
                   WHERE b.Status='BOOKED' AND s.Status='OPEN' AND b.AccommodationCode IS NULL ORDER BY b.BookingId);
DECLARE @s INT = (SELECT SessionId FROM dbo.Booking WHERE BookingId=@b);
EXEC dbo.usp_RescheduleBooking @BookingId=@b, @NewSessionId=@s, @NewBookingId=@nb OUTPUT, @ResultStatus=@st OUTPUT;
SELECT 'same session' AS Case_, @b AS OldBooking, @nb AS NewBooking, @st AS NewStatus,
       (SELECT Status FROM dbo.Booking WHERE BookingId=@b) AS OldStatus,
       (SELECT CancelReason FROM dbo.Booking WHERE BookingId=@b) AS OldReason;
-- into a full session of the same exam
DECLARE @b2 INT, @full INT;
SELECT TOP 1 @b2 = b.BookingId, @full = f.SessionId
  FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId=b.SessionId
  JOIN dbo.ExamSession f ON f.ExamId=s.ExamId AND f.Status='OPEN' AND f.SeatsAvailable=0 AND f.SessionId<>s.SessionId
 WHERE b.Status='BOOKED' AND s.Status='OPEN' AND b.AccommodationCode IS NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.Booking x WHERE x.SessionId=f.SessionId AND x.CandidateId=b.CandidateId)
 ORDER BY b.BookingId;
EXEC dbo.usp_RescheduleBooking @BookingId=@b2, @NewSessionId=@full, @NewBookingId=@nb OUTPUT, @ResultStatus=@st OUTPUT;
SELECT 'into full session' AS Case_, @b2 AS OldBooking, (SELECT Status FROM dbo.Booking WHERE BookingId=@b2) AS OldStatus, @full AS FullSession, @nb AS NewBooking, @st AS NewStatus;
ROLLBACK;
