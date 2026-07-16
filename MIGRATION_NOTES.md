# Migration Notes: ASP.NET MVC 5 (.NET Framework 4.5.1) → ASP.NET Core MVC (.NET 10)

This is a **re-platform**, not a version bump. The whole solution moves off `System.Web`,
Entity Framework 6, OWIN, and the classic MSBuild project format onto ASP.NET Core MVC,
EF Core, the built-in generic host, and SDK-style `net10.0` projects.

Projects (dependency order): `DataLayer` → `BizLayer` → `ServiceLayer` → `SampleWebApp`; `Tests` sits on top.

> ⚠️ **HIGHEST-RISK ITEM: `GenericServices` 1.0.9 → `EfCore.GenericServices`.** The public API
> shape has changed **substantially** (see §3). Every controller action and every DTO in
> `ServiceLayer` is affected. Coordinate the `ICrudServices` / DTO surface between the
> data/service subsession and the web subsession before finalizing.

---

## Legacy dependency → chosen replacement (summary)

| Legacy (packages.config, `net451`) | Replacement (`net10.0` `PackageReference`) |
|---|---|
| `EntityFramework` 6.1.3 (`System.Data.Entity`) | `Microsoft.EntityFrameworkCore` + `Microsoft.EntityFrameworkCore.SqlServer` + `Microsoft.EntityFrameworkCore.Design` (10.x) |
| `GenericServices` 1.0.9 (EF6-based) | `EfCore.GenericServices` (5.x) + `EfCore.GenericServices.AspNetCore` (for `CopyErrorsToModelState`) |
| `GenericLibsBase` 1.0.1 (`IGenericLogger`, `ISuccessOrErrors`) | `Microsoft.Extensions.Logging` (`ILogger<T>`) + EfCore.GenericServices' `IStatusGeneric` |
| `Autofac` 3.5 + `Autofac.Mvc5` 3.3.1 | Built-in `IServiceCollection` DI (simplest), or `Autofac` + `Autofac.Extensions.DependencyInjection` |
| `AutoMapper` 4.2.1 / 3.2.1 (BizLayer) | `AutoMapper` current (13.x) — but EfCore.GenericServices bundles/registers its own mapper; explicit AutoMapper only needed for the BizLayer if it still maps directly |
| `Microsoft.AspNet.Mvc` 5.2.3 + `System.Web.Mvc` | `Microsoft.AspNetCore.Mvc` (framework reference `Microsoft.AspNetCore.App`) |
| `System.Web.Optimization` + `WebGrease` + `Modernizr` + `Respond` (bundling) | `wwwroot/` static files + `<link>`/`<script>` tags (or `<environment>` tag helpers / LibMan). Modernizr/Respond are obsolete — drop. |
| OWIN (`Microsoft.Owin.*`, `Owin`) + `Microsoft.AspNet.Identity.*` | ASP.NET Core generic host `Program.cs`. **Identity is NOT wired up here** (see §4) — drop entirely. |
| `Microsoft.AspNet.SignalR` 2.0.3 (+ JS) | `Microsoft.AspNetCore.SignalR` — **but there is no server hub** (see §5). Drop the unused SignalR packages/JS. |
| `log4net` 2.0.3 | `Microsoft.Extensions.Logging` (Console/Debug); log4net still exists on Core but not worth keeping for a sample |
| `DelegateDecompiler.EntityFramework` 0.18 | `DelegateDecompiler.EntityFrameworkCore` (only if `[Computed]`/decompiled props are actually used — see §8) |
| `Newtonsoft.Json` 6.0.4 (`JsonNetResult`) | Built-in `System.Text.Json` / `return Json(...)`; `JsonNetResult` can be deleted |
| `Moq` 4.2 + `NUnit` 2.6.3 (`packages.config`) | `Moq` current + `NUnit` 4.x + `NUnit3TestAdapter` + `Microsoft.NET.Test.Sdk` |

---

## 1. `System.Web` / `System.Web.Mvc` — no equivalent in ASP.NET Core

`System.Web`, `System.Web.Mvc`, `System.Web.Routing`, `System.Web.Optimization` do not exist on
ASP.NET Core. Enumerated usages across the solution:

- **`HttpApplication` / `Global.asax` + `Application_Start`** — `SampleWebApp/Global.asax`,
  `SampleWebApp/Global.asax.cs` (`class MvcApplication : System.Web.HttpApplication`). → Delete both;
  replace with `Program.cs` (minimal hosting).
