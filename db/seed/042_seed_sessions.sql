/*
    Seed 042 - exam sessions 2026-09-01 .. 2027-03-31.
    Every active room gets at most one session a week (Mon-Fri, a few Saturdays).
    Sessions before the 2026-11-11 cut-off are DELIVERED, later ones OPEN,
    with the odd CANCELLED / CLOSED one.
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
GO

DECLARE @WeekZero DATETIME = '20260831';    -- a Monday
DECLARE @AsOf     DATETIME = '20261111';

;WITH w AS (
    SELECT TOP (31) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 AS w
    FROM sys.all_objects
), slot AS (
    SELECT r.RoomId, r.SeatCount, r.AccommodationSeats, r.TestCenterId, w.w,
           CAST((CAST(r.RoomId AS BIGINT) * 7919 + w.w * 104729 + 17) % 1000 AS INT) AS h
    FROM dbo.Room r
    JOIN dbo.TestCenter tc ON tc.TestCenterId = r.TestCenterId AND tc.IsActive = 1
    CROSS JOIN w
    WHERE r.IsActive = 1
), s AS (
    SELECT RoomId, TestCenterId, SeatCount, AccommodationSeats, h,
           DATEADD(MINUTE, CASE WHEN (RoomId + w) % 2 = 0 THEN 540 ELSE 810 END,
               DATEADD(DAY, w * 7 + CASE WHEN (RoomId * 13 + w) % 7 = 0 AND RoomId % 3 = 0 THEN 5
                                         ELSE (RoomId * 31 + w * 17 + h / 10) % 5 END, @WeekZero)) AS StartsAt,
           ((RoomId * 7 + w * 5 + h / 7) % 12) + 1 AS ExamNo
    FROM slot
    WHERE h < 420
)
INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, Status, LastModified)
SELECT e.ExamId, s.TestCenterId, s.RoomId, s.StartsAt,
       CASE WHEN s.SeatCount - (s.h % 4) < 8 THEN s.SeatCount ELSE s.SeatCount - (s.h % 4) END,
       CASE WHEN s.AccommodationSeats > 0 AND s.h % 3 <> 0 THEN s.AccommodationSeats ELSE 0 END,
       CASE WHEN s.SeatCount - (s.h % 4) < 8 THEN s.SeatCount ELSE s.SeatCount - (s.h % 4) END,
       CASE WHEN s.h % 50 = 7 THEN 'CANCELLED'
            WHEN s.StartsAt < @AsOf THEN 'DELIVERED'
            WHEN s.h % 37 = 5 THEN 'CLOSED'
            ELSE 'OPEN' END,
       CASE WHEN s.StartsAt < @AsOf THEN DATEADD(DAY, 1, s.StartsAt) ELSE '20261110 18:00' END
FROM s
JOIN (SELECT ExamId, ROW_NUMBER() OVER (ORDER BY ExamId) AS ExamNo FROM dbo.Exam) e ON e.ExamNo = s.ExamNo
WHERE s.StartsAt >= '20260901' AND s.StartsAt < '20270401'
ORDER BY s.StartsAt, s.RoomId;
GO
