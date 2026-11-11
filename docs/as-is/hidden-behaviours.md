# Hidden behaviours: Northwind Assessment Network legacy estate

Behaviours that a reader of `README.md` and `docs/SYSTEM_OVERVIEW.md` would not expect. All `path:line` references are to tag
`legacy-baseline` (commit `e458238`). Each entry names a runnable repro; the observed output for each repro, captured on a Windows
Server 2022 VM running `scripts\run-legacy.ps1` (SQL Server 2022 Express, JDK 8, IIS Express), is pasted into the deep dive it links to.

Abbreviations: `BA/` = `services/booking-api/src/main/java/com/northwind/assessment/booking/`,
`RS/` = `services/results-service/src/main/java/com/northwind/ad/results/`,
`CS/` = `services/capacity-sync/src/main/java/com/northwind/assessment/capacity/`.
Repro paths are relative to `docs/as-is/repros/`. Deep dives: [portal](deep-dives/portal.md), [java-services](deep-dives/java-services.md),
[sql](deep-dives/sql.md), [runtime-repros](deep-dives/runtime-repros.md).

Running the repros: rebuild with `scripts\run-legacy.ps1` (no `-SkipDb`), then `powershell -File docs\as-is\repros\sql\run-all.ps1`
(every SQL repro rolls back), `"$BASH" docs/as-is/repros/java-services/run-all.sh` (needs the three services), the cross-component PowerShell scripts in
`repros/runtime-repros/` and the portal helpers in `repros/portal/portal-repro.sh`. The Java and portal repros write rows; reseed before repeating them.

## Summary

