-- Result.Passed is computed by the writer at ingest time; changing Exam.PassingScore is audited but existing
-- Result rows keep the old verdict (no trigger/constraint ties Passed to Score). Rolled back.
USE AssessmentDelivery;
SET NOCOUNT ON;
BEGIN TRAN;
DECLARE @e INT = (SELECT TOP 1 ExamId FROM dbo.Result GROUP BY ExamId ORDER BY COUNT(*) DESC);
DECLARE @pm INT = (SELECT PassingScore FROM dbo.Exam WHERE ExamId=@e);
SELECT @e AS ExamId, @pm AS PassingScore;
SELECT COUNT(*) AS Results, SUM(CASE WHEN Passed=1 THEN 1 ELSE 0 END) AS PassedFlag,
       SUM(CASE WHEN Score >= @pm THEN 1 ELSE 0 END) AS ScoreMeetsCurrentPassMark
  FROM dbo.Result WHERE ExamId=@e;
UPDATE dbo.Exam SET PassingScore = PassingScore + 10 WHERE ExamId=@e;
SET @pm = @pm + 10;
SELECT COUNT(*) AS Results, SUM(CASE WHEN Passed=1 THEN 1 ELSE 0 END) AS PassedFlag,
       SUM(CASE WHEN Score >= @pm THEN 1 ELSE 0 END) AS ScoreMeetsNewPassMark
  FROM dbo.Result WHERE ExamId=@e;
SELECT TOP 1 Action, Detail FROM dbo.AuditLog WHERE EntityName='Exam' AND EntityId=@e ORDER BY AuditId DESC;
ROLLBACK;