- **`AreaRegistration.RegisterAllAreas()`** — `Global.asax.cs:38`. → No areas in this app; drop.
- **`RouteTable.Routes` / `RouteConfig.RegisterRoutes` / `MapRoute` / `UrlParameter.Optional`** —
  `Global.asax.cs:40`, `SampleWebApp/App_Start/RouteConfig.cs`. → Replace with endpoint routing:
  `app.MapControllerRoute("default", "{controller=Home}/{action=Index}/{id?}")`.
- **`GlobalFilters.Filters` / `FilterConfig`** — `Global.asax.cs:39`, `SampleWebApp/App_Start/FilterConfig.cs`
  (registers `HandleErrorAttribute`). → Configure filters in `AddControllersWithViews(o => o.Filters.Add(...))`
  or rely on `UseExceptionHandler`. `HandleErrorAttribute` → `Microsoft.AspNetCore.Mvc.Filters` equivalent /
  developer exception page.
- **`BundleTable.Bundles` / `BundleConfig` / `System.Web.Optimization`** — `Global.asax.cs:41`,
  `SampleWebApp/App_Start/BundleConfig.cs`. → Delete; see §6 (static files).
- **`ModelBinders.Binders.DefaultBinder`** — `Global.asax.cs:44` sets the custom `DiModelBinder`. → See §7.
- **`MvcHtmlString`** — `SampleWebApp/Controllers/PostsController.cs:126`,
  `TagsController.cs:111`, `PostsAsyncController.cs`, `TagsAsyncController.cs`
  (`TempData["errorMessage"] = new MvcHtmlString(response.ErrorsAsHtml())`). → `MvcHtmlString` is gone.
  `TempData` can only hold simple serializable types on Core, so **store a plain `string`** and render it
  with `@Html.Raw(TempData["errorMessage"])` in the view (or use `IHtmlContent`/`HtmlString` where a view model holds it).
- **`System.Web` in controllers/infra** — `HomeController.cs`, `WebUiInitialise.cs`
  (`HttpApplication application`, `application.Server.MapPath(...)`), `ValidationHelper.cs`,
  `JsonNetResult.cs` (`System.Web.Mvc.ActionResult`). → `Server.MapPath` → `IWebHostEnvironment.ContentRootPath`/`WebRootPath`;
  `ActionResult`/`Controller` come from `Microsoft.AspNetCore.Mvc`.
- **`_Layout.cshtml` `@using SampleWebApp.Infrastructure`** and view helpers — move common usings into
  `Views/_ViewImports.cshtml` and add `@addTagHelper *, Microsoft.AspNetCore.Mvc.TagHelpers`.

**`CodeView` feature caveat:** `HomeController`/`Posts`/`Tags` `CodeView` actions plus
`Tests/Helpers/TestFileHelpers.cs` and `Models/InternalsInfo.cs` read **source files off disk** at runtime.
Paths were relative to the `System.Web` app root; re-point them at `IWebHostEnvironment.ContentRootPath`
(or accept that CodeView is cosmetic and stub it).

---

## 2. Entity Framework 6 → EF Core gotchas

Affected: `DataLayer/DataClasses/SampleWebAppDb.cs`, `EfConfiguration.cs`,
`DataLayer/Startup/DataLayerInitialise.cs`, `DataLayer/DataClasses/Concrete/*`.

- **`DbContext` base + `System.Data.Entity`** → `Microsoft.EntityFrameworkCore`. `SampleWebAppDb : DbContext, IGenericServicesDbContext` —
  `IGenericServicesDbContext` is an EF6-GenericServices interface; **remove it** (EfCore.GenericServices uses `DbContext` directly).
- **Connection-string-by-name ctor is gone.** `SampleWebAppDb() : base("name=SampleWebAppDb")` and
  `internal SampleWebAppDb(string connectionString) : base(connectionString)` no longer compile.
  → Single ctor `public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options)`.
  Connection string supplied via `AddDbContext<SampleWebAppDb>(o => o.UseSqlServer(config.GetConnectionString("SampleWebAppDb")))`.
