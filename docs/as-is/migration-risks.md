# Migration risks (as-is)

Consolidated from the four deep dives ([portal](deep-dives/portal.md), [java-services](deep-dives/java-services.md), [sql](deep-dives/sql.md), [runtime-repros](deep-dives/runtime-repros.md)); each deep dive keeps its own detailed risk table. Impact/likelihood: H/M/L. "Characterization test" names the repro that should become a golden test before anything is changed. HB-n links to [hidden-behaviours.md](hidden-behaviours.md).

| ID | Risk | Impact | Likelihood | Evidence | Mitigation / characterization test |
|---|---|---|---|---|---|
| MR-01 | `ExamSession.SeatsAvailable` has five writers with inconsistent arithmetic; any channel moved alone changes availability | H | H | HB-01..HB-05; `db/triggers/032_trg_Booking_SeatSync.sql:18-24`, `BA/service/BookingService.java:113-116`, `CS/CapacitySyncService.java:59-67` | Derive availability from bookings in the target; reconcile counters before cutover; golden tests `sql/02`, `runtime-repros/01`, `runtime-repros/02` |
| MR-02 | No agreed seat formula (seed/check vs trigger vs capacity-sync; 112 sessions disagree after one sync) | H | H | HB-03; `db/checks/seat-drift.sql:18-26`, `033:66-68`, `CS/CapacitySyncService.java:29-34` | Business sign-off on one formula; snapshot both before migration; `sql/11`, `runtime-repros/04` |
| MR-03 | Retake policy differs by channel (30 calendar vs 30 working days, 11-day gap observed) | H | H | HB-07; `AssessmentDelivery.Business/Rules/RetakeRules.cs:24-25`, `BA/util/DateUtils.java:67-79` | Policy owner decision; parameterised date test `runtime-repros/05`, `java-services/07` |
| MR-04 | Capacity reductions silently demote confirmed bookings, and raising capacity does not restore them | H | M | HB-05; `db/triggers/033_trg_ExamSession_Update.sql:24-71` | Decide policy and notifications; extract demotion history from `Waitlist.AddedAt`; `sql/04`, `runtime-repros/03` |
| MR-05 | Concurrency differs by channel: partner path unlocked, duplicate waitlist positions reproduced | H | M | HB-29; `BA/dao/SessionDao.java:77`, `BA/dao/BookingDao.java:84-85` vs `020:34` | Concurrency test against the target; dedupe positions in migrated data; `runtime-repros/02` part b |
| MR-06 | No session close-out and zone-less local times on a UTC host | H | H | HB-25, HB-31; `CS/NightlySyncJob.java:13`, `db/schema/010_tables.sql:26,57` | Locate the external close-out process; map `TestCenter.TimeZone`; `runtime-repros/07` |
| MR-07 | Database enforces almost no business invariants and every component shares a high-privilege login | H | H | HB-08, HB-28; `db/schema/010_tables.sql:52-117`, `db/schema/000_create_database.sql:13-25` | Profile production data for violations (negative seats, invalid codes, multiple active bookings) before adding constraints; per-component logins; `sql/07`, `sql/12`, `sql/13` |
| MR-08 | Business rules duplicated across C#, Java and T-SQL with different semantics (accommodation default, duplicate scope, session search) | M | H | HB-09, HB-13, HB-21 | Single rules service; contract tests per rule across both channels; `java-services/06`, `java-services/08` |
| MR-09 | Reschedule rewrites provenance: new booking ids, `Channel` flipped, `ExternalRef` dropped/copied, no-op reschedule allowed | M | H | HB-10, HB-11; `023:23-41`, `BA/service/BookingService.java:147-176` | Model reschedule as an update with history; preserve partner references; `sql/06`, `runtime-repros/08` part a |
| MR-10 | Partner API has no idempotency key; retries duplicate bookings across sessions | M | M | HB-12; `db/schema/010_tables.sql:86` | Unique `(Channel, ExternalRef)` in the target; dedupe migrated rows; `java-services/05` |
| MR-11 | Results ingest accepts future sessions, freezes `Passed`, commits score files row by row and returns 200 on partial failure | M | M | HB-18, HB-19; `RS/service/ResultIngestService.java:42-76`, `RS/service/ScoreFileImporter.java:50-63` | Find `Result.ScoredAt < StartsAt` in data; define file-level atomicity; `runtime-repros/06`, `java-services/10`, `java-services/11` |
| MR-12 | Waitlist has no promotion and positions have gaps/ties; no code consumes it | M | H | HB-06, HB-29; `021:2-4,36-41` | Decide promotion semantics; renumber on migration; `sql/05` |
| MR-13 | Error contracts: documented codes never raised, system errors masked as business errors, three different error shapes | M | H | HB-14, HB-15; `020:85-87`, `AssessmentDelivery.Services/Common/SqlErrorHelper.cs:25`, `BA/web/ApiExceptionHandler.java:29-36` | Contract tests on error shapes before replacing any API; `sql/01`, `sql/06b`, `runtime-repros/07` |
| MR-14 | Triggers block ORM `OUTPUT` clauses and overwrite `LastModified`; EF Core/Hibernate defaults will fail | M | H | HB-16; `033:16-22` | Configure the ORM for trigger tables (no `OUTPUT`), or move trigger logic into the application first; `sql/08` |
| MR-15 | Audit trail is partial and cannot attribute changes to people or channels | M | H | HB-17, HB-24; `db/schema/010_tables.sql:127` | Define audit requirements; per-user identity; `sql/09` |
| MR-16 | No authentication on the portal or APIs; cancel by id with no ownership check | H | M | HB-22; `AssessmentDelivery.Web/Controllers/BookingController.cs:91-104` | Add identity before exposing a new surface; do not port the unauthenticated endpoints as-is |
| MR-17 | Capacity-sync state is in memory and its nightly run silently masks partner drift | M | M | HB-20; `CS/CapacitySyncService.java:42-44,65-67` | Retire once availability is derived; until then persist run history; `java-services/09`, `java-services/12` |
| MR-18 | `db/apply.*` drops the database on every default start; seed is frozen to 2026-11-10/11 | M | M | HB-30; `db/apply.ps1:44`, `scripts/run-legacy.ps1:247-250` | Never point apply scripts at shared environments; re-anchor seed dates for long-lived test fixtures; `sql/14` |
| MR-19 | Windows-only toolchain (IIS Express, .NET Framework 4.8, SQL Express install, Maven Central 429s) | M | M | [smoke-test.md](smoke-test.md); `scripts/run-legacy.ps1:131-166,263-266,382-406` | Containerise DB and Java; pin a Maven mirror; keep a Windows runner for the portal until it is replaced |
| MR-20 | Thin automated tests: 8 NUnit tests (rules only) and 2 tests per Java service; no DB or integration tests | H | H | [inventory.md](inventory.md); `AssessmentDelivery.Tests/`, `services/*/src/test` | Promote the repros in `docs/as-is/repros/` to a characterization suite before refactoring |
| MR-21 | Dead or misleading configuration and comments (`Portal.*` settings, proc header error codes, trigger headers, "admin screen" promotion) | L | H | HB-27, HB-14, HB-05, HB-06 | Treat code, not comments/config names, as the specification |

## Open questions for the business

1. Which `SeatsAvailable` definition is authoritative: `Capacity - (BOOKED + ATTENDED)` or capacity-sync's `Capacity - AccommodationCapacity - non-ET bookings`?
2. Retake wait: calendar days or working days? Do partners depend on the API's later date?
3. Is the partner double decrement known (and compensated by the nightly sync) or a defect?
4. Should capacity be reducible below the booked count, and who notifies demoted candidates?
5. Who promotes waitlisted candidates, and in what order?
6. Who closes sessions in production (the "01:00 close-out" referenced at `CS/NightlySyncJob.java:13`)?
7. Should results be rejected for sessions that have not started?
8. Are `Candidate`, `Exam`, `Room` maintained by another system (no writer exists in this estate)?
