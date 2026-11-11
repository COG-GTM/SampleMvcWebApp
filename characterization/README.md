# Characterization transcripts and replay harness (MBA-3820)

Golden transcripts of what the Northwind Assessment Network legacy estate does **today** (booking,
cancellation, reschedule, capacity, retake eligibility, accommodation, mixed traffic + capacity-sync),
odd behaviour included, plus a headless replay harness that proves later migration steps did not change
behaviour by accident.

- Captured from tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f`. The SHA is in every
  transcript (`legacy_baseline.sha`) and every report.
- 86 scenarios in `scenarios/*.yaml` -> 86 transcripts in `transcripts/<group>/<scenario>.json`; every one links
  the `docs/as-is/hidden-behavior.md` finding(s) it characterizes (`finding:`) and the checklist tests it covers
  (`tests:`). Catalogue with both columns: [`CATALOG.md`](CATALOG.md).
- Odd behaviour is pinned, not fixed: see [`ODDITIES.md`](ODDITIES.md).
- Reports land in `reports/replay-<target>-<sha7>.md` with X/Y MATCH per scenario group.

## Layout

| Path | What |
|---|---|
| `scenarios/*.yaml` | Scenario catalogue: channel, setup SQL, `finding`/`tests` ids, steps (`book`, `cancel`, `reschedule`, `capacity`, `eligibility`, `drift`, `sync`, `mixed_traffic`, `sql` direct T-SQL, `sessions`/`session` v1 reads, `search` portal search, `result` results-service ingest), oddity notes |
| `CATALOG.md` | Generated scenario catalogue (finding + test columns, CT-01..CT-38 coverage): `python -m chartool.catalog` |
| `known-defects.yaml` | Modern-target differences that are known and tracked (reported as KNOWN-DEFECT) |
| `capture`, `capture.cmd` | Headless capture tool: runs every scenario against the running legacy estate and writes the transcripts |
| `replay`, `replay.cmd` | Replay harness: `--target legacy` or `--target modern --base-url ...` |
| `chartool/` | Python package (adapters per channel, DB snapshot/restore, normalizer, comparer, report) |
| `tests/` | pytest: unit tests (offline) and functional tests (`-m integration`, need the running estate) |

## How a scenario runs

1. **Known state.** `capture --fresh-seed` runs `db/apply.ps1` (Windows) / `db/apply.sh` (Linux) **as they are
   at `legacy-baseline`** (the tag's `db/` and `scripts/` are extracted with `git archive` into the
   git-ignored `characterization/.legacy-baseline/`, or set `LEGACY_ROOT` to a checkout of the tag; later
   stories on this branch change both, e.g. M5 drops the booking procs). `scripts/mixed-traffic.*` and
   `db/checks/seat-drift.sql` come from the same tree. It then re-runs this branch's dev helper `db/security/035_sched_import_login.sql`
   (apply drops that database user; `--target modern` needs it to re-import). The rebuild
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
   - `SYSTEM`: `scripts/mixed-traffic.ps1|.sh` (fixed `SEED`, defaults 20/20/3/4; run from a `.seed-clock-` copy whose
     session window is `StartsAt > SEED_AS_OF` instead of `GETDATE()`, so the same sessions are picked on any day), `POST :8083/sync/run`,
     and `db/checks/seat-drift.sql`.
4. **DB snapshot** of the affected `ExamSession` rows (Capacity, AccommodationCapacity, SeatsAvailable,
   Status), **every** `Booking` and `Waitlist` row on those sessions, the candidates' `Result` rows, and
   the `AuditLog` rows written by the scenario; taken before and after.
5. **Normalization.** Seed booking ids are kept (they are deterministic); ids created by setup SQL become
   `setup#n`, ids created by the steps become `new#n` (also inside URLs, flash messages and modern
   50,000,000+ ids); `GETDATE()` timestamps written during the scenario become `<now>` (so do seed timestamps after the seed
   clock `SEED_AS_OF` = 2026-11-11, i.e. OPEN sessions' `StartsAt`, exactly as at capture time); fractional
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

`--target legacy` must point at an estate running from `legacy-baseline` (step 4 above): on the current
`assessment-delivery-demo` head M5 (#65) has dropped the booking procs and redirects the portal booking URLs to
`/app`, and M4 (#61) makes scheduling-service the single writer, so only the modern target is meaningful there.

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

## Coverage of the docs/as-is checklist (CT-01..CT-38)

`docs/as-is/migration-risks.md` section 2 lists 38 characterization tests for the 18 findings in
`docs/as-is/hidden-behavior.md`. Each scenario names the findings it pins (`finding: [HB-nn]`, copied into the
transcript) and the tests it covers (`tests: [CT-nn]`). The per-test transcript list is generated into
[`CATALOG.md`](CATALOG.md); `tests/test_scenarios_and_report.py` fails if a CT id has neither a transcript nor a
row below, or if `CATALOG.md` is stale. 36 of 38 tests have at least one transcript.

Notes on partial coverage (covered, but with a deterministic stand-in):

| Test | How it is covered |
|---|---|
| CT-08, CT-09 | True concurrency is not reproducible in a golden transcript. `last_seat_negative_counter_partner` pins the end state the race produces for one seat (SeatsAvailable -1, next booking WAITLISTED) and `book_duplicate_*` pins the duplicate check that a same-candidate race can slip past. |
| CT-31 | `retake_no_show_not_counted_*` pins that an ATTENDED booking without a `Result` is a first attempt; the latest-Result date rule is pinned by the `retake_*` window scenarios. |
| CT-33 | `proc_error_contract_db` records error number, severity, state and message for states 1, 2, 3, 5, 6 (RAISERROR with a message string gives number 50000; the code is the state). |

Not applicable as golden transcripts (the replay compares a single deterministic run):

| Test | Reason |
|---|---|
| CT-27 | N/A - concurrent opposite-direction reschedules are a lock-order/deadlock race (HB-17); whether and which side deadlocks depends on thread timing, so a transcript would not be deterministic. Needs a dedicated load/soak test (M6), not a golden transcript. |
| CT-32 | N/A - JVM time zone behaviour (HB-13) depends on the host time zone and the clock at 02:00; the estate is pinned to one host zone for capture, and `NightlySyncJob` is exercised through `POST /sync/run` instead of the cron. Changing the JVM zone is a deployment test, not a behaviour transcript. |

## Known defects (modern target)

`known-defects.yaml` lists transcripts that are expected to differ on `--target modern` for a known, tracked
reason (currently the on-call defect from #57 in scheduling-service capacity reductions). On a modern replay a
listed transcript that differs is reported as **KNOWN-DEFECT**: counted in its own column and in the status counts,
never as MATCH and never hidden (the full request/expected/actual is in the mismatch section). This happens only when
the actual run also shows the entry's `signature` business fields (for #57: `error: CAPACITY_BELOW_ACCOMMODATION_FLOOR`,
`legacyCode: 50005`). Any other difference in that transcript is still a MISMATCH. If it matches, it is counted as MATCH. Legacy replays ignore the file. A gateway answer `CENTER_NOT_MODERN` (the scenario's center is not
on the gateway allow-list, so nothing reached the modern service) is reported as SKIPPED with that note.

## Tests

```bash
cd characterization
python -m pytest -q                    # unit tests (offline) + catalogue/transcript consistency
python -m pytest -q -m integration     # functional: capture+replay a subset against the running estate
```
