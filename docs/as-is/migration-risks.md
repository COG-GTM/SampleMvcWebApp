# Migration risks: what to pin before touching the scheduling slice

Scope: tag `legacy-baseline` (`e458238`). The first slice to move to Java 21 / database-per-service
is **test-centre scheduling and seat capacity**: `TestCenter`, `Room`, `ExamSession` and, because
seat counts are derived from them, `Booking` and `Waitlist` (see
[data-ownership.md](data-ownership.md)). Every item below references a finding in
[hidden-behavior.md](hidden-behavior.md) that was reproduced against the running estate.

## 1. How to pin legacy behaviour

Characterization tests must run against the **real legacy database code**, not mocks: most of the
behaviour lives in procedures and triggers.

- Harness: SQL Server 2022 container (Testcontainers or `scripts/run-legacy.sh` with
  `db/apply.sh`), seed from `db/seed/`, booking-api on :8081. Each test rebuilds or snapshots the DB
  (`run-legacy.sh` re-creates it; `SKIP_DB=1` keeps it).
- Drive the **portal path** by executing the procs exactly as the C# services do
  (`EXEC dbo.usp_BookSeat @SessionId, @CandidateId, @AccommodationCode, @BookingId OUTPUT, @ResultStatus OUTPUT`
  etc. - `AssessmentDelivery.Services/Booking/BookingService.cs:116,136,177`,
  `AssessmentDelivery.Services/Admin/AdminService.cs:79`) plus the C# pre-checks that live outside
  the procs (retake: `RetakeRules.cs`, default accommodation: `BookingService.cs:101`). The C# rules
  can additionally be pinned with unit tests in `AssessmentDelivery.Tests` on a Windows agent.
- Drive the **partner path** through booking-api HTTP.
- Assert on: HTTP status/body or proc output, `Booking` rows (status, channel, external ref,
  accommodation), `Waitlist` rows (positions), `ExamSession.SeatsAvailable`, `AuditLog` rows, and
  the output of `db/checks/seat-drift.sql`.
- Existing coverage is negligible: 5 Java unit tests (all pass) and 5 C# unit tests (`AssessmentDelivery.Tests/UnitTests`); none
  covers the database, concurrency, triggers, the retake boundary or channel parity.
- For every behaviour that is a known bug (marked **bug?** below) pin *today's* behaviour and record
  the decision; the new service may intentionally differ, but the difference must be a test change,
  not an accident.

## 2. Characterization tests (must exist before the first scheduling change)

Priority P0 = blocks the first slice; P1 = before dual-running; P2 = before decommissioning legacy.

### Seat counting and booking

| ID | Given / When / Then | Ref | P |
|---|---|---|---|
| CT-01 | OPEN session with `SeatsAvailable = n > 0`; book via `usp_BookSeat` -> `BOOKED`, `Channel = PORTAL`, `SeatsAvailable = n-1`, one `AuditLog` row | HB-01 | P0 |
| CT-02 | Same session; partner `POST /api/v1/bookings` -> 201 `BOOKED`, `Channel = PARTNER`, `ExternalRef` kept, `SeatsAvailable = n-2` (**bug?**: double decrement) | HB-01 | P0 |
| CT-03 | Cancel BOOKED via proc or `DELETE /api/v1/bookings/{id}` -> `CANCELLED`, `SeatsAvailable + 1`, cancel of WAITLISTED deletes its `Waitlist` row, no renumbering | HB-01, HB-07 | P0 |
| CT-04 | Cancelling a BOOKED seat while a waitlist exists -> nobody promoted; a new booker gets the seat | HB-07 | P0 |
| CT-05 | `SeatsAvailable = 0`; book -> `WAITLISTED` + `Waitlist.Position = MAX+1` (both paths) | HB-02 | P0 |
| CT-06 | Session not OPEN -> proc raises msg 50000 state 1; booking-api returns its error code | HB-11 | P0 |
| CT-07 | Candidate already has an active booking on the session -> proc state 2; booking-api 409 | HB-02, HB-11 | P0 |
| CT-08 | 20 concurrent partner bookings for 1 seat -> today: >1 BOOKED, negative counter, duplicate positions (**bug?**); via proc -> exactly 1 BOOKED | HB-02 | P0 |
| CT-09 | 10 concurrent partner bookings by one candidate -> today: several active bookings (**bug?**); via proc -> 1 | HB-02 | P0 |
| CT-10 | Fresh seed -> `seat-drift.sql` returns 0 rows; after `POST /sync/run` -> every OPEN session with `AccommodationCapacity > 0` drifts by exactly `AccommodationCapacity` (112 sessions on the seed) | HB-03 | P0 |
| CT-11 | capacity-sync on a session made wrong by CT-02 -> counter rewritten to `max(0, Capacity - AccommodationCapacity - standard booked)`; only OPEN sessions touched; one aggregate `AuditLog` row, `EntityId = 0` | HB-03 | P0 |
| CT-12 | Direct `UPDATE Booking SET Status='CANCELLED'` -> counter unchanged; direct multi-row `INSERT` with `Channel='IMPORT'` -> counter decremented per BOOKED row, no capacity/duplicate/ET checks, no `Waitlist` rows | HB-15 | P1 |

