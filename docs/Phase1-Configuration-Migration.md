# Phase 1 Configuration Migration

> Companion to [Phase1-Migration-Guide.md](Phase1-Migration-Guide.md).
> Scope: only the configuration touched by Phase 1 (`HomeController`).
> Anything not listed here is deferred to Phase 2.

This document maps every configuration concern in the legacy MVC5 app to its
ASP.NET Core 6 replacement.

---

## 1. Top-level mapping

| Legacy file / concept                | .NET Core 6 replacement                                | Phase 1 status |
|--------------------------------------|--------------------------------------------------------|----------------|
| `SampleWebApp/Web.config`            | `netcore/src/SampleWebApp/appsettings.json`            | Done           |
| `Web.Debug.config` / `Web.Release.config` transforms | `appsettings.Development.json` / `Production.json` / `Staging.json` | Done           |
| `SampleWebApp/Web.AzureRelease.config` / `Web.WebWizRelease.config` | Environment-specific `appsettings.<env>.json` + `ASPNETCORE_ENVIRONMENT` | Done           |
| `SampleWebApp/Properties/Settings.Settings` (`SampleWebApp.Properties.Settings`) | `IOptions<T>` typed configuration | Done           |
| `Log4Net.xml` + `log4net.Config.XmlConfigurator` | Built-in `ILogger<T>` + `builder.Logging`              | Done           |
| `Global.asax.cs` `Application_Start` | `Program.cs` top-level statements                      | Done           |
| `App_Start/RouteConfig.cs`           | `app.MapControllerRoute(...)`                          | Done           |
| `App_Start/FilterConfig.cs`          | `AddControllersWithViews(o => o.Filters.Add(...))`     | Done           |
| `App_Start/BundleConfig.cs`          | `wwwroot/` static files (no bundling in Phase 1)       | Done           |
| `Infrastructure/AutofacDi.cs`        | `builder.Services.Add*` calls / `*ServiceExtensions.cs`| Deferred (no DI consumers in Phase 1) |
| `Infrastructure/DiModelBinder.cs`    | Constructor injection                                  | Deferred       |
| `Infrastructure/WebUiInitialise.cs`  | `Program.cs` + extension methods                       | Done           |
| `packages.config`                    | `<PackageReference>` in `*.csproj`                     | Done           |

---

## 2. Connection strings

### Legacy (`SampleWebApp/Web.config`)

```xml
<connectionStrings>
  <add name="SampleWebAppDb"
       connectionString="Data Source=(localdb)\mssqllocaldb;Initial Catalog=SampleWebAppDb;MultipleActiveResultSets=True;Integrated Security=SSPI;Trusted_Connection=True"
       providerName="System.Data.SqlClient" />
</connectionStrings>
```

### .NET Core 6 (`netcore/src/SampleWebApp/appsettings.json`)

```json
{
  "ConnectionStrings": {
    "SampleWebAppDb": "Data Source=(localdb)\\mssqllocaldb;Initial Catalog=SampleWebAppDb;MultipleActiveResultSets=True;Integrated Security=SSPI;Trusted_Connection=True"
  }
}
```

Read with:

```csharp
var cs = builder.Configuration.GetConnectionString("SampleWebAppDb");
```

In Phase 1 nothing in the new app actually opens this connection — it is
preserved for Phase 2 (`DataLayer` migration to EF Core).

**Provider names** are no longer part of the connection string in EF Core.
You select the provider at registration time:
`options.UseSqlServer(connectionString)` (Phase 2).

---

## 3. App settings

### Legacy `<appSettings>`

```xml
<appSettings>
  <add key="webpages:Version" value="3.0.0.0" />
  <add key="webpages:Enabled" value="false" />
  <add key="ClientValidationEnabled" value="true" />
  <add key="UnobtrusiveJavaScriptEnabled" value="true" />
  <add key="owin:AutomaticAppStartup" value="false" />
</appSettings>
```

