# characterization/ - golden transcripts of the legacy estate (MBA-3820)

Golden transcripts of what the legacy Assessment Delivery estate (tag `legacy-baseline`) does **today**,
odd behaviour included, plus a capture tool and a replay harness so every later migration step can prove it
did not change behaviour by accident. Background on the estate: [`README.md`](../README.md),
[`docs/SYSTEM_OVERVIEW.md`](../docs/SYSTEM_OVERVIEW.md) and [`docs/as-is/`](../docs/as-is/)
(hidden behaviours HB-01..HB-31). Oddities found at runtime: [`ODDITIES.md`](ODDITIES.md).

## Layout

| Path | What |
|---|---|
| `scenarios/*.yaml` | the catalogue: 64 scenarios (booking, cancel, reschedule, capacity, retake eligibility, accommodation, mixed traffic + capacity-sync) |
| `transcripts/<id>.json` | one golden transcript per scenario, captured from the legacy estate |
| `harness/` | Python capture/replay engine (`python -m harness list|capture|replay`) |
| `replay`, `capture` (bash) / `replay.ps1`, `capture.ps1` (PowerShell) | entry points on Linux / Git Bash and Windows |
| `reports/replay-<target>-<sha>.md` | replay report, pass/fail per scenario (`reports/actual/` holds the replayed transcripts of failures, git-ignored) |
| `tests/` | offline pytest suite (normalizer, comparator, catalogue/transcript/ODDITIES consistency) |
| `ODDITIES.md` | every `oddity: true` scenario, one line each, cross-referenced to HB-xx |

## What a transcript records

```text
id, title, area, channel (portal|api|both|db|sync), hb[], oddity, oddity_ids[], seed_as_of
setup[]   steps run after the reseed to build the scenario-specific state (each with request + response)
before    DB snapshot after setup: ExamSession (Capacity, AccommodationCapacity, SeatsAvailable, Status, ...
          plus computed BookedOrAttended / ExtraTimeBooked / Waitlisted / SeatDrift), every Booking and
          Waitlist row on the scenario's sessions, the candidates' Result rows
steps[]   request (portal form post or booking-api REST call, or proc / sync / seat-drift check),
          response (HTTP status + normalized JSON body, or the rendered portal fields: heading, alerts,
          validation errors, table/definition cells), and the session counters right after the step
after     the same snapshot after the steps, plus every AuditLog row written during the scenario
```

Snapshots cover the sessions listed in the scenario plus any session a new booking landed on, and the
candidates listed plus any candidate that booked.

**Normalization.** Identity values created during the scenario become ordered placeholders
(`<booking:1>`, `<waitlist:2>`, `<result:1>`, `<audit>` ids are dropped), including when they appear inside
portal messages; seeded ids (<= the seed's maximum) stay literal. Timestamps written at runtime (`BookedAt`,
`LastModified`, `LoggedAt`, `AddedAt`, sync `startedAt`...) become `<now>`; seeded timestamps stay literal.

## Deterministic against the seed clock

The seed is frozen at 2026-11-10 (HB-30) while the wall clock moves on, so nothing in a scenario depends on
"today":

* every scenario starts from the deterministic seed: `db/apply.ps1` / `db/apply.sh` once, a SQL Server
  backup of the result, and a `RESTORE` before each scenario (`--reseed apply` runs `db/apply` every time);
* `SEED_AS_OF` (default `2026-11-10`, `--seed-as-of`) drives the mixed-traffic session selection (the
  `scripts/mixed-traffic.*` query uses `StartsAt > GETDATE()`), and is recorded in every transcript;
* eligibility calls always pass `date=` explicitly (both channels default it to the server's today);
* session ids, candidates and dates are fixed in the YAML (sessions 1-462, unbooked candidates from
  `NW00000901`). The only clock-dependent legacy code left (`FindRescheduleTargets` listing sessions from
  today, used by the reschedule *form*) is not part of any compared field.

## Running it

Prerequisites: Python 3.9+ with `pip install -r characterization/requirements.txt`, `sqlcmd` (go-sqlcmd or
mssql-tools18) and the legacy estate running (`scripts/run-legacy.ps1` on Windows, `scripts/run-legacy.sh`
for the DB and Java services on Linux with the portal reachable via `PORTAL_URL`).

```bash
# Linux / Git Bash
characterization/replay --target legacy                 # all scenarios, writes reports/replay-legacy-<sha>.md
characterization/replay --target legacy 'RT-*' BK-02*    # a subset (ids or globs)
characterization/capture 'CP-*'                          # (re)capture golden transcripts - legacy only
python -m pytest characterization/tests -q               # offline tests (run from characterization/)
```

```powershell
# Windows
characterization\replay.ps1 --target legacy
characterization\capture.ps1 'CP-*'
```

On Windows Git Bash, run the bash entry points as `"$BASH" characterization/replay` if `bash` resolves to WSL.

Exit code 0 means every selected scenario matched; failures print the first differing JSON paths and write
the replayed transcript to `reports/actual/` for diffing.

Environment / flags (defaults are the legacy ports): `PORTAL_URL` (`http://localhost:8080`), `BOOKING_API_URL`
(`http://localhost:8081`), `CAPACITY_SYNC_URL` (`http://localhost:8083`), `AD_SQL_SERVER` (`localhost,1433`),
`AD_SQL_USER` / `AD_SA_PASSWORD` (`sa` / the local dev password used by `db/apply.*`), `SQLCMD`, `SEED_AS_OF`.

### Modern target (M6 parity)

```bash
characterization/replay --target modern --base-url http://localhost:9000 \
    [--reseed command --reseed-cmd "<script that restores the modern DB to the seed>"] [--compare http]
```

`--base-url` serves both the portal routes and `/api/v1` (and `/sync/run`) through the routing gateway;
`--portal-url` / `--api-url` / `--sync-url` override each one. With `--compare all` (default) the modern
target must expose an `AssessmentDelivery`-compatible DB through `--sql-server` / `--database` so the DB
snapshots can be compared; `--compare http` compares only the HTTP responses and rendered fields.

## Adding a scenario

1. Add an entry to a `scenarios/*.yaml` file: `id` (`XX-nn-slug`), `title`, `area`, `channel`, `hb`,
   `sessions`, `candidates`, optional `setup`, and `steps`. Step types: `portal_book`, `portal_cancel`,
   `portal_reschedule`, `portal_capacity`, `portal_eligibility`, `api_book`, `api_cancel`,
   `api_reschedule`, `api_eligibility`, `api_get`, `capacity_sync`, `seat_drift`, `mixed_traffic`,
   `db_proc`, `sql`, `db_query`. `save_as: b1` stores a new booking id for later `${b1}` references.
2. If it pins odd behaviour, set `oddity: true`, `oddity_ids: [ODD-xx]` and add/extend the row in
   `ODDITIES.md` (the tests enforce both directions).
3. `characterization/capture <id>`, review the transcript, then `characterization/replay --target legacy <id>`.
