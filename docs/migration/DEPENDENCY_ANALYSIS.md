# Dependency Analysis Document: .NET Framework to .NET Core Migration

## Overview

This document provides a comprehensive analysis of all .NET Framework-specific packages in the SampleMvcWebApp application and their .NET Core alternatives. The application is currently built on .NET Framework 4.5.1 and targets migration to .NET Core 6.0 or later.

## 1. Entity Framework Migration

### Current State
- **Package**: Entity Framework 6.1.3
- **Location**: `DataLayer/DataLayer.csproj` (lines 76-82)
- **Usage**: Database access layer with DbContext, entity configurations, and LINQ queries

### Target
- **Package**: Microsoft.EntityFrameworkCore 8.0.x (recommended for new projects) or 6.0.x (LTS)
- **Additional Packages Required**:
  - `Microsoft.EntityFrameworkCore.SqlServer` - SQL Server provider
  - `Microsoft.EntityFrameworkCore.Tools` - Migrations tooling
  - `Microsoft.EntityFrameworkCore.Design` - Design-time support

### Breaking Changes Between EF 6.1.3 and EF Core

#### DbContext Changes
| EF 6 Feature | EF Core Equivalent | Migration Notes |
|--------------|-------------------|-----------------|
| `Database.SetInitializer<T>()` | `Database.EnsureCreated()` or Migrations | EF Core uses migrations by default; `EnsureCreated()` for simple scenarios |
| `DbContext.Database.Initialize()` | `Database.Migrate()` | Explicit migration application |
| `DbEntityValidationException` | `DbUpdateException` | Validation handled differently in EF Core |
| `DbEntityEntry.GetValidationResult()` | Custom validation | Must implement `IValidatableObject` or use FluentValidation |
| `ObjectContext` | Not available | EF Core is DbContext-only |

#### Query Changes
| EF 6 Feature | EF Core Equivalent | Migration Notes |
|--------------|-------------------|-----------------|
| `DbSet<T>.Find(id)` | `DbSet<T>.Find(id)` | Same API, works identically |
| `DbSet<T>.FindAsync(id)` | `DbSet<T>.FindAsync(id)` | Same API, works identically |
| Lazy loading (default) | Explicit opt-in | Add `Microsoft.EntityFrameworkCore.Proxies` and configure |
| `Include()` with string | `Include()` with lambda only | String-based includes removed |
| `DbQuery<T>` | `DbSet<T>.HasNoKey()` | Keyless entity types |

#### Configuration Changes
| EF 6 Feature | EF Core Equivalent | Migration Notes |
|--------------|-------------------|-----------------|
| `DbConfiguration` class | `DbContextOptionsBuilder` | Configuration moved to startup |
| `SqlAzureExecutionStrategy` | `EnableRetryOnFailure()` | Built into SQL Server provider |
| `connectionStrings` in Web.config | `appsettings.json` | Configuration source change |
| Fluent API in `OnModelCreating` | Same, with syntax changes | Some methods renamed |

#### Specific Code Changes Required

**Current `SampleWebAppDb.cs` (EF 6):**
```csharp
public class SampleWebAppDb : DbContext, IGenericServicesDbContext
{
    public SampleWebAppDb() : base("name=" + NameOfConnectionString) {}
    
    protected override DbEntityValidationResult ValidateEntity(
        DbEntityEntry entityEntry, IDictionary<object, object> items)
    {
        // Custom validation logic
    }
}
```

**Target `SampleWebAppDb.cs` (EF Core):**
```csharp
public class SampleWebAppDb : DbContext
{
    public SampleWebAppDb(DbContextOptions<SampleWebAppDb> options) : base(options) {}
    
    public override int SaveChanges()
    {
        // Custom validation must be handled differently
        ValidateEntities();
        return base.SaveChanges();
    }
    
    private void ValidateEntities()
    {
        // Implement custom validation logic
    }
}
```

**Current `DataLayerInitialise.cs` (EF 6):**
```csharp
Database.SetInitializer(new CreateDatabaseIfNotExists<SampleWebAppDb>());
```

**Target (EF Core):**
```csharp
// In Program.cs or Startup.cs
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
    db.Database.EnsureCreated(); // or db.Database.Migrate();
}
```

**Current `EfConfiguration.cs` (EF 6):**
```csharp
public class EfConfiguration : DbConfiguration
{
    public EfConfiguration()
    {
        if (IsAzure)
            SetExecutionStrategy("System.Data.SqlClient", () => new SqlAzureExecutionStrategy());
    }
}
```

