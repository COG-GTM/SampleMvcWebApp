# Java services deep dive: current state (legacy-baseline)

Northwind Assessment Network, assessment delivery estate. This document describes the Java 8 services at git tag
`legacy-baseline` (commit `e458238`). Every `path:line` reference is to that commit. Repro scripts are in
[`../repros/java-services/`](../repros/java-services/) and their captured output is in `output/` there.

Paths below are shortened as follows:

- `BA/` = `services/booking-api/src/main/java/com/northwind/assessment/booking/`
- `RS/` = `services/results-service/src/main/java/com/northwind/ad/results/`
- `CS/` = `services/capacity-sync/src/main/java/com/northwind/assessment/capacity/`

Live evidence came from Windows Server running `scripts\run-legacy.ps1 -NoPortal`. That run used SQL Server 2022 Express
on `localhost,1433`, a freshly seeded `AssessmentDelivery` database, and the three services as jars on JDK 8u472.
All repros ran on 2026-11-11.

## 1. Scope and architecture

### Components

| Service | Port | Role | Entry point |
|---|---|---|---|
| booking-api | 8081 | Partner booking channel: session search, book, cancel, reschedule, retake eligibility | `BA/BookingApiApplication.java:1-18` |
| results-service | 8082 | Single-score POST and partner score-file (CSV) upload; candidate result listing | `RS/ResultsServiceApplication.java:1-15` |
| capacity-sync | 8083 | Recomputes `ExamSession.SeatsAvailable` nightly at 02:00 and on demand | `CS/CapacitySyncApplication.java:12`, `CS/NightlySyncJob.java:14` |

Ports and roles are documented in `services/README.md:9-11` and `docs/SYSTEM_OVERVIEW.md:19-21`.

### Request and data flow

```
partner systems --HTTP/JSON--> booking-api :8081 --JdbcTemplate--> AssessmentDelivery (SQL Server)
partner score files --multipart CSV--> results-service :8082 --JdbcTemplate--> same DB
scheduler (02:00) / POST /sync/run --> capacity-sync :8083 --JdbcTemplate--> same DB
portal :8080 (EF6 + stored procedures) -------------------------------------> same DB
```

- The services have no shared code module. Each one has its own DAO and service classes, plus its own copy of
  rules that the portal also implements in C#: `BA/service/SeatCounter.java:17-29` and
  `BA/service/RetakeEligibilityService.java:40-57` mirror `AssessmentDelivery.Business/Rules/RetakeRules.cs:14-32`.
- None of the services calls another service or the portal. All coupling goes through the shared tables and
  through the triggers on them (`db/triggers/032_trg_Booking_SeatSync.sql:13-23`,
  `db/triggers/033_trg_ExamSession_Update.sql:20-62`).
- booking-api uses inline SQL for every write except cancellation. Cancellation calls `usp_CancelBooking`
  (`BA/dao/BookingDao.java:90-92`). Booking does not call `usp_BookSeat` (`BA/dao/BookingDao.java:18-20`,
  `BA/dao/BookingDao.java:71-80`). results-service and capacity-sync use inline SQL only.

### Technology and versions

| Item | Value | Evidence |
|---|---|---|
| Parent | `spring-boot-starter-parent` 1.5.22.RELEASE | `services/pom.xml:10-11` |
| Java level | 1.8 | `services/pom.xml:28` |
| JDBC driver | `com.microsoft.sqlserver:mssql-jdbc` 7.4.1.jre8 | `services/pom.xml:31,39-40` |
| Utilities | commons-lang3 3.4 | `services/pom.xml:32,44-45` |
| Starters | web, jdbc, test (JUnit 4) | `services/pom.xml:53-65` |
| Build | Maven wrapper (Maven 3.8.8), fat jars from `spring-boot-maven-plugin` | `services/pom.xml:75`, `services/.mvn/wrapper/maven-wrapper.properties` |
| Pool | Tomcat JDBC pool, `test-on-borrow` with `SELECT 1`. Max active is 20 for booking-api and results-service and 5 for capacity-sync | `services/*/src/main/resources/application.properties:8-10` |
| Persistence | `JdbcTemplate`, with no ORM, no Flyway or Liquibase, and no schema ownership | all DAO classes |
| Transactions | Spring `@Transactional` with defaults: REQUIRED propagation and the driver default isolation (READ COMMITTED). No locking hints are used anywhere in the Java SQL | `BA/service/BookingService.java:57,131,148`, `RS/service/ResultIngestService.java:36` |

### Configuration

| Key | Local (`application.properties`) | Azure (`application-azure.properties`) |
|---|---|---|
| datasource url | `jdbc:sqlserver://localhost:1433;databaseName=AssessmentDelivery` (line 4 in each service) | `<your-server>.database.windows.net` placeholder (line 3) |
| user and password | `ad_app` / `${AD_DB_PASSWORD:<dev default>}`. The default password is hard-coded in all three files (lines 5-6) | `<your-app-user>` / `${AZURE_SQL_PASSWORD}` (lines 4-5) |
| JSON | dates as strings, indented (lines 12-13) | indentation off (line 7) |
| logging | `com.northwind=INFO` | `WARN` (line 8) |
| booking-api specific | `booking.channel=PARTNER`, `booking.audit-actor=booking-api` (`services/booking-api/src/main/resources/application.properties:16-17`) | not overridden |
| results-service specific | multipart 5 MB, `results.audit-actor=results-service` (`services/results-service/src/main/resources/application.properties:18-20`) | not overridden |
| capacity-sync specific | `capacity.sync.audit-actor=capacity-sync` (`services/capacity-sync/src/main/resources/application.properties:17`) | not overridden. The cron schedule is hard-coded (`CS/NightlySyncJob.java:14`) |

The channel value is configuration: `booking.channel` is injected into every insert (`BA/service/BookingService.java:114,119`).
If someone set it to `PORTAL`, trigger `trg_Booking_SeatSync` would stop firing for that channel
(`db/triggers/032_trg_Booking_SeatSync.sql:23`). The double decrement in section 4 would then become a single
decrement, so this setting silently changes seat accounting.

### How the services are started

- The supported path is `scripts\run-legacy.ps1`. It finds a JDK 8, runs `mvnw -DskipTests package` unless
  `-SkipBuild` is set, and starts each jar with `java -jar <jar> --logging.file=.run\<svc>.log`. It skips a service that
  is already running and fails if the port is held by another process (`scripts/run-legacy.ps1:331-357`).
- Manual start: `java -jar services/<svc>/target/<svc>.jar` (`services/README.md:43`). The Azure profile is selected with
  `--spring.profiles.active=azure`.
- Unit tests: `services\mvnw.cmd -B test` passes, but there are only 5 tests (section 2, "Tests").

### Endpoints and payloads

