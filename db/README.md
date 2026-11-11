# AssessmentDelivery database

SQL Server 2012+ scripts for the `AssessmentDelivery` database (developed and checked
against SQL Server 2022 Developer in Docker). Everything runs in file-name order:

| Folder | What |
|---|---|
| `schema/` | `000` creates the database + dev login `ad_app`, `010` the tables |
| `procs/` | `020`-`023` booking / cancel / capacity / reschedule procs used by the portal |
| `triggers/` | `030`-`033` audit and housekeeping triggers |
| `seed/` | `040`-`049` deterministic demo data (same rows and ids on every run) |
| `checks/` | read-only diagnostic queries |

## Applying

The apply scripts **drop and recreate** the database (dev only), then run
schema -> procs -> triggers -> seed. Safe to run as often as you like.

Local SQL Server in Docker:

```bash
docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=Str0ng!Passw0rd' \
  -e MSSQL_PID=Developer -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

Linux / macOS (needs `sqlcmd`, e.g. mssql-tools18):

```bash
./db/apply.sh              # rebuild + seed
./db/apply.sh --no-seed    # empty schema only
AD_SQL_SERVER=otherhost,1433 AD_SA_PASSWORD=... ./db/apply.sh
```

Windows (needs `sqlcmd` on the PATH):

```powershell
.\db\apply.ps1
.\db\apply.ps1 -NoSeed
.\db\apply.ps1 -Server "localhost,1433" -SaPassword "..."
```

Both scripts honour `AD_SQL_SERVER` (default `localhost,1433`), `AD_SQL_USER` (`sa`),
`AD_SA_PASSWORD` (`Str0ng!Passw0rd`, the local container) and `SQLCMD`.
The applications connect as `ad_app` / `Northw1nd!Dev2011`.

## Seed contents

Fixed anchor dates (sessions 2026-09-01 .. 2027-03-31, "now" for the seed is
2026-11-10), no `NEWID()` / `RAND()`.

| Table | Rows | Notes |
|---|---|---|
| TestCenter | 21 | NA-EAST 6, NA-WEST 5, EMEA 6, APAC 4 |
| Room | 36 | 1-3 per centre, some with accommodation seats |
| Exam | 12 | 4 exam owners, RetakeWaitDays 30 |
| ExamSession | 462 | DELIVERED 151, OPEN 285, CLOSED 8, CANCELLED 18; capacity 8-30, 211 with AccommodationCapacity > 0 |
| Candidate | 1,000 | `NW00000001`..`NW00001000`, ~5% with a DefaultAccommodationCode |
| Booking | 3,587 | BOOKED 1811, ATTENDED 1444, CANCELLED 298, WAITLISTED 34; PORTAL / PARTNER / IMPORT |
| Waitlist | 34 | one row per WAITLISTED booking |
| Result | 1,444 | one per ATTENDED booking, pass/fail by PassingScore |
| AuditLog | 12 | exam catalogue inserts |

Candidates `NW00000901`..`NW00000999` are the unassigned block: no bookings, no results.

At the end of the seed `SeatsAvailable = Capacity - (BOOKED + ATTENDED)` for every session.

## Checks

```bash
sqlcmd -S localhost,1433 -U sa -P 'Str0ng!Passw0rd' -C -W -i db/checks/seat-drift.sql
sqlcmd -S localhost,1433 -U sa -P 'Str0ng!Passw0rd' -C -W -i db/checks/row-counts.sql
```

- `seat-drift.sql` - sessions where `SeatsAvailable <> Capacity - (BOOKED + ATTENDED)`.
  No rows on a fresh seed.
- `row-counts.sql` - rows per table and bookings by session/booking status, for
  comparing runs.