**Target (EF Core):**
```csharp
// In Program.cs
builder.Services.AddDbContext<SampleWebAppDb>(options =>
    options.UseSqlServer(connectionString, sqlOptions =>
        sqlOptions.EnableRetryOnFailure(
            maxRetryCount: 5,
            maxRetryDelay: TimeSpan.FromSeconds(30),
            errorNumbersToAdd: null)));
```

---

## 2. ASP.NET MVC Migration

### Current State
- **Package**: Microsoft.AspNet.Mvc 5.2.3
- **Location**: `SampleWebApp/SampleWebApp.csproj` (lines 150-152)
- **Usage**: Web application framework with controllers, views, and routing

### Target
- **Package**: Built into ASP.NET Core (Microsoft.AspNetCore.Mvc)
- **Framework**: ASP.NET Core 6.0+ (included in .NET 6 SDK)

### Breaking Changes Between ASP.NET MVC 5 and ASP.NET Core MVC

#### Controller Base Classes
| MVC 5 | ASP.NET Core | Migration Notes |
|-------|--------------|-----------------|
| `System.Web.Mvc.Controller` | `Microsoft.AspNetCore.Mvc.Controller` | Different namespace, similar API |
| `ActionResult` | `IActionResult` | Interface-based return type |
| `HttpStatusCodeResult` | `StatusCodeResult` | Name change |
| `JsonResult` | `JsonResult` | Same name, different implementation |
| `ViewResult` | `ViewResult` | Same name, different implementation |

#### Action Result Types
| MVC 5 | ASP.NET Core | Migration Notes |
|-------|--------------|-----------------|
| `new HttpStatusCodeResult(404)` | `NotFound()` | Helper methods available |
| `new JsonResult { Data = obj }` | `Json(obj)` | Simplified syntax |
| `new RedirectToRouteResult(...)` | `RedirectToAction(...)` | Same helper method |
| `Content(string)` | `Content(string)` | Same API |

#### Routing Configuration

**Current `RouteConfig.cs` (MVC 5):**
```csharp
public class RouteConfig
{
    public static void RegisterRoutes(RouteCollection routes)
    {
        routes.IgnoreRoute("{resource}.axd/{*pathInfo}");
        routes.MapRoute(
            name: "Default",
            url: "{controller}/{action}/{id}",
            defaults: new { controller = "Home", action = "Index", id = UrlParameter.Optional }
        );
    }
}
```

**Target (ASP.NET Core):**
```csharp
// In Program.cs
app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");
```

#### View Rendering

| MVC 5 Feature | ASP.NET Core Equivalent | Migration Notes |
|---------------|------------------------|-----------------|
| `@Html.ActionLink()` | `<a asp-action="">` or `@Html.ActionLink()` | Tag helpers preferred |
| `@Html.BeginForm()` | `<form asp-action="">` | Tag helpers preferred |
| `@Html.EditorFor()` | `<input asp-for="">` | Tag helpers preferred |
| `@Styles.Render()` | Link tags or bundling middleware | Different bundling approach |
| `@Scripts.Render()` | Script tags or bundling middleware | Different bundling approach |
| `Web.config` in Views | `_ViewImports.cshtml` | Tag helper imports |

#### Specific Controller Changes Required

**Current `PostsController.cs` (MVC 5):**
```csharp
using System.Web.Mvc;

public class PostsController : Controller
{
    public ActionResult Index(int? id, IListService service)
    {
        // Service injected via DiModelBinder
        return View(query.ToList());
    }
    
    [HttpPost]
    [ValidateAntiForgeryToken]
    public ActionResult Edit(DetailPostDto dto, IUpdateService service)
    {
        if (!ModelState.IsValid)
            return View(service.ResetDto(dto));
        // ...
    }
}
```

**Target (ASP.NET Core):**
```csharp
using Microsoft.AspNetCore.Mvc;

public class PostsController : Controller
{
    private readonly IListService _listService;
    private readonly IUpdateService _updateService;
    
    public PostsController(IListService listService, IUpdateService updateService)
    {
        _listService = listService;
        _updateService = updateService;
    }
    
    public IActionResult Index(int? id)
    {
        // Service injected via constructor
        return View(query.ToList());
    }
    
    [HttpPost]
    [ValidateAntiForgeryToken]
    public IActionResult Edit(DetailPostDto dto)
    {
        if (!ModelState.IsValid)
            return View(_updateService.ResetDto(dto));
        // ...
    }
}
```

#### Global.asax to Program.cs

