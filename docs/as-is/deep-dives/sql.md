# As-is deep dive: SQL Server database (`AssessmentDelivery`)

Northwind Assessment Network legacy estate. Source of truth: tag `legacy-baseline` (commit `e458238`). All `path:line` references are relative to the repository root at that commit. Every repro in this document was run on the Windows VM (Windows Server 2022, SQL Server 2022 Express `16.0.1000.6`, go-sqlcmd `1.6.0`) against a database built by `scripts\run-legacy.ps1 -NoPortal` / `db\apply.ps1`. Repro scripts live in [`docs/as-is/repros/sql/`](../repros/sql/); every script that writes wraps its writes in `BEGIN TRAN ... ROLLBACK`, so the seed stays intact (`06b` and `13` write nothing).

Run all repros (after `db\apply.ps1`):

```powershell
powershell -ExecutionPolicy Bypass -File docs\as-is\repros\sql\run-all.ps1
# or one script:
sqlcmd -S localhost,1433 -U sa -P "Str0ng!Passw0rd" -C -W -s "|" -d AssessmentDelivery -i docs\as-is\repros\sql\02_partner_double_decrement.sql
```

(From a PowerShell started by a non-interactive tool, redirect stdin (`< NUL` / `< /dev/null`) or go-sqlcmd `-i` can hang.)

## 1. Scope and architecture

### Components

| Component | What it does with the database | Evidence |
|---|---|---|
| SQL Server database `AssessmentDelivery` | 9 tables, 4 stored procedures, 4 triggers; the only integration point between the portal and the Java services | `db/schema/010_tables.sql:20-130`, `db/procs/*`, `db/triggers/*` |
| Legacy .NET portal (`AssessmentDelivery.Web` + `AssessmentDelivery.Services`, EF6) | Reads via EF; candidate booking, cancel and reschedule through procs; admin capacity change through a proc; test-centre edits straight through EF | `AssessmentDelivery.Services/Booking/BookingService.cs:115-120,135-139,176-180`, `AssessmentDelivery.Services/Admin/AdminService.cs:59,78-79` |
| `booking-api` (Java/Spring, :8081) | Partner bookings: direct JDBC `INSERT` into `Booking`/`Waitlist`/`AuditLog` plus its own `SeatsAvailable` decrement; cancel through `usp_CancelBooking` | `services/booking-api/src/main/java/com/northwind/assessment/booking/dao/BookingDao.java:76,84-85,91,96`, `.../dao/SessionDao.java:83` |
| `results-service` (Java/Spring, :8082) | Score-file ingest: direct JDBC `INSERT Result`, `UPDATE Booking SET Status='ATTENDED'`, `INSERT AuditLog` | `services/results-service/src/main/java/com/northwind/ad/results/service/ResultIngestService.java:67,75,77` |
| `capacity-sync` (Java/Spring, :8083) | Nightly (02:00) and on-demand (`POST /sync/run`) rewrite of `ExamSession.SeatsAvailable` for OPEN sessions; summary `AuditLog` row | `services/capacity-sync/src/main/java/com/northwind/assessment/capacity/CapacitySyncService.java:29-34,65,96`, `.../NightlySyncJob.java:14`, `.../SyncController.java:9,16` |
| `db/apply.ps1`, `db/apply.sh` | Dev rebuild: drop database, run `schema -> procs -> triggers -> seed` in file-name order | `db/apply.ps1:44,47-55`, `db/apply.sh:42,48` |
| `db/checks/*.sql` | Read-only diagnostics (row counts, seat drift) | `db/checks/row-counts.sql`, `db/checks/seat-drift.sql:11-27` |

### Request / data flow

- Portal booking: controller -> `BookingService.Book` -> retake check in C# (`AssessmentDelivery.Services/Booking/BookingService.cs:106`) -> `EXEC dbo.usp_BookSeat` (`:115-120`) -> proc locks the session row, inserts `Booking` (Channel `PORTAL`), decrements `SeatsAvailable` or appends `Waitlist`, writes `AuditLog` (`db/procs/020_usp_BookSeat.sql:31-82`).
- Partner booking: `booking-api` `BookingService.book` (`@Transactional`, `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:57`) re-implements the proc's checks in Java, reads `SeatsAvailable` without a lock (`:110`, `SessionDao.java:79`), inserts `Booking` with Channel `PARTNER` (`BookingDao.java:76`), then decrements `SeatsAvailable` itself (`BookingService.java:116`, `SessionDao.java:83`); `trg_Booking_SeatSync` fires on the same insert (`db/triggers/032_trg_Booking_SeatSync.sql:12-24`).
- Cancel / reschedule (both channels): `usp_CancelBooking` / `usp_RescheduleBooking` (`BookingService.cs:135-139,176-180`, `BookingDao.java:91`).
- Capacity change (portal admin): `usp_ChangeCapacity` (`AdminService.cs:78-79`) -> `UPDATE ExamSession` -> `trg_ExamSession_Update` may demote bookings to the waitlist (`db/triggers/033_trg_ExamSession_Update.sql:24-71`).
- Results: score file -> `results-service` -> direct writes (`ResultIngestService.java:36-79`). No database object reacts to `Result` writes or to the `Booking` status update.
- Counter repair: `capacity-sync` overwrites `SeatsAvailable` with its own formula (`CapacitySyncService.java:59-67`).
- Every component connects as the single SQL login `ad_app` (`AssessmentDelivery.Web/Web.config:12`, `services/*/src/main/resources/application.properties:4-5`).

### Technology and versions

- Scripts target "SQL Server 2012+", developed against SQL Server 2022 Developer in Docker (`db/README.md:3-4`). Observed here: SQL Server 2022 Express RTM `16.0.1000.6`, database compatibility level 160, recovery model SIMPLE, collation `SQL_Latin1_General_CP1_CI_AS` (repro 13 output below). No `ALTER DATABASE ... SET COMPATIBILITY_LEVEL` or collation is pinned in `db/schema/000_create_database.sql:9-10`, so both follow the instance defaults.
- All temporal columns are `DATETIME` populated with `GETDATE()` defaults (server-local clock; `db/schema/010_tables.sql:28,49,62,76,87,90,103,116,128`). `TestCenter` carries a time-zone name, but `ExamSession.StartsAt` has no offset.
- T-SQL features in use: `SET XACT_ABORT ON`, `TRY/CATCH`, `RAISERROR` (no `THROW`), `UPDLOCK, HOLDLOCK` hints, `ROW_NUMBER()`, `TRIGGER_NESTLEVEL`, `DISABLE/ENABLE TRIGGER` in seed.

### Configuration

| Setting | Default | Where |
|---|---|---|
| Server | `localhost,1433` (`AD_SQL_SERVER`) | `db/apply.ps1:14`, `db/README.md:42` |
| Admin user / password | `sa` / `Str0ng!Passw0rd` (`AD_SQL_USER`, `AD_SA_PASSWORD`) | `db/apply.ps1:15-16`, `db/README.md:42-43` |
| `sqlcmd` path | `sqlcmd` on PATH (`SQLCMD`) | `db/apply.ps1:17-25`, `db/apply.sh:8,22-25` |
| App login | `ad_app` / `Northw1nd!Dev2011`, `CHECK_POLICY = OFF` | `db/schema/000_create_database.sql:13-14` |
| App permissions | `db_datareader`, `db_datawriter`, `GRANT EXECUTE` (database-wide) | `db/schema/000_create_database.sql:23-25` |
| sqlcmd flags | `-C -b -I` (trust cert, stop on error, QUOTED_IDENTIFIER ON) | `db/apply.ps1:28-30`, `db/apply.sh:29-31` |