### Accommodation

| ID | Given / When / Then | Ref | P |
|---|---|---|---|
| CT-13 | `ET25`/`ET50` booking when `ET in use >= AccommodationCapacity` -> proc state 3 (error, not waitlist); booking-api equivalent error | HB-08 | P0 |
| CT-14 | `SR` / `RB` -> no accommodation-seat check; unknown code -> rejected by both stacks | HB-08 | P0 |
| CT-15 | Candidate with `DefaultAccommodationCode = ET50`, blank code: portal path uses ET50; partner path stores `NULL` (confirmed with `NW00000903` on session 298) | HB-08 | P0 |
| CT-16 | Standard bookings fill all seats including the ET pool -> next ET candidate is WAITLISTED (pool not held back by procs) | HB-08, HB-03 | P1 |

### Capacity change

| ID | Given / When / Then | Ref | P |
|---|---|---|---|
| CT-17 | `usp_ChangeCapacity` increase by d -> `SeatsAvailable + d`; decrease with spare seats -> `SeatsAvailable - d` floored at 0; `< 0` or `< AccommodationCapacity` -> state 5 | HB-05 | P0 |
| CT-18 | Decrease below BOOKED count -> newest `BookedAt` (tie `BookingId`) BOOKED rows become WAITLISTED regardless of channel/accommodation, appended to `Waitlist` in `BookedAt` order, `SeatsAvailable = Capacity - BOOKED`, only one `CAPACITY a -> b` audit row (session 152: 28 -> 10 demoted 1579, 1580) | HB-05 | P0 |
| CT-19 | Same via plain `UPDATE ExamSession SET Capacity = ...` -> same demotion; plain `UPDATE` raising capacity leaves `SeatsAvailable` unchanged; raising capacity never re-promotes | HB-05 | P0 |
| CT-20 | Session with ATTENDED bookings: decrease counts only BOOKED (session can end over capacity) | HB-05, HB-03 | P1 |
| CT-21 | Capacity change / cancel on DELIVERED session -> accepted | HB-16 | P1 |

### Reschedule

| ID | Given / When / Then | Ref | P |
|---|---|---|---|
| CT-22 | Proc reschedule of PARTNER booking -> new booking `PORTAL`, `ExternalRef NULL`, old `CANCELLED` reason `Rescheduled` (1606 -> 3610) | HB-06 | P0 |
| CT-23 | API reschedule -> new booking `Channel = booking.channel` (`PARTNER`), `ExternalRef` copied, accommodation copied; old +1 seat, new -2 seats | HB-06, HB-01 | P0 |
| CT-24 | Same-session move: proc allows (new id), API 409 `SAME_SESSION` | HB-06 | P0 |
| CT-25 | Target full: both paths cancel the seat and return WAITLISTED (API still 200) | HB-06 | P0 |
| CT-26 | Different exam -> proc state 6; target not OPEN -> whole reschedule rolls back, original stays BOOKED | HB-06 | P0 |
| CT-27 | Opposite reschedules concurrently -> one may fail as deadlock (msg 50000 state 51) | HB-17 | P2 |

### Retake eligibility (gate on booking)

| ID | Given / When / Then | Ref | P |
|---|---|---|---|
| CT-28 | No `Result` rows -> eligible (both) | HB-04 | P0 |
| CT-29 | Any passed `Result` -> blocked (both); proc alone books anyway | HB-04, HB-09 | P0 |
| CT-30 | Failed on D, wait 30: portal earliest = D + 30 calendar days; Java = D + 30 working days (Mon-Fri, no holidays). Boundary pair: `NW00000113`/`HPI-ACC1`, D = 2026-11-07 -> 2026-12-07 vs 2026-12-18; test D-1, D, D+1 around both | HB-04 | P0 |
| CT-31 | Attempt = latest `Result` session `StartsAt` date; BOOKED/ATTENDED without a result does not count (no-shows) | HB-09 | P0 |
| CT-32 | Run CT-30 with JVM time zone UTC and with the centre's zone -> pin whether the date flips | HB-13 | P1 |

