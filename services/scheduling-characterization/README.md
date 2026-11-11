# scheduling-characterization

Characterization suite for the legacy scheduling and seat-capacity logic in the shared `AssessmentDelivery`
SQL Server database. It is the **executable spec** for the scheduling-service migration: Phase 1 must make it
pass against the new service unchanged, and Phase 3 reuses the scenario catalogue below.

Legacy behaviour, defects included, is the spec. Defects are pinned as characterized behaviour with their
`docs/as-is/hidden-behaviours.md` (HB-xx) / `docs/as-is/migration-risks.md` (MR-xx) ids. Nothing here fixes them.

## Run

```bash
cd services/scheduling-characterization
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B verify            # same as -Dtarget=legacy / -Ptarget-legacy
mvn -B verify -Dconcurrency.iterations=100 -Dconcurrency.threads=16    # heavier concurrency run
```

You need JDK 21, Maven 3.9 and a Docker engine. No other setup.

- Testcontainers starts `mcr.microsoft.com/mssql/server:2022-latest`, copies the repo's `db/` folder into it and runs
  the real `db/apply.sh` with the container's own sqlcmd. That applies `db/schema`, `db/procs`, `db/triggers` and
  `db/seed` in file order, the same way `scripts/run-legacy.sh` does. The tests connect as the shared `ad_app` login,
  the one the portal and the Java services use, so every proc and trigger runs exactly as in production.
- Docker Engine 29 rejects the docker-java default API version, so `src/test/resources/docker-java.properties` pins
  `api.version=1.44` (Testcontainers 1.21.4).
- If Maven Central returns 429, add a `~/.m2/settings.xml` mirror (`mirrorOf=central`) pointing at
  `https://maven-central.storage-download.googleapis.com/maven2`.
- To run against a database that is already provisioned (for example the `mssql` container from `scripts/run-legacy.sh`):
  `-Dlegacy.jdbcUrl='jdbc:sqlserver://localhost:1433;databaseName=AssessmentDelivery;encrypt=false'`
  (optionally with `-Dlegacy.user` / `-Dlegacy.password`; the default is `ad_app`). Fixtures use unique per-run codes, so
  repeated runs on the same database do not collide. The baseline drift is captured at start.

Fixtures: every scenario creates its own exam, candidates and sessions (sessions start in 2031) through
`Fixtures`. Scenarios never read seed rows, so the results do not depend on the wall clock (the seed is anchored on
2026-11-10/11, HB-30). The seed is still loaded, so the seat-drift invariant runs over realistic data.

## Target abstraction (how Phase 1 plugs in)

All scenarios are written against `com.northwind.scheduling.characterization.spi` (in `src/main/java`):

| Type | Role |
|---|---|
| `SchedulingTarget` | `book` / `cancel` / `changeCapacity` / `reschedule` (+ `markAttended`, the results-service write, and `reconcileSeats`, the capacity-sync reconciler). State reads: `session`, `booking`, `bookings`, `waitlist`, `audit`. Also exposes `fixtures()`, `seatDriftCheck()`, `capabilities()` and the optional `rawStorage()`. Must be thread-safe. |
| `Outcome` / `SchedulingError` / `ErrorCondition` | Operations return outcomes and never throw for business errors. `ErrorCondition` names the legacy errors and keeps the documented numeric code: `SESSION_NOT_OPEN` 50001, `DUPLICATE_ACTIVE_BOOKING` 50002, `NO_ACCOMMODATION_SEAT` 50003, `BOOKING_NOT_CANCELLABLE` 50004, `INVALID_CAPACITY` 50005, `RESCHEDULE_NOT_ALLOWED` 50006, `SYSTEM_ERROR` 50000. Scenarios assert both the condition and the code. |
| `Model` | `ExamSessionState`, `BookingState`, `WaitlistEntry`, `AuditEntry`, `SeatDrift`, `SessionSpec`. Column names follow `db/schema/010_tables.sql`. |
| `Fixtures` | `newExam`, `newCandidate`, `newSession(SessionSpec)` (reference data that Phase 1 replicates). |
| `SeatDriftCheck` | Pluggable drift invariant. `StateDerivedSeatDriftCheck` derives it from state reads for any target. |
| `RawStorageAccess` + `Capability.RAW_STORAGE` | Optional row-level writes that bypass the operations, as imports, booking-api, results-service and ad-hoc admin UPDATEs do. Only the trigger-032/033 storage scenarios and a few HB-01 setups need it. |
| `Capability.LEGACY_SQL_SEMANTICS` | Optional: raw SQL message number/state (`nativeNumber`/`nativeState`) and the `SUSER_SNAME()` audit-actor default. |
| `Capability.FRESH_DATABASE` | The run provisioned its own store (legacy: Testcontainers, not `-Dlegacy.jdbcUrl`); only then does SD-01 assert a drift-free seed baseline. |
| `SchedulingTargetProvider` / `SchedulingTargets` | ServiceLoader SPI. `-Dtarget=<name>` (default `legacy`) picks the provider, and the JVM uses one instance. |

