/*
    usp_ChangeCapacity - change the seat capacity of a session (centre admin).
    SeatsAvailable is adjusted by the same delta and never goes below zero.
    Errors: 50005 session not found / invalid capacity.
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

    DECLARE @OldCapacity INT, @AccCap INT;

    BEGIN TRY
        BEGIN TRANSACTION;

        SELECT @OldCapacity = Capacity, @AccCap = AccommodationCapacity
        FROM dbo.ExamSession WITH (UPDLOCK, HOLDLOCK)
        WHERE SessionId = @SessionId;

        IF @OldCapacity IS NULL OR @NewCapacity < 0 OR @NewCapacity < @AccCap
            RAISERROR('Invalid capacity %d for session %d.', 16, 5, @NewCapacity, @SessionId);

        UPDATE dbo.ExamSession
           SET Capacity       = @NewCapacity,
               SeatsAvailable = CASE WHEN SeatsAvailable + (@NewCapacity - Capacity) < 0 THEN 0
                                     ELSE SeatsAvailable + (@NewCapacity - Capacity) END,
               LastModified   = GETDATE()
         WHERE SessionId = @SessionId;

        INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail, Actor)
        VALUES ('ExamSession', @SessionId, 'CAPACITY',
                CAST(@OldCapacity AS VARCHAR(12)) + ' -> ' + CAST(@NewCapacity AS VARCHAR(12)),
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
