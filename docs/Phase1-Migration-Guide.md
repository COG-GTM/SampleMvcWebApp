# Phase 1 Migration Guide — HomeController to ASP.NET Core 6

> Audience: any engineer working on the .NET Upgrade. Reading time: ~20 min.
> Companion docs: [Phase1-Configuration-Migration.md](Phase1-Configuration-Migration.md),
> [Phase1-Troubleshooting.md](Phase1-Troubleshooting.md), [Onboarding.md](Onboarding.md).

This guide documents the patterns established in Phase 1 of the migration from
ASP.NET MVC 5 (.NET Framework 4.5.1) to ASP.NET Core 6. Phase 1 is intentionally
small — it migrates one controller (`HomeController`) — so subsequent phases
have a verified blueprint to follow.

---

## 1. Why we are migrating

`SampleMvcWebApp` is on .NET Framework 4.5.1, which is out of mainstream support.
The migration to .NET Core 6 unlocks:

- Cross-platform hosting (Linux containers).
- Built-in dependency injection, configuration, logging.
- A modern test stack (xUnit + `WebApplicationFactory`).
- Long-term security patching.

Phase 1 is the **foundation phase** — it does not touch the data layer,
`GenericServices`, or any controller other than `HomeController`. The goal is
to prove the migration mechanics on the simplest possible target.

---

## 2. Migration pattern: Strangler Fig

