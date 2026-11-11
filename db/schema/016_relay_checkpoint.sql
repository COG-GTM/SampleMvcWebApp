-- 016_relay_checkpoint.sql
-- MBA-3823: idempotency ledger for the scheduling-service outbox relay. One row per applied outbox event,
-- written in the same transaction as the relay's Booking/Waitlist/ExamSession/AuditLog changes, so a
-- replayed event (relay killed mid-batch and restarted) finds its EventId here and is a no-op.
USE AssessmentDelivery;
GO
IF OBJECT_ID('dbo.RelayCheckpoint') IS NOT NULL DROP TABLE dbo.RelayCheckpoint;
GO
CREATE TABLE dbo.RelayCheckpoint (
    EventId    UNIQUEIDENTIFIER NOT NULL CONSTRAINT PK_RelayCheckpoint PRIMARY KEY,
    EventSeq   BIGINT        NOT NULL,
    EventType  VARCHAR(40)   NOT NULL,
    AppliedAt  DATETIME2(3)  NOT NULL CONSTRAINT DF_RelayCheckpoint_AppliedAt DEFAULT (SYSUTCDATETIME())
);
CREATE INDEX IX_RelayCheckpoint_EventSeq ON dbo.RelayCheckpoint (EventSeq);
GO
