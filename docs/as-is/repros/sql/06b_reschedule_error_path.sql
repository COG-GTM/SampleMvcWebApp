-- Error path of the nested transaction in usp_RescheduleBooking: an ATTENDED booking (not cancellable) moved to
-- another session of the same exam. No outer transaction; nothing is written.
USE AssessmentDelivery;
SET NOCOUNT ON;
DECLARE @nb INT, @st VARCHAR(12), @rc INT;
DECLARE @b INT, @t INT;
SELECT TOP 1 @b = b.BookingId, @t = o.SessionId FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId=b.SessionId
  JOIN dbo.ExamSession o ON o.ExamId=s.ExamId AND o.Status='OPEN' WHERE b.Status='ATTENDED' ORDER BY b.BookingId, o.SessionId;
EXEC @rc = dbo.usp_RescheduleBooking @BookingId=@b, @NewSessionId=@t, @NewBookingId=@nb OUTPUT, @ResultStatus=@st OUTPUT;
SELECT @b AS BookingId, @t AS TargetSession, @rc AS ReturnCode, @nb AS NewBookingId, @@TRANCOUNT AS TranCount;
