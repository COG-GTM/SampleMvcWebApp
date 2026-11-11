# ADR 0002: scheduling rules ported into scheduling-service (MBA-3821)

- Status: accepted (M2). Owner: scheduling-service (`services/scheduling-service`).
- Inputs: [`characterization/ODDITIES.md`](../../characterization/ODDITIES.md) (ODD-01..ODD-13), the M1 transcripts in
  `characterization/transcripts/`, [`docs/as-is/hidden-behaviours.md`](../as-is/hidden-behaviours.md) (HB-xx) and
  [`docs/as-is/migration-risks.md`](../as-is/migration-risks.md) (MR-xx).
- Code: plain Java in `services/scheduling-service/src/main/java/com/northwind/scheduling/domain/` (no Spring), unit
  tested in `RulesTest` / `SessionCapacityTest`, end to end in `SchedulingApiIT`.

## Context

Seat inventory is owned by four codebases in the legacy estate: T-SQL procs (`db/procs/020..023`), triggers
(`db/triggers/032_trg_Booking_SeatSync.sql`, `033_trg_ExamSession_Update.sql`), booking-api Java (`BookingService`,
`RetakeEligibilityService`, `SeatCounter`, `DateUtils.addWorkingDays`), capacity-sync (`CapacitySyncService`) and the
portal C# (`RetakeRules.cs`, `SeatCounter.cs`, `AccommodationCodes.cs`, `BookingService.cs`, `EligibilityService.cs`).
M1 recorded where they disagree. scheduling-service owns its own `Scheduling` database; every rule below is decided
once. "Keep" = the modern service reproduces the legacy behaviour; "Change" = deliberate difference.

The service derives seat counts from booking rows on every read (`Capacity - count(BOOKED, ATTENDED)`, floored at 0)
and serialises writers per session (`UPDLOCK, HOLDLOCK` on `exam_session`). There is no `SeatsAvailable` counter to
drift, which is what settles ODD-01, ODD-05, ODD-10 (seat part) and ODD-13.

## Decisions

