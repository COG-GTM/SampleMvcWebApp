-- MBA-3829 (A4): waitlist auto-offer (ADR 0003). When a seat frees up on an OPEN session the next eligible
-- waitlisted booking gets a PENDING offer that holds the seat until it is accepted, declined or expires
-- (scheduling.waitlist.offer-ttl). Only PENDING rows hold a seat: seats available =
-- capacity - BOOKED - ATTENDED - PENDING offers.
-- waitlist_entry_id is a plain reference (no FK): the entry is deleted when the offer is accepted or given up,
-- the offer row stays as history.
CREATE TABLE seat_offer (
    offer_id            BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT pk_seat_offer PRIMARY KEY,
    session_id          INT            NOT NULL CONSTRAINT fk_seat_offer_session REFERENCES exam_session (session_id),
    booking_id          INT            NOT NULL CONSTRAINT fk_seat_offer_booking REFERENCES booking (booking_id),
    waitlist_entry_id   INT            NULL,
    candidate_id        INT            NOT NULL,
    waitlist_position   INT            NOT NULL,    -- position when offered
    status              VARCHAR(10)    NOT NULL,
    seat_kind           VARCHAR(13)    NOT NULL,
    offered_at          DATETIME2(3)   NOT NULL,
    expires_at          DATETIME2(3)   NOT NULL,
    decided_at          DATETIME2(3)   NULL,
    decided_by          VARCHAR(60)    NULL,
    CONSTRAINT ck_seat_offer_status CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED','WITHDRAWN')),
    CONSTRAINT ck_seat_offer_seat_kind CHECK (seat_kind IN ('STANDARD','ACCOMMODATION')),
    CONSTRAINT ck_seat_offer_decided CHECK ((status = 'PENDING' AND decided_at IS NULL)
                                         OR (status <> 'PENDING' AND decided_at IS NOT NULL)),
    CONSTRAINT ck_seat_offer_expiry CHECK (expires_at > offered_at)
);
-- At most one PENDING offer per booking (the session row lock already serialises offer decisions; this is the
-- database backstop against a double offer).
CREATE UNIQUE INDEX ux_seat_offer_pending_booking ON seat_offer (booking_id) WHERE status = 'PENDING';
CREATE INDEX ix_seat_offer_session_status ON seat_offer (session_id, status);
CREATE INDEX ix_seat_offer_pending_expiry ON seat_offer (expires_at) WHERE status = 'PENDING';

ALTER TABLE outbox_event DROP CONSTRAINT CK_outbox_event_type;
ALTER TABLE outbox_event ADD CONSTRAINT CK_outbox_event_type CHECK (event_type IN (
    'BookingCreated','BookingCancelled','BookingRescheduled','BookingWaitlisted','AttendanceRecorded','CapacityChanged',
    'SeatOffered','SeatOfferAccepted','SeatOfferDeclined','SeatOfferExpired'));
