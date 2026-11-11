/*
    Seed 044 - results for ATTENDED bookings, then retakes for some of the
    candidates who failed (rebooked at least a month later), then results for
    any retakes that have already been delivered.
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

DECLARE @Cutoff DATETIME = '20261110 18:00';
DECLARE @Pass INT = 1;

WHILE @Pass <= 2
BEGIN
    INSERT INTO dbo.Result (BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt, SourceFile, ReceivedAt)
    SELECT b.BookingId, b.CandidateId, s.ExamId, s.SessionId, sc.Score,
           CASE WHEN sc.Score >= e.PassingScore THEN 1 ELSE 0 END,
           DATEADD(MINUTE, 375, CAST(CAST(DATEADD(DAY, 2, s.StartsAt) AS DATE) AS DATETIME)),
           CASE WHEN b.BookingId % 5 = 0 THEN NULL
                ELSE N'NW_SCORES_' + LEFT(e.ExamCode, CHARINDEX('-', e.ExamCode) - 1) + N'_'
                     + CONVERT(NCHAR(8), DATEADD(DAY, 2, s.StartsAt), 112) + N'.csv' END,
           DATEADD(MINUTE, 390 + b.BookingId % 30, CAST(CAST(DATEADD(DAY, 2, s.StartsAt) AS DATE) AS DATETIME))
    FROM dbo.Booking b
    JOIN dbo.ExamSession s ON s.SessionId = b.SessionId
    JOIN dbo.Exam e ON e.ExamId = s.ExamId
    CROSS APPLY (SELECT 380 + CAST((CAST(b.CandidateId AS BIGINT) * 7919 + s.SessionId * 13) % 300 AS INT)
                            + CAST((CAST(b.CandidateId AS BIGINT) * 104729 + s.ExamId * 31) % 300 AS INT) AS Score) sc
    WHERE b.Status = 'ATTENDED'
      AND NOT EXISTS (SELECT 1 FROM dbo.Result r WHERE r.BookingId = b.BookingId)
    ORDER BY b.BookingId;

    IF @Pass = 1
    BEGIN
        IF OBJECT_ID('tempdb..#retake') IS NOT NULL DROP TABLE #retake;

        SELECT r.CandidateId, r.ExamId, fs.StartsAt AS FailedOn, t.SessionId, t.StartsAt, t.Status AS SessStatus,
               t.Capacity - (SELECT COUNT(*) FROM dbo.Booking x WHERE x.SessionId = t.SessionId AND x.Status IN ('BOOKED','ATTENDED')) AS FreeSeats,
               c.DefaultAccommodationCode AS AccCode,
               ROW_NUMBER() OVER (PARTITION BY t.SessionId ORDER BY fs.StartsAt, r.CandidateId) AS rn
        INTO #retake
        FROM dbo.Result r
        JOIN dbo.ExamSession fs ON fs.SessionId = r.SessionId
        JOIN dbo.Candidate c ON c.CandidateId = r.CandidateId
        CROSS APPLY (SELECT TOP (1) s2.SessionId, s2.StartsAt, s2.Status, s2.Capacity
                       FROM dbo.ExamSession s2
                      WHERE s2.ExamId = r.ExamId
                        AND s2.Status IN ('OPEN','DELIVERED')
                        AND s2.StartsAt >= DATEADD(DAY, 31 + r.CandidateId % 28, fs.StartsAt)
                        AND NOT EXISTS (SELECT 1 FROM dbo.Booking w WHERE w.SessionId = s2.SessionId AND w.Status = 'WAITLISTED')
                      ORDER BY s2.StartsAt, s2.SessionId) t
        WHERE r.Passed = 0
          AND (r.CandidateId * 7 + r.ExamId) % 10 < 6
          AND ISNULL(c.DefaultAccommodationCode, '') NOT IN ('ET25','ET50');

        INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef, BookedAt, LastModified)
        SELECT rt.SessionId, rt.CandidateId,
               CASE WHEN rt.SessStatus = 'DELIVERED' AND rt.StartsAt < '20261108' THEN 'ATTENDED' ELSE 'BOOKED' END,
               rt.AccCode,
               CASE WHEN rt.CandidateId % 4 = 1 THEN 'PARTNER' ELSE 'PORTAL' END,
               CASE WHEN rt.CandidateId % 4 = 1 THEN 'PTR-R' + RIGHT('000000' + CAST(rt.SessionId * 1000 + rt.CandidateId % 1000 AS VARCHAR(10)), 6) END,
               bk.BookedAt,
               CASE WHEN rt.SessStatus = 'DELIVERED' AND rt.StartsAt < '20261108' THEN DATEADD(DAY, 2, rt.StartsAt) ELSE bk.BookedAt END
        FROM #retake rt
        CROSS APPLY (SELECT CASE WHEN DATEADD(DAY, -1, rt.StartsAt) < @Cutoff THEN DATEADD(DAY, -1, rt.StartsAt) ELSE @Cutoff END AS Latest) l
        CROSS APPLY (SELECT CASE WHEN DATEADD(DAY, 4 + rt.CandidateId % 6, rt.FailedOn) < l.Latest
                                 THEN DATEADD(MINUTE, rt.CandidateId % 480, DATEADD(DAY, 4 + rt.CandidateId % 6, rt.FailedOn))
                                 ELSE DATEADD(MINUTE, -(rt.CandidateId % 480), l.Latest) END AS BookedAt) bk
        WHERE rt.rn <= rt.FreeSeats
        ORDER BY rt.SessionId, rt.rn;

        DROP TABLE #retake;
    END

    SET @Pass = @Pass + 1;
END
GO