**Current `Global.asax.cs` (MVC 5):**
```csharp
public class MvcApplication : System.Web.HttpApplication
{
    protected void Application_Start()
    {
        AreaRegistration.RegisterAllAreas();
        FilterConfig.RegisterGlobalFilters(GlobalFilters.Filters);
        RouteConfig.RegisterRoutes(RouteTable.Routes);
        BundleConfig.RegisterBundles(BundleTable.Bundles);
        ModelBinders.Binders.DefaultBinder = new DiModelBinder();
        WebUiInitialise.InitialiseThis(this);
    }
}
```

**Target `Program.cs` (ASP.NET Core):**
```csharp
var builder = WebApplication.CreateBuilder(args);

// Add services
builder.Services.AddControllersWithViews();
builder.Services.AddDbContext<SampleWebAppDb>(options => ...);
// Register other services

var app = builder.Build();

// Configure middleware
app.UseStaticFiles();
app.UseRouting();
app.UseAuthorization();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

app.Run();
```

---

## 3. Autofac Dependency Injection

### Current State
- **Package**: Autofac 3.5.0, Autofac.Mvc5 3.3.1
- **Location**: `SampleWebApp/SampleWebApp.csproj` (lines 43-49)
- **Usage**: Dependency injection container for all layers

### Target Options

#### Option A: Continue with Autofac (Recommended for minimal changes)
- **Package**: Autofac 8.x + Autofac.Extensions.DependencyInjection
- **Rationale**: Preserves existing module structure, minimal code changes

#### Option B: Migrate to Built-in .NET Core DI
- **Package**: Microsoft.Extensions.DependencyInjection (built-in)
- **Rationale**: Simpler, no external dependency, sufficient for most scenarios

### Recommendation: Option A (Autofac)

The application uses Autofac modules extensively (`ServiceLayerModule`, `DataLayerModule`), and Autofac provides features not available in built-in DI (property injection, modules, advanced lifetime scopes). Continuing with Autofac minimizes migration risk.

### Breaking Changes Between Autofac 3.5.0 and Autofac 8.x

| Autofac 3.5 Feature | Autofac 8.x Equivalent | Migration Notes |
|---------------------|----------------------|-----------------|
| `ContainerBuilder.Build()` | Same API | No change |
| `builder.RegisterModule<T>()` | Same API | No change |
| `AutofacDependencyResolver` | `AutofacServiceProviderFactory` | Integration approach changed |
| `DependencyResolver.SetResolver()` | `builder.Host.UseServiceProviderFactory()` | ASP.NET Core integration |
| `InstancePerHttpRequest` | `InstancePerLifetimeScope` | Scope naming changed |

### Configuration Changes Required

**Current `AutofacDi.cs` (Autofac 3.5):**
```csharp
internal static IContainer SetupDependency()
{
    var builder = new ContainerBuilder();
    builder.RegisterModule(new ServiceLayerModule());
    _container = builder.Build();
    return _container;
}
```

**Current `WebUiInitialise.cs` (MVC 5):**
```csharp
var container = AutofacDi.SetupDependency();
var mvcResolver = new AutofacDependencyResolver(container);
DependencyResolver.SetResolver(mvcResolver);
```

**Target `Program.cs` (ASP.NET Core with Autofac):**
```csharp
var builder = WebApplication.CreateBuilder(args);

// Use Autofac as the service provider factory
builder.Host.UseServiceProviderFactory(new AutofacServiceProviderFactory());

builder.Host.ConfigureContainer<ContainerBuilder>(containerBuilder =>
{
    containerBuilder.RegisterModule(new ServiceLayerModule());
});

builder.Services.AddControllersWithViews();
// ... other services

var app = builder.Build();
```

### DiModelBinder Replacement

The current `DiModelBinder` allows services to be injected as action parameters. In ASP.NET Core, this pattern is replaced by:

1. **Constructor Injection** (preferred): Services injected via controller constructor
2. **`[FromServices]` Attribute**: Services injected as action parameters

**Current Pattern (MVC 5):**
```csharp
public ActionResult Index(int? id, IListService service)
{
    // service injected via DiModelBinder
}
```

**Target Pattern (ASP.NET Core):**
```csharp
// Option 1: Constructor injection
private readonly IListService _service;
public PostsController(IListService service) => _service = service;

public IActionResult Index(int? id)
{
    // use _service
}

// Option 2: FromServices attribute
public IActionResult Index(int? id, [FromServices] IListService service)
{
    // service injected via attribute
}
```

---

## 4. System.Web Dependencies

### Current State
System.Web is used throughout the presentation layer for HTTP abstractions, MVC components, and web infrastructure.

### Files with System.Web Dependencies

