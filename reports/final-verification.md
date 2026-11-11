# MBA-3825 final verification (Windows)

Final head SHA (`origin/assessment-delivery-demo`, fetched right before branching): `e16030c933e221a4a0fcb52c99872a2708db765f`.
Legacy baseline: tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f` (separate worktree `../SampleMvcWebApp-legacy-baseline`).
Branch under test: `devin/mba-3825-parity-proof` at `0eb7550` (code under test; later commits on the branch add only reports) (final head + the harness/test changes and the
one scheduling-service fix listed below). VM: Windows Server 2022, Temurin 8u472 / 21.0.9, Maven 3.9.9,
VS 2022 Build Tools (MSBuild 17), Docker 29.6.1 (Linux containers, so Testcontainers ITs ran), SQL Server 2022 container.

Suites ran in a clean worktree of the branch head (`git worktree add ../SampleMvcWebApp-verify`) so that builds never
overwrote jars of the running estate.

| # | Suite | Command (from repo root unless stated) | Result |
|---|---|---|---|
| 1 | .NET solution build | `MSBuild.exe AssessmentDelivery.sln -restore -p:Configuration=Debug -v:m` | exit 0, 0 errors |
| 2 | NUnit | `packages/NUnit.ConsoleRunner.3.16.3/tools/nunit3-console.exe AssessmentDelivery.Tests/bin/Debug/AssessmentDelivery.Tests.dll` | **26/26 passed**, 0 failed, 0 skipped |
| 3 | Java 8 services (booking-api, results-service, capacity-sync) | `cd services && JAVA_HOME=<jdk8> mvnw.cmd -B verify` | BUILD SUCCESS: booking-api 9, results-service 4, capacity-sync 3 = **16/16** |
| 4 | scheduling-service (unit + JaCoCo gate + vitest + Testcontainers ITs) | `cd services/scheduling-service && JAVA_HOME=<jdk21> mvn -B verify` | BUILD SUCCESS at `0eb7550`: **53/53** unit, JaCoCo gate met, **8/8** vitest, **21/21** Testcontainers ITs |
| 5 | routing-gateway unit + integration | `cd services/routing-gateway && JAVA_HOME=<jdk21> mvn -B verify` | BUILD SUCCESS, **62/62** (on Windows, after the test-only CRLF normalization below) |
| 6 | routing-gateway functional suite | `services/routing-gateway/functional/run-functional-tests.sh` | **Not run on Windows**: the script is Linux-only (`ss`, `db/apply.sh`, `scripts/run-scheduling-service.sh`, `/usr/lib/jvm` defaults) and needs :8000/:8080 free, i.e. the whole estate down. Coordinator to re-run on Linux. |
| 7 | characterization unit tests | `PYTHONUTF8=1 python -m pytest -q characterization/tests` | **30 passed**, 3 deselected (need a live estate; covered by 8/9) |
| 8 | characterization replay, legacy | `characterization/replay --target legacy --base-url http://localhost:8080` (estate from the `legacy-baseline` worktree) | **86/86 MATCH** (`characterization/reports/replay-legacy-e458238.md`) |
| 9 | characterization replay, modern via :8000 | `characterization/replay --target modern --base-url http://localhost:8000 --report parity-e16030c.md` | **57/86 MATCH**; 16 ADR-APPROVED, 1 KNOWN_ONCALL_DEFECT (#57), 7 MISMATCH, 5 NOT REPLAYABLE, 0 SKIPPED, 0 ERROR (`characterization/reports/parity-e16030c.md`, `reports/parity.md`) |
| 10 | M4 drift before/after + reconciliation | `powershell -ExecutionPolicy Bypass -File scripts\drift-before-after.ps1` | before **112** seat-drift rows, after **0**, reconciliation **0** mismatches (`reports/drift-before-after.md`) |
| 11 | M4 relay / single-writer ITs | part of 4 (`SingleWriterRelayIT`, `LegacyProjectorTest`, `ReportFormatterTest`) | see 4 |
| 12 | M5 retirement (legacy booking URLs 302 to `/app`) | NUnit (2) + browser walk step 9 | see 2 and the browser evidence |

## Changes in this PR

- **Bug fix (scheduling-service)**: `SessionCapacity.changeCapacity` counted ATTENDED bookings when it decided how many
  bookings a capacity cut bumps. `db/triggers/033_trg_ExamSession_Update.sql` (legacy-baseline, lines 34-44) bumps
  `cnt - Capacity` rows where `cnt` counts `Status = 'BOOKED'` only, and ADR 0002 section 2 says the modern service
  reproduces that bump. The excess now comes from a separate `bumpableCount()` helper; `seatsHeld()`,
  `AccommodationFloorPolicy` and the `floorPolicy.check(...)` arguments (on-call defect #57) are untouched.
  Tests: `SessionCapacityTest.trg_ExamSession_Update_onlyBookedCountAgainstNewCapacity_deliveredSessionWithAttendedBumpsNobody`
  (new), `trg_ExamSession_Update_attendedHoldSeatsButAreNeverBumped` (M2 test, not a #57 test: its cut went to 1 seat
  with 1 BOOKED, which the legacy trigger does not bump; it now cuts to 0 to keep asserting that ATTENDED are never
  bumped), and `SessionCapacityTest.onCallDefect57_floorStillCountsAttended_30To24Rejected_bumpCountUnaffected`
  (guardrail: the #57 ALLOCATED floor still counts ATTENDED, so the 30 -> 24 shape still answers
  `CAPACITY_BELOW_ACCOMMODATION_FLOOR` / 50005; the existing #57 tests in `SessionCapacityTest` and `SchedulingApiIT`
  are unchanged and green). Session ids 41872/41875 do not exist in the deterministic seed (`ExamSession` ids 1-462),
  so the guardrail reproduces the shape instead of the id. Replay scenario fixed:
  `capacity_decrease_delivered_with_attended_admin`.
- **Harness**: the modern replay drains the M4 outbox relay after every step and maps modern booking ids
  (>= 50000000) to their projected `AssessmentDelivery` ids via `RelayBookingMap` before reading Booking/Waitlist
  state; the modern reset also clears `RelayBookingMap`/`RelayCheckpoint`. Report labels follow the story
  (`ADR-APPROVED DIVERGENCE`, `KNOWN_ONCALL_DEFECT (#57)`, `NOT REPLAYABLE (retired legacy surface)`).
  `characterization/modern-classification.yaml` lists ADR 0002 divergences (section cited, allowed diff fields
  restricted), retired surfaces, and root causes of the remaining mismatches. Tests: `characterization/tests/test_runner.py`.
- **Windows tooling**: `scripts/run-modern.ps1`, `scripts/drift-before-after.ps1`, gateway-routed
  `scripts/mixed-traffic.ps1`.
- **Test-only**: `V1ShapesTest` / `GatewayIntegrationTest` compare JSON with `\r\n` normalized to `\n` (Jackson's
  pretty printer uses the platform line separator). No runtime change.

- **Devin Review fixes**: `run-modern.ps1 -Stop` stops only `java` processes; `drift-before-after.ps1` requires bookings
  on both channels in both runs; ADR field rules accept globs, so ADR 0002 section 1 entries allow only
  `db_state.examSession.*.SeatsAvailable` (test: `test_adr_field_globs_only_accept_the_listed_session_column`).

## Deviations

- Gateway functional suite (row 6) not run on Windows, see above.
- The drift "after" run used evidence checkout `647989c` (final head + harness), before the bump fix; the drift
  workload contains no capacity reductions, so that fix cannot change it.
