/*
    trg_Exam_Audit - exam catalogue changes go to AuditLog.
    Exam owners asked for this in 2013 after a passing score was changed
    mid-window without anyone noticing.
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.trg_Exam_Audit') IS NOT NULL DROP TRIGGER dbo.trg_Exam_Audit;
GO
CREATE TRIGGER dbo.trg_Exam_Audit ON dbo.Exam
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail)
    SELECT 'Exam', i.ExamId,
           CASE WHEN d.ExamId IS NULL THEN 'INSERT' ELSE 'UPDATE' END,
           CASE WHEN d.ExamId IS NULL
                THEN N'code=' + i.ExamCode + N' owner=' + i.OwnerName
                     + N' pass=' + CAST(i.PassingScore AS NVARCHAR(10))
                ELSE N'code=' + i.ExamCode
                     + CASE WHEN d.PassingScore <> i.PassingScore
                            THEN N' pass ' + CAST(d.PassingScore AS NVARCHAR(10)) + N'->' + CAST(i.PassingScore AS NVARCHAR(10)) ELSE N'' END
                     + CASE WHEN d.RetakeWaitDays <> i.RetakeWaitDays
                            THEN N' retake ' + CAST(d.RetakeWaitDays AS NVARCHAR(10)) + N'->' + CAST(i.RetakeWaitDays AS NVARCHAR(10)) ELSE N'' END
                     + CASE WHEN d.IsPublished <> i.IsPublished
                            THEN N' published ' + CAST(d.IsPublished AS NVARCHAR(1)) + N'->' + CAST(i.IsPublished AS NVARCHAR(1)) ELSE N'' END
                     + CASE WHEN d.Title <> i.Title THEN N' title changed' ELSE N'' END
           END
    FROM inserted i
    LEFT JOIN deleted d ON d.ExamId = i.ExamId;
END
GO
