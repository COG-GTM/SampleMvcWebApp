# Legacy oddities pinned by the characterization transcripts (MBA-3820)

Every scenario tagged `oddity: true` in `scenarios/*.yaml` lists one or more `ODD-xx` ids below. Each entry
is a one-line statement of what the **legacy estate really did at runtime** (tag `legacy-baseline`, seed
`SEED_AS_OF=2026-11-10`), the transcripts that prove it, and the hidden-behaviour id it confirms in
[`docs/as-is/hidden-behaviours.md`](../docs/as-is/hidden-behaviours.md). Nothing here was fixed: the
transcripts are the contract later steps must either keep or change on purpose (via ADR
`docs/adr/0002-ported-rule-decisions.md`, written by the scheduling-service story from this file).

Ticket items 1-6 and ADR sections 6a/6b map to: 1 = ODD-01, 2 = ODD-02, 3 = ODD-03, 4 = ODD-04, 5 = ODD-05,
6 = ODD-06, 6a = ODD-07, 6b = ODD-08.

| Id | HB | Oddity (one line, as observed) | Transcripts (portal / api / db / sync) |
|---|---|---|---|
| ODD-01 | HB-01, HB-04 | A partner booking takes **two** seats (Java `decrementSeatsAvailable` + `trg_Booking_SeatSync`), a portal booking one: session 154 goes 11 -> 9 via booking-api vs 11 -> 10 via the portal, and `seat-drift.sql` drift becomes -1. | portal `BK-01`, `CN-01`; api `BK-02`, `CN-02`, `AC-02`, `AC-04`, `RS-02`; both `MX-01`, `MX-03`; portal admin demotes the partner booking first in `CP-07` |
| ODD-02 | HB-05 | Lowering capacity below the active bookings makes `trg_ExamSession_Update` demote the **newest** BOOKED rows to WAITLISTED and append them to `Waitlist` (154: 20 -> 6 demotes bookings 1613/1614/1615 to positions 1-3, `SeatsAvailable` 0); only a `CAPACITY` audit row is written, none for the demoted bookings. No booking-api endpoint changes capacity, so the channels are portal admin and the shared proc. | portal `CP-03`, `CP-04`, `CP-07`, `CP-08`; db `CP-09` |
| ODD-03 | HB-03 | `POST /sync/run` rewrites `SeatsAvailable` with `Capacity - AccommodationCapacity - standard BOOKED/ATTENDED`, clamped at 0, while `db/checks/seat-drift.sql` expects `Capacity - (BOOKED + ATTENDED)`: on a fresh seed the check reports 0 drifting sessions before the sync and 112 sessions (total drift 177) after it. | sync `MX-02`; both `MX-01`, `MX-03` (ET bookings excluded from the sync count) |
| ODD-04 | HB-07, HB-08 | Retake wait is 30 **calendar** days in the portal and 30 **working** days in booking-api: NW00000113 (failed HPI-ACC1 Sat 2026-11-07) is eligible from 07 Dec 2026 in the portal but from 2026-12-18 in the API; NW00000950 (failed Fri 2026-10-30, month-end window) 29 Nov 2026 (a Sunday) vs 2026-12-11. Booking session 216 (2026-12-10) succeeds in the portal and is refused 409 `NOT_ELIGIBLE` by the API. | both `RT-03`, `RT-04`, `RT-05`, `RT-09`, `RT-10`; portal `RT-14`; api `RT-15` |
| ODD-05 | HB-11, HB-01 | booking-api reschedule = `usp_CancelBooking` + partner book: the target session loses **two** seats (163: 5 -> 3) and the source gets one back; the portal reschedule (`usp_RescheduleBooking`) moves exactly one seat (163: 5 -> 4). Both issue a new booking id and leave the old row CANCELLED. | portal `RS-01`, `RS-10`; api `RS-02`, `RS-09` |
| ODD-06 | HB-14 | `usp_ChangeCapacity` rejects a capacity below `AccommodationCapacity` (strict rule, PR 51 is not in `legacy-baseline`): RAISERROR 50000 **state 5** "Invalid capacity 1 for session 154.", rendered by the portal as an HTTP 200 page with that alert; nothing changes. | portal `CP-05`; db `CP-06` |
| ODD-07 | HB-09 | Blank accommodation code: the portal stores the candidate's profile default (NW00000003 -> `ET25`, so it consumes an accommodation seat), booking-api stores NULL. | portal `AC-05`; api `AC-06` |
| ODD-08 | HB-10, HB-11 | Reschedule onto the **same** session: the portal accepts it (old booking CANCELLED, a new booking id BOOKED on the same session, the seat is released and re-taken); booking-api refuses it with 409 `SAME_SESSION`. | portal `RS-05`; api `RS-06` |
| ODD-09 | HB-06 | Cancelling a confirmed seat on a full session frees the counter (153: 0 -> 1) but **nobody is promoted**: the 3 WAITLISTED bookings and their `Waitlist` rows stay as they were; cancelling a WAITLISTED booking deletes its `Waitlist` row and changes no counter. | portal `CN-05`, `CN-03`; api `CN-06`, `CN-04` |
| ODD-10 | HB-10 | Portal reschedule of a PARTNER booking re-creates it as `Channel='PORTAL'` with `ExternalRef` NULL (PX-3008 is lost) and releases only one of the two seats the partner booking took. | portal `RS-08` |
| ODD-11 | HB-14 | The documented error codes 50001-50006 never occur: every proc error is RAISERROR **50000** and the "code" is the state (duplicate = state 2, not open = state 1, invalid capacity = state 5, different exam = state 6). | db `BK-07`, `CP-06`, `RS-07` |
| ODD-12 | HB-14 | The same rule failure is reported differently per channel: the portal answers **HTTP 200** re-rendering the form with a free-text alert (and names the candidate by internal id: "Candidate 906 already has an active booking..."), booking-api answers **HTTP 409** with `{error: DUPLICATE_BOOKING / SESSION_NOT_OPEN / NOT_CANCELLABLE / DIFFERENT_EXAM / NO_ACCOMMODATION_SEAT}` and the candidate number. | portal `BK-05`, `BK-08`, `BK-10`, `CN-07`, `RS-03`, `AC-01`, `AC-07`, `CP-05`; api `BK-06`, `BK-09`, `BK-11`, `CN-08`, `RS-04`, `AC-02`, `AC-08` |
| ODD-13 | HB-02 | Partner book + cancel leaks a seat (154: 11 -> 9 -> 10) and the counter can go **negative** (163 reaches `SeatsAvailable = -1` after three partner bookings, one of them ET50); under mixed traffic partner bookings are WAITLISTED on sessions that still have real free seats (154: 13 active of 20, 4 waitlisted). | api `CN-02`, `AC-04`; portal `RS-08`; both `MX-01` |

## Not oddities, but worth knowing when reading transcripts

- Extra-time (`ET25`/`ET50`) bookings beyond `AccommodationCapacity` are refused by both channels the same way
  (portal alert "No accommodation seat available on session N." / API 409 `NO_ACCOMMODATION_SEAT`), including on
  sessions with `AccommodationCapacity = 0`; `SR` and `RB` bookings never count against it (`AC-01`..`AC-04`, `AC-07`, `AC-08`).
- Full sessions put new bookings on the waitlist through both channels with the next `Position` (`BK-03`, `BK-04`).
- Closed and cancelled sessions are both reported as "not open for booking" (`BK-08`..`BK-11`); started sessions
  remain OPEN and bookable (HB-31), which is why the catalogue never relies on the wall clock.
- Audit actors differ by channel (`ad_app` for portal procs, `booking-api`, `portal-admin`, `capacity-sync`; HB-17);
  every transcript snapshots the new `AuditLog` rows so this is pinned too.
