# As-is documentation: Northwind Assessment Network legacy estate

Discovery and inventory of the legacy Assessment Delivery estate at tag `legacy-baseline` (commit `e458238da39c991b51d8f670f6d0c463b51b372f`), produced as the baseline for modernization. Every claim cites `path:line` at that commit; behaviours were reproduced against the estate running on Windows Server 2022 via `scripts\run-legacy.ps1`.

| Document | Contents |
|---|---|
| [architecture.md](architecture.md) | Components, shared-database integration, key flows by channel, runtime/config |
| [data-ownership.md](data-ownership.md) | Table x component R/I/U/D matrix, write mechanisms and transactions |
| [hidden-behaviours.md](hidden-behaviours.md) | 31 hidden behaviours (HB-01..HB-31) with `file:line` evidence and repro scripts |
| [inventory.md](inventory.md) | Code, database objects, seed row counts, HTTP surface, prerequisites |
| [migration-risks.md](migration-risks.md) | 21 risks (MR-01..MR-21) with mitigations and characterization tests; open questions |
| [smoke-test.md](smoke-test.md) | Local bring-up timings and the recorded portal + services smoke test (10/10 pass) |

## Deep dives (per slice, with pasted repro output)

| Slice | Document | Repros |
|---|---|---|
| .NET portal | [deep-dives/portal.md](deep-dives/portal.md) | `repros/portal/` |
| Java services | [deep-dives/java-services.md](deep-dives/java-services.md) | `repros/java-services/` (bash + curl, outputs in `output/`) |
| SQL procs/triggers | [deep-dives/sql.md](deep-dives/sql.md) | `repros/sql/` (`run-all.ps1`; every script rolls back) |
| Runtime, cross-component | [deep-dives/runtime-repros.md](deep-dives/runtime-repros.md) | `repros/runtime-repros/` (PowerShell) |

## Reproducing

1. `powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1` (elevated; drops and reseeds the database).
2. Run the slice's repro scripts. Java, portal and runtime repros write data: reseed with step 1 before repeating. SQL repros roll back.
3. Expect differences in ids and dates if the seed anchors (2026-11-10/11) are in the past on your clock (HB-30).

## Top findings

- A partner booking takes two seats; counters go negative and partners are waitlisted while seats are free (HB-01, HB-02).
- Three seat formulas; capacity-sync creates drift on 112 sessions per run (HB-03).
- Retake wait is calendar days on the portal and working days on the partner API (HB-07).
- Capacity cuts silently demote confirmed bookings (HB-05); nothing promotes the waitlist (HB-06).
- Concurrent partner bookings get duplicate waitlist positions (HB-29).
- Nothing closes started sessions; both channels book them (HB-31).
- Results are accepted for future sessions; score files partially commit with HTTP 200 (HB-18, HB-19).