| ID | Behaviour | Components | Severity | Repro |
|---|---|---|---|---|
| HB-01 | A partner booking takes two seats from `SeatsAvailable` | booking-api, trigger 032 | High | `sql/02_partner_double_decrement.sql`, `java-services/01-partner-double-decrement.sh`, `runtime-repros/01-book-portal-vs-partner.ps1` |
| HB-02 | Partner book + cancel leaks a seat; the counter goes negative and free seats are waitlisted | booking-api, procs | High | `java-services/01-partner-double-decrement.sh`, `java-services/03-negative-seats-phantom-full.sh`, `runtime-repros/02-partner-fill-and-race.ps1` |
| HB-03 | Three different seat formulas; capacity-sync rewrites counters with its own and creates drift | capacity-sync, trigger 033, checks | High | `sql/11_capacity_sync_formula.sql`, `java-services/09-capacity-sync-formula.sh`, `runtime-repros/04-capacity-sync.ps1` |
| HB-04 | `trg_Booking_SeatSync` only reacts to INSERT of BOOKED non-PORTAL rows; its behaviour is driven by `booking.channel` config | trigger 032, booking-api config | Medium | `sql/03_seatsync_gaps.sql` |
| HB-05 | Lowering capacity silently demotes the newest confirmed bookings to WAITLISTED | trigger 033, `usp_ChangeCapacity`, portal admin | High | `sql/04_capacity_reduction_demotes.sql`, portal deep dive, `runtime-repros/03-capacity-below-booked.ps1` |
| HB-06 | Freed seats never promote the waitlist; waitlist positions develop gaps | `usp_CancelBooking` | Medium | `sql/05_no_waitlist_promotion.sql`, `runtime-repros/08-reschedule-cancel-accommodation-race.ps1` |
| HB-07 | Retake wait is 30 calendar days in the portal and 30 working days in booking-api | portal, booking-api | High | `java-services/07-retake-working-days.sh`, portal deep dive, `runtime-repros/05-retake-rule-divergence.ps1` |
| HB-08 | `usp_BookSeat` trusts its caller for retake rules and accommodation codes | `usp_BookSeat` | High | `sql/07_bookseat_rules.sql` |
| HB-09 | Blank accommodation means "profile default" in the portal and "none" in booking-api | portal, booking-api | Medium | `java-services/06-default-accommodation.sh`, portal deep dive, `runtime-repros/08-reschedule-cancel-accommodation-race.ps1` |
| HB-10 | Portal reschedule rewrites partner bookings as PORTAL, drops `ExternalRef`, can give up a confirmed seat and issues a new booking id | portal, `usp_RescheduleBooking` | High | `sql/06_reschedule.sql`, portal deep dive |
| HB-11 | Partner reschedule bypasses `usp_RescheduleBooking`: two seats on the target, re-checks retake, copies `ExternalRef` | booking-api | Medium | `java-services/02-partner-reschedule.sh`, `runtime-repros/08-reschedule-cancel-accommodation-race.ps1` |
| HB-12 | `ExternalRef` is stored but is not an idempotency key | booking-api, schema | Medium | `java-services/05-idempotency-externalref.sh` |
| HB-13 | Session search: booking-api returns every status (500 oldest rows); portal offers started sessions and inactive centres | booking-api, portal | Medium | `java-services/08-session-list-not-open.sh`, portal deep dive |
| HB-14 | Documented error codes 50001-50003 never occur; codes are RAISERROR states, system errors are re-raised as user errors, and each surface renders them differently | procs, portal, Java | Medium | `sql/01_error_numbers.sql`, `runtime-repros/07-time-and-invalid-input.ps1` |
| HB-15 | A failing proc rolls back the caller's whole transaction | procs | Medium | `sql/06b_reschedule_error_path.sql` |
| HB-16 | `ExamSession.LastModified` is always overwritten; `OUTPUT` without `INTO` fails on trigger-bearing tables | triggers | Medium | `sql/08_lastmodified_and_output.sql` |
| HB-17 | Audit trail is partial and its `Actor` follows four different conventions | procs, Java, portal | Medium | `sql/09_audit_actor_and_coverage.sql`, `runtime-repros/08-reschedule-cancel-accommodation-race.ps1` |
| HB-18 | Results can be ingested for sessions that have not happened; `Passed` is frozen at ingest | results-service, schema | Medium | `java-services/10-results-ingest.sh`, `sql/10_result_passed_frozen.sql`, `runtime-repros/06-results-ingest.ps1` |
| HB-19 | Score-file upload commits row by row and returns HTTP 200 on partial failure, with strict undocumented parsing | results-service | Medium | `java-services/11-score-file-upload.sh`, `runtime-repros/06-results-ingest.ps1` |
| HB-20 | capacity-sync keeps `/sync/last` in memory, skips sessions on a lost compare-and-set, swallows audit failures and reports failures as 200 | capacity-sync | Medium | `java-services/12-sync-last-restart.sh` |
| HB-21 | Duplicate protection is per session only: a candidate can hold several active bookings for one exam | procs, booking-api | Medium | portal deep dive |
| HB-22 | Any booking can be cancelled by id, including on delivered sessions, and the past session's counter goes up | portal, `usp_CancelBooking` | Medium | portal deep dive |
| HB-23 | Portal capacity `[Range(0, 500)]` is never enforced server-side; capacity is not capped by room seats | portal admin | Low | portal deep dive |
| HB-24 | Deactivating a centre is a direct EF update with no audit row and no effect on its open sessions | portal admin | Low | portal deep dive |
| HB-25 | Time handling mixes centre-local `StartsAt`, server-local "today", JVM-zone crons/parsing and invariant-culture query strings | all | Medium | portal deep dive, `runtime-repros/07-time-and-invalid-input.ps1` |
| HB-26 | Unhandled portal errors render the ASP.NET error page with stack trace to local callers; `debug="true"` in base config | portal | Low | portal deep dive |
| HB-27 | `Portal.*` settings imply features (kiosk mode, results embargo) that do not exist | portal | Low | portal deep dive |
| HB-28 | The schema enforces none of the business invariants and the shared `ad_app` login can bypass every proc | schema, security | High | `sql/12_missing_constraints.sql`, `sql/13_apply_permissions.sql` |
| HB-29 | Partner booking path is read-check-write without locks: concurrent requests get duplicate waitlist positions | booking-api | High | `runtime-repros/02-partner-fill-and-race.ps1` (part b), `java-services/04-race-duplicate.sh` |
| HB-31 | Nothing closes sessions: started sessions stay OPEN and both channels book them | all | High | `runtime-repros/07-time-and-invalid-input.ps1` |
| HB-30 | `db/apply.*` drop the database on every run, and the seed is frozen in time with two anchors | db scripts, seed | Medium | `sql/14_seed_fingerprint.sql` |

