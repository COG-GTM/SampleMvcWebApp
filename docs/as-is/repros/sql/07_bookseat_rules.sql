-- usp_BookSeat: accepts any accommodation code, does not enforce retake wait, re-books after ATTENDED on the same
-- session, and the extra-time check runs before the "full -> waitlist" branch. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @id INT, @st VARCHAR(12);
DECLARE @s INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status='OPEN' AND SeatsAvailable > 2);
EXEC dbo.usp_BookSeat @SessionId=@s, @CandidateId=956, @AccommodationCode='XYZ', @BookingId=@id OUTPUT, @ResultStatus=@st OUTPUT;
SELECT 'unknown accommodation code' AS Case_, @id AS BookingId, @st AS Status, AccommodationCode FROM dbo.Booking WHERE BookingId=@id;
-- retake: a candidate whose failed result is recent, booked onto the next OPEN session of the same exam inside RetakeWaitDays
DECLARE @c INT, @x INT, @failed DATETIME, @next DATETIME, @wait INT;
SELECT TOP 1 @c = r.CandidateId, @x = n.SessionId, @failed = fs.StartsAt, @next = n.StartsAt, @wait = e.RetakeWaitDays
  FROM dbo.Result r JOIN dbo.ExamSession fs ON fs.SessionId=r.SessionId JOIN dbo.Exam e ON e.ExamId=r.ExamId
  JOIN dbo.ExamSession n ON n.ExamId=r.ExamId AND n.Status='OPEN' AND n.SeatsAvailable>0
       AND n.StartsAt < DATEADD(DAY, e.RetakeWaitDays, fs.StartsAt) AND n.StartsAt > fs.StartsAt
 WHERE r.Passed=0
   AND NOT EXISTS (SELECT 1 FROM dbo.Booking z WHERE z.SessionId=n.SessionId AND z.CandidateId=r.CandidateId)
 ORDER BY fs.StartsAt DESC, r.CandidateId;
EXEC dbo.usp_BookSeat @SessionId=@x, @CandidateId=@c, @BookingId=@id OUTPUT, @ResultStatus=@st OUTPUT;
SELECT 'retake inside wait window' AS Case_, @c AS CandidateId, @failed AS FailedSitting, @next AS NewSitting, @wait AS RetakeWaitDays,
       DATEDIFF(DAY, @failed, @next) AS DaysApart, @st AS Status;
-- full session + extra-time request whose accommodation seats are used up: error instead of waitlist
DECLARE @f INT = (SELECT TOP 1 s.SessionId FROM dbo.ExamSession s WHERE s.Status='OPEN' AND s.SeatsAvailable=0 ORDER BY s.SessionId);
UPDATE dbo.ExamSession SET AccommodationCapacity = 0 WHERE SessionId=@f;  -- make the accommodation pool "used up"
BEGIN TRY
  EXEC dbo.usp_BookSeat @SessionId=@f, @CandidateId=957, @AccommodationCode='ET25', @BookingId=@id OUTPUT, @ResultStatus=@st OUTPUT;
  SELECT 'ET on full session' AS Case_, @st AS Status;
END TRY BEGIN CATCH SELECT 'ET on full session' AS Case_, ERROR_NUMBER() AS ErrNumber, ERROR_STATE() AS State, ERROR_MESSAGE() AS Msg; END CATCH
IF @@TRANCOUNT > 0 ROLLBACK;