### How it is started

- `scripts\run-legacy.ps1` installs go-sqlcmd (Chocolatey) and SQL Server 2022 Express if missing, then runs `db\apply.ps1` unless `-SkipDb` (`scripts/run-legacy.ps1:8,24,70-99,247-250`). Observed in this session: install of both, then `db\apply.ps1` re-created and seeded the database (log lines `SQL Server instance MSSQLSERVER (MSSQLSERVER) running`, `db\apply.ps1: re-creating AssessmentDelivery (existing data is dropped; -SkipDb keeps it)`, `-- seed/049_seed_finalise.sql`, `== done`). A full `db\apply.ps1` rebuild took 6.5 s.
- `db/apply.ps1` / `db/apply.sh`: wait for the server, `ALTER DATABASE ... SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE` (`db/apply.ps1:44`, `db/apply.sh:42`), then every `*.sql` in `schema`, `procs`, `triggers`, `seed` sorted by name (`db/apply.ps1:47-55`, `db/apply.sh:48`); `-NoSeed` / `--no-seed` skips `seed` (`db/apply.ps1:48`, `db/apply.sh:19`). Each file is run against `master`; the files `USE AssessmentDelivery` themselves.
- Note for this run: the Java services could not be started in this session (`run-legacy.ps1` aborted at `Maven build failed (exit 1)` / `Cannot start maven from wrapper` after the DB was built), so behaviours that involve the Java services were reproduced by replaying the exact SQL statements those services issue.

## 2. Inventory

| Artifact | Path | LOC / count | Purpose | Depends on |
|---|---|---|---|---|
| Database + login | `db/schema/000_create_database.sql` | 27 | `CREATE DATABASE`, login/user `ad_app`, reader/writer roles, `GRANT EXECUTE` | sysadmin on target instance |
| Tables | `db/schema/010_tables.sql` | 130; 9 tables, 5 non-clustered indexes, 13 FKs, 3 CHECKs, 4 UNIQUEs | `TestCenter`, `Room`, `Exam`, `ExamSession`, `Candidate`, `Booking`, `Waitlist`, `Result`, `AuditLog` | `000` |
| `usp_BookSeat` | `db/procs/020_usp_BookSeat.sql` | 92 | Portal booking: lock session, validate, `BOOKED` + decrement or `WAITLISTED` + waitlist row, audit | `ExamSession`, `Booking`, `Waitlist`, `AuditLog`; `trg_Booking_SeatSync` (no-op for PORTAL) |
| `usp_CancelBooking` | `db/procs/021_usp_CancelBooking.sql` | 56 | Cancel BOOKED/WAITLISTED; give seat back or delete waitlist row; audit | `Booking`, `ExamSession`, `Waitlist`, `AuditLog`; `trg_ExamSession_Update` |
| `usp_ChangeCapacity` | `db/procs/022_usp_ChangeCapacity.sql` | 53 | Change capacity, shift `SeatsAvailable` by delta (floor 0), audit | `ExamSession`, `AuditLog`; `trg_ExamSession_Update` (demotions) |
| `usp_RescheduleBooking` | `db/procs/023_usp_RescheduleBooking.sql` | 51 | Same-exam move = `usp_CancelBooking` + `usp_BookSeat` in one transaction | `021`, `020` |
| `trg_Candidate_Audit` | `db/triggers/030_trg_Candidate_Audit.sql` | 45 | `AFTER UPDATE, DELETE` on `Candidate`: audit name/email/default-accommodation changes and deletes | `AuditLog` |
| `trg_Exam_Audit` | `db/triggers/031_trg_Exam_Audit.sql` | 34 | `AFTER INSERT, UPDATE` on `Exam`: one audit row per row | `AuditLog` |
| `trg_Booking_SeatSync` | `db/triggers/032_trg_Booking_SeatSync.sql` | 26 | `AFTER INSERT` on `Booking`: decrement `SeatsAvailable` for non-PORTAL BOOKED rows | `ExamSession`; fires `trg_ExamSession_Update` |
| `trg_ExamSession_Update` | `db/triggers/033_trg_ExamSession_Update.sql` | 74 | `AFTER UPDATE` on `ExamSession`: stamp `LastModified`; on capacity decrease demote newest BOOKED to WAITLISTED, append waitlist, recount seats | `Booking`, `Waitlist` |
| Seed reference | `db/seed/040_seed_reference.sql` | 113 | Wipe all tables, reseed identities, 21 centres, 36 rooms, 12 exams | all tables; `trg_Exam_Audit` (writes the 12 audit rows) |
| Seed candidates | `db/seed/041_seed_candidates.sql` | 46 | 1,000 candidates, `NW00000901`-`NW00000999` unassigned | `Candidate` |
| Seed sessions | `db/seed/042_seed_sessions.sql` | 48 | 462 sessions 2026-09-01..2027-03-31, status by `@AsOf = 20261111` | `Exam`, `Room`, `TestCenter` |
| Seed bookings | `db/seed/043_seed_bookings.sql` | 129 | 3,587 bookings + 34 waitlist rows, anchored at `20261110 18:00` | `ExamSession`, `Candidate`; `trg_Booking_SeatSync` |
| Seed results | `db/seed/044_seed_results.sql` | 78 | 1,444 results; ATTENDED only for sessions before `20261108` | `Booking`, `Exam` |
| Seed history | `db/seed/045_seed_history.sql` | 104 | Fixed retake histories (10 extra sessions) | all booking tables |
| Seed finalise | `db/seed/049_seed_finalise.sql` | 27 | Recompute `SeatsAvailable`, restore seeded `LastModified` with `trg_ExamSession_Update` disabled | `ExamSession`, `Booking` |
| Check: row counts | `db/checks/row-counts.sql` | 24 | Rows per table and bookings by session/booking status | all tables |
| Check: seat drift | `db/checks/seat-drift.sql` | 28 | Sessions where `SeatsAvailable <> Capacity - (BOOKED + ATTENDED)` | `ExamSession`, `Booking`, `Exam`, `TestCenter` |
| Apply (Windows) | `db/apply.ps1` | 60 | Drop + rebuild + optional seed | go-sqlcmd |
| Apply (bash) | `db/apply.sh` | 58 | Same as `apply.ps1` | sqlcmd / mssql-tools18 |
| README | `db/README.md` | 77 | Ordering, apply, credentials, seed contents, checks | — |
| Repros (this deep dive) | `docs/as-is/repros/sql/` | 15 `.sql` + 2 runners | Characterization scripts referenced below | seeded DB |

Observed baseline after a fresh seed (`db/checks/row-counts.sql`, `db/checks/seat-drift.sql`):

