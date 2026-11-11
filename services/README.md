# Northwind Assessment Network - Java services

Partner-facing and back-office services that sit next to the .NET portal and share the
`AssessmentDelivery` SQL Server database. See `docs/SYSTEM_OVERVIEW.md` for the shared
contract (tables, procs, routes).

| Module | Port | What it does |
|---|---|---|
| `booking-api` | 8081 | Partner booking channel: sessions, bookings, cancel/reschedule, retake eligibility |
| `results-service` | 8082 | Score ingestion (single result or CSV upload from centres) and candidate results |
| `capacity-sync` | 8083 | Nightly (02:00) re-sync of `ExamSession.SeatsAvailable`, plus manual trigger |

Stack: Java 8, Spring Boot 1.5.22, Spring MVC, `JdbcTemplate` (no ORM), mssql-jdbc 7.4 (jre8), JUnit 4.

## Build

Needs a JDK 8 (`JAVA_HOME` pointing at Temurin 8 / OpenJDK 8). The Maven wrapper downloads Maven itself.

```bash
cd services
./mvnw clean package        # compiles, runs the unit tests, builds services/<module>/target/<module>.jar
./mvnw test                 # unit tests only
```

## Run

The easy way on Linux (starts SQL Server 2022 in Docker, applies `db/`, builds and starts all three):

```bash
scripts/run-legacy.sh           # start
scripts/run-legacy.sh status
scripts/run-legacy.sh stop
```

Logs and PIDs go to `.run/`. Every `start` re-creates the database from `db/` (all data is dropped);
use `SKIP_DB=1 scripts/run-legacy.sh` to restart the services and keep the data. The portal is Windows-only and is not started by this script.

By hand, against a database that is already there:

```bash
java -jar services/booking-api/target/booking-api.jar
java -jar services/results-service/target/results-service.jar
java -jar services/capacity-sync/target/capacity-sync.jar
```

`scripts/mixed-traffic.sh` books a batch of seats through both channels (portal via `usp_BookSeat`,
partner via booking-api) on a freshly seeded database and prints a short summary.

## Profiles / configuration

| File | Used for |
|---|---|
| `src/main/resources/application.properties` | local dev: `localhost:1433`, DB `AssessmentDelivery`, login `ad_app`, password from `AD_DB_PASSWORD` (defaults to the dev password) |
| `src/main/resources/application-azure.properties` | Azure SQL. Activate with `--spring.profiles.active=azure`; fill in the server/user placeholders and set `AZURE_SQL_PASSWORD` |

Any property can be overridden on the command line, e.g. `--server.port=9081` or
`--spring.datasource.url=...`.

## Endpoints

Dates are `yyyy-MM-dd`, timestamps `yyyy-MM-dd'T'HH:mm:ss`. Errors come back as
`{"error": "...", "message": "..."}` with 400 (bad input), 404 (unknown id) or 409 (business rule).

### booking-api :8081

| Method | Path | |
|---|---|---|
| GET | `/api/v1/sessions?examCode=&centerCode=&from=&to=` | sessions with seatsAvailable |
| GET | `/api/v1/sessions/{sessionId}` | session detail incl. bookedSeats |
| POST | `/api/v1/bookings` | `{sessionId, candidateNumber, accommodationCode?, externalRef?}` -> 201 `{bookingId, status}` |
| GET | `/api/v1/bookings/{bookingId}` | |
| DELETE | `/api/v1/bookings/{bookingId}?reason=` | cancel |
| POST | `/api/v1/bookings/{bookingId}/reschedule` | `{newSessionId}` |
| GET | `/api/v1/candidates/{candidateNumber}/bookings` | bookings from every channel |
| GET | `/api/v1/candidates/{candidateNumber}/eligibility?examCode=&date=` | `{eligible, reason, earliestRetakeDate}` |

