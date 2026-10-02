# MIGRATION_NOTES — ASP.NET MVC 5 / .NET Framework 4.5.1 → ASP.NET Core MVC / .NET 10

Phase 0 output. Every later phase (and every subsession) must read this file first.
It lists the concrete gotchas found in **this** solution, the affected files, and the
replacement chosen for every legacy dependency. Section 10 is the binding
cross-subsession API contract.

Target: `net10.0` everywhere, SDK-style projects, `PackageReference`, nullable **disabled**
(the legacy code is not null-annotated), `ImplicitUsings` enabled.

---

## 0. Highest-risk item (read first)

**GenericServices 1.0.9 → EfCore.GenericServices 10.0.0 is a substantial API-shape change, not a rename.**

| Legacy (GenericServices 1.0.9, EF6) | EfCore.GenericServices 10.0.0 |
|---|---|
| 7+ separate interfaces injected per action: `IListService`, `IDetailService`, `IUpdateSetupService`, `IUpdateService`, `ICreateSetupService`, `ICreateService`, `IDeleteService` (+ `*Async` variants) | **One** facade: `ICrudServices` (sync) / `ICrudServicesAsync` (async). Both implement `IStatusGeneric` (`IsValid`, `Errors`, `Message`). |
| `service.GetAll<TDto>()` | `crud.ReadManyNoTracked<TDto>()` (returns `IQueryable`) |
| `service.GetDetail<TDto>(id)` → `ISuccessOrErrors<TDto>` | `crud.ReadSingle<TDto>(id)` / `await crudAsync.ReadSingleAsync<TDto>(id)` → returns the DTO, status on `crud` |
| `IUpdateSetupService.GetOriginal<TDto>(id)` / `ICreateSetupService.GetDto<TDto>()` (call `SetupSecondaryData`) | **No equivalent.** Read via `ReadSingle` and populate secondary data (dropdowns) yourself. |
| `service.Update(dto)` / `service.Create(dto)` → `ISuccessOrErrors` | `crud.UpdateAndSave(dto)` / `crud.CreateAndSave(dto)`; status on `crud` |
| `service.ResetDto(dto)` (repopulate dropdowns after validation failure) | **No equivalent.** Must be provided by ServiceLayer. |
| `service.Delete<TEntity>(id)` | `crud.DeleteAndSave<TEntity>(id)` |
| DTOs inherit `EfGenericDto<TEntity,TDto>` / `EfGenericDtoAsync<,>` | DTOs implement marker `ILinkToEntity<TEntity>`; optional `PerDtoConfig<TDto,TEntity>` class for AutoMapper tweaks. **No base class, no virtual hooks.** |
| Virtual hooks `SupportedFunctions`, `SetupSecondaryData`, `CreateDataFromDto`, `UpdateDataFromDto` | **Gone.** Custom create/update logic (e.g. Post ↔ Tags many-to-many, Blogger lookup) must live in a ServiceLayer service. |
| `IGenericServicesDbContext` / `IDbContextWithValidation` on the DbContext | Plain `DbContext`; registered via `services.GenericServicesSimpleSetup<TContext>(config, assemblies)` |
| `ISuccessOrErrors` / `SuccessOrErrors` (from `GenericLibsBase`) | `IStatusGeneric` / `StatusGenericHandler` (namespace `StatusGeneric`, package `GenericServices.StatusGeneric`) |
| `response.ErrorsAsHtml()`, `response.CopyErrorsToModelState(ModelState, dto)` (MVC5 helpers in GenericServices) | Not in EfCore.GenericServices. `EfCore.GenericServices.AspNetCore` only targets ASP.NET Core 2.1 → **write our own** `CopyErrorsToModelState` / `ErrorsAsHtml` extensions in `SampleWebApp/Infrastructure`. |
| `SaveChangesWithChecking()` (EF6 `ValidateEntity`) | `GenericServicesConfig { DirectAccessValidateOnSave = true, DtoAccessValidateOnSave = true }` makes `*AndSave` run DataAnnotations + `IValidatableObject` validation and return errors in status instead of throwing. |

Verified by a throw-away probe (outside the repo) on .NET 10: `EfCore.GenericServices 10.0.0` + SQLite in-memory,
`ReadManyNoTracked<TagListDto>()` with `PostsCount` aggregate and `CreateAndSave(new Tag{...})` both work.

### 0.1 AutoMapper version pin — **must be 14.0.0**
- `EfCore.GenericServices 10.0.0` declares `AutoMapper >= 13.0.1`, but calls the public
  `MapperConfiguration(Action<IMapperConfigurationExpression>)` ctor. AutoMapper **15.x / 16.x** made it
  inaccessible → at startup: `System.MethodAccessException ... SetupDtosAndMappings.CreateConfigAndMapper`.
  Reproduced with 15.1.3 and 16.2.0. **14.0.0 works.**
