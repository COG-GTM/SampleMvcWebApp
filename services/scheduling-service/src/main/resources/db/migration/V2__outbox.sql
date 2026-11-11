-- MBA-3823 transactional outbox. Every booking/capacity change made by SchedulingService appends one row here in
-- the same transaction (SchedulingService -> OutboxRepository.append), so a committed change always has its event
-- and a rolled-back one never does. event_seq is the monotonically increasing relay order; event_id is the
-- idempotency key the relay records in AssessmentDelivery.dbo.RelayCheckpoint (db/schema/016_relay_checkpoint.sql).
-- payload = JSON snapshot of the resulting state of the affected bookings (incl. waitlist position) and sessions
-- (capacity, accommodation capacity, derived SeatsAvailable), so applying an event twice gives the same rows.
CREATE TABLE outbox_event (
    event_seq      BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_outbox_event PRIMARY KEY,
    event_id       UNIQUEIDENTIFIER NOT NULL CONSTRAINT df_outbox_event_id DEFAULT (NEWID())
                                          CONSTRAINT uq_outbox_event_id UNIQUE,
    event_type     VARCHAR(40)    NOT NULL CONSTRAINT ck_outbox_event_type CHECK (event_type IN (
                       'BookingCreated', 'BookingCancelled', 'BookingRescheduled', 'BookingWaitlisted',
                       'AttendanceRecorded', 'CapacityChanged', 'InventoryBaselined')),
    aggregate_type VARCHAR(20)    NOT NULL CONSTRAINT ck_outbox_aggregate_type CHECK (aggregate_type IN ('Booking', 'ExamSession')),
    aggregate_id   INT            NOT NULL,
    payload        NVARCHAR(MAX)  NOT NULL,
    actor          VARCHAR(60)    NOT NULL,
    created_at     DATETIME2(3)   NOT NULL CONSTRAINT df_outbox_event_created_at DEFAULT (SYSUTCDATETIME()),
    relayed_at     DATETIME2(3)   NULL,
    attempts       INT            NOT NULL CONSTRAINT df_outbox_event_attempts DEFAULT (0),
    last_error     NVARCHAR(1000) NULL
);
CREATE INDEX ix_outbox_event_pending ON outbox_event (event_seq) WHERE relayed_at IS NULL;

-- legacy dbo.Booking.BookingId, set by the relay on first projection (imported bookings: their legacy id)
ALTER TABLE booking ADD legacy_booking_id INT NULL;
GO
CREATE UNIQUE INDEX uq_booking_legacy_booking_id ON booking (legacy_booking_id) WHERE legacy_booking_id IS NOT NULL;
