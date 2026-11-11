/*
    usp_CancelBooking - cancel a BOOKED or WAITLISTED booking and release the seat.
    Waitlisted candidates are NOT promoted automatically; centre staff promote
    them from the admin screen (manual process since 2013).
    Errors: 50004 booking not found or not cancellable.
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.usp_CancelBooking') IS NOT NULL DROP PROCEDURE dbo.usp_CancelBooking;
GO
CREATE PROCEDURE dbo.usp_CancelBooking
    @BookingId INT,
    @Reason    NVARCHAR(200) = NULL,
    @Actor     VARCHAR(60)   = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @Status VARCHAR(12), @SessionId INT;

    BEGIN TRY
        BEGIN TRANSACTION;

        SELECT @Status = Status, @SessionId = SessionId
        FROM dbo.Booking WITH (UPDLOCK, HOLDLOCK)
        WHERE BookingId = @BookingId;

        IF @Status IS NULL OR @Status NOT IN ('BOOKED','WAITLISTED')
            RAISERROR('Booking %d cannot be cancelled.', 16, 4, @BookingId);

        UPDATE dbo.Booking
           SET Status = 'CANCELLED', CancelledAt = GETDATE(), CancelReason = @Reason, LastModified = GETDATE()
         WHERE BookingId = @BookingId;

        IF @Status = 'BOOKED'
            UPDATE dbo.ExamSession
               SET SeatsAvailable = SeatsAvailable + 1, LastModified = GETDATE()
             WHERE SessionId = @SessionId;
        ELSE
            DELETE FROM dbo.Waitlist WHERE BookingId = @BookingId;

        INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor)
        VALUES ('Booking', @BookingId, 'CANCEL', @Reason, ISNULL(@Actor, SUSER_SNAME()));

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
