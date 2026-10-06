# MIGRATION_NOTES — ASP.NET MVC 5 / .NET Framework 4.5.1 → ASP.NET Core MVC / .NET 10

Phase 0 output. Every later phase and every subsession must read this file first. It lists the
gotchas found in **this** solution, the affected files, the replacement chosen for each legacy
dependency, and (§11) the binding API contract between subsessions A, B and C.

Global decisions: every project is SDK-style, `net10.0`, `PackageReference`, `ImplicitUsings`
enabled, `Nullable` **disabled** (legacy code is not null-annotated). Built-in
`Microsoft.Extensions.DependencyInjection` replaces Autofac. SQL Server stays the database.

---

## 0. Highest risk: GenericServices 1.0.9 (EF6) → EfCore.GenericServices 10.0.0

**The API shape changed substantially. This is a rewrite of every controller and every DTO, not a rename.**
GenericServices 1.0.9 only works with EF6; it cannot be used with EF Core.
EfCore.GenericServices 10.0.0 (net10.0 only) depends on `Microsoft.EntityFrameworkCore 10.0.0`,
`AutoMapper >= 13.0.1`, `GenericServices.StatusGeneric 1.2.0`, `Microsoft.AspNetCore.JsonPatch 10.0.0`.

| Legacy (GenericServices 1.0.9) | EfCore.GenericServices 10.0.0 |
|---|---|
| Seven+ separate services injected per action: `IListService`, `IDetailService`, `IUpdateSetupService`, `IUpdateService`, `ICreateSetupService`, `ICreateService`, `IDeleteService` (+ `*Async`) | **One** facade: `ICrudServices` (sync) / `ICrudServicesAsync` (async), namespace `GenericServices`. The facade is itself the status (`IStatusGeneric`: `IsValid`, `Errors`, `Message`). |
| `IListService.GetAll<TDto>()` | `ReadManyNoTracked<TDto>()` → `IQueryable<TDto>` (AutoMapper `ProjectTo`) |
| `IDetailService.GetDetail<T>(id)` → `ISuccessOrErrors<T>` (`.Result`) | `ReadSingle<T>(id)` / `await ReadSingleAsync<T>(id)` → `T` (null + `IsValid==false` if not found) |
| `IUpdateSetupService.GetOriginal<T>(id)`, `ICreateSetupService.GetDto<T>()` (call `SetupSecondaryData`) | **No equivalent.** Read with `ReadSingle`, then populate dropdowns yourself (ServiceLayer service, §11). |
| `IUpdateService.Update(dto)`, `ICreateService.Create(dto)` → `ISuccessOrErrors` | `UpdateAndSave(dto)`, `CreateAndSave(dto)`; result on the service (`IsValid`, `Message`, `Errors`) |
| `ResetDto(dto)` (re-populate dropdowns after a validation failure) | **No equivalent.** ServiceLayer must provide it. |
| `IDeleteService.Delete<TEntity>(id)` | `DeleteAndSave<TEntity>(id)` |
| DTO base classes `EfGenericDto<TEntity,TDto>` / `EfGenericDtoAsync<,>` with virtual hooks `SupportedFunctions`, `SetupSecondaryData`, `CreateDataFromDto`, `UpdateDataFromDto` | DTOs are POCOs implementing marker `ILinkToEntity<TEntity>`. Optional `PerDtoConfig<TDto,TEntity>` (AutoMapper `AlterReadMapping`/`AlterSaveMapping`). **No base class, no hooks** → Post↔Tag many-to-many + Blogger selection logic in `DetailPostDto(Async)` must move into a ServiceLayer service. `[ReadOnly(true)]` replaces `[DoNotCopyBackToDatabase]`. |
| `IGenericServicesDbContext` on `SampleWebAppDb` | Plain `DbContext`; registered with `services.GenericServicesSimpleSetup<SampleWebAppDb>(config, assemblies)` (namespace `GenericServices.Setup`; config type `GenericServices.Configuration.GenericServicesConfig`). |
| `ISuccessOrErrors` / `SuccessOrErrors` (GenericLibsBase) | `IStatusGeneric` / `StatusGenericHandler` (namespace `StatusGeneric`). Errors are `ValidationResult`s with `MemberNames`. |
| `CopyErrorsToModelState`, `ErrorsAsHtml` (MVC5 helpers) | Not in the core package; `EfCore.GenericServices.AspNetCore` targets ASP.NET Core 2.x only → we write our own `CopyErrorsToModelState(IStatusGeneric, ModelStateDictionary, dto)` / `ErrorsAsHtml` in `SampleWebApp/Infrastructure/ValidationHelper.cs`. |
| `SaveChangesWithChecking()` (EF6 `ValidateEntity`) | `GenericServicesConfig { DtoAccessValidateOnSave = true, DirectAccessValidateOnSave = true }` → `*AndSave` runs DataAnnotations + `IValidatableObject` and returns errors in status instead of throwing. Slug uniqueness is not a DataAnnotation → see §2. |
| Lifetimes (Autofac default: instance per dependency) | `ICrudServices`/`ICrudServicesAsync` registered transient; share the scoped `SampleWebAppDb`. Status accumulates per instance → one CRUD call per resolved instance. |

### 0.1 AutoMapper must be pinned to **14.0.0** (verified)
Probe (throw-away console, .NET 10, EfCore.GenericServices 10.0.0, SQLite in-memory, `GenericServicesSimpleSetup` + `CreateAndSave` + `ReadManyNoTracked`):

