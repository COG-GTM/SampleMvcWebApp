# As-is architecture (legacy estate)

Scope: tag `legacy-baseline` (`e458238`). Derived from the code and from running the estate on
Linux (SQL Server 2022 container + the three Java services via `scripts/run-legacy.sh`). The
portal's behaviour comes from its C# source; its database effects were reproduced by calling the
same stored procedures with the same arguments. Companion documents:
[data-ownership.md](data-ownership.md), [hidden-behavior.md](hidden-behavior.md),
[migration-risks.md](migration-risks.md).

## 1. Components

```mermaid
flowchart LR
  subgraph Users
    CAND[Candidate / kiosk<br/>no sign-in, candidate number]
    ADMIN[Centre admin]
    PARTNER[Partner booking systems]
    DELIV[Delivery system<br/>score files]
    OPS[Ops / cron]
  end

  subgraph Portal[".NET 4.8 portal :8080 (IIS / IIS Express, Windows only)"]
    WEB[AssessmentDelivery.Web<br/>MVC 5 controllers:<br/>Booking, Admin, Eligibility, Results]
    SVC[AssessmentDelivery.Services<br/>BookingService, AdminService,<br/>EligibilityService, ResultsService]
    BIZ[AssessmentDelivery.Business<br/>RetakeRules, SeatCounter,<br/>AccommodationCodes]
    DATA[AssessmentDelivery.Data<br/>EF6 AssessmentDeliveryDb]
    WEB --> SVC --> BIZ
    SVC --> DATA
  end

  subgraph Java["Java 8 / Spring Boot 1.5 services"]
    BAPI[booking-api :8081<br/>partner bookings, sessions,<br/>eligibility]
    RES[results-service :8082<br/>result ingest, CSV upload]
    SYNC[capacity-sync :8083<br/>nightly 02:00 + POST /sync/run]
  end

  subgraph DB["SQL Server: AssessmentDelivery (shared, login ad_app)"]
    TBL[(9 tables<br/>TestCenter Room Exam ExamSession<br/>Candidate Booking Waitlist Result AuditLog)]
    PROCS[[usp_BookSeat<br/>usp_CancelBooking<br/>usp_ChangeCapacity<br/>usp_RescheduleBooking]]
    TRG{{trg_Booking_SeatSync<br/>trg_ExamSession_Update<br/>trg_Candidate_Audit<br/>trg_Exam_Audit}}
    PROCS --> TBL
    TBL -. fires .-> TRG
    TRG --> TBL
  end

  CAND --> WEB
  ADMIN --> WEB
  PARTNER -->|REST JSON| BAPI
  DELIV -->|REST / multipart CSV| RES
  OPS -->|cron / manual| SYNC

  DATA -->|EF reads, TestCenter update| TBL
  DATA -->|EXEC procs| PROCS
  BAPI -->|JdbcTemplate: direct INSERT/UPDATE| TBL
  BAPI -->|EXEC usp_CancelBooking| PROCS
  RES -->|direct INSERT Result, UPDATE Booking| TBL
  SYNC -->|direct UPDATE ExamSession.SeatsAvailable| TBL
```

| Component | Tech | Talks to DB via | Key source |
|---|---|---|---|
| Portal | .NET Framework 4.8, ASP.NET MVC 5, EF6, Autofac | EF6 for reads and `TestCenter` edits; `ExecuteSqlCommand` for all booking / capacity writes (procs) | `AssessmentDelivery.Web/Controllers/*.cs`, `AssessmentDelivery.Services/**`, `AssessmentDelivery.Business/Rules/*.cs` |
| booking-api | Java 8, Spring Boot 1.5, JdbcTemplate | inline SQL; **only** `usp_CancelBooking` is used; booking/reschedule bypass `usp_BookSeat` | `services/booking-api/src/main/java/com/northwind/assessment/booking/**` |
| results-service | Java 8, Spring Boot 1.5, JdbcTemplate | inline SQL | `services/results-service/src/main/java/com/northwind/ad/results/**` |
| capacity-sync | Java 8, Spring Boot 1.5, `@Scheduled(cron = "0 0 2 * * *")` | inline SQL | `services/capacity-sync/src/main/java/com/northwind/assessment/capacity/**` |
| Database | SQL Server 2016+ / Azure SQL | 4 procs, 4 triggers, 9 tables, `db/checks/*.sql` diagnostics | `db/schema`, `db/procs`, `db/triggers`, `db/seed`, `db/checks` |

