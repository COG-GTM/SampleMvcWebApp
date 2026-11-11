# As-is deep dive: Northwind Assessment Network operator portal (.NET)

Source of truth: tag `legacy-baseline` (commit `e458238`). Every `path:line` below refers to that commit.
Runtime evidence was captured on 2026-11-11 on a Windows Server 2022 VM after a fresh
`powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1` (SQL Server 2022 Express, seeded
`AssessmentDelivery` database, three Java services on :8081-8083, IIS Express portal on :8080).
The VM clock/time zone was **UTC** and the OS culture `en-US`; SQL `GETDATE()` therefore returns UTC.

Several repros mutate the seed (cancellations, capacity changes, test bookings). Re-run
`scripts\run-legacy.ps1` (without `-SkipDb`) to reseed before repeating them.

## 1. Scope and architecture

### Components

| Layer | Project | Responsibility | Key evidence |
|---|---|---|---|
| UI | `AssessmentDelivery.Web` | ASP.NET MVC 5 controllers + Razor views, Autofac bootstrap, routes, `Web.config` | `AssessmentDelivery.Web/Global.asax.cs:21`, `AssessmentDelivery.Web/App_Start/RouteConfig.cs:13-21` |
| Service | `AssessmentDelivery.Services` | Use-case services (booking, admin, eligibility, results); calls stored procedures; maps RAISERROR to `PortalException` | `AssessmentDelivery.Services/Booking/BookingService.cs:90-128`, `AssessmentDelivery.Services/Common/SqlErrorHelper.cs:25` |
| Business | `AssessmentDelivery.Business` | Stateless rules: `RetakeRules`, `AccommodationCodes`, `SeatCounter` | `AssessmentDelivery.Business/Rules/RetakeRules.cs:18-31`, `AssessmentDelivery.Business/Rules/SeatCounter.cs:18-30` |
| Data | `AssessmentDelivery.Data` | EF6 code-first-over-existing-DB `AssessmentDeliveryDb`, 9 entities, initializer disabled | `AssessmentDelivery.Data/DataClasses/AssessmentDeliveryDb.cs:18`, `AssessmentDelivery.Data/Startup/DataLayerModule.cs:17` |
| Tests | `AssessmentDelivery.Tests` | NUnit 3 tests of the three static/stateless business rules only | `AssessmentDelivery.Tests/UnitTests/*.cs` |
| Script | `scripts/run-portal.ps1` | nuget restore, MSBuild, IIS Express on `-Port` (default 8080), polls `/Admin/Centers` | `scripts/run-portal.ps1:31-53` |

### Request / data flow

1. `Global.asax` registers routes/filters/bundles, installs `DiModelBinder` as default binder, then calls
   `WebUiInitialise.InitialiseThis` (`AssessmentDelivery.Web/Global.asax.cs:10-21`).
2. `WebUiInitialise` initialises service, business and data layers (`AssessmentDelivery.Services/Startup/ServiceLayerInitialise.cs:18-19`),
   builds the Autofac container (`AssessmentDelivery.Web/Infrastructure/WebUiInitialise.cs:35`) and sets the MVC resolver
   (`AssessmentDelivery.Web/Infrastructure/WebUiInitialise.cs:38`).
3. Autofac: `ServiceLayerModule` registers `DataLayerModule` and `BizLayerModule` and then every service interface by convention
   (`AssessmentDelivery.Services/Startup/ServiceLayerModule.cs:15-20`); controllers via `RegisterControllers`
   (`AssessmentDelivery.Web/Infrastructure/AutofacDi.cs:17-18`). `AssessmentDeliveryDb` is `InstancePerLifetimeScope` (one per request,
   `AssessmentDelivery.Data/Startup/DataLayerModule.cs:17`).
4. Controllers call services. **Reads** go through EF LINQ projections. **Seat-touching writes** go through
   `Database.ExecuteSqlCommand(TransactionalBehavior.DoNotEnsureTransaction, "EXEC dbo.usp_...")`
   (`AssessmentDelivery.Services/Booking/BookingService.cs:115-116,135-136,176-177`, `AssessmentDelivery.Services/Admin/AdminService.cs:78-79`).
   Centre edits are the only direct EF write (`AssessmentDelivery.Services/Admin/AdminService.cs:50-59`).
5. Stored procedures own transactions and locking (`db/procs/020_usp_BookSeat.sql:31-34`); triggers
   (`db/triggers/032_trg_Booking_SeatSync.sql`, `db/triggers/033_trg_ExamSession_Update.sql`) run as side effects of those writes
   and of Java JDBC writes to the same tables.
6. `SqlErrorHelper.TryTranslate` turns `SqlException` msg 50000 / severity 16 into `PortalException`
   (`AssessmentDelivery.Services/Common/SqlErrorHelper.cs:25`); controllers put `PortalException.Message` into `ModelState`
   (`AssessmentDelivery.Web/Controllers/BookingController.cs:60-63`). Anything else bubbles to `HandleErrorAttribute`
   (`AssessmentDelivery.Web/App_Start/FilterConfig.cs:9`).

Routes (`AssessmentDelivery.Web/App_Start/RouteConfig.cs:13-21`): `/` redirects to `/Admin/Centers`
(`AssessmentDelivery.Web/Controllers/HomeController.cs:5-11`); `Admin/Sessions/Capacity/{id}` is a dedicated route; everything else
is `{controller}/{action}/{id}`. POST actions carry `[ValidateAntiForgeryToken]` (`AssessmentDelivery.Web/Controllers/BookingController.cs:45-47,89-91,114-116`).

### Technology and versions

| Item | Version | Evidence |
|---|---|---|
| .NET Framework | 4.8 | `AssessmentDelivery.Web/Web.config:31-32` |
| ASP.NET MVC | 5.2.9 | `AssessmentDelivery.Web/packages.config:10` |
| Entity Framework | 6.4.4 | `AssessmentDelivery.Web/packages.config:7` |
| Autofac / Autofac.Mvc5 | 4.9.4 / 4.0.2 | `AssessmentDelivery.Web/packages.config:4-5` |
| NUnit / console runner | 3.13.3 / 3.16.3 | `AssessmentDelivery.Web/README.md:43` |
| Database | SQL Server, DB `AssessmentDelivery`, login `ad_app` | `AssessmentDelivery.Web/Web.config:12` |

