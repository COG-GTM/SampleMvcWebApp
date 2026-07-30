---
name: testing-samplemvcwebapp
description: How to run and end-to-end test the SampleMvcWebApp (ASP.NET Core MVC on .NET 10, EF Core, EfCore.GenericServices) locally against SQL Server in Docker — startup, seeded-data baseline, the UI flows worth asserting, and the multi-select/validation gotchas.
---

# Testing SampleMvcWebApp locally

The app is an ASP.NET Core MVC site on .NET 10 using EF Core and `EfCore.GenericServices`. It has no
authentication, so there is no login step — every page is reachable directly.

## Bring up the environment

1. **SQL Server** must be running in Docker. Check with `docker ps`; if the container exists but is
   stopped, `docker start mssql`. To recreate:
   ```bash
   docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=<sa-password>' \
     -e MSSQL_PID=Developer -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
   ```
   The connection string lives in `SampleWebApp/appsettings.Development.json`, so no extra config is
   needed when running in the Development environment.

2. **Run the app** (plain HTTP, no HTTPS redirect):
   ```bash
   cd <repo-root>
   ASPNETCORE_ENVIRONMENT=Development dotnet run --project SampleWebApp --urls http://localhost:5000
   ```
   Start it in a background shell and tee the output to a log file — the log is the cheapest way to
   prove there were no 500s (see "Server-log corroboration" below). First start takes ~15-20s because
   the app runs `Database.Migrate()` and seeds on startup, so it is self-healing: if the DB is missing
   or empty it will rebuild itself. Poll `curl -s -o /dev/null -w '%{http_code}' http://localhost:5000/`
   until it returns 200 rather than guessing a sleep duration.

## Navigating

The navbar is: `Home` | `Sync database` ▾ (Posts, Tags, Blogs) | `Async database` ▾ (Posts, Tags) |
`About` | `Contact`. The two dropdowns are the sync (`ICrudServices`) and async (`ICrudServicesAsync`)
code paths — both are worth covering, they are separate controllers.

**Seeded row ids are not `1..n`.** The seeder deletes and re-inserts, so ids drift upward every time
the data is reset. Always reach a record by clicking its Edit/Details/Delete link in the list page;
never type a guessed id into the URL bar.

Typical seeded baseline: 8 tags, 4 blogs, 17 posts. Capture the actual baseline from the list pages at
the start of a run instead of hardcoding it.

## The highest-value assertions

These are the spots where framework behaviour had to be hand-reimplemented, so they are the most
likely to regress:

1. **Duplicate tag slug.** `/Tags` → Create with a Name plus a Slug that an existing tag already uses
   (e.g. `programming`). It must redisplay the form with
   `The Slug on tag '<name>' must be unique and is already being used.`
   A 500 page / `DbUpdateException` / raw SQL unique-index error is a failure. EF Core has no
   `ValidateEntity` hook, so this check is hand-written in
   `DataLayer/DataClasses/Concrete/SampleWebAppDb.ValidateChangedEntities()`.

2. **Post title containing `!`.** Create or edit a post with a title like `Great migration!`. It must
   be rejected with
   `Sorry, but you can't get too excited and include a ! in the title.`
   **and the blogger drop-down and tag multi-select must still be fully populated on the redisplayed
   form.** That repopulation is hand-written (`ServiceLayer/PostServices/PostDtoService.ResetSecondaryData`)
   and replaced a GenericServices feature that no longer exists — empty controls after a validation
   error is the classic regression here. Open the drop-down in the recording so the proof is visual.

3. **Many-to-many round-trip.** After saving a post with 2+ tags, reopen Edit and confirm those tags
   come back pre-highlighted before changing them.

## Gotchas

- **Tag slugs reject hyphens.** A regex allows alphanumerics/underscore only, so use `demotag`, not
  `demo-tag`, when you want a *valid* slug.
- **The tag multi-select is a real `<select multiple>`.** Plain-click the first option then
  ctrl-click subsequent ones. If the list is scrolled, scroll it into view first.
- **Do not trust the `selected="true"` attribute in a scraped/annotated DOM for a `<select multiple>`
  after you click.** It can reflect the server-rendered HTML attribute rather than the live selection
  property, so it may still show the old selection. Verify visually instead — scroll the listbox and
  screenshot/zoom which options are highlighted.
- **`/Posts/Reset` re-seeds everything.** Only click it *before* your assertions, never after, or you
  destroy the evidence that your CRUD operations actually took effect.
- Leaving one artifact behind on purpose (e.g. an edited blogger) is a cheap way to prove at the end of
  a run that writes really reached SQL Server and survived a fresh read.

## Server-log corroboration

Because the app logs every request, this is a strong, cheap supplement to the visual evidence — run it
after the browser pass:

```bash
grep -c -i -E "Unhandled exception|DbUpdateException|SqlException|HTTP/1.1 500" /tmp/app.log
grep -oE "Request finished HTTP/1.1 (GET|POST) [^ ]* - [0-9]{3}" /tmp/app.log \
  | awk '{print $NF}' | sort | uniq -c
```

Expect zero matches on the first command, and only 200/302/304 on the second (302s are the
post-redirect-get after each successful Create/Edit/Delete).

## Devin Secrets Needed

None. The app has no auth and the local SQL Server SA password is supplied via the Docker run command
and `appsettings.Development.json`; no external credentials are required.
