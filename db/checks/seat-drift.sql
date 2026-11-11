/*
    seat-drift.sql - sessions whose SeatsAvailable counter does not match the bookings.
    Expected: SeatsAvailable = Capacity - (BOOKED + ATTENDED bookings) - seats held by pending waitlist
    offers (Waitlist.OfferExpiresAt IS NOT NULL, db/schema/012, ADR 0003).
    Drift > 0 means the counter shows fewer free seats than there really are.
    Read-only.  No rows = no drift.
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;

SELECT s.SessionId,
       e.ExamCode,
       tc.CenterCode,
       s.StartsAt,
       s.Capacity,
       s.SeatsAvailable,
       ISNULL(b.ActiveBookings, 0)                                  AS ActiveBookings,
       ISNULL(o.OfferedSeats, 0)                                    AS OfferedSeats,
       (s.Capacity - ISNULL(b.ActiveBookings, 0) - ISNULL(o.OfferedSeats, 0)) - s.SeatsAvailable AS Drift
FROM dbo.ExamSession s
JOIN dbo.Exam e        ON e.ExamId = s.ExamId
JOIN dbo.TestCenter tc ON tc.TestCenterId = s.TestCenterId
LEFT JOIN (SELECT SessionId, COUNT(*) AS ActiveBookings
             FROM dbo.Booking
            WHERE Status IN ('BOOKED','ATTENDED')
            GROUP BY SessionId) b ON b.SessionId = s.SessionId
LEFT JOIN (SELECT SessionId, COUNT(*) AS OfferedSeats
             FROM dbo.Waitlist
            WHERE OfferExpiresAt IS NOT NULL
            GROUP BY SessionId) o ON o.SessionId = s.SessionId
WHERE s.SeatsAvailable <> s.Capacity - ISNULL(b.ActiveBookings, 0) - ISNULL(o.OfferedSeats, 0)
ORDER BY s.StartsAt, s.SessionId;
GO
