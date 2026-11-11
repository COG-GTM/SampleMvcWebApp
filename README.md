# Assessment Delivery (Northwind Assessment Network)

Legacy exam-scheduling estate for the Northwind Assessment Network test-centre network.
Exam owners publish exams, test centres run exam sessions in rooms with a fixed number of
seats, and candidates book, cancel and reschedule seats - through the centre/candidate
portal or through partner channels. After delivery, score files are ingested and the
results are shown to candidates.

The estate is a .NET Framework 4.8 portal plus three Java 8 services, all reading and
writing **one shared SQL Server database** (`AssessmentDelivery`). Full contract (tables,
stored procedures, endpoints, routes): [docs/SYSTEM_OVERVIEW.md](docs/SYSTEM_OVERVIEW.md).

## Architecture

| Component | Tech | Port | Folder | Notes |
|---|---|---|---|---|
| Portal (centre admin + candidate) | .NET Framework 4.8, ASP.NET MVC 5, EF6, Autofac, IIS Express | 8080 | `AssessmentDelivery.*`, `AssessmentDelivery.sln` | [AssessmentDelivery.Web/README.md](AssessmentDelivery.Web/README.md) |
| booking-api (partner channels) | Java 8, Spring Boot 1.5, JdbcTemplate | 8081 | `services/booking-api` | [services/README.md](services/README.md) |
| results-service (score ingestion) | Java 8, Spring Boot 1.5 | 8082 | `services/results-service` | |
| capacity-sync (nightly seat re-sync) | Java 8, Spring Boot 1.5, `@Scheduled` | 8083 | `services/capacity-sync` | |
| SQL Server `AssessmentDelivery` | SQL Server 2016+ (Express/Developer locally), Azure SQL | 1433 | `db/` | [db/README.md](db/README.md) |

```
  candidates / centre admins          partner booking channels       centre score files
             |                                  |                            |
     Portal :8080 (IIS Express)        booking-api :8081            results-service :8082
             |  usp_BookSeat, EF6               |  JDBC                      |  JDBC
             +----------------------+-----------+----------------------------+
                                    |
                     SQL Server :1433  AssessmentDelivery  <---  capacity-sync :8083 (02:00 nightly)
```

## Prerequisites

**Windows** (full estate) - `scripts/run-legacy.ps1` installs whatever is missing when run from an
elevated PowerShell:

- SQL Server Express or Developer on `localhost,1433` with SQL authentication
  (installs SQL Server 2022 Express as the default instance if no instance exists; an existing
  instance gets TCP 1433 and mixed-mode authentication switched on)
- `sqlcmd` (installed through chocolatey or winget if missing)
- JDK 8 (installs Eclipse Temurin 8 if none is found); the Maven wrapper downloads Maven
- Visual Studio 2017+ or VS 2022 Build Tools with the web build tools workload and the
  .NET Framework 4.8 targeting pack, IIS Express 10, nuget.exe (`scripts/run-portal.ps1` installs these)

**Linux** (database + Java services) - Docker, `sqlcmd` (mssql-tools18), JDK 8.

## Setup and run