Legacy implementation (`...characterization.legacy`): `LegacyProcTarget` EXECs `usp_BookSeat`, `usp_CancelBooking`,
`usp_ChangeCapacity` and `usp_RescheduleBooking` over JDBC. `LegacyDatabase` provisions the container, `LegacyFixtures`
and `LegacyRawStorage` handle direct row writes, and `LegacySeatDriftSqlCheck` runs `db/checks/seat-drift.sql` verbatim,
batch by batch. `LegacyProcTargetProvider` is registered as `legacy`. The legacy error mapping uses the RAISERROR state
plus the proc's message template, because the state alone is ambiguous: Msg 266 also has state 2.

**Adding a target (Phase 1):**
1. Implement `SchedulingTarget`, for example `SchedulingServiceTarget`, which calls the scheduling-service HTTP/Java API.
   Map its errors onto `ErrorCondition`, and return BOOKED/WAITLISTED in `Outcome.status`.
2. Implement `Fixtures`, seeding the replicated reference data and sessions, and `seatDriftCheck()`. Return
   `new StateDerivedSeatDriftCheck(this, ...)` or a native check.
3. Optionally implement `RawStorageAccess` against the new tables and declare `RAW_STORAGE`. Scenarios that need a
   capability the target does not declare are reported as skipped, not failed.
4. Add a `SchedulingTargetProvider` whose `name()` is the new `-Dtarget` value, list it in
   `src/main/resources/META-INF/services/com.northwind.scheduling.characterization.spi.SchedulingTargetProvider`, and
   (optionally) add a `target-<name>` Maven profile.
5. Run `mvn -B verify -Dtarget=<name>`. Do not edit the scenarios.

## Seat-drift invariant

`SeatDriftInvariantExtension` runs on every test class through `CharacterizationTest`. It runs the target's
`seatDriftCheck()` (legacy: `db/checks/seat-drift.sql`) **after every test** over the whole database.
A test fails unless every reported row is either:
- part of the baseline captured once before the first scenario (the freshly provisioned seed has none, see SD-01), or
- drift that a scenario declared as characterized legacy behaviour with `expectDrift(sessionId, drift, "HB-xx ...")`.
  The declared value must match exactly, and declared drift that does not appear also fails.

Drift pinned as characterized behaviour (it is not silenced; the exact values are asserted):

| Source | Drift | Scenarios |
|---|---|---|
| HB-01 partner booking (trigger 032 + booking-api decrement) | +1 per partner booking | T32-10, CB-08, RS-13, SD-02, SD-03, SD-06 |
| HB-02 counter-only booking decision (phantom-full / negative counters) | fixture counter | BS-05, BS-06 |
| HB-03/HB-05 trigger 033 recount `Capacity - BOOKED` ignores ATTENDED | -1 | TD-06 |
| HB-05/HB-03 022 zero floor when ATTENDED rows exceed the new capacity | -1 | CC-04 |
| HB-03 capacity-sync formula `Capacity - AccommodationCapacity - non-ET bookings` | +AccCap - ET bookings | SD-04 |
| HB-04 trigger 032 ignores UPDATE/DELETE and PORTAL-channel inserts | ±1 | T32-03, T32-05, T32-06, T32-07 |
| MR-01 direct `Capacity` UPDATEs (no proc) leave the counter untouched | ±delta | T33-03, T33-04, T33-05, SD-02 |