| AutoMapper | Result |
|---|---|
| 16.2.0 | `MethodAccessException: ... SetupDtosAndMappings.CreateConfigAndMapper ... AutoMapper.MapperConfiguration..ctor(Action<IMapperConfigurationExpression>)` |
| 15.1.3 | same `MethodAccessException` |
| 14.0.0 | works (`Successfully read many Tag Dto`) |

AutoMapper 15 made that ctor non-public; EfCore.GenericServices 10.0.0 still calls it. Pin
`<PackageReference Include="AutoMapper" Version="14.0.0" />` explicitly in ServiceLayer. NuGet reports
**NU1903** (GHSA-rvv3-g6hj-g44x, recursion on deep/cyclic object graphs) for 14.0.0 — accepted: only our own
DTO/entity types are mapped. Remove the pin when EfCore.GenericServices ships an AutoMapper 15+ build.

---

## 1. `System.Web` / `System.Web.Mvc` — no equivalent in ASP.NET Core
`grep -rE "System\.Web|HttpApplication|Global\.asax|AreaRegistration|RouteTable|BundleTable|ModelBinders|MvcHtmlString"`:

| File | Legacy usage | Replacement |
|---|---|---|
| `SampleWebApp/Global.asax(.cs)` | `MvcApplication : HttpApplication`, `AreaRegistration.RegisterAllAreas`, `GlobalFilters`, `RouteTable.Routes`, `BundleTable.Bundles`, `ModelBinders.Binders.DefaultBinder = new DiModelBinder()`, `WebUiInitialise.InitialiseThis(this)` | Delete. Single `Program.cs` (minimal hosting). No areas exist. |
| `SampleWebApp/App_Start/RouteConfig.cs` | `MapRoute("{controller}/{action}/{id}")`, `IgnoreRoute("{resource}.axd/...")` | `app.MapControllerRoute("default", "{controller=Home}/{action=Index}/{id?}")`. Delete. |
| `SampleWebApp/App_Start/FilterConfig.cs` | `HandleErrorAttribute` | `app.UseExceptionHandler("/Home/Error")` outside Development. Delete. |
| `SampleWebApp/App_Start/BundleConfig.cs` | `System.Web.Optimization` bundles | wwwroot static files (§6). Delete. |
| `SampleWebApp/Controllers/*.cs` (6) | `System.Web.Mvc.Controller`, `ActionResult`, `MvcHtmlString`, interface-typed action params resolved by `DiModelBinder` | `Microsoft.AspNetCore.Mvc.Controller`, `IActionResult`, `[FromServices]` (§7). |
| `Controllers/{Posts,PostsAsync,Tags,TagsAsync}Controller.cs` | `TempData["errorMessage"] = new MvcHtmlString(response.ErrorsAsHtml())` | **Gotcha:** Core TempData (cookie provider, System.Text.Json) only round-trips primitives → store the **string**, render with `@Html.Raw(TempData["errorMessage"])` in `Views/{Tags,TagsAsync,Posts,PostsAsync}/Index.cshtml`. |
| `SampleWebApp/Infrastructure/DiModelBinder.cs` | `DefaultModelBinder.CreateModel` override resolving interfaces + `SampleWebAppDb` from `DependencyResolver` | Delete; `[FromServices]`. |
| `SampleWebApp/Infrastructure/WebUiInitialise.cs` | `InitialiseThis(HttpApplication)`, `Server.MapPath`, log4net, `Settings.Default.HostTypeString`, `DependencyResolver.SetResolver(AutofacDependencyResolver)` | `Program.cs` + `IConfiguration["HostType"]` + `ILogger`. |
| `SampleWebApp/Infrastructure/AutofacDi.cs` | Autofac container | Delete (built-in DI). |
| `SampleWebApp/Infrastructure/ValidationHelper.cs` | `System.Web.Mvc.ModelStateDictionary`, `JsonResult{Data=..}`, `ISuccessOrErrors` | `Microsoft.AspNetCore.Mvc.ModelBinding.ModelStateDictionary`, `new JsonResult(value)`, `IStatusGeneric`. |
| `SampleWebApp/Infrastructure/JsonNetResult.cs` | `ControllerContext`, `HttpResponseBase.Output` | Unused by controllers → delete (Core `JsonResult` uses System.Text.Json). |
| `SampleWebApp/Infrastructure/{Log4Net,Trace}GenericLogger.cs`, `Log4Net.xml` | GenericLibsBase loggers | Delete; `Microsoft.Extensions.Logging`. |
| `SampleWebApp/Models/InternalsInfo.cs` | `PerformanceCounter` (Windows-only, not in .NET 10 base) | Use `GC.GetGCMemoryInfo()` / `Environment` for "available MB". |
| `SampleWebApp/Views/Shared/Error.cshtml` | `@model System.Web.Mvc.HandleErrorInfo` | Model-less / `ErrorViewModel` with RequestId. |
| `SampleWebApp/Views/Web.config` | Razor namespaces (`System.Web.Mvc*`, `System.Web.Optimization`) | `Views/_ViewImports.cshtml` with `@using SampleWebApp` etc. + `@addTagHelper *, Microsoft.AspNetCore.Mvc.TagHelpers`. Delete Web.config. |
| `Tests/Helpers/{JsonHelper,ModelStateTester}.cs` | `System.Web.Mvc.JsonResult/ModelStateDictionary` | Core types (`JsonResult.Value`). |

**(C)** Core `ModelStateDictionary` enumerates keys in its internal prefix-tree order, not insertion order (MVC 5 kept insertion order), so `ReturnModelErrorsAsJson` can emit `errorsDict` properties in a different order. Tests compare the deserialized dictionary instead of the raw JSON string.