```bash
curl 'http://localhost:8081/api/v1/sessions?examCode=NW-NET-101&from=2026-12-01&to=2026-12-31'
curl http://localhost:8081/api/v1/sessions/2

curl -X POST -H 'Content-Type: application/json' http://localhost:8081/api/v1/bookings \
     -d '{"sessionId":2,"candidateNumber":"NW00000001","externalRef":"PTR-1001"}'
# 201 {"bookingId":1,"status":"BOOKED"}   (WAITLISTED when the session is full)

curl http://localhost:8081/api/v1/bookings/1
curl -X POST -H 'Content-Type: application/json' http://localhost:8081/api/v1/bookings/1/reschedule -d '{"newSessionId":3}'
curl -X DELETE 'http://localhost:8081/api/v1/bookings/1?reason=Candidate%20request'

curl http://localhost:8081/api/v1/candidates/NW00000001/bookings
curl 'http://localhost:8081/api/v1/candidates/NW00000001/eligibility?examCode=NW-NET-101&date=2026-12-01'
# {"eligible":true,"reason":"FIRST_ATTEMPT","earliestRetakeDate":null}
```

Eligibility reasons: `FIRST_ATTEMPT`, `ALREADY_PASSED`, `RETAKE_WAIT_NOT_ELAPSED`, `RETAKE_ALLOWED`.

### results-service :8082

| Method | Path | |
|---|---|---|
| POST | `/api/v1/results` | `{bookingId, score, scoredAt}` -> 201 `{status:"CREATED",...}`; 200 `{status:"DUPLICATE",resultId}` if the same result is already stored; 409 `DUPLICATE_RESULT` with `storedScore`/`submittedScore` if a different one is |
| POST | `/api/v1/results/upload` | multipart `file`, CSV `candidateNumber,sessionId,score,scoredAt` with a header row |
| GET | `/api/v1/results/candidate/{candidateNumber}` | |

```bash
curl -X POST -H 'Content-Type: application/json' http://localhost:8082/api/v1/results \
     -d '{"bookingId":7,"score":512,"scoredAt":"2026-09-10T12:30:00"}'

cat > NWLON01_20260910.csv <<CSV
candidateNumber,sessionId,score,scoredAt
NW00000003,1,780,2026-09-10T12:31:00
CSV
curl -F file=@NWLON01_20260910.csv http://localhost:8082/api/v1/results/upload
# {"fileName":"NWLON01_20260910.csv","sha256":"...","uploadId":1,"alreadyUploaded":false,"rowsRead":1,
#  "imported":1,"duplicates":0,"conflicts":0,"skipped":0,"errors":[],"conflictDetails":[]}

curl http://localhost:8082/api/v1/results/candidate/NW00000003
```

Bad CSV rows are skipped and listed in `errors` with their line number; the file name is stored
in `Result.SourceFile`.

Idempotency (MBA-3827). A booking holds at most one result (`UQ_Result_Booking`):

- Same `{bookingId, score, scoredAt}` again (REST or CSV row): a no-op, reported as a duplicate.
  A REST post without `scoredAt` matches on the score alone.
- Different score or `scoredAt`: rejected, the stored row is kept, and an `AuditLog` row
  (`EntityName='Result'`, `Action='DUPLICATE_REJECTED'`) records both values. REST returns 409
  `DUPLICATE_RESULT`; a CSV row is counted in `conflicts` and listed in `conflictDetails` by line.
- Concurrent identical posts: one inserts, the others get the duplicate answer (the unique-key
  violation is caught and re-classified, never a 500).
- Each imported file is recorded in `dbo.ResultUpload` with the SHA-256 of its bytes. Uploading the
  same content again returns 200 with `alreadyUploaded:true` and the original `uploadId`; rows are
  only re-checked (`imported=0`, already-stored rows count as `duplicates`) and nothing is inserted.
  Send corrected or new rows in a new file.
- Rows still commit one by one (HB-19): a bad, duplicate or conflicting row never rolls back the
  good rows of the same file. `skipped = rowsRead - imported`.

### capacity-sync :8083

| Method | Path | |
|---|---|---|
| POST | `/sync/run` | run the seat re-sync now |
| GET | `/sync/last` | last run (status, trigger, startedAt, finishedAt, sessionsChecked, sessionsUpdated) |

```bash
curl -X POST http://localhost:8083/sync/run
curl http://localhost:8083/sync/last
```

Every run (scheduled or manual) writes one `AuditLog` row.
