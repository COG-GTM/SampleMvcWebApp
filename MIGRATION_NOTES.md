# SampleMvcWebApp — ASP.NET MVC 5 / .NET Framework 4.5.1 → ASP.NET Core MVC / .NET 10

Phase 0 research output. This is a **re-platform**, not a version bump: `System.Web` is gone, EF6 is gone,
and the CRUD framework the whole app is built on (`GenericServices` 1.0.9) has a completely different API
in its EF Core successor.

Every finding below was verified against this repository's source (`grep`/read) or against the actual
NuGet packages (downloaded and reflected over / executed locally on the .NET 10 SDK).

---

## 0. Target versions

| Concern | Old | New |
| --- | --- | --- |
| TFM | `net451` | `net10.0` |
| Project format | old-style `.csproj` + `packages.config` | SDK-style + `PackageReference` |
| Web framework | ASP.NET MVC 5.2.3 (`System.Web.Mvc`) | ASP.NET Core MVC (`Microsoft.AspNetCore.App` framework reference) |
| ORM | EntityFramework 6.1.3 | `Microsoft.EntityFrameworkCore.SqlServer` 10.0.10 |
| CRUD framework | `GenericServices` 1.0.9 | `EfCore.GenericServices` 10.0.0 |
| Mapping | AutoMapper 4.2.1 | AutoMapper 14.0.0 (**pinned — see §4.3**) |
| DI | Autofac 3.5.0 + `Autofac.Mvc5` 3.3.1 | Autofac 9.3.1 + `Autofac.Extensions.DependencyInjection` 11.0.2 |
| Tests | NUnit 2.6.3, Moq 4.2 | NUnit 4.6.1 + `NUnit3TestAdapter` 6.2.0 + `Microsoft.NET.Test.Sdk` 18.8.1, Moq 4.20.72 |
| Logging | log4net 2.0.3 + `Log4Net.xml` | `Microsoft.Extensions.Logging` (`ILogger<T>`) |

---

## 1. `System.Web` / `System.Web.Mvc` — no equivalent in ASP.NET Core

`System.Web.dll` is part of the .NET Framework GAC and was **not** ported. Every usage must be replaced.
Complete enumeration for this solution:

### 1.1 `SampleWebApp` (all must change)

| File | Legacy API | Replacement |
| --- | --- | --- |
| `Global.asax` + `Global.asax.cs` | `System.Web.HttpApplication`, `Application_Start` | delete both; `Program.cs` (minimal hosting) |
| `Global.asax.cs` | `AreaRegistration.RegisterAllAreas()` | delete — no areas exist in this solution |
| `Global.asax.cs` | `RouteConfig.RegisterRoutes(RouteTable.Routes)` | `app.MapControllerRoute("default", "{controller=Home}/{action=Index}/{id?}")` |
| `Global.asax.cs` | `FilterConfig.RegisterGlobalFilters(GlobalFilters.Filters)` | `builder.Services.AddControllersWithViews(o => o.Filters.Add(...))`; the only filter is `HandleErrorAttribute` → replaced by `app.UseExceptionHandler("/Home/Error")` + `UseStatusCodePages` |
| `Global.asax.cs` | `BundleConfig.RegisterBundles(BundleTable.Bundles)` | delete — see §7 |
| `Global.asax.cs` | `ModelBinders.Binders.DefaultBinder = new DiModelBinder()` | delete — see §8 |
| `App_Start/RouteConfig.cs` | `System.Web.Routing.RouteCollection`, `UrlParameter.Optional`, `IgnoreRoute("{resource}.axd/...")` | endpoint routing in `Program.cs`; `.axd` ignore is meaningless in Core |
| `App_Start/FilterConfig.cs` | `GlobalFilterCollection`, `HandleErrorAttribute` | `MvcOptions.Filters` / exception-handler middleware |
| `App_Start/BundleConfig.cs` | `System.Web.Optimization.ScriptBundle` / `StyleBundle` | delete — see §7 |
| `Controllers/*.cs` (6 controllers) | `using System.Web.Mvc`, `Controller`, `ActionResult`, `[HttpPost]`, `[ValidateAntiForgeryToken]` | `using Microsoft.AspNetCore.Mvc`; `Controller`/`IActionResult` exist with the same names — the *namespace* is the change |
| `Controllers/PostsController.cs`, `PostsAsyncController.cs`, `TagsController.cs`, `TagsAsyncController.cs` | `new MvcHtmlString(response.ErrorsAsHtml())` stored in `TempData` | **`TempData` in ASP.NET Core only serialises primitives/strings** — an `HtmlString` cannot round-trip. Store the raw string in `TempData` and render with `@Html.Raw(...)` in the view |
| `Infrastructure/DiModelBinder.cs` | `DefaultModelBinder.CreateModel`, `DependencyResolver.Current` | delete — see §8 |
| `Infrastructure/JsonNetResult.cs` | `HttpResponseBase`, `response.Output`, `ContentEncoding` | delete — `JsonResult`/`return Json(obj)` in Core already uses a configurable serializer (`AddNewtonsoftJson` if Newtonsoft semantics are required) |
| `Infrastructure/ValidationHelper.cs` | `System.Web.Mvc.ModelStateDictionary`, `JsonResult` | `Microsoft.AspNetCore.Mvc.ModelBinding.ModelStateDictionary`, `Microsoft.AspNetCore.Mvc.JsonResult`. Note `modelState.AddModelError(key, msg)` is unchanged, but iterating gives `KeyValuePair<string, ModelStateEntry>` |
| `Infrastructure/WebUiInitialise.cs` | `HttpApplication`, `application.Server.MapPath("~/Log4Net.xml")` | `IWebHostEnvironment.ContentRootPath` / `WebRootPath`; the whole class collapses into `Program.cs` |
| `Views/Shared/Error.cshtml` | `@model System.Web.Mvc.HandleErrorInfo` | `HandleErrorInfo` does not exist. Use an `ErrorViewModel` + `IExceptionHandlerPathFeature` |
| `Views/Web.config` | `system.web.webPages.razor` section, `pageBaseType`, `<namespaces>` | delete; replaced by `Views/_ViewImports.cshtml` (`@using`, `@addTagHelper *, Microsoft.AspNetCore.Mvc.TagHelpers`) |
| `Web.config`, `Web.Debug.config`, `Web.Release.config`, `Web.AzureRelease.config`, `Web.WebWizRelease.config` | XDT transforms, `<connectionStrings>`, `<appSettings>`, `<runtime><assemblyBinding>` | `appsettings.json` + `appsettings.{Environment}.json`; binding redirects no longer exist |
| `Properties/Settings.settings` / `Settings.Designer.cs` | `System.Configuration.ApplicationSettingsBase` (`Settings.Default.HostTypeString`) | not supported in .NET Core — move to `appsettings.json` bound via `IConfiguration`/`IOptions<T>` |
| `Models/InternalsInfo.cs` | `new PerformanceCounter("Memory", "Available MBytes")` | **Windows-only.** `System.Diagnostics.PerformanceCounter` throws `PlatformNotSupportedException` on Linux. Replace with `GC.GetGCMemoryInfo().TotalAvailableMemoryBytes` / `Environment.WorkingSet` |

