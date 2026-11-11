# scheduling-service

Java 21 / Spring Boot 3 service that owns seat inventory for the Northwind Assessment Network test
centers: sessions, bookings, waitlists and capacity changes (story MBA-3821, M2 of the
assessment-delivery strangler-fig migration). Port **8090**.

- Own SQL Server database **`Scheduling`** (never `AssessmentDelivery`), created by Flyway
  (`src/main/resources/db/migration/V1__scheduling_schema.sql`).
- Reference data (test centers, rooms, exams, candidates, results) and the bootstrap sessions,
  bookings and waitlists are **copied** into local tables by a snapshot importer that reads
  `AssessmentDelivery` with the read-only login `ad_sched_import`
  (`db/security/035_sched_import_login.sql`). No cross-database joins, no linked servers, no writes
  to `AssessmentDelivery`.
- Stored-procedure / trigger rules are plain Java in `domain/` (no Spring); decisions where the
  legacy channels disagree are in [`docs/adr/0002-ported-rule-decisions.md`](../../docs/adr/0002-ported-rule-decisions.md).
- React + TypeScript + Vite + Tailwind SPA (`frontend/`) built into the jar and served at `/app`.

## Run

```bash
scripts/run-scheduling-service.sh          # SQL Server container, importer login, build, java -jar, import
scripts/run-scheduling-service.sh status
scripts/run-scheduling-service.sh stop
```

Then open http://localhost:8090/app (candidate) or http://localhost:8090/app/admin/centers/BOS01
(test-center admin; no sign-in, kiosk-era).

Manual equivalent (JDK 21, Maven 3.9; Node is downloaded by `frontend-maven-plugin`):

```bash
./db/apply.sh                                         # legacy DB + seed (AD_SA_PASSWORD)
sqlcmd -S localhost,1433 -U sa -P "$AD_SA_PASSWORD" -C -b -i db/security/035_sched_import_login.sql
cd services/scheduling-service
mvn -B package -DskipTests
SCHEDULING_IMPORT_ON_STARTUP=true java -jar target/scheduling-service.jar
```

| Setting | Env var | Default |
|---|---|---|
| Own DB JDBC URL / user / password | `SCHEDULING_DB_URL`, `SCHEDULING_DB_USER`, `SCHEDULING_DB_PASSWORD` | `localhost:1433`, db `Scheduling`, `sa`, local dev password |
| Legacy read-only source | `LEGACY_DB_URL`, `LEGACY_DB_USER`, `LEGACY_DB_PASSWORD` | db `AssessmentDelivery`, `ad_sched_import` |
| Import all centers at startup | `SCHEDULING_IMPORT_ON_STARTUP` | `false` (`true` in the run script) |
| Retake day counting | `scheduling.rules.retake-days` | `calendar` (`working` = booking-api) |
| Capacity floor | `scheduling.rules.accommodation-floor` | `booked-extra-time` (PR #51: only booked ET25/ET50 seats block a reduction; `allocated` = strict legacy floor) |

The service refuses to start if the scheduling datasource points at `AssessmentDelivery`.

## Integration contract

- Session ids and center ids/codes equal the legacy ids. Imported bookings keep their legacy
  `BookingId` (`origin = LEGACY_IMPORT`); bookings created here come from the sequence
  `booking_id_seq` starting at **50,000,001** (`origin = MODERN`, `legacy_booking_id` null until the
  M4 relay fills it).
- `GET /internal/routing/resolve?sessionId=|bookingId=|centerId=|centerCode=` (exactly one) returns
  `{"centerCode","centerId","sessionId","bookingId","bookingOrigin"}` from local tables only, or
  404 `{"error":"NOT_FOUND","message":...}`; center/session lookups send `Cache-Control: max-age=60`.
- `POST /internal/import/sessions?centerCode=` (omit for all) re-imports idempotently (upsert by
  legacy id). Sessions that scheduling-service has already changed (`locally_modified = 1`) are
  skipped so modern writes are never overwritten.

## API (`/api/v2`)

| Method | Path | Notes |
|---|---|---|
| GET | `/centers/{centerCode}/sessions?examCode=&from=&to=` | v1 `SessionInfo` fields + `accommodationUsed`, `waitlistCount`; `from` inclusive, `to` exclusive (`yyyy-MM-dd`) |
| GET | `/sessions/{sessionId}` | |
| POST | `/bookings` | `{"sessionId","candidateNumber","accommodationCode","externalRef","channel"}` -> 201 `BOOKED`/`WAITLISTED` |
| GET | `/bookings/{bookingId}` | v1 `BookingInfo` fields + `origin`, `legacyBookingId`, `waitlistPosition` |
| DELETE | `/bookings/{bookingId}?reason=` | 200 booking |
| POST | `/bookings/{bookingId}/reschedule` | `{"newSessionId"}` -> 201 (the new booking); same exam only |
| GET | `/candidates/{candidateNumber}` , `/candidates/{n}/bookings` , `/candidates/{n}/eligibility?examCode=&date=` | |
| PUT | `/sessions/{sessionId}/capacity` | `{"newCapacity","actor"}` -> `{"session","bumpedBookingIds"}` |
| GET | `/sessions/{sessionId}/waitlist` | `[{"position","bookingId","candidateNumber","candidateName","addedAt"}]` |
| GET | `/centers`, `/exams` | reference lists for the UI |

Errors: `{"error","message","legacyCode"}` with booking-api v1 codes/statuses, e.g.
`SESSION_NOT_OPEN` 409/50001, `DUPLICATE_BOOKING` 409/50002, `NO_ACCOMMODATION_SEAT` 409/50003,
`NOT_CANCELLABLE` 409/50004, `CAPACITY_BELOW_ACCOMMODATION_FLOOR` 409/50005, `DIFFERENT_EXAM`
409/50006, `VALIDATION` 400, `*_NOT_FOUND` 404.

## UI deep links

`/app?centerCode=BOS01&examCode=&date=`, `/app/book?sessionId=`, `/app/my-bookings?candidateNumber=`,
`/app/reschedule/{bookingId}`, `/app/admin/centers/{centerCode}`, `/app/admin/sessions/{sessionId}`.
Every `/app/**` path is forwarded to the SPA; the SPA calls `/api/v2/...` with relative URLs so it
works behind the routing gateway on :8000. For UI development: `cd frontend && npm run dev`
(Vite on :5173 proxies `/api` to :8090).

## Tests

```bash
cd services/scheduling-service
mvn -B verify          # domain unit tests + JaCoCo gate (domain >= 90% lines), frontend vitest,
                       # Testcontainers SQL Server integration tests (*IT), jar with the SPA
mvn -B verify -DskipFrontend=true   # backend only
```

- Integration tests start `mcr.microsoft.com/mssql/server:2022-latest`, apply the legacy `db/`
  scripts and the importer login, start the app with `scheduling.import.on-startup=true` and drive
  the HTTP API. Docker Engine 29+ needs Testcontainers 1.21.4 and `api.version=1.44`
  (`src/test/resources/docker-java.properties`), both already configured.
- JaCoCo report: `target/site/jacoco/index.html`.
- Browser verification (steps 1-7 of the story, headless, screenshots in `e2e/screenshots/`):

  ```bash
  cd e2e && npm ci && npx playwright install chromium
  BASE_URL=http://localhost:8090 CENTER_CODE=BOS01 node run.mjs
  ```
