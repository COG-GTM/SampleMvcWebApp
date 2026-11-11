# Northwind Assessment Network - Assessment Delivery: system overview

Exam scheduling for the Northwind Assessment Network: exam owners publish exams,
test centres run exam sessions in rooms with a fixed number of seats, and candidates
book, cancel and reschedule seats. Results come back from the delivery system after
the exam and are shown to candidates.

The system grew in layers. The centre-admin and candidate **portal** (ASP.NET MVC,
2011) came first. The **Java services** were added later (2014-2016) for partner
booking channels, score-file ingestion and seat-count housekeeping. Every component
reads and writes one shared SQL Server database, `AssessmentDelivery`.

## Components and ports

| Component | Tech | Port | Folder |
|---|---|---|---|
| SQL Server `AssessmentDelivery` | SQL Server 2016+ / Azure SQL | 1433 | `db/` |
| Portal (centre admin + candidate) | .NET Framework 4.8, ASP.NET MVC 5, EF6, Autofac | 8080 | `AssessmentDelivery.*` projects, `AssessmentDelivery.sln` |
| booking-api (partner channels) | Java 8, Spring Boot 1.5, JdbcTemplate, mssql-jdbc | 8081 | `services/booking-api` |
| results-service (score ingestion) | Java 8, Spring Boot 1.5, JdbcTemplate | 8082 | `services/results-service` |
| capacity-sync (scheduled seat re-sync) | Java 8, Spring Boot 1.5, `@Scheduled` | 8083 | `services/capacity-sync` |

Local development credentials: one SQL login per component (`ad_portal`, `ad_booking_api`, `ad_results`,
`ad_capacity_sync`, `ad_sched_import`, `ad_sched_relay`), created by `db/schema/000_create_database.sql`
(dev only; passwords in the root README). Since MBA-3823 `db/security/040_single_writer.sql` denies booking
and seat writes to all of them except `ad_sched_relay`: scheduling-service is the single writer and its outbox
relay projects every change back into `Booking` / `Waitlist` / `ExamSession` / `AuditLog`. On Azure the connection strings come
from App Service settings, and the committed transforms/profiles hold only placeholders.

## Database (`db/`)

Scripts are numbered and run in order: `schema/` -> `procs/` -> `triggers/` -> `seed/`.
`db/checks/` holds read-only diagnostic queries.
Rebuild locally with `db/apply.sh` (Linux/macOS) or `db/apply.ps1` (Windows); see `db/README.md`
for seed volumes and the check scripts.

| Table | Purpose / key columns |
|---|---|
| TestCenter | CenterCode (unique), Name, City, Region, TimeZone, IsActive |
| Room | TestCenterId, RoomName, SeatCount, AccommodationSeats |
| Exam | ExamCode (unique), Title, OwnerName, DurationMinutes, PassingScore (0-1000), RetakeWaitDays (default 30) |
| ExamSession | ExamId, TestCenterId, RoomId, StartsAt, Capacity, AccommodationCapacity, SeatsAvailable, Status OPEN/CLOSED/DELIVERED/CANCELLED |
| Candidate | CandidateNumber (unique, `NW` + 8 digits), names, Email, DefaultAccommodationCode |
| Booking | SessionId, CandidateId, Status BOOKED/CANCELLED/WAITLISTED/ATTENDED, AccommodationCode, Channel PORTAL/PARTNER/IMPORT, ExternalRef, BookedAt, CancelledAt |
| Waitlist | SessionId, CandidateId, BookingId, Position |
| Result | BookingId (unique), CandidateId, ExamId, SessionId, Score, Passed, ScoredAt, SourceFile |
| ResultUpload | FileName, Sha256 (indexed), RowsRead, Imported, Duplicates, Conflicts, Errors, UploadedAt. One row per score file upload |
| AuditLog | EntityName, EntityId, Action, Detail, Actor, LoggedAt |

**Seats.** `ExamSession.Capacity` counts every seat, including accommodation seats.
`SeatsAvailable` is a denormalised counter; BOOKED and ATTENDED bookings hold a seat.
Accommodation codes: `ET25` and `ET50` (extra time) need an accommodation-capable seat,
limited by `AccommodationCapacity`. `SR` (separate room) and `RB` (rest breaks) have
no seat constraint.

**Retakes.** A candidate who has passed an exam cannot book it again. A candidate who
failed can book again only after `Exam.RetakeWaitDays` (30) days from the date of the
last attempt (the `StartsAt` of the session the failed result belongs to).

### Stored procedures

| Procedure | Signature | Notes |
|---|---|---|
| `usp_BookSeat` | `@SessionId, @CandidateId, @AccommodationCode = NULL, @BookingId OUT, @ResultStatus OUT` | Portal booking. Returns BOOKED, or WAITLISTED when the session is full. Errors: 50001 not open, 50002 duplicate, 50003 no accommodation seat |
| `usp_CancelBooking` | `@BookingId, @Reason = NULL, @Actor = NULL` | Releases the seat. No automatic waitlist promotion |
| `usp_ChangeCapacity` | `@SessionId, @NewCapacity, @Actor = NULL` | Centre admin capacity change; SeatsAvailable shifts by the same delta, floored at 0 |
| `usp_RescheduleBooking` | `@BookingId, @NewSessionId, @NewBookingId OUT, @ResultStatus OUT` | Cancel + book, same exam only |

