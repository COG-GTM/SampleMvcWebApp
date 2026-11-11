# As-is deep dive: runtime repros across components

Northwind Assessment Network, legacy estate at tag `legacy-baseline` (commit `e458238`, branch `estate/legacy-baseline`).

This slice ran the whole estate on a Windows VM and drove the same operations through every channel: portal, booking-api, results-service, capacity-sync, and direct `sqlcmd` against the stored procedures. Every hidden behaviour below was observed live. Each has a runnable PowerShell repro in [`docs/as-is/repros/runtime-repros/`](../repros/runtime-repros/), and the output pasted here is real output from 2026-11-11 (VM clock and SQL Server both UTC). File:line references are at `legacy-baseline`.

How to re-run: start the estate (`powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1`), then run any repro, for example `powershell -ExecutionPolicy Bypass -File docs\as-is\repros\runtime-repros\01-book-portal-vs-partner.ps1`. From Git Bash, append `< /dev/null` (otherwise go-sqlcmd waits on stdin). The scripts choose their own sessions and unbooked candidates (`NW00000901`-`NW00000999`), so they can be re-run against an already-used database. The exact ids in the output will differ between runs; the behaviour does not.

| Script | Covers |
|---|---|
| `_common.ps1` | helpers: `Q` (sqlcmd as sa), `Api` (HTTP without throwing), `PortalForm` (anti-forgery token + POST), `SeatState` |
| `01-book-portal-vs-partner.ps1` | same book/cancel through portal and booking-api: seats, drift, AuditLog |
| `02-partner-fill-and-race.ps1` | partner channel filling a session one at a time, then 8 concurrent partner bookings |
| `03-capacity-below-booked.ps1` | portal admin capacity cut below the booked count, then a raise |
| `04-capacity-sync.ps1` | `POST /sync/run` against the seat-drift formula |
| `05-retake-rule-divergence.ps1` | retake eligibility and booking: portal vs booking-api |
| `06-results-ingest.ps1` | REST and CSV score ingestion, portal results page, invalid input |
| `07-time-and-invalid-input.ps1` | sessions that have already started, error contracts for invalid input |
| `08-reschedule-cancel-accommodation-race.ps1` | reschedule per channel, cancel with a waitlist, default accommodation, concurrent `usp_BookSeat` |

## 1. Scope and architecture

### Components (as run)

| Component | Tech / version (observed) | Port | Started by | Talks to |
|---|---|---|---|---|
| Portal (`AssessmentDelivery.Web` + `.Services`, `.Business`, `.Data`) | ASP.NET MVC 5, .NET Framework 4.8, EF6, IIS Express | 8080 (listener shows as PID 4, http.sys; IIS Express pid 8624) | `scripts/run-legacy.ps1:382-406` -> `scripts/run-portal.ps1` | SQL Server via EF6 + `EXEC usp_*` as `ad_app` (`AssessmentDelivery.Web/Web.config:12`) |
| booking-api | Java 8 (Temurin `jdk-8.0.472.8-hotspot`), Spring Boot 1.5, JdbcTemplate | 8081 | `scripts/run-legacy.ps1:331-352` (`java -jar`, `:349`) | SQL Server as `ad_app` (`services/booking-api/src/main/resources/application.properties`) |
| results-service | Java 8, Spring Boot 1.5, JdbcTemplate | 8082 | same | SQL Server |
| capacity-sync | Java 8, Spring Boot 1.5, `@Scheduled` + REST | 8083 | same | SQL Server |
| Database `AssessmentDelivery` | SQL Server 2022 Express RTM 16.0.1000.6, default instance | 1433 | `scripts/run-legacy.ps1:131-166` installs if missing; `db/apply.ps1` builds schema + seed | n/a |

There is no service-to-service HTTP. Integration happens entirely through the shared database: the denormalised counter `ExamSession.SeatsAvailable` (`db/schema/010_tables.sql:59`), triggers (`db/triggers/030`-`033`), and stored procedures (`db/procs/020`-`023`).

### Request and data flow per channel