```text
TableName|Rows        SessionStatus|BookingStatus|Bookings
TestCenter|21         CANCELLED|CANCELLED|72
Room|36               CLOSED|BOOKED|32
Exam|12               DELIVERED|ATTENDED|1444
ExamSession|462       DELIVERED|BOOKED|106
Candidate|1000        DELIVERED|CANCELLED|110
Booking|3587          OPEN|BOOKED|1673
Waitlist|34           OPEN|CANCELLED|116
Result|1444           OPEN|WAITLISTED|34
AuditLog|12
seat-drift.sql: (no rows)
```

Booking channel mix after seed: `PORTAL 2155`, `PARTNER 925`, `IMPORT 507`; 211 sessions have `AccommodationCapacity > 0` (matches `db/README.md:56,58`).

## 3. Data ownership

R = read, I = insert, U = update, D = delete. "Via" is the access mechanism at the database boundary. All components use the same login `ad_app`, so the database cannot tell writers apart (`AssessmentDelivery.Web/Web.config:12`, `services/*/src/main/resources/application.properties:5`).

| DB table | Op | Component | file:line | Via |
|---|---|---|---|---|
| `ExamSession` | R | portal (session search, reschedule targets) | `AssessmentDelivery.Services/Booking/BookingService.cs:28,155` | ORM (EF6) |
| `ExamSession` | U (`SeatsAvailable`) | portal booking | `db/procs/020_usp_BookSeat.sql:60-62` (called from `BookingService.cs:115-120`) | proc |
| `ExamSession` | U (`SeatsAvailable`) | portal / booking-api cancel | `db/procs/021_usp_CancelBooking.sql:36-39` (`BookingService.cs:135-139`, `BookingDao.java:91`) | proc |
| `ExamSession` | U (`Capacity`, `SeatsAvailable`) | portal admin | `db/procs/022_usp_ChangeCapacity.sql:31-36` (`AdminService.cs:78-79`) | proc |
| `ExamSession` | U (`SeatsAvailable`) | booking-api partner booking | `services/booking-api/.../dao/SessionDao.java:83` (`BookingService.java:116`) | JDBC |
| `ExamSession` | U (`SeatsAvailable`) | any non-PORTAL BOOKED insert | `db/triggers/032_trg_Booking_SeatSync.sql:18-24` | trigger |
| `ExamSession` | U (`LastModified`, `SeatsAvailable`) | any `ExamSession` update | `db/triggers/033_trg_ExamSession_Update.sql:19-22,66-70` | trigger |
| `ExamSession` | U (`SeatsAvailable`) | capacity-sync | `services/capacity-sync/.../CapacitySyncService.java:65-67` | JDBC |
| `ExamSession` | R | booking-api (seats, exam, start) | `services/booking-api/.../dao/SessionDao.java:24-27,67,73,79` | JDBC |
| `ExamSession` | R | capacity-sync | `CapacitySyncService.java:29-34` | JDBC |
| `Booking` | I | portal booking | `db/procs/020_usp_BookSeat.sql:56-57,68-69` | proc |
| `Booking` | I | booking-api partner booking | `services/booking-api/.../dao/BookingDao.java:76` | JDBC |
| `Booking` | U (`CANCELLED`) | portal / booking-api cancel, reschedule | `db/procs/021_usp_CancelBooking.sql:32-34`, `db/procs/023_usp_RescheduleBooking.sql:37` | proc |
| `Booking` | U (`WAITLISTED`) | capacity decrease | `db/triggers/033_trg_ExamSession_Update.sql:48-51` | trigger |
| `Booking` | U (`ATTENDED`) | results-service | `services/results-service/.../ResultIngestService.java:75-76` | JDBC |
| `Booking` | R | portal (my bookings), booking-api, results-service | `BookingService.cs:79,86`, `BookingDao.java:28-31,48,54,62`, `ResultIngestService.java:43-44,93` | ORM / JDBC |
| `Waitlist` | I | portal booking (full session) | `db/procs/020_usp_BookSeat.sql:72-74` | proc |
| `Waitlist` | I | booking-api partner booking (full session) | `BookingDao.java:84-85` | JDBC |
| `Waitlist` | I | capacity decrease | `db/triggers/033_trg_ExamSession_Update.sql:60-64` | trigger |
| `Waitlist` | D | cancel of a WAITLISTED booking | `db/procs/021_usp_CancelBooking.sql:40-41` | proc |
| `Result` | I | results-service | `ResultIngestService.java:67-70` | JDBC |
| `Result` | R | booking-api retake check, results-service | `services/booking-api/.../dao/ResultDao.java:27`, `ResultIngestService.java:54,111-112` | JDBC |
| `Candidate` | R | booking-api, results-service, portal | `services/booking-api/.../dao/CandidateDao.java:23`, `ResultIngestService.java:100` | JDBC / ORM |
| `Candidate` | I/U/D | none found in application code at legacy-baseline (seed only: `db/seed/041_seed_candidates.sql`) | — | — |
| `Exam` | R | booking-api retake rule, results-service pass mark | `services/booking-api/.../service/RetakeEligibilityService.java:60`, `CandidateDao.java:39`, `ResultIngestService.java:43-44` | JDBC |
| `Exam` | I/U | none found in application code (seed only: `db/seed/040_seed_reference.sql`) | — | — |
| `TestCenter` | U | portal admin (edit centre) | `AssessmentDelivery.Services/Admin/AdminService.cs:47-59` | ORM (EF6 `SaveChanges`) |
| `Room` | R | portal, booking-api | `SessionDao.java:27` | ORM / JDBC |
| `AuditLog` | I | portal booking / cancel / capacity | `db/procs/020_usp_BookSeat.sql:79-80`, `021_usp_CancelBooking.sql:43-44`, `022_usp_ChangeCapacity.sql:38-41` | proc |
| `AuditLog` | I | candidate / exam changes | `db/triggers/030_trg_Candidate_Audit.sql:13-43`, `db/triggers/031_trg_Exam_Audit.sql:16-32` | trigger |
| `AuditLog` | I | booking-api partner booking | `BookingDao.java:96` (`BookingService.java:122-124`) | JDBC |
| `AuditLog` | I | results-service ingest | `ResultIngestService.java:77-79` | JDBC |
| `AuditLog` | I | capacity-sync run summary (`EntityId = 0`) | `CapacitySyncService.java:96-99` | JDBC |
| all tables | D (all rows) | seed / apply | `db/seed/040_seed_reference.sql:17-25`, `db/apply.ps1:44` | script |

Invariants that only hold because of procs/triggers (the schema does not enforce them; repro 12 shows each one being violated by a plain DML statement that the `ad_app` role is allowed to run):

- `SeatsAvailable = Capacity - (BOOKED + ATTENDED)` — maintained by `020`, `021`, `022`, `032`, `033` and asserted only by `db/checks/seat-drift.sql:18-26`.
- One active (BOOKED/WAITLISTED) booking per candidate per session — `db/procs/020_usp_BookSeat.sql:40-43` and `BookingDao.java:54` only.
- Extra-time bookings never exceed `AccommodationCapacity` — `db/procs/020_usp_BookSeat.sql:45-52` and `BookingDao.java:59-68` only.
- Exactly one `Waitlist` row per WAITLISTED booking, consecutive positions — `020:72-74`, `021:40-41`, `033:53-64`, `BookingDao.java:84-85`.
- Every capacity/booking change is audited — only where a proc or Java writer remembers to insert into `AuditLog`.
- Retake wait (`Exam.RetakeWaitDays`) — enforced only in application code (`BookingService.cs:106`, `RetakeEligibilityService.java:60`), never in SQL (`db/procs/020_usp_BookSeat.sql:9`).