None of these have direct .NET Core 6 equivalents — `webpages:*` and `owin:*`
are legacy. Unobtrusive/client validation is on by default via the
`Microsoft.AspNetCore.Mvc.DataAnnotations` package included by
`AddControllersWithViews()`. Drop these keys.

### Legacy `Properties.Settings.HostTypeString`

`SampleWebApp/Properties/Settings.Settings` exposes a strongly-typed setting
read in `WebUiInitialise`:

```csharp
HostType = DecodeHostType(Settings.Default.HostTypeString); // LocalHost | WebWiz | Azure
```

### .NET Core 6 — typed options

```json
// appsettings.json
{
  "Hosting": {
    "HostType": "LocalHost"
  }
}
```

```csharp
// Options class
public sealed record HostingOptions
{
    public HostType HostType { get; init; } = HostType.LocalHost;
}

public enum HostType { LocalHost, WebWiz, Azure }

// Program.cs
builder.Services.Configure<HostingOptions>(builder.Configuration.GetSection("Hosting"));

// Usage in a controller / service
public sealed class SomeController : Controller
{
    private readonly HostingOptions _hosting;
    public SomeController(IOptions<HostingOptions> hosting) => _hosting = hosting.Value;
}
```

Phase 1 does not yet have a consumer for `HostingOptions`. The shape is
documented here so Phase 2 / 3 can adopt it without re-inventing.

### Environment-specific overrides

| Environment   | File                          | `ASPNETCORE_ENVIRONMENT` |
|---------------|-------------------------------|--------------------------|
| Development   | `appsettings.Development.json`| `Development`            |
| Staging       | `appsettings.Staging.json`    | `Staging`                |
| Production    | `appsettings.Production.json` | `Production`             |

These override values from `appsettings.json` on a per-key basis. Environment
variables override JSON files: `ConnectionStrings__SampleWebAppDb=...` works
out of the box.

---

## 4. Logging — log4net → `ILogger`

### Legacy

`SampleWebApp/Infrastructure/WebUiInitialise.cs`:

```csharp
case HostTypes.LocalHost:
case HostTypes.WebWiz:
    var log4NetPath = application.Server.MapPath(WebWizLog4NetRelPath);
    log4net.Config.XmlConfigurator.ConfigureAndWatch(new System.IO.FileInfo(log4NetPath));
    GenericLibsBaseConfig.SetLoggerMethod = name => new Log4NetGenericLogger(name);
    break;
case HostTypes.Azure:
    GenericLibsBaseConfig.SetLoggerMethod = name => new TraceGenericLogger(name);
    break;
```

`Log4Net.xml` (root of `SampleWebApp/`) configures appenders.

### .NET Core 6

`Program.cs` already calls `WebApplication.CreateBuilder(args)`, which adds:

- `Console`, `Debug`, `EventSource`, `EventLog` (Windows) providers.
- Reads `Logging:LogLevel` from configuration.

`appsettings.json`:

```json
{
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning",
      "Microsoft.EntityFrameworkCore": "Warning"
    }
  }
}
```

Per-environment overrides (e.g. `appsettings.Production.json` raising
`Default` to `Warning`) are documented in
[`netcore/docs/cicd.md`](../netcore/docs/cicd.md).

### Custom log4net pipeline

`Log4NetGenericLogger` and `TraceGenericLogger` are not ported — they exist
only to satisfy `GenericLibsBaseConfig.SetLoggerMethod`, which is part of
the legacy `GenericServices` plumbing not used in Phase 1.

If structured log output is required, add **Serilog** in a separate PR
(Phase 2 candidate). Until then, the built-in providers are sufficient.

---

## 5. Application startup

### Legacy

`Global.asax.cs`:

```csharp
protected void Application_Start()
{
    AreaRegistration.RegisterAllAreas();
    FilterConfig.RegisterGlobalFilters(GlobalFilters.Filters);
    RouteConfig.RegisterRoutes(RouteTable.Routes);
    BundleConfig.RegisterBundles(BundleTable.Bundles);

    ModelBinders.Binders.DefaultBinder = new DiModelBinder();
    WebUiInitialise.InitialiseThis(this);
}
```