- Pin `<PackageReference Include="AutoMapper" Version="14.0.0" />` explicitly in ServiceLayer (and any project that
  floats it) so NuGet does not resolve a newer version.
- `dotnet list package --vulnerable` flags AutoMapper 14.0.0 with **GHSA-rvv3-g6hj-g44x** (High, uncontrolled recursion
  when mapping deeply nested/cyclic object graphs). Accepted risk: mappings are only over our own DTO/entity types,
  never over user-supplied object graphs. Remove the pin once EfCore.GenericServices ships an AutoMapper 15+ build.
  Suppress the NU1903 warning only on the ServiceLayer reference if it fails the build (`NoWarn` scoped, documented).

---

## 1. `System.Web` / `System.Web.Mvc` — no equivalent in ASP.NET Core

Every usage (from `rg 'System\.Web|HttpApplication|Global\.asax|AreaRegistration|RouteTable|BundleTable|ModelBinders|MvcHtmlString'`):

| File | Legacy usage | Replacement |
|---|---|---|
| `SampleWebApp/Global.asax`, `Global.asax.cs` | `MvcApplication : HttpApplication`; `AreaRegistration.RegisterAllAreas()`, `FilterConfig.RegisterGlobalFilters(GlobalFilters.Filters)`, `RouteConfig.RegisterRoutes(RouteTable.Routes)`, `BundleConfig.RegisterBundles(BundleTable.Bundles)`, `ModelBinders.Binders.DefaultBinder = new DiModelBinder()` | Delete. Single `Program.cs` (minimal hosting). No areas exist. |
| `SampleWebApp/App_Start/RouteConfig.cs` | `RouteCollection.MapRoute("Default", "{controller}/{action}/{id}")` | `app.MapControllerRoute("default", "{controller=Home}/{action=Index}/{id?}")`. Delete file. |
| `SampleWebApp/App_Start/FilterConfig.cs` | `HandleErrorAttribute` global filter | `app.UseExceptionHandler("/Home/Error")` (non-Development) + `UseDeveloperExceptionPage` (Development, default). Delete file. |
| `SampleWebApp/App_Start/BundleConfig.cs` | `System.Web.Optimization` `ScriptBundle`/`StyleBundle` | Static files in `wwwroot` + `<script>/<link>` with `asp-append-version`. Delete file. |
| `SampleWebApp/Controllers/{Home,Blogs,Posts,PostsAsync,Tags,TagsAsync}Controller.cs` | `System.Web.Mvc.Controller`, `ActionResult`, `[HttpPost]`, `[ValidateAntiForgeryToken]`, `HttpNotFound()` | `Microsoft.AspNetCore.Mvc.Controller`, `IActionResult`, `NotFound()`. `[Bind]`, `[ValidateAntiForgeryToken]` exist in Core. |
| `Posts*/Tags*Controller.cs` | `TempData["errorMessage"] = new MvcHtmlString(response.ErrorsAsHtml())` | **TempData in Core is serialized (cookie TempData provider, JSON) — it cannot hold `MvcHtmlString`/`HtmlString` objects.** Store the HTML `string`, render with `@Html.Raw(TempData["errorMessage"])` in the 4 `Index.cshtml` views. |
| `SampleWebApp/Infrastructure/DiModelBinder.cs` | `DefaultModelBinder` resolving interfaces via `DependencyResolver.Current` | Delete. Use `[FromServices]` on action parameters (see §7). |
| `SampleWebApp/Infrastructure/WebUiInitialise.cs` | `HttpApplication`, `Server.MapPath("~/Log4Net.xml")`, `DependencyResolver.SetResolver(new AutofacDependencyResolver(...))`, `Settings.Default.HostTypeString` | Delete. Host type → `appsettings.json` `"HostType"`; paths → `IWebHostEnvironment.ContentRootPath`; DI → `builder.Services`. |
| `SampleWebApp/Infrastructure/JsonNetResult.cs` | `ActionResult.ExecuteResult(ControllerContext)`, `HttpResponseBase` | Delete; use built-in `Json(...)` / `JsonResult` (System.Text.Json). |
| `SampleWebApp/Infrastructure/ValidationHelper.cs` | `System.Web.Mvc.ModelStateDictionary`, `JsonNetResult` | Port to `Microsoft.AspNetCore.Mvc.ModelBinding.ModelStateDictionary`, return `JsonResult`. Keep the same JSON shape as the MVC5 code (`{ "errorsDict": { "<key>": { "errors": [msgs] } } }`, top-level errors under key `""`) because `Tests/Group06Mvc/Test02Validation.cs` asserts on it. *(Implemented in Subsession B; `ReturnErrorsAsJson`/`CopyErrorsToModelState`/`ErrorsAsHtml` now extend `StatusGeneric.IStatusGeneric` and take the dto as `object`.)* |
| `SampleWebApp/Views/Shared/Error.cshtml` | `@model System.Web.Mvc.HandleErrorInfo` | `@model SampleWebApp.Models.ErrorViewModel` (RequestId) or no model. |
| `SampleWebApp/Views/Web.config` | Razor host factory, `System.Web.Mvc.*` namespaces | Delete; namespaces → `Views/_ViewImports.cshtml` (+ `@addTagHelper *, Microsoft.AspNetCore.Mvc.TagHelpers`). |
| `SampleWebApp/Web.config`, `Web.Debug.config`, `Web.Release.config` | connection string, appSettings, `system.web`, OWIN, binding redirects | Delete; → `appsettings.json` / `appsettings.Development.json` (§9). |
| `SampleWebApp/Models/InternalsInfo.cs` | `System.Diagnostics.PerformanceCounter("Memory","Available MBytes")` — **Windows-only, throws PlatformNotSupportedException on Linux** | `GC.GetGCMemoryInfo().TotalAvailableMemoryBytes / (1024*1024)`. |
| `SampleWebApp/Properties/Settings.*` | `Settings.Default.HostTypeString` | `IConfiguration["HostType"]`. |
| `Tests/Helpers/JsonHelper.cs` | `System.Web.Helpers.Json.Decode` | `System.Text.Json` (`JsonDocument`/`JsonSerializer`). |
| `Tests/Helpers/ModelStateTester.cs` | `System.Web.Mvc.ModelStateDictionary` | `Microsoft.AspNetCore.Mvc.ModelBinding.ModelStateDictionary` (`FrameworkReference Microsoft.AspNetCore.App` in Tests). |
| `Tests/App.config` | binding redirects, EF6 config, connection string | Delete; test connection string from env var / `appsettings.json` copied to output. |