### Configuration

- Connection string `AssessmentDelivery` (SQL auth, plaintext password, `MultipleActiveResultSets=True`) - `AssessmentDelivery.Web/Web.config:11-13`.
- `Portal.HostType` (`LocalHost`/`Azure`/`OnPrem`) selects the data-layer mode (`AssessmentDelivery.Web/Web.config:20`,
  `AssessmentDelivery.Web/Infrastructure/WebUiInitialise.cs:32`); `Portal.EnvironmentName` / `Portal.BuildNumber` are shown in the
  layout header (`AssessmentDelivery.Web/Infrastructure/PortalHtmlExtensions.cs:42-47`, `AssessmentDelivery.Web/Views/Shared/_Layout.cshtml:20`).
- `authentication mode="None"`, `compilation debug="true"`, `customErrors mode="RemoteOnly"`, `globalization culture="en-GB"` -
  `AssessmentDelivery.Web/Web.config:30-34`.
- Transforms: `Web.Debug.config` (customErrors Off), `Web.Release.config` (on-prem placeholders, `OnPrem`/`PROD`, debug removed),
  `Web.AzureRelease.config` (Azure SQL connection string, `Azure`, customErrors On).

### How it is started

`scripts\run-legacy.ps1` deploys/seeds the DB, builds and starts the Java services and then calls `scripts\run-portal.ps1`
(nuget restore -> MSBuild -> `iisexpress.exe /path:<Web> /port:8080`, waits for `GET /Admin/Centers` = 200). Observed:

```text
[run-legacy] portal up on :8080 (IIS Express pid 9088)
[run-legacy] all components up
$ curl -s -o /dev/null -w "8080:%{http_code}\n" http://localhost:8080/Admin/Centers
8080:200
```

Unit tests (run on the built output):

```text
$ packages/NUnit.ConsoleRunner.3.16.3/tools/nunit3-console.exe AssessmentDelivery.Tests/bin/Debug/AssessmentDelivery.Tests.dll --noresult
  Overall result: Passed
  Test Count: 8, Passed: 8, Failed: 0, Warnings: 0, Inconclusive: 0, Skipped: 0
```

## 2. Inventory

| Artifact | Path | LOC or count | Purpose | Depends on |
|---|---|---|---|---|
| Solution | `AssessmentDelivery.sln` | 63 LOC, 5 projects | Build unit for the portal | MSBuild, NuGet `packages/` |
| Controllers | `AssessmentDelivery.Web/Controllers/*.cs` | 5 files, 338 LOC (Admin 107, Booking 138, Eligibility 46, Home 13, Results 34) | MVC endpoints | Service interfaces |
| Views | `AssessmentDelivery.Web/Views/**/*.cshtml` | 14 files, 691 LOC | Razor UI (Bootstrap 3), fixed `dd MMM yyyy` formats | View models |
| Web infrastructure | `AssessmentDelivery.Web/Infrastructure/*.cs`, `App_Start/*.cs`, `Global.asax.cs` | part of 17 `.cs` / 702 LOC in Web | Autofac, `DiModelBinder`, routes, filters, HTML helpers | Autofac.Mvc5 |
| Web.config + transforms | `AssessmentDelivery.Web/Web*.config` | 84 + 7 + 17 + 18 LOC | Connection string, appSettings, auth/culture/errors | IIS / IIS Express |
| Services | `AssessmentDelivery.Services/**` | 20 files, 728 LOC | Booking, Admin, Eligibility, Results, `SqlErrorHelper`, `PortalException` | Data, Business, stored procs |
| Business rules | `AssessmentDelivery.Business/**` | 9 files, 201 LOC | `RetakeRules`, `AccommodationCodes`, `SeatCounter`/`BookingStatus` | none |
| Data | `AssessmentDelivery.Data/**` | 13 files, 403 LOC; 9 entities | `AssessmentDeliveryDb` + entities mapped with `[Table]` | EF 6.4.4, `db/schema/010_tables.sql` |
| Unit tests | `AssessmentDelivery.Tests/UnitTests/*.cs` | 3 test classes, 104 LOC, 8 test cases | Rules only | NUnit 3 |
| Start script | `scripts/run-portal.ps1` | 137 LOC | restore/build/IIS Express/health poll | nuget.exe, vswhere/MSBuild, IIS Express |
| Stored procs used | `db/procs/020..023_*.sql` | 4 procs | All seat-changing writes | triggers `032`, `033` |

## 3. Data ownership

