# Data ownership: tables x components (legacy estate)

Scope: tag `legacy-baseline` (`e458238`). Built by tracing every SQL statement in the C# services
(EF6 `DbSet`s and `ExecuteSqlCommand`), the Java DAOs/services (inline JdbcTemplate SQL), the four
procedures and the four triggers, then checked against the running estate (`AuditLog` actors,
trigger side effects). See [hidden-behavior.md](hidden-behavior.md) for the consequences.

Legend: **R** reads, **W** writes (I insert, U update, D delete), **-** no access.
"Runtime writers" excludes the seed scripts (`db/seed/*.sql`, run only by `db/apply.sh`).
All components connect as the same SQL login `ad_app` (`db/schema/000_create_database.sql:13-26`),
so the database cannot tell writers apart.

## Matrix

| Table | Portal (C#/EF6) | `usp_BookSeat` | `usp_CancelBooking` | `usp_ChangeCapacity` | `usp_RescheduleBooking` | `trg_Booking_SeatSync` | `trg_ExamSession_Update` | `trg_Candidate_Audit` | `trg_Exam_Audit` | booking-api | results-service | capacity-sync | seed / checks | Runtime writers |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `TestCenter` | R, **W(U)** `UpdateCenter` via EF | - | - | - | - | - | - | - | - | R (session join) | - | - | W / - | 1 |
| `Room` | R (`s.Room`) | - | - | - | - | - | - | - | - | R (session join) | - | - | W / - | 0 |
| `Exam` | R | - | - | - | - | - | - | - | R (`inserted`/`deleted`) | R (exam code, retake wait) | R (`PassingScore`) | - | W / - | 0 |
| `ExamSession` | R | R (UPDLOCK), **W(U)** `SeatsAvailable-1` | **W(U)** `SeatsAvailable+1` | R (UPDLOCK), **W(U)** `Capacity`, `SeatsAvailable` | R (`ExamId`) | **W(U)** `SeatsAvailable-n` | R, **W(U)** `LastModified`, `SeatsAvailable` | - | - | R, **W(U)** `SeatsAvailable-1` | R | R, **W(U)** `SeatsAvailable` | W / R | **6** :warning: |
| `Candidate` | R | - | - | - | - | - | - | R (`inserted`/`deleted`) | - | R | R (CSV candidate number) | - | W / - | 0 |
| `Booking` | R | R, **W(I)** BOOKED/WAITLISTED, `Channel=PORTAL` | R (UPDLOCK), **W(U)** CANCELLED | - | R | R (`inserted`) | R, **W(U)** BOOKED->WAITLISTED | - | - | R, **W(I)** PARTNER | R, **W(U)** BOOKED->ATTENDED | R | W / R | **5** :warning: |
| `Waitlist` | - | R (`MAX(Position)`), **W(I)** | **W(D)** | - | - | - | R, **W(I)** | - | - | R (`MAX(Position)`), **W(I)** | - | - | W / R | **4** :warning: |
| `Result` | R (eligibility, results page) | - | - | - | - | - | - | - | - | R (eligibility) | R, **W(I)** | - | W / R | 1 |
| `AuditLog` | - | **W(I)** | **W(I)** | **W(I)** | (via nested procs) | - | - | **W(I)** | **W(I)** | **W(I)** | **W(I)** | **W(I)** | W(D) / - | **8** :warning: |

Notes on the matrix:
- `usp_RescheduleBooking` writes only through the procs it calls (`usp_CancelBooking`, `usp_BookSeat`).
- `trg_Booking_SeatSync` fires for **every** insert into `Booking`; it writes only when the row is
  `BOOKED` and `Channel <> 'PORTAL'` - i.e. for booking-api inserts and imports.
- `trg_ExamSession_Update` fires for **every** update of `ExamSession` - including those from
  `usp_BookSeat`, `usp_CancelBooking`, booking-api, capacity-sync and the Booking trigger above
  (nesting guard `TRIGGER_NESTLEVEL(@@PROCID) > 1` stops recursion). It only rewrites bookings when
  `Capacity` decreases.
- The portal reaches `ExamSession`, `Booking`, `Waitlist` and `AuditLog` writes only through procs.
  Its only direct write is `TestCenter` (no audit).
- `AuditLog` has no reader in any application; it is written by 8 runtime writers with different
  conventions: procs use the `@Actor` passed by the caller, triggers use `SUSER_SNAME()` (so `ad_app`
  at runtime, `sa` for the 12 seed rows written by `trg_Exam_Audit`), booking-api `booking-api`,
  results-service `results-service`, capacity-sync `capacity-sync` with `EntityId = 0`.
- Nothing at runtime writes `Room`, `Exam` or `Candidate`; they are maintained outside the codebase
  (seed / manual SQL). Their triggers (`trg_Exam_Audit`, `trg_Candidate_Audit`) audit those manual edits.

## Tables with more than one runtime writer

| Table | Writers | Why it matters for the scheduling slice |
|---|---|---|
| `ExamSession` (esp. `SeatsAvailable`) | `usp_BookSeat`, `usp_CancelBooking`, `usp_ChangeCapacity`, `trg_Booking_SeatSync`, `trg_ExamSession_Update`, booking-api `SessionDao`, capacity-sync | Four different formulas, double decrement on partner bookings, nightly overwrite ([HB-01](hidden-behavior.md#hb-01), [HB-03](hidden-behavior.md#hb-03)). This is the table the scheduling service is supposed to own first. |
| `Booking` | `usp_BookSeat`, `usp_CancelBooking`, `trg_ExamSession_Update`, booking-api `BookingDao`, results-service | Booking status is changed by a trigger on another table ([HB-05](hidden-behavior.md#hb-05)) and by the results slice (ATTENDED). Partner inserts bypass the proc's lock ([HB-02](hidden-behavior.md#hb-02)). |
| `Waitlist` | `usp_BookSeat`, `usp_CancelBooking`, `trg_ExamSession_Update`, booking-api | Three independent `MAX(Position)+1` implementations, no unique constraint, duplicates observed ([HB-02](hidden-behavior.md#hb-02)). |
| `AuditLog` | 3 procs, 2 triggers, 3 Java services | Append-only, no reader; inconsistent actor/entity conventions; capacity demotions are not audited ([HB-05](hidden-behavior.md#hb-05)). |

## Column-level hot spots

| Column | Written by | Read to make decisions by |
|---|---|---|
| `ExamSession.SeatsAvailable` | 7 writers above | `usp_BookSeat` (BOOKED vs WAITLISTED), booking-api `BookingService.java:110`, portal session lists, `GET /api/v1/sessions` |
| `ExamSession.Capacity` | `usp_ChangeCapacity`, ad-hoc SQL | `trg_ExamSession_Update` (demotion), capacity-sync, `seat-drift.sql` |
| `ExamSession.Status` | **nobody at runtime** (no close-out job in repo) | every booking path (`OPEN` only), capacity-sync (`OPEN` only) |
| `Booking.Status` | 2 procs, 1 trigger, booking-api, results-service | seat counts everywhere, retake (via `Result` join), cancel rules |
| `Booking.Channel` / `ExternalRef` | `usp_BookSeat` (always `PORTAL`, no ref), booking-api (`PARTNER`, ref) | `trg_Booking_SeatSync` (seat decrement), partner reconciliation |
| `Result.Passed` | results-service at ingest | retake eligibility in both stacks |

## Implications for database-per-service

- **Scheduling service (first slice)** should own `TestCenter`, `Room`, `ExamSession` and - because
  seat counts are derived from them - `Booking` and `Waitlist`. Splitting `ExamSession` from
  `Booking` across services would turn every booking into a distributed transaction.
- **Results** keeps `Result`, but it currently writes `Booking.Status = ATTENDED`; that becomes an
  event the scheduling service consumes.
- **Candidate / Exam** have no runtime writer; scheduling needs read models of `Candidate`
  (`DefaultAccommodationCode`) and `Exam` (`RetakeWaitDays`, `PassingScore`).
- While legacy and new services share the database, the two triggers on `Booking`/`ExamSession`
  and capacity-sync are additional hidden writers that will act on any row the new service writes
  back. They must be explicitly retired or accounted for.