Other Razor gotchas checked: no `@helper`/`@functions`, no `Html.Action`/`RenderAction` (child actions don't exist in Core — none used).
`@Html.Partial(...)` still works but should become `<partial name="..."/>` (sync `Html.Partial` triggers analyzer warning MVC1000).
`[UIHint("HiddenInput")]` still maps to Core's built-in `HiddenInput` template. Custom `EditorTemplates/DropDownListType.cshtml` and `MultiSelectListType.cshtml` keep working (`SelectList`/`MultiSelectList` live in `Microsoft.AspNetCore.Mvc.Rendering`).

---

## 2. Entity Framework 6 → EF Core 10

Packages: `Microsoft.EntityFrameworkCore` / `.SqlServer` / `.Design` **10.0.x** (+ `dotnet-ef` 10.0.12 local tool, `dotnet-tools.json` at repo root — `dotnet tool restore`).

| Gotcha | Affected file(s) | Replacement |
|---|---|---|
| `ValidateEntity(DbEntityEntry, IDictionary)` / `DbEntityValidationResult` / `DbValidationError` / `DbEntityEntry` **do not exist** | `DataLayer/DataClasses/SampleWebAppDb.cs` | Tag slug uniqueness: (a) **unique index** `modelBuilder.Entity<Tag>().HasIndex(t => t.Slug).IsUnique()` as the DB backstop, and (b) a pre-save validation that returns the **verbatim legacy message** `The Slug on tag '{Name}' must be unique and is already being used.` with member `Slug` as a `ValidationResult` so GenericServices surfaces it in status instead of an `DbUpdateException`. Recommended: `Tag : IValidatableObject` that resolves the `DbContext` from `ValidationContext` (EfCore.GenericServices passes the DbContext via `ValidationContext.Items`/service provider — verify) **or** a `SaveChangesWithChecking()` on the context that validates all Added/Modified entries. |
| EF Core does **not** validate DataAnnotations / `IValidatableObject` on `SaveChanges` (EF6 did) | `Post.cs` (`IValidatableObject`: title punctuation, banned words), `Tag.cs` (`[RegularExpression(@"\w*")]`, `[Required]`), `Blog.cs` | Enable `DirectAccessValidateOnSave`/`DtoAccessValidateOnSave` in GenericServices config; port `SaveChangesWithChecking()` onto `SampleWebAppDb` returning `IStatusGeneric` for non-GenericServices callers (`DataLayerInitialise.ResetBlogs`, tests). |
| Connection-string-by-name ctor `base("name=SampleWebAppDb")` is gone | `SampleWebAppDb.cs` | `public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options)`. Connection string from `ConnectionStrings:SampleWebAppDb` via `AddDbContext`. Add `IDesignTimeDbContextFactory<SampleWebAppDb>` in DataLayer (reads `ConnectionStrings__SampleWebAppDb` env var, falls back to the local docker SQL Server string) so `dotnet ef` works without the web host. |
| `SaveChangesAsync()` override signature differs | `SampleWebAppDb.cs` | Override `SaveChanges(bool acceptAllChangesOnSuccess)` and `SaveChangesAsync(bool, CancellationToken)` (the parameterless overloads funnel into these). |
| `HandleChangeTracking` latent bug: `if (trackUpdateClass == null) return;` aborts on the first non-`TrackUpdate` entity, so later `TrackUpdate` entities aren't stamped | `SampleWebAppDb.cs` | Iterate `ChangeTracker.Entries<TrackUpdate>().Where(e => e.State is Added or Modified)` and call `UpdateTrackingInfo()` on each. |
| `TrackUpdate.LastUpdated` has a protected setter; `UpdateTrackingInfo()` is `internal` | `Concrete/Helpers/TrackUpdate.cs` | EF Core maps non-public setters fine. Keep `internal` + `InternalsVisibleTo`. |
| Database initializers (`CreateDatabaseIfNotExists`, `NullDatabaseInitializer`, `Database.SetInitializer`) gone | `DataLayer/Startup/DataLayerInitialise.cs` | Migrations + `db.Database.Migrate()` at startup when `canCreateDatabase`; nothing otherwise. |
| `ResetBlogs` used `SaveChangesWithChecking()` + `GenericLibsBase` logger | `DataLayerInitialise.cs` | Keep signature `ResetBlogs(SampleWebAppDb, TestDataSelection)`; use ported `SaveChangesWithChecking()`; logging via `ILogger`. Delete order (posts→tags→blogs) still works; many-to-many join rows are removed by EF Core cascade. Add `SeedIfEmpty(db, selection)` for first-run seeding. |
| `EfConfiguration : DbConfiguration` + `SqlAzureExecutionStrategy` | `DataLayer/DataClasses/EfConfiguration.cs` | Delete; `UseSqlServer(cs, o => o.EnableRetryOnFailure())` when `HostType` is Azure. **Gotcha:** a retrying strategy forbids user-initiated transactions without `CreateExecutionStrategy().Execute(...)`; only enable for Azure. |
| Many-to-many `Post.Tags` ↔ `Tag.Posts`: EF6 created join table `TagPosts(Tag_TagId, Post_PostId)`; EF Core default is `PostTag(PostsPostId, TagsTagId)` | model config, `Tests/Helpers/DbSnapShot.cs` (raw SQL counts on join table) | Configure explicitly with `UsingEntity(j => j.ToTable("TagPosts"))` + FK column names `Tag_TagId`/`Post_PostId` to keep the legacy schema name; tests must use the same name. |
| EF6 FK cascade defaults vs EF Core | `Blog.Posts` (required FK `BlogId`) | EF Core cascades required relationships by default — same as EF6 for `Blog→Post`. Keep. |
| EF6 `__MigrationHistory` vs EF Core `__EFMigrationsHistory`; EF6 migrations not portable | — | Fresh **initial EF Core migration** generated with `dotnet tool run dotnet-ef migrations add InitialCreate --project DataLayer --startup-project DataLayer` (design-time factory) → `DataLayer/Migrations/*_InitialCreate.cs`. Existing EF6 databases are not upgraded in place. |
| `db.Entry(post).Collection(p => p.Tags).Load()` | `DetailPostDto*.cs` | Same API exists in EF Core (`Entry().Collection().Load()/LoadAsync()`). |
| `DbSet.Local`, `Database.SqlQuery<T>`, `ExecuteSqlCommand` | `Tests/Helpers/DbSnapShot.cs` | `Database.SqlQueryRaw<int>(...)` / `ExecuteSqlRaw`. |
| Lazy loading: EF6 `virtual` navigation props lazy-load by default; EF Core does **not** | `Blog.Posts`, `Post.Tags`, `Post.Blogger`, `Tag.Posts` | Do not add lazy-loading proxies. All reads that need navigation must project (GenericServices DTOs do) or `Include`. Audit `ResetBlogs`/tests for implicit lazy loads. |
| `MultipleActiveResultSets=True` relied on by lazy loads | connection string | Keep MARS in the connection string. |
| Seed XML files must remain embedded resources with the same manifest names `DataLayer.Startup.Internal.BlogsContentSimple.xml` / `BlogsContextMedium.xml` | `DataLayer/Startup/Internal/*.xml` | SDK projects don't embed `.xml` by default → explicit `<EmbeddedResource Include="Startup\Internal\*.xml" />`. Default RootNamespace `DataLayer` keeps the names. |

---

## 3. GenericServices — DTO-by-DTO plan (ServiceLayer)

| DTO | Legacy | New |
|---|---|---|
| `BlogServices/BlogListDto` | `EfGenericDto<Blog,BlogListDto>`, `PostsCount` via AutoMapper aggregate | `BlogListDto : ILinkToEntity<Blog>`; `PostsCount` still auto-flattened (`Posts.Count()`). |
| `TagServices/TagListDto` | same pattern, `PostsCount` | `ILinkToEntity<Tag>` (verified in probe). |
| `PostServices/SimplePostDto` (+ `SimplePostDtoAsync`) | list only; `BloggerName` (flattening), `ICollection<Tag> Tags` projected, `TagNames`/`LastUpdatedUtc` computed get-only props | `ILinkToEntity<Post>`; `BloggerName` flattens `Blogger.Name`; keep `Tags` projection (EF Core translates collection projection) and the computed get-only props (AutoMapper ignores get-only props on ProjectTo). |
| `PostServices/DetailPostDto` (+ `DetailPostDtoAsync`) | overrides `SetupSecondaryData` (Bloggers dropdown, Tags multi-select), `CreateDataFromDto`/`UpdateDataFromDto` (resolve Blogger, load/replace Tags), `SupportedFunctions` | `ILinkToEntity<Post>` for reads; dropdown population + create/update with tag/blogger resolution move into `IDetailPostService` / `IDetailPostServiceAsync` (§10) which use `ICrudServices` for read/save and the DbContext for the many-to-many fix-up. `PerDtoConfig` must `Ignore` `Bloggers`/`UserChosenTags`/`Tags` on DTO→entity. |
| `UiClasses/DropDownListType`, `MultiSelectListType` | plain classes | unchanged (no framework deps). |
| `Tests/Helpers/SimpleTagDto(Async)` | `EfGenericDto<Tag,...>` used by tests | `ILinkToEntity<Tag>`. |

GenericServices startup: `services.GenericServicesSimpleSetup<SampleWebAppDb>(new GenericServicesConfig { DirectAccessValidateOnSave = true, DtoAccessValidateOnSave = true }, typeof(ServiceLayerMarker).Assembly)`
— scans the given assemblies for `ILinkToEntity<>` DTOs and builds AutoMapper config once (singleton).
`IStatusGeneric.Message` default success messages differ from GenericServices 1.x (e.g. `Successfully created a Tag`) — controllers should keep using `crud.Message` for `TempData["message"]`.

---

## 4. OWIN + ASP.NET Identity

- Packages referenced: `Microsoft.AspNet.Identity.Core/EntityFramework/Owin 2.1.0`, `Microsoft.Owin*`, `Owin`.
- **Not wired up**: no `Startup.cs`/`[assembly: OwinStartup]`, no `IdentityDbContext`, no `AccountController`; `_LoginPartial` is commented out in `_Layout.cshtml`.
- Decision: **drop all OWIN/Identity packages; do not add ASP.NET Core Identity.** (If auth is ever needed: `AddDefaultIdentity<IdentityUser>().AddEntityFrameworkStores<...>()` — different schema, password hasher v3, cookie middleware instead of OWIN.)

## 5. SignalR 2.x

- Packages: `Microsoft.AspNet.SignalR(.Core/.JS/.SystemWeb) 2.0.3`.
- Server: **no `Hub` subclasses, no `MapSignalR()`** anywhere in the solution.
- Client: `Scripts/jquery.signalR-2.0.3*.js`, `Scripts/ActionRunner*.js`/`ActionRunnerComms.js` (expects an `ActionHub` via `$.hubConnection` from the removed GenericActions library). The bundle that included them is **commented out** in `BundleConfig.cs`, and no view references them.
- Decision: **SignalR is dead code → remove packages and the SignalR/ActionRunner JS; do not add ASP.NET Core SignalR** (there is no hub to port). If reintroduced: `builder.Services.AddSignalR()` + `app.MapHub<T>("/hub")`, client `@microsoft/signalr` (`new signalR.HubConnectionBuilder().withUrl(...)`) — the jQuery `$.hubConnection` API does not work against Core.

## 6. Bundling / static assets

- `System.Web.Optimization`, `WebGrease`, `Antlr`, `Microsoft.AspNet.Web.Optimization` — no Core equivalent; removed.
- Views: `_Layout.cshtml` (`@Styles.Render("~/Content/css")`, `@Scripts.Render("~/bundles/javascript")`, Modernizr), `Posts/Create|Edit.cshtml`, `PostsAsync/Create|Edit.cshtml` (`@Scripts.Render("~/bundles/jqueryval")`).
- Replacement: move `Content/` → `wwwroot/css` (+ `wwwroot/css/img` or keep `wwwroot/Content`), `Scripts/` → `wwwroot/js` (or `wwwroot/lib`), `fonts/` → `wwwroot/fonts` (Bootstrap 3 CSS references `../fonts/` — **keep the relative relationship** between css and fonts dirs). `app.UseStaticFiles()` / `app.MapStaticAssets()`. Layout uses `<link rel="stylesheet" href="~/css/bootstrap.css" asp-append-version="true" />` etc. jqueryval → `_ValidationScriptsPartial.cshtml` included via `@section scripts { <partial name="_ValidationScriptsPartial" /> }`.
- **As implemented (Subsession B):** `Content/` → `wwwroot/Content`, `Scripts/` → `wwwroot/Scripts`, `fonts/` → `wwwroot/fonts`, `favicon.ico` → `wwwroot/` (folder names kept, so every URL is unchanged and `Content/bootstrap.css` → `../fonts/` still resolves). Layout/`_ValidationScriptsPartial` use `<environment>` to pick unminified files in Development and `.min` otherwise. Program uses `app.MapStaticAssets()` + `.WithStaticAssets()` on the default route. Deleted: SignalR, ActionRunner*, Modernizr, Respond, `*-vsdoc.js`, `*.intellisense.js`, `_references.js`, `jquery-1.10.2.min.map`. Left in place (unreferenced, harmless): jQuery UI + themes, jquery-notify/notify.css, `npm.js`.
- **Gotcha:** `ViewBag.Title` (used by `_Layout`) collides with `DetailPostDto.Title`: in Core `@Html.EditorFor(m => m.Title)` resolves the string-based template lookup against `ViewData["Title"]` and loses the property's validation metadata (no `data-val-*`). The four Posts/PostsAsync Create/Edit views therefore use `@Html.TextBoxFor(m => m.Title, ...)`.
- `Modernizr` and `Respond` were IE8/9 shims — drop. Remove `*.min.map`/`-vsdoc.js` / `_references.js` intellisense files.
- Unobtrusive validation: Core uses `jquery.validate.unobtrusive` the same way; keep jQuery 1.10.2 / Bootstrap 3.0 assets that the views' markup is written for.

## 7. Dependency injection

- Legacy: `Autofac 3.5` + `Autofac.Mvc5` (`AutofacDependencyResolver`), modules `DataLayerModule`, `BizLayerModule`, `ServiceLayerModule` doing `RegisterAssemblyTypes(...).AsImplementedInterfaces()`, plus the **`DiModelBinder`** hack: MVC5's `DefaultModelBinder.CreateModel` was overridden so **any interface-typed action parameter** (e.g. `Index(int? id, IListService service)`) and `SampleWebAppDb` were resolved from the container.
- Core equivalent: `[FromServices]` on the parameter: `public IActionResult Index(int? id, [FromServices] ICrudServices service)`. (Core also infers `[FromServices]` for parameters whose type is registered in DI on `[ApiController]`s only — these are MVC controllers, so be explicit.)
- Decision: **built-in `Microsoft.Extensions.DependencyInjection`** (no Autofac). Modules become `IServiceCollection` extension methods: `AddDataLayer(cs, isAzure)`, `AddBizLayer()`, `AddServiceLayer(cs, isAzure)` (calls the other two + GenericServices setup). `*Initialise` classes keep their static startup role (`ServiceLayerInitialise.InitialiseThis(IServiceProvider, bool canCreateDatabase)` → migrate + seed).
- Lifetimes: `SampleWebAppDb` scoped (AddDbContext), `ICrudServices` scoped (GenericServices), AutoMapper config singleton.
- Tests `Group03ServiceLayer/Test10DiSimple.cs` and `Test11AutoFacModules.cs` exercised Autofac itself → port to the built-in container (`ServiceCollection` + `BuildServiceProvider(validateScopes: true)`), asserting the same resolutions.

## 8. AutoMapper 3.2/4.2 → 14.0.0

- Static `Mapper.CreateMap<,>()` / `Mapper.Map` (removed in AutoMapper 9) are not called directly in this solution — GenericServices 1.x hid them. EfCore.GenericServices owns the `MapperConfiguration`; per-DTO customisation via `PerDtoConfig<TDto,TEntity>` (`AlterReadMapping` / `AlterSaveMapping`).
- `ProjectTo` requires mappings that EF Core can translate to SQL (no client-only methods in `MapFrom`).
- Version pin: §0.1.

## 9. Project-file conversion

| Legacy | SDK-style |
|---|---|
| `packages.config` + `<Reference HintPath=..\packages\...>` | `<PackageReference>`; delete `packages.config`, `packages/` folder refs |
| `Properties/AssemblyInfo.cs` | Delete (SDK generates attributes; duplicates cause CS0579). `[assembly: InternalsVisibleTo("Tests")]` in DataLayer/ServiceLayer AssemblyInfo → `<InternalsVisibleTo Include="Tests" />` in the `.csproj`. |
| `<Compile Include=...>` lists | implicit globbing — remove; beware stray files now compiled automatically (e.g. `App_Start`, `Infrastructure` dead files must be deleted, not just excluded) |
| `Web.config` + `Web.Debug/Release.config` transforms | `appsettings.json` (empty `ConnectionStrings:SampleWebAppDb`, `HostType`) + `appsettings.Development.json` (local docker SQL Server). Environment via `ASPNETCORE_ENVIRONMENT`. |
| `App.config` binding redirects | Not needed (no binding redirects on .NET Core) |
| `Microsoft.Web.Infrastructure`, `Microsoft.CSharp`, `System.*` framework refs | implicit via `Microsoft.NET.Sdk` / `Microsoft.NET.Sdk.Web` |
| Web project GUID `{349c5851-...}` project type in `.sln` | SDK-style C# project type `{9A19103F-16F7-4668-BE54-9A1E7A4F7556}`; ensure `.sln` lists all 5 projects |
| `GenericLibsBase` (logging, `ISuccessOrErrors`), `log4net`, `DelegateDecompiler`, `Mono.Reflection`, `MarkdownSharp` (unused) | `Microsoft.Extensions.Logging.ILogger`, `IStatusGeneric`; drop the rest. `Log4NetGenericLogger`/`TraceGenericLogger`/`Log4Net.xml` deleted. |
| NUnit 2.6.3 (`[TestFixtureSetUp]`, `[TestFixtureTearDown]`, `Assert.AreEqual`, `ExpectedException`) | NUnit 4.x: `[OneTimeSetUp]`/`[OneTimeTearDown]`; **classic asserts moved** to `NUnit.Framework.Legacy.ClassicAssert` (or rewrite to `Assert.That`); `[ExpectedException]` → `Assert.Throws`. Add `Microsoft.NET.Test.Sdk` + `NUnit3TestAdapter`. Moq 4.2 → 4.20.x. Tests' own `ExtendAsserts` helpers must be updated. |

## 10. Cross-subsession API contract (binding)

Subsession A implements, B and C consume. Namespaces/types not listed here keep their legacy names.

```csharp
// DataLayer
namespace DataLayer.DataClasses {
  public class SampleWebAppDb : DbContext {
    public const string NameOfConnectionString = "SampleWebAppDb";
    public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options);
    public DbSet<Blog> Blogs; public DbSet<Post> Posts; public DbSet<Tag> Tags;
    public IStatusGeneric SaveChangesWithChecking();          // validates + slug uniqueness, never throws for validation
    public Task<IStatusGeneric> SaveChangesWithCheckingAsync();
  }
}
namespace DataLayer.Startup {
  public enum TestDataSelection { Small = 0, Medium = 1 }
  public static class DataLayerInitialise {
    public static void InitialiseThis(SampleWebAppDb db, bool canCreateDatabase); // Migrate() if canCreateDatabase
    public static void ResetBlogs(SampleWebAppDb db, TestDataSelection selection);
    public static void SeedIfEmpty(SampleWebAppDb db, TestDataSelection selection);
  }
  public static class DataLayerServiceCollectionExtensions {
    public static IServiceCollection AddDataLayer(this IServiceCollection s, string connectionString, bool isAzure = false);
  }
}
namespace DataLayer.DataClasses {
  // dotnet-ef design-time factory: ConnectionStrings__SampleWebAppDb env var, else local docker SQL Server (DB SampleWebAppDb)
  public class SampleWebAppDbDesignTimeFactory : IDesignTimeDbContextFactory<SampleWebAppDb> { }
}
// BizLayer
namespace BizLayer.Startup { public static class BizLayerServiceCollectionExtensions { public static IServiceCollection AddBizLayer(this IServiceCollection s); } }   // legacy BizLayerInitialise (empty) removed
// ServiceLayer
namespace ServiceLayer.Startup {
  public static class ServiceLayerServiceCollectionExtensions {
    // AddDataLayer + AddBizLayer + GenericServicesSimpleSetup<SampleWebAppDb>(validate-on-save) + IDetailPostService(+Async)
    public static IServiceCollection AddServiceLayer(this IServiceCollection s, string connectionString, bool isAzure = false);
  }
  public static class ServiceLayerInitialise {
    public static void InitialiseThis(IServiceProvider rootProvider, bool canCreateDatabase); // scope → migrate → SeedIfEmpty(Medium)
  }
}
namespace ServiceLayer.PostServices {
  public class SimplePostDto : ILinkToEntity<Post> { /* PostId, BlogId, BloggerName, Title, Tags, TagNames, LastUpdated, LastUpdatedUtc */ }
  public class SimplePostDtoAsync : ILinkToEntity<Post> { /* same */ }
  public static class SimplePostQueryExtensions {          // Posts Index(int? id): blogId null/0 => no filter
    public static IQueryable<SimplePostDto> FilterByBlogId(this IQueryable<SimplePostDto> posts, int? blogId);
    public static IQueryable<SimplePostDtoAsync> FilterByBlogId(this IQueryable<SimplePostDtoAsync> posts, int? blogId);
  }
  public interface IDetailPostDto { /* PostId, Title, Content, BlogId, Tags, Bloggers, UserChosenTags */ }
  public class DetailPostDto : ILinkToEntity<Post>, IDetailPostDto {
    /* PostId, Title, Content, BlogId, BloggerName, Tags, LastUpdated, LastUpdatedUtc, TagNames,
       Bloggers (DropDownListType), UserChosenTags (MultiSelectListType) — both non-null after ctor */ }
  public class DetailPostDtoAsync : ILinkToEntity<Post>, IDetailPostDto { /* same shape */ }
  public class DetailPostDtoConfig : PerDtoConfig<DetailPostDto, Post> { }            // ignores UI lists / Tags / Blogger / LastUpdated
  public class DetailPostDtoAsyncConfig : PerDtoConfig<DetailPostDtoAsync, Post> { }
  public interface IDetailPostService {               // impl DetailPostService (scoped)
    DetailPostDto GetDetail(int postId);           // null + invalid Status if missing
    DetailPostDto GetNew();                        // dropdowns populated (replaces ICreateSetupService)
    DetailPostDto GetForEdit(int postId);          // null + invalid Status if missing (replaces IUpdateSetupService)
    DetailPostDto ResetDto(DetailPostDto dto);     // repopulates dropdowns keeping user selections (Bloggers.SelectedValue, UserChosenTags.FinalSelection)
    IStatusGeneric Create(DetailPostDto dto);      // resolves Blogger + Tags, validates, saves; sets dto.PostId on success
    IStatusGeneric Update(DetailPostDto dto);      // replaces the Post's tags with the user's selection
    IStatusGeneric Status { get; }                 // status of the last GetDetail/GetForEdit
  }
  public interface IDetailPostServiceAsync {          // impl DetailPostServiceAsync (scoped)
    Task<DetailPostDtoAsync> GetDetailAsync(int postId);
    Task<DetailPostDtoAsync> GetNewAsync();
    Task<DetailPostDtoAsync> GetForEditAsync(int postId);
    Task<DetailPostDtoAsync> ResetDtoAsync(DetailPostDtoAsync dto);
    Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto);
    Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto);
    IStatusGeneric Status { get; }
  }
}
namespace ServiceLayer.TagServices  { public class TagListDto  : ILinkToEntity<Tag>  { /* TagId, Slug, Name, PostsCount */ } }
namespace ServiceLayer.BlogServices { public class BlogListDto : ILinkToEntity<Blog> { /* BlogId, Name, EmailAddress, PostsCount */ } }
```

Controller patterns (B):
- Blogs/Tags/Posts list: `[FromServices] ICrudServices service` → `service.ReadManyNoTracked<TagListDto>().ToList()`; Posts `Index(int? id)` filters by `BlogId` via a ServiceLayer query/extension, not EF in the controller.
- Tags/Blogs details/edit/create/delete use the entity class directly (`ReadSingle<Tag>(id)`, `UpdateAndSave(tag)`, `CreateAndSave(tag)`, `DeleteAndSave<Tag>(id)`), errors via `service.CopyErrorsToModelState(ModelState, dto)`.
- Posts details/create/edit via `[FromServices] IDetailPostService`, delete via `ICrudServices.DeleteAndSave<Post>(id)`.
- `*Async` controllers use `ICrudServicesAsync` / `IDetailPostServiceAsync`.
- `NumPosts` / `Reset` take `[FromServices] SampleWebAppDb db`.
- Detail post error member names (for ModelState): `Bloggers`, `UserChosenTags`, `Title` (Post.Validate), `""` for top-level content errors. Messages are the legacy ones (e.g. `The blogger was not selected. You must do that before the post can be saved.`, `You must select at least one tag for the post.`).
- `ICrudServices`/`ICrudServicesAsync` are scoped and their status (`IsValid`/`Errors`) accumulates across calls on the same instance, so use one CRUD call per request/scope (tests: create a new scope per operation after an expected failure).
- Medium seed data: 4 blogs, 17 posts, 8 tags. Small: see `BlogsContentSimple.xml`.

## 11. Runtime environment (VM)

- .NET SDK 10.0.301 present (also 8.0.425).
- SQL Server: Docker `mcr.microsoft.com/mssql/server:2022-latest`, container `mssql`, `localhost,1433`. LocalDB is Windows-only.
- Dev connection string (`appsettings.Development.json`, throwaway local container password, not a real secret):
  `Server=localhost,1433;Database=SampleWebAppDb;User Id=sa;Password=Str0ng!Passw0rd;TrustServerCertificate=True;MultipleActiveResultSets=True`
  (container started with `docker start mssql || docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=Str0ng!Passw0rd' -e MSSQL_PID=Developer -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest`)
  (`TrustServerCertificate=True` is required: `Microsoft.Data.SqlClient` 5+ defaults to `Encrypt=True`.)
- Run: `ASPNETCORE_ENVIRONMENT=Development dotnet run --project SampleWebApp --urls http://localhost:5000`.
- Tests: `dotnet test` (needs the same SQL Server; test DB name `SampleWebAppDb-Test`).

## 12. Verification artifact

Recorded CRUD video and screenshots: linked from the PR (filled in after Phase 2).