| DB table | Op | Component | file:line | Via |
|---|---|---|---|---|
| ExamSession | R | Portal booking search / reschedule targets | `AssessmentDelivery.Services/Booking/BookingService.cs:28,155` | ORM |
| ExamSession | U (`SeatsAvailable` -1) | Portal book | `db/procs/020_usp_BookSeat.sql:61` (called from `AssessmentDelivery.Services/Booking/BookingService.cs:115`) | proc |
| ExamSession | U (`SeatsAvailable` +1) | Portal cancel / reschedule | `db/procs/021_usp_CancelBooking.sql:38` | proc |
| ExamSession | U (`Capacity`, `SeatsAvailable`) | Portal admin capacity | `db/procs/022_usp_ChangeCapacity.sql:33` (from `AssessmentDelivery.Services/Admin/AdminService.cs:78`) | proc |
| ExamSession | U (`SeatsAvailable` recomputed) | Side effect of any capacity decrease | `db/triggers/033_trg_ExamSession_Update.sql:66-68` | trigger |
| ExamSession | U (`SeatsAvailable` -1) | booking-api partner booking | `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:116` | JDBC |
| ExamSession | U (`SeatsAvailable` -n) | Side effect of non-PORTAL `BOOKED` insert | `db/triggers/032_trg_Booking_SeatSync.sql:19-23` | trigger |
| Booking | R | My bookings, admin counts | `AssessmentDelivery.Services/Booking/BookingService.cs:71`, `AssessmentDelivery.Services/Admin/AdminService.cs:125` | ORM |
| Booking | I (`Channel='PORTAL'`, no `ExternalRef`) | Portal book / reschedule | `db/procs/020_usp_BookSeat.sql:56,68` | proc |
| Booking | U (`CANCELLED`) | Portal cancel / reschedule | `db/procs/021_usp_CancelBooking.sql:32-34` | proc |
| Booking | U (`BOOKED`->`WAITLISTED`) | Side effect of capacity decrease | `db/triggers/033_trg_ExamSession_Update.sql:28-52` | trigger |
| Booking | I (`Channel='PARTNER'`) | booking-api | `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:114,119` | JDBC |
| Waitlist | I | Portal book when full | `db/procs/020_usp_BookSeat.sql:72-73` | proc |
| Waitlist | D | Portal cancel of waitlisted booking | `db/procs/021_usp_CancelBooking.sql:40` | proc |
| Waitlist | I | Capacity decrease demotion | `db/triggers/033_trg_ExamSession_Update.sql:60-64` | trigger |
| AuditLog | I (Actor = SQL login) | Portal book | `db/procs/020_usp_BookSeat.sql:79` | proc |
| AuditLog | I (Actor `portal`) | Portal cancel | `db/procs/021_usp_CancelBooking.sql:43-44`, `AssessmentDelivery.Services/Booking/BookingService.cs:139` | proc |
| AuditLog | I (Actor `portal-admin`) | Portal capacity | `db/procs/022_usp_ChangeCapacity.sql:38-41`, `AssessmentDelivery.Web/Controllers/AdminController.cs:82` | proc |
| TestCenter | R/U (`Name`, `IsActive`) | Portal admin centre edit | `AssessmentDelivery.Services/Admin/AdminService.cs:50-59` | ORM (no audit) |
| Candidate | R | Lookup by number (trim + upper) | `AssessmentDelivery.Services/Booking/BookingService.cs:64-68` | ORM |
| Exam | R | Published exams for search, `RetakeWaitDays` | `AssessmentDelivery.Services/Booking/BookingService.cs:56`, `AssessmentDelivery.Services/Eligibility/EligibilityService.cs:37` | ORM |
| Result | R | Eligibility attempts, results page | `AssessmentDelivery.Services/Eligibility/EligibilityService.cs:20-37`, `AssessmentDelivery.Services/Results/ResultsService.cs:21` | ORM |
| Room | R | Centre list counts | `AssessmentDelivery.Services/Admin/AdminService.cs:23-38` | ORM |

The portal never writes `Candidate`, `Exam`, `Result` or `Room`; those are owned by seed/import and the Java
results-service. The portal's only non-procedural write is `TestCenter`.

## 4. Hidden behaviours

Repro helpers used below (Git Bash on the VM; also saved as `docs/as-is/repros/portal/portal-repro.sh`):

```bash
q()    { sqlcmd -S localhost,1433 -E -C -d AssessmentDelivery -W -s"|" -Q "SET NOCOUNT ON; $1" < /dev/null; }
# post <GET page that renders the form> <POST url> key=value...  (fetches the anti-forgery cookie + token first)
post() { local g=$1 p=$2; shift 2; local B=http://localhost:8080
  local tok=$(curl -s -c cj.txt -b cj.txt "$B$g" | grep -o 'name="__RequestVerificationToken" type="hidden" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"$//')
  local a=(); for kv in "$@"; do a+=(--data-urlencode "$kv"); done
  curl -s -c cj.txt -b cj.txt -o last.html -w "HTTP %{http_code} -> %{redirect_url}\n" "$B$p" --data-urlencode "__RequestVerificationToken=$tok" "${a[@]}"
  grep -oE 'validation-summary-errors[^<]*<ul><li>[^<]*' last.html | sed 's/.*<li>/  error: /'; }
```

### Blank accommodation on the portal form silently applies the candidate's profile default (Java treats blank as "none")

- **Where:** `AssessmentDelivery.Services/Booking/BookingService.cs:101`; contrast `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:62`.
- **What happens:** when the Book form's accommodation drop-down is left blank the portal substitutes `Candidate.DefaultAccommodationCode`, so an `ET25`/`ET50` candidate consumes an accommodation seat (and can get "No accommodation seat available") without choosing it, and there is no way to book them *without* accommodation. booking-api uses `trimToNull` and stores `NULL` for the same input.
- **Migration impact:** the same request yields different seat consumption per channel; a re-implementation must pick one rule explicitly and the accommodation-capacity check (`db/procs/020_usp_BookSeat.sql:50-51`) will fire differently.
- **Repro (run):**
  ```text
  $ post "/Booking/Book?sessionId=152" /Booking/Book SessionId=152 CandidateNumber=nw00000003 AccommodationCode=
  HTTP 302 -> http://localhost:8080/Booking/MyBookings?candidateNumber=NW00000003
  $ curl -s -X POST localhost:8081/api/v1/bookings -H 'Content-Type: application/json' \
      -d '{"sessionId":187,"candidateNumber":"NW00000003","accommodationCode":"","externalRef":"REPRO-1"}'
  { "bookingId" : 3589, "status" : "BOOKED" }
  $ q "SELECT BookingId,SessionId,Status,AccommodationCode,Channel FROM Booking WHERE BookingId IN (3588,3589)"
  3588|152|BOOKED|ET25|PORTAL        <- candidate default (NW00000003 profile = ET25)
  3589|187|BOOKED|NULL|PARTNER
  ```

### Retake wait is calendar days in the portal but working days in booking-api

- **Where:** `AssessmentDelivery.Business/Rules/RetakeRules.cs:24-25` (`Max(SessionDate).Date.AddDays(waitDays)`) vs
  `services/booking-api/src/main/java/com/northwind/assessment/booking/service/RetakeEligibilityService.java:49-53` and
  `services/booking-api/src/main/java/com/northwind/assessment/booking/util/DateUtils.java:67-79` (`addWorkingDays`, skips Sat/Sun).
