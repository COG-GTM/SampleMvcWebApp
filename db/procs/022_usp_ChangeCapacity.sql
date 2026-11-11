/*
    usp_ChangeCapacity - change the seat capacity of a session (centre admin).
    SeatsAvailable is adjusted by the same delta and never goes below zero.
    AccommodationCapacity is part of Capacity: a reduction below it shrinks the
    accommodation allocation to the new capacity. Only accommodation seats that are
    already booked (ET25/ET50, BOOKED or ATTENDED) block a reduction.
    Errors: 50005 session not found / invalid capacity / below booked accommodation seats.
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.usp_ChangeCapacity') IS NOT NULL DROP PROCEDURE dbo.usp_ChangeCapacity;
GO
CREATE PROCEDURE dbo.usp_ChangeCapacity
    @SessionId   INT,
    @NewCapacity INT,
    @Actor       VARCHAR(60) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @OldCapacity INT, @AccCap INT, @AccBooked INT;

    BEGIN TRY
        BEGIN TRANSACTION;

        SELECT @OldCapacity = Capacity, @AccCap = AccommodationCapacity
        FROM dbo.ExamSession WITH (UPDLOCK, HOLDLOCK)
        WHERE SessionId = @SessionId;

        IF @OldCapacity IS NULL OR @NewCapacity < 0
            RAISERROR('Invalid capacity %d for session %d.', 16, 5, @NewCapacity, @SessionId);

        SELECT @AccBooked = COUNT(*) FROM dbo.Booking
        WHERE SessionId = @SessionId AND Status IN ('BOOKED','ATTENDED')
          AND AccommodationCode IN ('ET25','ET50');

        IF @NewCapacity < @AccBooked
            RAISERROR('Capacity %d for session %d is below the %d accommodation seats already booked.',
                      16, 5, @NewCapacity, @SessionId, @AccBooked);

        UPDATE dbo.ExamSession
           SET Capacity       = @NewCapacity,
               AccommodationCapacity = CASE WHEN @NewCapacity < AccommodationCapacity THEN @NewCapacity
                                            ELSE AccommodationCapacity END,
               SeatsAvailable = CASE WHEN SeatsAvailable + (@NewCapacity - Capacity) < 0 THEN 0
                                     ELSE SeatsAvailable + (@NewCapacity - Capacity) END,
               LastModified   = GETDATE()
         WHERE SessionId = @SessionId;

        INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor)
        VALUES ('ExamSession', @SessionId, 'CAPACITY',
                CAST(@OldCapacity AS VARCHAR(12)) + ' -> ' + CAST(@NewCapacity AS VARCHAR(12))
                + CASE WHEN @NewCapacity < @AccCap
                       THEN ' (accommodation ' + CAST(@AccCap AS VARCHAR(12)) + ' -> ' + CAST(@NewCapacity AS VARCHAR(12)) + ')'
                       ELSE '' END,
                ISNULL(@Actor, SUSER_SNAME()));

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