- **`ValidateEntity` / `DbEntityValidationResult` / `DbValidationError` / `DbEntityEntry` do not exist in EF Core.**
  Used in `SampleWebAppDb.ValidateEntity` (lines 83–102) to enforce **`Tag.Slug` uniqueness**, and referenced in
  `Tests/Helpers/DummyIDbContextWithValidation.cs`, `Tests/UnitTests/Group01DataLayer/Test13Validation.cs`.
  → Two-part replacement:
  1. **Slug uniqueness** — add a **unique index** in `OnModelCreating`:
     `modelBuilder.Entity<Tag>().HasIndex(t => t.Slug).IsUnique();` **and/or** re-check inside an overridden
     `SaveChanges`/`SaveChangesAsync` (query `Tags.Any(x => x.TagId != tag.TagId && x.Slug == tag.Slug)`) to
     return a friendly validation error like the original message.
  2. **Data-annotation validation** (EF6 validated entities on save automatically; EF Core does **not**).
     `Post : IValidatableObject` and `[Required]`/`[MaxLength]` etc. were enforced by EF6 `SaveChanges`.
     On EF Core these are enforced by MVC ModelState on the way in, but the DataLayer's own
     `SaveChangesWithChecking()` (a GenericServices EF6 extension, see §3) must be re-implemented to call
     `Validator.TryValidateObject(...)` over changed entities if we want to preserve save-time validation.
- **`HandleChangeTracking` / `TrackUpdate`** — `SampleWebAppDb.HandleChangeTracking()` iterates
  `ChangeTracker.Entries()` and calls `TrackUpdate.UpdateTrackingInfo()` to stamp `LastUpdated`.
  `ChangeTracker.Entries()` **exists in EF Core** — port almost verbatim into the overridden
  `SaveChanges()`/`SaveChangesAsync(CancellationToken)`. **Bug to fix while porting:** the original uses
  `return;` inside the `foreach` (line 128) which aborts the loop on the first non-`TrackUpdate` entity —
  should be `continue;`.
- **Database initializers are gone.** `DataLayerInitialise.InitialiseThis` uses
  `Database.SetInitializer(new CreateDatabaseIfNotExists<...>())` / `NullDatabaseInitializer<...>`.
  → EF Core has no initializers. Use **migrations** (`dotnet ef migrations add InitialCreate`) applied via
  `context.Database.Migrate()` at startup, or `EnsureCreated()` for a sample.
- **`EfConfiguration : DbConfiguration` + `SqlAzureExecutionStrategy`** (`EfConfiguration.cs`) — `DbConfiguration`
  does not exist in EF Core. → Retry/resiliency is configured on the provider:
  `o.UseSqlServer(cs, sql => sql.EnableRetryOnFailure())`. Delete `EfConfiguration`.
- **Seeding (`ResetBlogs`)** — `DataLayerInitialise.ResetBlogs` deletes all Posts/Tags/Blogs then re-adds from XML,
  calling `context.SaveChangesWithChecking()` (GenericServices EF6 extension → replace, see §3). Loading XML via
  `Assembly.GetManifestResourceStream("DataLayer.Startup.Internal.BlogsContentSimple.xml")`
  (`LoadDbDataFromXml.cs`) still works, **but SDK-style projects compute embedded-resource logical names differently.**
  Ensure the two XMLs remain `<EmbeddedResource>` with the expected logical name (set
  `<EmbeddedResource Include="Startup/Internal/BlogsContentSimple.xml" LogicalName="DataLayer.Startup.Internal.BlogsContentSimple.xml" />`
  or verify `GetManifestResourceNames()` matches the hard-coded strings in `DataLayerInitialise`).
- **`Blog.Posts` / `Post.Tags` / `Tag.Posts` many-to-many** — EF Core 5+ supports skip navigations
  (`Post.Tags` ↔ `Tag.Posts`) with an auto join table; verify the generated join table name and the
  cascade behavior match expectations after the initial migration.

---

## 3. `GenericServices` 1.0.9 (EF6) → `EfCore.GenericServices` — API has changed substantially ⚠️

`GenericServices` 1.0.9 is EF6-based and **cannot** be used with EF Core. The maintained EF Core replacement is
**`EfCore.GenericServices`** (same author, Jon P Smith), but it is a **redesign**, not a drop-in. This touches
every controller action and every DTO.

### DTO base class change
- **Old:** DTOs derive from `EfGenericDto<TEntity, TDto>` (in `ServiceLayer/**`: `SimplePostDto`,
  `SimplePostDtoAsync`, `DetailPostDto`, `DetailPostDtoAsync`, `BlogListDto`, `TagListDto`; test DTOs
  `SimpleTagDto`, `SimpleTagDtoAsync`) and override `SupportedFunctions` (`CrudFunctions` enum),
  `SetupSecondaryData`, `CreateDataFromDto`, `UpdateDataFromDto`. They use `[DoNotCopyBackToDatabase]`,
  `GenericServices.Core`, `ISuccessOrErrors`.