### 1.2 `Tests`

| File | Legacy API | Replacement |
| --- | --- | --- |
| `Helpers/JsonHelper.cs` | `System.Web.Helpers.Json` | `System.Text.Json` or `Newtonsoft.Json` |
| `Helpers/ModelStateTester.cs` | `System.Web.Mvc.ModelStateDictionary` | `Microsoft.AspNetCore.Mvc.ModelBinding.ModelStateDictionary` |

### 1.3 Not present (good news)

- **No `AreaRegistration` subclasses** and no `Areas/` folder — `RegisterAllAreas()` is a no-op to delete.
- **No `HttpModule`/`HttpHandler`** implementations.
- **No `Session` usage** anywhere.

---

## 2. Entity Framework 6 → EF Core 10

`DataLayer/DataClasses/SampleWebAppDb.cs` is the focal point.

### 2.1 Removed APIs (hard compile errors)

| EF6 API (used at) | Status in EF Core | Replacement |
| --- | --- | --- |
| `System.Data.Entity` namespace | gone | `Microsoft.EntityFrameworkCore` |
| `DbContext(string nameOrConnectionString)` — `SampleWebAppDb() : base("name=SampleWebAppDb")` | **gone.** There is no connection-string-by-name resolution and no `App.config`/`Web.config` lookup | ctor must take `DbContextOptions<SampleWebAppDb>`; connection string comes from `IConfiguration` via `AddDbContext` |
| `protected override DbEntityValidationResult ValidateEntity(DbEntityEntry, IDictionary<object,object>)` (SampleWebAppDb.cs:85) | **gone.** EF Core does no validation at all on `SaveChanges` | re-implement in `SaveChanges`/`SaveChangesAsync` — see §2.2 |
| `DbEntityValidationResult`, `DbValidationError`, `DbEntityEntry` | gone | `System.ComponentModel.DataAnnotations.ValidationResult` + `Validator.TryValidateObject`; `EntityEntry` for change tracking |
| `Database.SetInitializer(new CreateDatabaseIfNotExists<T>())` / `NullDatabaseInitializer<T>` (`DataLayerInitialise.InitialiseThis`) | **gone.** No initializers in EF Core | `context.Database.Migrate()` (preferred) or `EnsureCreated()` |
| `DbConfiguration` / `SetExecutionStrategy("System.Data.SqlClient", () => new SqlAzureExecutionStrategy())` (`EfConfiguration.cs`) | gone | `options.UseSqlServer(cs, o => o.EnableRetryOnFailure())` |
| `db.Database.SqlQuery<int>("SELECT ...")` (`Tests/Helpers/DbSnapShot.cs`) | gone | `db.Database.SqlQueryRaw<int>(...)` (EF Core 7+) |
| `db.Entry(post).Collection(p => p.Tags).Load()` (`DetailPostDto.ChangeTagsBasedOnMultiSelectList`) | **still exists**, same shape | no change needed |
| `DbSet.Find(id)` / `AddRange` / `Remove` | still exist | no change |
| `SaveChangesAsync()` override | signature changed | must override `SaveChangesAsync(bool acceptAllChangesOnSuccess, CancellationToken)` — overriding the parameterless one no longer intercepts everything |
| `IDbSet<T>` | gone | `DbSet<T>` |

### 2.2 The `Tag.Slug` uniqueness check

EF6 ran this inside `ValidateEntity` and surfaced it as a validation error through
`SaveChangesWithChecking()`. Two complementary replacements:

1. **Unique index** in `OnModelCreating`:
   `modelBuilder.Entity<Tag>().HasIndex(x => x.Slug).IsUnique();`
   — this is the correct database-level guarantee, but it surfaces as a `DbUpdateException`
   wrapping `SqlException` 2601/2627, not as a friendly validation error.
