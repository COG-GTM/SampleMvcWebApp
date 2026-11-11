# Characterization transcripts and replay harness (MBA-3820)

Golden transcripts of what the Northwind Assessment Network legacy estate does **today** (booking,
cancellation, reschedule, capacity, retake eligibility, accommodation, mixed traffic + capacity-sync),
odd behaviour included, plus a headless replay harness that proves later migration steps did not change
behaviour by accident.

- Captured from tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f`. The SHA is in every
  transcript (`legacy_baseline.sha`) and every report.
- 53 scenarios in `scenarios/*.yaml` -> 53 transcripts in `transcripts/<group>/<scenario>.json`.
- Odd behaviour is pinned, not fixed: see [`ODDITIES.md`](ODDITIES.md).
- Reports land in `reports/replay-<target>-<sha7>.md` with X/Y MATCH per scenario group.

## Layout

| Path | What |
|---|---|
| `scenarios/*.yaml` | Scenario catalogue: channel, setup SQL, steps (`book`, `cancel`, `reschedule`, `capacity`, `eligibility`, `drift`, `sync`, `mixed_traffic`), oddity notes |
| `capture`, `capture.cmd` | Headless capture tool: runs every scenario against the running legacy estate and writes the transcripts |
| `replay`, `replay.cmd` | Replay harness: `--target legacy` or `--target modern --base-url ...` |
| `chartool/` | Python package (adapters per channel, DB snapshot/restore, normalizer, comparer, report) |
| `tests/` | pytest: unit tests (offline) and functional tests (`-m integration`, need the running estate) |

## How a scenario runs

1. **Known state.** `capture --fresh-seed` runs `db/apply.ps1` (Windows) / `db/apply.sh` (Linux), which
   drops and re-seeds `AssessmentDelivery` from `db/seed/040`-`049` (seed "now" = 2026-11-10), then copies
   it to `AssessmentDelivery_CharBaseline`. Before **every** scenario the harness restores the
   `AssessmentDelivery` tables from that pristine copy (same rows, same identity seeds; ~1 s instead of
   ~40 s for a rebuild). Without `--fresh-seed` an existing pristine copy is reused (it is created on
   first use).
2. **Setup SQL** from the scenario runs (e.g. inserting a failed attempt for a retake candidate).
3. **Steps** go through the channel adapter:
   - `PORTAL`: real form posts to the .NET portal on :8080 (`GET` the form, keep the antiforgery cookie
     and `__RequestVerificationToken`, `POST /Booking/Book`, `/Booking/Cancel/{id}`,
     `/Booking/Reschedule/{id}`, `/Admin/Sessions/Capacity/{id}`, `GET /Eligibility?...`). Status, the
     PRG redirect, the flash message, validation errors and rendered fields are recorded.
   - `PARTNER`: booking-api v1 REST on :8081 (`POST /api/v1/bookings`, `DELETE /api/v1/bookings/{id}`,
     `POST /api/v1/bookings/{id}/reschedule`, `GET /api/v1/candidates/{n}/eligibility`).
   - `SYSTEM`: `scripts/mixed-traffic.ps1|.sh` (fixed `SEED`, defaults 20/20/3/4), `POST :8083/sync/run`,
     and `db/checks/seat-drift.sql`.
4. **DB snapshot** of the affected `ExamSession` rows (Capacity, AccommodationCapacity, SeatsAvailable,
   Status), **every** `Booking` and `Waitlist` row on those sessions, the candidates' `Result` rows, and
   the `AuditLog` rows written by the scenario; taken before and after.
5. **Normalization.** Seed booking ids are kept (they are deterministic); ids created by setup SQL become
   `setup#n`, ids created by the steps become `new#n` (also inside URLs, flash messages and modern
   50,000,000+ ids); `GETDATE()` timestamps written during the scenario become `<now>`; fractional
   seconds are dropped. Portal messages are mapped to booking-api error codes and `usp_*` states
   (50001 not open, 50002 duplicate, 50003 no accommodation seat, 50005 invalid capacity, 50006 different
   exam) so both channels expose the same `business_fields`.

## Windows setup (exactly what was used for the committed transcripts)

Windows Server 2022, Git Bash, PowerShell 5.1.

1. **SQL Server**: SQL Server 2022 **Express** (default instance) on `localhost,1433`, SQL authentication,
   `sa` password from `AD_SA_PASSWORD` (the repo's dev default is used when unset; see the root README).
   `scripts/run-legacy.ps1` installs Express if no instance exists, enables TCP 1433 and mixed mode.
   `sqlcmd` (go-sqlcmd) at `C:\Program Files\SqlCmd\sqlcmd.exe`; the harness finds it there, on `PATH`,
   or via `SQLCMD`. ODBC: the built-in `SQL Server` driver is enough (`ODBC Driver 17/18` are preferred if
   present; override with `CHAR_ODBC_DRIVER`).
2. **Java**: JDK 8 for the legacy services (Temurin 8 at
   `C:\Program Files\Eclipse Adoptium\jdk-8.0.472.8-hotspot`); JDK 21 only for scheduling-service.
   Maven Central rate-limits: `MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2`
   and a mirror to the same URL in `%USERPROFILE%\.m2\settings.xml`.
3. **Python 3.10+** (3.14 used) and the harness requirements:
   ```bash
   python -m pip install -r characterization/requirements.txt   # pyodbc, PyYAML, requests, pytest
   ```
   In Git Bash set `PYTHONUTF8=1`.
4. **Start the legacy estate at `legacy-baseline`** (a separate worktree keeps the baseline pristine):
   ```bash
   git fetch origin --tags
   git worktree add ../SampleMvcWebApp-legacy-baseline legacy-baseline
   cd ../SampleMvcWebApp-legacy-baseline
   powershell -ExecutionPolicy Bypass -File scripts/run-legacy.ps1          # SQL Server, db/apply, :8081-8083, portal :8080
   powershell -ExecutionPolicy Bypass -File scripts/run-legacy.ps1 -Status  # all five should be UP
   ```
   (Use forward slashes for script paths from Git Bash.) The portal alone: `scripts/run-portal.ps1`
   (MSBuild + IIS Express on :8080).
5. **Capture / replay** from this branch:
   ```bash
   cd characterization
   ./capture --fresh-seed                     # rebuild via db/apply, then capture all scenarios (~4 min)
   git diff --exit-code transcripts           # determinism check after a second capture
   ./replay --target legacy --estate-dir ../../SampleMvcWebApp-legacy-baseline
   ./replay --only 'retake_*' book_open_session_portal   # subsets: names, globs or group names
   ```
   `capture.cmd` / `replay.cmd` do the same from `cmd.exe`/PowerShell.

On Linux the same commands work with `scripts/run-legacy.sh` (SQL Server in Docker) and the portal on a
Windows host via `PORTAL_URL`.

### Configuration (environment variables)

| Variable | Default |
|---|---|
| `AD_SQL_SERVER` / `AD_SA_PASSWORD` | `localhost,1433` / repo dev default |
| `AD_DATABASE`, `CHAR_BASELINE_DB`, `SCHEDULING_DATABASE` | `AssessmentDelivery`, `AssessmentDelivery_CharBaseline`, `Scheduling` |
| `PORTAL_URL`, `BOOKING_API_URL`, `CAPACITY_SYNC_URL` | `http://localhost:8080`, `:8081`, `:8083` |
| `SQLCMD`, `CHAR_ODBC_DRIVER` | auto-detected |

## Replay

```bash
./replay --target legacy                                        # -> reports/replay-legacy-e458238.md
./replay --target modern --base-url http://localhost:8090       # scheduling-service directly
./replay --target modern --base-url http://localhost:8000       # routing gateway (M3), centers allow-listed
./replay --target modern --base-url http://localhost:8090 --db-state scheduling
```

Exit code 0 only when every transcript matches. The report lists MATCH/MISMATCH/SKIPPED/ERROR per
scenario, X/Y MATCH per group (booking, full session, duplicate/closed, cancellation, reschedule, capacity
increase, capacity decrease, retake eligibility per channel, accommodation, mixed traffic) and, for every
mismatch, the request, the expected and actual business fields / DB state, the diff, and both SHAs
(legacy baseline and the estate under test).

**Legacy target** compares status, normalized response, business fields and DB state exactly.

**Modern target** (`--target modern`) uses per-channel adapters:

| Channel | `--base-url` = scheduling-service (:8090) | `--base-url` = routing gateway (:8000) |
|---|---|---|
| PORTAL | form post mapped to `/api/v2` (`POST /bookings`, `DELETE /bookings/{id}?reason=`, `POST /bookings/{id}/reschedule`, `PUT /sessions/{id}/capacity`, `GET /candidates/{n}/eligibility`), `channel: PORTAL` | same `/api/v2` calls through the gateway |
| PARTNER | `/api/v2` with `channel: PARTNER` | booking-api v1 request **unchanged** (gateway translates) |
| SYSTEM | mixed traffic / capacity-sync reported `SKIPPED` (scheduling-service has no capacity-sync) | traffic script pointed at the gateway |

`--partner-api auto` (default) probes `GET /api/v1/sessions` on the base URL to tell a gateway from
scheduling-service. Only channel-independent fields are compared: outcome (BOOKED / WAITLISTED / ERROR /
...), `legacyCode` (the `usp_*` state; error *names* and HTTP statuses may differ by channel), seats,
waitlist position, eligibility fields, bumped bookings, plus the normalized DB state.

DB state: `--db-state legacy` (default) reads `AssessmentDelivery` (needs the M4 outbox relay to project
modern writes back); `--db-state scheduling` reads `Scheduling` (`exam_session`, `booking`,
`waitlist_entry`) and normalizes it into the legacy shape. Before each scenario the harness restores
`AssessmentDelivery`, clears the scheduling-service booking tables and calls
`POST /internal/import/sessions` so scheduling-service starts from the same seed; `--modern-reset-cmd`
substitutes another re-sync. Deliberate differences are listed per transcript in
`modern_expected_mismatch` (from `docs/adr/0002-ported-rule-decisions.md`).

## Tests

```bash
cd characterization
python -m pytest -q                    # unit tests (offline) + catalogue/transcript consistency
python -m pytest -q -m integration     # functional: capture+replay a subset against the running estate
```
