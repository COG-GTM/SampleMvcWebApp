-- capacity-sync (nightly 02:00) recomputes SeatsAvailable = Capacity - AccommodationCapacity - standard bookings,
-- which disagrees with db/checks/seat-drift.sql (Capacity - (BOOKED+ATTENDED)) and with trg_ExamSession_Update
-- (Capacity - BOOKED). Replays the service's SELECT + UPDATE for all OPEN sessions, then runs the drift check. Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
SELECT COUNT(*) AS OpenSessions,
       SUM(CASE WHEN AccommodationCapacity > 0 THEN 1 ELSE 0 END) AS WithAccommodationSeats FROM dbo.ExamSession WHERE Status='OPEN';
SELECT COUNT(*) AS DriftRowsBefore FROM dbo.ExamSession s
 WHERE s.SeatsAvailable <> s.Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED'));
;WITH x AS (
  SELECT s.SessionId, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable,
        (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED')
            AND (b.AccommodationCode IS NULL OR b.AccommodationCode NOT IN ('ET25','ET50'))) AS StandardBooked
  FROM dbo.ExamSession s WHERE s.Status = 'OPEN')
UPDATE s SET SeatsAvailable = CASE WHEN x.Capacity - x.AccommodationCapacity - x.StandardBooked < 0 THEN 0
                                   ELSE x.Capacity - x.AccommodationCapacity - x.StandardBooked END, LastModified = GETDATE()
  FROM dbo.ExamSession s JOIN x ON x.SessionId = s.SessionId
 WHERE s.SeatsAvailable <> CASE WHEN x.Capacity - x.AccommodationCapacity - x.StandardBooked < 0 THEN 0
                                ELSE x.Capacity - x.AccommodationCapacity - x.StandardBooked END;
SELECT @@ROWCOUNT AS SessionsCapacitySyncWouldChange;
SELECT COUNT(*) AS DriftRowsAfter FROM dbo.ExamSession s
 WHERE s.SeatsAvailable <> s.Capacity - (SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED'));
ROLLBACK;
