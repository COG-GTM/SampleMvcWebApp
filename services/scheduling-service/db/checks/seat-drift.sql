/*
    seat-drift.sql (scheduling-service schema) - db/checks/seat-drift.sql ported to the Scheduling database.
    Sessions whose SeatsAvailable counter does not match the bookings.
    Expected: SeatsAvailable = Capacity - (BOOKED + ATTENDED bookings).
    ExamCode / CenterCode come from the reference replicas (LEFT JOIN: no FKs, a missing replica row must not hide drift).
    Read-only.  No rows = no drift.
*/
USE Scheduling;
GO
SET NOCOUNT ON;

SELECT s.SessionId,
       e.ExamCode,
       tc.CenterCode,
       s.StartsAt,
       s.Capacity,
       s.SeatsAvailable,
       ISNULL(b.ActiveBookings, 0)                                  AS ActiveBookings,
       (s.Capacity - ISNULL(b.ActiveBookings, 0)) - s.SeatsAvailable AS Drift
FROM dbo.ExamSession s
LEFT JOIN dbo.RefExam e        ON e.ExamId = s.ExamId
LEFT JOIN dbo.RefTestCenter tc ON tc.TestCenterId = s.TestCenterId
LEFT JOIN (SELECT SessionId, COUNT(*) AS ActiveBookings
             FROM dbo.Booking
            WHERE Status IN ('BOOKED','ATTENDED')
            GROUP BY SessionId) b ON b.SessionId = s.SessionId
WHERE s.SeatsAvailable <> s.Capacity - ISNULL(b.ActiveBookings, 0)
ORDER BY s.StartsAt, s.SessionId;
GO