## Details

### HB-01 A partner booking takes two seats
- **Where:** `BA/service/BookingService.java:113-116` and `BA/dao/SessionDao.java:82-83` insert the booking and subtract 1; `db/triggers/032_trg_Booking_SeatSync.sql:18-24` subtracts 1 again for every non-PORTAL BOOKED insert. The portal path (`db/procs/020_usp_BookSeat.sql:56-61`) inserts `Channel='PORTAL'`, so the trigger skips it.
- **What happens:** one partner booking moves `SeatsAvailable` by -2. Observed on session 187: `Capacity 10, SeatsAvailable 5` -> `3` with 6 BOOKED rows (expected 4). The admin screen shows the counter next to a row recount, so the columns disagree (`AssessmentDelivery.Services/Admin/AdminService.cs:112,123-124`).
- **Migration impact:** the counter is not a source of truth. Porting either the trigger or the Java decrement alone changes availability.
- **Repro:** `sql/02_partner_double_decrement.sql`; `java-services/01-partner-double-decrement.sh` (live via `POST :8081/api/v1/bookings`).

### HB-02 Book + cancel leaks a seat; negative counters and phantom-full sessions
- **Where:** cancel gives back only 1 via `db/procs/021_usp_CancelBooking.sql:36-38` (called from `BA/dao/BookingDao.java:90-92`). The booking decision reads only `SeatsAvailable > 0` (`BA/service/BookingService.java:110-123`, `db/procs/020_usp_BookSeat.sql:54`, `AssessmentDelivery.Web/Views/Booking/Index.cshtml:77`).
- **What happens:** each partner book/cancel pair leaks one seat; the counter reaches 0 or below while seats are physically free, and later candidates are WAITLISTED on both channels.
- **Repro:** `java-services/03-negative-seats-phantom-full.sh`.

### HB-03 Three seat formulas; capacity-sync introduces drift
- **Where:** seed and `db/checks/seat-drift.sql:18-26` use `Capacity - (BOOKED + ATTENDED)`; trigger `db/triggers/033_trg_ExamSession_Update.sql:66-68` recounts `Capacity - BOOKED`; capacity-sync uses `Capacity - AccommodationCapacity - non-ET (BOOKED + ATTENDED)` (`CS/CapacitySyncService.java:29-34,59-67`), with a compare-and-set update that auto-commits per session.
- **What happens:** a `POST :8083/sync/run` on a clean seed took `seat-drift.sql` from 5 to 112 rows. The 02:00 job (`CS/NightlySyncJob.java:14`) therefore re-introduces drift every night.
- **Repro:** `sql/11_capacity_sync_formula.sql`, `java-services/09-capacity-sync-formula.sh`.

### HB-04 `trg_Booking_SeatSync` coverage gaps and config coupling
- **Where:** `db/triggers/032_trg_Booking_SeatSync.sql:12-24` (AFTER INSERT only; BOOKED and `Channel <> 'PORTAL'`); `Channel` defaults to `PORTAL` (`db/schema/010_tables.sql:85`); booking-api inserts `booking.channel` from config (`services/booking-api/src/main/resources/application.properties:16`, `BA/service/BookingService.java:114,119`).
- **What happens:** status updates (cancel, ATTENDED, demotion) never touch the trigger; IMPORT inserts would decrement although no code writes them; setting `booking.channel=PORTAL` silently turns the double decrement into a single one.
- **Repro:** `sql/03_seatsync_gaps.sql`.