## 2. Entity Framework 6.1.3 → EF Core 10 (`DataLayer`)
- `DataLayer/DataClasses/SampleWebAppDb.cs`:
  - `ValidateEntity(DbEntityEntry, IDictionary)`, `DbEntityValidationResult`, `DbValidationError` **do not exist** in EF Core (EF Core does no validation on save). The Tag-Slug uniqueness check is re-implemented in three places:
    1. `HasIndex(t => t.Slug).IsUnique()` in `OnModelCreating` — database guarantee.
    2. A public `IStatusGeneric CheckTagSlugsUnique()`-style check inside `SampleWebAppDb.SaveChangesWithChecking(Async)` (DataAnnotations + `IValidatableObject` + slug check, never throws for validation) — for direct callers (seeding, tests).
    3. EfCore.GenericServices hook `GenericServicesConfig.BeforeSaveChanges` (signature `IStatusGeneric (DbContext)`; if it returns errors, SaveChanges is not called) wired to the same slug check, so `CreateAndSave`/`UpdateAndSave` return the legacy error (member `Slug`, message `The Slug on tag '{Name}' must be unique and is already being used.`) instead of a 500 from the unique index's `DbUpdateException`. (`SaveChangesExceptionHandler` exists as a fallback for SQL errors 2601/2627 but is not needed.)
  - `HandleChangeTracking()` / `TrackUpdate.UpdateTrackingInfo()` → override `SaveChanges(bool)` and `SaveChangesAsync(bool, CancellationToken)` (the EF6 `SaveChangesAsync()` overload signature is gone). **Legacy bug:** the loop `return`s on the first non-`TrackUpdate` entry instead of `continue` — fix to `continue`.
  - Connection-string-by-name ctor `base("name=SampleWebAppDb")` is gone → `SampleWebAppDb(DbContextOptions<SampleWebAppDb> options)`; the string comes from `ConnectionStrings:SampleWebAppDb` in appsettings via `AddDbContext`. Keep `internal const string NameOfConnectionString`→ make `public const`.
  - `[assembly: InternalsVisibleTo("Tests")]` → `<InternalsVisibleTo Include="Tests" />` in DataLayer.csproj (also ServiceLayer, SampleWebApp, which declared it in code too).
- `DataLayer/DataClasses/EfConfiguration.cs` (`DbConfiguration`, `SqlAzureExecutionStrategy`) → `UseSqlServer(cs, o => o.EnableRetryOnFailure())` when `HostType == Azure`. Delete file. **Gotcha:** retrying strategy + user-initiated transactions needs `CreateExecutionStrategy().Execute(...)`.
- Many-to-many `Post.Tags` ↔ `Tag.Posts` (EF6 implicit join table `TagPosts`) → EF Core 5+ skip navigations create join table `PostTag` (`PostsPostId`,`TagsTagId`) by convention. Fresh database, so no rename needed; document in migration.
- `Post.Blogger` + `BlogId`: EF6 matched FK by convention name `BlogId`; EF Core convention would look for `BloggerBlogId`/`BloggerId` → configure `HasOne(p => p.Blogger).WithMany(b => b.Posts).HasForeignKey(p => p.BlogId)` explicitly.
- `TrackUpdate.LastUpdated { get; protected set; }` — EF Core maps protected setters fine.
- Lazy loading: EF6 lazy-loaded `virtual Blogger`; EF Core has no lazy loading by default → use `Include` / projections. `Tags` collections are null unless included → the `Post.Validate` "must have at least one Tag" check is skipped when `Tags == null` (same as legacy).
- Database initializers (`CreateDatabaseIfNotExists`, `NullDatabaseInitializer`, `Database.SetInitializer`) are gone → `db.Database.Migrate()` at startup when `canCreateDatabase` (HostType != WebWiz). Initial migration `InitialCreate` generated with `dotnet ef` (local tool in `.config/dotnet-tools.json`) and a design-time factory `IDesignTimeDbContextFactory<SampleWebAppDb>`.
- **Seeding gotcha:** legacy `CreateDatabaseIfNotExists` created an **empty** DB; the data was loaded by tests / the Posts→Reset action. On a fresh .NET 10 DB the site would be empty → add `SeedIfEmpty(Medium)` at startup. Do **not** use `HasData` (XML-sourced data, many-to-many with generated keys).
- `ResetBlogs`: `ToList().ForEach(Remove)` works in EF Core; deleting Blogs cascades to Posts (FK required) and join rows — fine. Prefer `ExecuteDelete()` for speed. Keep XML embedded resources (`DataLayer/Startup/Internal/*.xml`) as `<EmbeddedResource>`; manifest names stay `DataLayer.Startup.Internal.BlogsContextMedium.xml`.
- `Find` → `Find` OK. `DbSet.Local`, `Entry(...).Collection(...).Load()` semantics similar.
- `MultipleActiveResultSets=True` is kept (harmless); Microsoft.Data.SqlClient 5+ defaults `Encrypt=True` → local dev string needs `TrustServerCertificate=True`.
- Migrations model differs: EF6 `__MigrationHistory` + `Configuration : DbMigrationsConfiguration` (none existed here) vs EF Core `__EFMigrationsHistory` + `ModelSnapshot`. No EF6 migrations to port.