## Concurrency

The procs lock `ExamSession` (020, 022) and `Booking` (021) `WITH (UPDLOCK, HOLDLOCK)`. The CX scenarios release
`concurrency.threads` callers (default 8) through a barrier and repeat `concurrency.iterations` times (default 25).
Every iteration checks its own sessions for drift and waitlist density. Each run appends a one-line result per
scenario to `target/concurrency-summary.txt`.

## Scenario catalogue

`CatalogueConsistencyTest` fails if this table and the `@Scenario` annotations diverge. The table is generated from
the annotations. Codes 50001-50006 are the documented codes; on the wire the legacy procs raise Msg 50000 with that
RAISERROR state (HB-14, asserted in BS-22 / CC-13 / RS-14).

#### BookSeatScenariosTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| BS-01 | 020 SeatsAvailable > 0 -> BOOKED, counter -1, PORTAL channel, BOOK audit | HB-01 | `BookSeatScenariosTest#bookWithFreeSeat` |
| BS-02 | 020 last seat -> BOOKED (counter 0); next -> WAITLISTED position 1, counter stays 0 |  | `BookSeatScenariosTest#lastSeatThenWaitlist` |
| BS-03 | 020 waitlist Position = MAX(Position)+1 per session (1,2,3; other session restarts at 1) |  | `BookSeatScenariosTest#waitlistPositionsArePerSessionSequence` |
| BS-04 | 020 Position uses MAX+1, not COUNT+1: gaps from cancelled waitlist rows are kept | HB-06, MR-12 | `BookSeatScenariosTest#waitlistPositionAfterGap` |
| BS-05 | 020 decision reads only the SeatsAvailable counter: counter 0 with free seats -> WAITLISTED | HB-02, MR-01 | `BookSeatScenariosTest#decisionUsesCounterOnly` |
| BS-06 | 020 negative counter -> WAITLISTED, counter unchanged (no floor, no repair) | HB-02 | `BookSeatScenariosTest#negativeCounterWaitlists` |
| BS-07 | 020 state 1: session does not exist -> 50001, nothing written | HB-14 | `BookSeatScenariosTest#unknownSession` |
| BS-08 | 020 state 1: session Status <> 'OPEN' (CLOSED/DELIVERED/CANCELLED) -> 50001 | HB-14, HB-31 | `BookSeatScenariosTest#sessionNotOpen` |
| BS-09 | 020 state 2: candidate already BOOKED on the session -> 50002, no change | HB-21 | `BookSeatScenariosTest#duplicateBooked` |
| BS-10 | 020 state 2: candidate already WAITLISTED on the session -> 50002 |  | `BookSeatScenariosTest#duplicateWaitlisted` |
| BS-11 | 020 duplicate check ignores CANCELLED: re-book after cancel -> new BOOKED row |  | `BookSeatScenariosTest#rebookAfterCancel` |
| BS-12 | 020 duplicate check ignores ATTENDED: ATTENDED + new BOOKED on the same session | HB-08 | `BookSeatScenariosTest#bookAfterAttended` |
| BS-13 | 020 duplicate scope is the session only: same candidate on two sessions of one exam | HB-21, HB-08 | `BookSeatScenariosTest#duplicateIsPerSessionOnly` |
| BS-14 | 020 ET25/ET50 with accommodation seat free -> BOOKED, code stored, counter -1 |  | `BookSeatScenariosTest#extraTimeWithinPool` |
| BS-15 | 020 state 3: ET pool used up by a BOOKED ET25 -> ET50 gets 50003 although seats are free |  | `BookSeatScenariosTest#extraTimePoolExhaustedByBooked` |
| BS-16 | 020 state 3: ATTENDED ET bookings also consume the pool |  | `BookSeatScenariosTest#extraTimePoolCountsAttended` |
| BS-17 | 020 state 3: AccommodationCapacity 0 -> any ET booking 50003 even with free seats | HB-08 | `BookSeatScenariosTest#extraTimeWithoutPool` |
| BS-18 | 020 ET check runs before the full check: ET on a full session with no pool -> 50003, not WAITLISTED | HB-08 | `BookSeatScenariosTest#extraTimeOnFullSessionErrorsInsteadOfWaitlist` |
| BS-19 | 020 WAITLISTED ET bookings do not consume the pool: ET on a full session with pool left -> WAITLISTED |  | `BookSeatScenariosTest#waitlistedExtraTimeDoesNotConsumePool` |
| BS-20 | 020 non-ET codes (SR, RB, unknown) skip the pool check and are stored as given | HB-08 | `BookSeatScenariosTest#nonExtraTimeCodes` |
| BS-21 | 020 CATCH re-raise: unknown candidate (FK 547) -> system error surfaced as 50000, nothing written | HB-14, MR-13 | `BookSeatScenariosTest#unknownCandidate` |
| BS-22 | 020 legacy SQL contract: errors are Msg 50000 with RAISERROR state 1/2/3 (documented 50001-50003 never raised) | HB-14 | `BookSeatScenariosTest#legacyErrorNumbersAreStates` |

#### CancelBookingScenariosTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| CB-01 | 021 BOOKED -> CANCELLED (CancelledAt, CancelReason), SeatsAvailable +1, CANCEL audit with reason/actor |  | `CancelBookingScenariosTest#cancelBookedReleasesSeat` |
| CB-02 | 021 WAITLISTED -> CANCELLED, Waitlist row deleted, SeatsAvailable unchanged, remaining positions not renumbered | HB-06, MR-12 | `CancelBookingScenariosTest#cancelWaitlistedDeletesWaitlistRow` |
| CB-03 | 021 freeing a seat never promotes the waitlist: seat free while candidates stay WAITLISTED | HB-06, MR-12 | `CancelBookingScenariosTest#cancelBookedDoesNotPromote` |
| CB-04 | 021 state 4: already CANCELLED -> 50004, no change, no audit row |  | `CancelBookingScenariosTest#cancelCancelled` |
| CB-05 | 021 state 4: ATTENDED -> 50004 |  | `CancelBookingScenariosTest#cancelAttended` |
| CB-06 | 021 state 4: unknown booking id -> 50004 |  | `CancelBookingScenariosTest#cancelUnknown` |
| CB-07 | 021 no session status check: cancelling on a DELIVERED session increments its counter | HB-22 | `CancelBookingScenariosTest#cancelOnDeliveredSession` |
| CB-08 | 021 gives back exactly 1 seat: partner book (-2, HB-01) + cancel (+1) leaks a seat | HB-01, HB-02 | `CancelBookingScenariosTest#partnerBookCancelLeaksSeat` |
| CB-09 | 021 audit actor defaults to the SQL login (SUSER_SNAME) when @Actor is NULL | HB-17 | `CancelBookingScenariosTest#auditActorDefaultsToLogin` |

#### ChangeCapacityScenariosTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| CC-01 | 022 raise: SeatsAvailable += delta, CAPACITY audit 'old -> new' with actor, waitlist not promoted | HB-06, MR-04 | `ChangeCapacityScenariosTest#raiseCapacity` |
| CC-02 | 022 lower, still >= BOOKED: SeatsAvailable += (new - old), no demotion |  | `ChangeCapacityScenariosTest#lowerAboveBooked` |
| CC-03 | 022 lower to exactly BOOKED: SeatsAvailable 0, no demotion |  | `ChangeCapacityScenariosTest#lowerToBooked` |
| CC-04 | 022 zero floor: SeatsAvailable + delta < 0 without demotion (ATTENDED rows) -> 0; drift -1 | HB-05, HB-03, MR-02 | `ChangeCapacityScenariosTest#zeroFloorWithAttended` |
| CC-05 | 022 zero floor masks partner drift: counter -1 floored to 0 equals the true value | HB-01 | `ChangeCapacityScenariosTest#zeroFloorMasksPartnerDrift` |
| CC-06 | 022 state 5: new capacity < AccommodationCapacity -> 50005, nothing changes |  | `ChangeCapacityScenariosTest#belowAccommodationCapacity` |
| CC-07 | 022 state 5: negative capacity -> 50005 |  | `ChangeCapacityScenariosTest#negativeCapacity` |
| CC-08 | 022 state 5: unknown session -> 50005 |  | `ChangeCapacityScenariosTest#unknownSession` |
| CC-09 | 022 boundary: capacity == AccommodationCapacity is accepted |  | `ChangeCapacityScenariosTest#equalToAccommodationCapacity` |
| CC-10 | 022 same capacity: no counter change, audit '5 -> 5' still written |  | `ChangeCapacityScenariosTest#sameCapacity` |
| CC-11 | 022 no status check: capacity of a CLOSED session can be changed |  | `ChangeCapacityScenariosTest#closedSession` |
| CC-12 | 022 no upper bound: capacity 9999 accepted (exceeds room seats) | HB-23 | `ChangeCapacityScenariosTest#noUpperBound` |
| CC-13 | 022 legacy SQL contract: Msg 50000 state 5, NULL actor audited as the SQL login | HB-14, HB-17 | `ChangeCapacityScenariosTest#legacyErrorAndActor` |

#### CapacityDemotionScenariosTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| TD-01 | 033 cut below BOOKED: newest BOOKED (BookedAt DESC) -> WAITLISTED + Waitlist rows; SeatsAvailable = Capacity - BOOKED; one CAPACITY audit, none per booking | HB-05, MR-04 | `CapacityDemotionScenariosTest#demotesNewest` |
| TD-02 | 033 demoted rows are appended after the existing waitlist (MAX(Position)+n), oldest demoted first | HB-05 | `CapacityDemotionScenariosTest#appendsAfterExistingWaitlist` |
| TD-03 | 033 selection orders by BookedAt DESC (not BookingId): latest BookedAt demoted, Position by BookedAt ASC | HB-05 | `CapacityDemotionScenariosTest#ordersByBookedAtNotId` |
| TD-04 | 033 BookedAt tie: BookingId DESC breaks the tie for selection, BookingId ASC for Position | HB-05 | `CapacityDemotionScenariosTest#tieBreakOnBookingId` |
| TD-05 | 033 raising capacity afterwards does not restore demoted bookings; counter += delta | MR-04, HB-06 | `CapacityDemotionScenariosTest#raiseDoesNotRestore` |
| TD-06 | 033 decision and recount count BOOKED only: with ATTENDED rows the recount leaves drift -1 | HB-03, HB-05, MR-02 | `CapacityDemotionScenariosTest#attendedNotCounted` |
| TD-07 | 033 cut to 0: every BOOKED row demoted, positions in BookedAt order |  | `CapacityDemotionScenariosTest#cutToZero` |
| TD-08 | 033 demoted booking keeps id/channel, CancelledAt NULL; cancelling it later deletes its Waitlist row, counter unchanged |  | `CapacityDemotionScenariosTest#demotedBookingLifecycle` |
| TD-09 | 033 demoting an ET booking frees the accommodation pool (pool counts BOOKED/ATTENDED only) |  | `CapacityDemotionScenariosTest#demotedExtraTimeFreesPool` |
| TD-10 | 033 cut with WAITLISTED but BOOKED <= new capacity: no demotion, waitlist untouched |  | `CapacityDemotionScenariosTest#noDemotionWhenBookedFits` |

#### RescheduleScenariosTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| RS-01 | 023 same exam, target has seats: old -> CANCELLED 'Rescheduled', new BOOKED id on target (PORTAL, accommodation copied), counters -1/+1 | HB-10, MR-09 | `RescheduleScenariosTest#moveToSessionWithSeats` |
| RS-02 | 023 target full: confirmed seat is given up, new booking WAITLISTED (no error) | HB-10, MR-09 | `RescheduleScenariosTest#moveIntoFullSessionLosesSeat` |
| RS-03 | 023 same session accepted: old CANCELLED, new BOOKED id, counter net unchanged | HB-10 | `RescheduleScenariosTest#sameSession` |
| RS-04 | 023 state 6 (same-exam guard): target session of a different exam -> 50006, nothing changes |  | `RescheduleScenariosTest#differentExam` |
| RS-05 | 023 state 6: unknown booking -> 50006 (not 50004) |  | `RescheduleScenariosTest#unknownBooking` |
| RS-06 | 023 state 6: unknown target session -> 50006 |  | `RescheduleScenariosTest#unknownTarget` |
| RS-07 | 023 nested 021 state 4: ATTENDED booking -> 50004, whole transaction rolled back | HB-15 | `RescheduleScenariosTest#attendedBookingCannotMove` |
| RS-08 | 023 nested 021 state 4: CANCELLED booking -> 50004, no new booking | HB-15 | `RescheduleScenariosTest#cancelledBookingCannotMove` |
| RS-09 | 023 nested 020 state 1: target not OPEN -> 50001, the cancel is rolled back (old booking still BOOKED) | HB-15 | `RescheduleScenariosTest#targetNotOpenRollsBackCancel` |
| RS-10 | 023 nested 020 state 2: candidate already active on target -> 50002, rolled back | HB-15, HB-21 | `RescheduleScenariosTest#duplicateOnTargetRollsBack` |
| RS-11 | 023 nested 020 state 3: ET booking to a target without accommodation seat -> 50003, rolled back | HB-15 | `RescheduleScenariosTest#extraTimeTargetPoolRollsBack` |
| RS-12 | 023 WAITLISTED booking: old Waitlist row deleted (no seat released), new booking on target | HB-06 | `RescheduleScenariosTest#moveWaitlistedBooking` |
| RS-13 | 023 partner booking: comes back as PORTAL with ExternalRef dropped; source keeps HB-01 drift | HB-10, HB-01, MR-09 | `RescheduleScenariosTest#partnerBookingBecomesPortal` |
| RS-14 | 023 legacy SQL contract: Msg 50000 state 6 / re-raised nested state 4; cancel audit actor is the SQL login | HB-14, HB-15, HB-17 | `RescheduleScenariosTest#legacyErrorContract` |

#### BookingSeatSyncTriggerTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| T32-01 | 032 INSERT BOOKED Channel=PARTNER -> SeatsAvailable -1 (single direct insert, no drift) | HB-04 | `BookingSeatSyncTriggerTest#partnerInsertDecrements` |
| T32-02 | 032 INSERT BOOKED Channel=IMPORT -> SeatsAvailable -1 | HB-04 | `BookingSeatSyncTriggerTest#importInsertDecrements` |
| T32-03 | 032 INSERT with default Channel (PORTAL) -> no decrement; drift -1 | HB-04 | `BookingSeatSyncTriggerTest#defaultChannelSkipped` |
| T32-04 | 032 INSERT WAITLISTED Channel=PARTNER -> no change | HB-04 | `BookingSeatSyncTriggerTest#waitlistedInsertSkipped` |
| T32-05 | 032 UPDATE WAITLISTED -> BOOKED (promotion) -> no change; drift -1 | HB-04 | `BookingSeatSyncTriggerTest#updateToBookedSkipped` |
| T32-06 | 032 UPDATE BOOKED -> CANCELLED -> no change; drift +1 | HB-04 | `BookingSeatSyncTriggerTest#updateToCancelledSkipped` |
| T32-07 | 032 DELETE of a BOOKED row -> no change; drift +1 | HB-04 | `BookingSeatSyncTriggerTest#deleteSkipped` |
| T32-08 | 032 multi-row INSERT is grouped per SessionId (-2 / -1) | HB-04 | `BookingSeatSyncTriggerTest#multiRowInsertGrouped` |
| T32-09 | 032 no floor: import into a full session drives SeatsAvailable negative | HB-04, HB-28 | `BookingSeatSyncTriggerTest#importIntoFullGoesNegative` |
| T32-10 | 032 + booking-api decrement: a partner booking takes two seats (drift +1) | HB-01, MR-01 | `BookingSeatSyncTriggerTest#partnerDoubleDecrement` |
| T32-11 | 032 skips usp_BookSeat inserts (Channel=PORTAL): portal booking moves the counter by exactly 1 | HB-01 | `BookingSeatSyncTriggerTest#portalProcSingleDecrement` |

#### ExamSessionUpdateTriggerTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| T33-01 | 033 every UPDATE overwrites LastModified with GETDATE() (explicit value discarded) | HB-16, MR-14 | `ExamSessionUpdateTriggerTest#lastModifiedOverwritten` |
| T33-02 | 033 direct UPDATE Capacity below BOOKED demotes newest and recounts (no proc, no audit) | HB-05, MR-04 | `ExamSessionUpdateTriggerTest#directCapacityCutDemotes` |
| T33-03 | 033 multi-row UPDATE: demotion and positions computed per session | HB-05 | `ExamSessionUpdateTriggerTest#multiSessionUpdate` |
| T33-04 | 033 direct capacity raise: counter untouched -> drift +delta | MR-01, HB-03 | `ExamSessionUpdateTriggerTest#directRaiseLeavesCounter` |
| T33-05 | 033 direct cut that needs no demotion: counter untouched -> negative drift | MR-01, HB-03 | `ExamSessionUpdateTriggerTest#directCutWithoutDemotion` |
| T33-06 | 033 recursion guard: the trigger's own ExamSession UPDATEs do not re-run demotion (single pass) | HB-05 | `ExamSessionUpdateTriggerTest#singlePass` |

#### ConcurrencyScenariosTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| CX-01 | 020 UPDLOCK/HOLDLOCK last-seat race: N bookers, 1 seat -> exactly 1 BOOKED, N-1 WAITLISTED with distinct positions 1..N-1, counter 0 | MR-05, HB-29 | `ConcurrencyScenariosTest#lastSeatRace` |
| CX-02 | 020 waitlist race on a full session: positions 1..N, no duplicates (contrast: partner path HB-29) | HB-29, MR-05 | `ConcurrencyScenariosTest#waitlistPositionRace` |
| CX-03 | 020 same candidate N times in parallel: exactly 1 BOOKED, N-1 x 50002 (duplicate check under the session lock) | HB-21 | `ConcurrencyScenariosTest#duplicateRace` |
| CX-04 | 020 accommodation race: pool of 1, N parallel ET25 bookers -> 1 BOOKED, N-1 x 50003 |  | `ConcurrencyScenariosTest#accommodationRace` |
| CX-05 | 020 vs 022/033 booking during a capacity cut (10 -> 5, 9 booked): serialised; always 5 BOOKED, counter 0, dense waitlist | HB-05, MR-04, MR-05 | `ConcurrencyScenariosTest#bookingDuringCapacityCut` |
| CX-06 | 021 vs 020 cancel racing bookers on a full session: freed seat goes to at most one booker, no promotion, counter consistent | HB-06 | `ConcurrencyScenariosTest#cancelVersusBook` |
| CX-07 | 022 raise racing bookers on a full session: at most 2 new BOOKED, waitlisted never promoted, counter consistent | HB-06 | `ConcurrencyScenariosTest#capacityRaiseVersusBook` |
| CX-08 | 023 crossing reschedules A->B / B->A: each is atomic; deadlock victims surface as 50000 system errors (HB-14) with nothing written | HB-14, HB-15, MR-13 | `ConcurrencyScenariosTest#crossingReschedules` |

#### SeatDriftScenariosTest

| ID | Proc/trigger branch -> expected outcome | HB/MR | Test method |
|---|---|---|---|
| SD-01 | seat-drift.sql on the freshly provisioned seed (db/apply.sh incl. 049_seed_finalise) returns no rows | HB-30 | `SeatDriftScenariosTest#seedBaselineIsClean` |
| SD-02 | target drift check (legacy: seat-drift.sql) and the state-derived check agree row for row | MR-02 | `SeatDriftScenariosTest#sqlCheckMatchesStateDerivedCheck` |
| SD-03 | seat-drift.sql row shape: ActiveBookings = BOOKED + ATTENDED, Drift = (Capacity - Active) - SeatsAvailable (+1 per partner booking) | HB-01 | `SeatDriftScenariosTest#driftRowShape` |
| SD-04 | capacity-sync reconciler formula Capacity - AccommodationCapacity - standard bookings rewrites a correct counter -> drift +AccCap-ET | HB-03, MR-02, MR-17 | `SeatDriftScenariosTest#reconcilerFormulaCreatesDrift` |
| SD-05 | capacity-sync reconciler repairs partner drift when AccommodationCapacity = 0 (masks HB-01) | HB-01, HB-03, MR-17 | `SeatDriftScenariosTest#reconcilerMasksPartnerDrift` |
| SD-06 | capacity-sync reconciler only touches OPEN sessions: drift on a CLOSED session survives | HB-03, HB-20 | `SeatDriftScenariosTest#reconcilerSkipsNonOpen` |