- **What happens:** with `Exam.RetakeWaitDays = 30` a failed candidate is eligible ~12 calendar days earlier through the portal than through the partner API. The portal also takes `Max` of attempts while Java takes `attempts.get(0)` of a `StartsAt DESC` query (`services/booking-api/src/main/java/com/northwind/assessment/booking/dao/ResultDao.java:29`); these agree only because of that ORDER BY.
- **Migration impact:** a single consolidated eligibility rule will change outcomes for one of the two channels; needs a business decision and characterization tests around the boundary.
- **Repro (run):** NW00000113 failed HPI-ACC1 on Sat 2026-11-07 13:30.
  ```text
  $ curl -s "http://localhost:8080/Eligibility?candidateNumber=NW00000113&examCode=HPI-ACC1&date=2026-12-07" | <strip tags>
  Eligible YES / Reason Retake wait period has elapsed / Earliest retake date 07 Dec 2026
  $ curl -s "localhost:8081/api/v1/candidates/NW00000113/eligibility?examCode=HPI-ACC1&date=2026-12-07"
  {"eligible":false,"reason":"RETAKE_WAIT_NOT_ELAPSED","earliestRetakeDate":"2026-12-18"}
  $ ... date=2026-12-17   portal: Eligible YES     java: eligible:false (earliest 2026-12-18)
  $ ... date=2026-12-21   java: {"eligible":true,"reason":"RETAKE_ALLOWED","earliestRetakeDate":"2026-12-18"}
  ```

### Partner bookings decrement `SeatsAvailable` twice, so the admin "Seats avail." and "Booked" columns disagree

- **Where:** `db/triggers/032_trg_Booking_SeatSync.sql:19-23` (decrements for every non-`PORTAL` `BOOKED` insert) plus
  `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:114-116` (inserts then decrements itself). The portal admin screen shows the stored counter next to a recount of rows (`AssessmentDelivery.Services/Admin/AdminService.cs:112,124-125`).
- **What happens:** each partner booking costs two seats in `SeatsAvailable`. Neither the trigger nor this coupling is mentioned in `docs/SYSTEM_OVERVIEW.md`. The portal then decides "Book" vs "Join waitlist" from the drifted counter (`AssessmentDelivery.Web/Views/Booking/Index.cshtml:77`, `SeatsAvailable > 0`) and `usp_BookSeat` waitlists on it (`db/procs/020_usp_BookSeat.sql:54`), so sessions can waitlist candidates while physical seats are free.
- **Migration impact:** counter cannot be trusted as source of truth; any migration of `SeatsAvailable` must recompute from rows and decide whether the trigger survives.
- **Repro (run):**
  ```text
  $ q "SELECT SessionId,Capacity,SeatsAvailable FROM ExamSession WHERE SessionId=187"
  187|10|5
  $ curl -s -X POST localhost:8081/api/v1/bookings ... (as above) -> { "bookingId" : 3589, "status" : "BOOKED" }
  $ q "SELECT Capacity,SeatsAvailable,(SELECT COUNT(*) FROM Booking b WHERE b.SessionId=187 AND Status IN('BOOKED','ATTENDED')) booked FROM ExamSession WHERE SessionId=187"
  10|3|6            <- one booking, two seats gone; 10-6 = 4 expected
  $ curl -s http://localhost:8080/Admin/Sessions/Capacity/187 | <strip tags>
  Capacity Acc. capacity Seats available Booked Waitlist 10 1 3 6 0
  ```

### Rescheduling a partner booking from the portal rewrites it as a PORTAL booking and drops the partner reference

- **Where:** `AssessmentDelivery.Services/Booking/BookingService.cs:176-177` -> `db/procs/023_usp_RescheduleBooking.sql:37-38`, which re-books through `db/procs/020_usp_BookSeat.sql:56-57` (hard-coded `Channel='PORTAL'`, no `ExternalRef` column) and cancels without an actor (`db/procs/021_usp_CancelBooking.sql:44` falls back to `SUSER_SNAME()`). booking-api's own reschedule copies `externalRef` across (`services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:171-176`).
- **What happens:** My Bookings lists partner/import bookings (`AssessmentDelivery.Services/Booking/BookingService.cs:71`) and lets the operator reschedule them; the new row is `PORTAL` with `ExternalRef NULL`, and both audit rows record the SQL login (`ad_app`) instead of an operator.
- **Migration impact:** partner reconciliation by `ExternalRef` breaks after any portal reschedule; channel attribution and audit actors in historical data are unreliable.
- **Repro (run):** partner booking 1929 (`PTR-0021703`, session 217, CTA-SEC2) moved to session 151.
  ```text
  $ post "/Booking/Reschedule/1929?candidateNumber=NW00000702" /Booking/Reschedule/1929 CandidateNumber=NW00000702 NewSessionId=151
  HTTP 302 -> http://localhost:8080/Booking/MyBookings?candidateNumber=NW00000702
  $ q "SELECT BookingId,SessionId,Status,Channel,ExternalRef FROM Booking WHERE BookingId IN (1929,3590)"
  1929|217|CANCELLED|PARTNER|PTR-0021703
  3590|151|WAITLISTED|PORTAL|NULL
  $ q "SELECT TOP 2 AuditId,EntityId,Action,Detail,Actor FROM AuditLog ORDER BY 1 DESC"
  21|3590|BOOK|session=151 status=WAITLISTED|ad_app
  20|1929|CANCEL|Rescheduled|ad_app
  ```

### Reschedule targets include sessions that already started today and full sessions; moving into a full session gives up a confirmed seat

- **Where:** `AssessmentDelivery.Services/Booking/BookingService.cs:154-155` (`StartsAt >= DateTime.Today`, server-local midnight, no seat filter); `db/procs/023_usp_RescheduleBooking.sql:37-38` cancels first, then `usp_BookSeat` waitlists when `SeatsAvailable = 0` (`db/procs/020_usp_BookSeat.sql:54-72`).
- **What happens:** the target list offered session 151 (PHX01, 2026-11-11 09:00 centre-local, already under way at 16:55 UTC, 0 seats). Choosing it cancelled the confirmed booking (session 217 seat released) and created a `WAITLISTED` booking. The UI only shows a generic success flash.
- **Migration impact:** a "move" can silently downgrade a confirmed seat; target filtering depends on server clock/zone, not centre time.
- **Repro (run):** same run as above.
  ```text
  $ curl -s "http://localhost:8080/Booking/Reschedule/1929?candidateNumber=NW00000702" | grep -c 'value="151"'
  1
  $ q "SELECT SessionId,SeatsAvailable FROM ExamSession WHERE SessionId IN (217,151)"   (before)  151|0  217|0
  ... after the POST:                                                                            151|0  217|1
  ```

### Session search and booking ignore start time and centre `IsActive`