- **(A, verified)** Medium seed loads Blogs=4, Posts=17, Tags=8, PostTag rows=29; Small loads 2/3/3. The Medium XML lists 10 tags but `mystery`/`murder` are not referenced by any post and are only reachable through posts → 8 saved (same as legacy, `Test10SetupBlogs` expects 8).
- **(A)** `ResetBlogs` uses `ExecuteDelete()`, which bypasses the change tracker → it calls `ChangeTracker.Clear()` before re-adding. Callers holding tracked entities from before a reset must re-query.
- **(A, verified)** A failed `CreateAndSave`/`UpdateAndSave` (validation or `BeforeSaveChanges` error) leaves the invalid entity **tracked** in the scoped `SampleWebAppDb`; any later `*AndSave` / `SaveChanges` in the same scope fails again with the same error. One request = one write is fine for MVC; tests that do several writes in one scope must use a new scope (or `db.ChangeTracker.Clear()`) after an expected failure.
- **(A)** `Microsoft.EntityFrameworkCore.Design 10.0.0` brings `System.Security.Cryptography.Xml 9.0.0` transitively (via MSBuild/Roslyn workspaces) → NU1903 warnings on DataLayer builds. It is `PrivateAssets=all` (design-time only, does not flow to ServiceLayer/SampleWebApp/Tests runtime) — accepted.

## 3. GenericServices DTOs (`ServiceLayer`) — see §0 and the contract in §11
- `TagListDto`, `BlogListDto`: `PostsCount` relied on AutoMapper aggregate flattening (`Posts.Count`) → still works with `ProjectTo` in AutoMapper 14 (verify in tests).
- `SimplePostDto(Async)`: `BloggerName` flattening works; `TagNames` / `LastUpdatedUtc` are computed getters → AutoMapper must **ignore** them (no setter, fine) and `Tags` must be projected (`ICollection<Tag>` projected from entity – AutoMapper maps to new `Tag` objects; OK).
- `DetailPostDto(Async)`: `SetupSecondaryData`, `CreateDataFromDto`, `UpdateDataFromDto`, `SetupRestOfDto`, `ChangeTagsBasedOnMultiSelectList` → `DetailPostService` / `DetailPostServiceAsync` in ServiceLayer (§11).
- `[assembly: InternalsVisibleTo("Tests")]` repeated in DTO files → remove from code (duplicate attributes), put in csproj.
- `DelegateDecompiler(.EntityFramework)`, `Mono.Reflection`, `MarkdownSharp`, `GenericLibsBase`: no runtime usage besides logger/status → drop.
- **(C)** `GenericServicesSimpleSetup` only registers DTOs from the assemblies passed to it, and every `ILinkToEntity<T>` class found must be `public` (otherwise setup throws `SETUP FAILED ... must be public`). DTOs in another assembly (e.g. `Tests/Helpers/SimpleTagDto`) used with `ICrudServices` fail with `... is not registered as a valid CrudServices DTO/ViewModel`, so `AddServiceLayer` is not enough. The tests build their own setup that scans both assemblies and reuses the same `GenericServicesConfig`, including `BeforeSaveChanges = db.CheckTagSlugsUnique()`.

- **(A, verified)** `DetailPostDto(Async)` uses `PerDtoConfig` (`DetailPostDtoConfig`/`DetailPostDtoAsyncConfig`, public, discovered from the ServiceLayer assembly): read mapping ignores `Bloggers`/`UserChosenTags`; save mapping ignores `Tags`, `Blogger`, `LastUpdated`. The post services map scalar fields onto a loaded `Post` and set `BlogId`/`Tags` from the selections themselves.
- **(A, verified)** Status messages: post services return `"Successfully created Post."` / `"Successfully updated Post."`; GenericServices itself returns e.g. `"Successfully created a Tag"`, `"Successfully deleted a Post"`, and `"Failed with 1 error"` when invalid (assert on `IsValid`/`Errors`, not on exact failure text).

## 4. OWIN + ASP.NET Identity — referenced, **not wired up**
`packages.config` references `Microsoft.AspNet.Identity.*`, `Microsoft.Owin.*`, `Owin`, but: no `Startup`/`[assembly: OwinStartup]` class,
`owin:AutomaticAppStartup=false`, `<authentication mode="None" />`, no `[Authorize]`, no account controllers/views.
**Decision:** drop all of them; no ASP.NET Core Identity added (nothing to port). `app.UseAuthorization()` kept for the pipeline shape only.

## 5. SignalR 2.0.3 — referenced, **no hub, no live client**
No `Hub` subclass and no `MapSignalR()` anywhere. Only dead client JS: `Scripts/ActionRunnerComms.js`, `Scripts/ActionRunnerUi.js`,
`Scripts/jquery.signalR-2.0.3(.min).js`, and the `~/bundles/ActionRunner` bundle is commented out in `BundleConfig.cs`; no view references them.
**Decision:** drop `Microsoft.AspNet.SignalR*` and delete the dead JS (ActionRunner*.js, jquery.signalR*, jquery-notify*, jquery-ui*, notify.css, themes/). ASP.NET Core SignalR is in the shared framework (`builder.Services.AddSignalR()` + `app.MapHub<T>()`, JS client `@microsoft/signalr` with `HubConnectionBuilder` — incompatible with the 2.x `$.connection.hub` jQuery client) — nothing to register because no hub exists. No SignalR-driven UI behaviour to verify.

