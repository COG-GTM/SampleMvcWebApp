/*
    Seed 046 - backfill dbo.Result.SessionStartsAt (schema 016) for the seeded results.
*/
USE AssessmentDelivery;
GO

UPDATE r SET SessionStartsAt = s.StartsAt
FROM dbo.Result r JOIN dbo.ExamSession s ON s.SessionId = r.SessionId
WHERE r.SessionStartsAt IS NULL;
GO