We are using the [Strangler Fig pattern](https://martinfowler.com/bliki/StranglerFigApplication.html).
The legacy MVC5 app and the new ASP.NET Core app **live in the same repository
and run side by side** until every controller has been migrated.

```
SampleMvcWebApp/                         <-- repository root
├── SampleWebApp/                        legacy MVC5 (.NET Framework 4.5.1)
├── BizLayer/                            legacy
├── DataLayer/                           legacy
├── ServiceLayer/                        legacy
├── Tests/                               legacy NUnit tests
│
├── netcore/                             new ASP.NET Core 6 app (Phase 1 scope)
│   ├── SampleWebApp.NetCore.sln
│   ├── src/
│   │   └── SampleWebApp/                migrated controllers + views
│   ├── tests/
│   │   ├── SampleWebApp.UnitTests/
│   │   ├── SampleWebApp.IntegrationTests/
│   │   └── SampleWebApp.PerformanceTests/
│   ├── docs/                            NET-12 / NET-13 docs
│   ├── Dockerfile
│   └── docker-compose.yml
│
├── docs/                                cross-phase documentation (this folder)
├── .github/workflows/                   ci.yml, cd.yml, code-quality.yml
└── README.md
```

Rules during the strangle:

- **Never** modify the legacy MVC5 app to make the Core app work. The legacy
  app is a frozen reference until its last controller is migrated.
- **Never** delete legacy code in the same PR that migrates a controller —
  keep removal in a separate PR so revert is trivial.
- Both apps are buildable from `master` at all times. CI must build both.
- The two apps may temporarily duplicate logic. Duplication is acceptable
  during Phase 1; it will be removed in the cleanup phase.

---

## 3. Target architecture (Phase 1)

```
netcore/src/SampleWebApp/
├── SampleWebApp.csproj              <-- Microsoft.NET.Sdk.Web, net6.0
├── Program.cs                       <-- top-level statements, no Startup.cs
├── appsettings.json                 <-- replaces Web.config
├── appsettings.Development.json
├── appsettings.Staging.json
├── appsettings.Production.json
├── Controllers/
│   └── HomeController.cs            <-- IActionResult instead of ActionResult
├── Models/
│   └── InternalsInfo.cs             <-- cross-platform replacements
├── Views/
│   ├── _ViewImports.cshtml          <-- tag helpers + namespaces
│   ├── _ViewStart.cshtml
│   ├── Home/
│   │   ├── Index.cshtml
│   │   ├── About.cshtml
│   │   ├── Contact.cshtml
│   │   ├── Internals.cshtml
│   │   └── CodeView.cshtml
│   └── Shared/
│       └── _Layout.cshtml
└── wwwroot/                         <-- replaces /Content, /Scripts, /fonts
    ├── css/
    ├── js/
    └── lib/
```

Key shape differences vs. the legacy app:

| Legacy MVC5 location          | ASP.NET Core 6 location              |
|-------------------------------|---------------------------------------|
| `Global.asax.cs`              | `Program.cs` (top-level statements)   |
| `App_Start/RouteConfig.cs`    | `app.MapControllerRoute(...)` in `Program.cs` |
| `App_Start/FilterConfig.cs`   | `builder.Services.AddControllersWithViews(o => o.Filters.Add(...))` |
| `App_Start/BundleConfig.cs`   | `wwwroot/` static files; LibMan/npm if bundling is needed |
| `Infrastructure/WebUiInitialise.cs` | `Program.cs` + `Extensions/*ServiceExtensions.cs` |
| `Infrastructure/AutofacDi.cs` | `builder.Services.Add*(...)` calls    |
| `Web.config` `<connectionStrings>` | `appsettings.json` `"ConnectionStrings"` |
| `Web.config` `<appSettings>`  | `appsettings.json` typed `IOptions<T>` |
| `Log4Net.xml` + `log4net.Config` | `builder.Logging` (built-in `ILogger<T>`) |
| `Content/*.css`, `Scripts/*.js`, `fonts/`, `favicon.ico` | `wwwroot/css/`, `wwwroot/js/`, `wwwroot/lib/`, `wwwroot/favicon.ico` |

See [Phase1-Configuration-Migration.md](Phase1-Configuration-Migration.md) for
the full mapping.

---

## 4. Migration patterns (apply to every controller in later phases)

### 4.1 Controller signature

```diff
- using System.Web.Mvc;
- using SampleWebApp.Models;
+ using Microsoft.AspNetCore.Mvc;
+ using SampleWebApp.Core.Models;

  namespace SampleWebApp.Controllers
  {
      public class HomeController : Controller
      {
-         public ActionResult Index()
+         public IActionResult Index()
          {
              return View();
          }
```

- `System.Web.Mvc` → `Microsoft.AspNetCore.Mvc`.
- `ActionResult` → `IActionResult` (or a specific `ViewResult` / `JsonResult` /
  `IActionResult`). `IActionResult` matches every helper return on `Controller`.
- `HttpStatusCodeResult` → `StatusCode(...)` helper.
- `JsonResult` constructor → `Json(...)` helper.

### 4.2 ViewBag, ViewData, TempData

- `ViewBag` is unchanged on the surface — `ViewBag.Message = "..."` works.
- `ViewData` is unchanged.
- `TempData` requires `builder.Services.AddControllersWithViews()` (already
  on by default) and a session provider for cross-request retention.

### 4.3 Models with framework-specific APIs

The legacy `InternalsInfo` model uses three APIs that need attention on
.NET Core:

| Legacy API | Phase 1 replacement | Notes |
|------------|--------------------|-------|
| `System.Threading.ThreadPool.GetMaxThreads` | Unchanged | Works as-is on .NET Core. |
| `System.Threading.ThreadPool.GetAvailableThreads` | Unchanged | Works as-is. |
| `new PerformanceCounter("Memory", "Available MBytes", true)` | `GC.GetGCMemoryInfo().TotalAvailableMemoryBytes` | `PerformanceCounter` is Windows-only and not in .NET 6 BCL; the GC API is cross-platform. |
| `GC.GetTotalMemory(true)` | `GC.GetTotalMemory(false)` | Forcing a full collection in a metrics endpoint is a real bug — use `false`. |

If a Windows-only metric is genuinely required, gate it behind
`OperatingSystem.IsWindows()` and use `System.Diagnostics.PerformanceCounter`
from the `System.Diagnostics.PerformanceCounter` NuGet package.

### 4.4 Views

Razor itself is largely compatible, but the file-level imports differ:

```cshtml
@* Views/_ViewImports.cshtml *@
@using SampleWebApp.Core
@using SampleWebApp.Core.Models
@addTagHelper *, Microsoft.AspNetCore.Mvc.TagHelpers
```

- `@Html.ActionLink("Posts", "Index", "Posts")` still works, but the idiomatic
  Core form uses tag helpers: `<a asp-controller="Posts" asp-action="Index">Posts</a>`.
- `@Url.Content("~/Content/Site.css")` → `<link rel="stylesheet" href="~/css/site.css" />`
  served by `app.UseStaticFiles()`.
- `Html.AntiForgeryToken()` → `<form asp-controller="..." asp-action="...">`
  (tag helper auto-injects the token) or `@Html.AntiForgeryToken()` still works.

### 4.5 Static assets

`wwwroot/` is the only path served as static files by default.

- `Content/Site.css` → `wwwroot/css/site.css`
- `Scripts/site.js`  → `wwwroot/js/site.js`
- `fonts/*`          → `wwwroot/lib/<package>/fonts/*`
- `favicon.ico`      → `wwwroot/favicon.ico`

Bundling/minification: `System.Web.Optimization` has no direct .NET Core
equivalent. Phase 1 does not bundle — files are served as-is. If bundling
is required later, evaluate LibMan + WebOptimizer or move to npm + Vite.

### 4.6 Dependency injection

Phase 1 deliberately does **not** port Autofac. The migrated controllers in
Phase 1 have no constructor dependencies, so the built-in
`Microsoft.Extensions.DependencyInjection` container is sufficient.

```csharp
// Program.cs
var builder = WebApplication.CreateBuilder(args);
builder.Services.AddControllersWithViews();
// Phase 2 will add: builder.Services.AddDbContext<SampleWebAppDbContext>(...)
var app = builder.Build();
```

Service registration is grouped into extension methods per layer to keep
`Program.cs` short:

```csharp
// netcore/src/DataLayer/Startup/DataLayerServiceExtensions.cs (Phase 2)
public static IServiceCollection AddDataLayer(this IServiceCollection services, IConfiguration config) { ... }
```

`DiModelBinder` (action-parameter injection via Autofac) is **not** ported.
Phase 2 controllers will receive their dependencies via constructor injection.

---

## 5. End-to-end migration procedure (template for future controllers)

Use this checklist when migrating any controller in Phase 2+:

1. **Plan the slice.** Identify the controller's actions, the views they
   render, every model class touched, every service injected.
2. **Create the new controller** under `netcore/src/SampleWebApp/Controllers/`
   with `Microsoft.AspNetCore.Mvc.Controller` base class. Keep route shape
   identical so deep links keep working post cut-over.
3. **Port models.** Replace framework-specific APIs (see §4.3). Keep DTOs
   identical on the wire where possible.
4. **Port views.** Copy the `.cshtml` files into
   `netcore/src/SampleWebApp/Views/<ControllerName>/`. Update `_ViewImports`
   and tag-helper usage. Update asset paths to `wwwroot/`.
5. **Port any required services.** Register them in `Program.cs` or in an
   `*ServiceExtensions.cs` extension method.
6. **Add unit tests.** Follow the conventions in
   [`netcore/docs/testing.md`](../netcore/docs/testing.md) (xUnit + Moq +
   FluentAssertions). Aim for >90% line coverage on controller + model.
7. **Add integration tests** with `WebApplicationFactory<Program>` for every
   endpoint, asserting status code, content type, and rendered markers.
8. **Run locally** — see §6.
9. **Lint** — `dotnet format --verify-no-changes netcore/SampleWebApp.NetCore.sln`.
10. **Open a PR** following [Development-Workflow.md](Development-Workflow.md).
    Link the Jira ticket in the title.
11. **Self-review** against [CodeReview-Guidelines.md](CodeReview-Guidelines.md).

---

## 6. Build and run

Prerequisites: see [Onboarding.md](Onboarding.md).

```bash
# from repo root

# Restore + build
dotnet build netcore/SampleWebApp.NetCore.sln -c Debug

# Run the migrated app (default http://localhost:5000, https://localhost:5001)
dotnet run --project netcore/src/SampleWebApp

# Run all tests with coverage
dotnet test netcore/SampleWebApp.NetCore.sln \
  --collect:"XPlat Code Coverage" \
  --settings netcore/coverlet.runsettings
```

Visit:

- `http://localhost:5000/` → `Home/Index`
- `http://localhost:5000/Home/About` → ViewBag string
- `http://localhost:5000/Home/Contact`
- `http://localhost:5000/Home/Internals` → live runtime metrics
- `http://localhost:5000/Home/CodeView`

The legacy MVC5 app continues to be buildable with Visual Studio against
`SampleWebApp.sln` / IIS Express; nothing in Phase 1 changes that.

---

## 7. Success criteria (Phase 1)

These are restated from the Jira epic so reviewers can tick them off
directly from this doc:

- [ ] `HomeController` fully functional in .NET Core 6.
- [ ] All 5 action methods (`Index`, `About`, `Contact`, `Internals`,
      `CodeView`) return `200 OK` with `text/html` and render their views.
- [ ] Views render with correct styling (Bootstrap, layout, scripts).
- [ ] Unit tests passing with **>90% line coverage** on
      `Controllers/HomeController.cs` and `Models/InternalsInfo.cs`.
- [ ] Integration tests passing (every endpoint hits `200 OK`; `/Home/DoesNotExist` → `404`).
- [ ] CI pipeline (`.github/workflows/ci.yml`) green on `master`.
- [ ] Performance equal to or better than the .NET Framework version on a
      single-instance baseline (see `netcore/tests/SampleWebApp.PerformanceTests`).
- [ ] Migration patterns documented (this folder).
- [ ] Team trained — see [training/Phase1-Training-Agenda.md](training/Phase1-Training-Agenda.md).

---

## 8. What Phase 1 deliberately does *not* do

To keep Phase 1 small and reversible, the following are explicitly out of
scope and deferred to later phases:

- Migrating `BlogsController`, `PostsController`, `PostsAsyncController`,
  `TagsController`, `TagsAsyncController`.
- Migrating the `DataLayer` to EF Core (Phase 2).
- Replacing `GenericServices` (Phase 2 / 3).
- Replacing `AutoMapper 4.x` (Phase 2 — API changed substantially since 4.x).
- Migrating SignalR.
- Migrating `Microsoft.AspNet.Identity` (no auth is currently in use; was
  deliberately removed in commit `64c0585`).
- Replacing the legacy NUnit `Tests` project — Phase 1 adds a parallel
  xUnit suite under `netcore/tests/`. The legacy suite stays put.

Touching any of the above in a Phase 1 PR will get rejected in code review.