### .NET Core 6 (`Program.cs`)

```csharp
var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllersWithViews();

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/Home/Error");
    app.UseHsts();
}

app.UseHttpsRedirection();
app.UseStaticFiles();
app.UseRouting();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

app.Run();

public partial class Program { }
```

Things removed and why:

| Removed                            | Why |
|------------------------------------|-----|
| `AreaRegistration.RegisterAllAreas` | Areas are auto-registered by `AddControllersWithViews()`. |
| `FilterConfig.RegisterGlobalFilters` | Filters register via `AddControllersWithViews(o => o.Filters.Add(...))`. No filters in Phase 1. |
| `BundleConfig.RegisterBundles`     | No bundling in Phase 1; `wwwroot/` files are served as-is. |
| `ModelBinders.Binders.DefaultBinder = new DiModelBinder()` | DI happens via constructor injection in Core; no custom default binder needed. |
| `WebUiInitialise.InitialiseThis`   | Logic split between `Program.cs` and per-layer service-extension methods. |

The `public partial class Program { }` line at the bottom is **load-bearing** —
it makes `Program` accessible to `WebApplicationFactory<Program>` in the
integration test project. See [`netcore/docs/testing.md`](../netcore/docs/testing.md).

---

## 6. Dependency injection — Autofac → built-in

### Legacy

`Infrastructure/AutofacDi.cs` builds an `IContainer`, `WebUiInitialise` passes
it to `AutofacDependencyResolver`, and the global model binder routes
action-method parameters through DI.

### Phase 1

`HomeController` has **zero** dependencies, so the built-in DI container is
all we need:

```csharp
builder.Services.AddControllersWithViews();
```

No `DiModelBinder` equivalent exists or is needed — controller constructor
parameters are resolved automatically by `Microsoft.Extensions.DependencyInjection`.

### Phase 2 / 3 — when DI gets interesting

When `DataLayer` and `BizLayer` land, the recommended grouping is per-layer
extension methods:

```csharp
// netcore/src/DataLayer/Startup/DataLayerServiceExtensions.cs
public static class DataLayerServiceExtensions
{
    public static IServiceCollection AddDataLayer(this IServiceCollection services, IConfiguration config)
    {
        services.AddDbContext<SampleWebAppDbContext>(o => o.UseSqlServer(config.GetConnectionString("SampleWebAppDb")));
        services.AddScoped<IBlogRepository, BlogRepository>();
        return services;
    }
}

// Program.cs
builder.Services
    .AddControllersWithViews()
    .Services
    .AddDataLayer(builder.Configuration)
    .AddBizLayer()
    .AddServiceLayer();
```

If Autofac becomes necessary (multi-tenancy, conditional registrations,
named services), add it via `Autofac.Extensions.DependencyInjection`:

```csharp
builder.Host.UseServiceProviderFactory(new AutofacServiceProviderFactory());
builder.Host.ConfigureContainer<ContainerBuilder>(cb => cb.RegisterModule(new MyModule()));
```

Defer this decision until Phase 2 — do not add Autofac speculatively.

---

## 7. Static files and bundling

### Legacy

- `Content/`, `Scripts/`, `fonts/`, `favicon.ico` served via IIS static
  handlers.
- `BundleConfig` produces minified bundles served from `~/bundles/...`.

### .NET Core 6

- `wwwroot/` is the only directory served as static content by
  `app.UseStaticFiles()`.
- Bundling: not done in Phase 1. If we need it later:
  - **LibMan + WebOptimizer** (Microsoft, integrates with ASP.NET Core).
  - **npm + Vite/esbuild** (heavier toolchain, full JS pipeline).
- Asset cache busting in views uses tag helpers:
  `<link rel="stylesheet" href="~/css/site.css" asp-append-version="true" />`.

