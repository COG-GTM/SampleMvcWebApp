/*
    030 - capacity audit for test-centre managers (MBA-3828).  Read-only: SELECT only, no writes.

    usp_GetCapacityAudit @TestCenterId, @From, @To, @SessionId = NULL, @Page = 1, @PageSize = 50
        Per-session capacity changes at one centre: AuditLog rows with EntityName = 'ExamSession' and
        Action = 'CAPACITY' (EntityId > 0) joined to ExamSession / Exam / Room.  Newest first, paged.
        @From NULL = the last 30 days, @To NULL = no upper bound; @To is exclusive.
        Detail formats parsed into OldCapacity / NewCapacity (NULL when not parseable, Detail is returned raw):
          legacy portal proc (usp_ChangeCapacity, retired)  '<old> -> <new>'   (whole string)
          scheduling-service outbox relay                   '... change=<old> -> <new> ... by=<actor>'
        Actor is the 'by=' value when present (the user behind the relay), else AuditLog.Actor.

    usp_GetCapacitySyncRuns @From, @To
        capacity-sync runs (Action = 'CAPACITY_SYNC', EntityId 0, network-wide), newest first, at most 50.
        Detail 'trigger=X status=Y checked=N updated=M [reason=...]'.  These rows never join to a session.
*/
USE AssessmentDelivery;
GO
CREATE OR ALTER PROCEDURE dbo.usp_GetCapacityAudit
    @TestCenterId INT,
    @From         DATETIME = NULL,
    @To           DATETIME = NULL,
    @SessionId    INT = NULL,
    @Page         INT = 1,
    @PageSize     INT = 50
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Since DATETIME = ISNULL(@From, DATEADD(DAY, -30, GETDATE()));
    IF @Page IS NULL OR @Page < 1 SET @Page = 1;
    IF @PageSize IS NULL OR @PageSize < 1 SET @PageSize = 50;

    SELECT a.AuditId,
           a.LoggedAt,
           s.SessionId,
           e.ExamCode,
           s.StartsAt,
           r.RoomName,
           v.OldCapacity,
           v.NewCapacity,
           v.NewCapacity - v.OldCapacity                    AS Delta,
           CAST(COALESCE(NULLIF(b.ByActor, ''), a.Actor) AS VARCHAR(60)) AS Actor,
           a.Actor                                          AS RawActor,
           a.Detail,
           s.Capacity                                       AS CurrentCapacity,
           s.SeatsAvailable,
           COUNT(*) OVER ()                                 AS TotalRows
    FROM dbo.AuditLog a
    JOIN dbo.ExamSession s ON s.SessionId = a.EntityId
    JOIN dbo.Exam e        ON e.ExamId = s.ExamId
    JOIN dbo.Room r        ON r.RoomId = s.RoomId
    -- d.Body: the text holding '<old> -> <new>'; for relay rows everything after 'change='
    CROSS APPLY (SELECT REPLACE(ISNULL(a.Detail, N''), CHAR(9), N' ') AS Txt) t
    CROSS APPLY (SELECT CHARINDEX(N' change=', N' ' + t.Txt) AS ChangeAt) c
    CROSS APPLY (SELECT CASE WHEN c.ChangeAt > 0 THEN SUBSTRING(t.Txt, c.ChangeAt + 7, 1000) ELSE t.Txt END AS Body) d
    CROSS APPLY (SELECT CHARINDEX(N'->', d.Body) AS ArrowAt) ar
    CROSS APPLY (SELECT CASE WHEN ar.ArrowAt > 0 THEN LTRIM(RTRIM(LEFT(d.Body, ar.ArrowAt - 1))) END AS OldTxt,
                        CASE WHEN ar.ArrowAt > 0 THEN LTRIM(SUBSTRING(d.Body, ar.ArrowAt + 2, 1000)) END AS Rest) p
    CROSS APPLY (SELECT CASE WHEN c.ChangeAt > 0 THEN LEFT(p.Rest, CHARINDEX(N' ', p.Rest + N' ') - 1)
                             ELSE RTRIM(p.Rest) END AS NewTxt) n
    CROSS APPLY (SELECT CASE WHEN (p.OldTxt LIKE N'[0-9]%' OR p.OldTxt LIKE N'-[0-9]%') AND SUBSTRING(p.OldTxt, 2, 20) NOT LIKE N'%[^0-9]%'
                              AND (n.NewTxt LIKE N'[0-9]%' OR n.NewTxt LIKE N'-[0-9]%') AND SUBSTRING(n.NewTxt, 2, 20) NOT LIKE N'%[^0-9]%'
                             THEN 1 ELSE 0 END AS Ok) ok
    CROSS APPLY (SELECT CASE WHEN ok.Ok = 1 THEN TRY_CAST(p.OldTxt AS INT) END AS OldCapacity,
                        CASE WHEN ok.Ok = 1 THEN TRY_CAST(n.NewTxt AS INT) END AS NewCapacity) v0
    CROSS APPLY (SELECT CASE WHEN v0.OldCapacity IS NOT NULL AND v0.NewCapacity IS NOT NULL THEN v0.OldCapacity END AS OldCapacity,
                        CASE WHEN v0.OldCapacity IS NOT NULL AND v0.NewCapacity IS NOT NULL THEN v0.NewCapacity END AS NewCapacity) v
    CROSS APPLY (SELECT CHARINDEX(N' by=', N' ' + t.Txt) AS ByAt) bya
    -- by= is the relay's last token and the actor may contain spaces: take the rest of the Detail
    CROSS APPLY (SELECT CASE WHEN bya.ByAt > 0
                             THEN NULLIF(LTRIM(RTRIM(SUBSTRING(t.Txt, bya.ByAt + 3, 1000))), N'') END AS ByActor) b
    WHERE a.EntityName = 'ExamSession'
      AND a.Action = 'CAPACITY'
      AND a.EntityId > 0
      AND s.TestCenterId = @TestCenterId
      AND a.LoggedAt >= @Since
      AND (@To IS NULL OR a.LoggedAt < @To)
      AND (@SessionId IS NULL OR a.EntityId = @SessionId)
    ORDER BY a.LoggedAt DESC, a.AuditId DESC
    OFFSET (@Page - 1) * @PageSize ROWS FETCH NEXT @PageSize ROWS ONLY;
