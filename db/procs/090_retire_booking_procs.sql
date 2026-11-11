-- 090_retire_booking_procs.sql
-- MBA-3824 (M5): scheduling-service is the only booking/capacity writer (docs/adr/0003-single-writer-outbox-relay.md),
-- the portal booking pages and booking-api write SQL are deleted, so the four legacy write procs go too.
-- Their source stays in git history (020_usp_BookSeat.sql .. 023_usp_RescheduleBooking.sql at tag legacy-baseline);
-- their behaviour is documented in docs/SYSTEM_OVERVIEW.md and docs/as-is/deep-dives/sql.md.
-- Idempotent: safe on a fresh apply (procs never created) and on an existing M4 database.
USE AssessmentDelivery;
GO
DROP PROCEDURE IF EXISTS dbo.usp_BookSeat;
DROP PROCEDURE IF EXISTS dbo.usp_CancelBooking;
DROP PROCEDURE IF EXISTS dbo.usp_ChangeCapacity;
DROP PROCEDURE IF EXISTS dbo.usp_RescheduleBooking;
GO
