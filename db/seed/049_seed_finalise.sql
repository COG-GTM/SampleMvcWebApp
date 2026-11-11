/*
    Seed 049 - recompute ExamSession.SeatsAvailable from the bookings now that
    everything is loaded (the booking trigger adjusts it row by row during the
    load).  BOOKED and ATTENDED bookings hold a seat.
    Run last.  db/checks/seat-drift.sql should return no rows afterwards.
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
GO

-- keep the seeded LastModified values
DISABLE TRIGGER dbo.trg_ExamSession_Update ON dbo.ExamSession;
GO

UPDATE s
   SET s.SeatsAvailable = s.Capacity - ISNULL(b.Active, 0),
       s.LastModified   = CASE WHEN s.Status = 'DELIVERED' THEN DATEADD(DAY, 1, s.StartsAt) ELSE '20261110 18:00' END
  FROM dbo.ExamSession s
  LEFT JOIN (SELECT SessionId, COUNT(*) AS Active
               FROM dbo.Booking
              WHERE Status IN ('BOOKED','ATTENDED')
              GROUP BY SessionId) b ON b.SessionId = s.SessionId;
GO

ENABLE TRIGGER dbo.trg_ExamSession_Update ON dbo.ExamSession;
GO