2. **Pre-save check in `SaveChanges`/`SaveChangesAsync`**, preserving the exact existing message
   (`Tests/UnitTests/Group01DataLayer/Test13Validation.cs` asserts on it verbatim):
   `"The Slug on tag '{name}' must be unique and is already being used."`
   Iterate `ChangeTracker.Entries<Tag>()` where `State is Added or Modified` and re-run the
   `Tags.Any(x => x.TagId != tag.TagId && x.Slug == tag.Slug)` query.

`IValidatableObject` on `Post` (title `!`/`?` rules, `sheep./lamb./cow./calf.` content rules) was
also executed by EF6's automatic validation. **EF Core will silently skip it** — it must be run
explicitly via `Validator.TryValidateObject(entity, ctx, results, validateAllProperties: true)`
inside the overridden `SaveChanges`.

`EfCore.GenericServices` offers `PerDtoConfig.UseSaveChangesWithValidation` /
`GenericServicesConfig.DirectAccessValidateOnSave`, which call
`SaveChangesWithValidation()` — but that only covers saves made *through* the CRUD services, not
direct `db.SaveChanges()` calls made by `Tests` and by `DataLayerInitialise.ResetBlogs`. Doing the
validation inside the `DbContext` override covers both.

### 2.3 `HandleChangeTracking` / `TrackUpdate`

`SampleWebAppDb.HandleChangeTracking()` has a **latent bug** worth preserving-or-fixing consciously:
it uses `return` instead of `continue` when an entry is not a `TrackUpdate`, so it stops at the first
non-`TrackUpdate` entity (e.g. a `Tag`) and silently skips the rest. The EF Core port should use
`foreach (var e in ChangeTracker.Entries<TrackUpdate>().Where(...)) e.Entity.UpdateTrackingInfo();`
which is both correct and simpler. Call it from **both** `SaveChanges(bool)` and
`SaveChangesAsync(bool, CancellationToken)`.

`TrackUpdate.UpdateTrackingInfo()` is `internal` and relies on `InternalsVisibleTo("Tests")` — see §9.

### 2.4 Schema / model differences

- **Many-to-many `Post` ↔ `Tag`**: EF6 auto-created a join table named **`TagPosts`** with columns
  `Tag_TagId` / `Post_PostId` (`Tests/Helpers/DbSnapShot.cs` hard-codes `SELECT COUNT(*) FROM dbo.TagPosts`).
  EF Core's skip-navigation convention would name it `PostTag` with `PostsPostId`/`TagsTagId`.
  Keep the old name explicitly if the existing schema matters:
  ```csharp
  modelBuilder.Entity<Post>()
      .HasMany(p => p.Tags).WithMany(t => t.Posts)
      .UsingEntity("TagPosts",
          l => l.HasOne(typeof(Tag)).WithMany().HasForeignKey("Tag_TagId"),
          r => r.HasOne(typeof(Post)).WithMany().HasForeignKey("Post_PostId"));
  ```
- **`Post.Blogger` cascade delete**: EF6 required navigation + `int BlogId` gives cascade delete by
  convention in both, but EF Core defaults `DeleteBehavior.Cascade` only for required FKs — verify.
- **`TrackUpdate.LastUpdated` has a `protected set`** — EF Core can still write it (it uses the
  backing field / non-public setter), same as EF6.
- **`[UIHint("HiddenInput")]`, `[MinLength]`, `[MaxLength]`, `[Required]`, `[EmailAddress]`,
  `[RegularExpression]`** all still work; `[MinLength]` is *not* mapped to the schema in either.
- **Lazy loading**: `Post.Blogger` is `virtual`. EF6 had lazy loading on by default; EF Core needs
  `Microsoft.EntityFrameworkCore.Proxies` + `UseLazyLoadingProxies()`, otherwise the navigation is
  `null` unless `Include`d. `SimplePostDto.BloggerName` / `DetailPostDto.BloggerName` are populated
  by AutoMapper *projection* (`ProjectTo`), which generates a SQL join and does **not** need lazy
  loading — but `DetailPostDto.SetupRestOfDto` and `Post.ToString()` do touch `Blogger` directly.
  Prefer explicit `Include`/projection over re-enabling proxies.

### 2.5 Migrations & seeding

- There are **no EF6 `Migrations/` in this repo** (the DB was created by `CreateDatabaseIfNotExists`),
  so no migration history to port: generate a single initial EF Core migration
  (`dotnet ef migrations add InitialCreate -p DataLayer -s SampleWebApp`).
- `DataLayer/Startup/DataLayerInitialise.InitialiseThis(bool isAzure, bool canCreateDatabase)` loses
  its reason to exist (initializers are gone). Reduce it to migrate-and-seed.
- `ResetBlogs(SampleWebAppDb, TestDataSelection)` reads embedded XML
  (`DataLayer/Startup/Internal/BlogsContentSimple.xml`, `BlogsContextMedium.xml`) via
  `LoadDbDataFromXml`. Those must stay `<EmbeddedResource>` in the SDK-style csproj — the default
  glob does **not** embed them, so add:
  `<EmbeddedResource Include="Startup\Internal\*.xml" />`. The manifest resource names
  (`DataLayer.Startup.Internal.BlogsContentSimple.xml`) are unchanged by the SDK-style conversion
  as long as `RootNamespace` stays `DataLayer`.
- `ResetBlogs` deletes Posts → Tags → Blogs then `SaveChanges()`. With the EF Core join table this
  still works, but the delete of `Tags` while `Posts` rows exist relies on cascade through
  `TagPosts`; delete order must remain Posts-first.