## 6. Bundling / static assets / views
- `System.Web.Optimization`, `WebGrease`, `Antlr`, `Modernizr 2.6.2`, `Respond 1.2.0` → drop. Modernizr/Respond are IE8/9 shims.
- Move `Content/` → `wwwroot/css` (+ `wwwroot/css/img`), `Scripts/` → `wwwroot/js`, `fonts/` → `wwwroot/fonts`, `favicon.ico` → `wwwroot/`. **Gotcha:** `bootstrap.css` references `../fonts/glyphicons-*` → fonts must stay a sibling of the css folder (`wwwroot/fonts`).
- `_Layout.cshtml`: `@Styles.Render("~/Content/css")` → `<link rel="stylesheet" href="~/css/bootstrap.css" asp-append-version="true" />` + `site.css` (note file is `Site.css` — **case-sensitive on Linux**, rename to `site.css` and update refs); `@Scripts.Render("~/bundles/javascript")` → `<script src="~/js/jquery-1.10.2.js">`, `bootstrap.js` (drop respond.js); `@RenderSection("scripts", required: false)` stays.
- `@Scripts.Render("~/bundles/jqueryval")` in `Views/{Posts,PostsAsync}/{Create,Edit}.cshtml` → `<partial name="_ValidationScriptsPartial" />` containing `jquery.validate.js` + `jquery.validate.unobtrusive.js`.
- Add `Views/_ViewImports.cshtml` (`@using`, `@addTagHelper`). `_ViewStart.cshtml` unchanged.
- `@Html.Partial(...)` → `<partial name=...>` / `await Html.PartialAsync` (sync `Html.Partial` gives MVC1000 warning and can deadlock).
- `@Html.ActionLink(..., new { id = 0 }, null)` → OK in Core; `new { area = "" }` harmless.
- Editor templates `Views/Shared/EditorTemplates/{DropDownListType,MultiSelectListType}.cshtml`: `SelectList`/`MultiSelectList` now in `Microsoft.AspNetCore.Mvc.Rendering`.
- `Views/Shared/{PostValidation,TagValidation}.cshtml` partials (client-side JSON validation helpers) — keep, check they still load jQuery.
- `ClientValidationEnabled`/`UnobtrusiveJavaScriptEnabled` appSettings → on by default in Core (`HtmlHelperOptions.ClientValidationEnabled`).
- **Gotcha:** `ViewBag.Title` collides with model property `Title` (DetailPostDto) in `EditorFor(m => m.Title)` lookups → use `TextBoxFor` / `asp-for` in Post Create/Edit views if validation attributes disappear.
- `[UIHint("HiddenInput")]` still honoured by `EditorFor`; `[ScaffoldColumn(false)]` still honoured by `DisplayForModel`/`EditorForModel`.
- Model binding of `DropDownListType`/`MultiSelectListType` (nested complex types with `private set` lists) — Core binds `SelectedValue` / `FinalSelection` only; lists must be re-populated (`ResetDto`) on postback (same as legacy).
- `[ValidateAntiForgeryToken]` → same attribute name in `Microsoft.AspNetCore.Mvc`; `@Html.AntiForgeryToken()` still works (forms with tag helpers add it automatically).
- **Gotcha (found in B):** `@Html.Label("Tags", htmlAttributes: new {...})` does not compile in Core — `IHtmlHelper.Label(expression, labelText, htmlAttributes)` has no optional `labelText`; pass it explicitly (`@Html.Label("Tags", "Tags", new {...})`). Razor views are compiled at build time in Core, so view errors surface in `dotnet build`.
- **Gotcha (confirmed in B):** the `ViewBag.Title` collision is real: `@Html.EditorFor(m => m.Title)` on `DetailPostDto(Async)` emitted no `data-val-*` attributes. Post Create/Edit views now use `<input asp-for="Title" class="form-control" />`, which emits `data-val-minlength`/`data-val-maxlength`.
- **Gotcha (found in B, fix belongs in ServiceLayer):** Core's model validation visitor reads *every* property of a bound DTO, including computed getters. `DetailPostDto(Async).TagNames => string.Join(", ", Tags.Select(...))` throws `ArgumentNullException` on POST (`Tags` is null after binding) → 500 on Post Create/Edit. Make the getter null-safe (`(Tags ?? []).Select(...)`) or mark it `[ValidateNever]`.

## 7. Dependency injection: Autofac 3.5 + Autofac.Mvc5 → built-in container
- Modules `DataLayerModule`, `BizLayerModule`, `ServiceLayerModule` (`RegisterAssemblyTypes(...).AsImplementedInterfaces()` + GenericServices assembly) and `AutofacDi` → `IServiceCollection` extension methods `AddDataLayer`, `AddBizLayer`, `AddServiceLayer` (§11). `BizLayerInitialise`/`ServiceLayerInitialise` remain static startup hooks (migrate + seed).
- `DiModelBinder` (any interface-typed action parameter, plus `SampleWebAppDb`, resolved from the container) → explicit `[FromServices]` on each such parameter. Without it Core tries to model-bind the interface and throws `InvalidOperationException: Could not create an instance of type 'ICrudServices'`. (Core only infers `[FromServices]` on `[ApiController]`s.)
- Autofac-specific tests `Tests/UnitTests/Group03ServiceLayer/Test10DiSimple.cs` (tests Autofac itself with `DependencyItems/*`) and `Test11AutoFacModules.cs` → rewrite against `ServiceCollection` / `BuildServiceProvider(validateScopes: true)`; the private-ctor / generic-interface scenarios map to built-in DI equivalents (open generics supported; private ctors not → assert failure).
- Option kept open: `Autofac.Extensions.DependencyInjection` 10.x if module-based assembly scanning is preferred — rejected: three tiny modules, no Autofac-only features used.

