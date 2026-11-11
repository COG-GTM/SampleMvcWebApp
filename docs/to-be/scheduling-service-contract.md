# scheduling-service contract (narrative)

Companion to [`scheduling-service-openapi.yaml`](scheduling-service-openapi.yaml) (OpenAPI 3.0.3). Inputs: the cross-boundary inventory [`scheduling-boundary.md`](scheduling-boundary.md), `docs/as-is/` (HB-xx, MR-xx), and the executable pins in `services/scheduling-contract-tests/`. Legacy behaviour, defects included, is the spec. Where this contract keeps a defect, it cites the HB entry.

## 1. Ownership and boundary

- scheduling-service owns `ExamSession`, `Booking`, `Waitlist`, the capacity audit and the seat reconciler (which absorbs `capacity-sync`). Its database is the only place these rows are written.
- `AssessmentDelivery` stays the system of record for `Exam`, `TestCenter`, `Room` and `Candidate`. scheduling-service keeps **read-only replicas** of them (section 5). There are **no cross-database foreign keys** in either direction. `ExamId`, `TestCenterId`, `RoomId` and `CandidateId` keep their legacy identity values, so ids line up during dual-run (Phase 3).
- `Result` and `ResultUpload` stay with results-service. The only scheduling column results and eligibility need is `ExamSession.StartsAt` (inventory P-E01, P-R01, BA-18, RS-05). It travels in the `GET /bookings/{id}` response, and results-service stores it on `Result` at ingest.
- Nobody else may read the scheduling tables after cutover. Every inventory row has an API, event, ref-local or results-local replacement.

## 2. Endpoints

| Operation | Endpoint | Replaces (inventory id) |
|---|---|---|
| Search sessions | `GET /sessions` | P-B01, P-B11, P-A04, BA-11, X-01 |
| Session counts per centre | `GET /sessions/summary?groupBy=testCenter` | P-A01 |
| Session detail (+ bookedSeats, waitlistCount) | `GET /sessions/{id}` | P-B02, P-B08, BA-12, BA-13, BA-16 |
| Change capacity | `POST /sessions/{id}/capacity` | P-A05 (`usp_ChangeCapacity` + trigger 033) |
| Capacity audit | `GET /sessions/capacity-audit` | P-A06, X-09 (`usp_GetCapacityAudit`) |
| Waitlist read | `GET /sessions/{id}/waitlist` | P-A04 (count), P-A08 (positions) |
| Roster | `GET /sessions/{id}/roster[?format=csv]` | P-A08 (admin roster export) |
| Booking queries | `GET /bookings` | P-B06, BA-02, RS-03 |
| Book | `POST /bookings` | P-B09 (`usp_BookSeat`), BA-04..BA-08, BA-14, BA-15 |
| Booking detail | `GET /bookings/{id}` | P-B07, P-B12, BA-01, BA-03, RS-01 |
| Cancel | `POST /bookings/{id}/cancel` | P-B10, BA-09 (`usp_CancelBooking`) |
| Reschedule | `POST /bookings/{id}/reschedule` | P-B13 (`usp_RescheduleBooking`), BA-20 |
| Mark attended | `POST /bookings/{id}/attendance` | RS-02 |
| Retake eligibility | `GET /eligibility/retake` | P-E01, BA-17, BA-18 (section 7) |
| Reference upsert/read | `PUT/GET /reference/{entity}/{id}` | section 5 |
| Replication status | `GET /reference/status` | section 5 |
| Reconciler runs | `GET/POST /reconciler/runs` | CS-01..CS-03, P-A07 |

## 3. Command semantics (parity targets)

