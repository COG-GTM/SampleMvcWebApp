/*
    Seed 045 - a few extra sessions and attempt histories (NW00000881 .. NW00000900)
    carried over from the 2026 UAT data set.
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

IF OBJECT_ID('tempdb..#sess') IS NOT NULL DROP TABLE #sess;
CREATE TABLE #sess (Tag VARCHAR(8) PRIMARY KEY, ExamCode VARCHAR(20), CenterCode VARCHAR(10), StartsAt DATETIME,
                    Capacity INT, AccCap INT, Status VARCHAR(12), SessionId INT NULL);

INSERT INTO #sess (Tag, ExamCode, CenterCode, StartsAt, Capacity, AccCap, Status) VALUES
 ('A0915', 'BCB-PM100', 'LDN01', '20260916 15:00', 12, 0, 'DELIVERED'),
 ('A1014', 'BCB-PM100', 'LDN01', '20261014 15:00', 12, 0, 'DELIVERED'),
 ('A1104', 'BCB-PM100', 'LDN01', '20261104 15:00', 12, 0, 'DELIVERED'),
 ('A1118', 'BCB-PM100', 'LDN01', '20261118 15:00', 16, 1, 'OPEN'),
 ('B0915', 'CTA-SEC2',  'BOS01', '20260915 16:00', 12, 0, 'DELIVERED'),
 ('B1020', 'CTA-SEC2',  'BOS01', '20261020 16:00', 12, 0, 'DELIVERED'),
 ('B1103', 'CTA-SEC2',  'BOS01', '20261103 16:00', 12, 0, 'DELIVERED'),
 ('B1124', 'CTA-SEC2',  'BOS01', '20261124 16:00', 16, 1, 'OPEN'),
 ('C1016', 'MHL-RN1',   'SYD01', '20261016 15:00', 12, 0, 'DELIVERED'),
 ('C1123', 'MHL-RN1',   'SYD01', '20261123 15:00', 18, 2, 'OPEN');

DECLARE @tag VARCHAR(8);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT Tag FROM #sess ORDER BY StartsAt, Tag;
OPEN c;
FETCH NEXT FROM c INTO @tag;
WHILE @@FETCH_STATUS = 0
BEGIN
    INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, Status, LastModified)
    SELECT e.ExamId, tc.TestCenterId,
           (SELECT MIN(r.RoomId) FROM dbo.Room r WHERE r.TestCenterId = tc.TestCenterId AND r.IsActive = 1),
           x.StartsAt, x.Capacity, x.AccCap, x.Capacity, x.Status,
           CASE WHEN x.Status = 'DELIVERED' THEN DATEADD(DAY, 1, x.StartsAt) ELSE '20261110 18:00' END
    FROM #sess x
    JOIN dbo.Exam e ON e.ExamCode = x.ExamCode
    JOIN dbo.TestCenter tc ON tc.CenterCode = x.CenterCode
    WHERE x.Tag = @tag;

    UPDATE #sess SET SessionId = SCOPE_IDENTITY() WHERE Tag = @tag;
    FETCH NEXT FROM c INTO @tag;
END
CLOSE c; DEALLOCATE c;

IF OBJECT_ID('tempdb..#hist') IS NOT NULL DROP TABLE #hist;
CREATE TABLE #hist (CandidateNumber VARCHAR(12), Tag VARCHAR(8), Status VARCHAR(12), Channel VARCHAR(10), Score INT NULL, Seq INT IDENTITY(1,1));

INSERT INTO #hist (CandidateNumber, Tag, Status, Channel, Score) VALUES
 ('NW00000892', 'A0915', 'ATTENDED', 'PORTAL',  604),
 ('NW00000896', 'A0915', 'ATTENDED', 'PARTNER', 781),
 ('NW00000894', 'B0915', 'ATTENDED', 'PORTAL',  655),
 ('NW00000897', 'B0915', 'ATTENDED', 'PORTAL',  742),
 ('NW00000881', 'A1014', 'ATTENDED', 'PORTAL',  642),
 ('NW00000882', 'A1014', 'ATTENDED', 'PARTNER', 688),
 ('NW00000884', 'A1014', 'ATTENDED', 'PORTAL',  571),
 ('NW00000888', 'A1014', 'ATTENDED', 'PORTAL',  733),
 ('NW00000889', 'A1014', 'ATTENDED', 'IMPORT',  702),
 ('NW00000885', 'B1020', 'ATTENDED', 'PORTAL',  612),
 ('NW00000886', 'B1020', 'ATTENDED', 'PARTNER', 684),
 ('NW00000890', 'B1020', 'ATTENDED', 'PORTAL',  755),
 ('NW00000887', 'C1016', 'ATTENDED', 'PORTAL',  598),
 ('NW00000891', 'C1016', 'ATTENDED', 'PORTAL',  710),
 ('NW00000893', 'A1104', 'ATTENDED', 'PORTAL',  633),
 ('NW00000898', 'A1104', 'ATTENDED', 'PORTAL',  719),
 ('NW00000895', 'B1103', 'ATTENDED', 'PARTNER', 667),
 ('NW00000899', 'A1118', 'BOOKED',   'PORTAL',  NULL),
 ('NW00000900', 'B1124', 'BOOKED',   'PARTNER', NULL),
 ('NW00000883', 'C1123', 'BOOKED',   'PORTAL',  NULL);

INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel, ExternalRef, BookedAt, LastModified)
SELECT s.SessionId, c.CandidateId, h.Status,
       c.DefaultAccommodationCode,
       h.Channel,
       CASE h.Channel WHEN 'PARTNER' THEN 'PTR-U' + RIGHT('00000' + CAST(h.Seq * 37 AS VARCHAR(6)), 5)
                      WHEN 'IMPORT'  THEN 'IMP' + CONVERT(CHAR(6), s.StartsAt, 112) + '-U' + CAST(h.Seq AS VARCHAR(4)) END,
       DATEADD(MINUTE, h.Seq * 53, DATEADD(DAY, -21, CASE WHEN s.StartsAt > '20261110' THEN CAST('20261101' AS DATETIME) ELSE s.StartsAt END)),
       CASE WHEN h.Status = 'ATTENDED' THEN DATEADD(DAY, 2, s.StartsAt)
            ELSE DATEADD(MINUTE, h.Seq * 53, DATEADD(DAY, -21, CASE WHEN s.StartsAt > '20261110' THEN CAST('20261101' AS DATETIME) ELSE s.StartsAt END)) END
FROM #hist h
JOIN #sess s ON s.Tag = h.Tag
JOIN dbo.Candidate c ON c.CandidateNumber = h.CandidateNumber
ORDER BY h.Seq;

INSERT INTO dbo.Result (BookingId, CandidateId, ExamId, SessionId, Score, Passed, ScoredAt, SourceFile, ReceivedAt)
SELECT b.BookingId, b.CandidateId, es.ExamId, es.SessionId, h.Score,
       CASE WHEN h.Score >= e.PassingScore THEN 1 ELSE 0 END,
       DATEADD(MINUTE, 375, CAST(CAST(DATEADD(DAY, 2, es.StartsAt) AS DATE) AS DATETIME)),
       N'NW_SCORES_' + LEFT(e.ExamCode, CHARINDEX('-', e.ExamCode) - 1) + N'_' + CONVERT(NCHAR(8), DATEADD(DAY, 2, es.StartsAt), 112) + N'.csv',
       DATEADD(MINUTE, 400, CAST(CAST(DATEADD(DAY, 2, es.StartsAt) AS DATE) AS DATETIME))
FROM #hist h
JOIN #sess s ON s.Tag = h.Tag
JOIN dbo.ExamSession es ON es.SessionId = s.SessionId
JOIN dbo.Exam e ON e.ExamId = es.ExamId
JOIN dbo.Candidate c ON c.CandidateNumber = h.CandidateNumber
JOIN dbo.Booking b ON b.SessionId = es.SessionId AND b.CandidateId = c.CandidateId
WHERE h.Status = 'ATTENDED'
ORDER BY h.Seq;

DROP TABLE #hist;
DROP TABLE #sess;
GO
