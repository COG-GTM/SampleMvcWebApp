/*
    usp_BookSeat - book a seat on an exam session (portal channel)

    Returns @ResultStatus = 'BOOKED' or 'WAITLISTED' (session full).
    Errors (RAISERROR, severity 16):
        50001 session not found / not open
        50002 candidate already holds an active booking on this session
        50003 no accommodation seat left for an extra-time booking
    Retake eligibility is validated by the calling application before this is called.
    2012-03  initial version (portal v2)
    2014-08  accommodation seats (ET25/ET50)
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.usp_BookSeat') IS NOT NULL DROP PROCEDURE dbo.usp_BookSeat;
GO
CREATE PROCEDURE dbo.usp_BookSeat
    @SessionId         INT,
    @CandidateId       INT,
    @AccommodationCode VARCHAR(10) = NULL,
    @BookingId         INT         OUTPUT,
    @ResultStatus      VARCHAR(12) OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @Seats INT, @AccCap INT, @SessStatus VARCHAR(12), @AccUsed INT, @Pos INT;

    BEGIN TRY
        BEGIN TRANSACTION;

        SELECT @Seats = SeatsAvailable, @AccCap = AccommodationCapacity, @SessStatus = Status
        FROM dbo.ExamSession WITH (UPDLOCK, HOLDLOCK)
        WHERE SessionId = @SessionId;

        IF @SessStatus IS NULL OR @SessStatus <> 'OPEN'
            RAISERROR('Session %d is not open for booking.', 16, 1, @SessionId);

        IF EXISTS (SELECT 1 FROM dbo.Booking
                   WHERE SessionId = @SessionId AND CandidateId = @CandidateId
                     AND Status IN ('BOOKED','WAITLISTED'))
            RAISERROR('Candidate %d already has an active booking on session %d.', 16, 2, @CandidateId, @SessionId);

        IF @AccommodationCode IN ('ET25','ET50')
        BEGIN
            SELECT @AccUsed = COUNT(*) FROM dbo.Booking
            WHERE SessionId = @SessionId AND Status IN ('BOOKED','ATTENDED')
              AND AccommodationCode IN ('ET25','ET50');
            IF @AccUsed >= @AccCap
                RAISERROR('No accommodation seat available on session %d.', 16, 3, @SessionId);
        END

        IF @Seats > 0
        BEGIN
            INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel)
            VALUES (@SessionId, @CandidateId, 'BOOKED', @AccommodationCode, 'PORTAL');
            SET @BookingId = SCOPE_IDENTITY();

            UPDATE dbo.ExamSession
               SET SeatsAvailable = SeatsAvailable - 1, LastModified = GETDATE()
             WHERE SessionId = @SessionId;

            SET @ResultStatus = 'BOOKED';
        END
        ELSE
        BEGIN
            INSERT INTO dbo.Booking (SessionId, CandidateId, Status, AccommodationCode, Channel)
            VALUES (@SessionId, @CandidateId, 'WAITLISTED', @AccommodationCode, 'PORTAL');
            SET @BookingId = SCOPE_IDENTITY();

            SELECT @Pos = ISNULL(MAX(Position), 0) + 1 FROM dbo.Waitlist WHERE SessionId = @SessionId;
            INSERT INTO dbo.Waitlist (SessionId, CandidateId, BookingId, Position)
            VALUES (@SessionId, @CandidateId, @BookingId, @Pos);

            SET @ResultStatus = 'WAITLISTED';
        END

        INSERT INTO dbo.AuditLog (EntityName, EntityId, Action, Detail)
        VALUES ('Booking', @BookingId, 'BOOK', 'session=' + CAST(@SessionId AS VARCHAR(12)) + ' status=' + @ResultStatus);

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