- **Where:** `AssessmentDelivery.Services/Booking/BookingService.cs:28-46` filters only `Status == "OPEN"` (+ optional criteria); `Book` checks only candidate/session existence (`AssessmentDelivery.Services/Booking/BookingService.cs:90-98`); `usp_BookSeat` checks only `Status='OPEN'` (`db/procs/020_usp_BookSeat.sql:34-38`). Only the centre *drop-down* hides inactive centres (`AssessmentDelivery.Services/Booking/BookingService.cs:59-61`).
- **What happens:** sessions that already began are listed with "Join waitlist"/"Book", and a session at a deactivated centre can be booked through the search URL (`centerCode=`) or a direct `Book?sessionId=` link.
- **Migration impact:** session status is maintained by a separate process (seed sets `DELIVERED` only for past dates, `db/seed/042_seed_sessions.sql:40`); a rewrite that derives bookability from time/centre state will reject bookings the legacy portal accepts.
- **Repro (run):**
  ```text
  $ curl -s "http://localhost:8080/Booking?examCode=CTA-SEC2&centerCode=PHX01" | grep "Book?sessionId=151"
  <a class="btn btn-xs btn-primary" href="/Booking/Book?sessionId=151">Join waitlist</a>      (session started 09:00 local)
  $ post "/Admin/EditCenter/6" /Admin/EditCenter TestCenterId=6 CenterCode=TOR01 "Name=Lakeshore Assessment Centre" IsActive=false
  HTTP 302 -> http://localhost:8080/Admin/Centers
  $ curl -s "http://localhost:8080/Booking?centerCode=TOR01" | grep -c 'href="/Booking/Book?sessionId='
  18
  $ post "/Booking/Book?sessionId=210" /Booking/Book SessionId=210 CandidateNumber=NW00000010 AccommodationCode=
  HTTP 302 -> http://localhost:8080/Booking/MyBookings?candidateNumber=NW00000010
  $ q "SELECT tc.IsActive center_active,b.BookingId,b.SessionId,b.Status FROM Booking b JOIN ExamSession s ON s.SessionId=b.SessionId JOIN TestCenter tc ON tc.TestCenterId=s.TestCenterId WHERE b.BookingId=3591"
  0|3591|210|BOOKED
  ```
  (centre re-activated afterwards with `IsActive=true`.)

### Deactivating a centre is a direct EF update with no audit row and no effect on its open sessions

- **Where:** `AssessmentDelivery.Services/Admin/AdminService.cs:50-59` (`Find` -> set `Name`/`IsActive` -> `SaveChanges`). No trigger exists on `TestCenter` (only `db/triggers/030`-`033`).
- **What happens:** every other admin write is audited by a proc; this one leaves no trace, and the centre's `OPEN` sessions stay open and bookable (see previous behaviour).
- **Migration impact:** no history of centre status changes to migrate; deactivation semantics (close sessions? notify candidates?) are undefined.
- **Repro (run):**
  ```text
  $ q "SELECT MAX(AuditId) FROM AuditLog"                                   -> 21
  $ post "/Admin/EditCenter/6" /Admin/EditCenter TestCenterId=6 CenterCode=TOR01 "Name=Lakeshore Assessment Centre" IsActive=false
  HTTP 302 -> http://localhost:8080/Admin/Centers
  $ q "SELECT IsActive FROM TestCenter WHERE TestCenterId=6; SELECT MAX(AuditId) FROM AuditLog; SELECT COUNT(*) FROM ExamSession WHERE TestCenterId=6 AND Status='OPEN' AND StartsAt>GETDATE()"
  0      21      17
  ```

### Reducing capacity in the portal silently demotes the newest confirmed bookings to WAITLISTED

- **Where:** portal calls `usp_ChangeCapacity` (`AssessmentDelivery.Services/Admin/AdminService.cs:78-79`), whose `UPDATE` (`db/procs/022_usp_ChangeCapacity.sql:33`) fires `trg_ExamSession_Update` (`db/triggers/033_trg_ExamSession_Update.sql:28-52`): bookings ranked `BookedAt DESC, BookingId DESC` beyond the new capacity become `WAITLISTED`, get appended to `Waitlist` (`db/triggers/033_trg_ExamSession_Update.sql:60-64`) and `SeatsAvailable` is recomputed as `Capacity - COUNT(BOOKED)` (`db/triggers/033_trg_ExamSession_Update.sql:66-68`).
- **What happens:** `docs/SYSTEM_OVERVIEW.md:62` only says SeatsAvailable "shifts by the same delta, floored at 0"; the screen text says the same. Candidates lose confirmed seats with no notification and no per-booking audit row (only the `CAPACITY` audit).
- **Migration impact:** a business-critical side effect lives in a trigger; a rewrite that ports only the proc loses it, one that ports it must decide on notification/audit. The trigger's recount ignores `ATTENDED`, unlike `SeatCounter` (`AssessmentDelivery.Business/Rules/SeatCounter.cs:20-27`).
- **Repro (run):** session 197 (capacity 24, 22 `BOOKED`, 2 seats).
  ```text
  $ post "/Admin/Sessions/Capacity/197" /Admin/Sessions/Capacity/197 newCapacity=20
  HTTP 302 -> http://localhost:8080/Admin/Sessions/Capacity/197
  $ q "SELECT Capacity,SeatsAvailable FROM ExamSession WHERE SessionId=197"                 -> 20|0
  $ q "SELECT TOP 3 BookingId,Status,BookedAt FROM Booking WHERE SessionId=197 ORDER BY BookedAt DESC,BookingId DESC"
  1835|WAITLISTED|2026-11-10 06:39:00.000
  1834|WAITLISTED|2026-11-09 17:48:00.000
  1833|BOOKED|2026-11-09 08:17:00.000
  $ q "SELECT Position,BookingId FROM Waitlist WHERE SessionId=197"                          -> 1|1834  2|1835
  $ q "SELECT TOP 1 EntityName,EntityId,Action,Detail,Actor FROM AuditLog ORDER BY AuditId DESC"
  ExamSession|197|CAPACITY|24 -> 20|portal-admin
  ```

### The `[Range(0, 500)]` on capacity is never enforced server-side

