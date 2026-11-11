/*
    scheduling-service database bootstrap. Run as sysadmin (sa). Safe to re-run.
      - creates the Scheduling database (schema is then managed by Flyway on service start)
      - grants the estate login ad_app ownership of it (Phase 1 keeps the shared login, HB-17 audit actor)
      - enables SQL Server Change Tracking on the four reference tables in AssessmentDelivery for the
        reference-data relay (contract section 5, open decision 3) and lets ad_app read the change tables
    db/apply.sh drops and re-creates AssessmentDelivery, so re-run this after it.
*/
IF DB_ID('Scheduling') IS NULL
    CREATE DATABASE Scheduling;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_app')
    CREATE LOGIN ad_app WITH PASSWORD = 'Northw1nd!Dev2011', CHECK_POLICY = OFF;
GO
USE Scheduling;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_app')
    CREATE USER ad_app FOR LOGIN ad_app;
ALTER ROLE db_owner ADD MEMBER ad_app;
GO
USE AssessmentDelivery;
GO
IF NOT EXISTS (SELECT 1 FROM sys.change_tracking_databases WHERE database_id = DB_ID('AssessmentDelivery'))
    ALTER DATABASE AssessmentDelivery SET CHANGE_TRACKING = ON (CHANGE_RETENTION = 7 DAYS, AUTO_CLEANUP = ON);
GO
IF NOT EXISTS (SELECT 1 FROM sys.change_tracking_tables WHERE object_id = OBJECT_ID('dbo.Exam'))
    ALTER TABLE dbo.Exam ENABLE CHANGE_TRACKING;
IF NOT EXISTS (SELECT 1 FROM sys.change_tracking_tables WHERE object_id = OBJECT_ID('dbo.TestCenter'))
    ALTER TABLE dbo.TestCenter ENABLE CHANGE_TRACKING;
IF NOT EXISTS (SELECT 1 FROM sys.change_tracking_tables WHERE object_id = OBJECT_ID('dbo.Room'))
    ALTER TABLE dbo.Room ENABLE CHANGE_TRACKING;
IF NOT EXISTS (SELECT 1 FROM sys.change_tracking_tables WHERE object_id = OBJECT_ID('dbo.Candidate'))
    ALTER TABLE dbo.Candidate ENABLE CHANGE_TRACKING;
GO
GRANT VIEW CHANGE TRACKING ON dbo.Exam TO ad_app;
GRANT VIEW CHANGE TRACKING ON dbo.TestCenter TO ad_app;
GRANT VIEW CHANGE TRACKING ON dbo.Room TO ad_app;
GRANT VIEW CHANGE TRACKING ON dbo.Candidate TO ad_app;
GO