- **Book.** Locks the session row for every channel (the `UPDLOCK, HOLDLOCK` of `020:34`). Checks run in the proc's order: OPEN (no `StartsAt` check, HB-31), then no active BOOKED/WAITLISTED booking by the candidate *on this session* (HB-21), then accommodation seat for ET25/ET50. The booking is BOOKED if seats are available, otherwise WAITLISTED with `MAX(Position)+1`. Retake eligibility is **not** checked (HB-08). The accommodation code is taken literally: the portal resolves a blank code to the candidate default before calling, and the partner channel does not (HB-09).
- **Cancel.** Locks the booking row. BOOKED releases one seat. WAITLISTED deletes its Waitlist row. Nobody is promoted, and position gaps remain (HB-06). Bookings on past or delivered sessions can be cancelled too (HB-22).
- **Reschedule.** Always creates a new booking id. `channel=PORTAL` follows `usp_RescheduleBooking`: the same-exam guard (state 6) runs before the transaction, and the new booking is PORTAL with no `externalRef` (HB-10). `channel=PARTNER` follows booking-api: a SAME_SESSION guard, cancel, then the partner book path. The booking keeps PARTNER and `externalRef` (HB-11).
- **Change capacity.** Applies the delta to `SeatsAvailable` with a floor of 0. A cut demotes the newest BOOKED rows (`BookedAt DESC, BookingId DESC`) and recounts seats as `Capacity - BOOKED` (HB-05). The only rejections are `< 0` and `< AccommodationCapacity`, with no upper bound (HB-23).
- **Mark attended.** BOOKED becomes ATTENDED. A booking already ATTENDED is a no-op returning 200, which makes the call idempotent. CANCELLED or WAITLISTED returns 409 `BOOKING_NOT_ACTIVE`. Future sessions and non-OPEN sessions are accepted (HB-18). Seats and waitlist are untouched. This matches `ResultIngestService.java:62-65,96-97`, pinned by `ResultsAttendContractTest`. results-service keeps its own validation, Result insert, duplicate/conflict handling, and per-row score-file commits (HB-19). For each CREATED or DUPLICATE ingest it calls attendance. A conflict (409 `DUPLICATE_RESULT`) never reaches scheduling-service.

## 4. Error mapping

Every legacy proc error is message **50000**, severity 16. The documented numbers 50001-50003 never reach callers (HB-14), and only the state tells the errors apart. `PortalReadContractTest.procErrorSurface` pins the state and exact text of each one. The service returns `{error, message, legacyState}` and keeps the legacy message text, so the portal UI can show the same text.

| Legacy source | State | Message | HTTP | `error` |
|---|---|---|---|---|
| `usp_BookSeat` session not OPEN or missing | 1 | `Session %d is not open for booking.` | 409 (404 if the id does not exist) | `SESSION_NOT_OPEN` / `SESSION_NOT_FOUND` |
| `usp_BookSeat` active booking on session | 2 | `Candidate %d already has an active booking on session %d.` | 409 | `DUPLICATE_BOOKING` |
| `usp_BookSeat` no accommodation seat | 3 | `No accommodation seat available on session %d.` | 409 | `NO_ACCOMMODATION_SEAT` |
| `usp_CancelBooking` not BOOKED/WAITLISTED or missing | 4 | `Booking %d cannot be cancelled.` | 409 (404 if missing) | `NOT_CANCELLABLE` / `BOOKING_NOT_FOUND` |
| `usp_ChangeCapacity` negative or below accommodation capacity, or missing session | 5 | `Invalid capacity %d for session %d.` | 422 (404 if missing) | `INVALID_CAPACITY` / `SESSION_NOT_FOUND` |
| `usp_RescheduleBooking` different exam / missing booking or session | 6 | `Booking %d cannot be moved to session %d.` | 409 (404 if missing) | `DIFFERENT_EXAM` |
| Reschedule's nested cancel/book failures | 1-4 | as above | 409 | as above; original booking untouched (HB-15 rollback) |
| booking-api validation (`BA/service/BookingService.java:60-70`) | - | sessionId/candidateNumber required, unknown accommodationCode, externalRef > 40 | 400 | `VALIDATION` |
| booking-api lookups | - | candidate / session / booking not found | 404 | `CANDIDATE_NOT_FOUND`, `SESSION_NOT_FOUND`, `BOOKING_NOT_FOUND` |
| booking-api `SAME_SESSION` | - | `Booking %d is already on session %d` | 409 | `SAME_SESSION` |
| results-service attend on CANCELLED/WAITLISTED | - | booking not active | 409 | `BOOKING_NOT_ACTIVE` |
| Idempotency key reused with a different body | - | - | 422 | `IDEMPOTENCY_KEY_REUSED` |
| `If-Match` mismatch on capacity | - | - | 412 | `VERSION_MISMATCH` |

