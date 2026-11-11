# ADR 0004: freed seats are offered to the waitlist (MBA-3829, A4)

Status: accepted. Deliberate behaviour change to [ADR 0002](0002-ported-rule-decisions.md) ODD-09, built on the single
writer and outbox of [ADR 0003](0003-single-writer-outbox-relay.md).

## Context

Legacy never promotes anyone: `usp_CancelBooking` frees the seat and leaves the waitlist untouched
([HB-06](../as-is/hidden-behaviours.md), [MR-12](../as-is/migration-risks.md)); centre staff promote by hand. ADR 0002
kept that behaviour (ODD-09) and left automatic promotion to a later story. Since M4 scheduling-service is the only
writer of bookings, waitlist and capacity, so the rule can change in one place and the relay carries the result to
every legacy reader.

## Decisions

1. **Trigger.** Whenever a seat frees up in an `OPEN` session (cancel of a BOOKED booking, reschedule out, capacity
   increase, an offer declined, expired or withdrawn), `WaitlistOfferPolicy` offers it, in the same transaction and
   under the same session lock (`UPDLOCK` on `exam_session`) as the change that freed it. Closed, delivered and
   cancelled sessions make no offers.
2. **Who.** The lowest-position `WAITLISTED` entry without a pending offer whose accommodation needs fit
   (`WaitlistOfferRules`, pure domain). `ET25`/`ET50` need a free accommodation seat; while none is free they are
   skipped and keep their position, and the next standard entry is offered. Several free seats give several offers in
   position order.
3. **Offer and hold.** A `seat_offer` row (`V3__waitlist_offers.sql`) with status `PENDING`, `expires_at = now +
   scheduling.waitlist.offer-ttl` (default `PT24H`). A pending offer holds its seat (and the accommodation seat for
   ET entries): `SessionCapacity.heldSeats` is subtracted from `seatsAvailable`, so a new booking is waitlisted, not
   booked, and `seatsAvailable` stays 0. A filtered unique index allows one pending offer per booking, so concurrent
   triggers cannot double-offer.
4. **Accept** (`POST /api/v2/offers/{offerId}/accept`, before expiry): the booking becomes `BOOKED`, its waitlist entry
   is removed and later positions move up by one. **Decline** (`.../decline`) and **expiry** (`WaitlistOfferExpiryJob`,
   every `scheduling.waitlist.expiry-poll`, default `PT1M`) cancel the waitlisted booking (reason `Waitlist offer
   declined` / `Waitlist offer expired`), remove its entry, shift later positions, and offer the seat to the next
   eligible entry; if nobody is left the seat is simply available. Accepting an expired offer answers
   `409 OFFER_NOT_PENDING` even if the job has not swept it yet.
5. **Withdraw.** Cancelling or rescheduling a booking with a pending offer withdraws the offer (`WITHDRAWN`) and passes
   the seat on; a capacity decrease withdraws the newest pending offers that no longer fit. Withdrawn candidates keep
   their position.
6. **Outbox.** New event types `SeatOffered`, `SeatOfferAccepted`, `SeatOfferDeclined`, `SeatOfferExpired`,
   `SeatOfferWithdrawn`, written in the same transaction and carrying the full resulting booking/session state like
   every M4 event (state transfer). The relay needs no new code: acceptance projects as `BOOKED` in `dbo.Booking`, the
   `dbo.Waitlist` row is deleted and the remaining positions are rewritten.
7. **Legacy view of a hold.** The hold exists only in scheduling-service. In legacy the offered booking stays
   `WAITLISTED` and `ExamSession.SeatsAvailable` stays `Capacity - (BOOKED + ATTENDED)` (the formula
   `db/checks/seat-drift.sql` checks), so reconciliation and seat drift stay at 0 during and after an offer cycle.
   Legacy writes are denied (ADR 0003), so no legacy path can take the held seat.
8. **Waitlisted cancel** keeps ODD-09: cancelling a waitlisted booking without an offer deletes its entry and does not
   renumber. Accept, decline and expiry renumber because the ticket requires positions 2-3 to become 1-2.

## Consequences

* Candidates see a pending offer under My bookings with Accept / Decline; the admin waitlist table shows offer state
  and the session page counts held seats.
* Authorisation is the same as for the other `/api/v2` candidate writes (cancel, reschedule): none beyond the gateway.
  Candidate authentication is out of scope for the scheduling slice.
* Anyone holding an offer id can accept or decline it; this is by design for now, consistent with cancel/reschedule,
  because the slice has no candidate login (MR-16 in `docs/as-is/migration-risks.md`).
* The routing gateway's modern write guard covers `POST /api/v2/offers/{id}/accept|decline`: it resolves offer ->
  session -> centre (`/api/v2/internal/routing/resolve?offerId=`) and refuses with `403 CENTER_NOT_MODERN` when that
  centre is not on `routing.modern-centers` or cannot be resolved (fail closed).
* Rolling back a centre to legacy (runbook section 5) leaves pending offers in Scheduling only; the legacy waitlist is
  intact, so staff promote by hand as before.