### HB-05 Capacity reduction demotes confirmed bookings
- **Where:** `AssessmentDelivery.Services/Admin/AdminService.cs:78-79` -> `db/procs/022_usp_ChangeCapacity.sql:31-36` -> `db/triggers/033_trg_ExamSession_Update.sql:24-71` (header `033:1-4` documents only `LastModified`).
- **What happens:** when capacity falls below the BOOKED count, the newest BOOKED rows become WAITLISTED, get waitlist rows and `SeatsAvailable` is recounted; no candidate notice, no audit row per booking.
- **Repro:** `sql/04_capacity_reduction_demotes.sql`; portal admin `/Admin/Sessions/Capacity/{id}` steps in the portal deep dive.

### HB-06 No waitlist promotion; position gaps
- **Where:** `db/procs/021_usp_CancelBooking.sql:2-4,36-41` (comment says promotion happens "from the admin screen"; no such code exists in `AssessmentDelivery.Services/Admin/AdminService.cs`).
- **What happens:** a cancel frees a seat while WAITLISTED candidates stay waitlisted; deleting a waitlist row leaves gaps in `Position`.
- **Repro:** `sql/05_no_waitlist_promotion.sql`.

### HB-07 Retake wait: calendar days vs working days
- **Where:** `AssessmentDelivery.Business/Rules/RetakeRules.cs:24-25` (`Max(SessionDate).Date.AddDays(30)`) vs `BA/service/RetakeEligibilityService.java:49-53` + `BA/util/DateUtils.java:67-79` (`addWorkingDays`, Mon-Fri, first row of a `StartsAt DESC` query at `BA/dao/ResultDao.java:29`).
- **What happens:** NW00000113 failed HPI-ACC1 on Sat 2026-11-07. Portal `/Eligibility?...&date=2026-12-07` says eligible (earliest 07 Dec); `GET :8081/api/v1/candidates/NW00000113/eligibility?examCode=HPI-ACC1&date=2026-12-07` says `RETAKE_WAIT_NOT_ELAPSED`, earliest 2026-12-18.
- **Repro:** `java-services/07-retake-working-days.sh`; portal side in the portal deep dive.

### HB-08 `usp_BookSeat` trusts the caller
- **Where:** `db/procs/020_usp_BookSeat.sql:9,20,40-52` (no `Result` access, no accommodation code validation); `db/schema/010_tables.sql:84` (no CHECK on `AccommodationCode`).
- **What happens:** any caller of the proc (or a new channel) can book a candidate inside the retake wait or with an invalid accommodation code; only C# (`AssessmentDelivery.Services/Booking/BookingService.cs:106`) and Java (`BA/service/RetakeEligibilityService.java:60`) enforce the rules.
- **Repro:** `sql/07_bookseat_rules.sql`.

### HB-09 Blank accommodation: profile default vs none
- **Where:** `AssessmentDelivery.Services/Booking/BookingService.cs:100-101` vs `BA/service/BookingService.java:62-68`.
- **What happens:** NW00000003 (profile ET25) booked with a blank code got `ET25/PORTAL` from the portal and `NULL/PARTNER` from booking-api. The portal cannot book an ET candidate without accommodation.
- **Repro:** `java-services/06-default-accommodation.sh`; portal POST in the portal deep dive.

### HB-10 Portal reschedule rewrites partner bookings
- **Where:** `AssessmentDelivery.Services/Booking/BookingService.cs:154-155,176-177` -> `db/procs/023_usp_RescheduleBooking.sql:23-41` -> `db/procs/020_usp_BookSeat.sql:56-57` (hard-coded `PORTAL`, no `ExternalRef`).
- **What happens:** a rescheduled PARTNER booking comes back as a new PORTAL booking id with `ExternalRef` lost; the target list includes sessions that started earlier today and full sessions, and moving into a full session cancels the confirmed seat and waitlists the candidate; the proc also accepts the same session.
- **Repro:** `sql/06_reschedule.sql`; portal deep dive.

