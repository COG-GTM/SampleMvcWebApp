/*
    011 - bookkeeping tables of the scheduling-service outbox relay (MBA-3823, M4).

    RelayCheckpoint   one row per applied outbox event (Scheduling.dbo.outbox_event.event_id),
                      written in the same transaction as the event's legacy writes, so a replay
                      of an event that is already here is a no-op.
    RelayBookingMap   modern booking id (Scheduling, >= 50,000,001) -> legacy dbo.Booking.BookingId
                      assigned by the identity on first relay.  Kept on the legacy side, inside the
                      same transaction, so a relay crash between the legacy commit and the write-back
                      of booking.legacy_booking_id into Scheduling can never insert the booking twice.

    Only ad_sched_relay writes them (db/security/040_single_writer.sql).
*/
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.RelayCheckpoint') IS NOT NULL DROP TABLE dbo.RelayCheckpoint;
IF OBJECT_ID('dbo.RelayBookingMap') IS NOT NULL DROP TABLE dbo.RelayBookingMap;
GO
CREATE TABLE dbo.RelayCheckpoint (
    EventId         BIGINT        NOT NULL CONSTRAINT PK_RelayCheckpoint PRIMARY KEY,
    EventType       VARCHAR(40)   NOT NULL,
    SessionId       INT           NULL,
    BookingId       INT           NULL,
    AppliedAt       DATETIME      NOT NULL CONSTRAINT DF_RelayCheckpoint_AppliedAt DEFAULT (GETDATE())
);
GO
CREATE TABLE dbo.RelayBookingMap (
    ModernBookingId BIGINT        NOT NULL CONSTRAINT PK_RelayBookingMap PRIMARY KEY,
    LegacyBookingId INT           NOT NULL CONSTRAINT UQ_RelayBookingMap_Legacy UNIQUE,
    CreatedAt       DATETIME      NOT NULL CONSTRAINT DF_RelayBookingMap_CreatedAt DEFAULT (GETDATE())
);
GO
