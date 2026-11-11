-- Partner bookings: booking-api inserts Channel=PARTNER directly (BookingDao.insertBooking) AND decrements
-- SeatsAvailable itself (SessionDao.decrementSeatsAvailable), while trg_Booking_SeatSync also decrements
-- for every non-PORTAL BOOKED insert. Replays the two exact statements booking-api runs. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @s INT = (SELECT MIN(SessionId) FROM dbo.ExamSession WHERE Status = 'OPEN' AND SeatsAvailable > 2);
SELECT @s AS SessionId, Capacity, SeatsAvailable AS SeatsBefore FROM dbo.ExamSession WHERE SessionId = @s;
INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef) VALUES (@s, 950, 'BOOKED', NULL, 'PARTNER', 'PTR-REPRO');
UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable - 1, LastModified = GETDATE() WHERE SessionId = @s;
SELECT s.SeatsAvailable AS SeatsAfter,
       s.Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) AS ExpectedPerSeatDriftCheck
FROM dbo.ExamSession s WHERE s.SessionId = @s;
ROLLBACK;
