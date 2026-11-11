# ADR-001: Retake-eligibility placement and results-service attendance

Status: accepted (Phase 2, Session 5). Context: `scheduling-service-contract.md` sections 3 and 7,
`scheduling-boundary.md` rows RS-01..RS-06, BA retake rows, P-E rows; `docs/as-is/hidden-behaviours.md` HB-07, HB-08,
HB-18, HB-19.

## 1. Retake eligibility: service API (S2 option B)

**Decision.** scheduling-service hosts `GET /api/v1/eligibility/retake?candidateNumber&examCode&date&policy`. Each
caller names its legacy policy:

| Caller | Policy | Rule kept byte-for-byte (HB-07) |
|---|---|---|
| portal `EligibilityService.Check` (`AssessmentDelivery.Services/Eligibility`) | `PORTAL_CALENDAR_DAYS` | calendar days from the latest attempt date; reasons "No previous attempts", "Candidate has already passed this exam", "Retake wait period applies until dd MMM yyyy", "Retake wait period has elapsed" |
| booking-api `RetakeEligibilityService.check` | `PARTNER_WORKING_DAYS` | Mon-Fri working days from the first attempt ordered `StartsAt DESC, ResultId DESC`; reasons `FIRST_ATTEMPT`, `ALREADY_PASSED`, `RETAKE_WAIT_NOT_ELAPSED`, `RETAKE_ALLOWED` |

Attempts are the attendance results scheduling-service records from `POST /bookings/{id}/attendance` (`result`
block, `passed` frozen as sent, HB-18), backfilled once from `dbo.Result` at cutover. `RetakeWaitDays` comes from
replicated `Exam` reference data.

**Why not remote reads (option A: callers keep the rule and read attempts + session dates over HTTP).** Both rules
need `Result` joined to the session start time, which after Phase 1 lives in scheduling-service. Option A keeps two
copies of the rule plus a new attempts endpoint and still makes every caller do two lookups; option B removes the
`Result`/`ExamSession` join from both callers (ResultDao and the EF `Results`/`Session` read) and keeps a single
place where the HB-07 divergence is pinned, by `RetakeEligibilityContractTest` (10 cases in `retake-cases.psv`).

**Not changed.** The divergence itself (HB-07). Enforcement stays in the callers (HB-08): `POST /bookings` does not
check eligibility. booking-api still runs its check inside its booking path through the same
`RetakeEligibilityService.check` signature, so `BookingService` (Session 4's area) is untouched.

**Outage.** booking-api returns 503 `SCHEDULING_UNAVAILABLE` from `GET /candidates/.../eligibility`; the portal shows
its normal error page. Neither falls back to a local rule (no stale second copy).

## 2. results-service attendance

**Decision.** `ResultIngestService.ingest` reads the booking with `GET /bookings/{id}` (status, candidate, session,
exam, `sessionStartsAt`), inserts the `Result` (+ `INGEST` audit) in its own transaction and commits, and only then
calls `POST /bookings/{id}/attendance` with `{result: {resultId, score, passed, scoredAt}}`, so a rollback can never
leave an ATTENDED booking without its Result. Score files resolve the booking with
`GET /bookings?candidateNumber&sessionId&status=BOOKED,ATTENDED&sort=bookingId,desc&limit=1` (legacy
`findBookingId`). Candidate history uses the new results-local `Result.SessionStartsAt` instead of joining
`ExamSession` (RS-05). `PassingScore` stays a local read of reference `Exam`.

Unchanged legacy semantics: BOOKED/ATTENDED qualify; CANCELLED/WAITLISTED are 409 `BOOKING_NOT_ACTIVE`; future and
cancelled sessions are accepted; `passed` frozen at ingest (HB-18); DUPLICATE and CONFLICT do not call attendance
when the booking is already ATTENDED; score-file rows commit one by one (HB-19).

**Attendance call fails after the Result committed.**
- 404/409 (the booking changed after our read): the Result and its `INGEST` audit row are deleted in a compensating
  transaction and the error is returned, so nothing is recorded (same outcome as legacy).
- 503: the Result stays committed and the booking stays BOOKED. Resending the same result (REST or score file) finds
  the stored Result, reports DUPLICATE, and completes the attendance call with the stored values (idempotent).

**scheduling-service unavailable during ingest.** Connect/read failures and 5xx map to 503
`SCHEDULING_UNAVAILABLE`.

- REST `POST /api/v1/results`: 503. If the booking read failed nothing is recorded; if only the attendance call failed
  the Result is kept (above). Either way the booking stays BOOKED and a retry converges.
- CSV upload: each affected row is reported in `errors[]` (its Result is kept only if the attendance call alone failed); rows that succeeded before or after stay
  committed (HB-19 baseline). Because some rows could not be tried, the file is **not** registered in
  `dbo.ResultUpload`, so re-sending the same bytes imports the failed rows and reports the stored ones as duplicates.
  (A file without outage errors is registered as before, and its replay still imports nothing.)
- Because attendance runs after commit, a commit failure records nothing in either system.