| Svc | Method and path | Request | Success | Error codes (`{"error","message"}`) | Evidence |
|---|---|---|---|---|---|
| booking-api | GET `/api/v1/sessions?examCode=&centerCode=&from=&to=` | `from` and `to` as `yyyy-MM-dd`. `to` covers the whole day | 200 `[SessionInfo]`, max 500 rows, ordered by StartsAt and SessionId, **every status** | 400 BAD_REQUEST on a bad date | `BA/web/SessionController.java:28-32`, `BA/dao/SessionDao.java:30,35-56` |
| booking-api | GET `/api/v1/sessions/{id}` | n/a | 200 `SessionInfo` plus `bookedSeats` (BOOKED+ATTENDED count) | 404 SESSION_NOT_FOUND | `BA/web/SessionController.java:44-45`, `BA/service/SeatCounter.java:17-21` |
| booking-api | POST `/api/v1/bookings` | `{sessionId, candidateNumber, accommodationCode?, externalRef?}` | 201 `{bookingId, status: BOOKED or WAITLISTED}` | 400 VALIDATION, 404 CANDIDATE_NOT_FOUND or SESSION_NOT_FOUND, 409 SESSION_NOT_OPEN, DUPLICATE_BOOKING, NO_ACCOMMODATION_SEAT or NOT_ELIGIBLE | `BA/web/BookingController.java:30-32`, `BA/service/BookingService.java:58-129` |
| booking-api | GET `/api/v1/bookings/{id}` | n/a | 200 `BookingInfo` (includes channel, externalRef, cancelReason) | 404 BOOKING_NOT_FOUND | `BA/web/BookingController.java:35-36` |
| booking-api | DELETE `/api/v1/bookings/{id}?reason=` | optional reason, default "Cancelled by partner" | 200 `BookingInfo` | 404, 409 NOT_CANCELLABLE | `BA/web/BookingController.java:44-46`, `BA/service/BookingService.java:131-145` |
| booking-api | POST `/api/v1/bookings/{id}/reschedule` | `{newSessionId}` | **200** (not 201) `{bookingId, status}`. The new booking gets a new id | 400, 404, 409 DIFFERENT_EXAM, SAME_SESSION, plus every booking error | `BA/web/BookingController.java:51-52`, `BA/service/BookingService.java:148-176` |
| booking-api | GET `/api/v1/candidates/{no}/bookings` | n/a | 200 `[BookingInfo]`, newest first | 404 CANDIDATE_NOT_FOUND | `BA/web/CandidateController.java:35-38`, `BA/dao/BookingDao.java:42-43` |
| booking-api | GET `/api/v1/candidates/{no}/eligibility?examCode=&date=` | `date` defaults to today in the JVM time zone | 200 `{eligible, reason, earliestRetakeDate}`. The reason is one of FIRST_ATTEMPT, ALREADY_PASSED, RETAKE_WAIT_NOT_ELAPSED or RETAKE_ALLOWED | 404 | `BA/web/CandidateController.java:41-59`, `services/README.md:94` |
| results-service | POST `/api/v1/results` | `{bookingId, score, scoredAt?}` | 201 `{resultId, bookingId, score, passed}` | 400 VALIDATION, 404 BOOKING_NOT_FOUND, 409 BOOKING_NOT_ACTIVE or DUPLICATE_RESULT | `RS/web/ResultsController.java:35-51`, `RS/service/ResultIngestService.java:36-80` |
| results-service | POST `/api/v1/results/upload` (multipart `file`) | CSV `candidateNumber,sessionId,score,scoredAt` | **200 even when some or all rows fail**: `{fileName, rowsRead, imported, skipped, errors:[{line,message}]}` | 400 only for an empty file or a multipart error | `RS/web/ResultsController.java:54-57`, `RS/service/ScoreFileImporter.java:31-66` |
| results-service | GET `/api/v1/results/candidate/{no}` | n/a | 200 list | 404 CANDIDATE_NOT_FOUND | `RS/web/ResultsController.java:66-70` |
| capacity-sync | POST `/sync/run` | n/a | 200 `SyncRun {status, trigger, startedAt, finishedAt, sessionsChecked, sessionsUpdated, message}` | none. Failures return `status=FAILED` with HTTP 200 | `CS/SyncController.java:16`, `CS/CapacitySyncService.java:44-86` |
| capacity-sync | GET `/sync/last` | n/a | 200 `SyncRun` from memory | none | `CS/SyncController.java:21`, `CS/CapacitySyncService.java:42,91` |

Error mapping in each service: `ApiException` maps to its own status. Malformed JSON and bad parameters map to 400 BAD_REQUEST,
with the raw Jackson or Spring exception text in `message`. Any `DataAccessException` maps to **500 DB_ERROR with the raw SQL
Server message** (`BA/web/ApiExceptionHandler.java:22-36`, `RS/web/RestErrorHandler.java:24-38`). Unmapped Spring errors,
such as a 405, return the Spring Boot default body (`timestamp/status/error/exception/message/path`). That default body
exposes the exception class name. The portal reports the same SQL failures differently: it translates only error 50000
at severity 16 into a user message (`AssessmentDelivery.Services/Common/SqlErrorHelper.cs:8-25`).

### Seat formula and capacity-sync

- The scheduled run uses `@Scheduled(cron = "0 0 2 * * *")`, which is 02:00 in the JVM's local time zone
  (`CS/NightlySyncJob.java:14`). Scheduling is enabled at `CS/CapacitySyncApplication.java:12`. `runSync` is
  `synchronized`, so it serialises runs only inside one JVM (`CS/CapacitySyncService.java:44`).
- For each `OPEN` session the job computes `expected = Capacity - AccommodationCapacity - (BOOKED+ATTENDED bookings without ET25/ET50)`,
  floored at 0 (`CS/CapacitySyncService.java:29-34,59-62`). It then runs a compare-and-set update:
  `UPDATE ... SET SeatsAvailable=? WHERE SessionId=? AND Status='OPEN' AND SeatsAvailable=<value read>`
  (`CS/CapacitySyncService.java:65-67`). The method is not `@Transactional`, so each update auto-commits. A concurrent
  booking makes the compare-and-set miss, and that session is silently skipped until the next run.
- After the run it writes one `AuditLog` row with `EntityId=0`. A failure to write that row is logged at WARN and swallowed
  (`CS/CapacitySyncService.java:96-101`).
- Every other component uses `Capacity - (BOOKED+ATTENDED)` with no accommodation reservation: `db/checks/seat-drift.sql`,
  `BA/service/SeatCounter.java:17-21`, `AssessmentDelivery.Business/Rules/SeatCounter.cs`, `docs/SYSTEM_OVERVIEW.md:46-47`.
  The proc and Java decrement paths reduce `SeatsAvailable` for ET bookings too (`db/procs/020_usp_BookSeat.sql:54-61`,
  `BA/service/BookingService.java:113-116`). capacity-sync therefore disagrees with the paths that maintain the counter
  between its runs.

