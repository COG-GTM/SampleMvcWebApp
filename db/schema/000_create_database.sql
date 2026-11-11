/*
    000 - database + per-component dev logins (DEV ONLY).

    MBA-3823 (M4) replaced the shared ad_app login with one login per component so that the
    database can enforce a single writer for bookings and seat counts (db/security/040_single_writer.sql):

      ad_portal          ASP.NET portal (AssessmentDelivery.Web)
      ad_booking_api     services/booking-api
      ad_results         services/results-service
      ad_capacity_sync   services/capacity-sync
      ad_sched_import    scheduling-service snapshot importer, read-only (db/security/035_sched_import_login.sql)
      ad_sched_relay     scheduling-service outbox relay, the only legacy writer of Booking/Waitlist/seat columns
                         (grants in db/security/040_single_writer.sql)

    The passwords below are local development defaults (the docker SQL Server in README.md).
    Outside dev the logins are created by ops and the configs carry placeholders only.
*/
IF DB_ID('AssessmentDelivery') IS NULL
    CREATE DATABASE AssessmentDelivery;
GO

-- the shared login is gone: drop it if an older checkout created it
IF EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_app')
    DROP LOGIN ad_app;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_portal')
    CREATE LOGIN ad_portal WITH PASSWORD = 'P0rtal!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_booking_api')
    CREATE LOGIN ad_booking_api WITH PASSWORD = 'B00king!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_results')
    CREATE LOGIN ad_results WITH PASSWORD = 'Resu1ts!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_capacity_sync')
    CREATE LOGIN ad_capacity_sync WITH PASSWORD = 'Capac1ty!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_sched_relay')
    CREATE LOGIN ad_sched_relay WITH PASSWORD = 'Sch3d!Re1ay2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
GO

USE AssessmentDelivery;
GO

-- legacy components keep the rights ad_app had (read, write, execute); 040_single_writer.sql then
-- DENYs booking/waitlist/seat writes and the booking procedures to every one of them.
DECLARE @logins TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @logins VALUES ('ad_portal'), ('ad_booking_api'), ('ad_results'), ('ad_capacity_sync');
DECLARE @name SYSNAME, @sql NVARCHAR(MAX);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM @logins;
OPEN c;
FETCH NEXT FROM c INTO @name;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'';
    IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = @name)
        SET @sql += N'CREATE USER ' + QUOTENAME(@name) + N' FOR LOGIN ' + QUOTENAME(@name) + N';';
    SET @sql += N'ALTER ROLE db_datareader ADD MEMBER ' + QUOTENAME(@name) + N';'
              + N'ALTER ROLE db_datawriter ADD MEMBER ' + QUOTENAME(@name) + N';'
              + N'GRANT EXECUTE TO ' + QUOTENAME(@name) + N';';
    EXEC sp_executesql @sql;
    FETCH NEXT FROM c INTO @name;
END
CLOSE c; DEALLOCATE c;
GO

-- the relay gets read access here and its narrow write grants in 040_single_writer.sql
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_sched_relay')
    CREATE USER ad_sched_relay FOR LOGIN ad_sched_relay;
ALTER ROLE db_datareader ADD MEMBER ad_sched_relay;
GO