- **Portal booking**: `BookingController.Book` -> `BookingService.Book` (`AssessmentDelivery.Services/Booking/BookingService.cs:101` defaults accommodation, `:106-107` retake check in C#) -> `EXEC dbo.usp_BookSeat` (`:116`). The proc locks the session row (`db/procs/020_usp_BookSeat.sql:33-36`), inserts with `Channel='PORTAL'`, decrements `SeatsAvailable` itself (`:56-61`) and writes audit with the default actor (`:79`).
- **Partner booking**: `POST /api/v1/bookings` -> `BookingService.book` (`services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:57-126`). It reads `SeatsAvailable` without a lock (`dao/SessionDao.java:77`), inserts the `Booking` row directly (`dao/BookingDao.java:76`), decrements in Java (`SessionDao.java:82-83`), and `trg_Booking_SeatSync` decrements again for every non-portal `BOOKED` insert (`db/triggers/032_trg_Booking_SeatSync.sql:18-23`).
- **Cancel**: both channels call `usp_CancelBooking` (`db/procs/021_usp_CancelBooking.sql:37-41`). Portal cancels pass `@Actor='portal'` (`BookingService.cs:139`); booking-api passes `booking-api` (`BookingDao.java:91`).
- **Capacity**: portal admin `POST /Admin/Capacity/{id}` -> `usp_ChangeCapacity` (`AssessmentDelivery.Services/Admin/AdminService.cs:79-82`, `db/procs/022_usp_ChangeCapacity.sql:31-34`) -> `trg_ExamSession_Update` (`db/triggers/033_trg_ExamSession_Update.sql:16-67`).
- **Results**: `POST /api/v1/results` or `/upload` -> `ResultIngestService.ingest` (`services/results-service/src/main/java/com/northwind/ad/results/service/ResultIngestService.java:37-88`), which inserts `Result`, sets the booking to `ATTENDED` and audits. The portal `/Results` page reads `Result` through EF.
- **Capacity sync**: nightly at 02:00 server time (`services/capacity-sync/src/main/java/com/northwind/assessment/capacity/NightlySyncJob.java:13-14`) or `POST /sync/run`. It recomputes `SeatsAvailable` for OPEN sessions (`CapacitySyncService.java:33-34,59-67`).

### Configuration that couples the components

- Connection strings and credentials are in plain text: `AssessmentDelivery.Web/Web.config:12` (`ad_app`), and each `services/*/src/main/resources/application.properties` (`${AD_DB_PASSWORD:Northw1nd!Dev2011}`).
- The partner identity is configuration. `booking.channel=PARTNER` and `booking.audit-actor=booking-api` (booking-api `application.properties`) feed `Booking.Channel`, and `trg_Booking_SeatSync` keys its double decrement on that value (`032:23`).
- Portal: `authentication mode="None"` (`Web.config:30`), so there is no sign-in; `customErrors mode="RemoteOnly"` (`:33`); culture `en-GB` (`:34`).
- Time: `ExamSession.StartsAt` is center-local `DATETIME` with no offset (`db/schema/010_tables.sql:57`). `TestCenter.TimeZone` is an IANA id marked "display only" (`010_tables.sql:26`). Java uses the JVM default zone (`util/DateUtils.java:56`); the portal uses `DateTime.Today` (`AssessmentDelivery.Web/Controllers/EligibilityController.cs:28`).

### Measured startup (this VM, Windows Server 2022, first run from a clean box)

| Fact | Observed |
|---|---|
| `run-legacy.ps1` wall clock, first run | 4m 15.9s (`time` output), including installing sqlcmd, installing SQL Server 2022 Express, `db/apply.ps1` reseed, Maven build and portal build |
| Spring Boot start | booking-api 3.666 s, results-service 2.67 s, capacity-sync 2.745 s (`Started ... in` lines in `.run\*.log`) |
| Listeners | 1433 (sqlservr pid 4560), 8080 (PID 4 / http.sys, IIS Express pid 8624), 8081 (6564), 8082 (5836), 8083 (1860), from `netstat -ano` |
| First request | portal `/` 302 -> `/Admin/Centers` in 0.02 s; `/Booking` 200 in 0.44 s; `GET :8081/api/v1/sessions?from=2099-01-01` 200 in 0.65 s; `GET :8082/.../candidate/NW00000001` 200 in 0.59 s; `GET :8083/sync/last` 200 `{"status":"NEVER_RUN",...}` |
| `db/checks/row-counts.sql` after seed | TestCenter 21, Room 36, Exam 12, ExamSession 462, Candidate 1000, Booking 3587, Waitlist 34, Result 1444, AuditLog 12 |
| Session/booking status after seed | CANCELLED/CANCELLED 72; CLOSED/BOOKED 32; DELIVERED/ATTENDED 1444; DELIVERED/BOOKED 106; DELIVERED/CANCELLED 110; OPEN/BOOKED 1673; OPEN/CANCELLED 116; OPEN/WAITLISTED 34 |
| `db/checks/seat-drift.sql` after seed | 0 rows |

### Prerequisites and Windows setup friction (observed)

- **sqlcmd** was missing. `run-legacy.ps1` installed go-sqlcmd v1.6.0 through Chocolatey (`C:\Program Files\SqlCmd\sqlcmd.exe`). go-sqlcmd needs `-C` (trust server certificate). Launched from Git Bash it blocks on stdin, so redirect `< /dev/null`.
- **SQL Server 2022 Express** (~250 MB download) was installed unattended as the default instance with SQL auth on 1433. This needs an elevated shell.
- **Maven Central rate limiting**: before the build, a user `~/.m2/settings.xml` mirror (`mirrorOf=central` -> GCS Maven mirror) was added and the wrapper's Maven 3.8.8 was pre-seeded under `~/.m2/wrapper/dists/apache-maven-3.8.8/<hash>/`. With both in place, the build ran inside the 4m15s above.
- **JDK 8** was found at `C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot` (`run-legacy.ps1:263-266` requires `javac 1.8.*`).
- **IIS Express** port 8080 is owned by http.sys (PID 4), so identify the portal process by its `iisexpress.exe` command line.
- The portal has no authentication, so the scripts use only `GET` plus an anti-forgery-token `POST`.

## 2. Inventory

LOC counts files tracked by git with extensions `cs, cshtml, java, sql, ps1, sh, config, properties, yml`.

| Artifact | Path | Files / LOC | Purpose | Depends on |
|---|---|---|---|---|
| Portal web | `AssessmentDelivery.Web/` | 37 / 1575 | MVC controllers and views: Admin, Booking, Eligibility, Results | Services, Data, IIS Express, `Web.config` |
| Portal services | `AssessmentDelivery.Services/` | 22 / 744 | booking, admin, eligibility and results services; `SqlErrorHelper` | Data, Business, procs `usp_BookSeat/CancelBooking/RescheduleBooking/ChangeCapacity` |
| Portal rules | `AssessmentDelivery.Business/` | 11 / 208 | `RetakeRules`, `SeatCounter`, `AccommodationCodes` | none |
| Portal data | `AssessmentDelivery.Data/` | 15 / 419 | EF6 `AssessmentDeliveryDb` (initializer disabled), entities | SQL Server schema from `db/` |
| booking-api | `services/booking-api/src/main` | 24 / 1431 | partner REST: sessions, bookings, candidates/eligibility | `Booking`, `ExamSession`, `Waitlist`, `Result`, `AuditLog`, `usp_CancelBooking`, `trg_Booking_SeatSync` |
| results-service | `services/results-service/src/main` | 12 / 543 | REST and CSV score ingestion, results query | `Booking`, `Result`, `Exam`, `AuditLog` |
| capacity-sync | `services/capacity-sync/src/main` | 7 / 259 | nightly/manual `SeatsAvailable` recompute | `ExamSession`, `Booking`, `AuditLog` |
| Schema | `db/schema/` | 2 / 157 | tables, constraints | n/a |
| Procs | `db/procs/` | 4 / 252 | `usp_BookSeat`, `usp_CancelBooking`, `usp_ChangeCapacity`, `usp_RescheduleBooking` | schema |
| Triggers | `db/triggers/` | 4 / 179 | `trg_Candidate_Audit`, `trg_Exam_Audit`, `trg_Booking_SeatSync`, `trg_ExamSession_Update` | schema |
| Seed | `db/seed/` | 7 / 545 | reference data, candidates, sessions, bookings, results, history; `049` disables/enables `trg_ExamSession_Update` (`049_seed_finalise.sql:13,26`) | schema, triggers |
| Checks | `db/checks/` | 2 / 52 | `row-counts.sql`, `seat-drift.sql` | schema |
| Scripts | `scripts/` | 5 / 1155 | `run-legacy.ps1`, `run-portal.ps1`, `mixed-traffic.ps1`, others | Chocolatey, SQL Server Express media, JDK 8, Maven wrapper, MSBuild/IIS Express |
| Tests | `AssessmentDelivery.Tests/`, `services/*/src/test` | 7 test source files | unit tests | not run in this slice |
| Runtime repros (this slice) | `docs/as-is/repros/runtime-repros/` | 9 scripts | characterisation scripts behind section 4 | live estate |

## 3. Data ownership

These rows were observed at runtime through AuditLog, row state, and the code paths that produced them.

| DB table | Op | Component | File:line | Via |
|---|---|---|---|---|
| ExamSession | R | portal | `AssessmentDelivery.Services/Booking/BookingService.cs:38-46` | EF6 |
| ExamSession | U (SeatsAvailable -1) | portal | `db/procs/020_usp_BookSeat.sql:60-61` | proc |
| ExamSession | U (SeatsAvailable +1) | portal, booking-api | `db/procs/021_usp_CancelBooking.sql:37-38` | proc |
| ExamSession | U (Capacity, SeatsAvailable) | portal admin | `AssessmentDelivery.Services/Admin/AdminService.cs:79`, `db/procs/022_usp_ChangeCapacity.sql:31-34` | proc |
| ExamSession | U (SeatsAvailable recompute) | DB, on any Capacity change | `db/triggers/033_trg_ExamSession_Update.sql:66-67` | trigger |
| ExamSession | R, U (SeatsAvailable -1) | booking-api | `services/booking-api/.../dao/SessionDao.java:77,82-83` | JDBC |
| ExamSession | U (SeatsAvailable -n) | DB, on non-portal `BOOKED` insert | `db/triggers/032_trg_Booking_SeatSync.sql:18-23` | trigger |
| ExamSession | R, U (SeatsAvailable overwrite) | capacity-sync | `services/capacity-sync/.../CapacitySyncService.java:33-34,63-67` | JDBC |
| Booking | I (BOOKED/WAITLISTED, Channel=PORTAL) | portal | `020_usp_BookSeat.sql:56,68` | proc |
| Booking | I (Channel=PARTNER) | booking-api | `.../dao/BookingDao.java:76` | JDBC |
| Booking | U (CANCELLED) | portal, booking-api | `021_usp_CancelBooking.sql:32` | proc |
| Booking | U (BOOKED -> WAITLISTED) | DB, on capacity cut | `033_trg_ExamSession_Update.sql:35,48` | trigger |
| Booking | U (BOOKED -> ATTENDED) | results-service | `services/results-service/.../ResultIngestService.java:75-76` | JDBC |
| Booking | R | results-service | `ResultIngestService.java:41-44,91-96` | JDBC |
| Waitlist | I (MAX(Position)+1 under row lock) | portal | `020_usp_BookSeat.sql:72-73` | proc |
| Waitlist | I (MAX(Position)+1, no lock) | booking-api | `BookingDao.java:84-85` | JDBC |
| Waitlist | I | DB, on capacity cut | `033_trg_ExamSession_Update.sql:60-62` | trigger |
| Waitlist | D (only when the waitlisted booking itself is cancelled) | portal, booking-api | `021_usp_CancelBooking.sql:41` | proc |
| Result | I | results-service | `ResultIngestService.java:65-70` | JDBC |
| Result | R | portal (eligibility, results page), booking-api (retake) | `AssessmentDelivery.Services/Eligibility/EligibilityService.cs:32-35`, `services/booking-api/.../dao/ResultDao.java:24-29` | EF6 / JDBC |
| AuditLog | I `BOOK` (Actor = column default `SUSER_SNAME()`) | portal | `020_usp_BookSeat.sql:79`, `db/schema/010_tables.sql:127` | proc |
| AuditLog | I `CANCEL` | portal (`portal`), booking-api (`booking-api`), reschedule (default actor) | `021_usp_CancelBooking.sql:43`, `BookingService.cs:139`, `BookingDao.java:91`, `db/procs/023_usp_RescheduleBooking.sql:37` | proc |
| AuditLog | I `BOOK` | booking-api | `services/booking-api/.../service/BookingService.java:124`, `BookingDao.java:96` | JDBC |
| AuditLog | I `CAPACITY` (`portal-admin`) | portal admin | `022_usp_ChangeCapacity.sql:38`, `AdminService.cs:82` | proc |
| AuditLog | I `CAPACITY_SYNC` (EntityId 0) | capacity-sync | `CapacitySyncService.java:96` | JDBC |
| AuditLog | I `INGEST` | results-service | `ResultIngestService.java:77-79` | JDBC |
| AuditLog | I on Candidate/Exam change | DB | `db/triggers/030_trg_Candidate_Audit.sql:13`, `db/triggers/031_trg_Exam_Audit.sql` | trigger |

Writes that leave no AuditLog row (observed): BOOKED -> ATTENDED (results-service), BOOKED -> WAITLISTED demotions by `trg_ExamSession_Update`, per-session `SeatsAvailable` overwrites by capacity-sync (only one summary row, EntityId 0), and both seat-trigger decrements.

Paths abbreviated with `...`: `services/booking-api/src/main/java/com/northwind/assessment/booking/`, `services/results-service/src/main/java/com/northwind/ad/results/service/`, `services/capacity-sync/src/main/java/com/northwind/assessment/capacity/`.

## 4. Hidden behaviours

Each entry gives: file:line, what happens, migration impact, repro, and observed output.

### 4.1 A partner booking takes two seats off `SeatsAvailable`; a partner cancel gives back one

- **File:line**: `services/booking-api/.../service/BookingService.java:114-116` (insert, then `SessionDao.decrementSeatsAvailable`, `dao/SessionDao.java:82-83`). `db/triggers/032_trg_Booking_SeatSync.sql:18-23` also decrements for every `BOOKED` insert whose `Channel <> 'PORTAL'`. The cancel path adds back only 1 (`db/procs/021_usp_CancelBooking.sql:37-38`).
- **What happens**: every partner `BOOKED` row costs 2 on the counter. The loss is permanent after a cancel (drift grows by 1 per partner booking) until capacity-sync next overwrites the value (4.6).
- **Migration impact**: no single layer owns the counter. If either half moves (the trigger dropped, or the Java decrement removed), seat availability changes for one channel only. Any reconciliation has to treat `SeatsAvailable` as untrustworthy.
- **Repro**: `01-book-portal-vs-partner.ps1`
- **Observed**:
```
-- before
   Capacity=16 AccCap=1 SeatsAvailable=14 BOOKED=1 ATTENDED=0 WAITLISTED=0 Drift=1
-- portal book message
   Booking 3590 confirmed.
-- after portal book
   Capacity=16 AccCap=1 SeatsAvailable=13 BOOKED=2 ATTENDED=0 WAITLISTED=0 Drift=1
-- booking-api POST /api/v1/bookings
   HTTP 201 { "bookingId" : 3591, "status" : "BOOKED" }
-- after partner book
   Capacity=16 AccCap=1 SeatsAvailable=11 BOOKED=3 ATTENDED=0 WAITLISTED=0 Drift=2
-- after both cancels
   Capacity=16 AccCap=1 SeatsAvailable=13 BOOKED=1 ATTENDED=0 WAITLISTED=0 Drift=2
-- seat-drift for session (db/checks/seat-drift.sql formula)
   SessionId=462 SeatsAvailable=13 Expected=15
```
(The first run on the clean seed took the same session from Drift=0 to Drift=1. The run shown is the second.)

### 4.2 The partner channel waitlists candidates while seats are free, and `SeatsAvailable` goes negative

- **File:line**: `BookingService.java:110` decides BOOKED vs WAITLISTED from `SeatsAvailable` (`SessionDao.java:77`), which 4.1 has already double-decremented. `SessionDao.java:82-83` has no floor.
- **What happens**: a session with 5 free seats accepted only 3 partner bookings and waitlisted the next 3. The booking-api then reported `seatsAvailable: -1` next to `bookedSeats: 10` on capacity 12.
- **Migration impact**: partners see sessions as full early, and waitlists hold candidates who could have been seated. A target system that computes availability from bookings will "free" seats that partners were told did not exist.
- **Repro**: `02-partner-fill-and-race.ps1` (part a)
- **Observed**:
```
(a) session 444 starts with SeatsAvailable=5; booking 6 candidates one by one via booking-api
   NW00000906 -> HTTP 201 { "bookingId" : 3592, "status" : "BOOKED" }
   NW00000907 -> HTTP 201 { "bookingId" : 3593, "status" : "BOOKED" }
   NW00000908 -> HTTP 201 { "bookingId" : 3594, "status" : "BOOKED" }
   NW00000909 -> HTTP 201 { "bookingId" : 3595, "status" : "WAITLISTED" }
   NW00000910 -> HTTP 201 { "bookingId" : 3596, "status" : "WAITLISTED" }
   NW00000911 -> HTTP 201 { "bookingId" : 3597, "status" : "WAITLISTED" }
-- after sequential partner fill
   Capacity=12 AccCap=0 SeatsAvailable=-1 BOOKED=10 ATTENDED=0 WAITLISTED=3 Drift=3
-- booking-api GET session
   HTTP 200 { "sessionId" : 444, ... "capacity" : 12, "accommodationCapacity" : 0, "seatsAvailable" : -1, "status" : "OPEN", "bookedSeats" : 10 }
```

### 4.3 Concurrent partner bookings are not serialised: duplicate waitlist positions

- **File:line**: `BookingService.java:57-58` (`@Transactional`, default READ COMMITTED, no lock hint), `SessionDao.java:77` (plain `SELECT`), `dao/BookingDao.java:84-85` (`ISNULL(MAX(Position),0)+1` with no lock). The portal proc does take `UPDLOCK, HOLDLOCK` (`db/procs/020_usp_BookSeat.sql:33-36`).
- **What happens**: 8 simultaneous `POST /api/v1/bookings` against 5 free seats produced 4 BOOKED and 4 WAITLISTED. Three of the waitlist rows got `Position=1`. `SeatsAvailable` ended at -3 with 1 real seat free.
- **Migration impact**: waitlist order is not a reliable FIFO. Any "promote position 1" logic in a new system will hit ties. Concurrency semantics differ by channel.
- **Repro**: `02-partner-fill-and-race.ps1` (part b)
- **Observed**:
```
(b) session 431: 8 concurrent POST /api/v1/bookings
-- before
   Capacity=9 AccCap=0 SeatsAvailable=5 BOOKED=4 ATTENDED=0 WAITLISTED=0 Drift=0
   NW00000912 -> HTTP 201 { "bookingId" : 3598, "status" : "BOOKED" }
   NW00000913 -> HTTP 201 { "bookingId" : 3602, "status" : "WAITLISTED" }
   ... 3599, 3600, 3601 BOOKED; 3603, 3604, 3605 WAITLISTED
-- after concurrent partner bookings
   Capacity=9 AccCap=0 SeatsAvailable=-3 BOOKED=8 ATTENDED=0 WAITLISTED=4 Drift=4
-- Waitlist positions
   3602|1
   3603|1
   3604|1
   3605|2
```
For contrast, 6 concurrent `usp_BookSeat` calls (the portal path) for 2 free seats serialised correctly: 2 BOOKED, 4 WAITLISTED, positions 4-7, Drift=0 (`08-reschedule-cancel-accommodation-race.ps1` part d, output in 4.5).

### 4.4 Cutting capacity below the booked count silently demotes the newest bookings to WAITLISTED, and raising it again does not restore them

- **File:line**: `db/procs/022_usp_ChangeCapacity.sql:29` validates only `NewCapacity >= AccommodationCapacity`. `:31-34` applies the delta with a floor of 0. Then `db/triggers/033_trg_ExamSession_Update.sql:35,48` demotes the latest `BOOKED` rows (`ORDER BY BookedAt DESC, BookingId DESC`), `:60-62` appends them to `Waitlist`, and `:66-67` recomputes `SeatsAvailable`. The only audit row written is `CAPACITY` (`022:38`).
- **What happens**: the portal said "Capacity updated". Two confirmed candidates were moved to the waitlist with no audit row on their bookings and no message. Raising capacity by 3 freed 3 seats, but the demoted candidates stayed waitlisted.
- **Migration impact**: candidates lose confirmed seats as a side effect of an admin screen. A target system must decide whether to reject such cuts, notify, or replicate this. The demotion is invisible in AuditLog, so history cannot be reconstructed from audit alone.
- **Repro**: `03-capacity-below-booked.ps1`
- **Observed**:
```
   portal book NW00000920 -> Booking 3606 confirmed.
   portal book NW00000921 -> Booking 3607 confirmed.
-- session 452 before capacity change
   Capacity=13 AccCap=0 SeatsAvailable=6 BOOKED=7 ATTENDED=0 WAITLISTED=0 Drift=0
POST /Admin/Capacity/452 newCapacity=5 (BOOKED=7, AccommodationCapacity=0)
-- portal message
   Capacity updated
-- after
   Capacity=5 AccCap=0 SeatsAvailable=0 BOOKED=5 ATTENDED=0 WAITLISTED=2 Drift=0
-- bookings changed by the trigger (BookingId|Status|BookedAt|Waitlist.Position)
   3606|WAITLISTED|2026-11-11 16:57:38|1
   3607|WAITLISTED|2026-11-11 16:57:38|2
-- new AuditLog rows
   CAPACITY|ExamSession|452|portal-admin|13 -> 5
Raise capacity back to 8
-- after raise (no waitlist promotion?)
   Capacity=8 AccCap=0 SeatsAvailable=3 BOOKED=5 ATTENDED=0 WAITLISTED=2 Drift=0
```

### 4.5 Freed seats never promote the waitlist, so new bookings jump the queue

- **File:line**: `db/procs/021_usp_CancelBooking.sql:37-41` increments `SeatsAvailable` and deletes only the cancelled booking's own waitlist row. No proc, trigger or service promotes from `Waitlist`. `020_usp_BookSeat.sql:56-61` gives any free seat to the caller.
- **What happens**: cancelling a BOOKED seat on a session with 3 waitlisted candidates left the waitlist untouched. On another session, after capacity-sync freed 2 seats (4.6) while 3 candidates were waitlisted, 6 new concurrent portal-path bookings took both seats ahead of them.
- **Migration impact**: "waitlist" is a parking state with no runtime semantics. Introducing auto-promotion in a target system changes outcomes for existing waitlisted candidates, so it needs a business decision and a data fix-up plan.
- **Repro**: `08-reschedule-cancel-accommodation-race.ps1` (parts b and d)
- **Observed**:
```
(b) session 153 has a waitlist; portal-cancel BOOKED booking 1581
-- before
   Capacity=22 AccCap=2 SeatsAvailable=0 BOOKED=22 ATTENDED=0 WAITLISTED=3 Drift=0
-- portal cancel
   Booking 1581 cancelled.
-- after
   Capacity=22 AccCap=2 SeatsAvailable=1 BOOKED=21 ATTENDED=0 WAITLISTED=3 Drift=0
-- waitlist after
   1603|1
   1604|2
   1605|3
(d) session 444, 6 parallel sqlcmd EXEC usp_BookSeat
-- before
   Capacity=12 AccCap=0 SeatsAvailable=2 BOOKED=10 ATTENDED=0 WAITLISTED=3 Drift=0
   candidateId=929 booking=3622 BOOKED
   candidateId=930 booking=3623 BOOKED
   candidateId=931 booking=3624 WAITLISTED   (932-934 also WAITLISTED)
-- after
   Capacity=12 AccCap=0 SeatsAvailable=0 BOOKED=12 ATTENDED=0 WAITLISTED=7 Drift=0
-- waitlist positions
   3595|1  3596|2  3597|3  3624|4  3625|5  3626|6  3627|7
```

### 4.6 capacity-sync uses a different seat formula from `db/checks/seat-drift.sql`, so it fixes partner drift and creates drift on every accommodation session

- **File:line**: `services/capacity-sync/.../CapacitySyncService.java:33-34` counts only non-ET (`ET25`/`ET50` excluded) BOOKED+ATTENDED, and only on `OPEN` sessions. `:59-61` sets `expected = Capacity - AccommodationCapacity - standardBooked`, floored at 0. `db/checks/seat-drift.sql` expects `Capacity - BOOKED - ATTENDED`. `:96` writes one summary audit row with `EntityId 0`, and `:100-101` swallows audit failures with a warning.
- **What happens**: before the sync, 3 sessions drifted (all from 4.1-4.3). One `POST /sync/run` updated 114 of 285 OPEN sessions. Afterwards 112 sessions drift by the check's formula, all of them OPEN with `AccommodationCapacity > 0`. Session 462 went from Drift=2 to Drift=1 rather than 0. A second run updated 0 sessions, so the sync considers its own result stable.
- **Migration impact**: there are two "correct" definitions of `SeatsAvailable` in the estate. Which one a reconciliation report or a migrated counter uses changes availability on more than a third of open sessions. The nightly 02:00 run (`NightlySyncJob.java:13-14`, server time) silently rewrites values that the portal and booking-api decide on.
- **Repro**: `04-capacity-sync.ps1` (run after 01-03 and 05)
- **Observed**:
```
-- sessions drifting by seat-drift.sql formula (before sync)
   3
-- session 462 before
   Capacity=16 AccCap=1 SeatsAvailable=13 BOOKED=1 ATTENDED=0 WAITLISTED=0 Drift=2
-- session 444 before
   Capacity=12 AccCap=0 SeatsAvailable=-1 BOOKED=10 ATTENDED=0 WAITLISTED=3 Drift=3
-- POST /sync/run (922 ms)
   HTTP 200 { "status" : "COMPLETED", "trigger" : "MANUAL", ... "sessionsChecked" : 285, "sessionsUpdated" : 114, "message" : null }
-- session 462 after
   Capacity=16 AccCap=1 SeatsAvailable=14 BOOKED=1 ATTENDED=0 WAITLISTED=0 Drift=1
-- session 444 after
   Capacity=12 AccCap=0 SeatsAvailable=2 BOOKED=10 ATTENDED=0 WAITLISTED=3 Drift=0
-- sessions drifting by seat-drift.sql formula (after sync)
   112
-- drift after sync by AccommodationCapacity>0 (count)
   AccCap>0 OPEN 112
-- latest CAPACITY_SYNC audit row
   CAPACITY_SYNC|ExamSession|0|capacity-sync|trigger=MANUAL status=COMPLETED checked=285 updated=114
-- second POST /sync/run immediately after
   HTTP 200 { ... "sessionsChecked" : 285, "sessionsUpdated" : 0, ... }
```

### 4.7 The retake wait is 30 calendar days in the portal and 30 working days in booking-api

- **File:line**: `AssessmentDelivery.Business/Rules/RetakeRules.cs:24-27` (`lastAttempt.AddDays(retakeWaitDays)`), versus `services/booking-api/.../service/RetakeEligibilityService.java` (`DateUtils.addWorkingDays`, `util/DateUtils.java:67-74`, which skips Saturday and Sunday). Both read `Exam.RetakeWaitDays` (`db/schema/010_tables.sql:47`). The proc does not check retakes at all (`AssessmentDelivery.Services/Booking/BookingService.cs:106-107`).
- **What happens**: for a fail on Sat 2026-11-07, the portal allows a retake from 07 Dec 2026 and booking-api from 2026-12-18. The portal accepted a booking on a 2026-12-07 session that booking-api refused.
- **Migration impact**: the business rule is ambiguous, and the two channels give different answers for 11 days. A unified rule will change eligibility for one channel's users.
- **Repro**: `05-retake-rule-divergence.ps1`
- **Observed**:
```
candidate NW00000113, exam HPI-ACC1, RetakeWaitDays=30, last failed attempt 2026-11-07 Sat
date 2026-12-07 (Mon)
   booking-api: HTTP 200 { "eligible" : false, "reason" : "RETAKE_WAIT_NOT_ELAPSED", "earliestRetakeDate" : "2026-12-18" }
   portal     : Eligible=YES; Reason=Retake wait period has elapsed; Earliest retake date=07 Dec 2026
date 2026-12-14 (Mon)
   booking-api: HTTP 200 { "eligible" : false, "reason" : "RETAKE_WAIT_NOT_ELAPSED", "earliestRetakeDate" : "2026-12-18" }
   portal     : Eligible=YES; Reason=Retake wait period has elapsed; Earliest retake date=07 Dec 2026
date 2026-12-18 (Fri)
   booking-api: HTTP 200 { "eligible" : true, "reason" : "RETAKE_ALLOWED", "earliestRetakeDate" : "2026-12-18" }
session 206 starts 2026-12-07 09:00
-- booking-api POST
   HTTP 409 { "error" : "NOT_ELIGIBLE", "message" : "Candidate NW00000113 is not eligible to book HPI-ACC1 (RETAKE_WAIT_NOT_ELAPSED), earliest retake date 2026-12-18" }
-- portal book
   Booking 3609 confirmed.
```

### 4.8 results-service accepts a score for a session that has not happened yet, which locks the booking and blocks the candidate

- **File:line**: `services/results-service/.../ResultIngestService.java:50` checks only that the booking is `BOOKED`/`ATTENDED`. There is no check on session status or `StartsAt`. `:61-62` computes `Passed` from the current `Exam.PassingScore`, `:75-76` sets `ATTENDED`, and `ResultsController.java:40-41` defaults `scoredAt` to now. `021_usp_CancelBooking.sql:30` then refuses to cancel the `ATTENDED` booking.
- **What happens**: a booking on a 2027-03-31 session was scored 999 on 2026-11-11. It became `ATTENDED` and cannot be cancelled. The portal shows a PASS dated 31 Mar 2027, and booking-api now reports `ALREADY_PASSED`. `SeatsAvailable` did not change (ATTENDED still holds the seat).
- **Migration impact**: results are trusted without temporal validation, so historical data may contain results that predate their session. A target system that validates this will reject replayed legacy data.
- **Repro**: `06-results-ingest.ps1`
- **Observed**:
```
future session 450 (2027-03-31 09:00 OPEN, BCB-PM100), candidate NW00000922
-- POST /api/v1/results score=999 for a session that has not started (no scoredAt)
   HTTP 201 { "resultId" : 1445, "bookingId" : 3610, "score" : 999, "passed" : true }
-- booking row now
   3610|ATTENDED|PARTNER
-- Result row (Score|Passed|ScoredAt|SourceFile|ReceivedAt)
   999|1|2026-11-11 16:59:03.437|NULL|2026-11-11 16:59:03
-- portal /Results row
   BCB-PM100 Project Management Practitioner 31 Mar 2027 Lakeshore Assessment Centre 999 700 PASS 11/11/2026
-- portal cancel of the ATTENDED booking
   Booking 3610 cannot be cancelled.
-- booking-api eligibility for the same exam
   HTTP 200 { "eligible" : false, "reason" : "ALREADY_PASSED", "earliestRetakeDate" : null }
```

### 4.9 The score CSV import is strict per row and partially commits

- **File:line**: `services/results-service/.../CsvRowParser.java:26-29` (`line.split(",")`, with no quote handling) and `:33` (`NW\d{8}`, case-sensitive). `ScoreFileImporter.java:52-57` processes each row independently, and a second score for the same booking is rejected per row (`ResultIngestService.java:54-58`).
- **What happens**: of 5 data rows, 1 was imported and 4 were reported as errors in a 200 response. Rows were rejected for a quoted candidate number, a lower-case candidate number, no active booking, and a duplicate result. A UTF-8 BOM and a blank line were tolerated.
- **Migration impact**: partners rely on a 200-with-errors contract and on exact formatting. A replacement importer has to keep partial commits and the same error lines, or partners must be migrated.
- **Repro**: `06-results-ingest.ps1` (CSV part)
- **Observed**:
```
CSV sent:
   candidateNumber,sessionId,score,scoredAt
   NW00000923,450,640,2026-11-11T08:00:00
   "NW00000924",450,700,2026-11-11
   nw00000924,450,500,2026-11-11
   NW00000925,450,500,2026-11-11 09:00:00

   NW00000923,450,650,2026-11-11
-- POST /api/v1/results/upload
   { "fileName" : "rr06-scores.csv", "rowsRead" : 5, "imported" : 1, "skipped" : 4, "errors" : [ { "line" : 3, "message" : "bad candidateNumber '\"NW00000924\"'" }, { "line" : 4, "message" : "bad candidateNumber 'nw00000924'" }, { "line" : 5, "message" : "no active booking for NW00000925 on session 450" }, { "line" : 7, "message" : "A result already exists for booking 3611" } ] }
```

### 4.10 Sessions that have already started stay OPEN and can still be booked through both channels

- **File:line**: nothing moves a session out of `OPEN` at runtime. `NightlySyncJob.java:13` refers to "the portal's 01:00 session close-out", but `git grep` finds no such job. `020_usp_BookSeat.sql:38` checks only `Status`. Portal search applies no `StartsAt >= now` filter (`AssessmentDelivery.Services/Booking/BookingService.cs:38,43`), and booking-api's book path has none either. `StartsAt` is a center-local `DATETIME` with no zone (`db/schema/010_tables.sql:57`), and the server clock is UTC.
- **What happens**: at 16:59 UTC, a Toronto session that started at 13:30 local was still OPEN. booking-api and the portal both booked it, and both listed it for today.
- **Migration impact**: close-out depends on an external or manual process that is not in the repo. Without a zone, "has it started" cannot be computed correctly anyway (13:30 Toronto is 18:30 UTC). Any target time logic needs a zone mapping from `TestCenter.TimeZone`.
- **Repro**: `07-time-and-invalid-input.ps1`
- **Observed**:
```
-- SQL Server GETDATE() / SYSDATETIMEOFFSET()
   2026-11-11 16:59:06 | 2026-11-11 16:59:06.9108309 +00:00
-- OPEN sessions with StartsAt < GETDATE()
   152 2026-11-11 13:30 TOR01 America/Toronto seats=14
   151 2026-11-11 09:00 PHX01 America/Phoenix seats=0
-- booking-api book past session 152
   HTTP 201 { "bookingId" : 3613, "status" : "BOOKED" }
-- portal book past session 152
   Booking 3614 confirmed.
-- booking-api sessions?from=2026-11-11&to=2026-11-11 contains past session 152?
   True
-- portal /Booking search for today lists Book link for 152?
   True
```

### 4.11 A blank accommodation code means "candidate default" in the portal and "none" in booking-api

- **File:line**: `AssessmentDelivery.Services/Booking/BookingService.cs:101` falls back to `Candidate.DefaultAccommodationCode`. `services/booking-api/.../service/BookingService.java:62-64` uses only the request value.
- **What happens**: a candidate with default ET50 booked through the portal got `ET50`, consuming accommodation capacity. A candidate with default ET25 booked through booking-api got `NULL`, a standard seat with no extra time.
- **Migration impact**: candidates silently lose their entitlement on the partner channel. Accommodation counts per session differ by channel mix.
- **Repro**: `08-reschedule-cancel-accommodation-race.ps1` (part c)
- **Observed**:
```
(c) session 442; NW00000903 default ET50 via portal, NW00000963 default ET25 via booking-api, both with blank accommodation
-- portal
   Booking 3620 confirmed.
-- booking-api
   HTTP 201 { "bookingId" : 3621, "status" : "BOOKED" }
-- rows
   NW00000903 PORTAL acc=ET50
   NW00000963 PARTNER acc=NULL
```

### 4.12 Reschedule differs by channel: the API converts portal bookings to PARTNER (and double-decrements), and the proc allows a no-op reschedule to the same session

- **File:line**: booking-api `BookingService.java:148-176` cancels (`:169`, reason `Rescheduled`) and then calls the partner `book()`. The new row takes the configured channel and repeats the 4.1 double decrement. The API also blocks `SAME_SESSION`. `db/procs/023_usp_RescheduleBooking.sql:29-41` checks only that the exam matches, so the same session is allowed and the inner cancel writes audit with the default actor (`:37`). The portal filters same-session targets in the UI only (`BookingService.cs:154-156`).
- **What happens**: two portal bookings were moved 451 -> 411, one through the portal and one through the API. The portal move stayed `PORTAL` with no drift. The API move became `PARTNER` and left Drift=1 on 411. Calling the proc for 411 -> 411 cancelled 3617 and created 3619 on the same session.
- **Migration impact**: `Booking.Channel` does not record the channel the candidate originally booked through. Booking ids change on every reschedule, which breaks external references.
- **Repro**: `08-reschedule-cancel-accommodation-race.ps1` (part a)
- **Observed**:
```
-- portal reschedule 3615
   Booking moved. New booking 3617 (BOOKED).
-- booking-api reschedule 3616
   HTTP 200 { "bookingId" : 3618, "status" : "BOOKED" }
-- y=411 after
   Capacity=11 AccCap=0 SeatsAvailable=3 BOOKED=7 ATTENDED=0 WAITLISTED=0 Drift=1
-- new bookings on y
   3617 BOOKED channel=PORTAL acc=NULL
   3618 BOOKED channel=PARTNER acc=NULL
-- booking-api reschedule 3618 to its own session
   HTTP 409 { "error" : "SAME_SESSION", "message" : "Booking 3618 is already on session 411" }
-- usp_RescheduleBooking 3617 to its own session 411 (what the portal proc allows)
   NewBookingId=3619 Status=BOOKED
```

### 4.13 AuditLog `Actor` depends on the DB login and the call path, not on the user or channel

- **File:line**: `db/schema/010_tables.sql:127` (`DEFAULT (SUSER_SNAME())`). `020_usp_BookSeat.sql:79` passes no actor. `BookingService.cs:139` hardcodes `portal` for cancel, `AdminService.cs:82` uses `portal-admin`, booking-api uses its config value `booking-api` (`BookingService.java:124`), and `023:37` inherits the login. The detail format also differs (partner rows include `channel=` and `ref=`).
- **What happens**: a portal booking is audited as `ad_app`, a portal cancel as `portal`, a reschedule's internal cancel as `ad_app` (portal) or `sa` (when an operator runs it), and partner rows as `booking-api`.
- **Migration impact**: AuditLog cannot answer "which channel or user did this" consistently. Audit-based reporting or compliance evidence needs per-path mapping rules.
- **Repro**: `01-book-portal-vs-partner.ps1`, `08-reschedule-cancel-accommodation-race.ps1`
- **Observed**:
```
   BOOK|Booking|3590|ad_app|session=462 status=BOOKED
   BOOK|Booking|3591|booking-api|session=462 status=BOOKED channel=PARTNER ref=RR01-462
   CANCEL|Booking|3590|portal|rr01 portal cancel
   CANCEL|Booking|3591|booking-api|rr01
   CANCEL|Booking|3615|ad_app|Rescheduled
   CANCEL|Booking|3616|booking-api|Rescheduled
   CANCEL|Booking|3617|sa|Rescheduled
   BOOK|Booking|3619|sa|session=411 status=BOOKED
```

### 4.14 Error contracts differ per component, and some invalid input is silently accepted

- **File:line**: booking-api returns `{error,message}` from `web/ApiExceptionHandler.java:22-44`, but `:29-30` passes raw Java/Jackson exception text to the client. results-service uses an unordered `HashMap` for the same shape (`RestErrorHandler.java:41-46`). capacity-sync has no handler, so Spring's default body leaks `exception`. Portal invalid dates bind to `null` and fall back to today (`EligibilityController.cs:28`). The portal shows only `msg 50000 / severity 16` texts and drops the proc's error state (`AssessmentDelivery.Services/Common/SqlErrorHelper.cs:25`).
- **Migration impact**: clients may parse error text. A target API needs a deliberate, versioned error model and a compatibility plan.
- **Repro**: `07-time-and-invalid-input.ps1`
- **Observed** (excerpt):
```
   GET sessions/abc -> HTTP 400 { "error" : "BAD_REQUEST", "message" : "Failed to convert value of type 'java.lang.String' to required type 'int'; nested exception is java.lang.NumberFormatException: For input string: \"abc\"" }
   GET capacity-sync /sync/run (GET) -> HTTP 405 { "timestamp" : "2026-11-11T16:59:08.539+0000", "status" : 405, "error" : "Method Not Allowed", "exception" : "org.springframework.web.HttpRequestMethodNotSupportedException", ... }
   POST bookings sessionId:'x' -> { "error" : "BAD_REQUEST", "message" : "JSON parse error: Unexpected character ('s' (code 115)) ... com.fasterxml.jackson.core.JsonParseException ...
   GET http://localhost:8080/Eligibility?candidateNumber=NW00000113&examCode=HPI-ACC1&date=notadate -> HTTP 200 (no message)
   GET http://localhost:8080/Booking/Book?sessionId=999999 -> HTTP 404
   GET http://localhost:8080/Booking/MyBookings?candidateNumber=XYZ -> HTTP 200 Candidate number XYZ was not found.
```
Validation that behaves consistently: booking-api rejects unknown accommodation codes (`HTTP 400 Unknown accommodationCode XX`) and non-ISO dates (`Invalid date '11/11/2026', expected yyyy-MM-dd`). results-service rejects scores outside 0-1000, duplicates, and cancelled bookings (output in `06-results-ingest.ps1`).

## 5. Migration risks

| # | Risk | Impact | Likelihood | Evidence | Mitigation / characterisation test |
|---|---|---|---|---|---|
| 1 | `SeatsAvailable` has multiple writers with inconsistent arithmetic (proc, Java, trigger, sync) | H | H | `032_trg_Booking_SeatSync.sql:18-23`, `BookingService.java:114-116`, `CapacitySyncService.java:59-61`, `022:31-34` | derive availability from bookings in the target; keep 01/02/04 as golden tests; reconcile before cutover |
| 2 | Two seat formulas (`seat-drift.sql` vs capacity-sync) disagree on 112 sessions after one sync | H | H | `CapacitySyncService.java:33-34,59`, `db/checks/seat-drift.sql` | business sign-off on one formula; snapshot both before migration |
| 3 | Partner bookings race (no lock, duplicate waitlist positions) | H | M | `SessionDao.java:77`, `BookingDao.java:84-85` | concurrency test (02b) against the target; dedupe positions in migrated data |
| 4 | Capacity cuts silently demote confirmed bookings with no audit | H | M | `033_trg_ExamSession_Update.sql:35-67`, `022:29` | decide policy; test 03; extract demotion history from Waitlist.AddedAt |
| 5 | No waitlist promotion; new bookings jump the queue | M | H | `021_usp_CancelBooking.sql:37-41` | decide promotion semantics; test 08b/08d |
| 6 | Retake rule differs (calendar vs working days) | H | H | `RetakeRules.cs:24-27`, `DateUtils.java:67-74` | owner decision; parameterised test over dates (05) |
| 7 | Accommodation default only on portal | M | H | `BookingService.cs:101`, `BookingService.java:62-64` | audit partner bookings of candidates with ET defaults; test 08c |
| 8 | Results accepted for future sessions; lock booking and eligibility | M | M | `ResultIngestService.java:50,75-76` | find `Result.ScoredAt < Session.StartsAt` in prod data; test 06 |
| 9 | No session close-out; zone-less local times on a UTC server | H | H | `NightlySyncJob.java:13`, `010_tables.sql:26,57`, `020:38` | locate the external close-out; map `TestCenter.TimeZone`; test 07 |
| 10 | `Booking.Channel` and AuditLog actor are not reliable provenance | M | H | `BookingService.java:148-176`, `010_tables.sql:127`, `023:37` | derive provenance from ExternalRef + audit rules; test 01/08a |
| 11 | Error payloads leak internals and differ per service | L | H | `ApiExceptionHandler.java:29-30`, `RestErrorHandler.java:41-46` | contract tests on error shapes (07) before replacing APIs |
| 12 | Windows-only bring-up (SQL Express install, IIS Express, Chocolatey, Maven 429) | M | M | `scripts/run-legacy.ps1:131-166,263-266,382-406` | containerise DB + Java; pin Maven mirror; document elevated prerequisites |

## 6. Open questions

1. Which `SeatsAvailable` definition is authoritative: `Capacity - BOOKED - ATTENDED` (`db/checks/seat-drift.sql`) or `Capacity - AccommodationCapacity - standard bookings` (capacity-sync)?
2. Does a session close-out process exist outside the repo (the "01:00 close-out" in `NightlySyncJob.java:13`)? Who moves sessions to `CLOSED`/`DELIVERED` in production?
3. Is the retake wait meant to be calendar days or working days? Do partners depend on the API's later date?
4. Is the double decrement on partner bookings known and compensated for by the nightly sync on purpose? Have partners been told about early "full" sessions?
5. Should capacity cuts be allowed below the booked count, and should affected candidates be notified?
6. Is anything (an operator or a report) expected to promote waitlisted candidates?
7. Should results be rejected for sessions that have not started, or for a booking not `BOOKED` at session time?
8. Not verified here: the unit test suites (`AssessmentDelivery.Tests`, `services/*/src/test`) were not run, and the IIS Express portal was exercised over HTTP, not through a browser.