| File | System.Web Usages | ASP.NET Core Equivalent |
|------|-------------------|------------------------|
| `Global.asax.cs` | `HttpApplication`, `System.Web.Mvc`, `System.Web.Optimization`, `System.Web.Routing` | `Program.cs` with middleware |
| `WebUiInitialise.cs` | `System.Web.HttpApplication`, `System.Web.Mvc.DependencyResolver` | ASP.NET Core DI |
| `DiModelBinder.cs` | `System.Web.Mvc.DefaultModelBinder`, `ControllerContext`, `ModelBindingContext` | Constructor injection or `[FromServices]` |
| `RouteConfig.cs` | `System.Web.Routing.RouteCollection` | Endpoint routing |
| `BundleConfig.cs` | `System.Web.Optimization.BundleCollection` | WebOptimizer or manual bundling |
| `FilterConfig.cs` | `System.Web.Mvc.GlobalFilterCollection` | `builder.Services.AddControllersWithViews(options => ...)` |
| `ValidationHelper.cs` | `System.Web.Mvc.ModelStateDictionary`, `System.Web.Mvc.JsonResult` | Same names in `Microsoft.AspNetCore.Mvc` |
| All Controllers | `System.Web.Mvc.Controller`, `ActionResult` | `Microsoft.AspNetCore.Mvc.Controller`, `IActionResult` |

### Detailed Migration for Each System.Web Usage

#### HttpApplication (Global.asax)
- **Current**: `MvcApplication : System.Web.HttpApplication`
- **Target**: Remove entirely; use `Program.cs` with minimal hosting model
- **Action**: Delete `Global.asax` and `Global.asax.cs`; move initialization to `Program.cs`

#### HttpContext
- **Current**: `System.Web.HttpContext.Current`
- **Target**: `IHttpContextAccessor` injected via DI
- **Action**: Inject `IHttpContextAccessor` where needed; avoid static access

#### Server.MapPath
- **Current**: `application.Server.MapPath(path)`
- **Target**: `IWebHostEnvironment.ContentRootPath` or `WebRootPath`
- **Action**: Inject `IWebHostEnvironment` and use `Path.Combine()`

#### ModelStateDictionary
- **Current**: `System.Web.Mvc.ModelStateDictionary`
- **Target**: `Microsoft.AspNetCore.Mvc.ModelBinding.ModelStateDictionary`
- **Action**: Update namespace; API is similar

#### JsonResult
- **Current**: `new JsonResult { Data = obj }`
- **Target**: `Json(obj)` helper method or `new JsonResult(obj)`
- **Action**: Use controller helper methods

---

## 5. Additional Dependencies Analysis

### 5.1 AutoMapper

#### Current State
- **Package**: AutoMapper 4.2.1
- **Location**: All projects (`packages.config`)
- **Usage**: Entity-to-DTO mapping in GenericServices

#### Target
- **Package**: AutoMapper 13.x
- **Compatibility**: Fully compatible with .NET Core

#### Breaking Changes
| AutoMapper 4.x | AutoMapper 13.x | Migration Notes |
|----------------|-----------------|-----------------|
| `Mapper.CreateMap<S,D>()` | Profile-based configuration | Static API removed |
| `Mapper.Map<D>(source)` | `IMapper.Map<D>(source)` | Instance-based mapping |
| Static configuration | `AddAutoMapper(assemblies)` | DI integration |

#### Migration Steps
1. Create mapping profiles inheriting from `Profile`
2. Register AutoMapper in DI: `builder.Services.AddAutoMapper(typeof(Program).Assembly)`
3. Inject `IMapper` where needed
4. Remove static `Mapper` calls

### 5.2 ASP.NET Identity

#### Current State
- **Package**: Microsoft.AspNet.Identity.Core 2.1.0, Microsoft.AspNet.Identity.EntityFramework 2.1.0, Microsoft.AspNet.Identity.Owin 2.1.0
- **Location**: `SampleWebApp/packages.config` (lines 19-21)
- **Usage**: Referenced but not actively used in current codebase

#### Target
- **Package**: Microsoft.AspNetCore.Identity.EntityFrameworkCore
- **Compatibility**: Complete rewrite required if Identity is used

#### Breaking Changes
| ASP.NET Identity 2.x | ASP.NET Core Identity | Migration Notes |
|---------------------|----------------------|-----------------|
| `UserManager<TUser>` | `UserManager<TUser>` | Similar API, different namespace |
| `SignInManager` | `SignInManager<TUser>` | Generic type parameter added |
| OWIN middleware | ASP.NET Core middleware | Complete configuration change |
| `IdentityDbContext` | `IdentityDbContext<TUser>` | Similar but different namespace |

