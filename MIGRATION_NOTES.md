# SampleMvcWebApp → ASP.NET Core MVC / .NET 10 migration notes

## 1. Target stack

| Area | Legacy (master) | Target |
|---|---|---|
| Runtime | .NET Framework 4.5.1, IIS/System.Web | `net10.0`, Kestrel |
| Web | ASP.NET MVC 5, Global.asax, App_Start, Web.config | ASP.NET Core MVC, minimal hosting `Program.cs`, `appsettings*.json` |
| Data | EF6 6.1, `DbConfiguration`, `CreateDatabaseIfNotExists` | EF Core 10 (SqlServer), migrations (`InitialCreate`) |
| CRUD library | GenericServices 1.0.9 + GenericLibsBase | EfCore.GenericServices 10.0.0 + `StatusGeneric` |
| Mapping | AutoMapper 4.2 (static) | AutoMapper **14.0.0** (pinned, used through EfCore.GenericServices) |
| DI | Autofac modules + `DiModelBinder` | built-in `IServiceCollection` (`AddServiceLayer`) + `[FromServices]` |
| Logging | log4net / GenericLibsBase logger | `Microsoft.Extensions.Logging` |
| Bundling | System.Web.Optimization | static files in `wwwroot` (prebuilt `.min` files, `asp-append-version`) |
| Tests | NUnit 2.6.3, Moq 4.2, EF6 | NUnit 4, current Moq, EF Core on SQL Server |