- **Where:** `AssessmentDelivery.Web/Models/AdminModels.cs:20-22` declares `[Required, Range(0, 500)] NewCapacity`, but the POST action binds a bare `int? newCapacity` and never checks `ModelState.IsValid` (`AssessmentDelivery.Web/Controllers/AdminController.cs:70-82`). Only negatives and "below accommodation capacity" are rejected (`AssessmentDelivery.Services/Admin/AdminService.cs:75`, `db/procs/022_usp_ChangeCapacity.sql:28-29`).
- **What happens:** client-side validation is the only upper bound; any HTTP client can set capacity 600 on a 10-seat room.
- **Migration impact:** validation rules declared on view models are not the real contract; characterize from service/proc behaviour, not annotations. Capacity is not tied to `Room.SeatCount`.
- **Repro (run):**
  ```text
  $ post "/Admin/Sessions/Capacity/187" /Admin/Sessions/Capacity/187 newCapacity=600
  HTTP 302 -> http://localhost:8080/Admin/Sessions/Capacity/187
  $ q "SELECT Capacity,SeatsAvailable FROM ExamSession WHERE SessionId=187"                 -> 600|593
  $ post "/Admin/Sessions/Capacity/187" /Admin/Sessions/Capacity/187 newCapacity=-1
  HTTP 200 ->
    error: Capacity cannot be negative.
  ```
  (restored with `newCapacity=10`.)

### Any booking can be cancelled by id, including bookings for sessions already delivered, and the seat counter of a past session goes up

- **Where:** POST `/Booking/Cancel/{id}` calls `_booking.Cancel(id, reason)` without checking that `id` belongs to the posted candidate number (`AssessmentDelivery.Web/Controllers/BookingController.cs:91-104`); `usp_CancelBooking` checks only `Status IN ('BOOKED','WAITLISTED')` (`db/procs/021_usp_CancelBooking.sql:24-30`), not session status or date; `SeatCounter.CanCancel` likewise ignores time (`AssessmentDelivery.Business/Rules/SeatCounter.cs:30`). There is no authentication (`AssessmentDelivery.Web/Web.config:30`).
- **What happens:** stale `BOOKED` rows on `DELIVERED` sessions (no-shows never moved to another status) can be cancelled after the fact; `SeatsAvailable` on the delivered session is incremented.
- **Migration impact:** history is mutable after delivery; any authz model added in migration changes current operator behaviour; downstream results reconciliation may see "cancelled" bookings that were sat.
- **Repro (run):** booking 3 (NW00000718, session 1, `DELIVERED`, 2026-09-01).
  ```text
  $ post "/Booking/Cancel/3?candidateNumber=NW00000718" /Booking/Cancel/3 CandidateNumber=NW00000718 Reason=repro
  HTTP 302 -> http://localhost:8080/Booking/MyBookings?candidateNumber=NW00000718
  $ q "SELECT BookingId,Status,CancelReason FROM Booking WHERE BookingId=3; SELECT Status,Capacity,SeatsAvailable FROM ExamSession WHERE SessionId=1"
  3|CANCELLED|repro
  DELIVERED|15|9          (was 8 before the cancel)
  ```
  The candidate number in the form is only used for the redirect; posting another candidate's number is accepted the same way (not separately run).

### RAISERROR mapping is by message text only: the portal shows the internal `CandidateId`, and the documented error codes are not what is raised

- **Where:** `AssessmentDelivery.Services/Common/SqlErrorHelper.cs:25` accepts any msg 50000 / class 16 and uses `err.Message`, ignoring `err.State`; `db/procs/020_usp_BookSeat.sql:43` formats the duplicate error with `@CandidateId` (surrogate key). `docs/SYSTEM_OVERVIEW.md:60` lists "50001 not open, 50002 duplicate, 50003 no accommodation seat" while line 65 says msg 50000 with the code in the state; actual states are 1-6 (`db/procs/020_usp_BookSeat.sql:38,43,51`, `db/procs/021_usp_CancelBooking.sql:30`, `db/procs/022_usp_ChangeCapacity.sql:29`, `db/procs/023_usp_RescheduleBooking.sql:31`). booking-api maps its own codes (`DUPLICATE_BOOKING` etc.) before ever reaching SQL and returns 500 `DB_ERROR` for SQL errors (`services/booking-api/src/main/java/com/northwind/assessment/booking/web/ApiExceptionHandler.java:33-36`).
- **What happens:** operators see "Candidate 3 already has an active booking" for candidate `NW00000003`; nothing in the portal can branch on the error type.
- **Migration impact:** error contracts must be re-derived; any client expecting 50001-50003 will never see them.
- **Repro (run):**
  ```text
  $ post "/Booking/Book?sessionId=152" /Booking/Book SessionId=152 CandidateNumber=NW00000003 AccommodationCode=
  HTTP 200 ->
    error: Candidate 3 already has an active booking on session 152.
  $ q "DECLARE @b INT,@s VARCHAR(20); EXEC dbo.usp_RescheduleBooking 3591,3,@b OUTPUT,@s OUTPUT;"
  Msg 50000, Level 16, State 1, Server devinbox, Procedure dbo.usp_RescheduleBooking, Line 36
  Session 3 is not open for booking.
  ```

### The audit actor depends on which proc ran: SQL login for portal bookings, literals for cancel/capacity

- **Where:** `db/procs/020_usp_BookSeat.sql:79-80` inserts `AuditLog` without `Actor` (column default / login); portal cancel passes `'portal'` (`AssessmentDelivery.Services/Booking/BookingService.cs:139`); capacity passes `'portal-admin'` (`AssessmentDelivery.Web/Controllers/AdminController.cs:82`); reschedule's nested cancel passes nothing (`db/procs/023_usp_RescheduleBooking.sql:37`, falls back to `SUSER_SNAME()` at `db/procs/021_usp_CancelBooking.sql:44`); booking-api writes `booking-api` (`services/booking-api/src/main/resources/application.properties:17`). No operator identity exists (`AssessmentDelivery.Web/Web.config:30`).
- **Migration impact:** `AuditLog.Actor` cannot be used to attribute actions to channels or people; any audit migration must map these four conventions.
- **Repro (run):** observed during the runs above:
  ```text
  $ q "SELECT AuditId,EntityName,EntityId,Action,Actor FROM AuditLog WHERE AuditId IN (13,15,17,20)"
  13|Booking|3588|BOOK|ad_app
  15|Booking|3|CANCEL|portal
  17|ExamSession|197|CAPACITY|portal-admin
  20|Booking|1929|CANCEL|ad_app
  ```

