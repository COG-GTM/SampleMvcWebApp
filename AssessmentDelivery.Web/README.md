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
  `db/schema/*.sql` then `db/procs/*.sql` applied. The portal connects as the dev login `ad_app`
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

Browser check of the session roster CSV export (headless Playwright, needs Node 18+, `sqlcmd` on PATH,
the portal on :8080 and a seeded database). It applies `e2e/fixture.sql` (one BOS01 session with
BOOKED, ATTENDED, CANCELLED and WAITLISTED bookings), downloads the roster through the
"Download roster (CSV)" link, checks the file and confirms that no `usp_*` proc ran and no AuditLog
row was written. Screenshots and the downloaded file go to `e2e/screenshots/`.

```powershell
cd AssessmentDelivery.Web\e2e
npm install
npx playwright install chromium
node run.mjs          # env: BASE_URL (default http://localhost:8080), SQL_SERVER, AD_SA_PASSWORD, SQLCMD
```

## Configuration

- `Web.config` - local dev connection string and `Portal.*` appSettings.
- `Web.Release.config` - on-prem release transform (custom errors on, debug off).
- `Web.AzureRelease.config` - Azure App Service transform (`AzureRelease` build configuration). The
  connection string is a placeholder; the real password is injected by the release pipeline in place
  of `__AZURE_SQL_PASSWORD__`. Never commit real credentials.

## Notes

- Writes that touch seats go through the stored procedures (`usp_BookSeat`, `usp_CancelBooking`,
  `usp_RescheduleBooking`, `usp_ChangeCapacity`); messages raised by the procs are shown on screen.
  Editing a centre's name/active flag is a plain EF update.
- Bookings made by partners through booking-api (channel `PARTNER`) and imported bookings show up in
  *My bookings* next to portal bookings.
- *Download roster (CSV)* on each session row (`/Admin/Sessions/Roster/{id}?format=csv`) returns the
  proctor check-in list: BOOKED/ATTENDED candidates, then the WAITLISTED ones with their waitlist
  position, each group by last/first name. UTF-8 with BOM, RFC 4180 quoting, read-only (no procs, no
  audit rows). Unknown session = 404.
- The "Booked" column in centre admin is counted from the Booking rows (BOOKED + ATTENDED); the
  "Seats avail." column is the stored `ExamSession.SeatsAvailable` figure.
