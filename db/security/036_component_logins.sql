-- 036_component_logins.sql
-- MBA-3823: one login per component instead of the shared ad_app (docs/as-is HB-28, MR-07).
-- Legacy logins keep the broad role membership ad_app had (reader/writer/EXECUTE) so read pages and
-- non-booking tables (Result, ResultUpload, AuditLog, Candidate...) behave as before; 040_single_writer.sql
-- then DENYs the booking/seat writes. ad_sched_relay is the only writer of Booking/Waitlist/seat columns.
-- Local-development passwords only (same convention as 035); Azure uses contained users + secrets.
-- Applied after the seed by db/apply.sh / db/apply.ps1. Server logins survive the DB rebuild, so idempotent.
USE master;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_portal')
    CREATE LOGIN ad_portal WITH PASSWORD = 'P0rtal!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_booking_api')
    CREATE LOGIN ad_booking_api WITH PASSWORD = 'B00kingApi!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_results')
    CREATE LOGIN ad_results WITH PASSWORD = 'Resu1ts!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_capacity_sync')
    CREATE LOGIN ad_capacity_sync WITH PASSWORD = 'CapSync!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
GO
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ad_sched_relay')
    CREATE LOGIN ad_sched_relay WITH PASSWORD = 'Sch3dRelay!Dev2026', CHECK_POLICY = OFF, DEFAULT_DATABASE = AssessmentDelivery;
GO
USE AssessmentDelivery;
GO
-- portal (AssessmentDelivery.Web, EF6)
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_portal')
    CREATE USER ad_portal FOR LOGIN ad_portal;
ALTER ROLE db_datareader ADD MEMBER ad_portal;
ALTER ROLE db_datawriter ADD MEMBER ad_portal;
GRANT EXECUTE TO ad_portal;
GO
-- booking-api (Java 8, JdbcTemplate)
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_booking_api')
    CREATE USER ad_booking_api FOR LOGIN ad_booking_api;
ALTER ROLE db_datareader ADD MEMBER ad_booking_api;
ALTER ROLE db_datawriter ADD MEMBER ad_booking_api;
GRANT EXECUTE TO ad_booking_api;
GO
-- results-service (Java 8)
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_results')
    CREATE USER ad_results FOR LOGIN ad_results;
ALTER ROLE db_datareader ADD MEMBER ad_results;
ALTER ROLE db_datawriter ADD MEMBER ad_results;
GRANT EXECUTE TO ad_results;
GO
-- capacity-sync (Java 8)
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_capacity_sync')
    CREATE USER ad_capacity_sync FOR LOGIN ad_capacity_sync;
ALTER ROLE db_datareader ADD MEMBER ad_capacity_sync;
ALTER ROLE db_datawriter ADD MEMBER ad_capacity_sync;
GRANT EXECUTE TO ad_capacity_sync;
GO
-- scheduling-service outbox relay: named-table rights only (no roles, no EXECUTE)
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_sched_relay')
    CREATE USER ad_sched_relay FOR LOGIN ad_sched_relay;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.Booking         TO ad_sched_relay;
GRANT SELECT, INSERT, UPDATE, DELETE ON dbo.Waitlist        TO ad_sched_relay;
GRANT SELECT, UPDATE                 ON dbo.ExamSession     TO ad_sched_relay;
GRANT SELECT, INSERT                 ON dbo.AuditLog        TO ad_sched_relay;
GRANT SELECT, INSERT                 ON dbo.RelayCheckpoint TO ad_sched_relay;
GRANT SELECT                         ON dbo.Candidate       TO ad_sched_relay;
-- SET IDENTITY_INSERT dbo.Booking ON: relayed bookings keep the BookingId scheduling-service assigned
-- (>= routing.modern-booking-id-floor), so v1 URLs and Result.BookingId resolve the same row on both sides.
GRANT ALTER ON dbo.Booking TO ad_sched_relay;
GO
-- reconciliation reads RelayCheckpoint through the read-only importer login
GRANT SELECT ON dbo.RelayCheckpoint TO ad_sched_import;
GO
