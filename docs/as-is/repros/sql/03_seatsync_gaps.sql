-- trg_Booking_SeatSync only handles INSERT of BOOKED non-PORTAL rows. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @s INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status = 'OPEN' AND SeatsAvailable > 3);
DECLARE @t TABLE (Step VARCHAR(60), SeatsAvailable INT, ActiveBookings INT);
INSERT @t SELECT 'start', SeatsAvailable, (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@s AND Status IN ('BOOKED','ATTENDED')) FROM dbo.ExamSession WHERE SessionId=@s;
INSERT INTO dbo.Booking (SessionId, CandidateId, Status) VALUES (@s, 951, 'BOOKED');            -- default Channel = PORTAL
INSERT @t SELECT 'direct INSERT, default channel PORTAL', SeatsAvailable, (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@s AND Status IN ('BOOKED','ATTENDED')) FROM dbo.ExamSession WHERE SessionId=@s;
INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) VALUES (@s, 952, 'WAITLISTED', 'IMPORT');
UPDATE dbo.Booking SET Status = 'BOOKED' WHERE SessionId=@s AND CandidateId = 952;                -- manual promotion
INSERT @t SELECT 'IMPORT WAITLISTED then UPDATE to BOOKED', SeatsAvailable, (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@s AND Status IN ('BOOKED','ATTENDED')) FROM dbo.ExamSession WHERE SessionId=@s;
INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) VALUES (@s, 953, 'BOOKED', 'IMPORT');
INSERT @t SELECT 'IMPORT BOOKED insert (trigger -1)', SeatsAvailable, (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@s AND Status IN ('BOOKED','ATTENDED')) FROM dbo.ExamSession WHERE SessionId=@s;
DELETE FROM dbo.Booking WHERE SessionId=@s AND CandidateId = 953;
INSERT @t SELECT 'DELETE that booking (no trigger)', SeatsAvailable, (SELECT COUNT(*) FROM dbo.Booking WHERE SessionId=@s AND Status IN ('BOOKED','ATTENDED')) FROM dbo.ExamSession WHERE SessionId=@s;
SELECT @s AS SessionId, * FROM @t;
-- no floor: a bulk IMPORT on a full session drives the counter negative
DECLARE @f INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status = 'OPEN' AND SeatsAvailable = 0);
INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) VALUES (@f, 954, 'BOOKED', 'IMPORT'), (@f, 955, 'BOOKED', 'IMPORT');
SELECT @f AS FullSessionId, Capacity, SeatsAvailable FROM dbo.ExamSession WHERE SessionId = @f;
ROLLBACK;
