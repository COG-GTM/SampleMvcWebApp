/*
    090 - retire the legacy booking procs (MBA-3824, M5).

    usp_BookSeat, usp_CancelBooking, usp_ChangeCapacity and usp_RescheduleBooking were the .NET portal's
    booking/capacity write path.  Since M4 scheduling-service is the only writer and the relay projects its
    changes into AssessmentDelivery; the portal booking pages are gone and their URLs are served by the modern
    UI through the routing gateway.  The procs' source stays in git history (db/procs/020-023 before MBA-3824).

    Drops them from databases built before this change; a no-op on a fresh build.  Idempotent.
*/
USE AssessmentDelivery;
GO
DROP PROCEDURE IF EXISTS dbo.usp_BookSeat;
DROP PROCEDURE IF EXISTS dbo.usp_CancelBooking;
DROP PROCEDURE IF EXISTS dbo.usp_ChangeCapacity;
DROP PROCEDURE IF EXISTS dbo.usp_RescheduleBooking;
GO
