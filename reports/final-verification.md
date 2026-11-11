# MBA-3825 final verification (Windows), M6b follow-up

Base SHA (`origin/assessment-delivery-demo`, fetched right before branching): `e45ee7a21c89eee0366dea6032dcd75b69d2af59`.
Legacy baseline: tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f` (separate worktree `../SampleMvcWebApp-legacy`).
Branch under test: `devin/mba-3825-zero-mismatch`; the runtime code of the replay and drift runs is `0d73ab1` (later commits change
only docs, reports, `characterization/modern-classification.yaml` and the gateway functional script). VM: Windows Server 2022,
Temurin 8u472 / 21.0.9, Maven 3.8.8 (plain `mvn` and the wrapper, via the Maven Central GCS mirror), VS 2022 Build Tools,
Docker (Linux containers, so the Testcontainers ITs ran), SQL Server 2022 (`MSSQLSERVER` instance on localhost,1433).

| # | Suite | Command (from repo root unless stated) | Result |
|---|---|---|---|
| 1 | .NET solution build | `nuget restore AssessmentDelivery.sln -PackagesDirectory packages` + `MSBuild.exe AssessmentDelivery.sln /p:Configuration=Debug /v:m` | exit 0, 0 errors |
| 2 | NUnit | `packages/NUnit.ConsoleRunner.3.16.3/tools/nunit3-console.exe AssessmentDelivery.Tests/bin/Debug/AssessmentDelivery.Tests.dll` | **26/26 passed** |
| 3 | Java 8 services | `cd services && JAVA_HOME=<jdk8> mvnw.cmd -B verify` | BUILD SUCCESS: booking-api 9, results-service 4, capacity-sync 3 = **16/16** |
| 4 | scheduling-service (unit + JaCoCo gate + vitest + Testcontainers ITs) | `cd services && JAVA_HOME=<jdk21> mvn -B -f scheduling-service/pom.xml verify` | BUILD SUCCESS: **53/53** unit, **8/8** vitest, **22/22** ITs (`SchedulingApiIT` 12, `SingleWriterRelayIT` 10) |
| 5 | routing-gateway unit + integration | `cd services && JAVA_HOME=<jdk21> mvn -B -f routing-gateway/pom.xml verify` | BUILD SUCCESS, **62/62** |
| 6 | routing-gateway functional suite | see "Gateway functional suite" below | **30/30 passed** with `SCHEDULING=real` (real scheduling-service + relay) and **30/30** with `SCHEDULING=stub` (`scheduling_stub.py`, no relay), Git Bash on Windows |
| 7 | characterization unit tests | `cd characterization && PYTHONUTF8=1 python -m pytest -q` | **31 passed**, 3 deselected (live-estate tests, covered by 8/9) |
| 8 | characterization replay, legacy | `characterization/replay --target legacy --estate-dir ../../SampleMvcWebApp-legacy` (estate from the `legacy-baseline` worktree) | **86/86 MATCH** (`characterization/reports/replay-legacy-e458238.md`) |
| 9 | characterization replay, modern via :8000 | `characterization/replay --target modern --base-url http://localhost:8000 --report parity-e45ee7a.md` | **63/86 MATCH; 17 ADR-APPROVED DIVERGENCE; 1 KNOWN_ONCALL_DEFECT (#57); 0 MISMATCH; 5 NOT REPLAYABLE; 0 SKIPPED; 0 ERROR** (`characterization/reports/parity-e45ee7a.md`, `reports/parity.md`); none of the 57 earlier MATCH rows regressed |
| 10 | M4 drift before/after + reconciliation | `powershell -ExecutionPolicy Bypass -File scripts\drift-before-after.ps1 -BeforeDir ..\SampleMvcWebApp-legacy -SkipBuild` | before **112** seat-drift rows, after **0**, reconciliation **0** mismatches (`reports/drift-before-after.md`) |

## Changes in this PR

- **Blank cancel reason** (`SchedulingService.cancel`): a blank or whitespace reason is stored as NULL (outbox payload
  `{"reason": null}`, relay projects NULL into `Booking.CancelReason`), as the portal's `usp_CancelBooking @Reason = NULL` did.
  Partner v1 cancels keep `"Cancelled by partner"` (booking-api and the gateway translation send it, as legacy booking-api did).
  Reschedule keeps `'Rescheduled'`. `/app` already renders the reason only when present.
- **Reschedule ExternalRef**: `POST /api/v2/bookings/{id}/reschedule` takes an optional `channel` (default `PORTAL`); the
  replacement booking copies `ExternalRef` only for `PARTNER`, which the gateway v1 translation and booking-api now send.
- **Negative capacity**: `SessionCapacity.changeCapacity(-1)` answers 400 `VALIDATION` without `legacyCode` (the portal rejected
  it before `usp_ChangeCapacity`). The #57 floor (`AccommodationFloorPolicy`, `floorPolicy.check(...)`) is untouched.
- **Harness, general portal-channel mapping** (`characterization/chartool/adapters_modern.py`): an uncoded 400 `VALIDATION` on
  the PORTAL channel is reported as `PORTAL_ERROR`, the legacy portal's generic page-error vocabulary. No comparator change.
- **ADR 0002 section 7** + `modern-classification.yaml` entry for `mixed_traffic_then_capacity_sync` (restricted fields).
- **Gateway functional suite** updated to the M4/M5 single-writer contract.

## Gateway functional suite (Windows, Git Bash)

`run-functional-tests.sh` is Linux-only (`ss`, `/opt/mssql-tools18`, `scripts/run-legacy.sh`), so on Windows the same steps were run
by hand with the M6 PowerShell scripts:

1. Fresh state: `scripts/run-modern.ps1 -Stop`; drop the `Scheduling` database (as the Linux runner does);
   `scripts/run-legacy.ps1 -SkipBuild` (fresh seed, DENYs) then `-Stop` and `-SkipBuild -SkipDb -NoPortal` (legacy services only,
   :8080 free for `portal_stub.py`); `scripts/run-modern.ps1 -SkipBuild -NoGateway` (scheduling-service :8090, import + relay).
2. `python portal_stub.py 8080`; startup guard: `java -jar target/routing-gateway.jar --server.port=0 --routing.modern-centers=NYC02`
   must exit with "Refusing to start"; then the gateway with `--routing.config-file=target/functional/routing.properties`.
3. `SQLCMD="C:/Program Files/SqlCmd/sqlcmd.exe" SCHEDULING=real AD_DB_USER=ad_portal AD_DB_PASSWORD=... PYTHONUTF8=1 python functional_test.py`.

Result: 30 passed, 0 failed (startup guard, 16-request read transparency, direct-write refusal for the four legacy logins,
retired procs absent, v1 book/duplicate/not-open/validation/malformed JSON/unknown booking/reschedule/cancel through :8000,
allow-list hot reload and rollback, portal redirects, ownership by booking id, rejected refresh without relay).
The same steps with `scheduling_stub.py 8090` in place of scheduling-service and `SCHEDULING=stub` also gave 30 passed, 0 failed
(in stub mode the C1/C2 relay checks assert that no `dbo.Booking` row appears, since the stub has no relay).
The Linux runner was updated the same way (post-M4 `ad_portal` read login default, relay-shaped allow-list file) but was not run
on Linux in this session.
