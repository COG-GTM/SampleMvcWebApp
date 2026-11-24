# Migration plan: SampleMvcWebApp → ASP.NET Core MVC on .NET 10

Scope sources: DeepWiki (`COG-GTM/SampleMvcWebApp` is not indexed, so the upstream `JonPSmith/SampleMvcWebApp` wiki was queried) cross-checked with a full grep of the code. DeepWiki found only 1 `System.Web` file; the grep below found 20.

## 1. Current stack

| Area | Today |
|---|---|
| Runtime | .NET Framework 4.5.1, old-style `.csproj` + `packages.config` (Windows/IIS only) |
| Web | ASP.NET MVC 5.2.3, Razor 3, `Global.asax`, `App_Start/*`, `Web.config` (+4 transforms), System.Web.Optimization bundles |
| Data | Entity Framework 6.1.3, `CreateDatabaseIfNotExists` initializer, LocalDB |
| Services | GenericServices 1.0.9 (+ GenericLibsBase, DelegateDecompiler, AutoMapper 4.2.1) |
| DI | Autofac 3.5 + Autofac.Mvc5, `DiModelBinder` injects services into action params |
| Logging | log4net 2.0.3 / Trace via `IGenericLogger` |
| Unused refs | ASP.NET Identity 2.1, OWIN 3.0 (cookies/Facebook/Google/…), SignalR 2.0.3 — referenced, never used |
| Tests | NUnit 2.6.3, Moq 4.2, LocalDB `TestSampleWebAppDb` |
| CI | none |

### Projects and dependencies

| Project | In .sln | References | Key packages |
|---|---|---|---|
| DataLayer | yes | – | EF 6.1.3, GenericServices, Autofac, AutoMapper, DelegateDecompiler |
| BizLayer | **no** | DataLayer | EF 6.1.1, GenericServices beta, Autofac (empty placeholder project) |
| ServiceLayer | yes | DataLayer | EF 6.1.3, GenericServices, Autofac, Newtonsoft.Json |
| SampleWebApp | yes | DataLayer, ServiceLayer | MVC 5, EF6, GenericServices, Autofac.Mvc5, log4net, Identity/OWIN/SignalR, jQuery/Bootstrap NuGet content |
| Tests | yes | DataLayer, ServiceLayer, SampleWebApp | NUnit 2.6.3, Moq, EF6, Autofac, MVC 5 |

### Files using `System.Web` (20)

- `SampleWebApp/Global.asax.cs`, `App_Start/RouteConfig.cs`, `App_Start/FilterConfig.cs`, `App_Start/BundleConfig.cs`
- `SampleWebApp/Controllers/` Home, Blogs, Posts, PostsAsync, Tags, TagsAsync
- `SampleWebApp/Infrastructure/` WebUiInitialise, DiModelBinder, ValidationHelper, JsonNetResult
- `SampleWebApp/Views/Shared/Error.cshtml`, `SampleWebApp/Web.config`, `SampleWebApp/Views/Web.config`
- `Tests/Helpers/JsonHelper.cs`, `Tests/Helpers/ModelStateTester.cs`, `Tests/App.config`
- `.vs/config/applicationhost.config` (IIS Express, delete)

## 2. Target stack

| Area | Target |
|---|---|
| Runtime | .NET 10 (`net10.0`), SDK-style projects, `PackageReference` |
| Web | ASP.NET Core MVC, `Program.cs` minimal hosting, `appsettings.json`, static files in `wwwroot` |
| Data | EF Core 10 (SqlServer), `InitialCreate` migration applied at startup |
| Services | EfCore.GenericServices 10 (`ICrudServices` / `ICrudServicesAsync`), AutoMapper pinned to 14.0.0 (15+ breaks EfCore.GenericServices at runtime) |
| DI | built-in `IServiceCollection` (`AddServiceLayer` extension), `[FromServices]` in actions |
| Logging | `Microsoft.Extensions.Logging` |
| Tests | NUnit 4 + Moq on net10.0 against real SQL Server (own test database) |
| CI | GitHub Actions: build + test on PRs with SQL Server 2022 service container |

## 3. Changes project by project

**DataLayer**
- SDK-style net10.0; EF Core 10 `SqlServer` + `Design`.
- `SampleWebAppDb`: options-based ctor; port `HandleChangeTracking` (`TrackUpdate.LastUpdated`) into `SaveChanges`/`SaveChangesAsync`; Post↔Tag many-to-many via skip navigations.
- EF6 `ValidateEntity` (Tag slug uniqueness) → unique index on `Tag.Slug` + pre-save check that returns a validation error (keeps the same user-visible message).
- Replace `EfConfiguration`/`SqlAzureExecutionStrategy` with `EnableRetryOnFailure` when `isAzure`.
- `DataLayerInitialise`: `Database.Migrate()` instead of EF6 initializers; `ResetBlogs` wrapped in execution strategy + transaction. XML seed loader unchanged.
- Add `Migrations/InitialCreate` (dotnet-ef tool manifest). Autofac `DataLayerModule` → `AddDataLayer(...)`.

