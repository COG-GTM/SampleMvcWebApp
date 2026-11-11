-- Business invariants that the schema does not enforce: each statement below succeeds. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @s INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status='OPEN' AND SeatsAvailable > 0);
DECLARE @r TABLE (Invariant VARCHAR(70), Accepted VARCHAR(5));
UPDATE dbo.ExamSession SET SeatsAvailable = -5 WHERE SessionId=@s;                         INSERT @r VALUES ('SeatsAvailable >= 0', 'yes');
UPDATE dbo.ExamSession SET SeatsAvailable = Capacity + 50 WHERE SessionId=@s;              INSERT @r VALUES ('SeatsAvailable <= Capacity', 'yes');
UPDATE dbo.ExamSession SET AccommodationCapacity = Capacity + 1 WHERE SessionId=@s;        INSERT @r VALUES ('AccommodationCapacity <= Capacity', 'yes');
UPDATE dbo.ExamSession SET RoomId = (SELECT TOP 1 r.RoomId FROM dbo.Room r WHERE r.TestCenterId <> (SELECT TestCenterId FROM dbo.ExamSession WHERE SessionId=@s)) WHERE SessionId=@s;
                                                                                          INSERT @r VALUES ('Session.RoomId belongs to Session.TestCenterId', 'yes');
INSERT dbo.Booking (SessionId, CandidateId, Status, Channel) VALUES (@s, 960, 'WAITLISTED', 'IMPORT'), (@s, 960, 'WAITLISTED', 'IMPORT');
                                                                                          INSERT @r VALUES ('one active booking per candidate per session', 'yes');
INSERT dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel) VALUES (@s, 961, 'WAITLISTED', 'BOGUS', 'IMPORT');
                                                                                          INSERT @r VALUES ('AccommodationCode in (ET25,ET50,SR,RB)', 'yes');
INSERT dbo.Waitlist (SessionId, CandidateId, BookingId, Position)
  SELECT TOP 1 SessionId, CandidateId, BookingId, 1 FROM dbo.Booking WHERE SessionId=@s AND CandidateId=960;
INSERT dbo.Waitlist (SessionId, CandidateId, BookingId, Position)
  SELECT TOP 1 SessionId, CandidateId, BookingId, 1 FROM dbo.Booking WHERE SessionId=@s AND CandidateId=960;
                                                                                          INSERT @r VALUES ('unique Waitlist (SessionId, Position) / BookingId', 'yes');
INSERT dbo.Waitlist (SessionId, CandidateId, BookingId, Position)
  SELECT TOP 1 @s + 1, 1, BookingId, 99 FROM dbo.Booking WHERE SessionId=@s AND CandidateId=961;
                                                                                          INSERT @r VALUES ('Waitlist Session/Candidate match its Booking', 'yes');
UPDATE dbo.ExamSession SET Status='CANCELLED' WHERE SessionId=@s;                          INSERT @r VALUES ('cancelling a session cancels its bookings', 'yes');
SELECT Invariant, Accepted AS AcceptedBySchema FROM @r;
SELECT Status, COUNT(*) AS BookingsOnCancelledSession FROM dbo.Booking WHERE SessionId=@s GROUP BY Status;
ROLLBACK;
