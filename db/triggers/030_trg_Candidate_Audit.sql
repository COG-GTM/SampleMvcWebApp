/*
    trg_Candidate_Audit - write candidate profile changes to AuditLog.
    Added after the 2012 data-protection review: support staff were editing
    names/emails from the admin screen with no trail.
    2012-06  initial
    2015-02  DefaultAccommodationCode added to the tracked columns
    2018-05  stop copying old/new name and email values into Detail
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.trg_Candidate_Audit') IS NOT NULL DROP TRIGGER dbo.trg_Candidate_Audit;
GO
CREATE TRIGGER dbo.trg_Candidate_Audit ON dbo.Candidate
AFTER UPDATE, DELETE
AS
BEGIN
    SET NOCOUNT ON;

    -- updates
    INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail)
    SELECT 'Candidate', i.CandidateId, 'UPDATE',
           LEFT(
             CASE WHEN ISNULL(d.FirstName,'') <> ISNULL(i.FirstName,'') OR ISNULL(d.LastName,'') <> ISNULL(i.LastName,'')
                  THEN N'name changed; '
                  ELSE N'' END
           + CASE WHEN ISNULL(d.Email,'') <> ISNULL(i.Email,'')
                  THEN N'email changed; '
                  ELSE N'' END
           + CASE WHEN ISNULL(d.DefaultAccommodationCode,'') <> ISNULL(i.DefaultAccommodationCode,'')
                  THEN N'accommodation: ' + ISNULL(d.DefaultAccommodationCode,'(none)') + N' -> ' + ISNULL(i.DefaultAccommodationCode,'(none)') + N'; '
                  ELSE N'' END, 1000)
    FROM inserted i
    JOIN deleted d ON d.CandidateId = i.CandidateId
    WHERE ISNULL(d.FirstName,'') <> ISNULL(i.FirstName,'')
       OR ISNULL(d.LastName,'') <> ISNULL(i.LastName,'')
       OR ISNULL(d.Email,'') <> ISNULL(i.Email,'')
       OR ISNULL(d.DefaultAccommodationCode,'') <> ISNULL(i.DefaultAccommodationCode,'');

    -- deletes (should not happen - candidates are never deleted, only anonymised)
    INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail)
    SELECT 'Candidate', d.CandidateId, 'DELETE', N'number=' + d.CandidateNumber
    FROM deleted d
    WHERE NOT EXISTS (SELECT 1 FROM inserted i WHERE i.CandidateId = d.CandidateId);
END
GO