The legacy portal raises `PortalException(message)` for 50000/16 (`AssessmentDelivery.Services/Common/SqlErrorHelper.cs:25`). Phase 2 maps any 4xx body's `message` to the same exception. booking-api today maps `NOT_CANCELLABLE` from the proc's message (`BookingService.java:138-143`) and keeps doing so from `error`.

## 5. Reference-data replication (Exam, TestCenter, Room, Candidate)

- **Copied columns** are only those the service needs (`ReferenceUpsert` in the YAML): exam code, title, duration, `PassingScore`, `RetakeWaitDays`, `IsPublished`; centre code, name, city, region, time zone, `IsActive`; room name, seat counts, `IsActive`; candidate number, names, email (roster), `DefaultAccommodationCode`.
- **Feed.** SQL Server Change Tracking on the four tables in `AssessmentDelivery`. A small relay (part of scheduling-service, running with read-only credentials on the source DB) polls `CHANGETABLE(CHANGES ...)` every 30 s and applies the changes through the same code path as `PUT /reference/{entity}/{id}`, using the change-tracking version as `sourceVersion`. `PUT` is idempotent and last-writer-wins by `sourceVersion`, so replays and out-of-order delivery are harmless. The portal's only reference write (centre name/active, P-A03) and seed scripts need no code change, because change tracking sees them. A full resync (`version = 0`) runs on first start and whenever change tracking reports the stored version is older than `CHANGE_TRACKING_MIN_VALID_VERSION`.
- **No cross-DB FKs.** Replica tables have primary keys only. `ExamSession.ExamId/TestCenterId/RoomId` and `Booking.CandidateId` are plain integer columns, validated against the replica when a command runs.
- **Staleness.** `GET /reference/status` reports lag per entity and `stale=true` beyond 5 minutes. Commands behave as follows:
  - Unknown candidate or session id at book time: 404 (`CANDIDATE_NOT_FOUND`). The relay is then asked for an immediate catch-up, and the client may retry.
  - Replica values are never used to reject a command that legacy would accept. Legacy `usp_BookSeat` does not look at `TestCenter.IsActive`, `Exam.IsPublished` or `Room` (HB-24), so stale values in those columns cannot change a booking outcome.
  - Display fields (exam title, centre name, room) may lag by up to one poll interval. That is acceptable for reads.
  - Deletes are tombstoned (`deleted=true`) and kept, because legacy rows are never deleted while sessions reference them.
- **Drift check.** Phase 3 dual-run compares replica row hashes with the source per entity, alongside the Booking/ExamSession/Waitlist/AuditLog diff.

## 6. Channel, idempotency and concurrency

- **`channel`** is required on book and reschedule and is stored on `Booking.Channel` (`PORTAL`, `PARTNER`, `IMPORT`). It selects these legacy differences:
  - **Seat accounting.** Today a PARTNER BOOKED insert decrements `SeatsAvailable` twice (DAO plus trigger 032) and a partner cancel releases once (HB-01, HB-02, pinned by `BookingApiContractTest.partnerBookAndCancelSeatMath`). The service implements this behind `scheduling.legacy-seat-accounting` (env `SCHEDULING_LEGACY_SEAT_ACCOUNTING`). **Decision (parent, Phase 1): the runtime default is `false`.** The service is the single writer, so in Phase 2 a partner booking takes one seat. The characterization target sets it to `true` for the pinned HB-01/HB-02 scenarios, and so should any dual-run that diffs against legacy partner traffic.
  - **Reschedule semantics** (section 3).
  - **Audit actor**, which comes from `X-Actor`.
- **Idempotency.**
  - Every POST accepts `Idempotency-Key`. The service stores (key, client, request hash, response) for 7 days. A replay returns the original status and body with `Idempotent-Replayed: true`. The same key with a different body returns 422.
  - `externalRef` is stored and returned but is **not** unique and not an idempotency key (HB-12). Partners that resend without a key still create duplicates, as today.
  - Attendance is naturally idempotent (ATTENDED to ATTENDED returns 200, `transitioned=false`).