## 4. Hidden behaviours

Unless stated otherwise, "Observed" is verbatim `sqlcmd -W -s "|"` output from the named repro script on the seeded database.

### Partner bookings decrement `SeatsAvailable` twice

- **Where:** `db/triggers/032_trg_Booking_SeatSync.sql:18-24`; `services/booking-api/src/main/java/com/northwind/assessment/booking/dao/BookingDao.java:76`; `services/booking-api/src/main/java/com/northwind/assessment/booking/dao/SessionDao.java:83`; `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:113-117`.
- **What happens:** booking-api inserts the partner booking directly with `Channel = 'PARTNER'`. The trigger decrements `SeatsAvailable` for every BOOKED non-PORTAL insert (it was written for the 2013 bulk imports, `032:1-6`), and booking-api then runs its own `UPDATE ... SET SeatsAvailable = SeatsAvailable - 1`. Each partner booking consumes two seats from the counter; the session shows "full" early and `seat-drift.sql` reports drift of +1 per partner booking. The nightly capacity-sync later overwrites the counter (with yet another formula, see below), masking the drift.
- **Migration impact:** the counter is not a reliable source of truth; any migration that copies `SeatsAvailable` instead of recomputing it inherits drift, and any re-implementation that keeps both the trigger and an explicit decrement reproduces the bug. Partner and portal availability differ intra-day.
- **Repro:** `02_partner_double_decrement.sql` replays the two statements booking-api issues inside a rolled-back transaction.
- **Observed:**

```text
SessionId|Capacity|SeatsBefore
152|28|16
SeatsAfter|ExpectedPerSeatDriftCheck
14|15
```

### `trg_Booking_SeatSync` only handles INSERT of BOOKED non-PORTAL rows

- **Where:** `db/triggers/032_trg_Booking_SeatSync.sql:12-24`; default `Channel = 'PORTAL'` at `db/schema/010_tables.sql:85`.
- **What happens:** the trigger is `AFTER INSERT` only and filters `Status = 'BOOKED' AND Channel <> 'PORTAL'`. Consequences: (a) any direct insert that omits `Channel` gets the default `PORTAL` and does not touch the counter; (b) promoting a WAITLISTED booking to BOOKED with an `UPDATE` does not decrement; (c) `DELETE` of a booking does not give the seat back; (d) there is no floor, so a bulk import into a full session drives `SeatsAvailable` negative. Multi-row inserts are handled correctly (grouped by `SessionId`).
- **Migration impact:** the counter is only correct if every writer follows the proc/trigger conventions; a data migration or new writer that uses `UPDATE`/`DELETE`, or forgets `Channel`, silently corrupts availability.
- **Repro:** `03_seatsync_gaps.sql` (rolled back).
- **Observed:**

```text
SessionId|Step|SeatsAvailable|ActiveBookings
152|start|16|12
152|direct INSERT, default channel PORTAL|16|13
152|IMPORT WAITLISTED then UPDATE to BOOKED|16|14
152|IMPORT BOOKED insert (trigger -1)|15|15
152|DELETE that booking (no trigger)|15|14
FullSessionId|Capacity|SeatsAvailable
151|9|-2
```

### Stored-procedure "error codes" are RAISERROR states, and system errors are re-raised as user errors

- **Where:** documented codes `db/procs/020_usp_BookSeat.sql:5-8`, `db/procs/023_usp_RescheduleBooking.sql:5`; actual raises `020:38,43,51`, `021_usp_CancelBooking.sql:30`, `022_usp_ChangeCapacity.sql:29`, `023:31`; CATCH re-raise `020:85-87`, `021:49-51`, `022:46-48`, `023:44-46`; portal translation `AssessmentDelivery.Services/Common/SqlErrorHelper.cs:9,25-26`.
- **What happens:** the headers promise errors 50001-50006, but every `RAISERROR` uses an ad-hoc message, so SQL Server returns message number 50000 and the "code" is only in `ERROR_STATE()` (1 not open, 2 duplicate, 3 no accommodation seat, 4 not cancellable, 5 invalid capacity, 6 wrong exam). Each CATCH block re-raises `ERROR_MESSAGE()` with severity 16 and the original state, so a foreign-key violation (547) or deadlock (1205) also surfaces as 50000 / severity 16 — and the portal's `SqlErrorHelper` turns every 50000/16 into a user-facing `PortalException` with the raw message. Deadlock victims therefore look like business errors (no retry), and constraint text leaks to users.
- **Migration impact:** callers that switch on error number see one code; a new API must map on state or message text, and must not treat re-raised system errors as validation failures.
- **Repro:** `01_error_numbers.sql`.
- **Observed:**

```text
-- book a CLOSED session (documented as 50001)
ErrNumber|Sev|State|Msg
50000|16|1|Session 263 is not open for booking.
-- book a non-existent candidate on an OPEN session (FK violation)
ErrNumber|Sev|State|Msg
50000|16|0|The INSERT statement conflicted with the FOREIGN KEY constraint "FK_Booking_Candidate". The conflict occurred in databas
```

### Lowering capacity silently demotes the most recent bookings to the waitlist

- **Where:** `db/triggers/033_trg_ExamSession_Update.sql:24-71` (header `033:1-4` describes only `LastModified`); `db/procs/022_usp_ChangeCapacity.sql:2-3,31-36`.
- **What happens:** when an `UPDATE` lowers `Capacity` below the number of BOOKED rows, the trigger flips the newest BOOKED bookings (`ORDER BY BookedAt DESC, BookingId DESC`, `033:35`) to WAITLISTED, appends them after any existing waitlist (`033:53-64`, oldest demoted first), and recounts `SeatsAvailable = Capacity - BOOKED` (`033:66-68`), overriding the proc's "delta, floor 0" arithmetic. Only one `CAPACITY` audit row is written by the proc; there is no audit row or notification per demoted candidate. The decision and the recount look at BOOKED only, so on sessions that already have ATTENDED rows capacity can be shrunk below the number of occupied seats without any demotion, and the drift check then disagrees with the stored counter.
- **Migration impact:** a "change capacity" API has an undocumented, candidate-visible side effect (confirmed candidates lose their seat). It applies to any writer that updates `Capacity`, not only the proc. Must be preserved or deliberately retired with a business decision; the BOOKED-vs-ATTENDED inconsistency should be fixed rather than copied.
- **Repro:** `04_capacity_reduction_demotes.sql` (rolled back).
- **Observed:**

