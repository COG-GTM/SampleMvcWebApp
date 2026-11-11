-- MBA-3823 (M4): transactional outbox. Every booking/capacity change inserts one row here in the same local
-- transaction as the change itself (no dual write). The relay (com.northwind.assessment.scheduling.relay)
-- projects pending rows into AssessmentDelivery in event_id order and stamps relayed_at.
-- event_id is the monotonically increasing sequence. Writers of one session serialise on its row lock, so per
-- session the ids follow commit order; the relay picks every row whose relayed_at is NULL (not "> last id"),
-- so a lower id that commits late is still relayed.
CREATE TABLE outbox_event (
    event_id     BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_outbox_event PRIMARY KEY,
    event_type   VARCHAR(40)    NOT NULL,
    session_id   INT            NOT NULL,
    booking_id   BIGINT         NULL,
    payload      NVARCHAR(MAX)  NOT NULL,
    created_at   DATETIME2(3)   NOT NULL,
    relayed_at   DATETIME2(3)   NULL,
    attempts     INT            NOT NULL CONSTRAINT DF_outbox_event_attempts DEFAULT (0),
    last_error   NVARCHAR(1000) NULL,
    CONSTRAINT CK_outbox_event_type CHECK (event_type IN ('BookingCreated','BookingCancelled','BookingRescheduled',
                                                          'BookingWaitlisted','AttendanceRecorded','CapacityChanged'))
);
CREATE INDEX IX_outbox_event_pending ON outbox_event (event_id) WHERE relayed_at IS NULL;
CREATE INDEX IX_booking_legacy_booking_id ON booking (legacy_booking_id) WHERE legacy_booking_id IS NOT NULL;