### Endpoints

| Stack | Route | Effect on DB |
|---|---|---|
| Portal | `GET /Booking` | EF: OPEN sessions, `StartsAt >= today` |
| Portal | `POST /Booking/Book` | retake check (C#) -> `EXEC usp_BookSeat` |
| Portal | `POST /Booking/Cancel/{id}` | `EXEC usp_CancelBooking` |
| Portal | `POST /Booking/Reschedule/{id}` | retake check (C#) -> `EXEC usp_RescheduleBooking` |
| Portal | `GET /Eligibility?candidateNumber=&examCode=&date=` | EF: `Result` rows -> `RetakeRules` (calendar days) |
| Portal | `GET /Results?candidateNumber=` | EF read |
| Portal | `GET/POST /Admin/EditCenter` | EF `SaveChanges` on `TestCenter` (no proc, no audit) |
| Portal | `GET/POST /Admin/Sessions/Capacity/{id}` | `EXEC usp_ChangeCapacity` (-> `trg_ExamSession_Update`) |
| booking-api | `GET /api/v1/sessions?examCode=&centerCode=&from=&to=` | read, all statuses, TOP 500 |
| booking-api | `GET /api/v1/sessions/{id}` | read + live booked count |
| booking-api | `POST /api/v1/bookings` | Java checks -> `INSERT Booking` (PARTNER) -> `UPDATE ExamSession SeatsAvailable-1` -> (`trg_Booking_SeatSync` -1 again) -> `INSERT AuditLog`; or `INSERT Waitlist` |
| booking-api | `GET /api/v1/bookings/{id}` | read |
| booking-api | `DELETE /api/v1/bookings/{id}?reason=` | `EXEC usp_CancelBooking` |
| booking-api | `POST /api/v1/bookings/{id}/reschedule` | Java cancel (proc) + Java book (direct) in one transaction |
| booking-api | `GET /api/v1/candidates/{no}/bookings` | read |
| booking-api | `GET /api/v1/candidates/{no}/eligibility?examCode=&date=` | `Result` rows -> working-day rule |
| results-service | `POST /api/v1/results` | `INSERT Result`, `UPDATE Booking -> ATTENDED`, `INSERT AuditLog` |
| results-service | `POST /api/v1/results/upload` | same, row by row |
| results-service | `GET /api/v1/results/candidate/{no}` | read |
| capacity-sync | `POST /sync/run`, cron 02:00 | `UPDATE ExamSession SeatsAvailable` for OPEN sessions, one `AuditLog` row |
| capacity-sync | `GET /sync/last` | in-memory last run |

## 2. Request flows

### 2.1 Booking

```mermaid
sequenceDiagram
  autonumber
  participant C as Candidate (portal)
  participant P as Portal BookingService.cs
  participant BS as usp_BookSeat
  participant DB as Booking / ExamSession / Waitlist
  participant T as trg_Booking_SeatSync
  participant X as Partner
  participant J as booking-api BookingService.java

  rect rgb(235,245,255)
  Note over C,T: Portal path
  C->>P: POST /Booking/Book (sessionId, candidateNumber, accommodation?)
  P->>P: blank accommodation -> Candidate.DefaultAccommodationCode
  P->>P: RetakeRules (calendar days, Result rows only)
  P->>BS: EXEC usp_BookSeat
  BS->>DB: SELECT ExamSession WITH (UPDLOCK, HOLDLOCK)
  BS->>BS: OPEN? duplicate? ET25/ET50 within AccommodationCapacity?
  alt SeatsAvailable > 0
    BS->>DB: INSERT Booking BOOKED, Channel=PORTAL
    DB-->>T: AFTER INSERT (Channel=PORTAL -> no-op)
    BS->>DB: SeatsAvailable - 1
  else full
    BS->>DB: INSERT Booking WAITLISTED + Waitlist MAX(Position)+1
  end
  BS->>DB: INSERT AuditLog
  end

  rect rgb(255,240,235)
  Note over X,T: Partner path
  X->>J: POST /api/v1/bookings {sessionId, candidateNumber, accommodationCode?, externalRef?}
  J->>DB: plain SELECTs (session, candidate, duplicate, ET count) - no lock
  J->>J: retake check (working days)
  J->>DB: SELECT SeatsAvailable (no lock)
  alt seats > 0
    J->>DB: INSERT Booking BOOKED, Channel=PARTNER, ExternalRef
    DB-->>T: AFTER INSERT -> SeatsAvailable - 1
    J->>DB: UPDATE SeatsAvailable - 1  (second decrement, HB-01)
  else
    J->>DB: INSERT Booking WAITLISTED + Waitlist MAX(Position)+1
  end
  J->>DB: INSERT AuditLog (Actor=booking-api)
  end
```

### 2.2 Cancellation

```mermaid
sequenceDiagram
  participant P as Portal (POST /Booking/Cancel/{id})
  participant J as booking-api (DELETE /api/v1/bookings/{id})
  participant CB as usp_CancelBooking
  participant DB as Booking / ExamSession / Waitlist / AuditLog
  P->>CB: EXEC @BookingId, @Reason, @Actor
  J->>CB: EXEC @BookingId, @Reason, 'booking-api'
  CB->>DB: SELECT Booking WITH (UPDLOCK, HOLDLOCK)
  alt status BOOKED
    CB->>DB: Status=CANCELLED, SeatsAvailable + 1
  else WAITLISTED
    CB->>DB: Status=CANCELLED, DELETE Waitlist row (positions not renumbered)
  else other
    CB-->>P: RAISERROR 50000 state 4
  end
  CB->>DB: INSERT AuditLog
  Note over CB,DB: No waitlist promotion (HB-07)
```

### 2.3 Capacity change

```mermaid
sequenceDiagram
  participant A as Centre admin (portal)
  participant S as capacity-sync 02:00 / POST /sync/run
  participant CC as usp_ChangeCapacity
  participant ES as ExamSession
  participant T as trg_ExamSession_Update
  participant B as Booking / Waitlist
  A->>CC: POST /Admin/Sessions/Capacity/{id} -> EXEC (id, newCapacity, actor)
  CC->>ES: lock row, reject <0 or < AccommodationCapacity
  CC->>ES: Capacity=new, SeatsAvailable += delta (floor 0)
  ES-->>T: AFTER UPDATE (also for any ad-hoc UPDATE)
  T->>ES: LastModified = now
  alt Capacity decreased and BOOKED > Capacity
    T->>B: newest BOOKED -> WAITLISTED, append Waitlist
    T->>ES: SeatsAvailable = Capacity - BOOKED
  end
  CC->>B: INSERT AuditLog "CAPACITY a -> b" (no per-booking audit)
  S->>ES: for every OPEN session: SeatsAvailable = max(0, Capacity - AccommodationCapacity - standard BOOKED/ATTENDED)
  ES-->>T: AFTER UPDATE (Capacity unchanged -> only LastModified)
  S->>B: one AuditLog row (EntityId 0)
```

### 2.4 Retake check

```mermaid
flowchart TB
  subgraph Portal
    P1[Book / Reschedule / GET /Eligibility] --> P2[EligibilityService.cs<br/>attempts = Result rows for candidate+exam]
    P2 --> P3{any Passed?}
    P3 -- yes --> PB[Blocked: already passed]
    P3 -- no --> P4["earliest = max(SessionDate).Date + RetakeWaitDays (calendar)"]
    P4 --> P5{requested.Date < earliest?}
    P5 -- yes --> PW[Blocked: wait]
    P5 -- no --> PA[Allowed -> EXEC usp_BookSeat]
  end
  subgraph Java[booking-api]
    J1[POST bookings / reschedule / GET eligibility] --> J2[ResultDao<br/>Result rows ordered by StartsAt desc]
    J2 --> J3{any Passed?}
    J3 -- yes --> JB[ALREADY_PASSED]
    J3 -- no --> J4["earliest = addWorkingDays(last, RetakeWaitDays)<br/>JVM default time zone"]
    J4 --> J5{onDate before earliest?}
    J5 -- yes --> JW[RETAKE_WAIT_NOT_ELAPSED]
    J5 -- no --> JA[Allowed -> direct INSERT]
  end
  DBX[(usp_BookSeat / direct SQL / IMPORT:<br/>no retake check at all)]
```

Confirmed difference: failed 2026-11-07, wait 30 -> portal earliest 2026-12-07, Java 2026-12-18
([HB-04](hidden-behavior.md#hb-04)).

## 3. Deployment and configuration

```mermaid
flowchart TB
  subgraph Local["Local dev (README, scripts/run-legacy.sh)"]
    DK[Docker: mcr.microsoft.com/mssql/server:2022-latest<br/>container mssql :1433, sa / AD_SA_PASSWORD]
    APPLY[db/apply.sh - drops and recreates AssessmentDelivery<br/>schema -> procs -> triggers -> seed]
    JV[java -jar booking-api / results-service / capacity-sync<br/>JDK 8, Maven wrapper services/mvnw]
    WIN[Windows: scripts/run-portal.ps1<br/>IIS Express :8080]
    DK --> APPLY
    JV --> DK
    WIN --> DK
  end
  subgraph Azure["Azure (profiles / transforms)"]
    AS1[App Service: portal<br/>connection string from App Service settings]
    AS2[App Service / VM: Java services<br/>SPRING_PROFILES_ACTIVE=azure<br/>password AZURE_SQL_PASSWORD]
    ASQL[(Azure SQL: AssessmentDelivery)]
    AS1 --> ASQL
    AS2 --> ASQL
  end
```

| Setting | Where | Value (local) |
|---|---|---|
| Portal connection string | `AssessmentDelivery.Web/Web.config:12` | `localhost,1433`, `ad_app`, `MultipleActiveResultSets=True`, `Application Name=AssessmentDeliveryPortal` |
| Portal app settings | `AssessmentDelivery.Web/Web.config:20-27` | `Portal.KioskMode=false`, `Portal.ResultsReleaseDelayHours=0`, environment name/build |
| booking-api | `services/booking-api/src/main/resources/application.properties` | port 8081, pool 20, `booking.channel=PARTNER`, `booking.audit-actor=booking-api`, password `${AD_DB_PASSWORD:...}` |
| results-service | `services/results-service/src/main/resources/application.properties` | port 8082, pool 20, 5 MB upload, `results.audit-actor=results-service` |
| capacity-sync | `services/capacity-sync/src/main/resources/application.properties` | port 8083, pool 5, `capacity.sync.audit-actor=capacity-sync`; cron in code (`NightlySyncJob.java:14`, server time) |
| Azure profiles | `services/*/src/main/resources/application-azure.properties` | placeholder server/user, `AZURE_SQL_PASSWORD` |
| DB login | `db/schema/000_create_database.sql:13-26` | `ad_app` in `db_datareader` + `db_datawriter` + `GRANT EXECUTE`; every component shares this one login, so `AuditLog.Actor` from triggers is the SQL login, not the app |

Operational facts observed while running:
- `scripts/run-legacy.sh` always re-creates the database (`SKIP_DB=1` keeps it); a fresh seed has zero
  rows in `db/checks/seat-drift.sql`.
- Java unit tests: 5 tests across the three modules, all pass (`services/mvnw -B test`); none touches
  the database, concurrency, triggers or channel parity.
- No job in the repository moves sessions OPEN -> CLOSED/DELIVERED although `NightlySyncJob.java:13`
  refers to a "01:00 session close-out" ([HB-13](hidden-behavior.md#hb-13)).