## 8. AutoMapper 3.2.1 / 4.2.1 → 14.0.0
- No direct `Mapper.CreateMap`/`Mapper.Map` calls in this solution (GenericServices 1.x hid them). Static `Mapper` API removed in AutoMapper 9; EfCore.GenericServices owns its `MapperConfiguration`. Per-DTO tweaks via `PerDtoConfig<TDto,TEntity>`.
- Two AutoMapper versions (3.2.1 in ServiceLayer/Tests, 4.2.1 in SampleWebApp) collapse to one pinned 14.0.0 (§0.1).
- `ProjectTo` must be EF-translatable: computed C# getters (`TagNames`) must not be mapped from the DB side.

## 9. Project-file conversion
| Legacy | SDK-style |
|---|---|
| `packages.config` + `<Reference HintPath="..\packages\...">`; committed `packages/` folder | `PackageReference`; delete `packages.config` files and the root `packages/` folder |
| `Properties/AssemblyInfo.cs` | delete (SDK generates; duplicates → CS0579). `InternalsVisibleTo` → csproj item |
| explicit `<Compile Include>` | implicit globbing: dead files (`App_Start/*`, `DiModelBinder`, loggers, `JsonNetResult`) must be **deleted**, not left |
| `Web.config` + `Web.{Debug,Release,AzureRelease,WebWizRelease}.config` transforms, `Properties/Settings.settings` (`HostTypeString`) | `appsettings.json` (`ConnectionStrings:SampleWebAppDb` empty, `HostType`) + `appsettings.Development.json` (local SQL Server); `ASPNETCORE_ENVIRONMENT` replaces transforms. Azure/WebWiz → `appsettings.Production.json` / env vars |
| `App.config` binding redirects, `Microsoft.Web.Infrastructure` | not needed |
| `.sln` web project type GUID `{349c5851-65df-11da-9384-00065b846f21}` | plain C# project; keep all 5 projects in `SampleWebApp.sln`; drop `.nuget` solution folder if any |
| NUnit 2.6.3 (`[TestFixtureSetUp]`, `Assert.AreEqual`, `ExpectedException`), Moq 4.2 | NUnit 4.x (`[OneTimeSetUp]`, `Assert.That` or `NUnit.Framework.Legacy.ClassicAssert`, `Assert.Throws`), `NUnit3TestAdapter`, `Microsoft.NET.Test.Sdk`, Moq 4.20.x |
| `Tests/Properties/Settings.settings` | `Tests/appsettings.json` or env var `ConnectionStrings__SampleWebAppDb` |

**(C)** `WebApplicationFactory<Program>` runs `Program.cs` in full, including `WebUiInitialise.InitialiseThis` (migrate + seed), against the `Development` connection string (`SampleWebAppDb`). Tests override it with `WithWebHostBuilder(b => b.UseSetting("ConnectionStrings:SampleWebAppDb", <test cs>))` so they only touch `SampleWebAppDb-Test`.

**(A)** `dotnet new tool-manifest` on SDK 10.0.301 writes `dotnet-tools.json` in the **current directory**, not `.config/`; it was moved to `.config/dotnet-tools.json` (`dotnet-ef 10.0.0`). Use `dotnet tool restore` then `dotnet ef ... --project DataLayer` (design-time factory reads `ConnectionStrings__SampleWebAppDb`).

## 10. Replacement summary
| Legacy package | Replacement |
|---|---|
| EntityFramework 6.1.x | Microsoft.EntityFrameworkCore(.SqlServer/.Design) 10.0.x |
| GenericServices 1.0.9 / GenericLibsBase | EfCore.GenericServices 10.0.0 (+ GenericServices.StatusGeneric) |
| AutoMapper 3.2.1/4.2.1 | AutoMapper **14.0.0** (pinned) |
| Autofac 3.5 + Autofac.Mvc5 | Microsoft.Extensions.DependencyInjection (built-in) |
| Microsoft.AspNet.Mvc 5.2.3 / Razor / WebPages | ASP.NET Core MVC (Microsoft.NET.Sdk.Web, net10.0) |
| Microsoft.AspNet.Web.Optimization, WebGrease, Antlr, Modernizr, Respond | wwwroot + static files + `asp-append-version` |
| Microsoft.AspNet.SignalR 2.0.3 | removed (no hub); ASP.NET Core SignalR available in shared framework if needed |
| Microsoft.Owin.*, Owin, Microsoft.AspNet.Identity.* | removed (not wired up) |
| log4net, GenericLibsBase loggers | Microsoft.Extensions.Logging |
| Newtonsoft.Json 6 (JsonNetResult) | System.Text.Json (`JsonResult`) |
| DelegateDecompiler, Mono.Reflection, MarkdownSharp | removed (unused) |
| NUnit 2.6.3, Moq 4.2 | NUnit 4.x + NUnit3TestAdapter + Microsoft.NET.Test.Sdk, Moq 4.20.x |

## 11. Cross-subsession API contract (binding — A implements, B and C consume)
Names not listed keep their legacy names/namespaces. Entities (`Blog`, `Post`, `Tag`, `TrackUpdate`) keep their shape.