### Duplicate-booking protection is per session only: a candidate can hold several active bookings for the same exam

- **Where:** `db/procs/020_usp_BookSeat.sql:40-43` and `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:85` check `SessionId + CandidateId`; the portal's eligibility check only looks at *results* (`AssessmentDelivery.Services/Eligibility/EligibilityService.cs:20-37`), not open bookings.
- **Migration impact:** a stricter "one active booking per exam" rule in a new system would reject existing data and change behaviour.
- **Repro (run):** NW00000003 ended up `BOOKED` on two CTA-NET1 sessions (one per channel) in the runs above:
  ```text
  $ q "SELECT b.BookingId,b.SessionId,e.ExamCode,b.Status,b.Channel FROM Booking b JOIN ExamSession s ON s.SessionId=b.SessionId JOIN Exam e ON e.ExamId=s.ExamId WHERE b.BookingId IN (3588,3589)"
  3588|152|CTA-NET1|BOOKED|PORTAL
  3589|187|CTA-NET1|BOOKED|PARTNER
  ```

### Dates mix server-local "today", centre-local session times and invariant-culture query strings

- **Where:** `ExamSession.StartsAt` is centre-local (`AssessmentDelivery.Data/DataClasses/Entities/ExamSession.cs:18-19`; `AssessmentDelivery.Web/Views/Admin/Sessions.cshtml:8`); "today" is the web server's clock (`AssessmentDelivery.Web/Controllers/EligibilityController.cs:28`, `AssessmentDelivery.Services/Booking/BookingService.cs:154`); `TestCenter.TimeZone` is display-only. `Web.config` sets `en-GB` (`AssessmentDelivery.Web/Web.config:34`) but MVC binds GET query-string values with the invariant culture, so `date=07/12/2026` means 12 July. Views format `dd MMM yyyy` (`AssessmentDelivery.Web/Views/Shared/_Layout.cshtml:21`).
- **Migration impact:** moving the host to another zone (the VM runs UTC) shifts "today" for eligibility and reschedule filtering; any UTC normalisation of `StartsAt` needs the centre zone; links/bookmarks using `dd/MM/yyyy` are silently misread.
- **Repro (run):**
  ```text
  $ powershell -NoProfile -Command "(Get-TimeZone).Id; (Get-Culture).Name"     -> UTC / en-US
  $ q "SELECT GETDATE(), SYSDATETIMEOFFSET()"   -> 2026-11-11 16:55:00.600 | 2026-11-11 16:55:00.6016261 +00:00
  $ curl -s "http://localhost:8080/Eligibility?candidateNumber=NW00000113&examCode=HPI-ACC1&date=07/12/2026" | grep -oE 'name="date"[^>]*value="[^"]*"'
  name="date" class="form-control input-sm" value="2026-07-12"
  ```
  Session 151 (PHX01 09:00 local = 16:00 UTC) was still offered as bookable/reschedulable at 16:55 UTC (see above).

### Unhandled errors show a full ASP.NET error page with stack trace to local callers; debug compilation is on

- **Where:** `customErrors mode="RemoteOnly"` and `compilation debug="true"` (`AssessmentDelivery.Web/Web.config:31-33`); `Web.Debug.config` turns customErrors Off; only `Web.AzureRelease.config` sets On. `HandleErrorAttribute` (`AssessmentDelivery.Web/App_Start/FilterConfig.cs:9`) only applies when custom errors are active. Non-50000 SQL errors are rethrown (`AssessmentDelivery.Services/Booking/BookingService.cs:124-126`).
- **Migration impact:** error-page behaviour depends on which transform was applied and whether the request is local (e.g. kiosk/RDP on the web server); the friendly `Error.cshtml` is rarely what operators see on the box.
- **Repro (run):**
  ```text
  $ curl -s -X POST http://localhost:8080/Booking/Book -d "SessionId=152&CandidateNumber=NW00000005" -o y.html -w "HTTP %{http_code}\n"
  HTTP 500
  $ grep -o "<title>[^<]*" y.html ; grep -c "Stack Trace" y.html
  <title>The required anti-forgery cookie &quot;__RequestVerificationToken&quot; is not present.
  1
  ```

### Several `Portal.*` appSettings are dead configuration

- **Where:** `Portal.KioskMode`, `Portal.DefaultRegion`, `Portal.SupportEmail`, `Portal.ResultsReleaseDelayHours` (`AssessmentDelivery.Web/Web.config:24-27`) are not read by any `.cs` or `.cshtml` file; only `Portal.HostType`, `Portal.EnvironmentName` and `Portal.BuildNumber` are used (`AssessmentDelivery.Web/Infrastructure/PortalHtmlExtensions.cs:42-47`). The results page shows results immediately regardless of `ResultsReleaseDelayHours` (`AssessmentDelivery.Services/Results/ResultsService.cs:16-33`).
- **Migration impact:** settings imply features (kiosk mode, results embargo) that do not exist; do not port them as requirements without confirmation.
- **Repro (run):**
  ```text
  $ grep -rn "KioskMode\|ResultsReleaseDelayHours\|DefaultRegion\|SupportEmail" --include=*.cs --include=*.cshtml .
  (no output)
  ```

## 5. Migration risks