- `context.SaveChangesWithChecking()` was a `GenericServices` (EF6) extension returning
  `ISuccessOrErrors`. Its EF Core counterpart is `SaveChangesWithValidation()` in
  `GenericServices.SaveChangesExtensions`, returning `StatusGeneric.IStatusGeneric`.

---

## 3. `GenericServices` 1.0.9 → `EfCore.GenericServices` 10.0.0 — ⚠️ HIGHEST RISK

**`GenericServices` 1.0.9 is EF6-only and cannot load on .NET 10.** The successor,
`EfCore.GenericServices` 10.0.0 (published 2025-11-24, `net10.0` TFM, by the same author), is a
**complete rewrite with an incompatible API**. Verified by downloading the package and reflecting
over `lib/net10.0/GenericServices.dll`.

### 3.1 Every old interface is gone

The old library exposed **13** service interfaces; the new one exposes **two**
(`ICrudServices`, `ICrudServicesAsync`, plus `ICrudServices<TContext>` variants for multi-DbContext apps).

| Old interface (used in controllers) | Old call | New equivalent on `ICrudServices` |
| --- | --- | --- |
| `IListService` | `service.GetAll<TDto>()` → `IQueryable<TDto>` | `service.ReadManyNoTracked<TDto>()` → `IQueryable<TDto>` |
| `IDetailService` | `service.GetDetail<TDto>(id)` → `ISuccessOrErrors<TDto>` (`.Result`) | `service.ReadSingle<TDto>(id)` → **`TDto` directly**; check `service.IsValid` afterwards |
| `IUpdateSetupService` | `service.GetOriginal<TDto>(id).Result` | `service.ReadSingle<TDto>(id)` |
| `IUpdateService` | `service.Update(dto)` → `ISuccessOrErrors` | `service.UpdateAndSave(dto)` → **`void`**; status is on the service itself |
| `IUpdateService` | `service.ResetDto(dto)` | **no equivalent** — see §3.3 |
| `ICreateSetupService` | `service.GetDto<TDto>()` | **no equivalent** — `new TDto()` + populate secondary data yourself |
| `ICreateService` | `service.Create(dto)` → `ISuccessOrErrors` | `service.CreateAndSave(dto)` → returns the created **entity** |
| `IDeleteService` | `service.Delete<TEntity>(id)` → `ISuccessOrErrors` | `service.DeleteAndSave<TEntity>(id)` → `void`; also `DeleteWithActionAndSave<T>(func, keys)` |
| `IDetailServiceAsync`, `IUpdateSetupServiceAsync`, `IUpdateServiceAsync`, `ICreateSetupServiceAsync`, `ICreateServiceAsync`, `IDeleteServiceAsync` | `...Async(...)` | `ICrudServicesAsync`: `ReadSingleAsync`, `CreateAndSaveAsync`, `UpdateAndSaveAsync`, `DeleteAndSaveAsync` (`ReadManyNoTracked` stays sync — it returns `IQueryable`) |

Exact new surface (from reflection):

```
ICrudServices : StatusGeneric.IStatusGeneric
    DbContext Context { get; }
    T                ReadSingle<T>(params object[] keys)
    T                ReadSingle<T>(Expression<Func<T,bool>> where)
    IQueryable<T>    ReadManyNoTracked<T>()
    IQueryable<TDto> ProjectFromEntityToDto<TEntity,TDto>(Func<IQueryable<TEntity>,IQueryable<TEntity>>)
    T                CreateAndSave<T>(T entityOrDto, string ctorOrStaticMethodName = null)
    void             UpdateAndSave<T>(T entityOrDto, string methodName = null)
    TEntity          UpdateAndSave<TEntity>(JsonPatchDocument<TEntity>, params object[] keys)
    void             DeleteAndSave<TEntity>(params object[] keys)
    void             DeleteWithActionAndSave<TEntity>(Func<DbContext,TEntity,IStatusGeneric>, params object[] keys)

StatusGeneric.IStatusGeneric
    bool IsValid { get; }   bool HasErrors { get; }   string Message { get; set; }
    IReadOnlyList<ErrorGeneric> Errors { get; }
    string GetAllErrors(string separator = null)
```

### 3.2 The status/error model changed

| Old (`GenericLibsBase` / `GenericServices.Core`) | New (`StatusGeneric` 1.2.0) |
| --- | --- |
| every method returns `ISuccessOrErrors` / `ISuccessOrErrors<T>` | the **service instance** implements `IStatusGeneric`; methods return the payload (or `void`) |
| `response.IsValid`, `response.SuccessMessage`, `response.Errors` (`ValidationResult`), `response.ErrorsAsHtml()` | `service.IsValid`, `service.Message`, `service.Errors` (`ErrorGeneric` → `.ErrorResult` is a `ValidationResult` with `ErrorMessage` + `MemberNames`), `service.GetAllErrors()` |
| `SuccessOrErrors.Success(...)`, `status.AddNamedParameterError(name, msg)` | `new StatusGenericHandler()`, `status.AddError(msg, params string[] propertyNames)` |

**Consequence for controllers:** `ICrudServices` is *stateful*, so it must be resolved
**scoped** and the status read immediately after the call. `SampleWebApp/Infrastructure/ValidationHelper.CopyErrorsToModelState`
must be rewritten to take `IStatusGeneric` instead of `ISuccessOrErrors` (mapping
`ErrorGeneric.ErrorResult.MemberNames` → model-state keys, same logic otherwise).

### 3.3 DTO model changed completely — affects every DTO in `ServiceLayer`

