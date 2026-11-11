/*
    usp_GetCapacityAudit - read-only capacity change history for one test centre (portal
    /Admin/Centers/{id}/CapacityAudit, MBA-3828).
    Lists the AuditLog rows written by usp_ChangeCapacity (EntityName 'ExamSession',
    Action 'CAPACITY', Detail '<old> -> <new>') for sessions at @TestCenterId, newest first.
    capacity-sync run rows (Action 'CAPACITY_SYNC', EntityId 0) are excluded by the Action filter.
    @From inclusive, @To exclusive. Detail that is not exactly '<int> -> <int>' comes back with
    NULL OldCapacity / NewCapacity / Delta and the raw Detail (same rule as
    AssessmentDelivery.Business.Rules.CapacityAuditDetail - keep the two in step).
    TotalRows = matching rows before paging. Reads only.
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.usp_GetCapacityAudit') IS NOT NULL DROP PROCEDURE dbo.usp_GetCapacityAudit;
GO
CREATE PROCEDURE dbo.usp_GetCapacityAudit
    @TestCenterId INT,
    @From         DATETIME,
    @To           DATETIME,
    @SessionId    INT = NULL,
    @PageNumber   INT = 1,
    @PageSize     INT = 50
AS
BEGIN
    SET NOCOUNT ON;

    IF @PageNumber IS NULL OR @PageNumber < 1 SET @PageNumber = 1;
    IF @PageSize IS NULL OR @PageSize < 1 SET @PageSize = 50;

    WITH audit AS (
        SELECT al.AuditId, al.LoggedAt, al.Actor, al.Detail,
               s.SessionId, e.ExamCode, s.StartsAt AS SessionStartsAt, r.RoomName,
               s.Capacity AS CurrentCapacity, s.SeatsAvailable AS CurrentSeatsAvailable,
               CHARINDEX(N' -> ', al.Detail) AS Arrow
          FROM dbo.AuditLog al
          JOIN dbo.ExamSession s ON s.SessionId = al.EntityId
          JOIN dbo.Exam e        ON e.ExamId = s.ExamId
          JOIN dbo.Room r        ON r.RoomId = s.RoomId
         WHERE al.EntityName = 'ExamSession'
           AND al.Action = 'CAPACITY'
           AND s.TestCenterId = @TestCenterId
           AND al.LoggedAt >= @From
           AND al.LoggedAt <  @To
           AND (@SessionId IS NULL OR al.EntityId = @SessionId)
    ), split AS (
        SELECT a.*,
               CASE WHEN a.Arrow > 1 THEN LEFT(a.Detail, a.Arrow - 1) END        AS OldText,
               CASE WHEN a.Arrow > 1 THEN SUBSTRING(a.Detail, a.Arrow + 4, 1000) END AS NewText
          FROM audit a
    ), parsed AS (
        SELECT sp.*,
               CASE WHEN ((sp.OldText LIKE N'[0-9]%'  AND DATALENGTH(sp.OldText) / 2 <= 10)
                       OR (sp.OldText LIKE N'-[0-9]%' AND DATALENGTH(sp.OldText) / 2 <= 11))
                     AND SUBSTRING(sp.OldText, 2, 20) + N'x' NOT LIKE N'%[^0-9]%x'
                    THEN TRY_CAST(sp.OldText AS INT) END AS OldValue,
               CASE WHEN ((sp.NewText LIKE N'[0-9]%'  AND DATALENGTH(sp.NewText) / 2 <= 10)
                       OR (sp.NewText LIKE N'-[0-9]%' AND DATALENGTH(sp.NewText) / 2 <= 11))
                     AND SUBSTRING(sp.NewText, 2, 20) + N'x' NOT LIKE N'%[^0-9]%x'
                    THEN TRY_CAST(sp.NewText AS INT) END AS NewValue
          FROM split sp
    )
    SELECT p.AuditId,
           p.LoggedAt,
           p.Actor,
           p.Detail,
           p.SessionId,
           p.ExamCode,
           p.SessionStartsAt,
           p.RoomName,
           CASE WHEN p.OldValue IS NOT NULL AND p.NewValue IS NOT NULL THEN p.OldValue END AS OldCapacity,
           CASE WHEN p.OldValue IS NOT NULL AND p.NewValue IS NOT NULL THEN p.NewValue END AS NewCapacity,
           CASE WHEN p.OldValue IS NOT NULL AND p.NewValue IS NOT NULL
                THEN CAST(p.NewValue AS BIGINT) - p.OldValue END                         AS Delta,
           p.CurrentCapacity,
           p.CurrentSeatsAvailable,
           COUNT(*) OVER ()                                                               AS TotalRows
      FROM parsed p
     ORDER BY p.LoggedAt DESC, p.AuditId DESC
    OFFSET (@PageNumber - 1) * @PageSize ROWS
     FETCH NEXT @PageSize ROWS ONLY;
END
GO
