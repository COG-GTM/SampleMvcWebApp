/*
    dbo.Result.SessionStartsAt - results-local copy of the session start time (inventory RS-05).
    Booking and ExamSession move to scheduling-service, so results-service records the start time it
    gets from GET /bookings/{id} at ingest instead of joining ExamSession for the candidate history.
    Additive and safe to re-run on an existing database; backfills existing rows while ExamSession is
    still in this database (seed 046 does the same after a fresh seed).
*/
USE AssessmentDelivery;
GO

IF COL_LENGTH('dbo.Result', 'SessionStartsAt') IS NULL
    ALTER TABLE dbo.Result ADD SessionStartsAt DATETIME NULL;
GO

UPDATE r SET SessionStartsAt = s.StartsAt
FROM dbo.Result r JOIN dbo.ExamSession s ON s.SessionId = r.SessionId
WHERE r.SessionStartsAt IS NULL;
GO
