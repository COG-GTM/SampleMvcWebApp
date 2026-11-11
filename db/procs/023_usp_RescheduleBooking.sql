/*
    usp_RescheduleBooking - move a booking to another session of the same exam.
    Implemented as cancel + new booking so seat counters stay in step.
    Returns the new booking id and its status (BOOKED / WAITLISTED).
    Errors: 50006 target session is for a different exam.
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.usp_RescheduleBooking') IS NOT NULL DROP PROCEDURE dbo.usp_RescheduleBooking;
GO
CREATE PROCEDURE dbo.usp_RescheduleBooking
    @BookingId     INT,
    @NewSessionId  INT,
    @NewBookingId  INT         OUTPUT,
    @ResultStatus  VARCHAR(12) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @CandidateId INT, @Acc VARCHAR(10), @OldExam INT, @NewExam INT;

    SELECT @CandidateId = b.CandidateId, @Acc = b.AccommodationCode, @OldExam = s.ExamId
    FROM dbo.Booking b JOIN dbo.ExamSession s ON s.SessionId = b.SessionId
    WHERE b.BookingId = @BookingId;

    SELECT @NewExam = ExamId FROM dbo.ExamSession WHERE SessionId = @NewSessionId;

    IF @OldExam IS NULL OR @NewExam IS NULL OR @OldExam <> @NewExam
    BEGIN
        RAISERROR('Booking %d cannot be moved to session %d.', 16, 6, @BookingId, @NewSessionId);
        RETURN 1;
    END

    BEGIN TRY
        BEGIN TRANSACTION;
        EXEC dbo.usp_CancelBooking @BookingId = @BookingId, @Reason = N'Rescheduled';
        EXEC dbo.usp_BookSeat @SessionId = @NewSessionId, @CandidateId = @CandidateId,
                              @AccommodationCode = @Acc,
                              @BookingId = @NewBookingId OUTPUT, @ResultStatus = @ResultStatus OUTPUT;
        COMMIT TRANSACTION;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        DECLARE @msg NVARCHAR(2048) = ERROR_MESSAGE(), @state INT = ERROR_STATE();
        RAISERROR(@msg, 16, @state);
        RETURN 1;
    END CATCH
    RETURN 0;
END
GO