| Id | Ticket item | Decision | Rule in the modern service | Transcripts | Source (legacy) | Modern code |
|---|---|---|---|---|---|---|
| ODD-01 | 1. partner double decrement | **Change** | Every booking takes exactly one seat, whatever the channel (`PORTAL`, `PARTNER`). The API transcripts still match because they only show the booking response; the DB-level drift (11 -> 9) is not reproduced. | portal `BK-01`, `CN-01`; api `BK-02`, `CN-02`, `AC-02`, `AC-04`, `RS-02`; `MX-01`, `MX-03` | `BookingDao.decrementSeatsAvailable` + `032_trg_Booking_SeatSync.sql` (HB-01, HB-04, MR-01) | `SessionCapacity`, `BookingRules` |
| ODD-02 | 2. capacity decrease bumps to waitlist | **Keep** | Lowering capacity below the active bookings demotes the **newest** BOOKED rows (BookedAt desc, BookingId desc) to WAITLISTED, appended after the last waitlist position in BookedAt order; nobody is promoted when capacity is raised again. Each change is recorded in `capacity_change` with the demoted booking ids (an improvement over the single legacy `CAPACITY` audit row). | portal `CP-03`, `CP-04`, `CP-07`; db `CP-09` | `033_trg_ExamSession_Update.sql`, `022_usp_ChangeCapacity.sql` (HB-05) | `CapacityChange` |
| ODD-03 | 3. two seat formulas | **Change** (`scheduling.rules.seat-formula: standard`) | Seats available = `Capacity - (BOOKED + ATTENDED)`, floored at 0 (the `db/checks/seat-drift.sql` formula, portal `SeatCounter.cs`). The capacity-sync formula (`Capacity - AccommodationCapacity - standard bookings`) is available as `capacity-sync` for comparison only. capacity-sync's `POST /sync/run` has nothing to repair and is not served by the modern service. | sync `MX-02`; `MX-01`, `MX-03` | `CapacitySyncService`, booking-api `SeatCounter` (HB-03) | `SeatFormula` |
| ODD-04 | 4. working vs calendar retake days | **Keep portal rule** (`scheduling.rules.retake-days: calendar`) | Earliest retake = last failed attempt date + `RetakeWaitDays` **calendar** days (portal `RetakeRules.cs` `AddDays`). The modern API therefore matches the portal answers and differs from booking-api on `RT-01..RT-07`, `RT-15` (e.g. NW00000113: 2026-12-07, not 2026-12-18). `working` reproduces `DateUtils.addWorkingDays` and is unit tested. Passed attempt -> not eligible (`ALREADY_PASSED`), no attempt -> eligible (`FIRST_ATTEMPT`). Why calendar: it is what candidates see today in the portal, the published rule text says "30 days", and it is the more permissive of the two, so no candidate who can book today loses that ability. | `RT-01`..`RT-15` | `RetakeRules.cs`, `RetakeEligibilityService`, `DateUtils.addWorkingDays` (HB-07, HB-08, MR-07) | `RetakeEligibilityPolicy`, `RetakeDayCounting` |
| ODD-05 | 5. reschedule seat movement | **Change** | Reschedule = cancel + book in one transaction, locking both sessions in id order. Exactly one seat moves on every channel (the portal behaviour), and the response carries a new booking id. The old row is CANCELLED with reason `Rescheduled`. | portal `RS-01`, `RS-10`; api `RS-02`, `RS-09` | `023_usp_RescheduleBooking.sql`, booking-api `BookingService.reschedule` (HB-11) | `ReschedulePolicy`, `SchedulingService.reschedule` |
| ODD-06 | 6. accommodation floor on capacity reductions | **Keep** (`scheduling.rules.accommodation-floor: allocated`) | Capacity below `AccommodationCapacity` (or negative) is rejected with `INVALID_CAPACITY` (HTTP 409, legacy code 50005 = state 5), and nothing changes. `booked` (floor = extra-time seats in use) is opt-in and not legacy behaviour. Equal to the allocation is allowed (`CP-08`). | portal `CP-05`, `CP-08`; db `CP-06` | `022_usp_ChangeCapacity.sql` (state 5; PR 51 not in `legacy-baseline`), HB-14 | `AccommodationFloorPolicy`, `CapacityChange` |
| ODD-07 | 6a. blank accommodation code | **Keep API meaning** | Blank / missing code = no accommodation (stored NULL, no accommodation seat used), as in booking-api. The portal's silent fallback to the profile default moves into the UI, which shows the code explicitly. Unknown codes -> `VALIDATION` 400. `ET25`/`ET50` consume accommodation seats, `SR`/`RB` never do (`AC-01..AC-04`, `AC-07`, `AC-08`). | portal `AC-05`; api `AC-06` | booking-api `StringUtils.trimToNull`, portal `AccommodationCodes.cs` / `BookingService.cs` (HB-09) | `AccommodationCodes` |
| ODD-08 | 6b. reschedule onto the same session | **Keep API rule** | Refused on every channel with `SAME_SESSION` (409). The portal's cancel + re-book on the same session (new id, seat released and re-taken) is not reproduced. | portal `RS-05`; api `RS-06` | booking-api `BookingService.reschedule`, `023_usp_RescheduleBooking.sql` (HB-10, HB-11) | `ReschedulePolicy` |
| ODD-09 | - | **Keep** | Cancelling a BOOKED seat frees it but promotes nobody. Cancelling a WAITLISTED booking deletes its waitlist entry, and the other positions are not renumbered. A second cancel -> `NOT_CANCELLABLE` (409 / 50004). Automatic promotion is a product decision for a later story. | portal `CN-05`, `CN-03`, `CN-07`; api `CN-06`, `CN-04`, `CN-08` | `021_usp_CancelBooking.sql` (HB-06) | `BookingRules.checkCancellable`, `SchedulingService.cancel` |
| ODD-10 | - | **Change** | A rescheduled booking keeps the original `channel`, `external_ref` and accommodation code, so partner references (PX-3008) survive. | portal `RS-08` | `023_usp_RescheduleBooking.sql` (HB-10) | `ReschedulePolicy`, `SchedulingService.reschedule` |
| ODD-11 | - | **Change (mapping)** | Errors carry a symbolic `error` and `legacyCode = 50000 + proc state`: `SESSION_NOT_OPEN` 50001, `DUPLICATE_BOOKING` 50002, `NO_ACCOMMODATION_SEAT` 50003, `NOT_CANCELLABLE` 50004, `INVALID_CAPACITY` 50005, `DIFFERENT_EXAM` 50006. This is the documented 5000x table the procs never raised. | db `BK-07`, `CP-06`, `RS-07` | `db/procs/020..023` (HB-14) | `ErrorCode`, `web/ApiExceptionHandler` |
| ODD-12 | - | **Change** | Every channel gets booking-api's shape: HTTP 409 `{error, message, legacyCode}` (404 / 400 for not found / validation), never an HTTP 200 page with an alert. The UI shows these as toasts and inline errors. | portal `BK-05`, `BK-08`, `BK-10`, `CP-05`, `RS-03`; api `BK-06`, `BK-09`, `BK-11`, `RS-04` | portal controllers vs booking-api (HB-14) | `web/ApiExceptionHandler` |
| ODD-13 | - | **Change** | No seat leak and no negative counter, because seats are derived. A booking is WAITLISTED only when the derived count says the session is full. | api `CN-02`, `AC-04`; portal `RS-08`; `MX-01` | ODD-01 mechanics (HB-02, MR-02) | `SessionCapacity` |

