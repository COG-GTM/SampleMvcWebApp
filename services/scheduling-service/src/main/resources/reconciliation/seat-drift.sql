
SELECT s.SessionId,
       e.ExamCode,
       tc.CenterCode,
       s.StartsAt,
       s.Capacity,
       s.SeatsAvailable,
       ISNULL(b.ActiveBookings, 0)                                  AS ActiveBookings,
       (s.Capacity - ISNULL(b.ActiveBookings, 0)) - s.SeatsAvailable AS Drift
FROM dbo.ExamSession s
JOIN dbo.Exam e        ON e.ExamId = s.ExamId
JOIN dbo.TestCenter tc ON tc.TestCenterId = s.TestCenterId
LEFT JOIN (SELECT SessionId, COUNT(*) AS ActiveBookings
             FROM dbo.Booking
            WHERE Status IN ('BOOKED','ATTENDED')
            GROUP BY SessionId) b ON b.SessionId = s.SessionId
WHERE s.SeatsAvailable <> s.Capacity - ISNULL(b.ActiveBookings, 0)
ORDER BY s.StartsAt, s.SessionId;
