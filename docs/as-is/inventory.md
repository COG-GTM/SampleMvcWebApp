# Inventory (as-is)

Counts are from `git ls-tree` at `legacy-baseline` (`e458238`); LOC counts all lines of `.cs .cshtml .java .sql .ps1 .sh .properties .config` files. Detailed per-file inventories (controllers, routes, endpoints, payloads, SQL statements, procs, triggers, indexes) are in the deep dives.

## Code

| Component | Path | Files | LOC | Purpose | Depends on |
|---|---|---|---|---|---|
| Portal web | `AssessmentDelivery.Web/` | 37 | 1,575 | 5 MVC controllers (`Admin`, `Booking`, `Eligibility`, `Home`, `Results`), 14 Razor views, Autofac wiring, config transforms | Services, Business, Data |
| Portal services | `AssessmentDelivery.Services/` | 22 | 744 | `BookingService`, `AdminService`, eligibility/results services, `SqlErrorHelper` | Data, Business |
| Portal business rules | `AssessmentDelivery.Business/` | 11 | 208 | `RetakeRules`, accommodation rules | - |
| Portal data | `AssessmentDelivery.Data/` | 15 | 419 | EF6 `DbContext`, entities and mappings | SQL Server |
| Portal tests | `AssessmentDelivery.Tests/` | 6 | 112 | 8 NUnit tests (business rules only; 8/8 pass) | Business |
| booking-api | `services/booking-api/` | 26 | 1,476 | `BookingController`, `CandidateController`, `SessionController`; DAOs; retake and date utilities | SQL Server, `usp_CancelBooking`, `trg_Booking_SeatSync` |
| results-service | `services/results-service/` | 14 | 575 | `ResultsController`; JSON and CSV ingest | SQL Server |
| capacity-sync | `services/capacity-sync/` | 9 | 276 | `SyncController`, `NightlySyncJob` (02:00) | SQL Server |
| Java tests | `services/*/src/test` | - | - | 2 tests per service (6 total, context/unit level); `BUILD SUCCESS` | - |
| Schema | `db/schema/` | 2 | 157 | Database, login `ad_app`, 9 tables | - |
| Stored procedures | `db/procs/` | 4 | 252 | `usp_BookSeat`, `usp_CancelBooking`, `usp_ChangeCapacity`, `usp_RescheduleBooking` | tables, triggers |
| Triggers | `db/triggers/` | 4 | 179 | `trg_Candidate_Audit`, `trg_Exam_Audit`, `trg_Booking_SeatSync`, `trg_ExamSession_Update` | tables |
| Seed | `db/seed/` | 7 | 545 | Deterministic seed anchored to 2026-11-10/11 | schema |
| Checks | `db/checks/` | 2 | 52 | `row-counts.sql`, `seat-drift.sql` | schema |
| Scripts | `scripts/` + `db/apply.*` | 5 (+2) | 1,155 | `run-legacy.ps1` orchestrator, apply scripts, test helpers | everything |

## Database objects

| Type | Objects |
|---|---|
| Tables (9) | `TestCenter`, `Room`, `Exam`, `ExamSession`, `Candidate`, `Booking`, `Waitlist`, `Result`, `AuditLog` |
| Procedures (4) | `usp_BookSeat`, `usp_CancelBooking`, `usp_ChangeCapacity`, `usp_RescheduleBooking` |
| Triggers (4) | `trg_Candidate_Audit`, `trg_Exam_Audit`, `trg_Booking_SeatSync`, `trg_ExamSession_Update` |
| Logins/users (1) | `ad_app` (shared by all components) |

## Seed data (fresh `run-legacy.ps1`, observed)

| Table | Rows |
|---|---|
| `TestCenter` | 21 |
| `Room` | 36 |
| `Exam` | 12 |
| `ExamSession` | 462 |
| `Candidate` | 1,000 |
| `Booking` | 3,587 |
| `Waitlist` | 34 |
| `Result` | 1,444 |
| `seat-drift.sql` rows | 0 |

## HTTP surface

| Component | Endpoints |
|---|---|
| Portal :8080 | `/` -> `Home/Index` (redirects to `/Admin/Centers`), `/Admin`, `/Admin/Centers`, `/Admin/EditCenter/{id}` (GET/POST), `/Admin/Sessions?centerId=`, `/Admin/Sessions/Capacity/{id}` (GET/POST, named route), `/Booking` (search), `/Booking/Book` (GET/POST), `/Booking/MyBookings`, `/Booking/Cancel/{id}` (GET/POST), `/Booking/Reschedule/{id}` (GET/POST), `/Eligibility`, `/Results` (`AssessmentDelivery.Web/App_Start/RouteConfig.cs:13-23`, `AssessmentDelivery.Web/Controllers/*.cs`) |
| booking-api :8081 | `GET /api/v1/sessions`, `GET /api/v1/sessions/{sessionId}`, `GET /api/v1/candidates/{candidateNumber}/eligibility`, `GET /api/v1/candidates/{candidateNumber}/bookings`, `POST /api/v1/bookings`, `GET /api/v1/bookings/{bookingId}`, `DELETE /api/v1/bookings/{bookingId}`, `POST /api/v1/bookings/{bookingId}/reschedule` |
| results-service :8082 | `POST /api/v1/results`, `POST /api/v1/results/upload` (CSV score file), `GET /api/v1/results/candidate/{candidateNumber}` |
| capacity-sync :8083 | `POST /sync/run`, `GET /sync/last` |

## Runtime prerequisites

Windows (portal: .NET Framework 4.8, IIS Express, MSBuild 17 / VS 2022 Build Tools, NuGet), JDK 8 (Temurin 1.8.0_472 observed), Maven 3.8.8 via `services/mvnw.cmd`, SQL Server 2022 Express + go-sqlcmd. Versions observed: [smoke-test.md](smoke-test.md).