```csharp
// ---------------- DataLayer ----------------
namespace DataLayer.DataClasses {
  public class SampleWebAppDb : DbContext {
    public const string NameOfConnectionString = "SampleWebAppDb";
    public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options);
    public DbSet<Blog> Blogs { get; set; } public DbSet<Post> Posts { get; set; } public DbSet<Tag> Tags { get; set; }
    // overrides SaveChanges(bool)/SaveChangesAsync(bool,CancellationToken) → TrackUpdate.UpdateTrackingInfo()
    public IStatusGeneric SaveChangesWithChecking();               // DataAnnotations + IValidatableObject + Tag Slug uniqueness; never throws for validation
    public Task<IStatusGeneric> SaveChangesWithCheckingAsync();
  }
  public class SampleWebAppDbDesignTimeFactory : IDesignTimeDbContextFactory<SampleWebAppDb> { } // env ConnectionStrings__SampleWebAppDb else local docker default
}
namespace DataLayer.Startup {
  public enum TestDataSelection { Small = 0, Medium = 1 }
  public static class DataLayerInitialise {
    public static void InitialiseThis(SampleWebAppDb db, bool canCreateDatabase);  // Database.Migrate() if canCreateDatabase
    public static void ResetBlogs(SampleWebAppDb db, TestDataSelection selection);
    public static void SeedIfEmpty(SampleWebAppDb db, TestDataSelection selection);
  }
  public static class DataLayerServiceCollectionExtensions {
    public static IServiceCollection AddDataLayer(this IServiceCollection services, string connectionString, bool isAzure = false); // AddDbContext (scoped)
  }
}
// ---------------- BizLayer ----------------
namespace BizLayer.Startup { public static class BizLayerServiceCollectionExtensions { public static IServiceCollection AddBizLayer(this IServiceCollection services); } }
// ---------------- ServiceLayer ----------------
namespace ServiceLayer.Startup {
  public static class ServiceLayerServiceCollectionExtensions {
    // AddDataLayer + AddBizLayer + GenericServicesSimpleSetup<SampleWebAppDb>(validate-on-save, BeforeSaveChanges = slug check) + IDetailPostService(+Async) scoped
    public static IServiceCollection AddServiceLayer(this IServiceCollection services, string connectionString, bool isAzure = false);
  }
  public static class ServiceLayerInitialise {
    public static void InitialiseThis(IServiceProvider rootProvider, bool canCreateDatabase); // creates scope → DataLayerInitialise.InitialiseThis → SeedIfEmpty(Medium)
  }
}
namespace ServiceLayer.TagServices  { public class TagListDto  : ILinkToEntity<Tag>  { int TagId; string Name; string Slug; int PostsCount; } }
namespace ServiceLayer.BlogServices { public class BlogListDto : ILinkToEntity<Blog> { int BlogId; string Name; string EmailAddress; int PostsCount; } }
namespace ServiceLayer.PostServices {
  public class SimplePostDto      : ILinkToEntity<Post> { int PostId; int BlogId; string BloggerName; string Title; ICollection<Tag> Tags; DateTime LastUpdated; DateTime LastUpdatedUtc {get;} string TagNames {get;} }
  public class SimplePostDtoAsync : ILinkToEntity<Post> { /* same */ }
  public static class SimplePostQueryExtensions {   // id null or 0 => no filter
    public static IQueryable<SimplePostDto>      FilterByBlogId(this IQueryable<SimplePostDto> q, int? blogId);
    public static IQueryable<SimplePostDtoAsync> FilterByBlogId(this IQueryable<SimplePostDtoAsync> q, int? blogId);
  }
  public class DetailPostDto : ILinkToEntity<Post> {
    int PostId; string Title; string Content; string BloggerName; DateTime LastUpdated; int BlogId; ICollection<Tag> Tags;
    DropDownListType Bloggers; MultiSelectListType UserChosenTags;   // non-null after ctor
    DateTime LastUpdatedUtc {get;} string TagNames {get;}
  }   // same validation attributes as legacy
  public class DetailPostDtoAsync : ILinkToEntity<Post> { /* same shape */ }
  public interface IDetailPostService {
    IStatusGeneric Status { get; }                 // status of last Get* call
    DetailPostDto GetDetail(int postId);           // null + invalid Status if not found
    DetailPostDto GetNew();                        // Bloggers + UserChosenTags populated (replaces ICreateSetupService.GetDto)
    DetailPostDto GetForEdit(int postId);          // populated + current selections (replaces IUpdateSetupService.GetOriginal)
    DetailPostDto ResetDto(DetailPostDto dto);     // repopulate lists keeping user's selections
    IStatusGeneric Create(DetailPostDto dto);      // Message on success e.g. "Successfully created Post."
    IStatusGeneric Update(DetailPostDto dto);      // replaces post's tags with selection
  }
  public interface IDetailPostServiceAsync {
    IStatusGeneric Status { get; }
    Task<DetailPostDtoAsync> GetDetailAsync(int postId); Task<DetailPostDtoAsync> GetNewAsync();
    Task<DetailPostDtoAsync> GetForEditAsync(int postId); Task<DetailPostDtoAsync> ResetDtoAsync(DetailPostDtoAsync dto);
    Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto); Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto);
  }
}
namespace ServiceLayer.UiClasses { /* DropDownListType, MultiSelectListType unchanged */ }
```
Error member names returned by the post services (B copies to ModelState): `Bloggers`
("The blogger was not selected. You must do that before the post can be saved." / "Could not find the blogger you selected. Did another user delete it?"),
`UserChosenTags` (legacy tag messages), `Title` / `""` from `Post.Validate`. Tag Slug duplicate: member `Slug`.