END
GO

CREATE OR ALTER PROCEDURE dbo.usp_GetCapacitySyncRuns
    @From DATETIME = NULL,
    @To   DATETIME = NULL
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Since DATETIME = ISNULL(@From, DATEADD(DAY, -30, GETDATE()));

    SELECT TOP (50)
           a.AuditId,
           a.LoggedAt,
           CAST(tr.Val AS VARCHAR(40))            AS [Trigger],
           CAST(st.Val AS VARCHAR(40))            AS [Status],
           CASE WHEN ch.Val NOT LIKE N'%[^0-9]%' THEN TRY_CAST(ch.Val AS INT) END AS Checked,
           CASE WHEN up.Val NOT LIKE N'%[^0-9]%' THEN TRY_CAST(up.Val AS INT) END AS Updated,
           CASE WHEN rs.At > 0 THEN SUBSTRING(t.Txt, rs.At + 7, 1000) END AS Reason,
           a.Detail,
           a.Actor
    FROM dbo.AuditLog a
    CROSS APPLY (SELECT ISNULL(a.Detail, N'') AS Txt) t
    CROSS APPLY (SELECT CHARINDEX(N' reason=', N' ' + t.Txt) AS At) rs
    -- the key=value part before ' reason=' (the reason itself may contain spaces and '=')
    CROSS APPLY (SELECT N' ' + CASE WHEN rs.At > 0 THEN LEFT(t.Txt, rs.At - 1) ELSE t.Txt END + N' ' AS Kv) k
    CROSS APPLY (SELECT CHARINDEX(N' trigger=', k.Kv) AS At) trp
    CROSS APPLY (SELECT CASE WHEN trp.At > 0 THEN SUBSTRING(k.Kv, trp.At + 9, CHARINDEX(N' ', k.Kv, trp.At + 9) - (trp.At + 9)) END AS Val) tr
    CROSS APPLY (SELECT CHARINDEX(N' status=', k.Kv) AS At) stp
    CROSS APPLY (SELECT CASE WHEN stp.At > 0 THEN SUBSTRING(k.Kv, stp.At + 8, CHARINDEX(N' ', k.Kv, stp.At + 8) - (stp.At + 8)) END AS Val) st
    CROSS APPLY (SELECT CHARINDEX(N' checked=', k.Kv) AS At) chp
    CROSS APPLY (SELECT CASE WHEN chp.At > 0 THEN SUBSTRING(k.Kv, chp.At + 9, CHARINDEX(N' ', k.Kv, chp.At + 9) - (chp.At + 9)) END AS Val) ch
    CROSS APPLY (SELECT CHARINDEX(N' updated=', k.Kv) AS At) upp
    CROSS APPLY (SELECT CASE WHEN upp.At > 0 THEN SUBSTRING(k.Kv, upp.At + 9, CHARINDEX(N' ', k.Kv, upp.At + 9) - (upp.At + 9)) END AS Val) up
    WHERE a.EntityName = 'ExamSession'
      AND a.Action = 'CAPACITY_SYNC'
      AND a.EntityId = 0
      AND a.LoggedAt >= @Since
      AND (@To IS NULL OR a.LoggedAt < @To)
    ORDER BY a.LoggedAt DESC, a.AuditId DESC;
END
GO
