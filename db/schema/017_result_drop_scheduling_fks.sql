/*
    Booking and ExamSession move to scheduling-service's own database (no cross-DB FKs, contract section 5),
    so bookings created after cutover exist only there. results-service validates the booking through
    GET /bookings/{id} before inserting, which replaces these two FKs (inventory RS-01).
    Safe to re-run.
*/
USE AssessmentDelivery;
GO

IF OBJECT_ID('dbo.FK_Result_Booking', 'F') IS NOT NULL
    ALTER TABLE dbo.Result DROP CONSTRAINT FK_Result_Booking;
IF OBJECT_ID('dbo.FK_Result_Session', 'F') IS NOT NULL
    ALTER TABLE dbo.Result DROP CONSTRAINT FK_Result_Session;
GO