- **New:** DTOs are POCOs implementing the **marker interface `ILinkToEntity<TEntity>`** (AutoMapper-based).
  No `EfGenericDto` base, no `CrudFunctions`, no `[DoNotCopyBackToDatabase]`. Read-shaping is controlled by
  property names / `[ReadOnly]` and AutoMapper conventions; per-DTO mapping tweaks go in a
  `PerDtoConfig<TDto, TEntity>` class.

### Service interface change (controllers)
| Old (per-operation interfaces, EF6) | New (`EfCore.GenericServices`) |
|---|---|
| `IListService.GetAll<TDto>()` | `ICrudServices.ReadManyNoTracked<TDto>()` (IQueryable) |
| `IDetailService.GetDetail<TDto>(id).Result` | `ICrudServices.ReadSingle<TDto>(id)` |
| `ICreateSetupService.GetDto<TDto>()` | no equivalent — `new TDto()` in the controller + populate dropdowns manually |
| `ICreateService.Create(dto)` | `ICrudServices.CreateAndSave(dto)` |
| `IUpdateSetupService.GetOriginal<TDto>(id).Result` | `ICrudServices.ReadSingle<TDto>(id)` |
| `IUpdateService.Update(dto)` | `ICrudServices.UpdateAndSave(dto)` |
| `IDeleteService.Delete<TEntity>(id)` | `ICrudServices.DeleteAndSave<TEntity>(id)` |
| `service.ResetDto(dto)` | no equivalent — re-populate the DTO's secondary data manually |

- **Async variants** (`PostsAsyncController`, `TagsAsyncController`, `*DtoAsync`) → `ICrudServicesAsync`
  with `...Async` methods (`ReadSingleAsync`, `CreateAndSaveAsync`, etc.).
- **Status object:** old `ISuccessOrErrors` (`.IsValid`, `.SuccessMessage`, `.Errors`, `.ErrorsAsHtml()`,
  `.CopyErrorsToModelState(ModelState, dto)`) → new `IStatusGeneric` exposed via the injected
  `ICrudServices` (`service.IsValid`, `service.GetAllErrors()`, `service.Message`). Use the
  **`EfCore.GenericServices.AspNetCore`** package for `service.CopyErrorsToModelState(ModelState, dto)` and
  the JSON/response helpers.
- **`SaveChangesWithChecking()`** (used in `DataLayerInitialise.ResetBlogs`) was a GenericServices EF6 extension.
  Replace with a plain `context.SaveChanges()` plus explicit validation if save-time validation is required (§2).

### Biggest behavioral gap — "secondary data" / setup pattern
`DetailPostDto.SetupSecondaryData` populates the **Blogger dropdown** and **Tags multi-select**
(`ServiceLayer/UiClasses/DropDownListType.cs`, `MultiSelectListType.cs`) and the overridden
`CreateDataFromDto`/`UpdateDataFromDto` translate the selected blog/tags back onto the `Post` entity.
`EfCore.GenericServices` has **no `SetupSecondaryData`/`ResetDto` hook**. Options:
1. Move the dropdown/multi-select population and the "map selected IDs → entity" logic **into the controller**
   (or a small hand-written service in `ServiceLayer`), calling `ICrudServices` only for the raw CRUD; **or**
2. Model `Post` create/update as a DDD-style method on the entity and use GenericServices' "call a method"
   feature. Given the sample's shape, **option 1 is lower-risk.**
This is the single most involved rewrite in `SampleWebApp/Controllers/PostsController.cs` +
`PostsAsyncController.cs` + `Views/Posts/*`. `Tags`/`Blogs` are simpler (direct entity/DTO CRUD).

### DI registration
- **Old:** Autofac modules auto-wire GenericServices (`ServiceLayerModule` registers
  `typeof(IListService).Assembly`).
- **New:** `services.GenericServicesSimpleSetup<SampleWebAppDb>(typeof(SimplePostDto).Assembly, ...)`
  (registers `ICrudServices`, the AutoMapper config from all DTO assemblies). See §7 for the broader DI move.

---

## 4. OWIN + `Microsoft.AspNet.Identity.*` → ASP.NET Core Identity

