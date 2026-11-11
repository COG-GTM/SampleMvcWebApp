/*
    dbo.Result.AttendancePending - 1 while scheduling-service has not confirmed POST /bookings/{id}/attendance for this
    result (results-service records attendance only after the Result commits, docs/to-be/adr-001 section 2).
    Existing and legacy rows are confirmed (0). Only a pending Result is removed when its booking turns out to be
    inactive; a resend of a pending Result completes the attendance call. Safe to re-run.
*/
USE AssessmentDelivery;
GO

IF COL_LENGTH('dbo.Result', 'AttendancePending') IS NULL
    ALTER TABLE dbo.Result ADD AttendancePending BIT NOT NULL
        CONSTRAINT DF_Result_AttendancePending DEFAULT (0);
GO
