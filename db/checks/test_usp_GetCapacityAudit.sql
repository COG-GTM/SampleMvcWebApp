/*
    Check for db/procs/030_usp_GetCapacityAudit.sql (MBA-3828). Run on a fresh seed:
        sqlcmd -S localhost,1433 -U sa -P <sa password> -C -b -I -i db/checks/test_usp_GetCapacityAudit.sql
    Everything happens inside BEGIN TRAN ... ROLLBACK. Test rows are dated January 2001 so seed/relay rows never
    fall into the queried range. Any failed assertion THROWs (sqlcmd -b exits non-zero); each passing one PRINTs PASS.
*/
SET NOCOUNT ON;
SET XACT_ABORT ON;
USE AssessmentDelivery;
GO

DECLARE @c1 INT, @c2 INT, @s1 INT, @s2 INT, @s3 INT, @s4 INT;
SELECT TOP (1) @c1 = TestCenterId FROM dbo.ExamSession GROUP BY TestCenterId HAVING COUNT(*) >= 3 ORDER BY TestCenterId;
SELECT TOP (1) @c2 = TestCenterId FROM dbo.ExamSession WHERE TestCenterId <> @c1 GROUP BY TestCenterId ORDER BY TestCenterId;
SELECT @s1 = MIN(SessionId) FROM dbo.ExamSession WHERE TestCenterId = @c1;
SELECT @s2 = MIN(SessionId) FROM dbo.ExamSession WHERE TestCenterId = @c1 AND SessionId > @s1;
SELECT @s3 = MIN(SessionId) FROM dbo.ExamSession WHERE TestCenterId = @c1 AND SessionId > @s2;
SELECT @s4 = MIN(SessionId) FROM dbo.ExamSession WHERE TestCenterId = @c2;
IF @s3 IS NULL OR @s4 IS NULL THROW 50000, 'FAIL: seed data has no two centres with sessions - run db/apply first', 1;

DECLARE @From DATETIME = '2001-01-01', @To DATETIME = '2001-02-01', @msg NVARCHAR(400);

