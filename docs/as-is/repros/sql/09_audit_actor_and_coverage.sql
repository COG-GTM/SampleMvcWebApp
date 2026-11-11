-- AuditLog.Actor defaults to SUSER_SNAME(): proc BOOK/WAITLIST rows (no @Actor parameter) are attributed to the
-- SQL login, i.e. ad_app for every application. Candidate INSERTs, Booking status UPDATEs, Result writes,
-- TestCenter/Room updates and trigger demotions are not audited. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @a BIGINT = (SELECT ISNULL(MAX(AuditId),0) FROM dbo.AuditLog);
DECLARE @id INT, @st VARCHAR(12);
DECLARE @s INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status='OPEN' AND SeatsAvailable > 0);
EXECUTE AS LOGIN = 'ad_app';
EXEC dbo.usp_BookSeat @SessionId=@s, @CandidateId=959, @BookingId=@id OUTPUT, @ResultStatus=@st OUTPUT;
EXEC dbo.usp_CancelBooking @BookingId=@id, @Reason=N'repro', @Actor='portal';
INSERT INTO dbo.Candidate (CandidateNumber, FirstName, LastName, Email) VALUES ('NWREPRO001', N'Repro', N'User', 'repro@example.invalid');
UPDATE dbo.Booking SET Status='ATTENDED' WHERE BookingId = (SELECT MIN(BookingId) FROM dbo.Booking WHERE Status='BOOKED' AND SessionId <> @s);
UPDATE dbo.TestCenter SET Name = Name + N' (renamed)' WHERE TestCenterId = 1;
UPDATE dbo.Candidate SET DateOfBirth = '19900101' WHERE CandidateNumber = 'NW00000001';
UPDATE dbo.Exam SET DurationMinutes = DurationMinutes + 1 WHERE ExamId = 1;
REVERT;
SELECT EntityName, EntityId, Action, LEFT(Detail,60) AS Detail, Actor FROM dbo.AuditLog WHERE AuditId > @a ORDER BY AuditId;
ROLLBACK;
