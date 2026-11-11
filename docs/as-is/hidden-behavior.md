# Hidden behaviour in the legacy estate

Scope: tag `legacy-baseline` (`e458238`). Every finding was found by reading the legacy code
and, where marked **Confirmed (run)**, reproduced against a running estate on Linux
(SQL Server 2022 container + `booking-api`, `results-service`, `capacity-sync` started with
`scripts/run-legacy.sh`, seed data from `db/seed/`). Session/booking ids in the reproductions
are from the standard seed; re-running on a fresh `db/apply.sh` gives the same ids.

The portal (.NET 4.8) cannot run on Linux. Portal behaviour below is established from its C#
source; wherever the portal's effect is just "call a stored procedure", the procedure was
executed directly with the same arguments the portal passes (that is how `scripts/mixed-traffic.sh`
simulates portal traffic too).

Conventions used in the reproductions:

```bash
SQL() { /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$AD_SA_PASSWORD" -d AssessmentDelivery -W -Q "SET NOCOUNT ON; $1"; }
API=http://localhost:8081   # booking-api
SYNC=http://localhost:8083  # capacity-sync
```

## Summary

| # | Finding | Kind | Severity for the scheduling slice | Status |
|---|---|---|---|---|
| [HB-01](#hb-01) | Every partner booking decrements `SeatsAvailable` twice (Java + trigger) | Stacks disagree / data corruption | Critical | Confirmed (run) |
| [HB-02](#hb-02) | Partner booking has no lock: concurrent requests overbook, drive `SeatsAvailable` negative, create duplicate active bookings for one candidate, duplicate waitlist positions | Data corruption | Critical | Confirmed (run) |
| [HB-03](#hb-03) | Four different formulas for `SeatsAvailable`; nightly capacity-sync rewrites the counter with its own and makes `seat-drift.sql` flag every session with accommodation seats | Stacks disagree | Critical | Confirmed (run) |
| [HB-04](#hb-04) | Retake wait is 30 **calendar** days in C#, 30 **working** days in Java | C# vs Java rule difference | High | Confirmed (run, both stacks - portal on Windows worker) |
| [HB-05](#hb-05) | Lowering capacity silently demotes the newest BOOKED bookings to WAITLISTED (trigger only, any `UPDATE`, no per-booking audit) | Rule only in a trigger | High | Confirmed (run) |
| [HB-06](#hb-06) | Reschedule differs by stack: the proc rewrites partner bookings to `PORTAL`, drops `ExternalRef`, allows same-session moves; Java keeps the channel but double-decrements | C# vs Java rule difference | High | Confirmed (run) |
| [HB-07](#hb-07) | Cancelling never promotes the waitlist; seats and waitlist coexist | Rule only in a proc | Medium | Confirmed (run) |
| [HB-08](#hb-08) | Candidate's default accommodation is applied by the portal only | C# vs Java rule difference | Medium | Confirmed (run) |
| [HB-09](#hb-09) | Retake eligibility is not enforced by the database; only `Result` rows count as attempts | Rule only in apps | Medium | Confirmed (run) |
| [HB-10](#hb-10) | Inactive test centres / rooms are still bookable | Missing rule | Low | Confirmed (run) |
| [HB-11](#hb-11) | Proc error contract is "msg 50000 + state", documented as 50001-50006 | Contract | Medium | Source |
| [HB-12](#hb-12) | Results ingestion: `Passed` frozen at ingest, booking flipped to ATTENDED, score files commit row by row | Rule only in Java | Medium (results slice) | Source |
| [HB-13](#hb-13) | Time: `StartsAt` is centre-local with no offset; Java uses JVM default zone; the "01:00 close-out" job is not in the repo | Implicit contract | Medium | Source |
| [HB-15](#hb-15) | `trg_Booking_SeatSync` is INSERT-only and bulk/direct writes bypass every booking rule (overbooking invisible to `seat-drift.sql`) | Rule only in a trigger | High | Confirmed (run, SQL worker) |
| [HB-16](#hb-16) | Cancellations and capacity changes are accepted on DELIVERED (past) sessions | Missing rule | Medium | Confirmed (run, SQL worker) |
| [HB-17](#hb-17) | Opposite-direction reschedules deadlock (inconsistent lock order); `LastModified` is not a reliable change marker | Concurrency / contract | Medium | Confirmed (run, SQL worker) |
| [HB-18](#hb-18) | Portal-only gaps: cancel has no ownership check, capacity range only validated in the browser, en-GB date parsing drops filters, config keys never read, no Azure SQL retry | Missing rules / config | Medium | Confirmed (run, Windows portal worker) |
| [HB-14](#hb-14) | `GET /api/v1/sessions` returns every status and silently truncates at 500 rows | Contract | Low | Confirmed (run, Java worker) |

The top four (HB-01..HB-04) are the ones most likely to make a migrated scheduling service and
the remaining legacy stack disagree on day one.

---

<a id="hb-01"></a>
## HB-01 Partner bookings decrement `SeatsAvailable` twice

**What happens.** `booking-api` does not use `usp_BookSeat`. It inserts the booking row directly
with `Channel = 'PARTNER'` and then runs its own `SeatsAvailable - 1`. The `AFTER INSERT` trigger
`trg_Booking_SeatSync` was added for imports that bypass the proc and also decrements the counter
for every `BOOKED` row whose channel is not `PORTAL`. Result: each partner booking consumes two
seats from the counter, but cancellation (`usp_CancelBooking`) gives back only one. The portal
path is unaffected because `usp_BookSeat` hard-codes `Channel = 'PORTAL'`, which the trigger skips.

**Evidence**
- `services/booking-api/src/main/java/com/northwind/assessment/booking/dao/BookingDao.java:76` direct `INSERT INTO dbo.Booking (..., Channel, ExternalRef)`; file header comment explains why the proc is bypassed.
- `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:110-116` reads seats, inserts `BOOKED`, then `sessionDao.decrementSeatsAvailable(...)`.
- `services/booking-api/src/main/java/com/northwind/assessment/booking/dao/SessionDao.java:82-83` `UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable - 1`.
- `db/triggers/032_trg_Booking_SeatSync.sql:12,19,23` trigger decrements for `Status = 'BOOKED' AND Channel <> 'PORTAL'`.
- `db/procs/020_usp_BookSeat.sql:57,61` proc inserts with `'PORTAL'` and decrements once.
- `db/procs/021_usp_CancelBooking.sql:38` cancel increments once.
- `db/schema/010_tables.sql:60` the column comment says the counter is "maintained by usp_BookSeat / usp_CancelBooking" - the Java writer is not mentioned.

**Reproduction (Confirmed, run)**
```bash
SQL "SELECT SeatsAvailable FROM ExamSession WHERE SessionId=154"          # 11
curl -s -XPOST $API/api/v1/bookings -H 'Content-Type: application/json' \
     -d '{"sessionId":154,"candidateNumber":"NW00000901","externalRef":"PX-1"}'   # 201 BOOKED (id 3588)
SQL "SELECT SeatsAvailable FROM ExamSession WHERE SessionId=154"          # 9   <- two seats gone
# portal path for comparison
SQL "DECLARE @b INT,@s VARCHAR(12); EXEC dbo.usp_BookSeat 154,902,NULL,@b OUTPUT,@s OUTPUT; SELECT @s"  # BOOKED
SQL "SELECT SeatsAvailable FROM ExamSession WHERE SessionId=154"          # 8   <- one seat
curl -s -XDELETE "$API/api/v1/bookings/3588?reason=test"                  # 200
SQL "SELECT SeatsAvailable, Capacity-(SELECT COUNT(*) FROM Booking WHERE SessionId=154 AND Status IN ('BOOKED','ATTENDED')) FROM ExamSession WHERE SessionId=154"
# 9 vs expected 10 -> permanent drift of 1 seat; db/checks/seat-drift.sql now lists session 154
```
Observed: `11 -> 9` for one partner booking; `seat-drift.sql` reported session 154 with drift 1
after the cancel.

**Migration impact.** The counter in production is wrong in a channel-dependent way, and has been
"fixed" every night by capacity-sync (HB-03), so nobody sees it. A new scheduling service that
owns seat counts must (a) not copy the counter from legacy as truth - recompute from bookings -
and (b) while both stacks write, either remove the Java decrement or the trigger branch, otherwise
dual-running will leak seats. Characterization test: partner booking must change availability by
exactly 1 (pin the *intended* behaviour, and separately pin today's -2 so the fix is deliberate).

---

<a id="hb-02"></a>
## HB-02 Partner booking race: overbooking, negative counter, duplicate waitlist positions

**What happens.** `usp_BookSeat` serialises bookers with `UPDLOCK, HOLDLOCK` on the session row.
`booking-api` reads `SeatsAvailable` with a plain `SELECT` inside a default (READ COMMITTED)
Spring transaction, decides BOOKED vs WAITLISTED in Java, then decrements. The duplicate-booking
and accommodation checks are equally unlocked. Waitlist positions are `MAX(Position)+1` with no
unique constraint.

**Evidence**
- `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:57` `@Transactional` (default isolation).
- `services/booking-api/src/main/java/com/northwind/assessment/booking/dao/SessionDao.java:77-79` unlocked `SELECT SeatsAvailable`.
- `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:85,110` duplicate check and seat read outside any lock.
- `services/booking-api/src/main/java/com/northwind/assessment/booking/dao/BookingDao.java:84-85` `ISNULL(MAX(Position),0)+1` then insert.
- Contrast `db/procs/020_usp_BookSeat.sql:33-34` (`WITH (UPDLOCK, HOLDLOCK)`), `:72` (same MAX+1 but under the lock).
- `db/schema/010_tables.sql` defines no unique key on `Waitlist(SessionId, Position)` and none on active `Booking(SessionId, CandidateId)`.

**Reproduction (Confirmed, run)** - 20 concurrent partner bookings on a session with 1 seat left:
```bash
SQL "SELECT Capacity,SeatsAvailable FROM ExamSession WHERE SessionId=167"   # 8 | 1
seq 910 929 | xargs -P 20 -I{} curl -s -o /dev/null -w '%{http_code}\n' -XPOST $API/api/v1/bookings \
   -H 'Content-Type: application/json' -d '{"sessionId":167,"candidateNumber":"NW00000{}"}' | sort | uniq -c   # 20 x 201
SQL "SELECT Capacity,SeatsAvailable,
            (SELECT COUNT(*) FROM Booking WHERE SessionId=167 AND Status='BOOKED') booked,
            (SELECT COUNT(*) FROM Booking WHERE SessionId=167 AND Status='WAITLISTED') waitlisted
       FROM ExamSession WHERE SessionId=167"
SQL "SELECT Position,COUNT(*) FROM Waitlist WHERE SessionId=167 GROUP BY Position HAVING COUNT(*)>1"
```
Observed: `Capacity 8, SeatsAvailable -3, booked 9, waitlisted 18` (one seat overbooked, counter
negative - double decrement from HB-01 compounds it) and duplicate waitlist positions
`1 x6, 3 x2, 4 x4, 5 x5`.

The same race also defeats the duplicate-booking rule. The run-and-confirm worker sent 10 parallel
requests for **one** candidate on one session and got 10 active bookings (the Java worker got 4 of 5);
`usp_BookSeat` under the same load allows exactly one. Running the same 20-way race through
`usp_BookSeat` for 2 free seats gave exactly 2 BOOKED.

```bash
seq 1 10 | xargs -P 10 -I{} curl -s -o /dev/null -w '%{http_code}\n' -XPOST $API/api/v1/bookings \
   -H 'Content-Type: application/json' -d '{"sessionId":<open session>,"candidateNumber":"NW00000950"}'
SQL "SELECT COUNT(*) FROM Booking b JOIN Candidate c ON c.CandidateId=b.CandidateId
      WHERE c.CandidateNumber='NW00000950' AND b.Status IN ('BOOKED','WAITLISTED')"   # > 1
```

**Migration impact.** Physical overbooking of a room is possible today through the partner
channel. The new service must make "seat decision + insert" atomic (row lock, optimistic version,
or a constraint), and waitlist ordering needs a tie-breaker that is stable under concurrency.
Characterization tests: N concurrent bookings for 1 seat -> exactly 1 BOOKED, N-1 WAITLISTED with
distinct positions; N concurrent bookings by the same candidate -> exactly 1 active booking. The
target schema needs a filtered unique index on active `(SessionId, CandidateId)`.

---

<a id="hb-03"></a>
## HB-03 Four definitions of "seats available"; capacity-sync overwrites the counter nightly

**What happens.** The same denormalised column is computed four different ways:

| Writer / reader | Formula | Evidence |
|---|---|---|
| `usp_BookSeat` / `usp_CancelBooking` / `usp_ChangeCapacity` | incremental: -1 per booked seat (incl. ET), +1 per cancel, +delta on capacity change, floored at 0 | `db/procs/020_usp_BookSeat.sql:61`, `db/procs/021_usp_CancelBooking.sql:38`, `db/procs/022_usp_ChangeCapacity.sql:33-34` |
| `db/checks/seat-drift.sql` (the team's own health check) and both `SeatCounter`s | `Capacity - (BOOKED + ATTENDED)` | `db/checks/seat-drift.sql:3,18,24`; `AssessmentDelivery.Business/Rules/SeatCounter.cs:20-22`; `services/booking-api/.../service/SeatCounter.java` |
| `trg_ExamSession_Update` after a capacity decrease | `Capacity - BOOKED` (ATTENDED ignored) | `db/triggers/033_trg_ExamSession_Update.sql:67-68` |
| `capacity-sync` (nightly 02:00 + `POST /sync/run`), OPEN sessions only | `max(0, Capacity - AccommodationCapacity - (BOOKED+ATTENDED without ET25/ET50))` | `services/capacity-sync/src/main/java/com/northwind/assessment/capacity/CapacitySyncService.java:29-34,59-67`; `NightlySyncJob.java:13-14` |

So after a nightly sync, every OPEN session with `AccommodationCapacity > 0` shows
`AccommodationCapacity` fewer general seats than the procedures believe; the next ET booking via
the proc decrements the counter again although capacity-sync never counted it; and `seat-drift.sql`
reports all of them as drifted. Conversely, capacity-sync silently repairs the HB-01 leak on
sessions without accommodation seats, which is why nobody has noticed it.

**Reproduction (Confirmed, run)**
```bash
sqlcmd ... -i db/checks/seat-drift.sql          # 1 row (session 154 from HB-01)
curl -s -XPOST $SYNC/sync/run                    # {"status":"COMPLETED","sessionsChecked":285,"sessionsUpdated":112}
sqlcmd ... -i db/checks/seat-drift.sql          # 112 rows
SQL "SELECT COUNT(*) drifted, SUM(CASE WHEN AccommodationCapacity>0 THEN 1 ELSE 0 END) with_acc
       FROM ExamSession s
      WHERE SeatsAvailable <> Capacity-(SELECT COUNT(*) FROM Booking b WHERE b.SessionId=s.SessionId AND b.Status IN ('BOOKED','ATTENDED'))"
# 112 | 112  -> every drifted session is one with accommodation seats
SQL "SELECT Capacity,AccommodationCapacity,SeatsAvailable FROM ExamSession WHERE SessionId=154"  # 20 | 2 | 8 (= 20-2-10)
```

**Migration impact.** There is no single legacy definition to port. The business must choose one
(the system overview and `seat-drift.sql` say `Capacity - (BOOKED+ATTENDED)` with ET limited
separately by `AccommodationCapacity`; capacity-sync implements a "reserved accommodation pool").
Whichever is chosen, capacity-sync must be switched off or changed when the scheduling service
takes ownership, or it will keep overwriting the counter in the shared DB. Also note the result of
a booking (BOOKED vs WAITLISTED) depends on the counter, so the formula is user-visible: the same
request can be waitlisted at 03:00 and booked at 01:00.

---

<a id="hb-04"></a>
## HB-04 Retake wait: calendar days (portal) vs working days (Java)

**What happens.** Both stacks say "30 day wait" (`Exam.RetakeWaitDays = 30`). The portal adds
calendar days; `booking-api` adds working days (Mon-Fri). A candidate who failed can therefore book
a retake in the portal ~12 days before the partner API will let them. Both count only `Result` rows
(see HB-09) and both use the last attempt's `StartsAt` date.

**Evidence**
- `AssessmentDelivery.Business/Rules/RetakeRules.cs:24-25` `lastAttempt.AddDays(retakeWaitDays)`; `:27` `requestedDate.Date < earliest` blocks.
- `AssessmentDelivery.Services/Eligibility/EligibilityService.cs:32-34` attempts = `Results` for candidate+exam.
- `services/booking-api/src/main/java/com/northwind/assessment/booking/service/RetakeEligibilityService.java:50-51` comment "30 day wait", code `DateUtils.addWorkingDays(...)`.
- `services/booking-api/src/main/java/com/northwind/assessment/booking/util/DateUtils.java:67-74` skips `SATURDAY`/`SUNDAY`.
- `docs/SYSTEM_OVERVIEW.md` ("Retakes") documents 30 days, not working days.

**Reproduction (Confirmed, run)** - `NW00000113` failed `HPI-ACC1` on 2026-11-07, `RetakeWaitDays = 30`:
```bash
for d in 2026-12-07 2026-12-08 2026-12-18; do
  curl -s "$API/api/v1/candidates/NW00000113/eligibility?examCode=HPI-ACC1&date=$d"; done
# 2026-12-07 -> {"eligible":false,"reason":"RETAKE_WAIT_NOT_ELAPSED","earliestRetakeDate":"2026-12-18"}
# 2026-12-08 -> eligible:false, earliest 2026-12-18
# 2026-12-18 -> eligible:true
SQL "SELECT DATEADD(day,30,CAST('2026-11-07' AS date))"   # 2026-12-07 = what RetakeRules.cs computes
# Portal: GET http://localhost:8080/Eligibility?candidateNumber=NW00000113&examCode=HPI-ACC1&date=2026-12-07 -> eligible
```
Observed: Java earliest `2026-12-18`; C# rule (`AddDays(30)`) earliest `2026-12-07`; an 11-day
window where the two channels give opposite answers.

Confirmed on the running portal by the Windows worker: `NW00000881` (failed `BCB-PM100` on
2026-10-14) -> portal `/Eligibility` earliest 2026-11-13, booking-api 2026-11-25; the portal booked
`NW00000884` onto session 460 (18 Nov) while booking-api refused the identical request with
`409 NOT_ELIGIBLE`.

**Migration impact.** Retake eligibility gates booking, so the scheduling service will either call
it or absorb it. The exam owner must decide calendar vs working days; until then pin both behaviours
with tests (`RetakeRulesTests` in `AssessmentDelivery.Tests/UnitTests` and
`RetakeEligibilityServiceTest` in booking-api do not cover the boundary). Holidays are not
considered by either.

---

<a id="hb-05"></a>
## HB-05 Lowering capacity silently demotes booked candidates to the waitlist

**What happens.** `usp_ChangeCapacity` only validates and shifts the counter. The actual business
rule - when capacity drops below the number of BOOKED rows, the most recently booked candidates
lose their seat - lives only in `trg_ExamSession_Update`. Because it is a trigger it fires for
**any** `UPDATE ... SET Capacity` (proc, ad-hoc SQL, a future sync job). Details:
- victims are chosen by `BookedAt DESC, BookingId DESC` regardless of channel or accommodation;
- only `BOOKED` rows are counted, so `ATTENDED` seats are ignored;
- demoted rows get new waitlist positions appended after existing ones;
- `SeatsAvailable` is recomputed as `Capacity - BOOKED` (a third formula, HB-03);
- no `AuditLog` row is written per demoted booking (only the proc's `CAPACITY a -> b` row) and
  nothing notifies the candidate or partner;
- raising capacity again never re-promotes the demoted candidates, and a plain `UPDATE` that
  *raises* capacity does not touch `SeatsAvailable` at all (only the proc applies the delta), so the
  counter and capacity diverge (confirmed by the run-and-confirm worker).

**Evidence**
- `db/procs/022_usp_ChangeCapacity.sql:3,33-34` (header claims only the counter is adjusted).
- `db/triggers/033_trg_ExamSession_Update.sql:16` nesting guard, `:24,32` capacity decrease, `:35` `ORDER BY b.BookedAt DESC, b.BookingId DESC`, `:39` `WHERE b.Status = 'BOOKED'`, `:44` `rn <= cnt - Capacity`, `:49` set `WAITLISTED`, `:60-62` waitlist insert, `:67-68` counter recompute.
- `AssessmentDelivery.Services/Admin/AdminService.cs:72-79` portal admin "Capacity" screen calls the proc.

**Reproduction (Confirmed, run)**
```bash
SQL "SELECT Capacity,SeatsAvailable,(SELECT COUNT(*) FROM Booking WHERE SessionId=152 AND Status='BOOKED') FROM ExamSession WHERE SessionId=152"  # 28 | 14 | 12
SQL "EXEC dbo.usp_ChangeCapacity 152, 10, 'u1-test'"
SQL "SELECT BookingId,Status,Channel,BookedAt FROM Booking WHERE SessionId=152 AND Status='WAITLISTED'"
# 1579 WAITLISTED PARTNER 2026-11-09 15:11 ; 1580 WAITLISTED PORTAL 2026-11-10 00:42
SQL "SELECT EntityName,EntityId,Action,Detail FROM AuditLog WHERE EntityId=152 ORDER BY AuditId DESC"   # only: ExamSession 152 CAPACITY 28 -> 10
SQL "UPDATE ExamSession SET Capacity=8 WHERE SessionId=152"            # plain UPDATE, no proc
SQL "SELECT (SELECT COUNT(*) FROM Booking WHERE SessionId=152 AND Status='BOOKED'),(SELECT COUNT(*) FROM Waitlist WHERE SessionId=152)"   # 8 | 4
```

**Migration impact.** This is the core of "capacity change" in the scheduling slice and it is
invisible from the portal, the Java code and the system overview. The new service must reproduce
(or consciously change) victim selection, waitlist ordering and the missing notification, and the
trigger must be dropped or disabled when the session table moves, otherwise any write-back from the
new service into the legacy DB will demote candidates a second time.

---

<a id="hb-06"></a>
## HB-06 Reschedule behaves differently per stack

**What happens.**

| | Portal -> `usp_RescheduleBooking` | `booking-api` `POST /bookings/{id}/reschedule` |
|---|---|---|
| Implementation | `usp_CancelBooking` + `usp_BookSeat` in one transaction | Java `cancel()` + Java `book()` in one `@Transactional` |
| Channel of the new booking | always `PORTAL` (even for partner bookings) | always the configured `booking.channel` (`PARTNER`), so a portal booking moved via the API becomes `PARTNER` |
| `ExternalRef` | lost (`NULL`) | copied |
| Same-session move | allowed (cancels and re-books the same seat) | rejected `409 SAME_SESSION` |
| Retake check | in C# before calling the proc | inside `book()` |
| Seat counter | -1 / +1 | +1 on old session, **-2** on new session (HB-01) |
| Target full | candidate loses the seat and is WAITLISTED on the new session | same, and still HTTP 200 |
| Validation | exam match checked **outside** the transaction | inside |

A failure inside the proc (e.g. target not OPEN) rolls back cleanly - the original booking stays
BOOKED (verified), which is worth pinning.

**Evidence**
- `db/procs/023_usp_RescheduleBooking.sql:23-31` validation before `BEGIN TRANSACTION` at `:36`; calls cancel+book.
- `db/procs/020_usp_BookSeat.sql:57,69` channel hard-coded `PORTAL`, no `ExternalRef` parameter.
- `AssessmentDelivery.Services/Booking/BookingService.cs:160-177` portal reschedule.
- `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:148-174` (`:167` `SAME_SESSION`, `:169` cancel, `:174` copies accommodation).

**Reproduction (Confirmed, run)**
```bash
# proc path on partner booking 1606 (ExternalRef PTR-0015401) -> session 298
SQL "DECLARE @n INT,@s VARCHAR(12); EXEC dbo.usp_RescheduleBooking 1606,298,@n OUTPUT,@s OUTPUT;
     SELECT BookingId,SessionId,Status,Channel,ExternalRef FROM Booking WHERE BookingId IN (1606,@n)"
# 1606 154 CANCELLED PARTNER PTR-0015401 ; 3610 298 BOOKED PORTAL NULL
# Java path on partner booking 1610 -> session 298
curl -s -XPOST $API/api/v1/bookings/1610/reschedule -H 'Content-Type: application/json' -d '{"newSessionId":298}'
# {"bookingId":3611,"status":"BOOKED"}  -> 3611 PARTNER PTR-0015405 ; session 298 SeatsAvailable 13 -> 11
# same-session move via proc
SQL "DECLARE @n INT,@s VARCHAR(12); EXEC dbo.usp_RescheduleBooking 1613,154,@n OUTPUT,@s OUTPUT; SELECT @n,@s"   # 3612 BOOKED
# rollback on failure: target CLOSED session 281
SQL "DECLARE @n INT,@s VARCHAR(12); EXEC dbo.usp_RescheduleBooking 1564,281,@n OUTPUT,@s OUTPUT"  # Msg 50000 'Session 281 is not open for booking.'
SQL "SELECT Status FROM Booking WHERE BookingId=1564"   # BOOKED
```

**Migration impact.** Partner reconciliation relies on `Channel`/`ExternalRef`; portal-side
reschedules of partner bookings break that link today. The new reschedule API must define one
behaviour (keep channel/ref, same-session rule, keep-seat-if-target-full or not) and the tests
must pin which legacy variant is being preserved.

---

<a id="hb-07"></a>
## HB-07 Cancellation never promotes the waitlist

**What happens.** Cancelling a BOOKED booking frees a seat (`SeatsAvailable + 1`) but leaves every
waitlisted candidate WAITLISTED; the next *new* booker gets the seat ahead of the waitlist.
Cancelling a WAITLISTED booking deletes its `Waitlist` row without renumbering positions.
Promotion is a manual, out-of-system step ("centre staff promote").

**Evidence**
- `db/procs/021_usp_CancelBooking.sql:3` header: "Waitlisted candidates are NOT promoted automatically", `:38` increment only.
- `services/booking-api/.../dao/BookingDao.java` cancel delegates to `usp_CancelBooking`; no promotion anywhere in Java or C# (`grep -rn promot` finds only the proc comment).

**Reproduction (Confirmed, run)** (session 167 after HB-02)
```bash
curl -s -XDELETE "$API/api/v1/bookings/1696?reason=f7"; curl -s -XDELETE "$API/api/v1/bookings/1697?reason=f7"
SQL "SELECT Capacity,(SELECT COUNT(*) FROM Booking WHERE SessionId=167 AND Status='BOOKED'),
            (SELECT COUNT(*) FROM Booking WHERE SessionId=167 AND Status='WAITLISTED') FROM ExamSession WHERE SessionId=167"
# 8 | 7 | 18  -> a free seat and 18 people waiting, nobody promoted
```

**Migration impact.** If the new service adds auto-promotion (a natural "improvement"), it changes
who sits the exam and must be a product decision. Pin "cancel does not promote" and "positions are
not renumbered".

---

<a id="hb-08"></a>
## HB-08 Default accommodation applied only by the portal

**What happens.** When the portal booking form leaves accommodation blank, the portal substitutes
`Candidate.DefaultAccommodationCode` (e.g. `ET50`), which then needs an accommodation seat.
`booking-api` only uses the code in the request body. The same candidate on the same session is
refused (or waitlisted on the ET pool) in the portal but gets a standard seat via a partner.

**Evidence**
- `AssessmentDelivery.Services/Booking/BookingService.cs:101` `string.IsNullOrWhiteSpace(accommodationCode) ? candidate.DefaultAccommodationCode : accommodationCode`.
- `services/booking-api/src/main/java/com/northwind/assessment/booking/service/BookingService.java:62` `StringUtils.trimToNull(req.getAccommodationCode())` - no default lookup.
- `db/procs/020_usp_BookSeat.sql:51` accommodation seat check (state 3).

**Reproduction (Confirmed, run)** - `NW00000903` has `DefaultAccommodationCode = 'ET50'`; session 298 has `AccommodationCapacity = 0`:
```bash
curl -s -XPOST $API/api/v1/bookings -H 'Content-Type: application/json' -d '{"sessionId":298,"candidateNumber":"NW00000903"}'
# 201 {"bookingId":3613,"status":"BOOKED"}  -> Booking.AccommodationCode = NULL
# What the portal sends for the same blank form (ET50 substituted), executed against the proc:
SQL "DECLARE @b INT,@s VARCHAR(12); EXEC dbo.usp_BookSeat 298,904,'ET50',@b OUTPUT,@s OUTPUT"
# Msg 50000, Level 16, State 3: No accommodation seat available on session 298.
```

Related (SQL worker, R10): accommodation seats are not *held back* by the procs - standard bookings
can consume every seat so an ET candidate is waitlisted - and when the ET pool itself is used up the
ET candidate gets an error (state 3) instead of a waitlist place. Only capacity-sync models a
reserved pool (HB-03).

**Migration impact.** Accommodation handling must be one rule in the new service; decide whether
the profile default applies to all channels. Pin both today.

---

<a id="hb-09"></a>
## HB-09 Retake eligibility is not enforced by the database

**What happens.** `usp_BookSeat` explicitly leaves retake checks to callers. Anything that writes
bookings without the C#/Java service layer (imports, `IMPORT` channel, ad-hoc SQL, the proc called
directly, a new service that forgets) can book a candidate who already passed. Both application
checks only look at `Result` rows, so a candidate with a BOOKED-but-not-yet-scored attempt, or an
ATTENDED attempt whose score file was skipped (HB-12), is treated as having no prior attempt.

**Evidence**
- `db/procs/020_usp_BookSeat.sql` header comment (retake validated by the calling application).
- `AssessmentDelivery.Services/Booking/BookingService.cs:106-107` "retake check has to happen here, usp_BookSeat does not know about results".
- `AssessmentDelivery.Services/Eligibility/EligibilityService.cs:32-34`; `services/booking-api/.../dao/ResultDao.java` - attempts = `Result` rows only.

**Reproduction (Confirmed, run)** - `NW00000002` passed `BCB-AGL`:
```bash
curl -s "$API/api/v1/candidates/NW00000002/eligibility?examCode=BCB-AGL&date=2026-11-14"   # eligible:false, ALREADY_PASSED
SQL "DECLARE @b INT,@s VARCHAR(12); EXEC dbo.usp_BookSeat 158,2,NULL,@b OUTPUT,@s OUTPUT; SELECT @b,@s"   # 3614 BOOKED
```

No-shows (ATTENDED/BOOKED past sessions without a `Result`) never count as attempts - 106 such
bookings exist in the seed (run-and-confirm worker).

**Migration impact.** In a database-per-service world the scheduling service cannot join to
`Result`; eligibility becomes a cross-service call or a replicated read model. Decide what counts
as an attempt (result only vs any attended booking).

---

<a id="hb-10"></a>
## HB-10 Inactive centres and rooms are still bookable

**What happens.** `TestCenter.IsActive` only filters the portal's centre drop-down
(`ListActiveCenters`). Neither `usp_BookSeat` nor `booking-api` checks `TestCenter.IsActive` or
`Room.IsActive`; deactivating a centre via the portal admin screen (an EF update, no proc, no audit)
does not close its sessions.

**Evidence**
- `AssessmentDelivery.Services/Booking/BookingService.cs:61` only use of `IsActive` in booking.
- `AssessmentDelivery.Services/Admin/AdminService.cs:50-59` `UpdateCenter` via `SaveChanges`, no audit.
- `grep -n IsActive db/procs services/booking-api/src/main` -> no matches.

**Reproduction (Confirmed, run)**
```bash
SQL "UPDATE TestCenter SET IsActive=0 WHERE CenterCode='SFO01'"
curl -s -XPOST $API/api/v1/bookings -H 'Content-Type: application/json' -d '{"sessionId":<open SFO01 session>,"candidateNumber":"NW00000940"}'  # 201 BOOKED
SQL "DECLARE @b INT,@s VARCHAR(12); EXEC dbo.usp_BookSeat <same session>,941,NULL,@b OUTPUT,@s OUTPUT; SELECT @s"   # BOOKED
SQL "UPDATE TestCenter SET IsActive=1 WHERE CenterCode='SFO01'"
```

**Migration impact.** Test-centre lifecycle is part of the scheduling slice; decide whether
deactivation closes/cancels sessions. Writes to `TestCenter` are not audited (no trigger).

---

<a id="hb-11"></a>
## HB-11 Stored-procedure error contract

**What happens.** All procs raise user errors with `RAISERROR(msg, 16, state)` - SQL error number
**50000**; the *state* carries the code (1 not open, 2 duplicate, 3 no accommodation seat,
4 cannot cancel, 5 invalid capacity, 6 cannot move). `docs/SYSTEM_OVERVIEW.md` lists them as
"50001..50006". The portal's `SqlErrorHelper` turns any msg 50000 / severity 16 into a user-facing
`PortalException` showing the proc's text; everything else is a 500. `booking-api` duplicates the
same checks in Java with its own HTTP 409/422 codes, so the two stacks report the same business
error differently. Because the `CATCH` blocks re-raise everything as msg 50000 with the original
state, foreign-key violations and deadlocks (state 51 observed by the SQL worker) also reach the
portal as "business" messages.

**Evidence**
- `db/procs/020_usp_BookSeat.sql:38,43,51,85-87`; `021_usp_CancelBooking.sql:30`; `022_usp_ChangeCapacity.sql:29`; `023_usp_RescheduleBooking.sql:31,44-46`.
- `AssessmentDelivery.Services/Common/SqlErrorHelper.cs:8-9,15` (`UserDefinedMessage = 50000`, severity 16).

**Reproduction.** Any HB-08 / HB-06 rollback reproduction above returns `Msg 50000, Level 16, State n`.

**Migration impact.** Any adapter that keeps the portal calling a proc-shaped facade must keep
"msg 50000 + human-readable text" or the portal shows a yellow screen instead of a message.

---

<a id="hb-12"></a>
## HB-12 Results ingestion side effects

**What happens.** `results-service` (the only writer of `Result`):
- computes `Passed = score >= Exam.PassingScore` once at ingest; later changes to `PassingScore`
  (audited by `trg_Exam_Audit`) never re-evaluate existing results, yet `Passed` drives retake
  eligibility (HB-04/HB-09);
- flips the booking `BOOKED -> ATTENDED` directly (no proc) - ATTENDED still holds a seat in
  `seat-drift.sql`/capacity-sync but not in the capacity-decrease trigger (HB-03/HB-05);
- does not check the session status or date (a result was accepted for a 2027-02-04 session and the
  booking marked ATTENDED);
- a corrected score for the same booking is rejected with 409 (`UQ_Result_Booking`), so there is no
  correction path; re-uploading a CSV skips every row;
- score-file upload commits each row independently, skips bad rows and still returns HTTP 200
  (7-row test file: 2 imported, 5 skipped); no CSV quote handling; lowercase candidate numbers rejected.

**Evidence**
- `services/results-service/src/main/java/com/northwind/ad/results/service/ResultIngestService.java:36,50,61,75`.
- `services/results-service/src/main/java/com/northwind/ad/results/service/ScoreFileImporter.java:18,57-61`; `CsvRowParser.java:28,35`.

**Reproduction**
```bash
curl -s -XPOST http://localhost:8082/api/v1/results -H 'Content-Type: application/json' \
     -d '{"bookingId":<a BOOKED booking>,"score":650,"scoredAt":"2026-11-12T12:00:00"}'
SQL "SELECT Status FROM Booking WHERE BookingId=<id>"     # ATTENDED
```

**Migration impact.** The scheduling service will receive "attended" state from the results slice;
the event contract must carry it, and seat counts must treat ATTENDED consistently.

---

<a id="hb-13"></a>
## HB-13 Time and scheduled-job assumptions

**What happens.**
- `ExamSession.StartsAt` is centre-local `DATETIME` with no offset (`TestCenter.TimeZone` is
  informational); Java date logic (`DateUtils`) truncates with the JVM default zone, the portal
  with server local time.
- capacity-sync runs at `0 0 2 * * *` *server* time "after the portal's 01:00 session close-out",
  but no close-out job exists in the repo: nothing in the legacy code moves sessions from OPEN to
  CLOSED/DELIVERED. That transition is done outside the codebase (ops / SQL Agent) and is
  undocumented.
- Session searches use `StartsAt >= today` (portal) and an inclusive `to` date converted to
  `< to+1` (Java).

**Evidence**
- `db/schema/010_tables.sql` (`StartsAt DATETIME`); `services/booking-api/.../util/DateUtils.java`;
  `services/capacity-sync/.../NightlySyncJob.java:13-14`; `AssessmentDelivery.Services/Booking/BookingService.cs:153-156`.

**Migration impact.** Java 21 services in containers default to UTC. Pin date-boundary tests
(retake boundary, "today" in session search) with an explicit zone, and find the owner of the
session close-out job before moving session status ownership.

---

<a id="hb-14"></a>
## HB-14 Session search contract

**What happens.** `GET /api/v1/sessions` returns sessions of **every** status (an Oct-Nov query
returned 88 DELIVERED and 6 CANCELLED alongside OPEN) and silently stops at 500 rows with no paging
indicator. The portal search only shows `Status = 'OPEN'` sessions starting today or later.

**Evidence**
- `services/booking-api/src/main/java/com/northwind/assessment/booking/dao/SessionDao.java:30,36` (`MAX_ROWS = 500`, `SELECT TOP 500`), `:36-55` no status filter.
- `AssessmentDelivery.Services/Booking/BookingService.cs:28` (`Status == "OPEN"`).

**Reproduction (Confirmed, run - Java worker)**
```bash
curl -s "$API/api/v1/sessions?from=2026-10-01&to=2026-11-30" | grep -o '"status" : "[A-Z]*"' | sort | uniq -c
curl -s "$API/api/v1/sessions" | grep -c '"sessionId"'     # 462 = every session of every status (seed is under the TOP 500 cap)
```

**Migration impact.** Partners may depend on seeing non-OPEN sessions and on the 500 cap; a new
session API with paging/filtering by default is a contract change for them.

---

<a id="hb-15"></a>
## HB-15 Seat trigger is INSERT-only; bulk and direct writes bypass every rule

**What happens.** `trg_Booking_SeatSync` is `AFTER INSERT` only. Any direct `UPDATE`/`DELETE` on
`Booking` (cancel by SQL, delete, move to another session) never changes the seat count and writes no
audit row. A multi-row direct insert (the `IMPORT` channel the trigger was written for) enforces none
of the proc's rules: no capacity check, no duplicate check, no accommodation limit, and a
`WAITLISTED` row gets no `Waitlist` row. `seat-drift.sql` cannot see the resulting overbooking
because the counter is consistent with the (overbooked) booking count - it only checks the counter,
never `BOOKED > Capacity`.

**Evidence**
- `db/triggers/032_trg_Booking_SeatSync.sql:12-13` (`AFTER INSERT`), `:19-23`.
- `db/schema/010_tables.sql` - no CHECK/unique constraint backs any booking rule.
- `db/checks/seat-drift.sql:18,26` compares counter with bookings only.

**Reproduction (Confirmed, run - SQL worker R03/R04)**
```sql
-- direct cancel: seat never returned, no audit
UPDATE dbo.Booking SET Status='CANCELLED' WHERE BookingId=<BOOKED id on session S>;
-- db/checks/seat-drift.sql now reports session S
-- bulk import into a 9-seat session
INSERT INTO dbo.Booking (SessionId,CandidateId,Status,AccommodationCode,Channel)
SELECT <S9>, CandidateId, 'BOOKED', CASE WHEN CandidateId%4=0 THEN 'ET25' END, 'IMPORT'
  FROM dbo.Candidate WHERE CandidateNumber BETWEEN 'NW00000960' AND 'NW00000971';
SELECT Capacity, SeatsAvailable FROM dbo.ExamSession WHERE SessionId=<S9>;   -- 9 | -3, not flagged by seat-drift.sql
```
Observed by the SQL worker: 12 BOOKED on a 9-seat session, same candidate twice, 3 ET bookings
against `AccommodationCapacity = 1`; direct updates left drift of +4 and -1 on two sessions with zero
audit rows.

**Migration impact.** Data migrated from legacy will contain overbooked sessions and duplicate
active bookings that the new schema's constraints will reject - plan a cleansing step and pin the
counts. Any bulk loader in the new world must go through the same rules as the API.

---

<a id="hb-16"></a>
## HB-16 Past (DELIVERED) sessions are still mutable

**What happens.** `usp_CancelBooking` checks only the booking status and `usp_ChangeCapacity` only
validates the number; neither checks `ExamSession.Status` or `StartsAt`. A cancellation or capacity
change on a DELIVERED session is accepted and leaves negative drift.

**Evidence**
- `db/procs/021_usp_CancelBooking.sql:24-41`; `db/procs/022_usp_ChangeCapacity.sql:24-36`.

**Reproduction (Confirmed, run - SQL worker R05b/R06b)**
```bash
SQL "EXEC dbo.usp_ChangeCapacity <DELIVERED session>, 5, 'test'"           # succeeds, demotes bookings via HB-05
SQL "EXEC dbo.usp_CancelBooking <BOOKED booking on a DELIVERED session>, N'late', 'test'"   # succeeds
```

**Migration impact.** Decide whether the new service freezes sessions after delivery; pin today's
permissive behaviour so the change is visible.

---

<a id="hb-17"></a>
## HB-17 Lock order and change-tracking columns

**What happens.**
- `usp_CancelBooking` locks `Booking` then `ExamSession`; `usp_BookSeat` locks `ExamSession` then
  `Booking`. Two reschedules in opposite directions (A->B and B->A) can deadlock: one commits, the
  other fails as a deadlock victim re-raised as msg 50000 state 51 (HB-11).
- `trg_ExamSession_Update` overwrites `LastModified` on every update - including no-op updates and
  capacity-sync runs - and ignores a caller-supplied value; `Booking.LastModified` is maintained by
  some writers only (procs/Java/trigger set it ad hoc; no trigger guarantees it).

**Evidence**
- `db/procs/021_usp_CancelBooking.sql:24-26` vs `db/procs/020_usp_BookSeat.sql:33-35`.
- `db/triggers/033_trg_ExamSession_Update.sql:19-22`.

**Reproduction (Confirmed, run - SQL worker R08/R11).** Two `sqlcmd` sessions running
`usp_RescheduleBooking` for a booking on A -> B and another on B -> A in a loop; one reports
`Msg 50000 ... State 51` (deadlock victim).

**Migration impact.** `LastModified` cannot be used as a CDC watermark for incremental data sync
from legacy to the new service; use row versions or a change table. The new service needs a single
lock order (session first) for move operations.

---

<a id="hb-18"></a>
## HB-18 Portal-only gaps (found by running the portal on Windows)

**What happens.**
- **Cancel has no ownership check.** `POST /Booking/Cancel/{id}` cancels any booking id; the
  candidate number in the form is only used for the redirect. There is no sign-in
  (`<authentication mode="None" />`), so anyone can cancel anyone's booking, including partner bookings.
- **Capacity range is client-side only.** `AdminModels.cs` declares `[Range(0, 500)]` but the POST
  action takes a bare `int? newCapacity`; the server accepted 9999. `usp_ChangeCapacity` only rejects
  negative values and values below `AccommodationCapacity`.
- **Dates are parsed as en-GB** (`<globalization culture="en-GB">`); a search with a date the model
  binder cannot parse silently drops the date filter and returns every session.
- **Config that nothing reads:** `Portal.ResultsReleaseDelayHours`, `Portal.KioskMode`,
  `Portal.DefaultRegion`, `Portal.SupportEmail`; results are visible to anyone by candidate number.
  `Portal.HostType=Azure` only sets `DataLayerInitialise.IsAzure`, which nothing reads - no EF
  execution strategy / transient-fault retry on Azure SQL.
- Proc error texts reach users verbatim; the duplicate-booking message exposes the internal
  `CandidateId`. Audit rows written by triggers carry the DB login (`ad_app`), not the operator.

**Evidence**
- `AssessmentDelivery.Web/Controllers/BookingController.cs:89-105`.
- `AssessmentDelivery.Web/Controllers/AdminController.cs:70` vs `AssessmentDelivery.Web/Models/AdminModels.cs:20`; `db/procs/022_usp_ChangeCapacity.sql:29`.
- `AssessmentDelivery.Web/Web.config:30,34` (authentication, globalization), `:20-27` (app settings); `AssessmentDelivery.Services/Booking/BookingService.cs:35-44`.
- `AssessmentDelivery.Data/Startup/DataLayerInitialise.cs:8,14-16`; `AssessmentDelivery.Services/Results/ResultsService.cs:16`.
- `db/procs/020_usp_BookSeat.sql:43` (message includes `@CandidateId`).

**Reproduction (Confirmed, run - Windows portal worker)**
```http
POST http://localhost:8080/Booking/Cancel/<any booking id>     (form: Reason=x, CandidateNumber=<anyone>, __RequestVerificationToken)
-> 302 to /Booking/MyBookings, booking CANCELLED
POST http://localhost:8080/Admin/Sessions/Capacity/460          (form: newCapacity=9999)
-> accepted; SELECT Capacity FROM ExamSession WHERE SessionId=460 -> 9999
```

**Migration impact.** The new scheduling API must add authorization on cancel/reschedule and
server-side capacity limits; decide whether the 500 limit is a real business rule. Containerised
Java 21 services must not inherit the "no retry" behaviour on Azure SQL.