| Risk | Impact | Likelihood | Evidence | Suggested mitigation / characterization test |
|---|---|---|---|---|
| Retake eligibility differs by channel (calendar vs working days; `Max` vs first row) | H | H | `AssessmentDelivery.Business/Rules/RetakeRules.cs:24-25`; `services/booking-api/src/main/java/com/northwind/assessment/booking/util/DateUtils.java:67-79` | Get a policy decision; table-driven tests for last attempt on Fri/Sat/Sun and boundary dates (D+29/30/31, D+41/42) against both engines |
| Retake rule lives only in callers; `usp_BookSeat` will book anyone (new callers bypass it) | H | M | `AssessmentDelivery.Services/Booking/BookingService.cs:106-109`; `db/procs/020_usp_BookSeat.sql` (no `Result` access) | Move the rule behind one booking entry point; test that a failed-within-wait candidate is rejected by every channel |
| `SeatsAvailable` drifts (trigger + Java double decrement; trigger recount ignores `ATTENDED`; floor at 0) | H | H | `db/triggers/032_trg_Booking_SeatSync.sql:19-23`; `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:116`; `db/triggers/033_trg_ExamSession_Update.sql:66-68`; `db/procs/022_usp_ChangeCapacity.sql:33` | Recompute from rows during migration; nightly reconciliation query `Capacity - COUNT(BOOKED,ATTENDED) <> SeatsAvailable`; per-channel booking tests asserting delta = 1 |
| Capacity decrease demotes confirmed bookings via a trigger with no notification | H | M | `db/triggers/033_trg_ExamSession_Update.sql:28-64` | Characterization test: capacity N -> N-k demotes exactly the k newest `BOOKED`; make the behaviour explicit (or blocked) in the target |
| Portal reschedule loses `Channel`/`ExternalRef` of partner bookings | H | M | `db/procs/020_usp_BookSeat.sql:56-57`; `db/procs/023_usp_RescheduleBooking.sql:37-38` | Preserve channel/ref on reschedule (or forbid for partner bookings); test reschedule of PARTNER/IMPORT bookings from every surface |
| No authentication / ownership checks on cancel and reschedule | H | H | `AssessmentDelivery.Web/Web.config:30`; `AssessmentDelivery.Web/Controllers/BookingController.cs:91-104,116-124` | Introduce operator identity before or with migration; tests that cancel/reschedule require ownership or an operator role |
| Bookability ignores time, centre `IsActive`, room/capacity limits | M | H | `AssessmentDelivery.Services/Booking/BookingService.cs:28-46,90-98`; `AssessmentDelivery.Web/Controllers/AdminController.cs:70-82` | Decide target rules; characterization tests for started sessions, inactive centres, capacity > room seats |
| Implicit default accommodation on blank input (portal only) | M | H | `AssessmentDelivery.Services/Booking/BookingService.cs:101`; `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:62` | Make "use profile default" an explicit option; test ET25 candidate booking with blank code in both channels |
| Time-zone handling: centre-local `StartsAt`, server-local "today", invariant query-string dates | M | H | `AssessmentDelivery.Data/DataClasses/Entities/ExamSession.cs:18-19`; `AssessmentDelivery.Web/Controllers/EligibilityController.cs:28`; `AssessmentDelivery.Services/Booking/BookingService.cs:154`; `AssessmentDelivery.Web/Web.config:34` | Store UTC + centre zone in target; tests with host TZ set to UTC and to a centre zone; reject ambiguous `dd/MM` inputs |
| Error contract is message text; state codes ignored; docs list non-existent 50001-50003 | M | M | `AssessmentDelivery.Services/Common/SqlErrorHelper.cs:25`; `docs/SYSTEM_OVERVIEW.md:60-65`; `db/procs/020_usp_BookSeat.sql:38-51` | Define typed error codes; golden tests for each proc error message/state and the HTTP/UI rendering |
| Unit tests cover only 3 static rules (8 cases); none for services, controllers, procs, triggers, retake wait boundary | H | H | `AssessmentDelivery.Tests/UnitTests/RetakeRulesTests.cs:13-23`; `AssessmentDelivery.Tests/UnitTests/SeatCounterTests.cs:9-10`; `AssessmentDelivery.Tests/UnitTests/AccommodationCodesTests.cs:12-27` | Build an HTTP + DB characterization suite from the repros in section 4 before changing behaviour |
| Check-then-act without a transaction: eligibility is evaluated before and outside the proc transaction | M | L | `AssessmentDelivery.Services/Booking/BookingService.cs:106-116` (`DoNotEnsureTransaction`); `db/procs/020_usp_BookSeat.sql:31` | Fold eligibility into the same transaction in the target; concurrency test with result ingestion during booking |
| Audit actor conventions inconsistent; centre edits not audited | M | M | `db/procs/020_usp_BookSeat.sql:79-80`; `AssessmentDelivery.Services/Booking/BookingService.cs:139`; `AssessmentDelivery.Services/Admin/AdminService.cs:50-59` | Map actors during data migration; add audit for centre changes; test every write produces one audit row with a real actor |
| Plaintext SQL credentials and `debug="true"` in base `Web.config`; error-page mode depends on transform | M | H | `AssessmentDelivery.Web/Web.config:12,31-33`; `AssessmentDelivery.Web/Web.Debug.config` | Externalise secrets; verify the transform used for each environment; test that remote and local errors render the friendly page |
| Bookings on delivered sessions are still mutable (cancel after the fact) | M | M | `db/procs/021_usp_CancelBooking.sql:24-30`; `AssessmentDelivery.Business/Rules/SeatCounter.cs:30` | Decide lifecycle (close bookings after delivery); test cancel on `DELIVERED` session is rejected or explicitly allowed |

## 6. Open questions

1. Which retake policy is authoritative - calendar days (portal) or working days (booking-api)? Is `RetakeWaitDays` meant to be calendar or working days, and do bank holidays matter?
2. Should a blank accommodation choice mean "candidate default" or "none"? Is there a legitimate need to book an ET25/ET50 candidate without accommodation?
3. Is the double decrement for partner bookings known/compensated anywhere (e.g. by capacity-sync), or is it a defect? Should `trg_Booking_SeatSync` survive migration?
4. Is silent demotion on capacity reduction intended, and who notifies demoted candidates? Should the recount include `ATTENDED`?
5. Should portal operators be able to reschedule/cancel partner and import bookings at all, and if so must `Channel`/`ExternalRef` be preserved?
6. What process moves sessions from `OPEN` to `DELIVERED`/`CLOSED` and stale `BOOKED` rows to `ATTENDED`/no-show? Until it runs, started sessions remain bookable.
7. What does deactivating a centre mean for its open sessions and existing bookings?
8. Are `Portal.KioskMode`, `Portal.ResultsReleaseDelayHours`, `Portal.DefaultRegion` and `Portal.SupportEmail` remnants, or features expected by operators?
9. Which time zone should "today" use for eligibility and reschedule filtering - the server's, the centre's, or the candidate's?
10. Is the upper bound of 500 seats (view model) a real business rule, and should capacity be capped by `Room.SeatCount`?