Errors are raised with `RAISERROR` at severity 16 (msg 50000; the state carries the
code above).

## Java services (`services/`)

Maven multi-module (`services/pom.xml`), Java 8, Spring Boot 1.5.x. Each service
has `application.properties` (local dev) and `application-azure.properties` (Azure
SQL placeholders, password from `AZURE_SQL_PASSWORD`).
Build/run notes and curl examples: `services/README.md`; on Linux `scripts/run-legacy.sh`
starts SQL Server in Docker plus all three services.

**booking-api :8081**

| Method | Path | |
|---|---|---|
| GET | `/api/v1/sessions?examCode=&centerCode=&from=&to=` | sessions with seatsAvailable |
| GET | `/api/v1/sessions/{sessionId}` | |
| POST | `/api/v1/bookings` | body `{sessionId, candidateNumber, accommodationCode?, externalRef?}` -> 201 `{bookingId, status}` |
| GET | `/api/v1/bookings/{bookingId}` | |
| DELETE | `/api/v1/bookings/{bookingId}?reason=` | cancel |
| POST | `/api/v1/bookings/{bookingId}/reschedule` | body `{newSessionId}` |
| GET | `/api/v1/candidates/{candidateNumber}/bookings` | all of a candidate's bookings (any channel) |
| GET | `/api/v1/candidates/{candidateNumber}/eligibility?examCode=&date=yyyy-MM-dd` | `{eligible, reason, earliestRetakeDate}` |

**results-service :8082**

| Method | Path | |
|---|---|---|
| POST | `/api/v1/results` | body `{bookingId, score, scoredAt}` |
| POST | `/api/v1/results/upload` | multipart CSV `candidateNumber,sessionId,score,scoredAt` |
| GET | `/api/v1/results/candidate/{candidateNumber}` | |

Ingesting a new result records attendance in scheduling-service
(`POST /api/v2/bookings/{id}/attendance`; the outbox relay then marks dbo.Booking ATTENDED) and
inserts a `Result` row (Passed = score >= Exam.PassingScore). Duplicates and conflicts never call
scheduling-service.

Ingestion is idempotent. A booking has at most one result (`UQ_Result_Booking`), and a
second result for the same booking is classified against the stored one:

| Case | REST `POST /api/v1/results` | CSV upload row |
|---|---|---|
| no result yet | 201 `{"status":"CREATED","resultId",...}`, attendance recorded in scheduling-service | counted in `imported` |
| same score and same `scoredAt` (compared to the second, SQL `DATETIME` rounds to ~3 ms) | 200 `{"status":"DUPLICATE","resultId",...}`, nothing written | counted in `duplicates`, nothing written |
| anything else | 409 `DUPLICATE_RESULT` with `storedScore`/`submittedScore` and `storedScoredAt`/`submittedScoredAt`; stored row unchanged | counted in `conflicts`, listed in `conflictRows` with its line number |

- A REST post without `scoredAt` stores "now" for a new result, and a replay without
  `scoredAt` is matched on the score only (a client retrying a timed-out post cannot
  resend the server's timestamp).
- Every conflict writes `AuditLog(EntityName='Result', EntityId=<stored ResultId>,
  Action='DUPLICATE_REJECTED')` in its own committed transaction, so it survives the 409.
- Concurrent posts for the same booking: the loser of the insert race hits
  `UQ_Result_Booking`, re-reads the winner's row and gets DUPLICATE or CONFLICT as above,
  never a 500.
- Uploads: the SHA-256 of the file bytes is recorded in `ResultUpload` for every upload.
  The report adds `sha256`, `uploadId`, `previouslyUploaded`/`previousUploadId` (an earlier
  upload with the same hash), `duplicates`, `conflicts` and `conflictRows` next to the
  existing `rowsRead`, `imported`, `skipped` and `errors` (malformed / unresolvable rows
  only). A replayed file reports `imported=0, duplicates=N, conflicts=0`.
- Rows commit one at a time: in a mixed file new rows are inserted, duplicates skipped,
  conflicts and bad rows reported, and a bad row never rolls back the good ones.

**capacity-sync :8083** re-syncs `SeatsAvailable` nightly (02:00). `POST /sync/run`
starts a run by hand and `GET /sync/last` shows the last run.

## Portal (:8080)

| Area | Routes |
|---|---|
| Centre admin | `/Admin/Centers`, `/Admin/Sessions?centerId=`, `/Admin/Sessions/Capacity/{id}` (calls usp_ChangeCapacity) |
| Candidate booking | `/Booking` (find sessions), `/Booking/Book`, `/Booking/MyBookings?candidateNumber=`, `/Booking/Cancel/{id}`, `/Booking/Reschedule/{id}` |
| Retake eligibility | `/Eligibility?candidateNumber=&examCode=&date=` |
| Results | `/Results?candidateNumber=` |

There is no sign-in: a candidate is selected by candidate number (kiosk-era design).
Build, run (`scripts/run-portal.ps1`) and test instructions: `AssessmentDelivery.Web/README.md`.