### HB-11 Partner reschedule bypasses the proc
- **Where:** `BA/service/BookingService.java:147-176` calls `usp_CancelBooking` and then the ordinary partner booking path.
- **What happens:** a PORTAL booking rescheduled through the API comes back as PARTNER (observed: 3616 -> 3618 `channel=PARTNER`, Drift=1 on the target), the target session loses two seats (HB-01), the retake rule is re-evaluated against the new date (a partner can be refused a reschedule the portal would allow), `ExternalRef` is copied to a new booking id, and the two steps are not atomic. Unlike the proc, Java rejects the same session.
- **Repro:** `java-services/02-partner-reschedule.sh`.

### HB-12 `ExternalRef` is not an idempotency key
- **Where:** `db/schema/010_tables.sql:86` (`ExternalRef VARCHAR(40) NULL`, no unique index); `BA/service/BookingService.java:69-71` only stores it.
- **What happens:** a partner retry with the same `externalRef` on another session creates a second booking; a retry on the same session is refused only by the per-session duplicate check (409).
- **Repro:** `java-services/05-idempotency-externalref.sh`.

### HB-13 Session search differs by channel
- **Where:** `BA/dao/SessionDao.java:30,35-56` (no status filter, `MAX_ROWS = 500`, ordered oldest first); `AssessmentDelivery.Services/Booking/BookingService.cs:28-46,90-98` (status only; no start-time or centre `IsActive` filter).
- **What happens:** partners see DELIVERED/CANCELLED sessions and never see later sessions once 500 older rows match; booking a non-OPEN session is then refused by the proc. The portal lists sessions that already started today and sessions at deactivated centres, and books them.
- **Repro:** `java-services/08-session-list-not-open.sh`; portal deep dive.

### HB-14 Error codes and error rendering
- **Where:** header comments `db/procs/020_usp_BookSeat.sql:5-8`, `db/procs/023_usp_RescheduleBooking.sql:5` document 50001-50003; actual raises are `RAISERROR(..., 16, n)` (message 50000, state 1-6) at `020:38,43,51`, `021_usp_CancelBooking.sql:30`, `022_usp_ChangeCapacity.sql:29`, `023:31`; CATCH blocks re-raise every error as 50000 (`020:85-87`, `021:49-51`). Portal maps by message text (`AssessmentDelivery.Services/Common/SqlErrorHelper.cs:25`, shows the surrogate `CandidateId` from `020:43`); Java maps `SQLException` to raw messages (`BA/web/ApiExceptionHandler.java:33-36`).
- **What happens:** callers cannot distinguish business errors from deadlocks or constraint violations; the same rule produces different text/status codes per channel.
- **Repro:** `sql/01_error_numbers.sql`.

### HB-15 Proc failure rolls back the caller's transaction
- **Where:** `020:85`, `021:49`, `022:46`, `023:44` (`IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION` with `SET XACT_ABORT ON`).
- **What happens:** a caller that opened its own transaction (for example the nested cancel inside `usp_RescheduleBooking`) gets Msg 266/3903 and loses all earlier work.
- **Repro:** `sql/06b_reschedule_error_path.sql`.

### HB-16 `LastModified` and `OUTPUT`
- **Where:** `db/triggers/033_trg_ExamSession_Update.sql:16-22`; triggers on `Candidate`, `Exam`, `Booking`, `ExamSession` (`030:13-14`, `031:10-11`, `032:12-13`, `033:10-11`); seed workaround `db/seed/049_seed_finalise.sql:12-13,26`.
- **What happens:** any value written to `ExamSession.LastModified` is replaced with `SYSDATETIME()`; an `INSERT/UPDATE ... OUTPUT` without `INTO` fails on all four tables (relevant to EF Core and other ORMs that use `OUTPUT`).
- **Repro:** `sql/08_lastmodified_and_output.sql`.