- **Concurrency.**
  - Book, cancel, capacity change and reconcile each run in one transaction. They take a pessimistic row lock (`SELECT ... FOR UPDATE` or the platform equivalent) on the session row (book, capacity, reconcile) or the booking row (cancel). That serialises the last seat and keeps waitlist positions unique for **all** channels.
  - The legacy partner path has no lock, so racing partner bookings can get duplicate waitlist positions (HB-29). This is a race artefact, not deterministic behaviour, and the service does not reproduce it. Phase 3 parity compares non-racing sequences and checks seat drift continuously under load.
  - Capacity change accepts an optional `If-Match` against the session `ETag`.
  - The reconciler absorbs capacity-sync. It keeps the compare-and-set update (`... AND SeatsAvailable = ?`), keeps the HB-03 formula `Capacity - AccommodationCapacity - standard bookings` for parity, and records every run, including failures, with a run row.

## 7. Retake eligibility placement: options and recommendation

Today two engines read `Result` and `Exam`, and both join `ExamSession` for `StartsAt`. They also disagree:
- The portal adds calendar days to `Max(SessionDate)` (`AssessmentDelivery.Business/Rules/RetakeRules.cs`).
- booking-api adds Mon-Fri working days to the first row ordered `StartsAt DESC` (`RetakeEligibilityService.java`, HB-07).

`RetakeEligibilityContractTest` pins both engines across 10 cases. The partner side runs over HTTP. The portal side runs through the portal's EF query plus the real `RetakeRules.cs` compiled with Mono. After cutover, `ExamSession.StartsAt` lives in scheduling-service.

| Option | Description | Pros | Cons |
|---|---|---|---|
| A. Remote reads | Each caller keeps its engine and reads `Result`/`Exam` locally. `StartsAt` comes from either per-attempt `GET /sessions/{id}` or a `Result.SessionStartsAt` column that results-service fills at ingest | Smallest code change; HB-07 is preserved automatically | Two engines remain, each still needing scheduling data; per-attempt calls are N+1 or need a results schema change plus backfill; booking-api still reads results-service's table across a boundary |
| B. scheduling-service API | `GET /eligibility/retake?policy=...`. Attempts are the results recorded on attendance (`POST /bookings/{id}/attendance` carries `{resultId, score, passed}`) plus a one-off backfill from `Result`. `StartsAt` is the service's own. `RetakeWaitDays` comes from the Exam replica | No cross-DB reads at all; one place for both policies with HB-07 kept explicit via `policy`; both callers already call scheduling-service when booking; `passed` is frozen at ingest exactly like legacy (HB-18) | scheduling-service holds a copy of result outcomes (results-service stays the system of record); needs a backfill and the attendance payload |
| C. results-service API | results-service exposes eligibility from `Result` + local `Exam`, with `StartsAt` denormalised at ingest | Owner of `Result` owns the rule | Needs the same denormalisation as A, and the booking flows gain a second synchronous dependency (portal and booking-api -> results-service) |

**Recommendation: option B.** scheduling-service hosts `GET /eligibility/retake`, and callers pass `policy=PORTAL_CALENDAR_DAYS` (portal) or `PARTNER_WORKING_DAYS` (booking-api), keeping the HB-07 divergence byte-for-byte. Enforcement stays in the callers, which call eligibility before `POST /bookings` as today, so `POST /bookings` still does not check retakes (HB-08). Session 5 adds the `result` block to the attendance call, backfills attempts from `Result`, and switches both callers. Acceptance is `RetakeEligibilityContractTest` re-pointed at the endpoint: the same 10 cases, both policies, identical outputs. Whether to unify the two policies is a product decision, deliberately outside this migration.

## 8. Open decisions (need an owner before Phase 1 closes)

1. ~~Keep `legacy-seat-accounting` on through cutover?~~ Decided: default off. It is on only for characterization and legacy-parity dual-runs (section 6).
2. Serialising partner bookings removes the HB-29 duplicate waitlist positions. Accept this as the one intentional non-reproduction, since it cannot be reproduced deterministically?
3. Reference feed via Change Tracking needs `ALTER DATABASE ... SET CHANGE_TRACKING = ON` on `AssessmentDelivery` (DBA change).

## 9. Phase 1 implementation deviations (services/scheduling-service, PR #89)