```text
SessionId|Capacity|SeatsAvailable|Booked
151|9|0|9
Capacity|SeatsAvailable
7|0
Status|N
BOOKED|7
CANCELLED|1
WAITLISTED|2
BookingId|Position|Status|BookedAt
1566|1|WAITLISTED|2026-11-09 11:32:00.000
1567|2|WAITLISTED|2026-11-09 21:03:00.000
EntityName|EntityId|Action|Detail|Actor
ExamSession|151|CAPACITY|9 -> 7|repro
DeliveredSessionId|Capacity|SeatsAvailable|Booked|Attended
1|15|8|1|6
Capacity|SeatsAvailable|SeatDriftCheckExpects
6|0|-1
```

### Freed seats never promote the waitlist; waitlist positions develop gaps

- **Where:** `db/procs/021_usp_CancelBooking.sql:2-4,36-41`; `db/procs/022_usp_ChangeCapacity.sql:31-36`.
- **What happens:** cancelling a BOOKED booking adds a seat back to `SeatsAvailable`; raising capacity adds seats; neither touches WAITLISTED bookings (promotion is a manual admin step per `021:3-4`). The session then shows free seats while candidates are still waitlisted, and the next portal or partner booker jumps the queue. Cancelling a WAITLISTED booking deletes its `Waitlist` row without renumbering, leaving gaps (1, 3).
- **Migration impact:** queue fairness depends on staff action; a new system that auto-promotes changes candidate-visible behaviour. Code that assumes `Position` is dense (1..n) breaks.
- **Repro:** `05_no_waitlist_promotion.sql` (rolled back).
- **Observed:**

```text
SessionId|Capacity|SeatsAvailable
153|22|0
Capacity|SeatsAvailable|StillWaitlisted
24|3|3
BookingId|Position
1603|1
1605|3
```

### Rescheduling can lose a confirmed seat and accepts the same session

- **Where:** `db/procs/023_usp_RescheduleBooking.sql:23-33,35-41`; caller-side guards `services/booking-api/.../service/BookingService.java:148-177` (rejects same session) and `AssessmentDelivery.Services/Booking/BookingService.cs:154-170`.
- **What happens:** the proc validates only that both sessions exist and belong to the same exam, reading them without locks before the transaction starts (`023:23-27`). It then cancels the old booking and books the new session. If the target is full, the candidate's BOOKED seat is cancelled and the new booking is WAITLISTED (the proc returns `WAITLISTED`, no error). Rescheduling into the same session is accepted: the old booking becomes `CANCELLED` with reason `Rescheduled` and a new booking id is issued. The cancel inside the reschedule passes no `@Actor`, so its audit row is attributed to the SQL login.
- **Migration impact:** "reschedule" is not atomic from the candidate's point of view; new implementations must decide whether to refuse a move into a full session. Booking ids change on every reschedule, so external references to booking ids (partner `ExternalRef` lookups, result files) go stale.
- **Repro:** `06_reschedule.sql` (rolled back).
- **Observed:**

```text
Case_|OldBooking|NewBooking|NewStatus|OldStatus|OldReason
same session|1564|3595|BOOKED|CANCELLED|Rescheduled
Case_|OldBooking|OldStatus|FullSession|NewBooking|NewStatus
into full session|1565|CANCELLED|217|3596|WAITLISTED
```

### A failing proc rolls back the caller's whole transaction