Old: `public class XDto : EfGenericDto<TEntity, XDto>` with overridable
`SupportedFunctions` (`CrudFunctions.List` / `.AllCrud`), `SetupSecondaryData(context, dto)`,
`CreateDataFromDto(...)`, `UpdateDataFromDto(...)`, and `[DoNotCopyBackToDatabase]`.

New: `public class XDto : ILinkToEntity<TEntity>` — a **pure marker interface**, no base class,
no virtual hooks at all.

| Old member | New |
| --- | --- |
| `EfGenericDto<TEntity,TDto>` base class | `ILinkToEntity<TEntity>` marker interface |
| `SupportedFunctions => CrudFunctions.List` | nothing — a DTO is usable for whatever you call; read-only-ness is expressed by omitting settable properties |
| `[DoNotCopyBackToDatabase]` on `DetailPostDto.LastUpdated` | no attribute. Use `PerDtoConfig<TDto,TEntity>.AlterSaveMapping = cfg => cfg.ForMember(x => x.LastUpdated, o => o.Ignore())`, or make the DTO property get-only |
| `protected override void SetupSecondaryData(IGenericServicesDbContext, TDto)` — populates `DetailPostDto.Bloggers` (`DropDownListType`) and `UserChosenTags` (`MultiSelectListType`) | **no equivalent.** This must move into the controller/a hand-written service |
| `protected override ISuccessOrErrors<Post> CreateDataFromDto(...)` / `UpdateDataFromDto(...)` — `DetailPostDto.SetupRestOfDto`, `SetBloggerIdFromDropDownList`, `ChangeTagsBasedOnMultiSelectList` | `PerDtoConfig.CreateMethod` / `UpdateMethod` name a **DDD method or ctor on the entity**; or `AlterSaveMapping`. The existing logic needs the `DbContext` (it does `db.Blogs.Find`, `db.Tags.Where`, `db.Entry(post).Collection(...).Load()`), which entity methods do not get |
| `IGenericServicesDbContext` (implemented by `SampleWebAppDb`) | gone — `EfCore.GenericServices` takes the concrete `DbContext` |
| `GenericServicesConfig`/logging via `GenericLibsBase.GenericLibsBaseConfig` | `GenericServicesConfig` (different type), `ILogger<T>` |

### 3.4 Recommended shape (decision)

`EfCore.GenericServices` handles the **simple** cases in this app perfectly:

- `BlogListDto`, `TagListDto`, `SimplePostDto`, `SimplePostDtoAsync` → `ILinkToEntity<T>` + `ReadManyNoTracked`
- `Blog` and `Tag` direct CRUD (`BlogsController`, `TagsController`, `TagsAsyncController`) →
  `CreateAndSave` / `ReadSingle` / `UpdateAndSave` / `DeleteAndSave` on the entity types
- `Post` delete → `DeleteAndSave<Post>(id)`

It does **not** cleanly handle `DetailPostDto` create/update, because that flow needs
context-aware secondary data (`Bloggers` dropdown, `UserChosenTags` multi-select) and rewrites a
many-to-many collection. Plan: keep `DetailPostDto` as an `ILinkToEntity<Post>` DTO for reads, and
add an explicit `ServiceLayer` service (e.g. `IPostDtoService` with
`SetupSecondaryData`, `CreateAsync`, `UpdateAsync` returning `IStatusGeneric`) that owns the
blogger/tags logic against `SampleWebAppDb`. This keeps the controllers thin and preserves the
existing UX and validation messages.

Setup in `Program.cs`:

```csharp
builder.Services.GenericServicesSimpleSetup<SampleWebAppDb>(
    Assembly.GetAssembly(typeof(BlogListDto)));   // scans ServiceLayer for ILinkToEntity<> DTOs
```
Unit tests use `db.SetupSingleDtoAndEntities<TDto>()` / `db.SetupEntitiesDirect()` from
`GenericServices.Setup.UnitTestSetup`, then `new CrudServices(db, utData.ConfigAndMapper)`.

---

## 4. AutoMapper

### 4.1 Registration API

AutoMapper 4.x used the **static** `Mapper.CreateMap<,>()` / `Mapper.Map(...)` API (configured
internally by `GenericServices` 1.0.9). That static API was removed in AutoMapper 5.
`EfCore.GenericServices` builds its own `MapperConfiguration`/`IMapper` internally from the
`ILinkToEntity<>` scan — the app does **not** register AutoMapper itself.

### 4.2 Aggregate/flattening conventions still apply

`BlogListDto.PostsCount` ← `Blog.Posts.Count` and `TagListDto.PostsCount` ← `Tag.Posts.Count`
(AutoMapper "Aggregate" convention) and `SimplePostDto.BloggerName` ← `Post.Blogger.Name`
(flattening convention) are unchanged and still work.

### 4.3 ⚠️ AutoMapper version is pinned by a hard incompatibility

- `EfCore.GenericServices` 10.0.0 declares `AutoMapper >= 13.0.1`, so NuGet restores **13.0.1**.
- **AutoMapper 15.x breaks it at runtime**, verified locally:
  `System.MethodAccessException: Attempt by method 'GenericServices.Setup.Internal.SetupDtosAndMappings.CreateConfigAndMapper(...)' to access method 'AutoMapper.MapperConfiguration..ctor(System.Action<AutoMapper.IMapperConfigurationExpression>)' failed.`
  (AutoMapper 15 made that ctor non-public; the `ILoggerFactory` overload is now required.)