Mapping:

| Legacy path                                    | New path                          |
|------------------------------------------------|-----------------------------------|
| `SampleWebApp/Content/Site.css`                | `netcore/src/SampleWebApp/wwwroot/css/site.css` |
| `SampleWebApp/Content/bootstrap.css`           | `netcore/src/SampleWebApp/wwwroot/lib/bootstrap/dist/css/bootstrap.css` |
| `SampleWebApp/Scripts/jquery-*.js`             | `netcore/src/SampleWebApp/wwwroot/lib/jquery/jquery.js` |
| `SampleWebApp/Scripts/site.js`                 | `netcore/src/SampleWebApp/wwwroot/js/site.js` |
| `SampleWebApp/fonts/*`                         | `netcore/src/SampleWebApp/wwwroot/lib/bootstrap/dist/fonts/*` |
| `SampleWebApp/favicon.ico`                     | `netcore/src/SampleWebApp/wwwroot/favicon.ico` |

---

## 8. NuGet packages — Phase 1 subset

Only the packages actually consumed by the .NET Core `HomeController` are
listed. The full migration matrix lives on the NET-10 infra branch's
`docs/PackageMigration.md` and will be re-published when NET-10 merges.

| Legacy package                          | Phase 1 status            | Replacement |
|-----------------------------------------|---------------------------|-------------|
| `Microsoft.AspNet.Mvc` 5.2.3            | Removed (new project SDK) | `Microsoft.NET.Sdk.Web` |
| `Microsoft.AspNet.Razor` 3.2.3          | Removed                   | Included in `Microsoft.NET.Sdk.Web` |
| `Microsoft.AspNet.WebPages` 3.2.3       | Removed                   | n/a |
| `Microsoft.AspNet.Web.Optimization` 1.1.3 | Removed                 | `wwwroot/` static files; LibMan if bundling needed |
| `bootstrap` 3.3.2                       | Kept                      | Bootstrap 5 upgrade is a separate ticket |
| `jQuery` 1.10.2                         | Kept                      | jQuery 3.x upgrade is a separate ticket |
| `Microsoft.jQuery.Unobtrusive.Validation` 3.2.3 | Kept              | Library script under `wwwroot/lib/` |
| `log4net` 2.0.3                         | Removed (Phase 1)         | Built-in `ILogger<T>` |
| `Autofac` / `Autofac.Mvc5`              | Removed (Phase 1)         | Built-in DI; revisit in Phase 2 |
| `EntityFramework` 6.1.3                 | Not in Phase 1 scope      | `Microsoft.EntityFrameworkCore` 6.x (Phase 2) |
| `AutoMapper` 4.2.1                      | Not in Phase 1 scope      | `AutoMapper` 12.x (Phase 2 — API changed) |
| `GenericServices` 1.0.9                 | Not in Phase 1 scope      | Replacement strategy TBD (Phase 2 / 3) |
| `Microsoft.AspNet.SignalR.*` 2.0.3      | Not in Phase 1 scope      | `Microsoft.AspNetCore.SignalR` (Phase 4) |
| `Microsoft.AspNet.Identity.*`           | N/A                       | Identity was removed from the legacy app already (commit `64c0585`). |

---

## 9. Configuration anti-patterns to avoid

- **Don't** read `IConfiguration` ad-hoc inside controllers or services.
  Use typed `IOptions<T>`.
- **Don't** call `builder.Configuration["Foo:Bar"]` in tests — use
  `WebApplicationFactory.WithWebHostBuilder(b => b.ConfigureAppConfiguration(...))`
  to override.
- **Don't** add new keys to `appsettings.json` without binding them to an
  options class — that is how typos turn into silent missing config.
- **Don't** mix log4net and `ILogger<T>` after Phase 1. The migrated app is
  `ILogger` only.
- **Don't** persist secrets in `appsettings.json`. Use `dotnet user-secrets`
  locally, environment variables in CI, and a secret store in production.