- **Where:** `db/procs/020_usp_BookSeat.sql:85`, `021_usp_CancelBooking.sql:49`, `022_usp_ChangeCapacity.sql:46`, `023_usp_RescheduleBooking.sql:44` (`IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION`), combined with `SET XACT_ABORT ON`.
- **What happens:** the procs do not use save points. When one fails inside an outer transaction (booking-api's `@Transactional` cancel/reschedule, `BookingService.java:131,148`, or `usp_RescheduleBooking` calling the other two), the CATCH block rolls back everything the caller had done, and SQL Server additionally raises Msg 266 (transaction count mismatch). The caller's own `COMMIT`/`ROLLBACK` then fails with Msg 3903. In `usp_RescheduleBooking` the inner error is re-raised once more; the return code is 1 and `@@TRANCOUNT` is 0.
- **Migration impact:** callers cannot compose these procs with other work in one transaction; Java code that wraps them in Spring transactions gets "no corresponding BEGIN TRANSACTION" errors on top of the real error. Characterize before replacing with a service layer.
- **Repro:** first (unfixed) run of `04_capacity_reduction_demotes.sql` called `usp_ChangeCapacity` with a NULL session inside `BEGIN TRAN`; `06b_reschedule_error_path.sql` runs the nested error path without an outer transaction.
- **Observed:**

```text
Msg 50000, Level 16, State 5, Server devinbox, Procedure dbo.usp_ChangeCapacity, Line 39
Invalid capacity (null) for session (null).
Msg 266, Level 16, State 2, Server devinbox, Procedure dbo.usp_ChangeCapacity, Line 0
Transaction count after EXECUTE indicates a mismatching number of BEGIN and COMMIT statements. Previous count = 1, current count = 0.
Msg 3903, Level 16, State 1, Server devinbox, Line 31
The ROLLBACK TRANSACTION request has no corresponding BEGIN TRANSACTION.

-- 06b_reschedule_error_path.sql
Msg 50000, Level 16, State 4, Server devinbox, Procedure dbo.usp_RescheduleBooking, Line 36
Booking 1 cannot be cancelled.
BookingId|TargetSession|ReturnCode|NewBookingId|TranCount
1|152|1|NULL|0
```

### `usp_BookSeat` trusts the caller for retake rules and accommodation codes

- **Where:** `db/procs/020_usp_BookSeat.sql:9,20,40-52`; `db/schema/010_tables.sql:84` (no CHECK on `AccommodationCode`); app-side checks `AssessmentDelivery.Services/Booking/BookingService.cs:106`, `services/booking-api/.../service/RetakeEligibilityService.java:60`.
- **What happens:** (a) the proc never looks at `Result`/`RetakeWaitDays`, so any direct `EXEC` (or a new caller) can book a candidate who failed 9 days ago into a 30-day wait window; (b) any accommodation code is accepted and stored (`'XYZ'`), only `ET25`/`ET50` consume accommodation seats; (c) the accommodation check runs before the "full -> waitlist" branch, so an extra-time candidate on a full session whose accommodation pool is exhausted gets an error instead of a waitlist place, unlike a standard candidate; (d) the duplicate check ignores ATTENDED, so a candidate can hold an ATTENDED and a new BOOKED booking on the same session.
- **Migration impact:** business rules are split between SQL and two application code bases (C# and Java) that must stay in sync; a migration must consolidate retake and accommodation validation in one place and decide what to do with invalid codes already stored.
- **Repro:** `07_bookseat_rules.sql` (rolled back; the third case first sets the session's `AccommodationCapacity` to 0 to simulate a used-up pool).
- **Observed:**

```text
Case_|BookingId|Status|AccommodationCode
unknown accommodation code|3597|BOOKED|XYZ
Case_|CandidateId|FailedSitting|NewSitting|RetakeWaitDays|DaysApart|Status
retake inside wait window|113|2026-11-07 13:30:00.000|2026-11-16 13:30:00.000|30|9|BOOKED
Case_|ErrNumber|State|Msg
ET on full session|50000|3|No accommodation seat available on session 151.
```

### `ExamSession.LastModified` is always overwritten, and `OUTPUT` without `INTO` fails on four tables

- **Where:** `db/triggers/033_trg_ExamSession_Update.sql:16-22`; triggers on `Candidate`, `Exam`, `Booking`, `ExamSession` (`db/triggers/030:13-14`, `031:10-11`, `032:12-13`, `033:10-11`); seed workaround `db/seed/049_seed_finalise.sql:12-13,26`.
- **What happens:** every `UPDATE` on `ExamSession` (even `SET Status = Status`) stamps `LastModified = GETDATE()` regardless of the value the writer supplied, which is why the seed must disable the trigger to keep its fixed timestamps. Any statement with `OUTPUT inserted.*` (no `INTO`) against a table with an enabled trigger fails with Msg 334 — this blocks ORMs that use `OUTPUT` for generated keys (e.g. EF Core's default SQL Server insert/update batching) and is why the Java writers use `SCOPE_IDENTITY()` (`BookingDao.java:76-78`, `ResultIngestService.java:67-68`). `OUTPUT ... INTO @table` works.
- **Migration impact:** `LastModified` cannot be preserved through a data migration that replays `UPDATE`s with triggers enabled; ORM upgrades need `OUTPUT`-free configuration (e.g. EF Core `UseSqlOutputClause(false)` / `HasTrigger`) or the triggers must be removed first.
- **Repro:** `08_lastmodified_and_output.sql` (rolled back; the `OUTPUT` statements run as dynamic SQL so the compile-time error can be caught).
- **Observed:**

```text
SessionId|SeededLastModified
151|2026-11-10 18:00:00.000
AfterExplicit2020Update|Verdict
2026-11-11 16:57:15.780|overwritten by trigger
Case_|ErrNumber|Msg
ExamSession OUTPUT|334|The target table 'dbo.ExamSession' of the DML statement cannot have any enabled triggers if the statement contains an OUTPUT clause without INTO claus
Case_|ErrNumber|Msg
Booking OUTPUT|334|The target table 'dbo.Booking' of the DML statement cannot have any enabled triggers if the statement contains an OUTPUT clause without INTO clause.
Case_|RowsCaptured
Booking OUTPUT INTO @table|1
```

### Audit trail is partial and attributes most rows to the shared SQL login

- **Where:** `db/schema/010_tables.sql:127` (`Actor DEFAULT SUSER_SNAME()`); `db/procs/020_usp_BookSeat.sql:79-80` (no `@Actor` parameter); `db/procs/023_usp_RescheduleBooking.sql:37` (no actor passed to cancel); `db/triggers/030_trg_Candidate_Audit.sql:13-14,34-37` (UPDATE/DELETE only, four columns); `db/triggers/031_trg_Exam_Audit.sql:16-32`; `services/capacity-sync/.../CapacitySyncService.java:94-103` (audit failure swallowed, `EntityId = 0`).
- **What happens:** portal `BOOK` rows, trigger-written rows and the reschedule cancel get `Actor = ad_app` (the login all components share), so who booked is not recorded. Not audited at all: candidate inserts, changes to `DateOfBirth`, any `Booking` status change outside the procs (results-service ATTENDED, trigger demotions, manual promotions), `Result` writes other than results-service's own row, `TestCenter`/`Room` edits (portal admin uses EF `SaveChanges`, `AdminService.cs:47-59`). `trg_Exam_Audit` writes a row for every update even when no tracked column changed (Detail only `code=...`). capacity-sync logs one summary row with `EntityId = 0` and continues if the insert fails.
- **Migration impact:** the audit log cannot be used as an event source or to rebuild history; compliance claims ("support staff edits are tracked", `030:3-4`) only partly hold. Requirements for actor propagation must be set before replacing the triggers.
- **Repro:** `09_audit_actor_and_coverage.sql` (runs the writes under `EXECUTE AS LOGIN = 'ad_app'`, then lists new audit rows; rolled back). The seven writes produced three audit rows.
- **Observed:**

```text
EntityName|EntityId|Action|Detail|Actor
Booking|3599|BOOK|session=152 status=BOOKED|ad_app
Booking|3599|CANCEL|repro|portal
Exam|1|UPDATE|code=HPI-ACC1|ad_app
```

(No rows for the candidate insert, the `DateOfBirth` update, the direct `Booking` status update, or the `TestCenter` rename.)

### `Result.Passed` is frozen at ingest time; pass-mark changes do not re-grade

- **Where:** `db/schema/010_tables.sql:106-117` (no computed column or CHECK tying `Passed` to `Score`); `services/results-service/.../ResultIngestService.java:61-70`; `db/triggers/031_trg_Exam_Audit.sql:23-24` (pass-mark change is audited, nothing else).
- **What happens:** results-service compares the score to the exam's current `PassingScore` and stores the boolean. Raising `Exam.PassingScore` afterwards is logged (`pass 720->730`) but existing `Result` rows keep their old verdicts. Retake eligibility in booking-api reads `Result` (`services/booking-api/.../dao/ResultDao.java:27`), so it uses the stored verdict.
- **Migration impact:** `Passed` is historical data, not derivable; migrations must copy it, not recompute it. Reports that recompute pass/fail from `Score` will disagree with the legacy outcome for any exam whose pass mark changed.
- **Repro:** `10_result_passed_frozen.sql` (rolled back).
- **Observed:**

```text
ExamId|PassingScore
5|720
Results|PassedFlag|ScoreMeetsCurrentPassMark
209|67|67
Results|PassedFlag|ScoreMeetsNewPassMark
209|67|61
Action|Detail
UPDATE|code=BCB-PM200 pass 720->730
```

### Three different `SeatsAvailable` formulas; capacity-sync introduces drift every night

- **Where:** seed and check `db/seed/049_seed_finalise.sql:16-23`, `db/checks/seat-drift.sql:18-26` (`Capacity - (BOOKED + ATTENDED)`); trigger `db/triggers/033_trg_ExamSession_Update.sql:66-68` (`Capacity - BOOKED`); capacity-sync `services/capacity-sync/.../CapacitySyncService.java:27-34,59-67` (`Capacity - AccommodationCapacity - non-extra-time (BOOKED + ATTENDED)`, floor 0), scheduled `NightlySyncJob.java:14` (`0 0 2 * * *`).
- **What happens:** capacity-sync "repairs" the counter by holding back the whole accommodation pool from the general pool, which neither `usp_BookSeat` (`020:54`) nor the check query does. On the fresh seed it would rewrite 112 of 285 OPEN sessions, after which `seat-drift.sql` reports 112 drifting sessions; during the day the procs then adjust the rewritten value by ±1. Which number is "right" depends on which component you ask; the portal and booking-api both decide BOOKED vs WAITLISTED from the stored counter.
- **Migration impact:** availability semantics (is the accommodation pool part of `SeatsAvailable`?) must be decided before migration; reconciliation tests comparing legacy and new systems will flap depending on whether the nightly job has run. The service could not be started on this VM (Maven wrapper failure), so its exact SQL was replayed.
- **Repro:** `11_capacity_sync_formula.sql` replays the service's `SELECT` and `UPDATE` for all OPEN sessions (rolled back). Live equivalent: `curl -X POST http://localhost:8083/sync/run` then `db/checks/seat-drift.sql` — **not run** (Java services not running in this session).
- **Observed:**

```text
OpenSessions|WithAccommodationSeats
285|132
DriftRowsBefore
0
SessionsCapacitySyncWouldChange
112
DriftRowsAfter
112
```

### Business invariants the schema does not enforce

- **Where:** `db/schema/010_tables.sql:52-66` (`ExamSession`: no CHECK on `SeatsAvailable`/`Capacity`/`AccommodationCapacity`, `RoomId` and `TestCenterId` independent FKs), `:79-95` (`Booking`: no unique active booking, no CHECK on `AccommodationCode`), `:97-104` (`Waitlist`: no unique `(SessionId, Position)` or `BookingId`, no FK consistency with `Booking`), `:106-117` (`Result` repeats `CandidateId`/`ExamId`/`SessionId` with no consistency check); no cascade from session status to bookings.
- **What happens:** every invariant listed in section 3 can be broken by a single DML statement from `ad_app`. Cancelling a session (`Status = 'CANCELLED'`) leaves its BOOKED and WAITLISTED bookings untouched.
- **Migration impact:** the legacy data must be profiled for violations before loading into a stricter target schema (new constraints will reject legacy rows); the seed data is clean (`seat-drift.sql` returns no rows) but production data written by the Java services may not be.
- **Repro:** `12_missing_constraints.sql` (rolled back).
- **Observed:**

```text
Invariant|AcceptedBySchema
SeatsAvailable >= 0|yes
SeatsAvailable <= Capacity|yes
AccommodationCapacity <= Capacity|yes
Session.RoomId belongs to Session.TestCenterId|yes
one active booking per candidate per session|yes
AccommodationCode in (ET25,ET50,SR,RB)|yes
unique Waitlist (SessionId, Position) / BookingId|yes
Waitlist Session/Candidate match its Booking|yes
cancelling a session cancels its bookings|yes
Status|BookingsOnCancelledSession
BOOKED|12
CANCELLED|1
WAITLISTED|3
```

### Unlocked read-then-write in the Java partner path races with the proc

- **Where:** `services/booking-api/.../service/BookingService.java:110-120` (`readSeatsAvailable` then insert); `services/booking-api/.../dao/SessionDao.java:79`; `services/booking-api/.../dao/BookingDao.java:54,62,84-85` (duplicate check, accommodation count and `MAX(Position)+1` without locks); contrast `db/procs/020_usp_BookSeat.sql:33-34` (`UPDLOCK, HOLDLOCK`).
- **What happens:** under the default READ COMMITTED isolation, two partner bookings (or a partner and a portal booking) can both read `SeatsAvailable = 1` and both insert BOOKED, and two waitlisted partner bookings can get the same `Position`; no unique constraint catches either (previous behaviour).
- **Migration impact:** overbooking is possible today under concurrency; a replacement must use a single locked/atomic path for all channels. Characterization requires a concurrent load test.
- **Repro:** **not run** — needs two concurrent booking-api requests and the Java services were not running in this session; the absence of the guarding constraints is shown by `12_missing_constraints.sql` above.

### Apply scripts drop the live database; the app login can bypass every proc

- **Where:** `db/apply.ps1:44`, `db/apply.sh:42` (`SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE`); `db/README.md:16-17` ("Safe to run as often as you like"); `scripts/run-legacy.ps1:8,247-250` (runs `apply.ps1` unless `-SkipDb`); `db/schema/000_create_database.sql:13-14,20-26`.
- **What happens:** every `run-legacy.ps1` start without `-SkipDb` kills open connections (running Java services and portal) and recreates the database from seed. The login is only created if missing, so a changed password is never reset, and the user/roles block runs only when the user does not exist. `ad_app` is a member of `db_datawriter` and has database-wide `EXECUTE`, so it can `UPDATE`/`DELETE` any table directly — including `AuditLog` — which is exactly how the Java services bypass the procs. `CHECK_POLICY = OFF`, default database `master`.
- **Migration impact:** least-privilege cannot be introduced without first moving the Java writers behind procs or an API; the audit log is not tamper-evident. The dev rebuild must never point at a shared environment (`AD_SQL_SERVER` override, `db/README.md:42`).
- **Repro:** `13_apply_permissions.sql` (read-only) after `db\apply.ps1`.
- **Observed:**

```text
RoleName
db_datareader
db_datawriter
permission_name|state_desc|class_desc
CONNECT|GRANT|DATABASE
EXECUTE|GRANT|DATABASE
name|is_policy_checked|is_expiration_checked|default_database_name
ad_app|0|0|master
CanUpdateExamSession|CanDeleteAuditLog|CanInsertBooking|CanAlterDb
1|1|1|0
name|compatibility_level|recovery_model_desc|collation_name
AssessmentDelivery|160|SIMPLE|SQL_Latin1_General_CP1_CI_AS
```

### Seed is deterministic but frozen in time, with two anchors and server-local timestamps

- **Where:** `db/README.md:48-49` ("now" = 2026-11-10); `db/seed/042_seed_sessions.sql:4,12-13,40-43` (`@AsOf = 20261111`); `db/seed/043_seed_bookings.sql:13-14` (`@Cutoff = 20261110 18:00`, `@Pending = 20261108`); `db/seed/044_seed_results.sql:12,59,64`; `db/seed/040_seed_reference.sql:6,17-37` (deletes and reseeds identities); `db/schema/010_tables.sql:128` (`LoggedAt DEFAULT GETDATE()`), `:127` (`Actor DEFAULT SUSER_SNAME()`).
- **What happens:** two full rebuilds produce identical ids and contents (fingerprints below match exactly), except the 12 seed `AuditLog` rows, which get the wall-clock apply time and the applying login (`sa`). Session statuses are fixed by `@AsOf = 2026-11-11`, one day after the README's "now"; sessions on 2026-11-11 are OPEN. Nothing ages the data: as the wall clock passes, OPEN sessions fall into the past and stay bookable (2 already at the time of this run). `StartsAt` is centre-local without an offset while all `GETDATE()` defaults are server-local; the VM's SQL clock read 16:57 while the session's local time was ~11:00, i.e. the server clock is UTC here, so `BookedAt`/`LastModified` from live writes and seeded values (local-looking, e.g. `20261110 18:00`) are on different time bases.
- **Migration impact:** tests that depend on "today" (reschedule targets use `DateTime.Today`, `AssessmentDelivery.Services/Booking/BookingService.cs:154-155`) behave differently depending on the run date; time zone handling must be designed explicitly (`datetimeoffset` or UTC + centre zone). Golden-master comparisons must exclude `AuditLog.LoggedAt`/`Actor` and any `GETDATE()` columns.
- **Repro:** `14_seed_fingerprint.sql` after two separate `db\apply.ps1` runs.
- **Observed (first rebuild shown; the second rebuild returned the same `T|N|Fp` table):**

```text
T|N|Fp
Candidate|1000|-1896646717
ExamSession|462|-1628692454
Booking|3587|-949379316
Waitlist|34|2127011
Result|1444|91819295
AuditLog|12|49344
AuditMin|AuditMax|Actors|AnActor
2026-11-11 16:54:23.433|2026-11-11 16:54:23.433|1|sa
Status|Sessions|FirstStart|LastStart
CANCELLED|18|2026-09-08 09:00:00.000|2027-02-22 09:00:00.000
CLOSED|8|2027-01-01 09:00:00.000|2027-02-04 09:00:00.000
DELIVERED|151|2026-09-01 09:00:00.000|2026-11-10 09:00:00.000
OPEN|285|2026-11-11 09:00:00.000|2027-03-31 09:00:00.000
OpenSessionsAlreadyInThePast_ByWallClock
2
```

## 5. Migration risks

| Risk | Impact | Likelihood | Evidence | Suggested mitigation / characterization test |
|---|---|---|---|---|
| `SeatsAvailable` copied as-is carries drift (partner double decrement, capacity-sync formula) | H | H | `db/triggers/032_trg_Booking_SeatSync.sql:18-24`, `services/booking-api/.../dao/SessionDao.java:83`, `services/capacity-sync/.../CapacitySyncService.java:59` | Recompute availability from bookings during migration; run `db/checks/seat-drift.sql` before/after cut-over; repros 02, 11 as regression tests |
| Undefined availability semantics (accommodation pool in or out of `SeatsAvailable`; ATTENDED counted or not) | H | H | `db/seed/049_seed_finalise.sql:16-23`, `db/triggers/033_trg_ExamSession_Update.sql:66-68`, `CapacitySyncService.java:27-34` | Business decision first; then a single shared availability function with table-driven tests over the seed |
| Capacity reduction demotes confirmed candidates without notice | H | M | `db/triggers/033_trg_ExamSession_Update.sql:24-71` | Keep repro 04 as a characterization test; decide explicitly whether the new system keeps, notifies or forbids it |
| Business rules split across SQL, C# and Java (retake wait, accommodation codes, duplicate check) | H | H | `db/procs/020_usp_BookSeat.sql:9,40-52`, `AssessmentDelivery.Services/Booking/BookingService.cs:106`, `services/booking-api/.../service/RetakeEligibilityService.java:60` | Consolidate into one booking service; port repro 07 cases into its test suite |
| Overbooking / duplicate waitlist positions under concurrency in the partner path | H | M | `services/booking-api/.../service/BookingService.java:110-120`, `BookingDao.java:84-85` | Concurrent load test against legacy to measure; atomic, locked booking path plus unique constraints in the target |
| Stricter target constraints reject legacy rows (negative seats, duplicate active bookings, invalid codes, inconsistent waitlist/result keys, bookings on cancelled sessions) | M | H | `db/schema/010_tables.sql:52-117` | Data-profiling queries derived from repro 12 run against a production copy before schema design is frozen |
| Error contract: all proc errors are 50000; codes only in state; system errors masked as user errors | M | H | `db/procs/020_usp_BookSeat.sql:5-8,85-87`, `AssessmentDelivery.Services/Common/SqlErrorHelper.cs:9,25` | Map state 1-6 explicitly; treat state 0 / non-proc errors as technical; add retry for deadlocks; repro 01 |
| Procs cannot be composed in caller transactions (rollback of outer transaction, Msg 266/3903) | M | M | `db/procs/021_usp_CancelBooking.sql:49`, `023_usp_RescheduleBooking.sql:35-44`, `BookingService.java:131,148` | Characterize error paths (repro 06b and 04 first-run); new service layer owns transactions |
| Triggers block `OUTPUT`-based ORMs and rewrite `LastModified` | M | H | `db/triggers/033_trg_ExamSession_Update.sql:16-22`, triggers `030`-`033` | Configure ORM without `OUTPUT` or retire triggers first; disable triggers during bulk data migration (as `049` does) and re-validate counters afterwards |
| Audit trail incomplete and attributed to shared login | M | H | `db/schema/010_tables.sql:127`, `db/procs/020_usp_BookSeat.sql:79-80`, `AssessmentDelivery.Services/Admin/AdminService.cs:47-59` | Do not use `AuditLog` as migration source of truth; define actor propagation and audited events up front; repro 09 |
| Over-privileged shared login (`db_datawriter` + database-wide `EXECUTE`) | M | H | `db/schema/000_create_database.sql:13-14,23-25` | Per-component logins; move direct writers behind procs/APIs before revoking `db_datawriter` |
| Time-zone ambiguity (`DATETIME` + `GETDATE()`, centre-local `StartsAt`, server clock UTC on this VM) | M | M | `db/schema/010_tables.sql:28,62,87,128`, `AssessmentDelivery.Services/Booking/BookingService.cs:154-155` | Inventory each column's time base; move to `datetimeoffset`/UTC; tests that run with different server time zones |
| `Result.Passed` recomputed instead of copied | M | L | `services/results-service/.../ResultIngestService.java:61-70`, `db/triggers/031_trg_Exam_Audit.sql:23-24` | Migrate stored verdicts verbatim; repro 10 |
| Dev apply script pointed at a shared environment drops data | H | L | `db/apply.ps1:44`, `db/apply.sh:42`, `db/README.md:16-17,42` | Guard rails (refuse non-localhost without an explicit flag) before reuse in CI/test environments |
| Seed frozen at 2026-11-11: date-relative tests start failing as time passes | L | H | `db/seed/042_seed_sessions.sql:13`, `db/seed/043_seed_bookings.sql:13-14` | Inject a clock in the new system; pin test clock to the seed anchor; repro 14 for determinism |
| Manual waitlist promotion lost or changed in migration | M | M | `db/procs/021_usp_CancelBooking.sql:2-4,36-41` | Confirm current admin procedure; repro 05 as characterization test |

## 6. Open questions

- Which `SeatsAvailable` definition is the business truth: the seed/check formula, the trigger's `Capacity - BOOKED`, or capacity-sync's "general pool only" (`db/checks/seat-drift.sql:18-26`, `db/triggers/033_trg_ExamSession_Update.sql:66-68`, `services/capacity-sync/.../CapacitySyncService.java:59`)?
- Is the partner double decrement known/compensated operationally (e.g. relying on the nightly capacity-sync), and how much drift exists in production today?
- Is the capacity-reduction demotion (`033:24-71`) relied upon by centre staff, and are demoted candidates told?
- Where are candidate and exam records created/edited in production? No application writer was found at legacy-baseline (`Candidate`/`Exam` I/U only in seed), yet both carry audit triggers (`030`, `031`).
- How are waitlisted candidates promoted today (`021:3-4` says "from the admin screen") — no promotion code path was found in `AdminService.cs`.
- What time zone does the production SQL Server run in, and is `StartsAt` always centre-local?
- Is the Azure SQL deployment mentioned in `db/schema/000_create_database.sql:5-7` still live, and does it use the same login/permission model?
- Are `SR`/`RB` the complete list of non-extra-time accommodation codes (`db/schema/010_tables.sql:84`), and should invalid stored codes be cleaned up?
- Do any reports or downstream systems read `AuditLog`, `LastModified` or booking ids (which change on every reschedule, `db/procs/023_usp_RescheduleBooking.sql:37-40`)?