- **AutoMapper 14.0.0 works** (verified: create/read/list round-trip against SQLite on .NET 10).
- Both 13.x and 14.x are flagged by `GHSA-rvv3-g6hj-g44x` (DoS via uncontrolled recursion, patched in
  15.1.1 / 16.1.1), so `dotnet restore` emits **NU1903**. There is no version that is both patched and
  compatible with `EfCore.GenericServices` 10.0.0.
  **Decision: pin AutoMapper 14.0.0** (newest compatible) and accept/track the advisory; revisit when
  upstream `EfCore.GenericServices` moves to AutoMapper 15+. Do **not** silence the warning.
  Note the app never maps attacker-controlled recursive graphs, so exposure is minimal.
- Also note AutoMapper 14+ ships under a **dual (commercial) licence**; 13.x is the last MIT release.

---

## 5. OWIN + `Microsoft.AspNet.Identity.*`

`SampleWebApp/packages.config` references `Microsoft.AspNet.Identity.Core/EntityFramework/Owin` 2.1.0,
`Microsoft.Owin*` 2.1/3.0 and the Facebook/Google/Twitter/MicrosoftAccount/OAuth security packages.

**Identity is *not* wired up.** Verified:
- no `Startup.cs`/`Startup.Auth.cs`, no `[assembly: OwinStartup]`, no `IdentityDbContext`,
  no `ApplicationUser`, no `AccountController`, no `_LoginPartial.cshtml` (the `@Html.Partial("_LoginPartial")`
  in `_Layout.cshtml` is commented out);
- `Web.config` has `<authentication mode="None" />`, `<remove name="FormsAuthenticationModule" />`
  and `owin:AutomaticAppStartup = false`;
- the only Identity-ish code is a dead constant `WebUiInitialise.ResetIndentityDatabase = false`.

**Action: drop all OWIN and `Microsoft.AspNet.Identity.*` packages entirely.** No ASP.NET Core Identity
replacement is needed. If auth is added later it would be
`Microsoft.AspNetCore.Identity.EntityFrameworkCore` + `AddIdentity<TUser,TRole>()` + `UseAuthentication()`,
with `IdentityDbContext` and a separate `IdentityUser` model.

---

## 6. SignalR 2.x → ASP.NET Core SignalR

- Packages referenced: `Microsoft.AspNet.SignalR{,.Core,.JS,.SystemWeb}` 2.0.3.
- **There is no server-side `Hub` in this repository.** `grep` for `: Hub` / `IHubContext` /
  `MapSignalR` / `RouteTable.Routes.MapHubs` returns nothing; `BundleConfig.cs` explicitly comments
  out the ActionRunner bundle with the note that this code "has been moved out to another library".
- The client side is orphaned but still present:
  - `Scripts/jquery.signalR-2.0.3.js` (+ `.min.js`) — the ASP.NET SignalR 2 JS client
  - `Scripts/ActionRunnerComms.js` — uses `$.hubConnection()`, `connection.createHubProxy('ActionHub')`,
    `actionChannel.on(...)`, `connection.start()`
  - `Scripts/ActionRunnerUi.js`
  - No view references either script (no `@Scripts.Render("~/bundles/ActionRunner")`).
- ASP.NET Core SignalR is **wire-incompatible** with SignalR 2: server is
  `Microsoft.AspNetCore.SignalR` (in the shared framework, `builder.Services.AddSignalR()` +
  `app.MapHub<T>("/actionhub")`), and the JS client is the npm package `@microsoft/signalr`
  (`new signalR.HubConnectionBuilder().withUrl("/actionhub").build()`, `connection.on(...)`,
  `connection.invoke(...)`). `$.connection` / `createHubProxy` do not exist.
- **Action: drop the SignalR 2 packages and the dead `jquery.signalR-*.js`.** Carry
  `ActionRunner*.js` into `wwwroot/js/` verbatim (they are unreferenced demo assets) and note in the
  README that they target the legacy SignalR 2 protocol. Wiring a real ASP.NET Core hub is out of
  scope for this re-platform since no hub exists to port.

---

## 7. Bundling: `System.Web.Optimization` → `wwwroot` + static files

- `SampleWebApp/App_Start/BundleConfig.cs` defines three live bundles:
  `~/bundles/javascript` (jquery-{version}.js, bootstrap.js, respond.js),
  `~/Content/css` (bootstrap.css, site.css),
  `~/bundles/jqueryval` (`jquery.validate*`).
- View usages (all must be replaced with plain `<script>`/`<link>`):
  - `Views/Shared/_Layout.cshtml:8` `@Styles.Render("~/Content/css")`
  - `Views/Shared/_Layout.cshtml:57` `@Scripts.Render("~/bundles/javascript")`
  - `Views/Posts/Create.cshtml:63`, `Views/Posts/Edit.cshtml:68`,
    `Views/PostsAsync/Create.cshtml:63`, `Views/PostsAsync/Edit.cshtml:66` — `@Scripts.Render("~/bundles/jqueryval")`