| Area | Deviation | Why |
|---|---|---|
| `POST /sessions` | Added: it creates an ExamSession, which is admin and fixture creation. | Session creation was the portal admin's direct INSERT, so the contract had no endpoint for it. The characterization target and dual-run need one. |
| `POST /reference/sync[?full=true]` | Added: it forces a relay catch-up or a full resync. | It makes fixture rows written to AssessmentDelivery visible immediately instead of after the 30 s poll. |
| `POST /reconciler/runs` | Optional `sessionIds` (comma list) and `trigger` query parameters. | The suite's `reconcileSeats(ids)` limits a run to its own sessions. Omitting them gives capacity-sync's every-OPEN-session run. A run already in progress returns 409 `RECONCILER_RUNNING`. |
| `GET /audit?entityName=&entityId=` | Added: read-only AuditLog rows for an entity. | Parity checks and the Phase 3 AuditLog diff. |
| `POST /admin/import[?replace=true]` | Added: re-runs the initial import from AssessmentDelivery. Disabled unless `scheduling.import.api-enabled=true`. | Cutover rehearsal. It is gated because `replace=true` wipes Scheduling data. |
| `/admin/storage/*` | Added: a direct-write compatibility API (insert/patch/delete Booking, patch ExamSession, adjust seats). It applies the former trigger behaviour explicitly (HB-04/HB-05). Disabled unless `scheduling.storage-api.enabled=true`. | The suite's RAW_STORAGE scenarios, and dual-run replays of legacy direct writers. Not for clients. |
| Unknown candidate on `POST /bookings` | 404 `CANDIDATE_NOT_FOUND` with `legacyState: 0` and the legacy FK message (`FK_Booking_Candidate`). | usp_BookSeat re-raised the FK violation as Msg 50000 with state 0 (BS-22). |
| Roster CSV | Cells that start with `= + - @`, tab or CR get a leading `'`. | Neutralises spreadsheet formula (CSV) injection. Every other cell is unchanged. |
| Reconciler formula (HB-03 removal) | With `scheduling.legacy-seat-accounting=false` (runtime default) the reconciler sets every session's `SeatsAvailable` to the seat-drift.sql definition, `Capacity - (BOOKED + ATTENDED)`, so a run always ends with zero drift. capacity-sync's `max(0, Capacity - AccommodationCapacity - StandardBooked)` on OPEN sessions applies only when the flag is `true` (characterization, legacy-parity dual-run). | HB-03 makes the nightly sync create drift. Once the service is the single writer that is a defect, not parity. |
| Reconciler history | The initial import seeds `ReconcilerRun` rows from legacy `CAPACITY_SYNC` AuditLog rows (`trigger=… status=… checked=… updated=…`; `COMPLETED` becomes `OK`; runs already present are skipped, so re-imports are idempotent). Runs include `actor`. | So that `GET /reconciler/runs` shows pre-cutover capacity-sync history. The portal DTO reads `actor`. |
| Lock order and deadlock retry | Every command locks `UPDLOCK, HOLDLOCK` on ExamSession rows first (ascending SessionId when a reschedule touches two), then the Booking row. A transaction that still loses a 1205 is re-run up to 5 times. | The procs' booking-then-session order deadlocked crossing reschedules (about half of them, HB-15/MR-13) and, over JDBC, cancel/attend-vs-book. In Phase 3 load these surfaced as 5xx where the target is "no worse than legacy". CX-08 accepts zero victims, and its expectations are unchanged. |
| `GET /sessions/capacity-audit` | `testCenterId` and `sessionId` are both optional. `from` is inclusive and `to` exclusive, both `date-time` as in usp_GetCapacityAudit (a bare `yyyy-MM-dd` means midnight). | The portal page always passes `testCenterId`. Other callers can query by session. |

### Security posture (Phase 1)

The contract does not define authentication, so the service matches the legacy trust model. Today every caller (portal, booking-api, results-service, capacity-sync) connects to `AssessmentDelivery` as the shared `ad_app` login over the internal network, with no per-caller authorization. scheduling-service has the same posture: **no authentication on any endpoint, reachable only on the internal network** (bind it or firewall it to the estate subnet). `X-Client-Id` scopes idempotency and `X-Actor` names the audit actor. Neither is a credential. The destructive and compatibility endpoints (`/admin/import`, `/admin/storage/*`) are off by default. Adding service-to-service auth (mTLS or OAuth2 client credentials) is a follow-up for after cutover. It needs every Phase 2 client to change at once.