**SampleWebApp (B owns):**
```csharp
namespace SampleWebApp.Infrastructure {
  public static class ValidationHelper {
    void CopyErrorsToModelState<T>(this IStatusGeneric status, ModelStateDictionary modelState, T displayDto);
    void CopyErrorsToModelState(this IStatusGeneric status, ModelStateDictionary modelState);
    string ErrorsAsHtml(this IStatusGeneric status);
    JsonResult ReturnModelErrorsAsJson(this ModelStateDictionary modelState);   // Value = new { errorsDict = dict }
    JsonResult ReturnErrorsAsJson<T>(this IStatusGeneric status, T displayDto);
  }
}
public partial class Program { }   // exposed for WebApplicationFactory<Program> in Tests
```
Controller pattern: `Index([FromServices] ICrudServices service)` → `service.ReadManyNoTracked<TagListDto>().ToList()`;
Tags/Blogs details/edit/create/delete use entity classes directly (`ReadSingle<Tag>(id)`, `UpdateAndSave(tag)`, `CreateAndSave(tag)`, `DeleteAndSave<Tag>(id)`);
Posts use `[FromServices] IDetailPostService` (+ `DeleteAndSave<Post>`); `*Async` controllers use the async variants;
`NumPosts`/`Reset` take `[FromServices] SampleWebAppDb db`. Success text goes to `TempData["message"] = service.Message`.

## 12. Runtime environment (Linux VM)
- .NET SDK 10.0.301. LocalDB is Windows-only → SQL Server 2022 Developer in Docker:
  `docker start mssql || docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=Str0ng!Passw0rd' -e MSSQL_PID=Developer -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest`
- Dev connection string (`appsettings.Development.json`; throw-away local container password):
  `Server=localhost,1433;Database=SampleWebAppDb;User Id=sa;Password=Str0ng!Passw0rd;TrustServerCertificate=True;MultipleActiveResultSets=True`
- Run: `ASPNETCORE_ENVIRONMENT=Development dotnet run --project SampleWebApp --urls http://localhost:5000`
- Tests: `dotnet test` against database `SampleWebAppDb-Test` on the same server (override with `ConnectionStrings__SampleWebAppDb`).

## 13. Baseline & verification artifacts
- **Subsession 0 — legacy .NET Framework 4.5.1 baseline on Windows** ([session](https://app.devin.ai/sessions/0a46be762464420a95068bffc57afb4c), `master` unmodified, IIS Express + LocalDB, Chrome):
  - Video: [legacy-baseline-edited.mp4](https://app.devin.ai/attachments/2fce1050-b7a0-4aea-91cc-2471316fd3ad/legacy-baseline-edited.mp4); screenshots: [posts-index](https://app.devin.ai/attachments/03932834-b8e9-4ef9-afe4-0edf6ff0cafc/posts-index.png), [post-create](https://app.devin.ai/attachments/232fed79-2096-4097-810b-39e9ec9ba891/post-create.png), [tags-index](https://app.devin.ai/attachments/3fa0a284-7e1b-4361-ae4c-e3aeab6f06ec/tags-index.png), [tag-duplicate-slug](https://app.devin.ai/attachments/a5b1ab37-b56b-427b-8859-21bce67f219d/tag-duplicate-slug.png).
  - Build needed the `Microsoft.NETFramework.ReferenceAssemblies.net451` package (the VM's 4.5.1 targeting pack had only XML docs → MSB3644). MSBuild: 0 errors, 5 warnings. NUnit 2.6.3: 54/54 passed. `BizLayer` was **not in the legacy `.sln`** (fixed in the .NET 10 `.sln`).
  - Legacy DB is **empty on first load**; data appears only after Posts › Reset → 4 blogs, 17 posts, 8 tags (2 of the 10 XML tags are unused, so never saved).
  - Messages: `Successfully reset the blogs data`; `The total number of Posts is 17`; `Successfully created/updated/deleted Post.` / `... Tag.`; `The Title field is required.`; `The Slug on tag '<name>' must be unique and is already being used.`
- **Intentional behaviour differences in the .NET 10 port vs. the baseline**
  - The database is migrated **and seeded on startup when empty** (`ServiceLayerInitialise` → `SeedIfEmpty`), so the first page load already shows 4 / 17 / 8. Reset still works as before.
  - Create/update Post keep the legacy wording (`DetailPostService`). Tag/Blog create/update/delete and Post delete use EfCore.GenericServices' default success text (e.g. `Successfully deleted a Post`) instead of GenericServices 1.0.9's `Successfully deleted Post.`.
  - Post title validation and duplicate tag slug messages are identical.
- **Phase 2: .NET 10 run in the Linux VM** (commit `3734475`, .NET SDK 10.0.301, SQL Server 2022 in Docker, Chrome; the DB was dropped first so startup ran the EF Core migration and seed):
  - Video: [phase2-net10-crud-share.mp4](https://app.devin.ai/attachments/c82df3ee-5fba-4b6f-b81c-26716fb2af40/phase2-net10-crud-share.mp4) (3 min).
  - Passed: fresh migrate + seed gives 4 blogs / 17 posts / 8 tags.
  - Passed: Blogs create/edit/delete and filtering posts by blog. The legacy app also has no Blog Details page.
  - Passed: Posts list/details/create/edit/delete, including changing the blogger and tags. Empty-title and `!`-title validation work.
  - Passed: Tags list/details/create/edit/delete. A duplicate slug shows the validation error, not a 500.
  - Passed: PostsAsync/TagsAsync CRUD.
  - Passed: data survives a page reload and a real restart of the `dotnet run` process.
  - Passed: Reset and NumPosts give 17 posts.
  - Passed: no JavaScript errors, no static-asset 404s, no SignalR traffic.
  - Minor, pre-existing (same markup in legacy `master`): on Post Create/Edit the `Bloggers`/`Tags` labels point at ids that don't exist (Chrome Issues warning only).
