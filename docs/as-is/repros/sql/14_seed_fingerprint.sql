-- Fingerprint of the seeded data (run after two separate rebuilds; identical output = deterministic seed).
-- Columns defaulted by GETDATE() at insert time are excluded and counted separately.
USE AssessmentDelivery;
SET NOCOUNT ON;
SELECT 'Candidate' AS T, COUNT(*) AS N, CHECKSUM_AGG(CHECKSUM(CandidateId, CandidateNumber, FirstName, LastName, Email, DateOfBirth, DefaultAccommodationCode)) AS Fp FROM dbo.Candidate
UNION ALL SELECT 'ExamSession', COUNT(*), CHECKSUM_AGG(CHECKSUM(SessionId, ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, Status, LastModified)) FROM dbo.ExamSession
UNION ALL SELECT 'Booking', COUNT(*), CHECKSUM_AGG(CHECKSUM(BookingId, SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef, BookedAt, CancelledAt, LastModified)) FROM dbo.Booking
UNION ALL SELECT 'Waitlist', COUNT(*), CHECKSUM_AGG(CHECKSUM(WaitlistId, SessionId, CandidateId, BookingId, Position)) FROM dbo.Waitlist
UNION ALL SELECT 'Result', COUNT(*), CHECKSUM_AGG(CHECKSUM(ResultId, BookingId, Score, Passed, ScoredAt)) FROM dbo.Result
UNION ALL SELECT 'AuditLog', COUNT(*), CHECKSUM_AGG(CHECKSUM(AuditId, EntityName, EntityId, Action)) FROM dbo.AuditLog;
SELECT MIN(CreatedOn) AS CandidateCreatedOnMin, MAX(CreatedOn) AS CandidateCreatedOnMax FROM dbo.Candidate;
SELECT MIN(LoggedAt) AS AuditMin, MAX(LoggedAt) AS AuditMax, COUNT(DISTINCT Actor) AS Actors, MIN(Actor) AS AnActor FROM dbo.AuditLog;
SELECT Status, COUNT(*) AS Sessions, MIN(StartsAt) AS FirstStart, MAX(StartsAt) AS LastStart FROM dbo.ExamSession GROUP BY Status;
SELECT COUNT(*) AS OpenSessionsAlreadyInThePast_ByWallClock FROM dbo.ExamSession WHERE Status='OPEN' AND StartsAt < GETDATE();
