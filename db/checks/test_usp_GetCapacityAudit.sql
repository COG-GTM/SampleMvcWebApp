/*
    test_usp_GetCapacityAudit.sql - SQL test for dbo.usp_GetCapacityAudit (MBA-3828) on seed data.
    Makes capacity changes (written the way the retired usp_ChangeCapacity did, MBA-3824) plus
    hand-written AuditLog rows inside one
    transaction, checks the proc output, then ROLLS BACK (leaves the database unchanged).
    Test rows are moved to 2030 so they never mix with real/seed audit rows.
    Prints PASS/FAIL per check and raises error 50000 at the end if any check failed.
    Run:  sqlcmd -S localhost,1433 -E -C -b -i db/checks/test_usp_GetCapacityAudit.sql
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @results TABLE (Seq INT IDENTITY(1,1), Name VARCHAR(200), Passed BIT, Info NVARCHAR(400));
DECLARE @out TABLE (AuditId BIGINT, LoggedAt DATETIME, Actor VARCHAR(60), Detail NVARCHAR(1000), SessionId INT,
                    ExamCode VARCHAR(20), SessionStartsAt DATETIME, RoomName NVARCHAR(50), OldCapacity INT,
                    NewCapacity INT, Delta BIGINT, CurrentCapacity INT, CurrentSeatsAvailable INT, TotalRows INT,
                    Seq INT IDENTITY(1,1));

DECLARE @c1 INT = (SELECT TestCenterId FROM dbo.ExamSession WHERE SessionId = 154);
DECLARE @s1 INT, @s2 INT, @s3 INT, @x1 INT, @c2 INT;
SELECT @s1 = MIN(SessionId) FROM dbo.ExamSession WHERE TestCenterId = @c1 AND Status = 'OPEN';
SELECT @s2 = MIN(SessionId) FROM dbo.ExamSession WHERE TestCenterId = @c1 AND Status = 'OPEN' AND SessionId > @s1;
SELECT @s3 = MIN(SessionId) FROM dbo.ExamSession WHERE TestCenterId = @c1 AND Status = 'OPEN' AND SessionId > @s2;
SELECT @x1 = MIN(SessionId) FROM dbo.ExamSession WHERE TestCenterId <> @c1 AND Status = 'OPEN';
SELECT @c2 = TestCenterId FROM dbo.ExamSession WHERE SessionId = @x1;
DECLARE @cap1 INT = (SELECT Capacity FROM dbo.ExamSession WHERE SessionId = @s1);
DECLARE @cap2 INT = (SELECT Capacity FROM dbo.ExamSession WHERE SessionId = @s2);
DECLARE @cap3 INT = (SELECT Capacity FROM dbo.ExamSession WHERE SessionId = @s3);
DECLARE @capx INT = (SELECT Capacity FROM dbo.ExamSession WHERE SessionId = @x1);
DECLARE @new1 INT = @cap1 + 2, @new2 INT = @cap2 + 1, @new3 INT = @cap3 + 3, @newx INT = @capx + 1;
PRINT CONCAT('centre C1=', @c1, ' sessions ', @s1, ',', @s2, ',', @s3, '; centre C2=', @c2, ' session ', @x1);

DECLARE @maxAudit BIGINT = (SELECT ISNULL(MAX(AuditId), 0) FROM dbo.AuditLog);
DECLARE @a1 BIGINT, @a2 BIGINT, @a3 BIGINT, @ax BIGINT, @bad BIGINT, @sync BIGINT;
DECLARE @before INT, @after INT, @n INT;

BEGIN TRANSACTION;

-- three portal-style changes at C1, one at C2 (raises only, so trigger 033 demotes nothing),
-- same capacity update and '<old> -> <new>' AuditLog row as the retired usp_ChangeCapacity (legacy-baseline)
UPDATE dbo.ExamSession SET Capacity = CASE SessionId WHEN @s1 THEN @new1 WHEN @s2 THEN @new2 WHEN @s3 THEN @new3 ELSE @newx END,
       SeatsAvailable = SeatsAvailable + (CASE SessionId WHEN @s1 THEN @new1 WHEN @s2 THEN @new2 WHEN @s3 THEN @new3 ELSE @newx END - Capacity)
 WHERE SessionId IN (@s1, @s2, @s3, @x1);
INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES ('ExamSession', @s1, 'CAPACITY', CONCAT(@cap1, ' -> ', @new1), 'portal-admin');
SELECT @a1 = MAX(AuditId) FROM dbo.AuditLog WHERE EntityId = @s1 AND Action = 'CAPACITY';
INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES ('ExamSession', @s2, 'CAPACITY', CONCAT(@cap2, ' -> ', @new2), 'portal-admin');
SELECT @a2 = MAX(AuditId) FROM dbo.AuditLog WHERE EntityId = @s2 AND Action = 'CAPACITY';
INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES ('ExamSession', @s3, 'CAPACITY', CONCAT(@cap3, ' -> ', @new3), 'test-admin');
SELECT @a3 = MAX(AuditId) FROM dbo.AuditLog WHERE EntityId = @s3 AND Action = 'CAPACITY';
INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES ('ExamSession', @x1, 'CAPACITY', CONCAT(@capx, ' -> ', @newx), 'portal-admin');
SELECT @ax = MAX(AuditId) FROM dbo.AuditLog WHERE EntityId = @x1 AND Action = 'CAPACITY';

-- malformed CAPACITY detail and a capacity-sync run row (EntityId 0)
INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES ('ExamSession', @s1, 'CAPACITY', N'capacity reset by DBA', 'dba');
SET @bad = SCOPE_IDENTITY();
INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES ('ExamSession', 0, 'CAPACITY_SYNC', N'trigger=SCHEDULED status=OK checked=285 updated=112', 'capacity-sync');
SET @sync = SCOPE_IDENTITY();
INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor) VALUES ('ExamSession', 0, 'CAPACITY', N'1 -> 2', 'x');

UPDATE dbo.AuditLog SET LoggedAt = CASE AuditId WHEN @a1 THEN '2030-01-01T09:00:00' WHEN @a2 THEN '2030-01-01T09:10:00'
                                                WHEN @a3 THEN '2030-01-01T09:20:00' WHEN @ax THEN '2030-01-01T09:30:00'
                                                WHEN @bad THEN '2030-01-01T09:40:00' ELSE '2030-01-01T09:50:00' END
 WHERE AuditId > @maxAudit;

-- read-only check: fingerprint the tables before and after all proc calls
DECLARE @fp BIGINT = (SELECT MAX(AuditId) FROM dbo.AuditLog);
SELECT @before = CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.AuditLog WHERE AuditId <= @fp;
SET @before = @before ^ (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.ExamSession)
                      ^ (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.Booking)
                      ^ (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.Waitlist);
DECLARE @bookingsBefore INT = (SELECT COUNT(*) FROM dbo.Booking), @auditBefore INT = (SELECT COUNT(*) FROM dbo.AuditLog);

-- 1. C1, whole day: 3 changes + malformed row, newest first, no sync / EntityId 0 rows
INSERT INTO @out (AuditId, LoggedAt, Actor, Detail, SessionId, ExamCode, SessionStartsAt, RoomName, OldCapacity, NewCapacity, Delta, CurrentCapacity, CurrentSeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = '2030-01-01', @To = '2030-01-02';
SET @n = (SELECT COUNT(*) FROM @out);
INSERT INTO @results VALUES ('C1 lists exactly 4 rows (3 changes + malformed)', IIF(@n = 4, 1, 0), CONCAT('rows=', @n));
INSERT INTO @results VALUES ('C1 rows newest first',
    IIF(STUFF((SELECT ',' + CAST(AuditId AS VARCHAR(20)) FROM @out ORDER BY Seq FOR XML PATH('')), 1, 1, '')
        = CONCAT(@bad, ',', @a3, ',', @a2, ',', @a1)
        , 1, 0), NULL);
INSERT INTO @results VALUES ('C1 excludes C2 change, CAPACITY_SYNC and EntityId 0',
    IIF(NOT EXISTS (SELECT 1 FROM @out WHERE AuditId IN (@ax, @sync) OR SessionId NOT IN (@s1, @s2, @s3)), 1, 0), NULL);
INSERT INTO @results VALUES ('old/new/delta/actor for session s1',
    IIF(EXISTS (SELECT 1 FROM @out WHERE AuditId = @a1 AND OldCapacity = @cap1 AND NewCapacity = @cap1 + 2 AND Delta = 2 AND Actor = 'portal-admin'), 1, 0), NULL);
INSERT INTO @results VALUES ('old/new/delta/actor for session s2',
    IIF(EXISTS (SELECT 1 FROM @out WHERE AuditId = @a2 AND OldCapacity = @cap2 AND NewCapacity = @cap2 + 1 AND Delta = 1 AND Actor = 'portal-admin'), 1, 0), NULL);
INSERT INTO @results VALUES ('old/new/delta/actor for session s3',
    IIF(EXISTS (SELECT 1 FROM @out WHERE AuditId = @a3 AND OldCapacity = @cap3 AND NewCapacity = @cap3 + 3 AND Delta = 3 AND Actor = 'test-admin'), 1, 0), NULL);
INSERT INTO @results VALUES ('malformed detail: raw text, NULL old/new/delta',
    IIF(EXISTS (SELECT 1 FROM @out WHERE AuditId = @bad AND Detail = N'capacity reset by DBA' AND OldCapacity IS NULL AND NewCapacity IS NULL AND Delta IS NULL), 1, 0), NULL);
INSERT INTO @results VALUES ('session columns: exam, start, room, current capacity/seats',
    IIF(NOT EXISTS (SELECT 1 FROM @out o JOIN dbo.ExamSession s ON s.SessionId = o.SessionId JOIN dbo.Exam e ON e.ExamId = s.ExamId JOIN dbo.Room r ON r.RoomId = s.RoomId
                     WHERE o.ExamCode <> e.ExamCode OR o.SessionStartsAt <> s.StartsAt OR o.RoomName <> r.RoomName
                        OR o.CurrentCapacity <> s.Capacity OR o.CurrentSeatsAvailable <> s.SeatsAvailable), 1, 0), NULL);
INSERT INTO @results VALUES ('TotalRows = 4', IIF(NOT EXISTS (SELECT 1 FROM @out WHERE TotalRows <> 4), 1, 0), NULL);

-- 2. sessionId filter
DELETE FROM @out;
INSERT INTO @out (AuditId, LoggedAt, Actor, Detail, SessionId, ExamCode, SessionStartsAt, RoomName, OldCapacity, NewCapacity, Delta, CurrentCapacity, CurrentSeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = '2030-01-01', @To = '2030-01-02', @SessionId = @s2;
INSERT INTO @results VALUES ('sessionId filter shows only that session',
    IIF((SELECT COUNT(*) FROM @out) = 1 AND NOT EXISTS (SELECT 1 FROM @out WHERE SessionId <> @s2), 1, 0), NULL);

-- 3. C2 sees only its own change; a C1 session id at C2 returns nothing
DELETE FROM @out;
INSERT INTO @out (AuditId, LoggedAt, Actor, Detail, SessionId, ExamCode, SessionStartsAt, RoomName, OldCapacity, NewCapacity, Delta, CurrentCapacity, CurrentSeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c2, @From = '2030-01-01', @To = '2030-01-02';
INSERT INTO @results VALUES ('C2 lists only its one change',
    IIF((SELECT COUNT(*) FROM @out) = 1 AND EXISTS (SELECT 1 FROM @out WHERE AuditId = @ax AND Delta = 1), 1, 0), NULL);
DELETE FROM @out;
INSERT INTO @out (AuditId, LoggedAt, Actor, Detail, SessionId, ExamCode, SessionStartsAt, RoomName, OldCapacity, NewCapacity, Delta, CurrentCapacity, CurrentSeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c2, @From = '2030-01-01', @To = '2030-01-02', @SessionId = @s1;
INSERT INTO @results VALUES ('session of another centre returns no rows', IIF((SELECT COUNT(*) FROM @out) = 0, 1, 0), NULL);

-- 4. date range: @From inclusive, @To exclusive
DELETE FROM @out;
INSERT INTO @out (AuditId, LoggedAt, Actor, Detail, SessionId, ExamCode, SessionStartsAt, RoomName, OldCapacity, NewCapacity, Delta, CurrentCapacity, CurrentSeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = '2030-01-01T09:10:00', @To = '2030-01-01T09:40:00';
INSERT INTO @results VALUES ('range is [From, To)',
    IIF((SELECT COUNT(*) FROM @out) = 2 AND NOT EXISTS (SELECT 1 FROM @out WHERE AuditId NOT IN (@a2, @a3)), 1, 0), NULL);

-- 5. paging
DELETE FROM @out;
INSERT INTO @out (AuditId, LoggedAt, Actor, Detail, SessionId, ExamCode, SessionStartsAt, RoomName, OldCapacity, NewCapacity, Delta, CurrentCapacity, CurrentSeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = '2030-01-01', @To = '2030-01-02', @PageNumber = 2, @PageSize = 3;
INSERT INTO @results VALUES ('page 2 of size 3 holds the oldest row, TotalRows 4',
    IIF((SELECT COUNT(*) FROM @out) = 1 AND EXISTS (SELECT 1 FROM @out WHERE AuditId = @a1 AND TotalRows = 4), 1, 0), NULL);
DELETE FROM @out;
INSERT INTO @out (AuditId, LoggedAt, Actor, Detail, SessionId, ExamCode, SessionStartsAt, RoomName, OldCapacity, NewCapacity, Delta, CurrentCapacity, CurrentSeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = '2030-01-01', @To = '2030-01-02', @PageNumber = 3, @PageSize = 3;
INSERT INTO @results VALUES ('page past the end is empty', IIF((SELECT COUNT(*) FROM @out) = 0, 1, 0), NULL);

-- 6. Detail parser edge cases (same cases as CapacityAuditDetailTests)
DECLARE @cases TABLE (Detail NVARCHAR(1000), ExpOld INT, ExpNew INT);
INSERT INTO @cases VALUES (N'12 -> 9', 12, 9), (N'0 -> 30', 0, 30), (N'-1 -> 5', -1, 5), (N'2147483647 -> 0', 2147483647, 0),
    (N'12 -> 9 ', NULL, NULL), (N' 12 -> 9', NULL, NULL), (N'12->9', NULL, NULL), (N'12  -> 9', NULL, NULL), (N'1 -> 2 -> 3', NULL, NULL),
    (N'a -> 9', NULL, NULL), (N'12 -> ', NULL, NULL), (N' -> 9', NULL, NULL), (N'2147483648 -> 1', NULL, NULL), (N'1.5 -> 2', NULL, NULL), (N'00000000001 -> 1', NULL, NULL), (N'12 -> 9' + NCHAR(10), NULL, NULL),
    (N'+1 -> 2', NULL, NULL), (N'1 - > 2', NULL, NULL), (N'', NULL, NULL), (NULL, NULL, NULL);
INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor, LoggedAt)
SELECT 'ExamSession', @s1, 'CAPACITY', Detail, 'parser-test', '2030-02-01T10:00:00' FROM @cases;
DELETE FROM @out;
INSERT INTO @out (AuditId, LoggedAt, Actor, Detail, SessionId, ExamCode, SessionStartsAt, RoomName, OldCapacity, NewCapacity, Delta, CurrentCapacity, CurrentSeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = '2030-02-01', @To = '2030-02-02', @PageSize = 100;
INSERT INTO @results
SELECT CONCAT('parse ''', ISNULL(c.Detail, N'<NULL>'), ''''),
       IIF(o.AuditId IS NOT NULL AND ISNULL(o.OldCapacity, -999) = ISNULL(c.ExpOld, -999) AND ISNULL(o.NewCapacity, -999) = ISNULL(c.ExpNew, -999)
           AND (o.Delta = CAST(c.ExpNew AS BIGINT) - c.ExpOld OR (o.Delta IS NULL AND c.ExpOld IS NULL)), 1, 0),
       CONCAT('old=', o.OldCapacity, ' new=', o.NewCapacity, ' delta=', o.Delta)
  FROM @cases c
  OUTER APPLY (SELECT TOP 1 * FROM @out x WHERE ISNULL(x.Detail, N'<NULL>') = ISNULL(c.Detail, N'<NULL>')
                AND ISNULL(DATALENGTH(x.Detail), -1) = ISNULL(DATALENGTH(c.Detail), -1)) o;

-- 7. the proc wrote nothing
SET @after = (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.AuditLog WHERE AuditId <= @fp)
           ^ (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.ExamSession)
           ^ (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.Booking)
           ^ (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.Waitlist);
INSERT INTO @results VALUES ('proc calls changed no AuditLog/ExamSession/Booking/Waitlist rows',
    IIF(@after = @before AND (SELECT COUNT(*) FROM dbo.AuditLog) = @auditBefore + (SELECT COUNT(*) FROM @cases)
        AND (SELECT COUNT(*) FROM dbo.AuditLog WHERE AuditId > @fp AND Actor <> 'parser-test') = 0
        AND (SELECT COUNT(*) FROM dbo.Booking) = @bookingsBefore, 1, 0), NULL);
INSERT INTO @results VALUES ('proc body has no INSERT/UPDATE/DELETE/MERGE',
    IIF(NOT EXISTS (SELECT 1 FROM sys.sql_modules WHERE object_id = OBJECT_ID('dbo.usp_GetCapacityAudit')
                    AND (definition LIKE '%INSERT %' OR definition LIKE '%UPDATE %' OR definition LIKE '%DELETE %' OR definition LIKE '%MERGE %')), 1, 0), NULL);

ROLLBACK TRANSACTION;

SELECT IIF(Passed = 1, 'PASS', 'FAIL') AS Result, Name, Info FROM @results ORDER BY Seq;
DECLARE @failed INT = (SELECT COUNT(*) FROM @results WHERE Passed = 0), @total INT = (SELECT COUNT(*) FROM @results);
IF @failed > 0
    RAISERROR('test_usp_GetCapacityAudit: %d of %d checks FAILED', 16, 1, @failed, @total);
ELSE
    PRINT CONCAT('test_usp_GetCapacityAudit: all ', @total, ' checks passed');
GO
