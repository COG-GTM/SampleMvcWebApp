-- 040_single_writer.sql
-- MBA-3823: scheduling-service is the single writer of bookings and seat counts. Every legacy login loses
-- direct write access to the contested data (docs/as-is/data-ownership.md; HB-01, HB-02, HB-03, HB-04,
-- HB-05, HB-29; MR-01, MR-02, MR-07, MR-17). Booking/capacity writes for all centres go through the routing
-- gateway (:8000) to scheduling-service; its outbox relay (ad_sched_relay) projects them back here.
-- Legacy read pages keep rendering from these tables. ad_sched_import stays read-only (035).
-- DENY beats the db_datawriter / GRANT EXECUTE membership from 036, so writes fail with error 229/230.
USE AssessmentDelivery;
GO
DENY INSERT, UPDATE, DELETE ON dbo.Booking  TO ad_portal;
DENY INSERT, UPDATE, DELETE ON dbo.Waitlist TO ad_portal;
DENY UPDATE ON dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity) TO ad_portal;
DENY EXECUTE ON dbo.usp_BookSeat          TO ad_portal;
DENY EXECUTE ON dbo.usp_CancelBooking     TO ad_portal;
DENY EXECUTE ON dbo.usp_ChangeCapacity    TO ad_portal;
DENY EXECUTE ON dbo.usp_RescheduleBooking TO ad_portal;
DENY INSERT, UPDATE, DELETE ON dbo.RelayCheckpoint TO ad_portal;
GO
DENY INSERT, UPDATE, DELETE ON dbo.Booking  TO ad_booking_api;
DENY INSERT, UPDATE, DELETE ON dbo.Waitlist TO ad_booking_api;
DENY UPDATE ON dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity) TO ad_booking_api;
DENY EXECUTE ON dbo.usp_BookSeat          TO ad_booking_api;
DENY EXECUTE ON dbo.usp_CancelBooking     TO ad_booking_api;
DENY EXECUTE ON dbo.usp_ChangeCapacity    TO ad_booking_api;
DENY EXECUTE ON dbo.usp_RescheduleBooking TO ad_booking_api;
DENY INSERT, UPDATE, DELETE ON dbo.RelayCheckpoint TO ad_booking_api;
GO
DENY INSERT, UPDATE, DELETE ON dbo.Booking  TO ad_results;
DENY INSERT, UPDATE, DELETE ON dbo.Waitlist TO ad_results;
DENY UPDATE ON dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity) TO ad_results;
DENY EXECUTE ON dbo.usp_BookSeat          TO ad_results;
DENY EXECUTE ON dbo.usp_CancelBooking     TO ad_results;
DENY EXECUTE ON dbo.usp_ChangeCapacity    TO ad_results;
DENY EXECUTE ON dbo.usp_RescheduleBooking TO ad_results;
DENY INSERT, UPDATE, DELETE ON dbo.RelayCheckpoint TO ad_results;
GO
DENY INSERT, UPDATE, DELETE ON dbo.Booking  TO ad_capacity_sync;
DENY INSERT, UPDATE, DELETE ON dbo.Waitlist TO ad_capacity_sync;
DENY UPDATE ON dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity) TO ad_capacity_sync;
DENY EXECUTE ON dbo.usp_BookSeat          TO ad_capacity_sync;
DENY EXECUTE ON dbo.usp_CancelBooking     TO ad_capacity_sync;
DENY EXECUTE ON dbo.usp_ChangeCapacity    TO ad_capacity_sync;
DENY EXECUTE ON dbo.usp_RescheduleBooking TO ad_capacity_sync;
DENY INSERT, UPDATE, DELETE ON dbo.RelayCheckpoint TO ad_capacity_sync;
GO
-- a leftover shared login from an older apply must not keep a side door open
IF EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ad_app')
    DROP USER ad_app;
GO
