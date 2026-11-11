-- Proc "error codes" 50001..50006 are RAISERROR states, not message numbers: every proc error surfaces as Msg 50000.
-- Also: the CATCH re-raise turns system errors (FK 547) into Msg 50000, which SqlErrorHelper shows to portal users.
USE AssessmentDelivery;
SET NOCOUNT ON;
DECLARE @id INT, @st VARCHAR(12), @closed INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status = 'CLOSED');
PRINT '-- book a CLOSED session (documented as 50001)';
BEGIN TRY EXEC dbo.usp_BookSeat @SessionId = @closed, @CandidateId = 950, @BookingId = @id OUTPUT, @ResultStatus = @st OUTPUT; END TRY
BEGIN CATCH SELECT ERROR_NUMBER() AS ErrNumber, ERROR_SEVERITY() AS Sev, ERROR_STATE() AS State, ERROR_MESSAGE() AS Msg; END CATCH
PRINT '-- book a non-existent candidate on an OPEN session (FK violation)';
DECLARE @open INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status = 'OPEN' AND SeatsAvailable > 0);
BEGIN TRY EXEC dbo.usp_BookSeat @SessionId = @open, @CandidateId = 999999, @BookingId = @id OUTPUT, @ResultStatus = @st OUTPUT; END TRY
BEGIN CATCH SELECT ERROR_NUMBER() AS ErrNumber, ERROR_SEVERITY() AS Sev, ERROR_STATE() AS State, LEFT(ERROR_MESSAGE(), 120) AS Msg; END CATCH
