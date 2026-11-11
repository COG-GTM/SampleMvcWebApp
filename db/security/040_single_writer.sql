/*
    040 - single writer for bookings and seat counts (MBA-3823, M4).

    scheduling-service is the only writer of bookings, waitlist entries and seat counts.  Its changes reach
    AssessmentDelivery only through the outbox relay, which connects as ad_sched_relay.  Every other login
    is DENIED (DENY beats any role grant, including db_datawriter and GRANT EXECUTE):

      - INSERT / UPDATE / DELETE on dbo.Booking and dbo.Waitlist
      - UPDATE of dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity)
      - EXECUTE on usp_BookSeat, usp_CancelBooking, usp_ChangeCapacity, usp_RescheduleBooking

    Legacy read pages keep reading these tables.  Writes for all centers go through the routing layer to
    scheduling-service.  Applied by db/apply.sh after 035 (idempotent; re-runnable).
*/
USE AssessmentDelivery;
GO
SET NOCOUNT ON;
DECLARE @legacy TABLE (name SYSNAME PRIMARY KEY);
INSERT INTO @legacy VALUES ('ad_portal'), ('ad_booking_api'), ('ad_results'), ('ad_capacity_sync'), ('ad_sched_import');

DECLARE @name SYSNAME, @sql NVARCHAR(MAX), @u NVARCHAR(300);
DECLARE c CURSOR LOCAL FAST_FORWARD FOR
    SELECT l.name FROM @legacy l WHERE EXISTS (SELECT 1 FROM sys.database_principals p WHERE p.name = l.name);
OPEN c;
FETCH NEXT FROM c INTO @name;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @u = QUOTENAME(@name);
    SET @sql = N'DENY INSERT, UPDATE, DELETE ON dbo.Booking TO ' + @u + N';'
             + N'DENY INSERT, UPDATE, DELETE ON dbo.Waitlist TO ' + @u + N';'
             + N'DENY INSERT, DELETE ON dbo.ExamSession TO ' + @u + N';'
             + N'DENY UPDATE ON dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity) TO ' + @u + N';'
             + N'DENY EXECUTE ON dbo.usp_BookSeat TO ' + @u + N';'
             + N'DENY EXECUTE ON dbo.usp_CancelBooking TO ' + @u + N';'
             + N'DENY EXECUTE ON dbo.usp_ChangeCapacity TO ' + @u + N';'
             + N'DENY EXECUTE ON dbo.usp_RescheduleBooking TO ' + @u + N';'
             + N'DENY INSERT, UPDATE, DELETE ON dbo.RelayCheckpoint TO ' + @u + N';'
             + N'DENY INSERT, UPDATE, DELETE ON dbo.RelayBookingMap TO ' + @u + N';';
    EXEC sp_executesql @sql;
    PRINT CONCAT(@name, ': booking/waitlist/seat writes denied');
    FETCH NEXT FROM c INTO @name;
END
CLOSE c; DEALLOCATE c;
GO

-- the relay: read everything, write exactly the tables it projects into, nothing else
IF IS_ROLEMEMBER('db_datawriter', 'ad_sched_relay') = 1 ALTER ROLE db_datawriter DROP MEMBER ad_sched_relay;
IF IS_ROLEMEMBER('db_owner', 'ad_sched_relay') = 1 ALTER ROLE db_owner DROP MEMBER ad_sched_relay;
IF IS_ROLEMEMBER('db_datareader', 'ad_sched_relay') = 0 ALTER ROLE db_datareader ADD MEMBER ad_sched_relay;
GRANT INSERT, UPDATE ON dbo.Booking TO ad_sched_relay;
GRANT INSERT, UPDATE, DELETE ON dbo.Waitlist TO ad_sched_relay;
GRANT UPDATE ON dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity, LastModified) TO ad_sched_relay;
GRANT INSERT ON dbo.AuditLog TO ad_sched_relay;
GRANT INSERT ON dbo.RelayCheckpoint TO ad_sched_relay;
GRANT INSERT ON dbo.RelayBookingMap TO ad_sched_relay;
GO
PRINT 'ad_sched_relay: projection writes on Booking, Waitlist, ExamSession seats, AuditLog, RelayCheckpoint, RelayBookingMap';
GO