### Score-file parsing and pass/fail

- Each line is split on `,` with no quoting support, so quoted values fail validation. There must be exactly 4 fields
  (`RS/service/CsvRowParser.java:28-30`). The candidate number must match `NW\d{8}` exactly, which makes it
  case-sensitive (`RS/service/CsvRowParser.java:35`). The score must be an integer from 0 to 1000
  (`RS/service/CsvRowParser.java:48-49`).
- `scoredAt` is tried against three non-lenient formats: `yyyy-MM-dd'T'HH:mm:ss`, `yyyy-MM-dd HH:mm:ss` and
  `yyyy-MM-dd`, the last meaning midnight in the JVM time zone (`RS/service/CsvRowParser.java:18,57-65`). A header line is
  detected only on line 1, by prefix (`RS/service/CsvRowParser.java:21`, `RS/service/ScoreFileImporter.java:43`). A UTF-8
  BOM is stripped (`RS/service/ScoreFileImporter.java:40-41`).
- The booking is resolved by `TOP 1 ... CandidateNumber=? AND SessionId=? AND Status IN ('BOOKED','ATTENDED')`
  (`RS/service/ResultIngestService.java:93-94`).
- Each row runs `ingest()` in its own transaction (`RS/service/ResultIngestService.java:36`), so a file can be partially
  imported. Row errors are collected as `{line,message}` (`RS/service/ScoreFileImporter.java:50-63`).
- `Passed = score >= Exam.PassingScore`, read at ingest time and stored (`RS/service/ResultIngestService.java:61-62`). The
  result is inserted, then the booking moves from BOOKED to ATTENDED, then an `AuditLog` row is written
  (`RS/service/ResultIngestService.java:67-77`). `UQ_Result_Booking` backs up the duplicate check
  (`db/schema/010_tables.sql:117`, `RS/service/ResultIngestService.java:54-57,71-72`).
- A single POST with no `scoredAt` uses the JVM clock (`new Date()`), not the database `GETDATE()`
  (`RS/web/ResultsController.java:41-42`).

## 2. Inventory

LOC counts are from `wc -l` at legacy-baseline.

| Artifact | Path | LOC or count | Purpose | Depends on |
|---|---|---|---|---|
| Parent POM | `services/pom.xml` | 87 | Versions, 3 modules, shared deps | Spring Boot 1.5.22 parent |
| Maven wrapper | `services/mvnw`, `services/mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` | 259 / 149 / 19 | Build without a local Maven install | Maven Central (downloads 3.8.8) |
| Services README | `services/README.md` | 133 | Ports, run commands, endpoint tables | n/a |
| booking-api module | `services/booking-api/pom.xml` | 32 | Jar `booking-api.jar` | parent |
| booking-api web layer | `BA/web/*.java` (5 files) | 261 | REST controllers, `ApiException`, handler | services, DAOs |
| booking-api services | `BA/service/BookingService.java`, `RetakeEligibilityService.java`, `SeatCounter.java` | 178 / 64 / 30 | Booking rules, retake rule, seat count | DAOs, `DateUtils` |
| booking-api DAOs | `BA/dao/BookingDao.java`, `SessionDao.java`, `CandidateDao.java`, `ResultDao.java` | 118 / 104 / 43 / 42 | JdbcTemplate SQL | `Booking`, `Waitlist`, `ExamSession`, `Exam`, `Candidate`, `Result`, `AuditLog`, `TestCenter`, `Room`, `usp_CancelBooking`, trigger 032 |
| booking-api models and utils | `BA/model/*.java` (8), `BA/util/DateUtils.java` | 462 / 84 | DTOs; JVM-time-zone date helpers including `addWorkingDays` | commons-lang3 |
| booking-api config | `services/booking-api/src/main/resources/application*.properties` | 19 / 8 | Local and Azure profiles | env `AD_DB_PASSWORD`, `AZURE_SQL_PASSWORD` |
| booking-api tests | `.../booking/service/RetakeEligibilityServiceTest.java` | 45 (2 tests) | First attempt; already passed | JUnit 4 |
| results-service code | `RS/**/*.java` (10 files) | 515 | Controller, CSV parser, importer, ingest | `Booking`, `Result`, `Exam`, `ExamSession`, `Candidate`, `AuditLog` |
| results-service config | `services/results-service/src/main/resources/application*.properties` | 20 / 8 | Profiles, 5 MB multipart | env as above |
| results-service tests | `.../results/service/CsvRowParserTest.java` | 32 (2 tests) | Valid row; missing field | JUnit 4 |
| capacity-sync code | `CS/*.java` (5 files) | 234 | Scheduler, sync service, controller, DTO | `ExamSession`, `Booking`, `AuditLog`, trigger 033 |
| capacity-sync config | `services/capacity-sync/src/main/resources/application*.properties` | 17 / 8 | Profiles; pool max 5 | env as above |
| capacity-sync tests | `.../capacity/CapacitySyncServiceTest.java` | 17 (1 test) | Initial `NEVER_RUN` state only | JUnit 4 |
| Total | `services/**` (java, properties, xml, md, wrapper) | 3,056 lines | | |

Tests: `services\mvnw.cmd -B test` with JDK 8 on 2026-11-11 gave
`Tests run: 2` for booking-api, `Tests run: 2` for results-service and `Tests run: 1` for capacity-sync, with
`BUILD SUCCESS`. No test touches a database, the booking flow, accommodation, working-day arithmetic, the seat formula
or CSV edge cases.

## 3. Data ownership

None of the services owns a table. All of them read and write the portal's schema (`db/schema/010_tables.sql`). Mechanism key:
JDBC means inline `JdbcTemplate` SQL, proc means a stored procedure called from Java, and trigger means a side effect fired by a Java statement.