### Contracts used by other components

| ID | Given / When / Then | Ref | P |
|---|---|---|---|
| CT-33 | Proc errors are msg 50000, severity 16, state 1-6 with the exact message texts; FK violations/deadlocks re-raised as 50000 | HB-11 | P0 |
| CT-34 | `GET /api/v1/sessions` returns all statuses, `TOP 500`, ordered by `StartsAt, SessionId`, `to` inclusive of the whole day; `GET /api/v1/sessions/{id}` includes live booked count | HB-14 | P1 |
| CT-35 | Portal session search: OPEN only, `StartsAt >= today`, active centres only in the filter; inactive centre's sessions still bookable by both paths | HB-10 | P1 |
| CT-36 | Result ingest marks BOOKED -> ATTENDED; ATTENDED still counts as a held seat in `seat-drift.sql` and capacity-sync but not in the capacity trigger | HB-12, HB-03 | P1 |
| CT-38 | Portal cancel of a booking that belongs to another candidate -> accepted today (**bug?**); capacity 9999 via portal POST -> accepted | HB-18 | P1 |
| CT-37 | `AuditLog` rows per operation (entity, action, actor) for book/cancel/capacity/partner/sync | data-ownership | P2 |

## 3. Risks and decisions that must precede cutover

| # | Risk | Evidence | Mitigation / decision owner |
|---|---|---|---|
| R1 | No single definition of "seats available"; legacy counter is wrong for partner bookings and rewritten nightly. Copying `SeatsAvailable` into the new DB copies the corruption. | HB-01, HB-03 | Product: pick the formula (with or without a reserved accommodation pool). Engineering: recompute from bookings at migration, never copy the column. |
| R2 | Dual-running while legacy writers remain: `trg_Booking_SeatSync`, `trg_ExamSession_Update` and capacity-sync act on any row the new service writes back into the shared DB. | data-ownership | Disable capacity-sync and the two triggers for migrated sessions (or move ownership atomically); add a CT that a write-back produces no extra side effect. |
| R3 | Partner path overbooks and duplicates under concurrency. | HB-02 | New service: atomic seat decision + filtered unique index on active `(SessionId, CandidateId)`; data cleansing for existing duplicates/overbooked sessions. |
| R4 | Capacity-decrease demotion is invisible business logic with no notification and no audit. | HB-05 | Product: keep newest-first? notify? Engineering: port behind an explicit domain event. |
| R5 | Retake rule differs by channel; DB enforces none; depends on `Result` (other service). | HB-04, HB-09 | Exam owner: calendar vs working days, holidays, what counts as an attempt. Engineering: eligibility API or replicated read model. |
| R6 | Channel/ExternalRef lost on portal reschedules; partner reconciliation depends on them. | HB-06 | Keep channel/ref in the new reschedule; backfill not possible for past moves. |
| R7 | Error contract (msg 50000 + state) consumed by the portal's `SqlErrorHelper`. | HB-11 | Any proc-shaped facade kept for the portal must preserve it. |
| R8 | Session status transitions (OPEN -> CLOSED/DELIVERED) happen outside the repo; capacity-sync assumes a 01:00 close-out. | HB-13 | Find the job (SQL Agent / ops script) before moving `ExamSession` ownership. |
| R9 | `LastModified` cannot drive incremental sync. | HB-17 | Use rowversion / CDC / outbox for legacy -> new replication. |
| R10 | Time zones: `StartsAt` is centre-local without offset; Java 21 containers default to UTC. | HB-13 | Store `StartsAt` with zone or offset in the new schema; pin CT-32. |
| R12 | No authorization anywhere in the legacy booking flows (portal has no sign-in, cancel has no ownership check). | HB-18 | New API needs an auth model from day one; partner and candidate identities must map to bookings. |
| R11 | Legacy data already violates the intended invariants (overbooked sessions, duplicate active bookings, waitlisted rows without `Waitlist`, negative counters). | HB-02, HB-15 | Pre-migration data-quality report: `BOOKED > Capacity`, duplicates, orphan waitlist, negative `SeatsAvailable`. |

## 4. Open questions for the owning team

1. Which seat formula is correct, and is the accommodation pool reserved for ET candidates?
2. Who promotes from the waitlist today, and should the new service do it automatically?
3. Is newest-first demotion on capacity reduction intended, and should candidates be notified?
4. Retake wait: calendar or working days? Do no-shows count as attempts?
5. Where does the 01:00 session close-out run, and what are the production database settings
   (isolation level / `READ_COMMITTED_SNAPSHOT`, which changes the HB-02 race window)?
6. Should portal reschedules preserve partner channel and external reference?
