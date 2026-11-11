/*
    Seed 043 - bookings for the sessions created by 042, plus waitlist rows.
    Candidates come from NW00000001 .. NW00000880 and NW00001000; within one exam
    nobody is booked twice (first attempts only - retakes are added in 044).
    Channel mix roughly 58% PORTAL / 26% PARTNER / 16% IMPORT.
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

DECLARE @Cutoff  DATETIME = '20261110 18:00';   -- latest booking timestamp in the seed
DECLARE @Pending DATETIME = '20261108';         -- delivered on/after this date: score files not in yet

IF OBJECT_ID('tempdb..#n') IS NOT NULL DROP TABLE #n;
SELECT TOP (100) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS n INTO #n FROM sys.all_objects;

IF OBJECT_ID('tempdb..#slot') IS NOT NULL DROP TABLE #slot;
CREATE TABLE #slot (
    SessionId INT, Slot INT, ExamId INT, StartsAt DATETIME, SessStatus VARCHAR(12),
    Capacity INT, AccCap INT, IsFull BIT, Fill INT, K INT NULL, CandidateId INT NULL,
    AccCode VARCHAR(10) NULL, Status VARCHAR(12) NULL, Channel VARCHAR(10) NULL, BookedAt DATETIME NULL,
    PRIMARY KEY (SessionId, Slot)
);

;WITH s AS (
    SELECT es.SessionId, es.ExamId, es.StartsAt, es.Status, es.Capacity, es.AccommodationCapacity,
           CAST((CAST(es.SessionId AS BIGINT) * 6271 + 911) % 1000 AS INT) AS h
    FROM dbo.ExamSession es
), f AS (
    SELECT s.*,
           CASE WHEN s.Status = 'OPEN' AND s.h % 17 = 0 THEN 1 ELSE 0 END AS IsFull,
           CASE WHEN s.Status = 'OPEN' AND s.h % 17 = 0 THEN s.Capacity + 1 + s.h % 3
                WHEN s.Status = 'DELIVERED' THEN s.Capacity * (55 + s.h % 45) / 100
                WHEN s.Status = 'CANCELLED' THEN s.Capacity * 30 / 100
                ELSE s.Capacity * (5 + s.h % 60) / 100 END AS Fill
    FROM s
)
INSERT INTO #slot (SessionId, Slot, ExamId, StartsAt, SessStatus, Capacity, AccCap, IsFull, Fill)
SELECT f.SessionId, n.n, f.ExamId, f.StartsAt, f.Status, f.Capacity, f.AccommodationCapacity, f.IsFull, f.Fill
FROM f JOIN #n n ON n.n <= f.Fill;

-- spread candidates over each exam's slots so nobody sits the same exam twice here
;WITH k AS (
    SELECT SessionId, Slot, ROW_NUMBER() OVER (PARTITION BY ExamId ORDER BY StartsAt, SessionId, Slot) AS K
    FROM #slot
)
UPDATE s SET s.K = k.K FROM #slot s JOIN k ON k.SessionId = s.SessionId AND k.Slot = s.Slot;

IF EXISTS (SELECT 1 FROM #slot WHERE K > 881)
    RAISERROR('seed 043: too many slots for one exam, candidate spread would repeat', 16, 1);

UPDATE s
   SET s.CandidateId = c.CandidateId, s.AccCode = c.DefaultAccommodationCode
  FROM #slot s
  CROSS APPLY (SELECT CAST((CAST(s.K AS BIGINT) * 409 + s.ExamId * 53) % 881 AS INT) AS idx) x
  JOIN dbo.Candidate c
    ON c.CandidateNumber = 'NW' + RIGHT('00000000' + CAST(CASE WHEN x.idx < 880 THEN x.idx + 1 ELSE 1000 END AS VARCHAR(8)), 8);

-- extra-time candidates only where the session has an accommodation seat for them
;WITH et AS (
    SELECT SessionId, Slot, AccCap, ROW_NUMBER() OVER (PARTITION BY SessionId ORDER BY Slot) AS rn
    FROM #slot WHERE AccCode IN ('ET25','ET50')
)
DELETE s FROM #slot s JOIN et ON et.SessionId = s.SessionId AND et.Slot = s.Slot WHERE et.rn > et.AccCap;

;WITH r AS (
    SELECT SessionId, Slot,
           ROW_NUMBER() OVER (PARTITION BY SessionId ORDER BY Slot) AS rn,
           COUNT(*) OVER (PARTITION BY SessionId) AS cnt
    FROM #slot
)
UPDATE s
   SET s.Status = CASE
            WHEN s.SessStatus = 'CANCELLED' THEN 'CANCELLED'
            WHEN s.IsFull = 1 THEN CASE WHEN r.rn <= s.Capacity THEN 'BOOKED' ELSE 'WAITLISTED' END
            WHEN s.SessStatus = 'DELIVERED' THEN
                 CASE WHEN h.h % 100 < 7 THEN 'CANCELLED'
                      WHEN h.h % 100 < 10 OR s.StartsAt >= @Pending THEN 'BOOKED'
                      ELSE 'ATTENDED' END
            ELSE CASE WHEN h.h % 100 < 9 THEN 'CANCELLED' ELSE 'BOOKED' END
        END,
       s.Channel = CASE
            WHEN s.IsFull = 1 AND r.rn > s.Capacity THEN 'PORTAL'
            WHEN h.h2 % 100 < 58 THEN 'PORTAL'
            WHEN h.h2 % 100 < 84 THEN 'PARTNER'
            ELSE 'IMPORT' END,
       s.BookedAt = DATEADD(MINUTE, -((r.cnt - r.rn + 1) * 600 + h.h % 300),
                        CASE WHEN DATEADD(DAY, -1, s.StartsAt) > @Cutoff THEN @Cutoff ELSE DATEADD(DAY, -1, s.StartsAt) END)
  FROM #slot s
  JOIN r ON r.SessionId = s.SessionId AND r.Slot = s.Slot
  CROSS APPLY (SELECT CAST((CAST(s.SessionId AS BIGINT) * 7919 + s.Slot * 104729 + 3) % 1000 AS INT) AS h,
                      CAST((CAST(s.CandidateId AS BIGINT) * 3571 + s.SessionId * 31 + 7) % 1000 AS INT) AS h2) h;

INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef,
                         BookedAt, CancelledAt, CancelReason, LastModified)
SELECT s.SessionId, s.CandidateId, s.Status, s.AccCode, s.Channel,
       CASE s.Channel
            WHEN 'PARTNER' THEN 'PTR-' + RIGHT('0000000' + CAST(s.SessionId * 100 + s.Slot AS VARCHAR(10)), 7)
            WHEN 'IMPORT'  THEN 'IMP' + CONVERT(CHAR(6), s.BookedAt, 112) + '-' + CAST(s.SessionId AS VARCHAR(10)) + '-' + CAST(s.Slot AS VARCHAR(4))
       END,
       s.BookedAt,
       c.CancelledAt,
       CASE WHEN s.Status <> 'CANCELLED' THEN NULL
            WHEN s.SessStatus = 'CANCELLED' THEN N'Session cancelled by centre'
            ELSE CASE s.Slot % 4 WHEN 0 THEN N'Candidate request' WHEN 1 THEN N'Illness'
                                 WHEN 2 THEN CASE WHEN s.Channel = 'PARTNER' THEN N'Partner cancellation' ELSE N'Candidate request' END
                                 ELSE N'Work commitments' END
       END,
       CASE WHEN s.Status = 'ATTENDED' THEN DATEADD(DAY, 2, s.StartsAt) ELSE ISNULL(c.CancelledAt, s.BookedAt) END
FROM #slot s
CROSS APPLY (SELECT CASE WHEN DATEADD(HOUR, -2, s.StartsAt) < @Cutoff THEN DATEADD(HOUR, -2, s.StartsAt) ELSE @Cutoff END AS Latest) l
CROSS APPLY (SELECT CASE WHEN s.Status <> 'CANCELLED' THEN NULL
                         WHEN DATEADD(DAY, 1 + s.Slot % 5, s.BookedAt) > l.Latest THEN l.Latest
                         ELSE DATEADD(DAY, 1 + s.Slot % 5, s.BookedAt) END AS CancelledAt) c
ORDER BY s.SessionId, s.Slot;

INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position, AddedAt)
SELECT b.SessionId, b.CandidateId, b.BookingId,
       ROW_NUMBER() OVER (PARTITION BY b.SessionId ORDER BY b.BookedAt, b.BookingId),
       b.BookedAt
FROM dbo.Booking b
WHERE b.Status = 'WAITLISTED'
ORDER BY b.SessionId, b.BookingId;

DROP TABLE #slot;
DROP TABLE #n;
GO
