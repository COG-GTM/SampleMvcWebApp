# Assessment Delivery Portal (AssessmentDelivery.*)

Centre-admin and candidate portal for the Northwind Assessment Network.
ASP.NET MVC 5 on .NET Framework 4.8, EF6 (database-first, no migrations), Autofac 4 + Autofac.Mvc5,
Bootstrap 3 / jQuery. Hosted on **http://localhost:8080** (IIS Express).

| Project | What lives there |
|---|---|
| `AssessmentDelivery.Data` | `AssessmentDeliveryDb` (EF6 DbContext) and entities mapped onto the shared tables |
| `AssessmentDelivery.Business` | rules: retake eligibility, accommodation codes, seat counting |
| `AssessmentDelivery.Services` | booking / admin / eligibility / results services; calls the `usp_*` procs |
| `AssessmentDelivery.Web` | MVC 5 controllers, views, theme (`Content/Site.css`) |
| `AssessmentDelivery.Tests` | NUnit 3 unit tests |

Routes are listed in [docs/SYSTEM_OVERVIEW.md](../docs/SYSTEM_OVERVIEW.md#portal-8080).

## Prerequisites

- Windows, .NET Framework 4.8
- Visual Studio 2017+ or VS Build Tools with the *Web development build tools* workload
  (`Microsoft.WebApplication.targets`) and the .NET Framework 4.8 targeting pack
  (`run-portal.ps1` installs the 2022 Build Tools if no MSBuild is found)
- IIS Express 10 (installed by the script if missing)
- SQL Server (Express or Developer) listening on `localhost,1433` with SQL authentication, with
  `db/schema/*.sql` then `db/procs/*.sql` applied. The portal connects as the dev login `ad_portal`
  (connection string `AssessmentDelivery` in `Web.config`).

## Run

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-portal.ps1        # restore, build, start, return when :8080 answers
powershell -ExecutionPolicy Bypass -File scripts\run-portal.ps1 -Wait  # same, then block until IIS Express exits
```

Options: `-Port 8080`, `-Configuration Debug|Release|AzureRelease`, `-SkipBuild`.
IIS Express output goes to `logs\iisexpress.log`.

## Tests

```powershell
nuget restore AssessmentDelivery.sln -PackagesDirectory packages
msbuild AssessmentDelivery.sln /p:Configuration=Debug
packages\NUnit.ConsoleRunner.3.16.3\tools\nunit3-console.exe AssessmentDelivery.Tests\bin\Debug\AssessmentDelivery.Tests.dll
```

## Configuration

- `Web.config` - local dev connection string and `Portal.*` appSettings.
- `Web.Release.config` - on-prem release transform (custom errors on, debug off).
- `Web.AzureRelease.config` - Azure App Service transform (`AzureRelease` build configuration). The
  connection string is a placeholder; the real password is injected by the release pipeline in place
  of `__AZURE_SQL_PASSWORD__`. Never commit real credentials.

## Notes

- The booking and capacity pages were retired in MBA-3824: scheduling-service is the only writer of
  bookings and seats, and the routing gateway (:8000) redirects `/Booking/*` and
  `/Admin/Sessions/Capacity/{id}` to the modern UI (`/app/...`). The portal's Bookings tab, quick
  candidate lookup and the Capacity link on the admin session list point at those modern pages
  (`Portal.ModernUiBaseUrl`, empty = same origin as the portal, i.e. behind the gateway).
- What is left is read-only over the relay-projected tables (`/Admin/Sessions`, `/Eligibility`,
  `/Results`), plus editing a centre's name/active flag (a plain EF update).
  `scripts/check-retired-booking.sh` (CI) and `RetiredBookingGuardTests` fail if C# booking writes,
  `SeatsAvailable` updates or the dropped booking procs reappear.
- The "Booked" column in centre admin is counted from the Booking rows (BOOKED + ATTENDED); the
  "Seats avail." column is the stored `ExamSession.SeatsAvailable` figure.