### HB-17 Partial audit trail with mixed actors
- **Where:** `db/schema/010_tables.sql:127` (`Actor DEFAULT SUSER_SNAME()`); `020:79-80` (no actor -> `ad_app`/login); `AssessmentDelivery.Services/Booking/BookingService.cs:139` (`'portal'`); `AssessmentDelivery.Services/Admin/AdminService.cs:78-79` (`'portal-admin'`); `BA/dao/BookingDao.java` (`'booking-api'`); `023:37` (no actor on reschedule cancel); `CS/CapacitySyncService.java:96-101` (`EntityId 0`, exceptions swallowed); no audit for centre edits (`AdminService.cs:50-59`) or result ingest.
- **Repro:** `sql/09_audit_actor_and_coverage.sql`.

### HB-18 Results for future sessions; `Passed` frozen
- **Where:** `RS/service/ResultIngestService.java:42-62,75-76` checks only booking status (BOOKED or ATTENDED), computes `Passed` from the current pass mark and sets the booking ATTENDED; `db/schema/010_tables.sql:106-117` stores `Passed` with no link to `Score`/`PassMark`.
- **What happens:** a result for a session weeks in the future is accepted (201) and the booking becomes ATTENDED; changing an exam's pass mark later does not change stored outcomes.
- **Repro:** `java-services/10-results-ingest.sh`, `sql/10_result_passed_frozen.sql`.

### HB-19 Score-file upload semantics
- **Where:** `RS/service/ScoreFileImporter.java:31-66` (one transaction per row, returns 200 with per-row errors); `RS/service/CsvRowParser.java:18-65` (header required, fixed column order, integer scores, `yyyy-MM-dd` only).
- **What happens:** a file with one bad row imports the other rows and returns HTTP 200; retrying the file creates duplicate-row errors for the rows that succeeded.
- **Repro:** `java-services/11-score-file-upload.sh`.

### HB-20 capacity-sync run state
- **Where:** `CS/CapacitySyncService.java:42-44` (`volatile SyncRun lastRun`), `:65-67` (compare-and-set update; 0 rows -> silently skipped), `:77-79` (failures recorded but HTTP 200), `:96-101` (audit failures swallowed).
- **What happens:** `/sync/last` is empty after every restart and cannot show history across instances; a concurrent booking makes the sync skip that session without a log line.
- **Repro:** `java-services/12-sync-last-restart.sh`.

### HB-21 Duplicate protection per session only
- **Where:** `020:40-43`, `BA/service/BookingService.java:85` + `BA/dao/BookingDao.java:52-55` check `SessionId + CandidateId`; nothing checks the exam.
- **What happens:** a candidate can hold active bookings on several sessions of the same exam on either channel.

### HB-22 Cancel any booking by id, including delivered sessions
- **Where:** `AssessmentDelivery.Web/Controllers/BookingController.cs:91-104` (no ownership check); `021:24-30` checks only `Status IN ('BOOKED','WAITLISTED')`.
- **What happens:** anyone posting `/Booking/Cancel/{id}` cancels another candidate's booking; cancelling on a DELIVERED session increments its seat counter.

### HB-23 Capacity range not enforced
- **Where:** `AssessmentDelivery.Web/Models/AdminModels.cs:20-22` vs `AssessmentDelivery.Web/Controllers/AdminController.cs:70-82` (binds `int? newCapacity`, no `ModelState.IsValid`); `022` checks only `>= 0`.
- **What happens:** capacity 9999 is accepted and exceeds the room's seat count.

### HB-24 Centre deactivation
- **Where:** `AssessmentDelivery.Services/Admin/AdminService.cs:50-59`; no trigger on `TestCenter`.
- **What happens:** no audit row; open sessions at the centre remain bookable (HB-13).