**Identity is referenced but NOT wired up.** `SampleWebApp/packages.config` lists
`Microsoft.AspNet.Identity.Core/EntityFramework/Owin`, all `Microsoft.Owin.*`, and `Owin`, **but**:
- There is **no `Startup.cs`**, no `[assembly: OwinStartup]`, no `ConfigureAuth`, no `IdentityModels`,
  no `ApplicationUser`/`ApplicationDbContext`, no `AccountController`/`ManageController`.
- `_Layout.cshtml` has `@*@Html.Partial("_LoginPartial")*@` **commented out**.
- `WebUiInitialise.cs` has a dead `ResetIndentityDatabase` const, never used.

→ **Action: drop OWIN + Identity entirely.** No ASP.NET Core Identity needs to be added. If auth is wanted
later, add `Microsoft.AspNetCore.Identity.EntityFrameworkCore` then, but it is out of scope for a faithful port.

---

## 5. `Microsoft.AspNet.SignalR` 2.x → ASP.NET Core SignalR

**No server-side hub exists.** Grep for `: Hub`, `IHubContext`, `GlobalHost`, `MapSignalR` → **zero matches**.
- SignalR packages are present in `SampleWebApp/packages.config` (`Microsoft.AspNet.SignalR*` 2.0.3) and the JS
  client `SampleWebApp/Scripts/jquery.signalR-2.0.3.min.js` exists, referenced only by the **ActionRunner**
  scripts (`Scripts/ActionRunnerComms.js`, `ActionRunnerUi.js`).
- The **ActionRunner bundle is commented out** in `BundleConfig.cs` (lines 63–68) and no view includes those
  scripts — the whole ActionRunner/SignalR feature was already removed from the live app.

→ **Action: drop the SignalR NuGet packages and the SignalR/ActionRunner JS.** If a future demo needs it,
use `Microsoft.AspNetCore.SignalR` (server `Hub` + `@microsoft/signalr` JS client — different server + client API).
No hub migration is required for this port.

---

## 6. Bundling → `wwwroot` + static files / tags

`System.Web.Optimization`, `WebGrease`, `Modernizr`, `Respond` and `@Scripts.Render`/`@Styles.Render`:
- `BundleConfig.cs` defines `~/bundles/javascript` (jquery, bootstrap, respond), `~/Content/css`
  (bootstrap.css, site.css), `~/bundles/jqueryval` (jquery.validate).
- View usages: `_Layout.cshtml:8` `@Styles.Render("~/Content/css")`, `_Layout.cshtml:57`
  `@Scripts.Render("~/bundles/javascript")`; `Views/Posts/Create.cshtml:63`, `Posts/Edit.cshtml:68`,
  `PostsAsync/Create.cshtml:63`, `PostsAsync/Edit.cshtml:66` `@Scripts.Render("~/bundles/jqueryval")`.

→ **Action:**
- Create `SampleWebApp/wwwroot/` and move `Content/` (→ `wwwroot/css`), `Scripts/` (→ `wwwroot/js`),
  `fonts/` (→ `wwwroot/lib/...` or keep under css). Delete obsolete `Modernizr`/`Respond`/`WebGrease`.
- Add `app.UseStaticFiles()` in `Program.cs`.
- Replace `@Styles.Render`/`@Scripts.Render` with plain `<link rel="stylesheet" href="~/css/...">` and
  `<script src="~/js/...">` (optionally wrapped in `<environment include="Development">` tag helpers, or use
  LibMan/`libman.json` to restore jquery/bootstrap/jquery-validation). jquery-validation-unobtrusive is still
  needed for client validation on the Create/Edit views.

---

## 7. DI: `Autofac.Mvc5` → built-in container (or `Autofac.Extensions.DependencyInjection`)

- **Autofac wiring:** `SampleWebApp/Infrastructure/AutofacDi.cs` builds a container from `ServiceLayerModule`,
  and `WebUiInitialise.cs` sets `DependencyResolver.SetResolver(new AutofacDependencyResolver(container))`
  (`Autofac.Integration.Mvc`). Modules: `DataLayer/Startup/DataLayerModule.cs`,
  `ServiceLayer/Startup/ServiceLayerModule.cs`, `BizLayer/Startup/BizLayerModule.cs`
  (+ `*Initialise.cs` helpers).
  → **Recommended: built-in `IServiceCollection`.** In `Program.cs`:
  `AddDbContext<SampleWebAppDb>(...)`, `GenericServicesSimpleSetup<SampleWebAppDb>(dtoAssemblies)`, and
  `builder.Services.Scan(...)`/explicit registration for any BizLayer services. The DataLayer/ServiceLayer/BizLayer
  `Module`/`Initialise` classes should be replaced by small `IServiceCollection` extension methods
  (e.g. `AddDataLayer`, `AddServiceLayer`). If keeping Autofac is preferred, use
  `Autofac.Extensions.DependencyInjection` + `UseServiceProviderFactory(new AutofacServiceProviderFactory())`
  and port the `Module`s (they mostly work unchanged).
