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
| Capacity floor | `scheduling.rules.accommodation-floor` | `allocated` (`booked-extra-time` = PR #51) |
| Waitlist offer TTL (ISO-8601) | `SCHEDULING_WAITLIST_OFFER_TTL` / `scheduling.waitlist.offer-ttl` | `PT24H` |
| Offer expiry job on/off | `SCHEDULING_WAITLIST_EXPIRY_ENABLED` / `scheduling.waitlist.expiry-enabled` | `true` |
| Offer expiry poll interval | `SCHEDULING_WAITLIST_EXPIRY_POLL_MS` / `scheduling.waitlist.expiry-poll-ms` | `60000` |

The service refuses to start if the scheduling datasource points at `AssessmentDelivery`.

## Integration contract

- Session ids and center ids/codes equal the legacy ids. Imported bookings keep their legacy
  `BookingId` (`origin = LEGACY_IMPORT`); bookings created here come from the sequence
  `booking_id_seq` starting at **50,000,001** (`origin = MODERN`, `legacy_booking_id` filled by the
  relay on first projection). Every `/api/v2/bookings/{id}` endpoint accepts either id.
- `GET /internal/routing/resolve?sessionId=|bookingId=|centerId=|centerCode=` (exactly one) returns
  `{"centerCode","centerId","sessionId","bookingId","bookingOrigin"}` from local tables only, or
  404 `{"error":"NOT_FOUND","message":...}`; center/session lookups send `Cache-Control: max-age=60`.
- `POST /internal/import/sessions?centerCode=` (omit for all) re-imports idempotently (upsert by
  legacy id). Sessions that scheduling-service has already changed (`locally_modified = 1`) are
  skipped so modern writes are never overwritten.

## Single writer, outbox and relay (MBA-3823)

scheduling-service is the **only writer** of bookings and seat counts. The database enforces it:
`db/security/040_single_writer.sql` DENYs INSERT/UPDATE/DELETE on `dbo.Booking` / `dbo.Waitlist`, UPDATE of
`dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity)` to `ad_portal`, `ad_booking_api`, `ad_results`,
`ad_capacity_sync` and `ad_sched_import` (the four legacy booking procs were dropped in MBA-3824). Only
`ad_sched_relay` can write those tables (and cannot delete bookings). booking-api forwards v1 writes through the routing layer, results-service calls
`POST /api/v2/bookings/{bookingId}/attendance`, capacity-sync is disabled (`capacity.sync.enabled=false`).

- **Transactional outbox.** Every booking/capacity change inserts a row into `outbox_event` (Flyway `V2__outbox.sql`)
  in the same local `Scheduling` transaction (`OutboxWriter` is `Propagation.MANDATORY`): `BookingCreated`,
  `BookingWaitlisted`, `BookingCancelled`, `BookingRescheduled`, `AttendanceRecorded`, `CapacityChanged`, and the
  waitlist offer events `SeatOffered`, `SeatOfferAccepted`, `SeatOfferDeclined`, `SeatOfferExpired` (MBA-3829).
  `event_id` is an IDENTITY (monotonic). The payload carries the full resulting state of every touched session and
  booking (status, accommodation code, channel, waitlist position, capacity, derived `SeatsAvailable`), not deltas,
  so applying it is naturally idempotent.
- **Relay** (`relay` package, `scheduling.relay.enabled`, polls every 200 ms). For each pending event, in id order,
  one AssessmentDelivery transaction as `ad_sched_relay`: check `dbo.RelayCheckpoint` (key-range locked), upsert
  `Booking` (a modern booking gets its legacy `BookingId` on first relay, kept in `dbo.RelayBookingMap` and written
  back to `booking.legacy_booking_id`, so v1 URLs and `Result.BookingId` keep working), sync `Waitlist`, set
  `ExamSession` capacity/seats, add one `AuditLog` row, insert the checkpoint. Then `relayed_at` is set in
  `Scheduling`. A crash between the two commits only causes a replay, which the checkpoint turns into a no-op.
  A failing event stops the batch (ordering) and is retried with `attempts` / `last_error` recorded.
- **Trigger neutralization.** The relay connection runs `sp_set_session_context N'writer', N'scheduling-relay'`
  (read-only). `trg_Booking_SeatSync` returns immediately and `trg_ExamSession_Update` only stamps `LastModified`,
  so the legacy tables hold exactly what scheduling-service decided.
- **Reconciliation.** `GET /internal/reconciliation[?writeReport=true]` or the CLI
  `java -jar target/scheduling-service.jar reconcile [--wait-seconds=30]` (exit 0 only with 0 mismatches and
  0 drift rows) compares, per session, Capacity, SeatsAvailable, BOOKED/ATTENDED/WAITLISTED counts, accommodation
  seats used and every booking's status/session/accommodation, includes `db/checks/seat-drift.sql` and the relay
  backlog/lag, and writes `reports/reconciliation-<timestamp>.md`. `GET /internal/relay/status` and
  `POST /internal/relay/run` expose the relay.

  What reconciliation catches on the legacy estate (`legacy-baseline`, see `reports/drift-before-after.md`) are the
  two drift causes in `docs/as-is/hidden-behavior.md`: **HB-01** (`docs/as-is/hidden-behavior.md:51-67`), the
  partner double decrement in booking-api (`BookingService.java:110-116`, `SessionDao.java:82-83` at
  `legacy-baseline`) plus `db/triggers/032_trg_Booking_SeatSync.sql:12,19,23`, and **HB-03**
  (`docs/as-is/hidden-behavior.md:148-163`), capacity-sync's seat formula
  (`services/capacity-sync/.../CapacitySyncService.java:29-34,59-67`) disagreeing with `db/checks/seat-drift.sql:3,18,24`.
  With a single writer, one seat formula, the trigger skip for relayed rows and `capacity.sync.enabled=false`,
  neither can occur.

**Alternative considered: CDC / Debezium.** SQL Server CDC on the `Scheduling` DB with Debezium would give the same
guarantee (changes published only after commit, no dual write) without an outbox table, but needs Kafka + Kafka
Connect (and CDC agent jobs) to run and operate. The outbox is one table and an in-process poller; swapping later
is contained to the relay (the projection code consumes the same state-carrying events).

| Relay setting | Env var | Default |
|---|---|---|
| Enable relay | `SCHEDULING_RELAY_ENABLED` | `false` (`true` in the run script) |
| Legacy write target | `RELAY_DB_URL`, `RELAY_DB_USER`, `RELAY_DB_PASSWORD` | db `AssessmentDelivery`, `ad_sched_relay`, local dev password |
| Poll interval | `SCHEDULING_RELAY_POLL_MS` | `200` |
| Report directory | `RECONCILIATION_REPORT_DIR` | `reports` |

## API (`/api/v2`)

| Method | Path | Notes |
|---|---|---|
| GET | `/centers/{centerCode}/sessions?examCode=&from=&to=` | v1 `SessionInfo` fields + `accommodationUsed`, `waitlistCount`; `from` inclusive, `to` exclusive (`yyyy-MM-dd`) |
| GET | `/sessions/{sessionId}` | |
| POST | `/bookings` | `{"sessionId","candidateNumber","accommodationCode","externalRef","channel"}` -> 201 `BOOKED`/`WAITLISTED` |
| GET | `/bookings/{bookingId}` | v1 `BookingInfo` fields + `origin`, `legacyBookingId`, `waitlistPosition` |
| DELETE | `/bookings/{bookingId}?reason=` | 200 booking |
| POST | `/bookings/{bookingId}/reschedule` | `{"newSessionId"}` -> 201 (the new booking); same exam only |
| POST | `/bookings/{bookingId}/attendance` | BOOKED -> ATTENDED (idempotent; 409 `BOOKING_NOT_ACTIVE` otherwise); used by results-service |
| GET | `/candidates/{candidateNumber}` , `/candidates/{n}/bookings` , `/candidates/{n}/eligibility?examCode=&date=` | |
| PUT | `/sessions/{sessionId}/capacity` | `{"newCapacity","actor"}` -> `{"session","bumpedBookingIds"}` |
| GET | `/sessions/{sessionId}/waitlist` | `[{"position","bookingId","candidateNumber","candidateName","addedAt","accommodationCode","offerState","offerId","offerExpiresAt","skipReason"}]`; `offerState` = `OFFERED` / `SKIPPED` (needs an accommodation seat) / `WAITING` |
| GET | `/candidates/{n}/offers` | the candidate's seat offers (all statuses); `/candidates/{n}/bookings` also carries `pendingOffer` |
| GET | `/offers/{offerId}` | `{"offerId","bookingId","sessionId","status","seatKind","offeredAt","expiresAt","decidedAt",...}` |
| POST | `/offers/{offerId}/accept` | `{"actor"}` optional -> 200 offer `ACCEPTED`; booking BOOKED, waitlist compacted |
| POST | `/offers/{offerId}/decline` | -> 200 offer `DECLINED`; booking CANCELLED, seat offered to the next candidate |
| POST | `/bookings/{bookingId}/offer/accept`, `/bookings/{bookingId}/offer/decline` | aliases acting on the booking's pending offer |
| GET | `/centers`, `/exams` | reference lists for the UI |

Errors: `{"error","message","legacyCode"}` with booking-api v1 codes/statuses, e.g.
`SESSION_NOT_OPEN` 409/50001, `DUPLICATE_BOOKING` 409/50002, `NO_ACCOMMODATION_SEAT` 409/50003,
`NOT_CANCELLABLE` 409/50004, `CAPACITY_BELOW_ACCOMMODATION_FLOOR` 409/50005, `DIFFERENT_EXAM`
409/50006, `VALIDATION` 400, `*_NOT_FOUND` 404 (incl. `OFFER_NOT_FOUND`), `OFFER_NOT_PENDING` 409,
`OFFER_EXPIRED` 409.

### Waitlist auto-offer (MBA-3829, ADR 0003)

A seat freed on an OPEN session (cancel, reschedule-out, capacity increase, declined/expired offer) is offered
to the lowest waitlist position whose accommodation needs fit (`WaitlistOfferPolicy`); ET25/ET50 candidates
without a free accommodation seat are skipped but keep their position. A PENDING offer holds the seat
(`seatsAvailable` stays 0) until `expires_at`. Accept books the candidate and compacts later positions;
decline or expiry cancels the waitlisted booking and moves on. See `docs/adr/0003-waitlist-auto-offer.md`.

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

- Waitlist auto-offer browser flow (MBA-3829: full session with waitlist 1-3 via the API, candidate A cancels in
  My bookings, candidate B accepts the offer, admin waitlist compacted; screenshots in
  `e2e/screenshots/waitlist-offer[-<SHOT_PREFIX>]/`):

  ```bash
  BASE_URL=http://localhost:8090 node waitlist-offer.mjs
  BASE_URL=http://localhost:8000 SHOT_PREFIX=gateway node waitlist-offer.mjs   # through the routing gateway
  ```