| DB table | Op | Component | file:line | Via |
|---|---|---|---|---|
| ExamSession (+Exam, TestCenter, Room) | R | booking-api session search and detail | `BA/dao/SessionDao.java:21-23,35-60` | JDBC |
| ExamSession | R | booking-api `findExamId`, `findStartsAt`, `readSeatsAvailable` | `BA/dao/SessionDao.java:65-79` | JDBC |
| ExamSession.SeatsAvailable | U (-1) | booking-api book (BOOKED) | `BA/dao/SessionDao.java:82-83`, called at `BA/service/BookingService.java:116` | JDBC |
| ExamSession.SeatsAvailable | U (-1) | booking-api book (BOOKED, Channel<>'PORTAL') | `db/triggers/032_trg_Booking_SeatSync.sql:13-23` | trigger |
| ExamSession.SeatsAvailable | U (+1) | booking-api cancel | `BA/dao/BookingDao.java:90-92` -> `db/procs/021_usp_CancelBooking.sql:36-38` | proc |
| ExamSession.SeatsAvailable, LastModified | U | capacity-sync | `CS/CapacitySyncService.java:65-67` | JDBC |
| ExamSession.LastModified | U | every Java UPDATE of ExamSession | `db/triggers/033_trg_ExamSession_Update.sql:20` | trigger |
| Booking (+Candidate, ExamSession, Exam) | R | booking-api booking reads, duplicate check, accommodation usage | `BA/dao/BookingDao.java:25-64` | JDBC |
| Booking | I | booking-api book (BOOKED or WAITLISTED, Channel=`booking.channel`, ExternalRef) | `BA/dao/BookingDao.java:71-80` | JDBC |
| Booking | U (CANCELLED) | booking-api cancel and reschedule | `db/procs/021_usp_CancelBooking.sql:26-38` via `BA/dao/BookingDao.java:91` | proc |
| Booking | R | results-service booking lookup | `RS/service/ResultIngestService.java:42,93-94` | JDBC |
| Booking | U (BOOKED -> ATTENDED) | results-service ingest | `RS/service/ResultIngestService.java:75-76` | JDBC |
| Booking | R (count) | capacity-sync | `CS/CapacitySyncService.java:31-33` | JDBC |
| Waitlist | R (MAX(Position)), I | booking-api WAITLISTED path | `BA/dao/BookingDao.java:82-86` | JDBC |
| Waitlist | D | booking-api cancel of a WAITLISTED booking | `db/procs/021_usp_CancelBooking.sql` via `BA/dao/BookingDao.java:91` | proc |
| Candidate | R | booking-api | `BA/dao/CandidateDao.java:20-24` | JDBC |
| Candidate | R | results-service | `RS/service/ResultIngestService.java:93,100` | JDBC |
| Exam | R (ExamId by code, RetakeWaitDays) | booking-api | `BA/dao/CandidateDao.java:38-39`, `BA/service/RetakeEligibilityService.java:59-62` | JDBC |
| Exam | R (PassingScore) | results-service | `RS/service/ResultIngestService.java:42` | JDBC |
| Result (+ExamSession) | R | booking-api retake attempts | `BA/dao/ResultDao.java:24-30` | JDBC |
| Result | R, I | results-service ingest and listing | `RS/service/ResultIngestService.java:54,67-68,107` | JDBC |
| AuditLog | I | booking-api (BOOK). Cancel is audited inside the proc | `BA/dao/BookingDao.java:95-97`, `BA/service/BookingService.java:124-126` | JDBC |
| AuditLog | I | results-service | `RS/service/ResultIngestService.java:77` | JDBC |
| AuditLog | I (EntityId 0, failure swallowed) | capacity-sync | `CS/CapacitySyncService.java:96-101` | JDBC |

### Divergence matrix: Java vs portal vs SQL procs