- **Custom `DiModelBinder` / action-parameter service injection** — `SampleWebApp/Infrastructure/DiModelBinder.cs`
  overrides `CreateModel` to resolve **interface-typed action parameters** from the container
  (that is how `IListService service`, `IDetailService service`, `SampleWebAppDb db`, etc. get injected into
  controller actions). ASP.NET Core has this built in: annotate the action parameter with **`[FromServices]`**
  (e.g. `public IActionResult Index([FromServices] ICrudServices service)`), or inject via constructor. In
  recent ASP.NET Core, `[FromServices]` on the parameter is optional (implicit resolution can be enabled), but
  **be explicit** to preserve intent. → **Delete `DiModelBinder` and the `ModelBinders.Binders.DefaultBinder`
  line in `Global.asax.cs`; add `[FromServices]` to every controller action that took a service parameter.**

---

## 8. AutoMapper 4.x → current AutoMapper

- Old registration was implicit inside GenericServices (EF6) and `AutoMapper` 4.2.1 (BizLayer uses 3.2.1).
- New AutoMapper (13.x) requires **explicit configuration** (`MapperConfiguration`/profiles) and DI via
  `services.AddAutoMapper(assemblies)` if the BizLayer maps directly. **However**, `EfCore.GenericServices`
  registers and owns its own mapper for DTO↔entity projection, so for the CRUD DTOs you generally do **not**
  hand-configure AutoMapper — GenericServices does it. Only add explicit AutoMapper if BizLayer or a non-CRUD
  mapping needs it. Note the legacy `PostsCount` "aggregate" mapping (`TagListDto.PostsCount`) and
  `BloggerName` flattening rely on AutoMapper conventions — verify these project correctly under GenericServices.
- **`DelegateDecompiler`** (`DelegateDecompiler.EntityFramework` 0.18) is referenced in Data/Service/Tests
  packages but the DTOs here use plain computed getters (`LastUpdatedUtc`, `TagNames`) evaluated **in memory**,
  not `.Decompile()` in a query. If nothing calls `.Decompile()`/uses `[Computed]`, **drop DelegateDecompiler**;
  otherwise use `DelegateDecompiler.EntityFrameworkCore`.

---

## 9. Old-style `.csproj` → SDK-style `net10.0`

All five projects are classic MSBuild (`ToolsVersion="12.0"`, `TargetFrameworkVersion v4.5.1`,
`packages.config`, explicit `<Compile Include>`, `AssemblyInfo.cs`, `Web.config` transforms, HintPath refs).

→ **Action per project:** replace with SDK-style:
```xml
<Project Sdk="Microsoft.NET.Sdk">        <!-- Web: Microsoft.NET.Sdk.Web -->
  <PropertyGroup>
    <TargetFramework>net10.0</TargetFramework>
    <Nullable>disable</Nullable>          <!-- keep disabled to minimize churn -->
    <ImplicitUsings>disable</ImplicitUsings>
  </PropertyGroup>
  <ItemGroup> ...PackageReference / ProjectReference... </ItemGroup>
</Project>
```
Specifics:
- **`AssemblyInfo.cs`** — SDK-style auto-generates assembly attributes → set
  `<GenerateAssemblyInfo>false</GenerateAssemblyInfo>` **or** delete the `Properties/AssemblyInfo.cs`
  files to avoid duplicate-attribute errors.
- **`InternalsVisibleTo("Tests")`** — currently declared via `[assembly: InternalsVisibleTo("Tests")]`
  scattered in many files (`SampleWebAppDb.cs`, `DataLayerModule.cs`, `AutofacDi.cs`, several DTOs).
  Keep **one** declaration per project that needs it — cleanest in the project file:
  `<ItemGroup><InternalsVisibleTo Include="Tests" /></ItemGroup>` in `DataLayer.csproj` (and any other project
  whose internals the tests touch). Remove the duplicate source-level attributes to prevent CS0579.
