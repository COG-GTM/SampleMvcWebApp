/*
    AssessmentDelivery - database + application login
    Run as sysadmin (sa) against the target instance.  Safe to re-run.

    NOTE: Azure SQL does not support CREATE DATABASE ... from here or server
    logins in master the same way; on Azure the database and contained user are
    created by the platform team (see ops wiki "AD Azure cutover 2016").
*/
IF DB_ID('AssessmentDelivery') IS NULL
    CREATE DATABASE AssessmentDelivery;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_app')
    CREATE LOGIN ad_app WITH PASSWORD = 'Northw1nd!Dev2011', CHECK_POLICY = OFF;
GO

USE AssessmentDelivery;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_app')
BEGIN
    CREATE USER ad_app FOR LOGIN ad_app;
    ALTER ROLE db_datareader ADD MEMBER ad_app;
    ALTER ROLE db_datawriter ADD MEMBER ad_app;
    GRANT EXECUTE TO ad_app;
END
GO