| Rule | booking-api (Java) | Portal (C#) / procs | Evidence |
|---|---|---|---|
| Booking write | Inline INSERT, then an explicit `SeatsAvailable-1`; trigger 032 subtracts another 1 | `usp_BookSeat` under `UPDLOCK, HOLDLOCK`: inserts a PORTAL row and subtracts 1. Trigger 032 skips PORTAL rows | `BA/service/BookingService.java:113-116`, `db/procs/020_usp_BookSeat.sql:33-61`, `db/triggers/032_trg_Booking_SeatSync.sql:23` |
| Concurrency | Read, check, then write under READ COMMITTED with no locks | Row lock on the session for the whole proc | `BA/service/BookingService.java:85,110`, `db/procs/020_usp_BookSeat.sql:34` |
| Duplicate check | BOOKED or WAITLISTED on the same session | Same | `BA/dao/BookingDao.java:52-55`, `db/procs/020_usp_BookSeat.sql:42` |
| Accommodation code validation | Trim, upper-case, must be in {ET25, ET50, SR, RB} | `AccommodationCodes.TryParse`. A blank field uses `Candidate.DefaultAccommodationCode` | `BA/service/BookingService.java:62-67`, `AssessmentDelivery.Services/Booking/BookingService.cs:100-104` |
| Extra-time seat | ET25/ET50 count (BOOKED+ATTENDED) must be below `AccommodationCapacity` | Same check in the proc | `BA/dao/BookingDao.java:60-64`, `db/procs/020_usp_BookSeat.sql:45-51` |
| Retake wait | `addWorkingDays(lastSessionDate, RetakeWaitDays)` (Mon-Fri) | `lastAttempt.Date.AddDays(retakeWaitDays)` (calendar) | `BA/service/RetakeEligibilityService.java:51`, `AssessmentDelivery.Business/Rules/RetakeRules.cs:24-25` |
| Session search | All statuses, including past and CANCELLED | `Status == "OPEN"` only | `BA/dao/SessionDao.java:35-56`, `AssessmentDelivery.Services/Booking/BookingService.cs:28` |
| Cancel | Pre-check in Java, then `usp_CancelBooking` (+1) | `usp_CancelBooking` (+1) | `BA/service/BookingService.java:137-141`, `AssessmentDelivery.Services/Booking/BookingService.cs:136` |
| Reschedule | Java `cancel()` then partner `book()`: -2 on the target, re-checks retake and accommodation, copies ExternalRef | `usp_RescheduleBooking` = cancel proc + book proc (-1 on the target), with no retake re-check in SQL | `BA/service/BookingService.java:147-176`, `db/procs/023_usp_RescheduleBooking.sql:27-38` |
| Seat count | BOOKED+ATTENDED (`SeatCounter`). capacity-sync uses Capacity-AccCap-nonET | BOOKED+ATTENDED; `seat-drift.sql` uses Capacity-(BOOKED+ATTENDED) | `BA/service/SeatCounter.java:17-21`, `CS/CapacitySyncService.java:29-34,59`, `db/checks/seat-drift.sql` |
| Errors | JSON `{error,message}`, raw SQL text on 500 | `PortalException` from SQL 50000/16 only | `BA/web/ApiExceptionHandler.java:33-36`, `AssessmentDelivery.Services/Common/SqlErrorHelper.cs:8-25` |

## 4. Hidden behaviours

All repros were run against the live estate on the Windows VM on 2026-11-11 unless marked otherwise. They ran in the order
01 to 12 on one freshly seeded database, so later outputs include the effects of earlier repros (the drift on session 155,
for example). Script 01 was re-run once after a fix to its booking-id extraction, so its output starts from session 155 as
script 02 left it. Run them with `"$BASH" docs/as-is/repros/java-services/NN-*.sh` (Git Bash) or `run-all.sh`.
`seat_row` prints `SessionId Capacity AccCap SeatsAvailable SeatHolding ExpectedPerSeatDriftSql`, where the last
column is `Capacity - (BOOKED+ATTENDED)`.

### Partner booking decrements SeatsAvailable twice

- **Where:** `BA/service/BookingService.java:113-116` and `BA/dao/SessionDao.java:82-83` explicitly subtract 1.
  `db/triggers/032_trg_Booking_SeatSync.sql:13-23` subtracts 1 again for every inserted `BOOKED` row whose
  `Channel <> 'PORTAL'`. The partner channel is `PARTNER` (`services/booking-api/src/main/resources/application.properties:16`).
- **What happens:** every successful partner booking takes 2 seats from the counter but creates 1 seat-holding row.
  `bookedSeats` in the session detail is correct while `seatsAvailable` is too low. These two fields come from the same
  response, `BA/web/SessionController.java:44-45`.
- **Migration impact:** this is high impact. Whether the decrement lives in the app or in the trigger is undocumented;
  `docs/SYSTEM_OVERVIEW.md:46-47` calls the counter "denormalised" without naming who maintains it. A port that keeps only
  one of the two decrements changes availability numbers in production. A port that keeps both carries the bug forward.
- **Repro:** `01-partner-double-decrement.sh` (session 155, candidate NW00000911). Observed:

```
$ before                      155 16 0 12 4 12
$ curl -X POST http://localhost:8081/api/v1/bookings -d '{"sessionId":155,"candidateNumber":"NW00000911","externalRef":"HB-DD-1"}'
{ "bookingId" : 3599, "status" : "BOOKED" }   HTTP 201
$ after partner booking       155 16 0 10 5 11     <- seats 12 -> 10, seat-holding 4 -> 5
$ curl -X GET http://localhost:8081/api/v1/sessions/155
  "capacity" : 16, "seatsAvailable" : 10, "bookedSeats" : 5
```

### A partner book followed by a cancel leaks one seat

- **Where:** a booking subtracts 2 (previous item). A cancel adds 1 through `usp_CancelBooking`
  (`BA/dao/BookingDao.java:90-92`, `db/procs/021_usp_CancelBooking.sql:36-38`).
- **What happens:** each partner book-and-cancel cycle permanently lowers `SeatsAvailable` by 1 until capacity-sync runs.
  The seat-drift check (`db/checks/seat-drift.sql`) starts reporting the session.
- **Migration impact:** drift accumulates with partner traffic. Any reconciliation or data migration that copies
  `SeatsAvailable` instead of recomputing it will import wrong counters.
- **Repro:** continuation of `01-partner-double-decrement.sh`:

```
$ curl -X DELETE http://localhost:8081/api/v1/bookings/3599?reason=repro
{ "bookingId" : 3599, "status" : "CANCELLED", "channel" : "PARTNER", "externalRef" : "HB-DD-1", "cancelReason" : "repro", ... }  HTTP 200
$ after partner cancel        155 16 0 11 4 12     <- back to 4 holders but 11 seats (expected 12)
```

### The counter goes negative, and free seats are sold as waitlist

- **Where:** the booking decision reads only `SeatsAvailable > 0` (`BA/service/BookingService.java:110-123`).
  The Java update has no floor (`BA/dao/SessionDao.java:83`) and neither does trigger 032.
- **What happens:** the double decrement drives the counter below the real number of free seats and then below zero. Partner
  and portal bookings both go to WAITLISTED while the session still has physical seats. Seat-holding and capacity are not
  consulted. `usp_BookSeat` makes the same `@Seats > 0` decision (`db/procs/020_usp_BookSeat.sql:54`), so portal customers
  are affected too.
- **Migration impact:** customer-visible lost sales and waitlists with no cause visible in any UI. The target system must
  decide whether availability is derived or stored, and must replay or recompute before cut-over.
- **Repro:** `03-negative-seats-phantom-full.sh` (session 157, capacity 16). Observed:

```
$ before                      157 16 0 5 11 5
NW00000904 -> {"bookingId":3591,"status":"BOOKED"}
NW00000905 -> {"bookingId":3592,"status":"BOOKED"}
NW00000906 -> {"bookingId":3593,"status":"BOOKED"}
NW00000907 -> {"bookingId":3594,"status":"WAITLISTED"}     <- 14 of 16 seats held, still waitlisted
$ curl -X GET http://localhost:8081/api/v1/sessions/157
  "capacity" : 16, "seatsAvailable" : -1, "status" : "OPEN", "bookedSeats" : 14
```

### Partner reschedule bypasses `usp_RescheduleBooking` and applies different rules

- **Where:** `BA/service/BookingService.java:147-176`. The Java code calls `cancel()` (the proc) and then the ordinary
  partner `book()`, which copies `AccommodationCode` and `ExternalRef` (`BA/service/BookingService.java:169-176`). The
  portal uses `usp_RescheduleBooking` (`AssessmentDelivery.Services/Booking/BookingService.cs:177`,
  `db/procs/023_usp_RescheduleBooking.sql:27-38`).
- **What happens:** the source session gets +1 and the target session gets -2 (Java decrement plus trigger), so the
  operation is not seat-neutral. The partner path re-runs retake eligibility, the duplicate check and the extra-time seat
  check on the target; the proc path checks only same-exam and seat availability. The new row inherits the old
  `ExternalRef`, so two rows (one CANCELLED, one BOOKED) share the reference. The response is 200 with a **new** bookingId.
- **Migration impact:** the two reschedule paths differ in seat effect, eligibility rules and identifiers. Partners that
  track a booking by id must follow the id change, and reports keyed on ExternalRef will double-count.
- **Repro:** `02-partner-reschedule.sh` (155 -> 164, candidate NW00000902). Observed:

```
booked: { "bookingId" : 3589, "status" : "BOOKED" }
$ before reschedule           155 16 0 9 5 11  |  164 19 0 9 10 9
$ curl -X POST http://localhost:8081/api/v1/bookings/3589/reschedule -d '{"newSessionId":164}'
{ "bookingId" : 3590, "status" : "BOOKED" }   HTTP 200
$ after reschedule            155 16 0 10 4 12 |  164 19 0 7 11 8     <- 155 +1, 164 -2
BookingId SessionId Status    Channel ExternalRef CancelReason
3589      155       CANCELLED PARTNER HB-RS-1     Rescheduled
3590      164       BOOKED    PARTNER HB-RS-1     NULL
```

### ExternalRef is stored but is not an idempotency key

- **Where:** `ExternalRef VARCHAR(40) NULL` has no unique constraint or index (`db/schema/010_tables.sql:86`). Java checks
  only its length (`BA/service/BookingService.java:69-71`) and inserts it as given (`BA/dao/BookingDao.java:71-80`).
  Duplicates are detected only by candidate and session (`BA/dao/BookingDao.java:52-55`).
- **What happens:** a retried POST with the same ExternalRef gets 409 DUPLICATE_BOOKING instead of the original 201 and
  bookingId. The same ExternalRef used for a different candidate creates a second booking. After a cancel, a retry creates a
  new booking.
- **Migration impact:** partners cannot safely retry. A target API that adds `Idempotency-Key` semantics will change
  behaviour that partner integrations may depend on, so agree the contract first.
- **Repro:** `05-idempotency-externalref.sh`. Observed:

```
POST {"sessionId":165,"candidateNumber":"NW00000909","externalRef":"PTR-IDEM-1"}  -> 201 {"bookingId":3596,"status":"BOOKED"}
POST same body again                                                               -> 409 {"error":"DUPLICATE_BOOKING", ...}
POST {"sessionId":165,"candidateNumber":"NW00000910","externalRef":"PTR-IDEM-1"}  -> 201 {"bookingId":3597,"status":"BOOKED"}
BookingId SessionId CandidateId Status ExternalRef
3596      165       909         BOOKED PTR-IDEM-1
3597      165       910         BOOKED PTR-IDEM-1
```

### The partner channel ignores the candidate's default accommodation

- **Where:** Java uses only the request field (`BA/service/BookingService.java:62-68`). `CandidateDao` loads
  `DefaultAccommodationCode` (`BA/dao/CandidateDao.java:22`) but never uses it. The portal applies it when the field is blank
  (`AssessmentDelivery.Services/Booking/BookingService.cs:100-101`).
- **What happens:** a candidate with ET50 on their profile gets a standard seat with no extra time when booked by a
  partner. No accommodation seat is checked or reserved.
- **Migration impact:** this is an accessibility and compliance risk. A unified booking service has to pick one rule, and
  either choice changes the behaviour of one channel.
- **Repro:** `06-default-accommodation.sh`. Observed:

```
CandidateNumber DefaultAccommodationCode
NW00000903      ET50
POST {"sessionId":165,"candidateNumber":"NW00000903"} -> 201 {"bookingId":3598,"status":"BOOKED"}
BookingId Status AccommodationCode Channel
3598      BOOKED NULL              PARTNER
```

### Retake wait is counted in working days by booking-api and in calendar days by the portal

- **Where:** `BA/service/RetakeEligibilityService.java:50-51` (`addWorkingDays`, which skips Saturday and Sunday,
  `BA/util/DateUtils.java:67-79`). The portal uses `lastAttempt.AddDays(retakeWaitDays)`
  (`AssessmentDelivery.Business/Rules/RetakeRules.cs:24-25`). The overview documents "30 days"
  (`docs/SYSTEM_OVERVIEW.md:52-53`). The Java comment says "30 day wait" (`BA/service/RetakeEligibilityService.java:50`).
- **What happens:** with 30 wait days, a partner candidate waits about 6 weeks while a portal candidate waits 30 days. On
  the dates in between, the same candidate is eligible in the portal and rejected by the partner API. Holidays are not
  considered.
- **Migration impact:** the business rule has to be decided before consolidating. Characterization tests are needed for
  both rules, including weekend boundaries.
- **Repro:** `07-retake-working-days.sh` (NW00000113, HPI-ACC1, last failed sitting 2026-11-07, 30 days). Observed:

```
LastAttempt             Passed RetakeWaitDays CalendarRuleEarliest
2026-11-07 13:30:00.000 0      30             2026-12-07
GET /api/v1/candidates/NW00000113/eligibility?examCode=HPI-ACC1&date=2026-12-07
{ "eligible" : false, "reason" : "RETAKE_WAIT_NOT_ELAPSED", "earliestRetakeDate" : "2026-12-18" }
GET ...&date=2026-12-18 -> { "eligible" : true, "reason" : "RETAKE_ALLOWED", "earliestRetakeDate" : "2026-12-18" }
POST /api/v1/bookings {"sessionId":206,"candidateNumber":"NW00000113"}   (session 206 starts 2026-12-07)
{ "error" : "NOT_ELIGIBLE", "message" : "... (RETAKE_WAIT_NOT_ELAPSED), earliest retake date 2026-12-18" }  HTTP 409
```

The portal side, which allows a booking on or after 2026-12-07, is inferred from `RetakeRules.cs:24-27`. It was not run
here because this slice started the estate with `-NoPortal`. The portal smoke test belongs to the parent session.

### Session search returns closed, cancelled and past sessions

- **Where:** `BA/dao/SessionDao.java:35-56` has no status filter, and `MAX_ROWS = 500` (`BA/dao/SessionDao.java:30`). The
  portal filters `Status == "OPEN"` (`AssessmentDelivery.Services/Booking/BookingService.cs:28`). The README describes the
  result as "sessions with seatsAvailable" (`services/README.md:68`).
- **What happens:** partners see DELIVERED and CANCELLED sessions with a `seatsAvailable` value. Once the estate holds more
  than 500 sessions, the list silently truncates to the oldest 500, because it is ordered by StartsAt.
- **Migration impact:** partner UIs may filter on their side and depend on this. Any change to the filter or limit alters
  the API contract.
- **Repro:** `08-session-list-not-open.sh`. Observed:

```
curl http://localhost:8081/api/v1/sessions | count by status
     18 "status" : "CANCELLED"
      8 "status" : "CLOSED"
    151 "status" : "DELIVERED"
    285 "status" : "OPEN"
curl '...?from=2026-09-01&to=2026-09-30'  ->  2 CANCELLED, 63 DELIVERED
```

### capacity-sync uses its own seat formula, and its runs make the shared drift check report sessions

- **Where:** `CS/CapacitySyncService.java:29-34` counts only non-ET BOOKED and ATTENDED bookings, and
  `CS/CapacitySyncService.java:59-62` subtracts `AccommodationCapacity` and floors the result at 0. Every other component uses
  `Capacity - (BOOKED+ATTENDED)` (`db/checks/seat-drift.sql`, `BA/service/SeatCounter.java:17-21`,
  `docs/SYSTEM_OVERVIEW.md:46-47`). The UPDATE also fires `trg_ExamSession_Update`, which sets `LastModified`
  (`db/triggers/033_trg_ExamSession_Update.sql:20`).
- **What happens:** the nightly run fixes the partner drift and also rewrites every session that has accommodation seats to
  `Capacity - AccCap - standardBooked`. Accommodation seats then disappear from `seatsAvailable` until the next booking
  path changes the counter again. Right after a run, `seat-drift.sql` reports many OPEN sessions as drifted. Each run touches
  `LastModified` on every changed session. CLOSED sessions are not synced.
- **Migration impact:** this is high impact. There is no single definition of "available seats", and the 02:00 job and the
  daytime booking paths keep moving the counter back and forth. Any data-quality baseline taken after 02:00 will look
  different from one taken in the afternoon.
- **Repro:** `09-capacity-sync-formula.sh`. Observed:

```
seat-drift row count before: 5            <- caused by the partner repros above
seat_row 154 before:  154 20 2 11 9 11
$ curl -X POST http://localhost:8083/sync/run
{ "status" : "COMPLETED", "trigger" : "MANUAL", "sessionsChecked" : 285, "sessionsUpdated" : 116, "message" : null }
seat-drift row count after: 112           <- all OPEN
seat_row 154 after:   154 20 2 9 9 11     <- 2 accommodation seats removed from availability
AuditLog: ExamSession 0 CAPACITY_SYNC "trigger=MANUAL status=COMPLETED checked=285 updated=116" capacity-sync
second POST /sync/run -> "sessionsUpdated" : 0     (idempotent under its own formula)
```

### `/sync/last` is held in memory, and capacity-sync swallows audit failures

- **Where:** `CS/CapacitySyncService.java:42` (`volatile SyncRun lastRun = new SyncRun()`) and
  `CS/CapacitySyncService.java:84,91`. Audit write failures go to `LOG.warn` and are swallowed at
  `CS/CapacitySyncService.java:100-101`. A failed run returns HTTP 200 with `status=FAILED`
  (`CS/CapacitySyncService.java:77-79`).
- **What happens:** after a restart or redeploy, `/sync/last` reports `NEVER_RUN` even though `AuditLog` holds the runs.
  Monitoring that polls `/sync/last` cannot tell "never ran" from "restarted", and an HTTP 200 does not mean the sync worked.
  With several instances, each instance runs the 02:00 job, because `synchronized` locks only within one JVM.
- **Migration impact:** operational monitoring and scaling (for example App Service scale-out) need a persisted job state
  and a distributed lock or single scheduler.
- **Repro:** `12-sync-last-restart.sh`. It stops the capacity-sync JVM and restarts it with
  `scripts\run-legacy.ps1 -SkipDb -SkipBuild -NoPortal`. Observed:

```
GET /sync/last -> { "status" : "COMPLETED", "trigger" : "MANUAL", "sessionsChecked" : 285, ... }
CapacitySyncAuditRows 2
stopped pid 6012
[run-legacy] starting capacity-sync on :8083 (log .run\capacity-sync.log)
[run-legacy] capacity-sync up on :8083
GET /sync/last -> { "status" : "NEVER_RUN", "trigger" : null, "startedAt" : null, "sessionsChecked" : 0, ... }
CapacitySyncAuditRows 2
```

### results-service accepts a score for a session that has not happened yet and marks the booking ATTENDED

- **Where:** `RS/service/ResultIngestService.java:42-62` checks only the booking status (BOOKED or ATTENDED), the
  duplicate check and the score range. It does not check `ExamSession.Status` or `StartsAt`. The booking is moved to ATTENDED
  at `RS/service/ResultIngestService.java:75-76`. `scoredAt` defaults to the JVM clock
  (`RS/web/ResultsController.java:41-42`). `Passed` is frozen from the current `PassingScore`
  (`RS/service/ResultIngestService.java:61-62`).
- **What happens:** a mistyped bookingId records a result against a future OPEN session and switches the candidate's booking
  to ATTENDED. The booking can no longer be cancelled (`BA/service/BookingService.java:137-138`). The fail then starts the
  retake clock from that session's future date (`BA/dao/ResultDao.java:24-30`). A later change to `Exam.PassingScore` does
  not re-grade stored results.
- **Migration impact:** characterization tests should pin these guards, or their absence, so a new results API does not
  silently tighten or loosen them. Stored `Passed` values must be migrated as they are, not recomputed.
- **Repro:** `10-results-ingest.sh` (booking 1956, session 223 starts 2026-12-15 and is OPEN). Observed:

```
BookingId CandidateNumber Status SessionId StartsAt                SessionStatus ExamCode PassingScore
1956      NW00000038      BOOKED 223       2026-12-15 09:00:00.000 OPEN          HPI-ACC2 650
$ curl -X POST http://localhost:8082/api/v1/results -d '{"bookingId":1956,"score":100}'
{ "resultId" : 1445, "bookingId" : 1956, "score" : 100, "passed" : false }   HTTP 201
BookingId Status       ResultId Score Passed ScoredAt                SourceFile
1956      ATTENDED     1445     100   0      2026-11-11 16:55:48.763 NULL
```

### Score-file upload commits row by row, returns 200 on partial failure, and has strict, undocumented parsing rules

- **Where:** `RS/service/ScoreFileImporter.java:50-63` imports each row in its own transaction
  (`RS/service/ResultIngestService.java:36`) and turns failures into line errors. `RS/service/CsvRowParser.java:28,35` uses a
  plain `split(",")`, so quoted values are not unwrapped, and requires `NW\d{8}`, which makes the candidate number
  case-sensitive. `RS/service/CsvRowParser.java:18` accepts three date formats; date-only means midnight. The importer
  always returns HTTP 200 (`RS/web/ResultsController.java:54-63`).
- **What happens:** a file is partially imported, and the caller has to inspect `errors[]` to notice. A re-upload of the same
  file is rejected row by row as `A result already exists`, with no file-level duplicate detection. `SourceFile` is stored
  but never checked. Lower-case or quoted candidate numbers, which spreadsheet exports commonly produce, are rejected.
- **Migration impact:** a batch or message-based replacement has to choose between all-or-nothing and per-row commits.
  Partners may rely on partial success and on the error format.
- **Repro:** `11-score-file-upload.sh`. Observed:

```
candidateNumber,sessionId,score,scoredAt
NW00000447,223,880,2026-12-15
nw00000856,223,700,2026-12-15T12:00:00
"NW00000856",223,700,2026-12-15T12:00:00
NW00000856,223,700,2026-12-15 12:00:00
NW00000410,153,500,2026-12-15T12:00:00
$ curl -F file=@NWTEST_20261211.csv http://localhost:8082/api/v1/results/upload      -> HTTP 200
{ "rowsRead" : 5, "imported" : 2, "skipped" : 3, "errors" : [
  { "line" : 3, "message" : "bad candidateNumber 'nw00000856'" },
  { "line" : 4, "message" : "bad candidateNumber '\"NW00000856\"'" },
  { "line" : 6, "message" : "no active booking for NW00000410 on session 153" } ] }
upload the same file again -> HTTP 200 { "imported" : 0, "skipped" : 5, errors: line 2 "A result already exists for booking 1957", line 5 "... booking 1958", ... }
ResultId BookingId Score Passed ScoredAt                SourceFile
1446     1957      880   1      2026-12-15 00:00:00.000 NWTEST_20261211.csv
1447     1958      700   1      2026-12-15 12:00:00.000 NWTEST_20261211.csv
```

## 5. Migration risks

| Risk | Impact | Likelihood | Evidence | Mitigation / characterization test |
|---|---|---|---|---|
| Seat counter double-maintained by Java code and trigger 032; porting only one side changes availability | H | H | `BA/service/BookingService.java:116`, `db/triggers/032_trg_Booking_SeatSync.sql:13-23` | Test: one partner booking lowers SeatsAvailable by exactly N (pin today's 2). Decide on a derived counter or a single writer, and drop the trigger or the app decrement together |
| No single seat formula (sync vs procs vs drift check); data reconciliation baselines conflict | H | H | `CS/CapacitySyncService.java:29-34,59`, `db/checks/seat-drift.sql`, `docs/SYSTEM_OVERVIEW.md:46-47` | Agree one formula with the business, rewrite seat-drift and sync from it, and recompute counters during migration instead of copying them |
| Partner booking race: read-check-write under READ COMMITTED without locks, so overbooking or duplicate rows are possible under concurrency | H | M | `BA/service/BookingService.java:85,110-116`, compare `db/procs/020_usp_BookSeat.sql:34` | `04-race-duplicate.sh` ran 8 parallel identical POSTs and got one 201 plus seven 409s, so a duplicate was **not** reproduced at that load. Add a load characterization test with many candidates on a 1-seat session and add a unique filtered index or locking |
| Waitlist position `MAX(Position)+1` without a lock produces duplicate positions under concurrency | M | M | `BA/dao/BookingDao.java:82-86` | Concurrent waitlist test; use a sequence or a lock in the target |
| Retake rule differs by channel (working vs calendar days) | H | H | `BA/service/RetakeEligibilityService.java:51`, `AssessmentDelivery.Business/Rules/RetakeRules.cs:25` | Business decision; parameterized tests across weekends and month ends for both rules |
| Default accommodation ignored by the partner channel | H | M | `BA/service/BookingService.java:62-68`, `AssessmentDelivery.Services/Booking/BookingService.cs:100-101` | Decide on the rule; add a test for a candidate with ET50 default and a blank request field |
| Reschedule semantics differ (seat effect, eligibility re-check, ExternalRef copy, new id) | M | H | `BA/service/BookingService.java:147-176`, `db/procs/023_usp_RescheduleBooking.sql:27-38` | Contract tests for both paths; decide whether reschedule keeps or changes the booking id |
| ExternalRef not idempotent; partner retries give 409 or duplicates | M | H | `db/schema/010_tables.sql:86`, `BA/service/BookingService.java:69-71` | Define an idempotency contract with partners before changing behaviour; test retry semantics |
| Session search returns all statuses, capped at 500 oldest-first | M | M | `BA/dao/SessionDao.java:30,35-56` | Pin the current response shape with a contract test; plan versioned API changes |
| Raw SQL Server messages and exception class names exposed in HTTP responses | M | H | `BA/web/ApiExceptionHandler.java:33-36`, `RS/web/RestErrorHandler.java:35-38` | Not run live (needs a DB fault). Test error shapes; sanitize in the target behind the same `error` codes |
| results-service accepts results for future or non-delivered sessions and freezes `Passed` | M | M | `RS/service/ResultIngestService.java:42-62,75-76` | Characterization test with a future session; migrate `Passed` as stored |
| Score-file per-row commit and HTTP 200 on partial failure; re-upload not detected at file level | M | H | `RS/service/ScoreFileImporter.java:50-63`, `RS/web/ResultsController.java:54-63` | Golden-file tests for the parser (quotes, case, BOM, 3 date formats) and the report format |
| capacity-sync state in memory, duplicate runs when scaled out, audit failures swallowed, failures return 200 | M | M | `CS/CapacitySyncService.java:42-44,77-79,100-101`, `CS/NightlySyncJob.java:14` | Persist the run state; use a single scheduler or distributed lock; alert on `status=FAILED` |
| JVM default time zone drives cron (02:00), date-only parsing, `today()` and `scoredAt` default | M | M | `CS/NightlySyncJob.java:14`, `BA/util/DateUtils.java:47,61,68`, `RS/service/CsvRowParser.java:18,57`, `RS/web/ResultsController.java:42` | Pin `-Duser.timezone` in tests; make the zone explicit in the target |
| Dev DB password default hard-coded in three properties files; Azure profile only placeholders | M | H | `services/*/src/main/resources/application.properties:6`, `application-azure.properties:3-5` | Move to secret store or managed identity; remove defaults |
| Very thin automated tests (5 unit tests, no DB tests) | H | H | `services/*/src/test/**` (section 2) | Build the characterization suite from the repros in `docs/as-is/repros/java-services/` before refactoring |
| End-of-life stack (Spring Boot 1.5, Java 8, mssql-jdbc 7.4) | M | H | `services/pom.xml:10-11,28,31` | Upgrade path via Boot 2.7 then 3.x; JUnit 4 tests need a migration |

## 6. Open questions

1. Which seat formula is the business rule? Should accommodation seats be reserved out of `SeatsAvailable` (capacity-sync)
   or counted inside it (procs, overview)? (`CS/CapacitySyncService.java:59` vs `docs/SYSTEM_OVERVIEW.md:46-47`)
2. The code comment says partner bookings bypass `usp_BookSeat` because the proc "did not take Channel/ExternalRef" in
   2014 (`BA/dao/BookingDao.java:18-20`). Was trigger 032 added later without anyone removing the explicit Java decrement,
   and is anyone compensating for the resulting drift other than capacity-sync? (`db/triggers/032_trg_Booking_SeatSync.sql:2`)
3. Is the retake wait meant to be working days, which matches the Java implementation and an "exam owner policy" comment
   (`BA/service/RetakeEligibilityService.java:50`), or calendar days, as in the portal and overview?
4. Do partners rely on ExternalRef being reusable, or on a reschedule changing the bookingId?
5. Which time zone do the JVMs and SQL Server run in for production? On this VM both reported UTC, as the 16:55 timestamps
   show at 10:55 local time.
6. Is anything writing `Channel='IMPORT'` bookings? Trigger 032 would decrement for them too, but no Java code path writes
   that channel. (`db/schema/010_tables.sql:85,92`)
7. Should results be rejected for sessions that are not DELIVERED? Should `Passed` be recomputed when `PassingScore` changes?
8. Is capacity-sync deployed as a single instance in Azure? Two instances would run the 02:00 job twice.

## Repro index

| Script | Section 4 item | Run? |
|---|---|---|
| `common.sh` | helpers (`sql`, `api`, `seat_row`, `val`) | n/a |
| `01-partner-double-decrement.sh` | double decrement, book+cancel leak | run |
| `02-partner-reschedule.sh` | reschedule divergence | run |
| `03-negative-seats-phantom-full.sh` | negative counter / phantom full | run |
| `04-race-duplicate.sh` | race risk (section 5) | run, duplicate not reproduced |
| `05-idempotency-externalref.sh` | ExternalRef not idempotent | run |
| `06-default-accommodation.sh` | default accommodation ignored | run |
| `07-retake-working-days.sh` | working vs calendar days | run (portal side inferred, not run) |
| `08-session-list-not-open.sh` | session search statuses | run |
| `09-capacity-sync-formula.sh` | capacity-sync formula | run |
| `10-results-ingest.sh` | result on future session | run |
| `11-score-file-upload.sh` | score-file partial import | run |
| `12-sync-last-restart.sh` | in-memory `/sync/last` | run |
