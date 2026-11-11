/*
    Roster export fixture (MBA-3826 functional test). Idempotent: safe to run again.
    One CLOSED session at BOS01 on 2027-06-15 09:00 (outside the seed date range) with
    BOOKED, ATTENDED, CANCELLED and WAITLISTED bookings. Rows are inserted directly
    (no usp_* procs) so the measured export window starts from a quiet database.
    Prints: FIXTURE|SessionId|TestCenterId|CenterCode|ExamCode
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
USE AssessmentDelivery;

DECLARE @CenterId INT = (SELECT TestCenterId FROM dbo.TestCenter WHERE CenterCode = 'BOS01');
DECLARE @RoomId INT = (SELECT TOP 1 RoomId FROM dbo.Room WHERE TestCenterId = @CenterId ORDER BY RoomId);
DECLARE @ExamId INT = (SELECT ExamId FROM dbo.Exam WHERE ExamCode = 'HPI-ACC1');
DECLARE @StartsAt DATETIME = '20270615 09:00';

IF @CenterId IS NULL OR @RoomId IS NULL OR @ExamId IS NULL
    THROW 50000, 'Seed data missing (BOS01 / HPI-ACC1) - run db\apply.ps1 first', 1;

BEGIN TRAN;

IF NOT EXISTS (SELECT 1 FROM dbo.ExamSession WHERE TestCenterId = @CenterId AND ExamId = @ExamId AND StartsAt = @StartsAt)
    INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, Status)
    VALUES (@ExamId, @CenterId, @RoomId, @StartsAt, 3, 1, 3, 'CLOSED');

DECLARE @SessionId INT = (SELECT SessionId FROM dbo.ExamSession WHERE TestCenterId = @CenterId AND ExamId = @ExamId AND StartsAt = @StartsAt);

DECLARE @f TABLE (CandidateNumber VARCHAR(12), FirstName NVARCHAR(60), LastName NVARCHAR(60), Email NVARCHAR(200),
                  Status VARCHAR(12), AccommodationCode VARCHAR(10), Channel VARCHAR(10), BookedAt DATETIME, Position INT);
INSERT INTO @f VALUES
 ('NW90000001', N'"Jo"', N'O''Neil', N'jo.oneil@example.com',    'BOOKED',     NULL,   'PORTAL',  '20270501 10:00', NULL),
 ('NW90000002', N'Ann',  N'Abbott',  N'ann.abbott@example.com',  'ATTENDED',   NULL,   'PARTNER', '20270502 11:15', NULL),
 ('NW90000003', N'Zoe',  N'Zimmer',  N'zoe.zimmer@example.com',  'BOOKED',     'ET25', 'IMPORT',  '20270503 12:30', NULL),
 ('NW90000004', N'Ben',  N'Baker',   N'ben.baker@example.com',   'CANCELLED',  NULL,   'PORTAL',  '20270504 09:45', NULL),
 ('NW90000005', N'Yan',  N'Young',   N'yan.young@example.com',   'WAITLISTED', NULL,   'PORTAL',  '20270505 16:20', 2),
 ('NW90000006', N'Cy',   N'Carter',  N'cy.carter@example.com',   'WAITLISTED', 'RB',   'PARTNER', '20270505 08:05', 1);

INSERT INTO dbo.Candidate (CandidateNumber, FirstName, LastName, Email)
SELECT f.CandidateNumber, f.FirstName, f.LastName, f.Email
  FROM @f f
 WHERE NOT EXISTS (SELECT 1 FROM dbo.Candidate c WHERE c.CandidateNumber = f.CandidateNumber);

INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, BookedAt, CancelledAt, CancelReason)
SELECT @SessionId, c.CandidateId, f.Status, f.AccommodationCode, f.Channel, f.BookedAt,
       CASE WHEN f.Status = 'CANCELLED' THEN DATEADD(DAY, 1, f.BookedAt) END,
       CASE WHEN f.Status = 'CANCELLED' THEN N'roster fixture' END
  FROM @f f
  JOIN dbo.Candidate c ON c.CandidateNumber = f.CandidateNumber
 WHERE NOT EXISTS (SELECT 1 FROM dbo.Booking b WHERE b.SessionId = @SessionId AND b.CandidateId = c.CandidateId);

INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position)
SELECT @SessionId, b.CandidateId, b.BookingId, f.Position
  FROM @f f
  JOIN dbo.Candidate c ON c.CandidateNumber = f.CandidateNumber
  JOIN dbo.Booking b ON b.SessionId = @SessionId AND b.CandidateId = c.CandidateId
 WHERE f.Status = 'WAITLISTED'
   AND NOT EXISTS (SELECT 1 FROM dbo.Waitlist w WHERE w.BookingId = b.BookingId);

UPDATE dbo.ExamSession SET SeatsAvailable = 0 WHERE SessionId = @SessionId AND SeatsAvailable <> 0;

COMMIT;

SELECT 'FIXTURE|' + CAST(s.SessionId AS VARCHAR(12)) + '|' + CAST(s.TestCenterId AS VARCHAR(12)) + '|' + tc.CenterCode + '|' + e.ExamCode
  FROM dbo.ExamSession s
  JOIN dbo.TestCenter tc ON tc.TestCenterId = s.TestCenterId
  JOIN dbo.Exam e ON e.ExamId = s.ExamId
 WHERE s.SessionId = @SessionId;