- **`Web.config` / `Web.*.config` transforms** (`Web.Debug/Release/AzureRelease/WebWizRelease.config`) and
  `Properties/Settings.Designer.cs` (`HostTypeString`) → move connection string + app settings into
  `appsettings.json` + `appsettings.{Environment}.json`. Connection string today (from `Web.config`):
  `Data Source=(localdb)\mssqllocaldb;Initial Catalog=SampleWebAppDb;MultipleActiveResultSets=True;Integrated Security=SSPI;Trusted_Connection=True`
  (LocalDB is Windows-only; on the Linux VM use a real SQL Server instance / container connection string).
- **`.gitignore`/`.gitattributes`** already present; add `bin/`, `obj/` are already ignored.
- **Embedded resources** — carry over `BlogsContentSimple.xml` / `BlogsContextMedium.xml` as
  `<EmbeddedResource>` with matching logical names (§2).
- Delete the `packages/` folder and all `packages.config`, `App.config` files once converted.

---

## 10. Tests project

`Tests/Tests.csproj` (classic) + `packages.config` (NUnit 2.6.3, Moq 4.2, +MVC/SignalR/Owin refs).
- Convert to SDK-style `net10.0`; add `Microsoft.NET.Test.Sdk`, `NUnit` (4.x), `NUnit3TestAdapter`, `Moq` (current).
- **`DummyIDbContextWithValidation.cs` + `Test13Validation.cs`** rely on EF6 `DbEntityValidationResult`/
  `ValidateEntity` — rewrite against the EF Core Slug-uniqueness/`SaveChanges` approach (§2), or drop if the
  behavior moved to a DB unique index.
- Test DTOs `Helpers/SimpleTagDto.cs`, `SimpleTagDtoAsync.cs` derive from `EfGenericDto` → port to
  `ILinkToEntity<Tag>` like the ServiceLayer DTOs (§3).
- `Test11AutoFacModules.cs` tests the Autofac modules — rewrite for the new DI approach (build a
  `ServiceCollection`, assert `ICrudServices` etc. resolve), or remove if DI moved to built-in container.
- `Test10SetupBlogs` / `Test14ReadWriteBlogs` use `InternalsVisibleTo` + in-memory/LocalDB context — point them
  at an EF Core provider (SQLite in-memory or `Microsoft.EntityFrameworkCore.InMemory`) via `DbContextOptions`.
- Keep `[assembly: InternalsVisibleTo("Tests")]` working by declaring it in the producing projects' `.csproj` (§9).

---

## 11. Suggested subsession split (Phase 1)

- **A — Data + Service + Biz layers** (§2, §3, §7, §8, §9): SDK-style `net10.0`; EF6→EF Core; rewrite
  `SampleWebAppDb`; `GenericServices`→`EfCore.GenericServices` (DTOs → `ILinkToEntity<>`); AutoMapper; Autofac
  modules → `IServiceCollection` extensions. Publishes the `ICrudServices`/DTO surface the web app consumes.
- **B — Web app** (§1, §4, §5, §6, §7, §9): SDK-style `Microsoft.NET.Sdk.Web` `net10.0`;
  `Global.asax`+`App_Start`+OWIN → `Program.cs`; `Web.config`→`appsettings.json`; controllers → ASP.NET Core MVC
  (`[FromServices]`, `MvcHtmlString`→`Html.Raw`/`HtmlString`); drop SignalR/Identity; `wwwroot` + static files;
  Razor `_ViewImports`/`_ViewStart`. Depends on A's public API.
- **C — Tests** (§10): SDK-style `net10.0`; NUnit4/Moq/Test.Sdk; rewrite against EF Core `SampleWebAppDb` +
  `EfCore.GenericServices`; preserve `InternalsVisibleTo` via `DataLayer.csproj`.

After merge: `dotnet ef migrations add InitialCreate` for `SampleWebAppDb`, update `ResetBlogs` seeding, fix
`SampleWebApp.sln` (SDK-style project refs), ensure `dotnet build` + `dotnet test` pass on .NET 10, update `README.md`.

---

## 12. Verification (Phase 2)

- Install/verify **.NET 10 SDK** and a **SQL Server** instance (LocalDB is Windows-only; on this Linux VM use
  SQL Server for Linux / a container — `mssql-tools18` is already present under `/opt`). Apply the EF Core
  migration to create+seed `SampleWebAppDb`, `dotnet run` the web app, and exercise **Blogs/Posts/Tags CRUD**
  (list, details, create, edit, delete) end-to-end through the `EfCore.GenericServices` path; confirm persistence.
