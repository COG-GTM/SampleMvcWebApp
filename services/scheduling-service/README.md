# scheduling-service

Owns exam scheduling and seat capacity (`ExamSession`, `Booking`, `Waitlist` and their `AuditLog` rows) in its own
`Scheduling` database. It replaces the `usp_BookSeat` / `usp_CancelBooking` / `usp_ChangeCapacity` / `usp_RescheduleBooking`
procs, the `trg_Booking_SeatSync` / `trg_ExamSession_Update` triggers and `services/capacity-sync` in `AssessmentDelivery`.
Java 21, Spring Boot 3, JdbcTemplate, Flyway. Port **8084**. API: `docs/to-be/scheduling-service-openapi.yaml`.

Legacy behaviour, defects included, is the spec. The Phase 0 characterization suite
(`services/scheduling-characterization`) runs unchanged against this service (`-Dtarget=scheduling-service`).

## Build and test

```bash
cd services/scheduling-service
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B verify                        # unit + Testcontainers SQL Server tests
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B -DskipTests package            # target/scheduling-service.jar

cd ../scheduling-characterization                                                   # acceptance: the Phase 0 suite
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B verify -Dtarget=scheduling-service
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B verify -Dtarget=scheduling-service -Dconcurrency.iterations=100 -Dconcurrency.threads=16
```

The characterization target provisions SQL Server 2022 with `db/apply.sh` (the same way as the legacy target). It then
runs `db/bootstrap.sql` as sa and launches `target/scheduling-service.jar` against the result. To use a service that is
already running instead, pass `-Dscheduling.baseUrl=http://localhost:8084/api/v1 -Dlegacy.jdbcUrl=...`; that service
needs `scheduling.storage-api.enabled=true`.

## Run locally

```bash
WITH_SCHEDULING=1 scripts/run-legacy.sh        # legacy estate + Scheduling DB bootstrap + this service on :8084
```

Or run it manually against the `mssql` container:

```bash
/opt/mssql-tools18/bin/sqlcmd -C -S localhost,1433 -U sa -P 'Str0ng!Passw0rd' -i services/scheduling-service/db/bootstrap.sql
java -jar services/scheduling-service/target/scheduling-service.jar
```

`db/bootstrap.sql` is idempotent. It creates the `Scheduling` database owned by the shared `ad_app` login. It also
enables SQL Server Change Tracking on `AssessmentDelivery` for `Exam`, `TestCenter`, `Room` and `Candidate` and grants
`VIEW CHANGE TRACKING` to `ad_app`. Flyway (`src/main/resources/db/migration`) creates the schema at startup.

| Env var | Default | |
|---|---|---|
| `SCHEDULING_PORT` | `8084` | HTTP port |
| `SCHEDULING_DB_URL` / `_USER` / `_PASSWORD` | `jdbc:sqlserver://localhost:1433;databaseName=Scheduling;...` / `ad_app` | own database |
| `AD_DB_URL` / `AD_DB_USER` / `AD_DB_PASSWORD` | `...databaseName=AssessmentDelivery...` / `ad_app` | reference-data source and initial import |
| `SCHEDULING_IMPORT` | `if-empty` | initial import: `if-empty`, `always` (replace), `never` |
| `SCHEDULING_REF_POLL_MS` | `30000` | Change Tracking relay poll interval (`0` = off) |
| `SCHEDULING_RECONCILER_CRON` | `0 0 2 * * *` | seat reconciler schedule (capacity-sync's nightly run; `-` = off) |
| `SCHEDULING_STORAGE_API` | `false` | `/api/v1/admin/storage/*` direct-write compatibility API (tests and dual-run only) |
| `SCHEDULING_LEGACY_SEAT_ACCOUNTING` | `false` | HB-01/HB-02 partner double decrement (parity only; the characterization target sets `true`) |

## Security

No authentication: the service keeps the legacy trust model (shared `ad_app`, internal network only). Expose port 8084 only on the estate network. See contract section 9, "Security posture". `/admin/import` and `/admin/storage/*` are off unless `SCHEDULING_IMPORT_API` / `SCHEDULING_STORAGE_API` are set.

## Data and reference import

- **Owned tables:** `ExamSession`, `Booking`, `Waitlist`, `AuditLog`, plus `Attempt` (results, for retake eligibility),
  `ReconcilerRun`, `IdempotencyKey`, `ImportRun` and `ReplicationState`. There are no triggers.
- **Replicated reference data:** `RefExam`, `RefTestCenter`, `RefRoom` and `RefCandidate` keep legacy IDs and have no
  cross-database FKs. Each row stores `SourceVersion` (monotonic) and a `Deleted` tombstone. A poller reads
  `CHANGETABLE(CHANGES ...)` on `AssessmentDelivery` and applies the changes. It falls back to a full resync when the
  tracked version expires. `PUT /api/v1/reference/{entity}/{id}` accepts pushed changes, and `POST /api/v1/reference/sync`
  forces a catch-up.
- **Initial import:** on startup with `if-empty`, or through `POST /api/v1/admin/import?replace=true`. It copies the
  reference tables, `ExamSession`, `Booking` and `Waitlist`, with identities preserved. It also copies their `AuditLog`
  rows and `Result` rows (as `Attempt`) from `AssessmentDelivery`.

## Semantics carried over

| Legacy | Here |
|---|---|
| `usp_BookSeat` | `SchedulingCommands.book`: `UPDLOCK, HOLDLOCK` on the session row; status → duplicate → ET25/ET50 cap checks; BOOKED decrements seats, otherwise WAITLISTED at `MAX(Position)+1` (no StartsAt check, HB-31) |
| `usp_CancelBooking` | `cancel`: BOOKED releases a seat, WAITLISTED deletes its waitlist row, no promotion and gaps stay (HB-06) |
| `usp_ChangeCapacity` + `trg_ExamSession_Update` | `changeCapacity` + `Demotion`: delta applied with a zero floor, then on a cut the newest BOOKED rows (`BookedAt DESC`) are demoted and seats recounted as `Capacity - BOOKED` (HB-05) |
| `usp_RescheduleBooking` | `reschedulePortal`: same-exam guard before the transaction, then cancel + book in one transaction with the same lock order, so crossing reschedules can still deadlock and the victim rolls back |
| `trg_Booking_SeatSync` | explicit: a PARTNER booking takes 2 seats (HB-01) only while `scheduling.legacy-seat-accounting=true` (env `SCHEDULING_LEGACY_SEAT_ACCOUNTING`). The runtime default is `false`; the characterization target turns it on |
| `CapacitySyncService.runSync` | `SeatReconciler`: `max(0, Capacity - AccommodationCapacity - StandardBooked)` with a compare-and-set, `CAPACITY_SYNC` audit rows, run history (HB-03) |

Proc rejections come back as `{error, message, legacyState}`. `legacyState` is the RAISERROR state, and `5000+state` is
the documented code (HB-14). The message text is the proc's.