### Windows: everything

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1           # SQL Server, fresh DB, services, portal
powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1 -SkipDb   # keep the existing data
powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1 -Status
powershell -ExecutionPolicy Bypass -File scripts\run-legacy.ps1 -Stop     # services + portal (SQL Server keeps running)
```

Steps: SQL Server check/install -> `db\apply.ps1` (drops and re-creates `AssessmentDelivery` with the
seed data) -> JDK 8 -> `services\mvnw.cmd package` -> booking-api, results-service, capacity-sync ->
`scripts\run-portal.ps1` (NuGet restore, MSBuild, IIS Express). Other switches: `-SkipBuild`
(reuse the jars and the portal build), `-NoPortal`, `-SaPassword` (or `AD_SA_PASSWORD`; default
`Str0ng!Passw0rd`, the same as the Linux container). Logs and pids go to `.run\`. If the `sa`
password does not match, the script resets it through Windows authentication.

### Linux: database + Java services

```bash
scripts/run-legacy.sh            # SQL Server 2022 container, fresh DB, the three services
SKIP_DB=1 scripts/run-legacy.sh  # keep the data
scripts/run-legacy.sh status
scripts/run-legacy.sh stop
```

The portal is Windows-only (.NET Framework 4.8).

### Pieces on their own

- Database only: `db\apply.ps1` / `db/apply.sh` (see [db/README.md](db/README.md))
- Java services: `cd services && ./mvnw package`, then `java -jar <module>/target/<module>.jar`
- Portal only: `scripts\run-portal.ps1`

## Ports and URLs

| What | URL |
|---|---|
| Portal | http://localhost:8080/ - `/Admin/Centers`, `/Admin/Sessions/Roster/{id}?format=csv`, `/Booking`, `/Booking/MyBookings?candidateNumber=`, `/Eligibility`, `/Results` |
| booking-api | http://localhost:8081/api/v1/sessions |
| results-service | http://localhost:8082/api/v1/results/candidate/NW00000001 |
| capacity-sync | http://localhost:8083/sync/last (`POST /sync/run` to run now) |
| SQL Server | `localhost,1433`, database `AssessmentDelivery` |

## Development credentials

Local development only:

| Login | Password | Used by |
|---|---|---|
| `ad_app` | `Northw1nd!Dev2011` | portal and Java services (created by `db/schema/000_create_database.sql`) |
| `sa` | `Str0ng!Passw0rd` (override with `AD_SA_PASSWORD`) | the apply/run scripts |

There is no sign-in in the portal: candidates are picked by candidate number.

## Seed data

`db/seed/` is deterministic: the same rows and ids on every run (fixed anchor dates, no `NEWID()`/`RAND()`).

| | |
|---|---|
| Test centres / rooms | 21 centres in 4 regions (NA-EAST, NA-WEST, EMEA, APAC), 36 rooms |
| Exams | 12 exams from 4 exam owners, retake wait 30 days |
| Exam sessions | 462, dated 2026-09-01 .. 2027-03-31 (DELIVERED / OPEN / CLOSED / CANCELLED) |
| Candidates | 1,000, candidate numbers `NW` + 8 digits: `NW00000001` .. `NW00001000`; `NW00000901` .. `NW00000999` have no bookings |
| Bookings | 3,587 across the PORTAL, PARTNER and IMPORT channels, plus waitlist rows |
| Results | 1,444, one per attended booking |

Row counts per table: `db/checks/row-counts.sql`.

## Tests

Portal (NUnit 3):

```powershell
nuget restore AssessmentDelivery.sln -PackagesDirectory packages
& "$(& "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -find 'MSBuild\**\Bin\MSBuild.exe')" AssessmentDelivery.sln /p:Configuration=Debug /v:m
packages\NUnit.ConsoleRunner.3.16.3\tools\nunit3-console.exe AssessmentDelivery.Tests\bin\Debug\AssessmentDelivery.Tests.dll
```

Java services (JUnit 4, needs JDK 8):

```bash
cd services
./mvnw test          # mvnw.cmd test on Windows
```

## Diagnostic checks and traffic script

Read-only queries under `db/checks/`:

```powershell
sqlcmd -S localhost,1433 -U sa -P "Str0ng!Passw0rd" -C -W -i db\checks\row-counts.sql   # rows per table, bookings by status
sqlcmd -S localhost,1433 -U sa -P "Str0ng!Passw0rd" -C -W -i db\checks\seat-drift.sql   # sessions whose SeatsAvailable differs from the bookings
```

`scripts/mixed-traffic.ps1` (Windows) and `scripts/mixed-traffic.sh` (Linux) are smoke/load scripts that
book and cancel a batch of seats through both channels on a freshly seeded database - the portal
(`/Booking/Book` over HTTP on Windows) and booking-api REST - then print a session summary and the
`seat-drift.sql` row count.

## Azure configuration

The committed configuration for Azure carries placeholders only; real values come from App Service
settings or the release pipeline.

- Portal: `AssessmentDelivery.Web/Web.AzureRelease.config` (build configuration `AzureRelease`) points
  the `AssessmentDelivery` connection string at `<your-server>.database.windows.net` with the
  `__AZURE_SQL_PASSWORD__` token. `Web.Release.config` is the on-prem equivalent.
- Java services: `application-azure.properties` in each module, activated with
  `--spring.profiles.active=azure`; fill in the server/user placeholders and set `AZURE_SQL_PASSWORD`.
