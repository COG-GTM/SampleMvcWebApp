# Seat drift before (legacy) vs after (modern), MBA-3825

| | Legacy (before) | Modern (after) |
|---|---|---|
| Code | tag `legacy-baseline`, commit `e458238da39c991b51d8f670f6d0c463b51b372f` (tag object `40cfb01`) | **tested code SHA `90becef3a79ea7cec0034ec54d786795c04bee2d`** (branch `devin/mba-3825-parity-proof-v2`) |
| Estate | `scripts/run-legacy.ps1` in a `git worktree` of the tag: portal :8080, booking-api :8081, capacity-sync :8083 | `run-legacy.ps1` (reseed) + `run-scheduling-service.ps1` + `run-routing-gateway.ps1`; all traffic through the gateway :8000 |
| Starting state | `db/apply.ps1` fresh seed (seed "now" 2026-11-10) | fresh seed + `reseed-scheduling-db.sh` (restore AssessmentDelivery from the fresh-seed backup, then `mode=reset` import into Scheduling: both DBs consistent, runbook §4) |
| Workload | `scripts/mixed-traffic.ps1` (defaults: 20 portal + 20 partner bookings, 3 + 3 cancels, sessions 154 155 157 158, candidates NW00000901..941) | same script with `PORTAL_URL=BOOKING_API_URL=http://localhost:8000` |
| Portal / partner calls | 20 ok / 20 ok, 3 + 3 cancelled | 20 ok / 20 ok, 3 + 3 cancelled |
| **`db/checks/seat-drift.sql` rows** | **2** (sessions 155 and 158, total drift +11) | **0** |
| **Reconciliation mismatches** (`GET /internal/reconciliation`) | n/a: no second store exists at `legacy-baseline` (scheduling-service and reconciliation arrive in M2/M4); the drift rows are the legacy measure | **0** (462 sessions and 3627 bookings compared; 0 session, 0 booking mismatches, 0 drift rows) |

The same `db/checks/seat-drift.sql` was used on both sides (byte-identical in the tag and the branch).

## seat-drift.sql rows per session

| SessionId | Exam | Centre | Starts | Legacy Capacity | Legacy SeatsAvailable | Legacy active bookings | Legacy drift | Modern drift |
|---|---|---|---|---|---|---|---|---|
| 155 | HPI-ACC2 | DUB01 | 2026-11-12 13:30 | 16 | 1 | 8 | **+7** | 0 (row absent) |
| 158 | BCB-AGL | LDS01 | 2026-11-14 09:00 | 11 | 0 | 7 | **+4** | 0 (row absent) |

These are the same sessions and values M4 recorded (155 +7, 158 +4). Cause on the legacy side: the partner double decrement and
the capacity-sync formula (HB-01, HB-03, HB-04; MR-01, MR-02 in `docs/as-is/`; ODD-01/ODD-03 in `characterization/ODDITIES.md`).
Modern removes it by design: seats are derived as `Capacity - (BOOKED + ATTENDED)` and only the relay writes legacy
(ADR 0002 ODD-01/ODD-03/ODD-13, ADR 0003).

Session summary printed by the workload (`SessionId|Capacity|SeatsAvailable|Booked`):

| Session | Legacy | Modern |
|---|---|---|
| 154 | 20 / 3 / 17 | 20 / 3 / 17 |
| 155 | 16 / 1 / 8 | 16 / 5 / 11 |
| 157 | 16 / 1 / 15 | 16 / 0 / 16 |
| 158 | 11 / 0 / 7 | 11 / 0 / 11 |

On legacy the stored counter runs out early (partner bookings take two seats), so later bookings on 155/157/158 are waitlisted
while seats are really free; modern books them (one seat per booking) and the stored counter equals the derived count.

## Commands

```bash
# legacy (worktree of tag legacy-baseline)
git worktree add --detach ../ad-legacy legacy-baseline
powershell -ExecutionPolicy Bypass -File scripts/run-legacy.ps1 < /dev/null
"$BASH" characterization/replay --target legacy --report replay-legacy.md       # harness copied from 90becef
powershell -ExecutionPolicy Bypass -File db/apply.ps1 < /dev/null                  # fresh seed before the workload
powershell -ExecutionPolicy Bypass -File scripts/mixed-traffic.ps1 < /dev/null
sqlcmd -S localhost,1433 -E -C -d AssessmentDelivery -W -s '|' -i db/checks/seat-drift.sql
powershell -ExecutionPolicy Bypass -File scripts/run-legacy.ps1 -Stop < /dev/null

# modern (branch at 90becef)
powershell -ExecutionPolicy Bypass -File scripts/run-legacy.ps1 < /dev/null       # reseeds AssessmentDelivery
sqlcmd -S localhost,1433 -E -C -Q "BACKUP DATABASE [AssessmentDelivery] TO DISK = N'<backup dir>\AssessmentDelivery_scheduling_seed.bak' WITH INIT, FORMAT, COPY_ONLY"
powershell -ExecutionPolicy Bypass -File scripts/run-scheduling-service.ps1 -SkipBuild < /dev/null
powershell -ExecutionPolicy Bypass -File scripts/run-routing-gateway.ps1 -SkipBuild < /dev/null
# (64-transcript replay, see characterization/reports/parity-90becef.md)
RESEED_BACKUP_PATH=<seed .bak> "$BASH" services/scheduling-service/scripts/reseed-scheduling-db.sh
curl -s http://localhost:8090/internal/relay                                       # pending 0 before the workload
PORTAL_URL=http://localhost:8000 BOOKING_API_URL=http://localhost:8000 powershell -ExecutionPolicy Bypass -File scripts/mixed-traffic.ps1 < /dev/null
curl -s http://localhost:8090/internal/relay                                       # pending 0: everything projected
curl -s http://localhost:8090/internal/reconciliation
sqlcmd -S localhost,1433 -E -C -d AssessmentDelivery -W -s '|' -i db/checks/seat-drift.sql
```

## Raw outputs (`reports/drift-before-after/`)

| File | Content |
|---|---|
| [legacy-db-apply.log](drift-before-after/legacy-db-apply.log) | legacy fresh reseed before the workload |
| [legacy-mixed-traffic.txt](drift-before-after/legacy-mixed-traffic.txt) | legacy workload output (ends with its own seat-drift count: 2 rows) |
| [legacy-seat-drift.txt](drift-before-after/legacy-seat-drift.txt) | legacy `seat-drift.sql`, 2 rows |
| [modern-reseed.txt](drift-before-after/modern-reseed.txt) | consistent reseed of both DBs before the modern workload |
| [modern-mixed-traffic.txt](drift-before-after/modern-mixed-traffic.txt) | modern workload through :8000 (0 drift rows) |
| [modern-relay-status.json](drift-before-after/modern-relay-status.json) | relay status after the workload (`pending: 0`) |
| [modern-reconciliation.txt](drift-before-after/modern-reconciliation.txt) | `GET /internal/reconciliation` response (0 mismatches, 0 drift) |
| [reconciliation-20261111-235704.md](drift-before-after/reconciliation-20261111-235704.md) | the report file that call wrote |
| [modern-seat-drift.txt](drift-before-after/modern-seat-drift.txt) | modern `seat-drift.sql`, 0 rows |
| [gateway-redirect-probe.txt](drift-before-after/gateway-redirect-probe.txt) | retired portal URLs and `/app` through :8000 (ADR 0004 redirects; `/app` stays on :8000) |

All outputs come from one run on 2026-11-11 (Windows Server 2022 VM, SQL Server 2022, one instance shared by both estates
one after the other). Commits after `90becef` on this branch change only reports/docs.