**BizLayer** — SDK-style net10.0, drop EF6/GenericServices/Autofac, add to the solution (it is an empty placeholder today; kept so the layer structure is unchanged).

**ServiceLayer**
- SDK-style net10.0; EfCore.GenericServices 10 + AutoMapper 14.0.0.
- Rewrite DTOs (`SimplePostDto(Async)`, `DetailPostDto(Async)`, `BlogListDto`, `TagListDto`) from GenericServices `EfGenericDto` to EfCore.GenericServices `ILinkToEntity<>` DTOs; keep the dropdown/multi-select helpers (`DropDownListType`, `MultiSelectListType`) for Bloggers/Tags.
- `ServiceLayerModule` → `AddServiceLayer(IServiceCollection, connectionString, isAzure)` (registers DbContext + GenericServices).

**SampleWebApp**
- `Microsoft.NET.Sdk.Web` net10.0. Delete `Global.asax(.cs)`, `App_Start/*`, `Web*.config`, `Views/Web.config`, `packages.config`, `AssemblyInfo`, `Settings.*`, `AutofacDi`, `DiModelBinder`, log4net loggers, `.vs/`.
- `Program.cs`: `AddControllersWithViews`, `AddServiceLayer`, exception handler + HSTS + HTTPS redirect outside Development, static files, default route, DB migrate/seed on startup, `public partial class Program`.
- `appsettings.json` (empty connection string, host type) + `appsettings.Development.json` (local Docker SQL Server).
- Controllers: `System.Web.Mvc` → `Microsoft.AspNetCore.Mvc`, services via `[FromServices]`, `ValidationHelper` rewritten for `ModelStateDictionary` + `IStatusGeneric`, `JsonNetResult` → `Json(...)`.
- Views: `_ViewImports.cshtml` with tag helpers; bundles → direct `<script>/<link>` to `wwwroot/lib`; `MvcHtmlString` → `IHtmlContent`; Content/Scripts/fonts → `wwwroot`.

**Tests**
- SDK-style net10.0, NUnit 4, NUnit3TestAdapter, Microsoft.NET.Test.Sdk, current Moq.
- Own connection string `ConnectionStrings__SampleWebAppDbTest` (default `SampleWebAppDb-Test`) so tests never wipe the app DB.
- Port DataLayer, ServiceLayer (GenericServices → EfCore.GenericServices), DI (Autofac module tests → `IServiceCollection` resolution tests) and MVC validation tests; drop tests of removed infrastructure (Autofac-specific behaviour, MVC5 model binder).

**Repo** — `SampleWebApp.sln` updated (all 5 projects), `.gitignore` for bin/obj, README run instructions, `.github/workflows/ci.yml`.

## 4. Risks

| Risk | Mitigation |
|---|---|
| GenericServices → EfCore.GenericServices is an API rewrite (every controller + DTO) | Keep controller actions/URLs/views identical; verify each CRUD page in the browser |
| AutoMapper 15/16 break EfCore.GenericServices | Pin 14.0.0, accept NU1903 advisory warning, document it |
| EF6 `ValidateEntity` has no EF Core equivalent | Unique index + explicit pre-save check |
| Core validation reads computed getters (e.g. `TagNames`) → 500 on POST | Null-safe getters |
| Failed save leaves entities tracked | `ChangeTracker.Clear()` on failure |
| Existing EF6-created DBs can't be upgraded by `InitialCreate` (`TagPosts` join table, no history table) | Document: use a fresh database |
| Tests reset the DB | Separate test database / connection string |
| `sa` password in config | Only in `appsettings.Development.json` and CI env, never in `appsettings.json` |
| Success-message wording changes (EfCore.GenericServices) | Accept, list as a known difference |
| Live site `CodeView` pages show legacy code snippets | Keep pages rendering; content stays explanatory |

## 5. How it will be tested

1. Start SQL Server 2022 in Docker (`mssql`, port 1433).
2. `dotnet build` — 0 errors.
3. `dotnet test` — all NUnit tests green against the real SQL Server test database.
4. Run the app (`ASPNETCORE_ENVIRONMENT=Development dotnet run --project SampleWebApp --urls http://localhost:5000`), reset data via Posts › Reset (expect 4 blogs / 17 posts / 8 tags), then in the browser check create / edit / delete for **Blogs, Posts and Tags** (plus the async Posts/Tags pages and duplicate-slug validation).
5. CI: `.github/workflows/ci.yml` runs `dotnet build` + `dotnet test` on PRs with a SQL Server service container; PR to `master` must be green.

Out of scope: no merge to `master`, no Windows baseline run, no child sessions.
