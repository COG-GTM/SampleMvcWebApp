# Architecture (as-is): Northwind Assessment Network legacy estate

Baseline: tag `legacy-baseline` (commit `e458238`). Verified running on Windows Server 2022 with `scripts\run-legacy.ps1` (see [smoke-test.md](smoke-test.md)).

## Components

| Component | Stack | Port | Entry point | Role |
|---|---|---|---|---|
| Portal | ASP.NET MVC 5.2.9, .NET Framework 4.8, EF6 6.4.4, Autofac, IIS Express | 8080 | `AssessmentDelivery.Web/Global.asax.cs:10-21` | Operations/admin UI and candidate booking UI; no login (`/` redirects to `/Admin/Centers`, `AssessmentDelivery.Web/App_Start/RouteConfig.cs:13-21`) |
| booking-api | Java 8, Spring Boot 1.5.22, `JdbcTemplate` | 8081 | `BA/BookingApiApplication.java` | Partner channel: session search, eligibility, book, cancel, reschedule |
| results-service | Java 8, Spring Boot 1.5.22, `JdbcTemplate` | 8082 | `RS/ResultsServiceApplication.java` | Result ingest (single JSON and CSV score file), candidate results |
| capacity-sync | Java 8, Spring Boot 1.5.22, `@Scheduled` | 8083 | `CS/NightlySyncJob.java:14` | Nightly (02:00, JVM zone) recompute of `ExamSession.SeatsAvailable`; `POST /sync/run`, `GET /sync/last` |
| Database `AssessmentDelivery` | SQL Server 2022 Express | 1433 | `db/apply.ps1`, `db/apply.sh` | Shared system of record: 9 tables, 4 procs, 4 triggers, deterministic seed |

Paths: `BA/` = `services/booking-api/src/main/java/com/northwind/assessment/booking/`, `RS/` = `services/results-service/src/main/java/com/northwind/ad/results/`, `CS/` = `services/capacity-sync/src/main/java/com/northwind/assessment/capacity/`.

## Integration style: shared database

```
            Browser (ops staff, candidates)          Partner systems
                     |                                     |
              Portal :8080                     booking-api :8081   results-service :8082   capacity-sync :8083
     EF6 reads + proc writes (+ EF writes)       JdbcTemplate SQL       JdbcTemplate SQL        JdbcTemplate SQL
                     \_____________________________ | ______________________ | _____________________ /
                                                    v                        v
                               SQL Server "AssessmentDelivery" (single login ad_app)
                     procs: usp_BookSeat, usp_CancelBooking, usp_ChangeCapacity, usp_RescheduleBooking
                     triggers: trg_Candidate_Audit, trg_Exam_Audit, trg_Booking_SeatSync, trg_ExamSession_Update
```

- No component calls another over HTTP; there are no queues. All coordination happens through rows, procs and triggers in one database, using one login (`ad_app`, `db/schema/000_create_database.sql:13-25`).
- **Portal layering:** `Web` (controllers, views, Autofac wiring `AssessmentDelivery.Web/Infrastructure/WebUiInitialise.cs:35-38`) -> `Services` (`AssessmentDelivery.Services/Startup/ServiceLayerModule.cs:15-20`) -> `Business` (rules such as `RetakeRules`) -> `Data` (EF6 `DbContext`, `AssessmentDelivery.Data/Startup/DataLayerModule.cs:17`). Seat-touching writes go through procs (`AssessmentDelivery.Services/Booking/BookingService.cs:115-116,135-136,176-177`, `AssessmentDelivery.Services/Admin/AdminService.cs:78-79`); test-centre edits are direct EF writes (`AdminService.cs:50-59`).
- **Java services:** inline SQL in DAOs; only cancel uses a proc (`BA/dao/BookingDao.java:90-92`). Partner booking inserts rows directly and relies on `trg_Booking_SeatSync` plus its own decrement (see [HB-01](hidden-behaviours.md#hb-01-a-partner-booking-takes-two-seats)).
- **Business logic location:** split three ways. Retake and accommodation rules exist in C# and Java separately with different semantics (HB-07, HB-09); seat counting exists in procs, a trigger, Java and capacity-sync with different formulas (HB-03); `usp_BookSeat` enforces only session status, duplicates and seat count (HB-08).

## Key flows

| Flow | Portal path | Partner path |
|---|---|---|
| Search sessions | EF query, `Status='OPEN'` (`AssessmentDelivery.Services/Booking/BookingService.cs:28-46`) | `GET /api/v1/sessions`, no status filter, 500 rows (`BA/dao/SessionDao.java:30,35-56`) |
| Eligibility | `RetakeRules` calendar days (`AssessmentDelivery.Business/Rules/RetakeRules.cs:18-31`) | `RetakeEligibilityService` working days (`BA/service/RetakeEligibilityService.java:40-57`) |
| Book | `usp_BookSeat` (seat -1 in proc; `Channel='PORTAL'`) | direct INSERT + Java -1 + trigger -1 (`BA/service/BookingService.java:110-120`) |
| Cancel | `usp_CancelBooking` (+1) | `usp_CancelBooking` (+1) |
| Reschedule | `usp_RescheduleBooking` = cancel + `usp_BookSeat` | Java cancel + partner book (`BA/service/BookingService.java:147-176`) |
| Capacity change | `usp_ChangeCapacity` -> `trg_ExamSession_Update` demotion/recount | n/a (capacity-sync rewrites `SeatsAvailable` nightly) |
| Results | read-only `/Results` | `POST /api/v1/results`, `POST /api/v1/results/upload` (`RS/service/ResultIngestService.java`, `RS/service/ScoreFileImporter.java`) |

## Runtime and configuration

- Start: `powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1` (installs SQL Server 2022 Express if missing, applies `db/` (drops and recreates the database), builds the services with `services\mvnw.cmd`, builds the portal with MSBuild and starts IIS Express). `-SkipDb -SkipBuild` restarts without reseeding; `-Status` and `-Stop` manage the processes. About 4 minutes on a bare VM ([smoke-test.md](smoke-test.md)).
- Connection strings: portal `AssessmentDelivery.Web/Web.config` (`ad_app`); each Java service `src/main/resources/application.properties` (same login). `booking.channel=PARTNER` in booking-api controls trigger behaviour (HB-04).
- Time: no time-zone columns; `StartsAt` is centre-local, "today" is the host clock (HB-25).
- Security: no authentication in the portal or the APIs; a shared high-privilege SQL login (HB-28).

## Deep dives

[portal](deep-dives/portal.md) · [java-services](deep-dives/java-services.md) · [sql](deep-dives/sql.md) · [runtime-repros](deep-dives/runtime-repros.md) · [smoke test](smoke-test.md)