### HB-25 Time handling
- **Where:** `AssessmentDelivery.Data/DataClasses/Entities/ExamSession.cs:18-19` (centre-local `StartsAt`, no zone column); `AssessmentDelivery.Web/Controllers/EligibilityController.cs:28` (server-local today); `AssessmentDelivery.Web/Web.config:34` (globalization); `BA/util/DateUtils.java:47,61` (JVM default zone); `CS/NightlySyncJob.java:14` (cron in JVM zone).
- **What happens:** "today", retake dates and the nightly run depend on the host time zone; centres in other zones are compared against server-local midnight.

### HB-26 Error pages
- **Where:** `AssessmentDelivery.Web/Web.config:31-33` (`customErrors mode="RemoteOnly"`, `compilation debug="true"`); only `Web.AzureRelease.config` turns customErrors On.

### HB-27 Dead `Portal.*` settings
- **Where:** `AssessmentDelivery.Web/Web.config:24-27` (`Portal.KioskMode`, `Portal.DefaultRegion`, `Portal.SupportEmail`, `Portal.ResultsReleaseDelayHours`) are read by no `.cs`/`.cshtml` file; results are visible immediately.

### HB-28 Schema and permissions
- **Where:** `db/schema/010_tables.sql:52-66,79-95,106-117` (no CHECKs on seats/capacity/accommodation/score, `RoomId` and `TestCenterId` independent FKs, no unique active booking); `db/schema/000_create_database.sql:13-25` (`ad_app` is `db_owner`-level, used by every component).
- **Repro:** `sql/12_missing_constraints.sql`, `sql/13_apply_permissions.sql`.

### HB-29 Partner path races
- **Where:** `BA/service/BookingService.java:110-120` (read `SeatsAvailable`, then insert); `BA/dao/BookingDao.java:54,62,84-85` (duplicate check, waitlist `MAX(Position)+1`) under default READ COMMITTED.
- **What happens:** 8 simultaneous `POST /api/v1/bookings` against 5 free seats on session 431 gave 4 BOOKED and 4 WAITLISTED; three waitlist rows got `Position=1` and `SeatsAvailable` ended at -3 with one real seat free. The same 6-way test through `usp_BookSeat` (portal path, `UPDLOCK, HOLDLOCK` at `db/procs/020_usp_BookSeat.sql:34`) serialised correctly. A separate 8-way run of the bash repro did not produce a duplicate booking, so duplicate *bookings* remain unproven; duplicate *positions* are reproduced.
- **Repro:** `runtime-repros/02-partner-fill-and-race.ps1` (part b); `java-services/04-race-duplicate.sh`.

### HB-30 Destructive apply and frozen seed
- **Where:** `db/apply.ps1:44`, `db/apply.sh:42` (`SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE`) vs `db/README.md:16-17` ("safe to run as often as you like"); `scripts/run-legacy.ps1:247-250` runs it unless `-SkipDb`; seed anchors `db/seed/042_seed_sessions.sql:12-13` (`@AsOf = 20261111`) and `db/seed/043_seed_bookings.sql:13-14` (`@Cutoff = 20261110 18:00`).
- **What happens:** every default start wipes data; after the anchor dates pass, "future" seeded sessions become past and eligibility/booking repros shift.
- **Repro:** `sql/14_seed_fingerprint.sql`.

### HB-31 No session close-out
- **Where:** no code moves a session out of `OPEN`; `CS/NightlySyncJob.java:13` refers to "the portal's 01:00 session close-out", which does not exist in the repo. `db/procs/020_usp_BookSeat.sql:38` checks only `Status`; portal search (`AssessmentDelivery.Services/Booking/BookingService.cs:38,43`) and booking-api have no `StartsAt >= now` filter. `StartsAt` is zone-less centre-local time (`db/schema/010_tables.sql:57`) next to `TestCenter.TimeZone` (`010_tables.sql:26`).
- **What happens:** at 16:59 UTC a Toronto session that started at 13:30 local was still OPEN; booking-api (booking 3613) and the portal (booking 3614) both booked it.
- **Repro:** `runtime-repros/07-time-and-invalid-input.ps1`.