#### Recommendation
Since Identity appears to be referenced but not actively used (authentication is disabled in Web.config), consider removing these packages during migration unless authentication is planned.

### 5.3 SignalR

#### Current State
- **Package**: Microsoft.AspNet.SignalR 2.0.3
- **Location**: `SampleWebApp/packages.config` (lines 24-27)
- **Usage**: Referenced but appears unused in current codebase (ActionRunner scripts commented out)

#### Target
- **Package**: Microsoft.AspNetCore.SignalR (built into ASP.NET Core)
- **Compatibility**: Complete rewrite required

#### Breaking Changes
| SignalR 2.x | ASP.NET Core SignalR | Migration Notes |
|-------------|---------------------|-----------------|
| `Hub` base class | `Hub` base class | Different namespace |
| `$.connection.hub` | `HubConnectionBuilder` | JavaScript client rewrite |
| OWIN startup | Endpoint routing | `app.MapHub<T>("/path")` |
| `GlobalHost.ConnectionManager` | `IHubContext<T>` via DI | Dependency injection |

#### Recommendation
Since SignalR appears unused (ActionRunner functionality commented out in BundleConfig.cs), consider removing these packages during migration unless real-time features are planned.

### 5.4 GenericServices

#### Current State
- **Package**: GenericServices 1.0.9, GenericLibsBase 1.0.1
- **Location**: All projects
- **Usage**: Core service layer abstraction for CRUD operations

#### Target
- **Replacement**: Custom services + MediatR pattern (as per migration plan)
- **Rationale**: GenericServices is not maintained for .NET Core; MediatR provides similar patterns

#### Components to Replace

| GenericServices Component | Replacement Strategy |
|--------------------------|---------------------|
| `IListService` | Custom query handlers with MediatR |
| `IDetailService` / `IDetailServiceAsync` | MediatR query handlers |
| `ICreateService` / `ICreateServiceAsync` | MediatR command handlers |
| `IUpdateService` / `IUpdateServiceAsync` | MediatR command handlers |
| `IDeleteService` / `IDeleteServiceAsync` | MediatR command handlers |
| `EfGenericDto<TEntity, TDto>` | AutoMapper profiles + custom DTOs |
| `EfGenericDtoAsync<TEntity, TDto>` | AutoMapper profiles + custom DTOs |
| `IGenericServicesDbContext` | Standard `DbContext` |
| `ISuccessOrErrors` | `Result<T>` pattern or FluentResults |

#### Migration Approach
1. Create MediatR request/response classes for each operation
2. Implement handlers that replace GenericServices functionality
3. Replace DTO base classes with plain DTOs + AutoMapper profiles
4. Implement result pattern for success/error handling

### 5.5 Other Dependencies

#### log4net 2.0.3
- **Target**: Microsoft.Extensions.Logging + Serilog (or keep log4net)
- **Compatibility**: log4net works with .NET Core
- **Recommendation**: Migrate to `ILogger<T>` for consistency with ASP.NET Core

#### Newtonsoft.Json 6.0.4
- **Target**: System.Text.Json (built-in) or Newtonsoft.Json 13.x
- **Compatibility**: Both work with .NET Core
- **Recommendation**: Use System.Text.Json unless specific Newtonsoft features needed

#### DelegateDecompiler 0.18.0
- **Target**: DelegateDecompiler.EntityFramework.Core
- **Compatibility**: .NET Core version available
- **Note**: May not be needed if computed properties are refactored

---

## Summary: Recommended Migration Approach

### Phase 1: Infrastructure (Low Risk)
1. Migrate to .NET 6/8 SDK project format
2. Update Autofac to latest version
3. Update AutoMapper to latest version
4. Update Newtonsoft.Json or migrate to System.Text.Json

### Phase 2: Data Layer (Medium Risk)
1. Migrate Entity Framework 6 to EF Core
2. Update DbContext configuration
3. Migrate connection string to appsettings.json
4. Update database initialization logic

### Phase 3: Service Layer (High Risk)
1. Replace GenericServices with custom services + MediatR
2. Update DTOs to remove GenericServices base classes
3. Implement result pattern for error handling
4. Update AutoMapper configurations

### Phase 4: Presentation Layer (Medium Risk)
1. Migrate controllers from MVC 5 to ASP.NET Core MVC
2. Replace Global.asax with Program.cs
3. Update routing configuration
4. Migrate views (minimal changes with Razor)
5. Update bundling/minification approach

### Phase 5: Cleanup (Low Risk)
1. Remove unused packages (Identity, SignalR if not needed)
2. Update logging to ILogger
3. Remove System.Web references
4. Final testing and validation
