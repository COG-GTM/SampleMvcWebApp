/*
    035 - read-only login for the scheduling-service snapshot importer (MBA-3821).  DEV ONLY.

    scheduling-service owns its own database (Scheduling).  It copies reference data
    (TestCenter, Room, Exam, Candidate, Result) and the bootstrap ExamSession / Booking /
    Waitlist rows out of AssessmentDelivery with this login.  db_datareader only: the
    login cannot write to AssessmentDelivery.

    Applied by db/apply.sh after the seed (since MBA-3823; apply.sh drops AssessmentDelivery,
    which drops the database user but not the server login).  To re-apply by hand:

      sqlcmd -S localhost,1433 -U sa -P "$AD_SA_PASSWORD" -C -b -i db/security/035_sched_import_login.sql

    The password below is a local development default; override it outside dev by
    re-creating the login and setting LEGACY_DB_PASSWORD for scheduling-service.
*/
USE master;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_sched_import')
    CREATE LOGIN ad_sched_import WITH PASSWORD = 'Sch3d!Imp0rt2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
GO
USE AssessmentDelivery;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_sched_import')
    CREATE USER ad_sched_import FOR LOGIN ad_sched_import;
GO
-- if the user exists from an earlier run with more rights, strip them back to read-only
IF IS_ROLEMEMBER('db_datawriter', 'ad_sched_import') = 1 ALTER ROLE db_datawriter DROP MEMBER ad_sched_import;
IF IS_ROLEMEMBER('db_owner', 'ad_sched_import') = 1 ALTER ROLE db_owner DROP MEMBER ad_sched_import;
IF IS_ROLEMEMBER('db_datareader', 'ad_sched_import') = 0 ALTER ROLE db_datareader ADD MEMBER ad_sched_import;
DENY INSERT, UPDATE, DELETE, EXECUTE ON SCHEMA::dbo TO ad_sched_import;
GO
PRINT 'ad_sched_import: db_datareader on AssessmentDelivery';
GO
