# scheduling-contract-tests (Phase 0, Session 2)

These tests pin the current behaviour of everything that crosses the future scheduling-service boundary, so Phase 2 can show that the rewired callers behave the same. Inventory: [`docs/to-be/scheduling-boundary.md`](../../docs/to-be/scheduling-boundary.md). Contract: [`docs/to-be/scheduling-service-openapi.yaml`](../../docs/to-be/scheduling-service-openapi.yaml) and [`docs/to-be/scheduling-service-contract.md`](../../docs/to-be/scheduling-service-contract.md).

This is a standalone Java 21 Maven module, kept outside the Java 8 `services/pom.xml` aggregator.

## Run

```bash
services/scheduling-contract-tests/run-contract-tests.sh
```

- The script builds the legacy jars with `services/mvnw` under JDK 8 if they are missing. Set `REBUILD_LEGACY=1` to force a rebuild.
- It then runs `mvn test` under JDK 21.
- Requirements: Docker, plus JDK 8 and JDK 21 (override the paths with `JAVA8_HOME`/`JAVA21_HOME`).
- Optional: `mono` + `mcs` to execute the real portal `RetakeRules.cs`.
- Maven Central rate-limits: use the GCS mirror (`MVNW_REPOURL`, `~/.m2/settings.xml`).

### Targets

The same tests prove legacy and migrated behaviour match. Select with `-Dcontract.target=legacy|scheduling` (or `CONTRACT_TARGET=...` for the script); default `legacy`.

```bash
services/scheduling-contract-tests/run-contract-tests.sh                                 # legacy (run on migration/phase0 or estate code)
CONTRACT_TARGET=scheduling services/scheduling-contract-tests/run-contract-tests.sh      # rewired callers + real scheduling-service
```

- **legacy**: as described below; ExamSession/Booking/Waitlist fixtures and assertions use `AssessmentDelivery`.
- **scheduling**: the container also gets the `Scheduling` DB (`services/scheduling-service/db/bootstrap.sql`), the real `scheduling-service.jar` runs under JDK 21 (import off, relay polling off, reconciler cron off, `scheduling.legacy-seat-accounting=false`), and booking-api/results-service start with `--scheduling.base-url` pointing at it. Reference rows are still written to `AssessmentDelivery` and replicated with `POST /reference/sync`; ExamSession/Booking/Waitlist fixtures, their assertions (`LegacyEstate.sched`) and the `Attempt` twin of each `Result` live in `Scheduling`.

Per-target differences (assertions are otherwise identical):

| Where | legacy | scheduling | Why |
|---|---|---|---|
| `BookingApiContractTest` seat counts after a PARTNER BOOKED row | -2 (DAO + trigger 032) | -1 | HB-01 removed on purpose: scheduling-service is the single writer (contract section 6) |
| `PortalReadContractTest` | runs | skipped | portal EF reads of `AssessmentDelivery` no longer exist once Session 4 rewires the portal; Windows parity checks in `docs/to-be/portal-contract-spec.md` |
| `RetakeEligibilityContractTest` portal policy | portal SQL + Java/Mono `RetakeRules` | `GET /eligibility/retake?policy=PORTAL_CALENDAR_DAYS` (`portalViaSchedulingService`) | ADR-001: the portal asks scheduling-service; the expected values in `retake-cases.psv` are unchanged |

`BookingApiContractTest` on the scheduling target needs Session 4's booking-api rewiring (run it on `migration/phase2`).

## Harness

- `support/LegacyEstate` starts **one** SQL Server 2022 container per JVM. It runs `db/apply.sh --no-seed` inside the container (schema, the five procs, all triggers), then launches the **real** legacy `booking-api.jar` and `results-service.jar` under Java 8 on free ports, pointed at the container as `ad_app`. Logs go to `target/legacy-logs/`.
- Every test creates its own fixtures (`support/Fixtures`), so no seed rows and no wall-clock drift are involved. Future and past are relative to `now()`, and retake cases use fixed dates with an explicit `date` parameter.
- The tests use Testcontainers 1.21.4 with `src/test/resources/docker-java.properties` (`api.version=1.44`) for Docker Engine 29.

| Class | Pins |
|---|---|
| `ResultsAttendContractTest` | results-service ingest -> `Booking.Status='ATTENDED'`: qualifying statuses, idempotent replay, conflicts, CANCELLED/WAITLISTED rejection, future and cancelled sessions (HB-18), frozen `Passed`, score-file partial success and booking resolution (HB-19), seat-drift unaffected |
| `BookingApiContractTest` | booking-api session and booking JSON shapes and values, no status filter (HB-13), ordering, 404 body, partner double decrement and single release (HB-01/HB-02), waitlist `MAX+1`, partner error codes |
| `PortalReadContractTest` | portal EF6 reads as SQL twins (`PortalQueries`): open-session search (HB-31), session detail, reschedule targets, candidate bookings, admin counts, roster export; proc error surface 50000/16/state 1-6 (HB-14) |
| `RetakeEligibilityContractTest` | 10 shared cases (`retake-cases.psv`) through booking-api HTTP (working days), the portal query plus a Java transliteration of `RetakeRules`, and the portal query plus the **real `RetakeRules.cs` under Mono** (calendar days): HB-07 |
| `InventoryGuardTest` | every cited file:line in the inventory still holds the same SQL/LINQ (`inventory-guard.psv`). Phase 2 rewiring is expected to trip it: update the inventory with the change |

## Limitation: the .NET portal

The portal targets .NET Framework 4.8 and runs only on Windows, so this suite does **not** run the portal's C# data-access code. What it does run:

- SQL equivalents of its LINQ queries.
- The portal's real retake rule, compiled from source with Mono.
- The proc error surface the portal maps to messages.

EF's generated SQL, the controllers, `SqlErrorHelper`, and the blank-accommodation default are not exercised. [`docs/to-be/portal-contract-spec.md`](../../docs/to-be/portal-contract-spec.md) lists the matching checks to run on Windows in Phase 2. If `mono`/`mcs` are absent, `portalRealCSharp` is reported as *skipped*.
