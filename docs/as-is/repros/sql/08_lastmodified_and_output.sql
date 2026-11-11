-- trg_ExamSession_Update overwrites any LastModified a writer supplies; OUTPUT without INTO is rejected on every
-- trigger-bearing table (Msg 334). Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @s INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status='OPEN');
SELECT SessionId, LastModified AS SeededLastModified FROM dbo.ExamSession WHERE SessionId=@s;
UPDATE dbo.ExamSession SET LastModified = '20200101' WHERE SessionId=@s;
SELECT LastModified AS AfterExplicit2020Update, CASE WHEN LastModified > '20250101' THEN 'overwritten by trigger' ELSE 'kept' END AS Verdict
  FROM dbo.ExamSession WHERE SessionId=@s;
BEGIN TRY EXEC (N'UPDATE dbo.ExamSession SET Status = Status OUTPUT inserted.SessionId WHERE SessionId = 1;'); END TRY
BEGIN CATCH SELECT 'ExamSession OUTPUT' AS Case_, ERROR_NUMBER() AS ErrNumber, LEFT(ERROR_MESSAGE(),150) AS Msg; END CATCH
BEGIN TRY EXEC (N'INSERT INTO dbo.Booking (SessionId, CandidateId, Status) OUTPUT inserted.BookingId VALUES (1, 958, ''WAITLISTED'');'); END TRY
BEGIN CATCH SELECT 'Booking OUTPUT' AS Case_, ERROR_NUMBER() AS ErrNumber, LEFT(ERROR_MESSAGE(),150) AS Msg; END CATCH
DECLARE @o TABLE (BookingId INT);
INSERT INTO dbo.Booking (SessionId, CandidateId, Status) OUTPUT inserted.BookingId INTO @o VALUES (@s, 958, 'WAITLISTED');
SELECT 'Booking OUTPUT INTO @table' AS Case_, COUNT(*) AS RowsCaptured FROM @o;
IF @@TRANCOUNT > 0 ROLLBACK;