- `System.Web.Optimization`, `WebGrease` and `Antlr` (WebGrease's CSS parser) have no .NET Core
  equivalent — delete all three.
- `Modernizr` 2.6.2 and `Respond` 1.2.0 are IE6–8 shims. `Respond` is in the live JS bundle;
  `Modernizr`'s bundle is commented out. Both are obsolete — drop `Respond`/`Modernizr` from the
  bundle content (keep the files if we want a byte-for-byte asset move, but nothing should reference them).
- Static assets must move under `wwwroot/` so `app.UseStaticFiles()` (or `MapStaticAssets()`) can serve them:
  `Content/` → `wwwroot/css/` (+ `wwwroot/css/themes/`, images), `Scripts/` → `wwwroot/js/`,
  `fonts/` → `wwwroot/fonts/`, `favicon.ico` → `wwwroot/favicon.ico`.
  ⚠️ `Content/bootstrap.css` references `../fonts/glyphicons-halflings-regular.woff2`, so the relative
  `css/` ↔ `fonts/` layout must be preserved.
- Use `<environment include="Development">` tag helper for dev vs. min files, and
  `asp-append-version="true"` for cache-busting. `_ViewImports.cshtml` must contain
  `@addTagHelper *, Microsoft.AspNetCore.Mvc.TagHelpers`.
- Client validation (`jquery.validate.unobtrusive`) still needs `ClientValidationEnabled` /
  `UnobtrusiveJavaScriptEnabled` — those `appSettings` keys are gone; unobtrusive validation is on by
  default in ASP.NET Core as long as the scripts are loaded.

---

## 8. DI: `Autofac.Mvc5` + `DiModelBinder` → built-in container / `[FromServices]`

### 8.1 The custom action-parameter injection pattern

This app's signature trick: services are injected as **action method parameters**, not constructor
parameters — e.g. `public ActionResult Index(int? id, IListService service)`. It works because
`Global.asax.cs` sets `ModelBinders.Binders.DefaultBinder = new DiModelBinder()`, and
`DiModelBinder.CreateModel` returns `DependencyResolver.Current.GetService(modelType)` whenever the
parameter type is an **interface**.

ASP.NET Core has this built in: annotate the parameter with **`[FromServices]`**:

```csharp
public IActionResult Index(int? id, [FromServices] ICrudServices service)
```

Delete `Infrastructure/DiModelBinder.cs`. Note the concrete-type parameters
(`PostsController.NumPosts(SampleWebAppDb db)`, `Reset(SampleWebAppDb db)`) also need `[FromServices]`
— they were never handled by `DiModelBinder` (which only intercepts interfaces); in MVC 5 they were
model-bound as an empty new instance, which is another latent bug the port fixes.

### 8.2 Container

- `Autofac.Mvc5` (`AutofacDependencyResolver`, `DependencyResolver.SetResolver`) is MVC5-only — delete
  `Infrastructure/AutofacDi.cs` and the `Autofac.Integration.Mvc` usage in `WebUiInitialise.cs`.
- Two options:
  1. **Built-in `IServiceCollection`** — simplest. The Autofac modules do only
     `RegisterAssemblyTypes(asm).AsImplementedInterfaces()` plus one scoped `DbContext` registration,
     which is a handful of explicit `services.AddScoped<,>()` lines. `EfCore.GenericServices`
     registers itself into `IServiceCollection` anyway (`GenericServicesSimpleSetup<TContext>`).
  2. **Keep Autofac** via `Autofac.Extensions.DependencyInjection` 11.0.2 +
     `builder.Host.UseServiceProviderFactory(new AutofacServiceProviderFactory())` +
     `builder.Host.ConfigureContainer<ContainerBuilder>(b => b.RegisterModule(new ServiceLayerModule()))`.
     Autofac 9.x renamed nothing critical here; `Module`, `ContainerBuilder`,
     `RegisterAssemblyTypes(...).AsImplementedInterfaces()`, `InstancePerLifetimeScope()` all still exist.
- **Decision: option 1 (built-in container).** It removes `Autofac`, `Autofac.Mvc5` and the four
  startup module classes (`DataLayerModule`, `BizLayerModule`, `ServiceLayerModule`, `AutofacDi`)
  and matches how `EfCore.GenericServices` and `AddDbContext` expect to be wired.
  `Tests/UnitTests/Group03ServiceLayer/Test11AutoFacModules.cs` and `Test10DiSimple.cs` must be
  rewritten against `ServiceCollection`/`IServiceProvider` (or deleted if they only test Autofac itself).
- `SampleWebAppDb` was `InstancePerLifetimeScope` → `AddDbContext<SampleWebAppDb>(...)` is scoped by
  default; keep it scoped so all repositories share one context per request.

---

## 9. Old-style `.csproj` → SDK-style

For each of the five projects:

- Delete `packages.config`, the `<Reference Include="..."><HintPath>..\packages\...</HintPath></Reference>`
  blocks, the `<Compile Include="..."/>` file lists, `.nuget/`, `packages/`, `App.config`,
  and the `Microsoft.CSharp.targets`/`EnsureNuGetPackageBuildImports` boilerplate.
- `Properties/AssemblyInfo.cs`: SDK-style projects auto-generate assembly attributes →
  either delete the file or set `<GenerateAssemblyInfo>false</GenerateAssemblyInfo>`.
  **Deleting is preferred**, moving `Title`/`Company`/`Version` into MSBuild properties.
- **`InternalsVisibleTo("Tests")`** currently appears as a *file-level* attribute in **four** places:
  `DataLayer/DataClasses/SampleWebAppDb.cs:40`, `DataLayer/Startup/DataLayerModule.cs:552`,
  `ServiceLayer/**/*.cs` (several DTOs) and `SampleWebApp/Infrastructure/AutofacDi.cs`.
  Replace with a single MSBuild item per project:
  ```xml
  <ItemGroup>
    <InternalsVisibleTo Include="Tests" />
  </ItemGroup>
  ```
  (`InternalsVisibleTo` as an MSBuild item is supported by the .NET SDK.) Needed because
  `TrackUpdate.UpdateTrackingInfo()`, `SampleWebAppDb.NameOfConnectionString`, the internal
  `SampleWebAppDb(string)` ctor and `EfConfiguration.IsAzure` setter are `internal`.
- `SampleWebApp.csproj` must become `<Project Sdk="Microsoft.NET.Sdk.Web">`; the others
  `<Project Sdk="Microsoft.NET.Sdk">`; `Tests` also `Microsoft.NET.Sdk` with
  `<IsPackable>false</IsPackable>` and the test SDK packages.
- The web project drops all the `<Content Include="Views\...">` / `ProjectExtensions`/IIS-Express
  `<WebProjectProperties>` blocks; `.cshtml` under `Views/` is compiled by the Razor SDK automatically.
- `SampleWebApp.sln`: the project GUIDs/type GUIDs for the web project change
  (`{349c5851-65df-11da-9384-00065b846f21}` MVC project-type GUID is no longer used). Regenerate the
  solution entries or use `dotnet sln`.
- `.vs/config/applicationhost.config` (IIS Express) is dead — remove.
- `Project_Readme.html`, `Log4Net.xml`, `SampleWebApp.sln.DotSettings` — review/remove.

---

## 10. Miscellaneous

| Item | Note |
| --- | --- |
| `log4net` 2.0.3 + `Log4Net.xml` + `Log4NetGenericLogger`/`TraceGenericLogger` | replace with `Microsoft.Extensions.Logging` (`ILogger<T>`, console provider). `GenericLibsBase.GenericLibsBaseConfig.SetLoggerMethod` is gone with `GenericLibsBase` |
| `GenericLibsBase` 1.0.1 | EF6-era support library; no .NET Core successor. Its `ISuccessOrErrors` role is taken by `StatusGeneric` |
| `DelegateDecompiler` / `.EntityFramework` 0.18.0 | referenced in four `packages.config` files but **never used in code** (only mentioned in comments). Drop. (`DelegateDecompiler.EntityFrameworkCore` exists if `[Computed]` is wanted later) |
| `MarkdownSharp` 1.13 | referenced by `SampleWebApp` but unused in code. Drop |
| `Mono.Reflection` | transitive of `DelegateDecompiler`. Drop |
| `Newtonsoft.Json` 6.0.4 | only used by `JsonNetResult` (deleted) and `Tests/Helpers/JsonHelper`. Use `System.Text.Json`, or `Microsoft.AspNetCore.Mvc.NewtonsoftJson` if `JsonPatchDocument` overloads of `UpdateAndSave` are used |
| jQuery 1.10.2 / jQuery UI 1.10.4 / Bootstrap 3.3.2 | very old, but a like-for-like asset move keeps the UI identical. Upgrading the front end is explicitly **out of scope** |
| `TempData` | ASP.NET Core requires a TempData provider; the cookie provider is registered by `AddControllersWithViews()` by default, but values must be serialisable (see §1.1 `MvcHtmlString`) |
| `HostTypes` enum (`NotSet`/`LocalHost`/`WebWiz`/`Azure`) driven by `Settings.Default.HostTypeString`, rendered in `_Layout.cshtml` footer | move to `appsettings.json` (`"HostType": "LocalHost"`) bound with `IOptions<AppSettings>`; inject into the layout via `@inject` |
| Connection string `Data Source=(localdb)\mssqllocaldb;...;Integrated Security=SSPI` | LocalDB is Windows-only. For Linux/CI use SQL Server in Docker: `Server=localhost,1433;Database=SampleWebAppDb;User Id=sa;Password=...;TrustServerCertificate=True` |
| `Tests` NUnit 2.6.3 | `[TestFixtureSetUp]` → `[OneTimeSetUp]`; NUnit 4 also removed `Assert.That` string-overload shims and the classic `Assert.AreEqual` is deprecated. Test classes must be `public`. Needs `Microsoft.NET.Test.Sdk` + `NUnit3TestAdapter` to run under `dotnet test` |
| `Tests/Helpers/DummyIDbContextWithValidation.cs` | implements the EF6 `IGenericServicesDbContext` — rewrite or delete |

---

## 11. Phase 1 work split (subsessions)

- **A — `DataLayer` + `BizLayer` + `ServiceLayer`**: §2, §3.3/§3.4, §4, §8.2, §9.
- **B — `SampleWebApp`**: §1.1, §3.1/§3.2, §5, §6, §7, §8.1, §9.
- **C — `Tests`**: §1.2, §9, and the NUnit 4 / EF Core / `EfCore.GenericServices` test-setup changes.

Contract between A and B (agreed before A finalises):

```csharp
// ServiceLayer
public interface IPostDtoService                      // hand-written, owns Bloggers/Tags logic
{
    DetailPostDto GetDtoForCreate();
    DetailPostDto GetDtoForUpdate(int postId);
    IStatusGeneric Create(DetailPostDto dto);
    IStatusGeneric Update(DetailPostDto dto);
    void ResetSecondaryData(DetailPostDto dto);       // replaces IUpdateService.ResetDto
}
```
Everything else goes through `ICrudServices` / `ICrudServicesAsync` injected with `[FromServices]`.

---

## 12. Verification (Phase 2)

Runs in the VM on Linux: .NET 10 SDK 10.0.301, SQL Server 2022 in Docker, `dotnet ef database update`,
`dotnet run --project SampleWebApp`, then a recorded browser pass over the Blogs / Posts / Tags
list-details-create-edit-delete flows.

**Video proof:** see `docs/migration-verification.md` (link added at the end of Phase 2).
