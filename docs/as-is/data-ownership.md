# Data ownership (as-is)

Every component reads and writes the single `AssessmentDelivery` database as `ad_app`. No table has a single owner. R/I/U/D = read/insert/update/delete. Line-level evidence for every cell is in the deep dives' data-ownership tables: [portal](deep-dives/portal.md), [java-services](deep-dives/java-services.md), [sql](deep-dives/sql.md).
Paths: `BA/`, `RS/`, `CS/` as in [architecture.md](architecture.md). Schema: `db/schema/010_tables.sql`.

## Matrix

| Table | Portal | booking-api | results-service | capacity-sync | Procs / triggers | Effective owner (de facto) |
|---|---|---|---|---|---|---|
| `TestCenter` | R, U (direct EF: `AssessmentDelivery.Services/Admin/AdminService.cs:50-59`) | R (session search join) | - | - | - | Portal admin |
| `Room` | R | R | - | - | - | Seed only (no writer) |
| `Exam` | R | R (retake wait, pass mark) | R (`PassMark` at ingest) | - | `trg_Exam_Audit` R | Seed only; pass-mark change audited by trigger |
| `ExamSession` | R; U via `usp_ChangeCapacity` | R; U `SeatsAvailable` (`BA/dao/SessionDao.java:79-83`) | R | R, U `SeatsAvailable` (`CS/CapacitySyncService.java:59-67`) | `usp_BookSeat`/`usp_CancelBooking`/`usp_ChangeCapacity` U; `trg_Booking_SeatSync` U; `trg_ExamSession_Update` U `LastModified`, recount | **Contested**: five writers of `SeatsAvailable` |
| `Candidate` | R | R (accommodation profile) | R | - | `trg_Candidate_Audit` R | Seed only (no app writer) |
| `Booking` | R; I/U via procs | R, I direct (`BA/dao/BookingDao.java:71-86`), U via `usp_CancelBooking` | R, U `Status='ATTENDED'` (`RS/service/ResultIngestService.java:75-76`) | R (counts) | `usp_BookSeat` I, `usp_CancelBooking` U, `usp_RescheduleBooking` U+I, `trg_ExamSession_Update` U (demotion) | **Contested**: portal procs, booking-api, results-service, trigger |
| `Waitlist` | R | I (`MAX(Position)+1`, `BA/dao/BookingDao.java:82-86`) | - | - | `usp_BookSeat` I, `usp_CancelBooking` D, `trg_ExamSession_Update` I | Contested; nobody promotes (HB-06) |
| `Result` | R (`/Results`, eligibility) | R (retake) | I (`RS/service/ResultIngestService.java:61-70`) | - | - | results-service |
| `AuditLog` | I via procs (`'portal'`, `'portal-admin'`, login) | I (`'booking-api'`) | - | I (`EntityId 0`, `CS/CapacitySyncService.java:96-101`) | all four procs and triggers I | Shared append-only; inconsistent actor (HB-17) |

## Write mechanisms by component

| Component | Mechanism | Transactions |
|---|---|---|
| Portal | EF6 reads; `Database.SqlQuery`/`ExecuteSqlCommand` for procs; one direct EF `SaveChanges` (centre edit) | Proc-internal transactions only; `SET XACT_ABORT ON` + `ROLLBACK` in each proc |
| booking-api | `JdbcTemplate` inline SQL; `EXEC dbo.usp_CancelBooking` via `jdbcTemplate.update` (`BA/dao/BookingDao.java:91`) | Spring `@Transactional` (`BA/service/BookingService.java:57,131,148`) at default READ COMMITTED, no row locks on the seat read (HB-29) |
| results-service | `JdbcTemplate` inline SQL | One transaction per result; one per score-file row (HB-19) |
| capacity-sync | `JdbcTemplate` read + compare-and-set update | Auto-commit per session (HB-20) |
| SQL procs | `UPDLOCK, HOLDLOCK` seat read in `usp_BookSeat` (`db/procs/020_usp_BookSeat.sql:34`); nested proc calls in `usp_RescheduleBooking` | Caller transaction rolled back on error (HB-15) |
| Triggers | AFTER triggers on `Candidate`, `Exam`, `Booking`, `ExamSession` | Run inside the writer's transaction; block `OUTPUT` without `INTO` (HB-16) |

## Consequences for modernization

- `ExamSession.SeatsAvailable` and `Booking.Status` must get a single owner before any channel is moved; today the value is a byproduct of whichever path last wrote it (HB-01 to HB-05).
- `Candidate`, `Exam`, `Room` have no application writer at all; their lifecycle is outside this estate (seed scripts only).
- The audit log cannot be used to attribute changes to people (HB-17).