- No SignalR behavior to verify (feature already removed, §5).
- Record a screen video of the CRUD flows as proof and reference the artifact here / in the final summary.

---

## 13. Migration outcome (what was actually done)

Executed across three parallel child sessions off this branch, then merged and finalized here:

- **A — Data/Service/Biz** (PR #24): SDK-style `net10.0`; EF Core 10; `SampleWebAppDb(DbContextOptions<>)`;
  `HandleChangeTracking` ported into `SaveChanges`/`SaveChangesAsync` (the EF6 early-`return` bug fixed to
  `continue`); `Tag.Slug` uniqueness = unique index in `OnModelCreating` **plus** a pre-save duplicate check;
  `EfConfiguration`/initializers removed; DTOs re-based on `ILinkToEntity<T>`; the old `SetupSecondaryData`
  dropdown/multiselect lifecycle moved into a hand-written `IPostCrudHelper` (Post create/update run there,
  not through `ICrudServices.CreateAndSave`, so the many-to-many Tag + blogger selection and `IValidatableObject`
  rules are preserved); Autofac modules → `IServiceCollection` extensions (`AddDataLayer`/`AddServiceLayer`/`AddBizLayer`).
- **B — Web app** (PR #25): SDK-style `Microsoft.NET.Sdk.Web`; single `Program.cs` (minimal hosting, endpoint
  routing, `AddControllersWithViews`, `AddDbContext<SampleWebAppDb>(UseSqlServer)`); `Global.asax`/`App_Start`/OWIN/
  `DiModelBinder`/`WebUiInitialise`/`AutofacDi` all deleted; controllers on `Microsoft.AspNetCore.Mvc` with
  `[FromServices]`; `MvcHtmlString`→`Html.Raw`; a hand-written `CopyErrorsToModelState(IStatusGeneric)` (the
  `EfCore.GenericServices.AspNetCore` helper has no 10.x release); `Content`/`Scripts`/`fonts`→`wwwroot` with
  plain `<link>`/`<script>` tags; `_ViewImports.cshtml` added; `appsettings.json`+`appsettings.Development.json`.
- **C — Tests** (PR #26): SDK-style `net10.0`; NUnit 4 / Moq 4.20 / Test.Sdk 17.11; in-memory **SQLite**
  `SampleWebAppDb` (honors the Slug unique index); Autofac-module tests rebuilt as DI-extension tests;
  `ModelStateTester` re-implemented on ASP.NET Core validation; `DbSnapShot` join-count re-pointed at `PostTag`.
  **45/45 tests pass.** `InternalsVisibleTo("Tests")` restored via `DataLayer/Properties/AssemblyInfo.cs`
  (the `<InternalsVisibleTo>` MSBuild item is a no-op when `GenerateAssemblyInfo=false`).

Finalized on this branch: EF Core `InitialCreate` migration + `DesignTimeDbContextFactory`, README rewrite,
`BizLayer` re-added to `SampleWebApp.sln`. `dotnet build SampleWebApp.sln` → 0 errors; `dotnet test` → 45/45.

### Package versions
EF Core / EFCore.SqlServer / EFCore.Design / EFCore.Sqlite `10.0.0`; `EfCore.GenericServices 10.0.0`;
`AutoMapper 13.0.1`; NUnit `4.2.2`; NUnit3TestAdapter `4.6.0`; Microsoft.NET.Test.Sdk `17.11.1`; Moq `4.20.72`.

### Known build warnings (NU1903) — accepted, with rationale
- `AutoMapper 13.0.1` (GHSA-rvv3-g6hj-g44x / CVE-2026-32933): a DoS that requires ~25,000-level self-referential
  object graphs. `EfCore.GenericServices 10.0.0` depends on AutoMapper `13.0.1`; AutoMapper 14/15 introduce
  breaking API changes that break GenericServices' mapper. The app's flat DTOs cannot produce such graphs, so the
  version is kept to preserve verified-working mapping. Revisit when GenericServices supports the patched AutoMapper.
- `System.Security.Cryptography.Xml 9.0.0`: pulled **transitively, design-time only** via
  `Microsoft.EntityFrameworkCore.Design` (`PrivateAssets=all`); not shipped in the app output.