## 2. Legacy baseline (verified on Windows, not re-run)
- Builds with 0 errors; NUnit 2.6.3: 54/54 pass; all UI flows pass.
- DB starts empty; `/Posts/Reset` seeds 4 blogs / 17 posts / 8 tags (2 of the XML's 10 tags are unreferenced — expected).
- No Blog Details page exists. Post Create's Bloggers/Tags labels have a pre-existing a11y warning.

## 3. Affected files (grep: System.Web, HttpApplication, Global.asax, AreaRegistration, RouteTable, BundleTable, ModelBinders, MvcHtmlString)
- Web host: `Global.asax(.cs)`, `App_Start/{BundleConfig,FilterConfig,RouteConfig}.cs`, `Web*.config`, `Views/Web.config`, `Properties/Settings.*`, `Log4Net.xml`, `Project_Readme.html`.
- Web infra: `Infrastructure/{AutofacDi,DiModelBinder,JsonNetResult,Log4NetGenericLogger,TraceGenericLogger,ValidationHelper,WebUiInitialise}.cs`.
- Controllers: all 6 (`System.Web.Mvc`, DI-bound action params, `MvcHtmlString`).
- Views: `_Layout` (`@Styles/@Scripts.Render`), Create/Edit views (`@Html.Partial`, `@Scripts.Render("~/bundles/jqueryval")`), `Shared/Error.cshtml`, `Posts/CodeView.cshtml`.
- `Models/InternalsInfo.cs` uses Windows-only `PerformanceCounter`.
- DataLayer: `SampleWebAppDb`, `EfConfiguration`, `Startup/*`. ServiceLayer: all DTOs, `Startup/*`. BizLayer: `Startup/*` (BizLayer is not in the legacy .sln).
- Tests: `Helpers/{JsonHelper,ModelStateTester,DummyIDbContextWithValidation,SimpleTagDto*,DbSnapShot}.cs`, all `UnitTests/*`.
- Every project: `packages.config`, `App.config`, `Properties/AssemblyInfo.cs`, HintPath references; root `packages/`.

## 4. Scope decisions (grep-verified)
- **SignalR**: no hub exists; `jquery.signalR-2.0.3*.js` and `ActionRunner*.js` are only referenced from a commented-out bundle → delete the scripts, nothing to port.
- **Identity / OWIN / external logins**: no `Startup.Auth.cs`, `IdentityModels` or `ApplicationUser` exist; packages are referenced only by `Tests/packages.config`/`App.config`; `_LoginPartial` is commented out → remove, nothing to port.
- **DelegateDecompiler**: package referenced, no `.Decompile()`/`[Computed]` usage → drop.
- **JsonNetResult**: only referenced in comments → delete; `ValidationHelper` returns `JsonResult`.
- **Publish profiles**: none in repo.
- **Seeding**: the app applies migrations and seeds the Medium XML data on startup when the DB has no blogs and no tags (legacy needed a manual Reset).

## 5. DataLayer (EF6 → EF Core 10)
- Options-based ctor; `OnModelCreating`: unique index on `Tag.Slug`, `Post`↔`Tag` many-to-many, `Post.Blogger` → `BlogId` FK.
- `ValidateEntity` removed; slug uniqueness moves to the `SaveChanges`/`SaveChangesAsync` overrides (see §11).
- `HandleChangeTracking`/`TrackUpdate` runs in the `SaveChanges` overrides after `ChangeTracker.DetectChanges()`.
- `EfConfiguration` (Azure execution strategy) → `UseSqlServer(cs, o => { if (isAzure) o.EnableRetryOnFailure(); })`.
- `ResetBlogs` runs inside `CreateExecutionStrategy().Execute(() => { using var tx = BeginTransaction(); ... })`.

## 6. ServiceLayer / BizLayer (GenericServices 1.x → EfCore.GenericServices 10)
- `EfGenericDto<TEntity,TDto>` → `ILinkToEntity<TEntity>`; `[DoNotCopyBackToDatabase]` → `[ReadOnly(true)]`; `ISuccessOrErrors` → `IStatusGeneric`; `CrudFunctions` → gone (DTO capabilities are implicit).
- `SetupSecondaryData`/`CreateDataFromDto`/`UpdateDataFromDto` have no equivalent → moved to `PostService`/`PostServiceAsync` (§11).
- `DropDownListType`, `MultiSelectListType` are not in the new library → kept as-is in `ServiceLayer/UiClasses`.
- Autofac modules → `AddServiceLayer` (ServiceLayer) and `AddBizLayer` (BizLayer, currently empty).
- AutoMapper is configured by EfCore.GenericServices (`GenericServicesSimpleSetup`); `PerDtoConfig<TDto,TEntity>` for custom maps (e.g. `PostsCount`).

## 7. SampleWebApp
- `Program.cs`: `AddControllersWithViews`, `AddServiceLayer(cs, isAzure)`, `UseExceptionHandler("/Home/Error")` + `UseHsts()` + `UseHttpsRedirection()` outside Development, static files, default route `{controller=Home}/{action=Index}/{id?}`, `ServiceLayerInitialise.InitialiseThis(app.Services, canCreateDatabase)`, `public partial class Program {}`.
- `appsettings.json` ships an empty `ConnectionStrings:SampleWebAppDb`; local value in `appsettings.Development.json`.
- Controllers use `[FromServices]` for `ICrudServices`/`ICrudServicesAsync`/`IPostService`/`IPostServiceAsync`/`SampleWebAppDb`.
- `Content/`, `Scripts/`, `fonts/`, `favicon.ico` → `wwwroot/`; `Views/Web.config` → `_ViewImports.cshtml`; `@Html.Partial` → `<partial>`.

## 8. Tests
- `ConnectionStrings__SampleWebAppDbTest` env var, default DB `SampleWebAppDb-Test` on the local container. Never the app's `ConnectionStrings__SampleWebAppDb` (Reset would wipe the app DB).
- Autofac module tests → `IServiceCollection`/`AddServiceLayer` resolution tests. `System.Web.Mvc` helpers (`ModelStateTester`, `JsonHelper`) → ASP.NET Core `ModelStateDictionary`/`JsonResult`.
- `[ExpectedException]` → `Assert.Throws`; classic asserts → `Assert.That` / `ClassicAssert`.

## 9. Removed legacy artifacts
`Global.asax*`, `App_Start/`, `Web*.config`, `Views/Web.config`, `packages.config`, `App.config`, `Properties/AssemblyInfo.cs`, `Properties/Settings.*`, `Log4Net.xml`, root `packages/`, `.vs/`, Autofac/DiModelBinder/log4net/JsonNetResult infra, SignalR/ActionRunner scripts.

## 10. Environment
- SQL Server: `docker start mssql 2>/dev/null || docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=Str0ng!Passw0rd' -e MSSQL_PID=Developer -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest`.
- The `sa` password lives only in `appsettings.Development.json` (and the test default connection string).
- An EF6-created database cannot be upgraded by `InitialCreate` (EF6 `TagPosts` join table, no `__EFMigrationsHistory`) → use a new database.
- `dotnet-ef` is a local tool (`.config/dotnet-tools.json`): `dotnet tool run dotnet-ef ...`.

## 11. BINDING cross-subsession API contract
Deviations must be minimal and listed in the PR description.

### DataLayer
```csharp
namespace DataLayer.DataClasses
public class SampleWebAppDb : DbContext
{
    public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options);
    public DbSet<Blog> Blogs { get; set; }
    public DbSet<Post> Posts { get; set; }
    public DbSet<Tag> Tags { get; set; }
    // SaveChanges()/SaveChanges(bool)/SaveChangesAsync(...) overrides:
    //  1. ChangeTracker.DetectChanges(); 2. TrackUpdate.UpdateTrackingInfo() on Added/Modified;
    //  3. Added/Modified Tag whose Slug is used by another tag -> throw SlugNotUniqueException.
}
public class SlugNotUniqueException : DbUpdateException   // Message:
    // "The Slug on tag '{tag.Name}' must be unique and is already being used."
    public string MemberName => "Slug";
// Entities Blog, Post (TrackUpdate, IValidatableObject), Tag keep their public shape and data annotations.

namespace DataLayer.Startup
public enum TestDataSelection { Small = 0, Medium = 1 }
public static class DataLayerInitialise
{
    public static void InitialiseThis(SampleWebAppDb db, bool canCreateDatabase); // Migrate() if allowed; seed Medium if no blogs and no tags
    public static void ResetBlogs(SampleWebAppDb db, TestDataSelection selection); // transactional, see §5
}
```

### ServiceLayer
```csharp
namespace ServiceLayer.Startup
public static class ServiceLayerSetup
{
    // AddDbContext<SampleWebAppDb>(UseSqlServer, EnableRetryOnFailure if isAzure),
    // GenericServicesSimpleSetup<SampleWebAppDb>(new GenericServicesConfig { DtoAccessValidateOnSave = true,
    //   DirectAccessValidateOnSave = true, SaveChangesExceptionHandler = maps SlugNotUniqueException to an error on "Slug" },
    //   typeof(DetailPostDto).Assembly), IPostService/IPostServiceAsync scoped.
    public static IServiceCollection AddServiceLayer(this IServiceCollection services, string connectionString, bool isAzure);
}
public static class ServiceLayerInitialise
{
    public static void InitialiseThis(IServiceProvider services, bool canCreateDatabase); // creates a scope, calls DataLayerInitialise.InitialiseThis
}

// DTOs (namespaces unchanged), all ILinkToEntity<TEntity>:
ServiceLayer.BlogServices.BlogListDto      : Blog  { int BlogId; string Name; string EmailAddress; int PostsCount; }
ServiceLayer.TagServices.TagListDto        : Tag   { int TagId; string Slug; string Name; int PostsCount; }
ServiceLayer.PostServices.SimplePostDto    : Post  { int PostId; int BlogId; string BloggerName; string Title;
                                                     ICollection<Tag> Tags; DateTime LastUpdated; DateTime LastUpdatedUtc; string TagNames; }
ServiceLayer.PostServices.SimplePostDtoAsync : same shape as SimplePostDto
ServiceLayer.PostServices.DetailPostDto    : Post  { int PostId; string Title; string Content; string BloggerName;
                                                     [ReadOnly(true)] DateTime LastUpdated; int BlogId; ICollection<Tag> Tags;
                                                     DropDownListType Bloggers; MultiSelectListType UserChosenTags;
                                                     DateTime LastUpdatedUtc; string TagNames /* (Tags ?? []).Select(...) */ }
ServiceLayer.PostServices.DetailPostDtoAsync : same shape as DetailPostDto
// UiClasses DropDownListType / MultiSelectListType: unchanged public API.

namespace ServiceLayer.PostServices
public interface IPostService
{
    DetailPostDto GetDetail(int postId);          // null if not found; lists not populated
    DetailPostDto GetForEdit(int postId);         // null if not found; Bloggers + UserChosenTags populated
    DetailPostDto GetNewForCreate();              // Bloggers + UserChosenTags populated
    DetailPostDto ResetDto(DetailPostDto dto);    // repopulates lists after a model error, keeps user input
    IStatusGeneric Create(DetailPostDto dto);     // errors on "Bloggers"/"UserChosenTags"/entity validation; Message on success
    IStatusGeneric Update(DetailPostDto dto);
}
public interface IPostServiceAsync              // same members on DetailPostDtoAsync, Async suffix, Task<...>
{
    Task<DetailPostDtoAsync> GetDetailAsync(int postId);
    Task<DetailPostDtoAsync> GetForEditAsync(int postId);
    Task<DetailPostDtoAsync> GetNewForCreateAsync();
    Task<DetailPostDtoAsync> ResetDtoAsync(DetailPostDtoAsync dto);
    Task<IStatusGeneric> CreateAsync(DetailPostDtoAsync dto);
    Task<IStatusGeneric> UpdateAsync(DetailPostDtoAsync dto);
}
// Blogs/Tags/Post lists/Post delete use ICrudServices / ICrudServicesAsync directly
// (ReadManyNoTracked<TDto>(), ReadSingle<T>(id), CreateAndSave, UpdateAndSave, DeleteAndSave<T>(id)).
```

### BizLayer
```csharp
namespace BizLayer.Startup
public static class BizLayerSetup { public static IServiceCollection AddBizLayer(this IServiceCollection services); } // no registrations yet
```

### SampleWebApp
```csharp
namespace SampleWebApp.Infrastructure
public static class ValidationHelper
{
    public static void CopyErrorsToModelState<T>(this IStatusGeneric status, ModelStateDictionary modelState, T displayDto);
    public static void CopyErrorsToModelState(this IStatusGeneric status, ModelStateDictionary modelState);
    public static JsonResult ReturnModelErrorsAsJson(this ModelStateDictionary modelState);   // Value = new { errorsDict = dict }
    public static JsonResult ReturnErrorsAsJson<T>(this IStatusGeneric status, T displayDto);
    public static string ErrorsAsHtml(this IStatusGeneric status);
}
// Program: public partial class Program {}  (for WebApplicationFactory)
```
Tests reference DataLayer, ServiceLayer, SampleWebApp.

## 12. Gotchas (verified in earlier runs)
- Pin AutoMapper 14.0.0: 15.x/16.x throw `MethodAccessException` inside EfCore.GenericServices. `NU1903` restore warning is accepted.
- Namespaces: `GenericServices.Configuration` (GenericServicesConfig), `GenericServices.Setup` (GenericServicesSimpleSetup), `StatusGeneric` (IStatusGeneric).
- EfCore.GenericServices may save with `AutoDetectChangesEnabled` off: the `SaveChanges` override must call `ChangeTracker.DetectChanges()` before touching `TrackUpdate`, or the change is lost.
- EF Core 8+ scalar raw SQL needs the column aliased `Value`: `SqlQueryRaw<int>("SELECT COUNT(*) AS [Value] ...")`.
- `@Html.Label("Tags", htmlAttributes: ...)` does not compile in Core.
- `EditorFor(m => m.Title)` emits no `data-val-*` because of the `ViewBag.Title` collision → use `<input asp-for="Title">`.
- Core's validation visitor reads computed getters: `TagNames => Tags.Select(...)` 500s on POST → `(Tags ?? []).Select(...)`.
- A failed `CreateAndSave`/`UpdateAndSave` leaves the entity tracked → `ChangeTracker.Clear()` before redisplay/retry.
- Success messages use EfCore.GenericServices wording (expected difference from legacy).
- Do not `pkill -f SampleWebApp` (kills the calling shell); kill the run shell by id.
- `ChangeTracker.DetectChanges()` is also needed AFTER `TrackUpdate.UpdateTrackingInfo()` in `HandleChangeTracking`, otherwise `LastUpdated` is not saved on updates (EfCore.GenericServices saves with auto-detect off).
- `ICrudServices`/`ICrudServicesAsync` are scoped and their status accumulates: a second call in the same scope after a failed one still reports the old errors. `PostService(Async)` saves via `SaveChangesWithValidation(Async)(IGenericServicesConfig)` instead.
- `GenericServicesSimpleSetup` does not register `IGenericServicesConfig`; `AddServiceLayer` registers the config instance as a singleton.
- `dotnet new tool-manifest` on the .NET 10 SDK writes `dotnet-tools.json` to the repo root, not `.config/`; move it.
- EF Core SqlServer 10.0.0 pulls `System.Security.Cryptography.Pkcs`/`.Xml` 9.0.x (NU1902/NU1903); DataLayer pins both to 10.0.15.

## 13. Verification evidence
_To be filled after integration._