Behaviour that both channels already share is kept as recorded: the full-session waitlist with the next position
(`BK-03`, `BK-04`); CLOSED and CANCELLED sessions are "not open" (`BK-08`..`BK-11`); duplicate active booking per
candidate and session (`BK-05`, `BK-06`); validation order session open -> duplicate -> accommodation seat -> retake
eligibility -> booked or waitlisted (`020_usp_BookSeat.sql`, `BookingService.book`).

## Replay against the modern service

`characterization/replay --target modern --base-url http://localhost:8090 --compare http --reseed command --reseed-cmd services/scheduling-service/scripts/reseed-scheduling-db.sh`
(the reseed script restores the AssessmentDelivery seed backup as `sa` and then re-imports through the read-only
login). The result is in the PR. Every failing transcript falls into one of the groups below.

| Group | Transcripts | Why they fail on :8090 | Link |
|---|---|---|---|
| A. portal channel | all `*-portal-*` (BK-01/03/05/08/10, CN-01/03/05/07, RS-01/03/05/08/10, CP-01..05/07/08, AC-01/03/05/07, RT-14) and the portal steps of `RT-01..RT-13` | They drive MVC routes (`/Booking/Book`, `/Admin/...`) that the modern service does not serve. Its UI is the SPA at `/app`, verified by the Playwright e2e. | [FU-1](#follow-ups) |
| B. db channel with portal setup | `BK-07`, `RS-07` | Their setup step books through the portal route (404) before calling the procs. | [FU-1](#follow-ups) |
| C. capacity-sync | `MX-01`, `MX-02`, `MX-03` | They call `POST /sync/run` and the `seat-drift.sql` check. The modern service has no counter to sync. | ODD-03 / [FU-3](#follow-ups) |
| D. retake rule | API steps of `RT-01..RT-07`, `RT-15` | The modern service applies calendar days and booking-api applied working days (e.g. eligible on 2026-12-07 instead of 2026-12-18). | ODD-04 (decision) |
| E. setup written to legacy after reseed | API steps of `RT-08..RT-11` | Their setup inserts NW00000950's failed result into AssessmentDelivery after the reseed command ran, so the snapshot importer never sees it. | [FU-2](#follow-ups) |

The transcripts that pass are the booking-api channel for booking, cancel, reschedule and accommodation (`BK-02`,
`BK-04`, `BK-06`, `BK-09`, `BK-11`, `CN-02`, `CN-04`, `CN-06`, `CN-08`, `RS-02`, `RS-04`, `RS-06`, `RS-09`, `AC-02`,
`AC-04`, `AC-06`, `AC-08`) plus `CP-06`, `CP-09`. They are served by the `/api/v1` facade (`web/LegacyApiV1Controller`)
on top of the same domain rules.

## Follow-ups

- **FU-1 (portal-channel replay, M3/M6):** replay the portal transcripts through the M3 routing gateway
  (`GET /api/v2/internal/routing/resolve`), or teach the harness a `modern-ui` channel that maps `portal_book` /
  `portal_cancel` / `portal_reschedule` / `admin_capacity` onto `/api/v2`. Until then they are covered functionally by
  `SchedulingApiIT` and the Playwright e2e (steps 1-7).
- **FU-2 (harness ordering):** for `--target modern`, the harness should run SQL setup before the reseed command (or run
  the reseed command again after setup) so that legacy-side fixtures reach the snapshot importer. Affects
  `RT-08..RT-11`. Calling `POST /api/v2/internal/import/sessions?mode=reset` after setup fixes them manually.
- **FU-3 (retire capacity-sync):** once M4 makes scheduling-service the single writer, capacity-sync and
  `ExamSession.SeatsAvailable` can be removed. `MX-*` should then be re-recorded against the derived seat counts.
