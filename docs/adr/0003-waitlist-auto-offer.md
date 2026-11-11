# ADR 0003: Waitlist auto-offer (deliberate change from usp_CancelBooking)

- Status: accepted
- Story: MBA-3829 (A4), epic MBA-3819
- Date: 2026-11-11

## Context

ADR 0002 ported the legacy seat rules into `services/scheduling-service` unchanged where possible. One of
those rules is that nobody is ever promoted from the waitlist: `usp_CancelBooking` frees the seat and
leaves `dbo.Waitlist` untouched, and center staff promote candidates by hand. Waitlist positions are never
compacted. MBA-3829 changes that on purpose: a freed seat is offered to the next waitlisted candidate, who
accepts or declines it in the modern UI.

## Decision

### 1. When an offer is made

`WaitlistOfferPolicy` (`domain/`, plain Java) runs under the session row lock
(`SessionRepository.lockForUpdate`, `UPDLOCK, ROWLOCK, HOLDLOCK`) in the same transaction as the change that
freed the seat:

- a BOOKED booking is cancelled (`SchedulingService.cancel`),
- a booking is rescheduled out of the session (the old session),
- capacity is increased (`changeCapacity`),
- an offer is declined or expires (`SeatOfferService.decline` / `expireDue`),
- a WAITLISTED booking that holds a pending offer is cancelled (the offer is WITHDRAWN and re-offered).

Only OPEN sessions make offers. Seats freed on a CLOSED/CANCELLED session stay as they are.

### 2. Who gets it

Free seats = capacity - BOOKED - ATTENDED - PENDING offers. The policy walks the waitlist in position order
and makes one offer per free seat. An ET25/ET50 candidate needs a free accommodation seat
(accommodation capacity - accommodation seats used by BOOKED/ATTENDED - ACCOMMODATION offers); without one
they are **skipped but keep their position** and the next standard candidate is offered. The skip is
re-evaluated every time, so the skipped candidate gets the next seat that fits. If nobody is eligible the seat
simply stays available for normal booking. Two free seats produce two offers, in position order.

### 3. The seat is held

A PENDING offer counts as a held seat everywhere: `SeatFormulas.seatsAvailable`, `SessionCapacity.reserve()`
(another booker is waitlisted while the offer is pending), the outbox `SessionState.seatsAvailable` and the
legacy `dbo.ExamSession.SeatsAvailable`. So `seatsAvailable` stays 0 during an offer cycle (AC1).

### 4. Accept, decline, expire, withdraw

| Transition | Booking | Waitlist | Offer | Event | Then |
|---|---|---|---|---|---|
| accept (before `expires_at`) | WAITLISTED -> BOOKED | entry removed, later positions shift down by 1 | ACCEPTED | `SeatOfferAccepted` | - |
| decline | WAITLISTED -> CANCELLED (reason "Seat offer declined") | entry removed, compacted | DECLINED | `SeatOfferDeclined` | next candidate offered |
| expiry job | WAITLISTED -> CANCELLED (reason "Seat offer expired") | entry removed, compacted | EXPIRED | `SeatOfferExpired` | next candidate offered |
| candidate cancels the waitlisted booking | CANCELLED | entry removed (legacy behaviour, not compacted) | WITHDRAWN | `BookingCancelled` | seat re-offered |
| capacity cut below the held seats | stays WAITLISTED | kept | WITHDRAWN (newest offers first) | `CapacityChanged` | - |

- **Position compaction on accept and give-up** is a deliberate change from legacy ("never compacted"); AC2
  requires positions 2-3 to become 1-2. Plain cancellation of a waitlisted booking without an offer keeps the
  legacy behaviour.
- **Decline / expiry leave the waitlist.** Keeping a candidate who declined at position 1 would re-offer them
  the same seat immediately; moving them to the end is a policy nobody asked for. Leaving the waitlist matches
  what "decline" means to the candidate, and they can book or join the waitlist again.
- **`SeatOfferDeclined` is its own event** (instead of a reason inside `SeatOfferExpired`) so the outbox and
  `dbo.AuditLog` (`OFFER_DECLINE`) say what happened.
- Accepting at or after `expires_at` is rejected with `OFFER_EXPIRED` (409) even if the job has not run yet
  (`PendingOffer.isExpiredAt`). The job then expires it and moves on.
- TTL: `scheduling.waitlist.offer-ttl` (ISO-8601, default `PT24H`). Job: `scheduling.waitlist.expiry-enabled`,
  `scheduling.waitlist.expiry-poll-ms`. Time comes from the injectable `Clock` bean.

### 5. No double offers

Every path that creates or decides an offer locks the session row first, so offer generation is serialized
per session. In addition `ux_seat_offer_pending_booking` (filtered unique index, `WHERE status = 'PENDING'`)
guarantees at most one pending offer per booking, and decisions update `WHERE status = 'PENDING'` so a racing
accept and decline cannot both win (the loser gets `OFFER_NOT_PENDING`, 409). `WaitlistOfferIT` races parallel
cancels, capacity increases and accept/decline.

### 6. Legacy projection and reconciliation

Events carry the full resulting state, as in MBA-3823: every touched booking (including re-numbered waitlist
positions and `offerExpiresAt`) and the session (`seatsOffered`, derived `seatsAvailable`). The relay:

- writes acceptance into `dbo.Booking` as BOOKED, deletes the `dbo.Waitlist` row and syncs shifted positions;
- marks a held seat in legacy with the new nullable column `dbo.Waitlist.OfferExpiresAt`
  (`db/schema/012_waitlist_offer_hold.sql`), cleared on accept/decline/expiry/withdrawal;
- sets `dbo.ExamSession.SeatsAvailable` to the derived modern value (0 while held);
- adds `AuditLog` actions `OFFER`, `OFFER_ACCEPT`, `OFFER_DECLINE`, `OFFER_EXPIRE` (fit `VARCHAR(30)`; event
  names fit `RelayCheckpoint.EventType VARCHAR(40)`).

The held seat is accounted for the same way on both sides, so there is no false drift while an offer is pending:
`ReconciliationService` compares `SeatsOffered` (modern: PENDING `seat_offer` rows; legacy: `Waitlist` rows with
`OfferExpiresAt`), and both `seat-drift.sql` copies compute expected seats as
`Capacity - BOOKED/ATTENDED - OfferedSeats`. After a full cycle reconciliation reports 0 mismatches (AC6).
Legacy readers that do not know the column keep working: it is nullable and nothing legacy writes it.

## Consequences

- Waitlisted candidates no longer depend on staff; staff can still change capacity, and see offer state
  ("Offered, expires ...", "Skipped: needs ET25") in the admin waitlist.
- A seat can be unavailable for up to one TTL without anybody being booked on it. The TTL is configurable.
- Legacy "promote by hand" from the .NET portal is retired along with the other booking writes (MBA-3824);
  the single writer makes all promotions.
