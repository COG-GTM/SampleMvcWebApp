-- MBA-3829 waitlist auto-offer (ADR 0005). A freed seat in an OPEN session is offered to the lowest-position waitlist
-- entry whose accommodation needs fit (service/WaitlistOfferPolicy). While an offer is PENDING the seat is held:
-- SessionCapacity subtracts held seats from availability, so nobody else can book it.
-- The hold lives only here. Legacy dbo.ExamSession.SeatsAvailable keeps Capacity - (BOOKED + ATTENDED) (seat-drift.sql,
-- ADR 0003), the offered booking stays WAITLISTED in legacy until the relay projects SeatOfferAccepted as BOOKED.
CREATE TABLE seat_offer (
    offer_id       INT IDENTITY(1,1) NOT NULL CONSTRAINT pk_seat_offer PRIMARY KEY,
    session_id     INT          NOT NULL CONSTRAINT fk_seat_offer_session REFERENCES exam_session(session_id),
    booking_id     INT          NOT NULL CONSTRAINT fk_seat_offer_booking REFERENCES booking(booking_id),
    candidate_id   INT          NOT NULL CONSTRAINT fk_seat_offer_candidate REFERENCES candidate_snapshot(candidate_id),
    position       INT          NOT NULL,
    status         VARCHAR(10)  NOT NULL CONSTRAINT ck_seat_offer_status CHECK (status IN
                       ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN')),
    offered_at     DATETIME2(0) NOT NULL,
    expires_at     DATETIME2(0) NOT NULL,
    resolved_at    DATETIME2(0) NULL,
    resolution     NVARCHAR(200) NULL
);
GO
-- at most one live offer per waitlisted booking (no double offers, even under concurrent triggers)
CREATE UNIQUE INDEX uq_seat_offer_pending_booking ON seat_offer (booking_id) WHERE status = 'PENDING';
CREATE INDEX ix_seat_offer_session_status ON seat_offer (session_id, status);
CREATE INDEX ix_seat_offer_pending_expiry ON seat_offer (expires_at) WHERE status = 'PENDING';

ALTER TABLE outbox_event DROP CONSTRAINT ck_outbox_event_type;
ALTER TABLE outbox_event ADD CONSTRAINT ck_outbox_event_type CHECK (event_type IN (
    'BookingCreated', 'BookingCancelled', 'BookingRescheduled', 'BookingWaitlisted',
    'AttendanceRecorded', 'CapacityChanged', 'InventoryBaselined',
    'SeatOffered', 'SeatOfferAccepted', 'SeatOfferDeclined', 'SeatOfferExpired', 'SeatOfferWithdrawn'));