-- row counts + checksums of every user table, so we can prove the procs write nothing
CREATE TABLE #snap (Phase VARCHAR(10) NOT NULL, TableName SYSNAME NOT NULL, Cnt BIGINT NOT NULL, Chk INT NULL);
DECLARE @snapSql NVARCHAR(MAX) = N'';
SELECT @snapSql = @snapSql + CASE WHEN @snapSql = N'' THEN N'' ELSE N' UNION ALL ' END
     + N'SELECT @phase, N''' + t.name + N''', COUNT_BIG(*), CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM dbo.' + QUOTENAME(t.name)
FROM sys.tables t WHERE t.schema_id = SCHEMA_ID('dbo') AND t.is_ms_shipped = 0;
SET @snapSql = N'INSERT #snap (Phase, TableName, Cnt, Chk) ' + @snapSql;

BEGIN TRAN;

INSERT dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor, LoggedAt) VALUES
 ('ExamSession', @s1, 'CAPACITY', '20 -> 18', 'portal-user', '2001-01-10T10:00:00'),
 ('ExamSession', @s2, 'CAPACITY', CONCAT('outbox event=901 CapacityChanged session=', @s2, ' capacity=25 seats=7 change=22 -> 25 by=admin-ui'),
                                   'scheduling-relay', '2001-01-11T10:00:00'),
 ('ExamSession', @s1, 'CAPACITY', ' 18->-1 ', 'portal-user', '2001-01-12T10:00:00'),
 ('ExamSession', @s3, 'CAPACITY', 'capacity reduced by ops', 'dba', '2001-01-13T10:00:00'),
 ('ExamSession', @s4, 'CAPACITY', '30 -> 10', 'portal-user', '2001-01-12T12:00:00'),
 ('ExamSession', 0, 'CAPACITY_SYNC', 'trigger=SCHEDULED status=COMPLETED checked=285 updated=3', 'capacity-sync', '2001-01-12T02:00:00'),
 ('ExamSession', 0, 'CAPACITY_SYNC', 'trigger=MANUAL status=FAILED checked=0 updated=0 reason=connection reset', 'capacity-sync', '2001-01-13T02:00:00');

EXEC sp_executesql @snapSql, N'@phase VARCHAR(10)', @phase = 'before';

CREATE TABLE #a (Seq INT IDENTITY(1,1) PRIMARY KEY, AuditId BIGINT, LoggedAt DATETIME, SessionId INT, ExamCode VARCHAR(40),
    StartsAt DATETIME, RoomName VARCHAR(100), OldCapacity INT, NewCapacity INT, Delta INT, Actor VARCHAR(60), RawActor VARCHAR(60),
    Detail NVARCHAR(1000), CurrentCapacity INT, SeatsAvailable INT, TotalRows INT);
DECLARE @cols NVARCHAR(400) = N'AuditId, LoggedAt, SessionId, ExamCode, StartsAt, RoomName, OldCapacity, NewCapacity, Delta, Actor, RawActor, Detail, CurrentCapacity, SeatsAvailable, TotalRows';
CREATE TABLE #sync (Seq INT IDENTITY(1,1) PRIMARY KEY, AuditId BIGINT, LoggedAt DATETIME, [Trigger] VARCHAR(40), [Status] VARCHAR(40),
    Checked INT, Updated INT, Reason NVARCHAR(1000), Detail NVARCHAR(1000), Actor VARCHAR(60));

-- 1. centre C1, default page: the 3 changes + the malformed row, newest first; C2 and EntityId 0 never appear
INSERT #a (AuditId, LoggedAt, SessionId, ExamCode, StartsAt, RoomName, OldCapacity, NewCapacity, Delta, Actor, RawActor, Detail, CurrentCapacity, SeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = @From, @To = @To;
IF (SELECT COUNT(*) FROM #a) <> 4 OR EXISTS (SELECT 1 FROM #a WHERE TotalRows <> 4)
    THROW 50001, 'FAIL: C1 should list exactly 4 rows (3 changes + 1 malformed) with TotalRows = 4', 1;
PRINT 'PASS: C1 lists exactly the 3 capacity changes + 1 malformed row (TotalRows = 4)';
IF EXISTS (SELECT 1 FROM #a WHERE SessionId IN (0, @s4)) OR EXISTS (SELECT 1 FROM #a a JOIN dbo.ExamSession s ON s.SessionId = a.SessionId WHERE s.TestCenterId <> @c1)
    THROW 50002, 'FAIL: rows from another centre or EntityId 0 leaked into the C1 list', 1;
PRINT 'PASS: no C2 row and no EntityId 0 (CAPACITY_SYNC) row in the per-session list';
IF EXISTS (SELECT 1 FROM #a a JOIN #a b ON b.Seq = a.Seq + 1 WHERE b.LoggedAt > a.LoggedAt)
   OR (SELECT Detail FROM #a WHERE Seq = 1) <> N'capacity reduced by ops' OR (SELECT LoggedAt FROM #a WHERE Seq = 4) <> '2001-01-10T10:00:00'
    THROW 50003, 'FAIL: rows are not newest first', 1;
PRINT 'PASS: newest first';
IF NOT EXISTS (SELECT 1 FROM #a WHERE Seq = 4 AND SessionId = @s1 AND OldCapacity = 20 AND NewCapacity = 18 AND Delta = -2 AND Actor = 'portal-user' AND RawActor = 'portal-user')
   OR NOT EXISTS (SELECT 1 FROM #a WHERE Seq = 3 AND SessionId = @s2 AND OldCapacity = 22 AND NewCapacity = 25 AND Delta = 3 AND Actor = 'admin-ui' AND RawActor = 'scheduling-relay')
   OR NOT EXISTS (SELECT 1 FROM #a WHERE Seq = 2 AND SessionId = @s1 AND OldCapacity = 18 AND NewCapacity = -1 AND Delta = -19 AND Actor = 'portal-user')
    THROW 50004, 'FAIL: wrong old/new/delta/actor (legacy, relay change=/by=, whitespace/negative)', 1;
PRINT 'PASS: old/new/delta/actor for legacy "<old> -> <new>", relay "change=<old> -> <new> by=", whitespace and negative values';
IF NOT EXISTS (SELECT 1 FROM #a WHERE Seq = 1 AND SessionId = @s3 AND OldCapacity IS NULL AND NewCapacity IS NULL AND Delta IS NULL
                                    AND Detail = N'capacity reduced by ops' AND Actor = 'dba')
    THROW 50005, 'FAIL: malformed Detail should come back raw with NULL old/new/delta', 1;
PRINT 'PASS: malformed Detail passed through raw (OldCapacity/NewCapacity/Delta NULL)';
IF EXISTS (SELECT 1 FROM #a a JOIN dbo.ExamSession s ON s.SessionId = a.SessionId JOIN dbo.Exam e ON e.ExamId = s.ExamId JOIN dbo.Room r ON r.RoomId = s.RoomId
           WHERE a.CurrentCapacity <> s.Capacity OR a.SeatsAvailable <> s.SeatsAvailable OR a.ExamCode <> e.ExamCode OR a.RoomName <> r.RoomName OR a.StartsAt <> s.StartsAt)
    THROW 50006, 'FAIL: session columns do not match ExamSession/Exam/Room', 1;
PRINT 'PASS: ExamCode/StartsAt/RoomName and current Capacity/SeatsAvailable come from the session';

-- 2. sessionId filter
TRUNCATE TABLE #a;
INSERT #a (AuditId, LoggedAt, SessionId, ExamCode, StartsAt, RoomName, OldCapacity, NewCapacity, Delta, Actor, RawActor, Detail, CurrentCapacity, SeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = @From, @To = @To, @SessionId = @s1;
IF (SELECT COUNT(*) FROM #a) <> 2 OR EXISTS (SELECT 1 FROM #a WHERE SessionId <> @s1 OR TotalRows <> 2)
    THROW 50007, 'FAIL: sessionId filter should return only the 2 rows of that session', 1;
PRINT 'PASS: sessionId filter returns only that session''s changes';

-- 3. C2 sees its own row only
TRUNCATE TABLE #a;
INSERT #a (AuditId, LoggedAt, SessionId, ExamCode, StartsAt, RoomName, OldCapacity, NewCapacity, Delta, Actor, RawActor, Detail, CurrentCapacity, SeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c2, @From = @From, @To = @To;
IF (SELECT COUNT(*) FROM #a) <> 1 OR NOT EXISTS (SELECT 1 FROM #a WHERE SessionId = @s4 AND OldCapacity = 30 AND NewCapacity = 10 AND Delta = -20)
    THROW 50008, 'FAIL: C2 should list only its own change', 1;
PRINT 'PASS: C2 lists only its own change';

-- 4. paging (page size 2): 2 + 2 + 0 rows, TotalRows stays 4, order continues across pages
TRUNCATE TABLE #a;
INSERT #a (AuditId, LoggedAt, SessionId, ExamCode, StartsAt, RoomName, OldCapacity, NewCapacity, Delta, Actor, RawActor, Detail, CurrentCapacity, SeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = @From, @To = @To, @Page = 1, @PageSize = 2;
INSERT #a (AuditId, LoggedAt, SessionId, ExamCode, StartsAt, RoomName, OldCapacity, NewCapacity, Delta, Actor, RawActor, Detail, CurrentCapacity, SeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = @From, @To = @To, @Page = 2, @PageSize = 2;
DECLARE @afterPage2 INT = (SELECT COUNT(*) FROM #a);
INSERT #a (AuditId, LoggedAt, SessionId, ExamCode, StartsAt, RoomName, OldCapacity, NewCapacity, Delta, Actor, RawActor, Detail, CurrentCapacity, SeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = @From, @To = @To, @Page = 3, @PageSize = 2;
IF @afterPage2 <> 4 OR (SELECT COUNT(*) FROM #a) <> 4 OR EXISTS (SELECT 1 FROM #a WHERE TotalRows <> 4)
   OR (SELECT COUNT(DISTINCT AuditId) FROM #a) <> 4
   OR EXISTS (SELECT 1 FROM #a a JOIN #a b ON b.Seq = a.Seq + 1 WHERE b.LoggedAt > a.LoggedAt)
    THROW 50009, 'FAIL: paging (OFFSET/FETCH, TotalRows)', 1;
PRINT 'PASS: paging - page 1 and 2 hold 2 rows each in order, page 3 is empty, TotalRows = 4';

-- 5. date range: nothing outside @From/@To
TRUNCATE TABLE #a;
INSERT #a (AuditId, LoggedAt, SessionId, ExamCode, StartsAt, RoomName, OldCapacity, NewCapacity, Delta, Actor, RawActor, Detail, CurrentCapacity, SeatsAvailable, TotalRows)
EXEC dbo.usp_GetCapacityAudit @TestCenterId = @c1, @From = '2001-01-11', @To = '2001-01-12';
IF (SELECT COUNT(*) FROM #a) <> 1 OR NOT EXISTS (SELECT 1 FROM #a WHERE SessionId = @s2)
    THROW 50010, 'FAIL: @From inclusive / @To exclusive range', 1;
PRINT 'PASS: date range (@From inclusive, @To exclusive)';

-- 6. capacity-sync runs come from the separate proc, parsed, newest first
INSERT #sync (AuditId, LoggedAt, [Trigger], [Status], Checked, Updated, Reason, Detail, Actor)
EXEC dbo.usp_GetCapacitySyncRuns @From = @From, @To = @To;
IF (SELECT COUNT(*) FROM #sync) <> 2
   OR NOT EXISTS (SELECT 1 FROM #sync WHERE Seq = 1 AND [Trigger] = 'MANUAL' AND [Status] = 'FAILED' AND Checked = 0 AND Updated = 0 AND Reason = N'connection reset')
   OR NOT EXISTS (SELECT 1 FROM #sync WHERE Seq = 2 AND [Trigger] = 'SCHEDULED' AND [Status] = 'COMPLETED' AND Checked = 285 AND Updated = 3 AND Reason IS NULL
                                       AND Actor = 'capacity-sync' AND Detail = N'trigger=SCHEDULED status=COMPLETED checked=285 updated=3')
    THROW 50011, 'FAIL: usp_GetCapacitySyncRuns rows/parsing', 1;
PRINT 'PASS: usp_GetCapacitySyncRuns returns the 2 EntityId 0 CAPACITY_SYNC runs parsed (trigger/status/checked/updated/reason), newest first';

-- 7. reads only: every user table has the same row count and checksum as before the procs ran
EXEC sp_executesql @snapSql, N'@phase VARCHAR(10)', @phase = 'after';
IF EXISTS (SELECT TableName, Cnt, Chk FROM #snap WHERE Phase = 'before' EXCEPT SELECT TableName, Cnt, Chk FROM #snap WHERE Phase = 'after')
   OR EXISTS (SELECT TableName, Cnt, Chk FROM #snap WHERE Phase = 'after' EXCEPT SELECT TableName, Cnt, Chk FROM #snap WHERE Phase = 'before')
    THROW 50012, 'FAIL: a table changed while the capacity audit procs ran', 1;
SET @msg = CONCAT('PASS: reads only - row counts and CHECKSUM_AGG unchanged for all ', (SELECT COUNT(*) FROM #snap WHERE Phase = 'after'), ' dbo tables');
PRINT @msg;

ROLLBACK;
PRINT 'PASS: test_usp_GetCapacityAudit - all checks passed (rolled back)';
GO
