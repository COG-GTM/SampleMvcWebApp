-- 035_sched_import_login.sql
-- Read-only login for the scheduling-service snapshot importer (MBA-3821).
-- The importer copies reference data (and the one-off session/booking seed) out of AssessmentDelivery
-- into the Scheduling database. It must never write here, so the user gets SELECT on named tables only:
-- no db_datareader/db_datawriter role, no EXECUTE, no access to AuditLog/ResultUpload.
-- Applied by db/apply.sh and db/apply.ps1 after the seed (the DB is re-created on every apply, the
-- server login survives, so both halves are idempotent).
USE master;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_sched_import')
    CREATE LOGIN ad_sched_import WITH PASSWORD = 'Sch3dImport!Dev2026', CHECK_POLICY = OFF,
        DEFAULT_DATABASE = AssessmentDelivery;
GO
USE AssessmentDelivery;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_sched_import')
    CREATE USER ad_sched_import FOR LOGIN ad_sched_import;
GO
-- reference snapshots
GRANT SELECT ON dbo.TestCenter  TO ad_sched_import;
GRANT SELECT ON dbo.Room        TO ad_sched_import;
GRANT SELECT ON dbo.Exam        TO ad_sched_import;
GRANT SELECT ON dbo.Candidate   TO ad_sched_import;
-- result history for retake eligibility (exam_result_snapshot)
GRANT SELECT ON dbo.Result      TO ad_sched_import;
-- initial seat-inventory import (exam_session / booking / waitlist_entry)
GRANT SELECT ON dbo.ExamSession TO ad_sched_import;
GRANT SELECT ON dbo.Booking     TO ad_sched_import;
GRANT SELECT ON dbo.Waitlist    TO ad_sched_import;
GO
