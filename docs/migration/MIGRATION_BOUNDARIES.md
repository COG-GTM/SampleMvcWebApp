# Migration Boundaries Document: .NET Framework to .NET Core Migration

## Overview

This document provides a detailed breakdown of each layer in the SampleMvcWebApp application, identifying migration boundaries and listing all files that will require modification during the .NET Framework to .NET Core migration.

## Architecture Overview

The application follows a classic 3-tier layered architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                    SampleWebApp                              │
│              (Presentation Layer - ASP.NET MVC)              │
│  Controllers, Views, Infrastructure, Configuration           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     ServiceLayer                             │
│              (Business Logic / Service Layer)                │
│  DTOs, Service Interfaces, AutoMapper Configurations         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      DataLayer                               │
│              (Data Access Layer - Entity Framework)          │
│  Entities, DbContext, Database Configurations                │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. DataLayer Analysis

### Location
`DataLayer/DataLayer.csproj`

### Purpose
The DataLayer provides data access functionality using Entity Framework 6. It contains entity definitions, the DbContext, database initialization logic, and Autofac module registration.

### Key Components

#### 1.1 Entity Classes

| File | Description | Migration Impact |
|------|-------------|------------------|
| `DataClasses/Concrete/Blog.cs` | Blog entity with Name, EmailAddress, Posts collection | **Low** - Data annotations compatible |
| `DataClasses/Concrete/Post.cs` | Post entity with Title, Content, Tags, implements IValidatableObject | **Medium** - Validation logic needs review |
| `DataClasses/Concrete/Tag.cs` | Tag entity with Slug, Name, Posts collection | **Low** - Data annotations compatible |
| `DataClasses/Concrete/Helpers/TrackUpdate.cs` | Base class for tracking LastUpdated | **Low** - No EF-specific code |

#### Entity Relationships
- **Blog** (1) → (Many) **Post**: One-to-many via `BlogId` foreign key
- **Post** (Many) ↔ (Many) **Tag**: Many-to-many via junction table (EF convention)

#### Entity Class Changes Required

**`Post.cs` - IValidatableObject Implementation:**
```csharp
// Current (EF 6) - Works in EF Core
public class Post : TrackUpdate, IValidatableObject
{
    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        // Custom validation logic - compatible with EF Core
    }
}
```
**Migration Notes**: The `IValidatableObject` interface works in EF Core, but validation is not automatically called. Must be invoked manually or via `SaveChanges` override.

#### 1.2 DbContext

| File | Description | Migration Impact |
|------|-------------|------------------|
| `DataClasses/SampleWebAppDb.cs` | Main DbContext with DbSets, SaveChanges override, ValidateEntity override | **High** - Significant changes required |

**Current Implementation Analysis:**

```csharp
public class SampleWebAppDb : DbContext, IGenericServicesDbContext
{
    // Connection string from Web.config
    public SampleWebAppDb() : base("name=" + NameOfConnectionString) {}
    
    public DbSet<Blog> Blogs { get; set; }
    public DbSet<Post> Posts { get; set; }
    public DbSet<Tag> Tags { get; set; }
    
    // Override for change tracking
    public override int SaveChanges()
    {
        HandleChangeTracking();
        return base.SaveChanges();
    }
    
    // Override for async
    public override Task<int> SaveChangesAsync()
    {
        HandleChangeTracking();
        return base.SaveChangesAsync();
    }
    
    // EF 6 specific - ValidateEntity override
    protected override DbEntityValidationResult ValidateEntity(
        DbEntityEntry entityEntry, IDictionary<object, object> items)
    {
        // Custom validation for Tag uniqueness
    }
}
```

**Required Changes for EF Core:**
1. Remove `IGenericServicesDbContext` interface (GenericServices replacement)
2. Change constructor to accept `DbContextOptions<SampleWebAppDb>`
3. Remove `ValidateEntity` override (not available in EF Core)
4. Implement custom validation in `SaveChanges`/`SaveChangesAsync`
5. Update `HandleChangeTracking` to use EF Core change tracker API

#### 1.3 Configuration

| File | Description | Migration Impact |
|------|-------------|------------------|
| `DataClasses/EfConfiguration.cs` | DbConfiguration for Azure retry strategy | **High** - Complete replacement |

**Current Implementation:**
```csharp
public class EfConfiguration : DbConfiguration
{
    public static bool IsAzure { get; internal set; }
    
    public EfConfiguration()
    {
        if (IsAzure)
            SetExecutionStrategy("System.Data.SqlClient", 
                () => new SqlAzureExecutionStrategy());
    }
}
```

**Required Changes**: Delete this file; configure retry in `Program.cs`:
```csharp
options.UseSqlServer(connectionString, sqlOptions =>
    sqlOptions.EnableRetryOnFailure());
```

#### 1.4 Startup/Initialization

| File | Description | Migration Impact |
|------|-------------|------------------|
| `Startup/DataLayerInitialise.cs` | Database initialization, seeding | **High** - Database.SetInitializer replacement |
| `Startup/DataLayerModule.cs` | Autofac module for DI registration | **Medium** - Update for EF Core DI |
| `Startup/Internal/LoadDbDataFromXml.cs` | XML data loading for seeding | **Low** - No EF-specific code |

**DataLayerInitialise.cs Changes:**
```csharp
// Current (EF 6)
Database.SetInitializer(new CreateDatabaseIfNotExists<SampleWebAppDb>());

// Target (EF Core) - Move to Program.cs
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
    db.Database.EnsureCreated();
}
```

**DataLayerModule.cs Changes:**
```csharp
// Current (Autofac 3.5)
builder.RegisterType<SampleWebAppDb>()
    .As<SampleWebAppDb>()
    .As<IGenericServicesDbContext>()
    .InstancePerLifetimeScope();

// Target (Autofac 8.x with EF Core)
builder.RegisterType<SampleWebAppDb>()
    .AsSelf()
    .InstancePerLifetimeScope();
// Note: DbContext registration typically done via AddDbContext in ASP.NET Core
```

#### 1.5 Project File

| File | Description | Migration Impact |
|------|-------------|------------------|
| `DataLayer.csproj` | Project file (old format) | **High** - Convert to SDK-style |
| `packages.config` | NuGet packages | **High** - Convert to PackageReference |
| `App.config` | Configuration | **Medium** - Merge into appsettings.json |

### DataLayer Files Summary

| File Path | Change Type | Priority |
|-----------|-------------|----------|
| `DataLayer.csproj` | Replace with SDK-style | High |
| `packages.config` | Delete (use PackageReference) | High |
| `DataClasses/SampleWebAppDb.cs` | Major refactor | High |
| `DataClasses/EfConfiguration.cs` | Delete | High |
| `Startup/DataLayerInitialise.cs` | Major refactor | High |
| `Startup/DataLayerModule.cs` | Update registrations | Medium |
| `DataClasses/Concrete/Blog.cs` | Namespace updates only | Low |
| `DataClasses/Concrete/Post.cs` | Namespace updates only | Low |
| `DataClasses/Concrete/Tag.cs` | Namespace updates only | Low |
| `DataClasses/Concrete/Helpers/TrackUpdate.cs` | No changes | None |
| `Startup/Internal/LoadDbDataFromXml.cs` | No changes | None |

---

## 2. ServiceLayer Analysis

### Location
`ServiceLayer/ServiceLayer.csproj`

### Purpose
The ServiceLayer contains DTOs (Data Transfer Objects), service abstractions, and business logic. It heavily depends on the GenericServices library which will be replaced with custom services + MediatR.

### Key Components

#### 2.1 DTOs (Data Transfer Objects)

| File | Description | Migration Impact |
|------|-------------|------------------|
| `PostServices/DetailPostDto.cs` | Full post DTO for CRUD, inherits `EfGenericDto<Post, DetailPostDto>` | **High** - Remove GenericServices base |
| `PostServices/DetailPostDtoAsync.cs` | Async version, inherits `EfGenericDtoAsync<Post, DetailPostDtoAsync>` | **High** - Remove GenericServices base |
| `PostServices/SimplePostDto.cs` | List view DTO, inherits `EfGenericDto<Post, SimplePostDto>` | **High** - Remove GenericServices base |
| `PostServices/SimplePostDtoAsync.cs` | Async list DTO | **High** - Remove GenericServices base |
| `BlogServices/BlogListDto.cs` | Blog list DTO, inherits `EfGenericDto<Blog, BlogListDto>` | **High** - Remove GenericServices base |
| `TagServices/TagListDto.cs` | Tag list DTO, inherits `EfGenericDto<Tag, TagListDto>` | **High** - Remove GenericServices base |

**Current DTO Pattern (GenericServices):**
```csharp
public class DetailPostDto : EfGenericDto<Post, DetailPostDto>
{
    [Key]
    public int PostId { get; set; }
    public string Title { get; set; }
    public string Content { get; set; }
    
    // GenericServices-specific
    protected override CrudFunctions SupportedFunctions
    {
        get { return CrudFunctions.AllCrud; }
    }
    
    protected override void SetupSecondaryData(
        IGenericServicesDbContext context, DetailPostDto dto)
    {
        // Setup dropdown lists
    }
    
    protected override ISuccessOrErrors<Post> CreateDataFromDto(
        IGenericServicesDbContext context, DetailPostDto source)
    {
        // Custom create logic
    }
}
```

**Target DTO Pattern (Plain DTO + AutoMapper):**
```csharp
public class DetailPostDto
{
    [Key]
    public int PostId { get; set; }
    public string Title { get; set; }
    public string Content { get; set; }
    
    // UI helper properties
    public DropDownListType Bloggers { get; set; }
    public MultiSelectListType UserChosenTags { get; set; }
}

// Separate mapping profile
public class PostMappingProfile : Profile
{
    public PostMappingProfile()
    {
        CreateMap<Post, DetailPostDto>();
        CreateMap<DetailPostDto, Post>();
    }
}

// Separate service for setup
public interface IPostSetupService
{
    Task SetupSecondaryDataAsync(DetailPostDto dto);
}
```

#### 2.2 UI Helper Classes

| File | Description | Migration Impact |
|------|-------------|------------------|
| `UiClasses/DropDownListType.cs` | Dropdown list helper | **Low** - No framework dependencies |
| `UiClasses/MultiSelectListType.cs` | Multi-select list helper | **Low** - No framework dependencies |

These classes are framework-agnostic and require no changes.

#### 2.3 Startup/Initialization

| File | Description | Migration Impact |
|------|-------------|------------------|
| `Startup/ServiceLayerInitialise.cs` | Layer initialization | **Medium** - Remove GenericServices init |
| `Startup/ServiceLayerModule.cs` | Autofac module | **High** - Update for new services |

**ServiceLayerModule.cs Changes:**
```csharp
// Current (registers GenericServices)
builder.RegisterAssemblyTypes(typeof(IListService).Assembly)
    .AsImplementedInterfaces();

// Target (register MediatR and custom services)
builder.RegisterMediatR(typeof(ServiceLayerModule).Assembly);
builder.RegisterAssemblyTypes(typeof(ServiceLayerModule).Assembly)
    .Where(t => t.Name.EndsWith("Service"))
    .AsImplementedInterfaces();
```

#### 2.4 GenericServices Dependencies to Replace

| GenericServices Interface | Current Usage | Replacement |
|--------------------------|---------------|-------------|
| `IListService` | List queries | MediatR `IRequest<List<T>>` |
| `IDetailService` | Get by ID | MediatR `IRequest<T>` |
| `IDetailServiceAsync` | Async get by ID | MediatR `IRequest<T>` |
| `ICreateService` | Create entity | MediatR `IRequest<Result>` |
| `ICreateServiceAsync` | Async create | MediatR `IRequest<Result>` |
| `IUpdateService` | Update entity | MediatR `IRequest<Result>` |
| `IUpdateServiceAsync` | Async update | MediatR `IRequest<Result>` |
| `IDeleteService` | Delete entity | MediatR `IRequest<Result>` |
| `IDeleteServiceAsync` | Async delete | MediatR `IRequest<Result>` |
| `ICreateSetupService` | Setup for create | Custom service |
| `IUpdateSetupService` | Setup for update | Custom service |
| `ISuccessOrErrors` | Result type | `Result<T>` pattern |
| `EfGenericDto<,>` | DTO base class | Plain DTO |
| `EfGenericDtoAsync<,>` | Async DTO base | Plain DTO |
| `IGenericServicesDbContext` | DbContext interface | Standard DbContext |

#### 2.5 Project File

| File | Description | Migration Impact |
|------|-------------|------------------|
| `ServiceLayer.csproj` | Project file (old format) | **High** - Convert to SDK-style |
| `packages.config` | NuGet packages | **High** - Convert to PackageReference |

### ServiceLayer Files Summary

| File Path | Change Type | Priority |
|-----------|-------------|----------|
| `ServiceLayer.csproj` | Replace with SDK-style | High |
| `packages.config` | Delete (use PackageReference) | High |
| `PostServices/DetailPostDto.cs` | Remove GenericServices base, refactor | High |
| `PostServices/DetailPostDtoAsync.cs` | Remove GenericServices base, refactor | High |
| `PostServices/SimplePostDto.cs` | Remove GenericServices base | High |
| `PostServices/SimplePostDtoAsync.cs` | Remove GenericServices base | High |
| `BlogServices/BlogListDto.cs` | Remove GenericServices base | High |
| `TagServices/TagListDto.cs` | Remove GenericServices base | High |
| `Startup/ServiceLayerModule.cs` | Update for MediatR | High |
| `Startup/ServiceLayerInitialise.cs` | Remove GenericServices init | Medium |
| `UiClasses/DropDownListType.cs` | No changes | None |
| `UiClasses/MultiSelectListType.cs` | No changes | None |

### New Files to Create

| File Path | Description |
|-----------|-------------|
| `PostServices/Queries/GetPostsQuery.cs` | MediatR query for post list |
| `PostServices/Queries/GetPostByIdQuery.cs` | MediatR query for single post |
| `PostServices/Commands/CreatePostCommand.cs` | MediatR command for create |
| `PostServices/Commands/UpdatePostCommand.cs` | MediatR command for update |
| `PostServices/Commands/DeletePostCommand.cs` | MediatR command for delete |
| `PostServices/Handlers/GetPostsHandler.cs` | Handler for list query |
| `PostServices/Handlers/GetPostByIdHandler.cs` | Handler for detail query |
| `PostServices/Handlers/CreatePostHandler.cs` | Handler for create |
| `PostServices/Handlers/UpdatePostHandler.cs` | Handler for update |
| `PostServices/Handlers/DeletePostHandler.cs` | Handler for delete |
| `Common/Result.cs` | Result pattern implementation |
| `Mapping/PostMappingProfile.cs` | AutoMapper profile |
| Similar files for Blog and Tag services |

---

## 3. SampleWebApp (Presentation Layer) Analysis

### Location
`SampleWebApp/SampleWebApp.csproj`

### Purpose
The presentation layer contains ASP.NET MVC 5 controllers, Razor views, infrastructure components, and application configuration.

### Key Components

#### 3.1 Controllers

| File | Description | Migration Impact |
|------|-------------|------------------|
| `Controllers/HomeController.cs` | Home page controller | **Medium** - Namespace changes |
| `Controllers/PostsController.cs` | Sync CRUD for posts | **High** - DI pattern change |
| `Controllers/PostsAsyncController.cs` | Async CRUD for posts | **High** - DI pattern change |
| `Controllers/TagsController.cs` | Sync CRUD for tags | **High** - DI pattern change |
| `Controllers/TagsAsyncController.cs` | Async CRUD for tags | **High** - DI pattern change |
| `Controllers/BlogsController.cs` | CRUD for blogs | **High** - DI pattern change |

**Current Controller Pattern (MVC 5 with DiModelBinder):**
```csharp
public class PostsController : Controller
{
    // Services injected as action parameters via DiModelBinder
    public ActionResult Index(int? id, IListService service)
    {
        var query = service.GetAll<SimplePostDto>();
        return View(query.ToList());
    }
    
    [HttpPost]
    [ValidateAntiForgeryToken]
    public ActionResult Edit(DetailPostDto dto, IUpdateService service)
    {
        if (!ModelState.IsValid)
            return View(service.ResetDto(dto));
        
        var response = service.Update(dto);
        if (response.IsValid)
        {
            TempData["message"] = response.SuccessMessage;
            return RedirectToAction("Index");
        }
        
        response.CopyErrorsToModelState(ModelState, dto);
        return View(dto);
    }
}
```

**Target Controller Pattern (ASP.NET Core with Constructor DI):**
```csharp
public class PostsController : Controller
{
    private readonly IMediator _mediator;
    private readonly IPostSetupService _setupService;
    
    public PostsController(IMediator mediator, IPostSetupService setupService)
    {
        _mediator = mediator;
        _setupService = setupService;
    }
    
    public async Task<IActionResult> Index(int? id)
    {
        var posts = await _mediator.Send(new GetPostsQuery { BlogId = id });
        return View(posts);
    }
    
    [HttpPost]
    [ValidateAntiForgeryToken]
    public async Task<IActionResult> Edit(DetailPostDto dto)
    {
        if (!ModelState.IsValid)
        {
            await _setupService.SetupSecondaryDataAsync(dto);
            return View(dto);
        }
        
        var result = await _mediator.Send(new UpdatePostCommand { Dto = dto });
        if (result.IsSuccess)
        {
            TempData["message"] = result.Message;
            return RedirectToAction("Index");
        }
        
        result.CopyErrorsToModelState(ModelState);
        return View(dto);
    }
}
```

#### 3.2 Infrastructure

| File | Description | Migration Impact |
|------|-------------|------------------|
| `Infrastructure/WebUiInitialise.cs` | Application startup, DI setup | **High** - Move to Program.cs |
| `Infrastructure/AutofacDi.cs` | Autofac container setup | **High** - Move to Program.cs |
| `Infrastructure/DiModelBinder.cs` | Custom model binder for DI | **High** - Delete (use constructor DI) |
| `Infrastructure/ValidationHelper.cs` | ModelState error handling | **Medium** - Namespace changes |
| `Infrastructure/Log4NetGenericLogger.cs` | Log4net logger wrapper | **Medium** - Replace with ILogger |
| `Infrastructure/TraceGenericLogger.cs` | Trace logger wrapper | **Medium** - Replace with ILogger |

**WebUiInitialise.cs - Complete Replacement:**
This file's functionality moves to `Program.cs`:
- Host type detection → Configuration/environment variables
- Logging setup → `builder.Logging.AddXxx()`
- Service layer initialization → Startup services
- Autofac setup → `builder.Host.UseServiceProviderFactory()`

**DiModelBinder.cs - Delete:**
The custom model binder pattern is replaced by:
1. Constructor injection (preferred)
2. `[FromServices]` attribute for action parameters

#### 3.3 App_Start Configuration

| File | Description | Migration Impact |
|------|-------------|------------------|
| `App_Start/RouteConfig.cs` | Route registration | **High** - Move to Program.cs |
| `App_Start/BundleConfig.cs` | Script/CSS bundling | **High** - Replace with WebOptimizer or manual |
| `App_Start/FilterConfig.cs` | Global filters | **Medium** - Move to AddControllersWithViews |

**RouteConfig.cs → Program.cs:**
```csharp
// Current (MVC 5)
routes.MapRoute(
    name: "Default",
    url: "{controller}/{action}/{id}",
    defaults: new { controller = "Home", action = "Index", id = UrlParameter.Optional }
);

// Target (ASP.NET Core)
app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");
```

**BundleConfig.cs → WebOptimizer or manual:**
```csharp
// Current (MVC 5)
bundles.Add(new ScriptBundle("~/bundles/javascript").Include(
    "~/Scripts/jquery-{version}.js",
    "~/Scripts/bootstrap.js"));

// Target Option 1: WebOptimizer
builder.Services.AddWebOptimizer(pipeline =>
{
    pipeline.AddJavaScriptBundle("/js/bundle.js",
        "js/jquery.js",
        "js/bootstrap.js");
});

// Target Option 2: Manual in _Layout.cshtml
<script src="~/js/jquery.min.js"></script>
<script src="~/js/bootstrap.min.js"></script>
```

#### 3.4 Global.asax

| File | Description | Migration Impact |
|------|-------------|------------------|
| `Global.asax` | Application entry point | **High** - Delete |
| `Global.asax.cs` | Application startup code | **High** - Move to Program.cs |

**Global.asax.cs → Program.cs:**
```csharp
// Current (MVC 5)
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

// Target (ASP.NET Core Program.cs)
var builder = WebApplication.CreateBuilder(args);

// Services
builder.Services.AddControllersWithViews();
builder.Services.AddDbContext<SampleWebAppDb>(options => ...);
builder.Host.UseServiceProviderFactory(new AutofacServiceProviderFactory());
builder.Host.ConfigureContainer<ContainerBuilder>(containerBuilder =>
{
    containerBuilder.RegisterModule(new ServiceLayerModule());
});

var app = builder.Build();

// Middleware
app.UseStaticFiles();
app.UseRouting();
app.UseAuthorization();

app.MapControllerRoute(
    name: "default",
    pattern: "{controller=Home}/{action=Index}/{id?}");

// Database initialization
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<SampleWebAppDb>();
    db.Database.EnsureCreated();
}

app.Run();
```

#### 3.5 Configuration Files

| File | Description | Migration Impact |
|------|-------------|------------------|
| `Web.config` | Main configuration | **High** - Split to appsettings.json |
| `Web.Debug.config` | Debug transforms | **Medium** - Use appsettings.Development.json |
| `Web.Release.config` | Release transforms | **Medium** - Use appsettings.Production.json |
| `Views/Web.config` | View configuration | **Medium** - Use _ViewImports.cshtml |

**Web.config Connection String → appsettings.json:**
```xml
<!-- Current (Web.config) -->
<connectionStrings>
    <add name="SampleWebAppDb" 
         connectionString="Data Source=(localdb)\mssqllocaldb;Initial Catalog=SampleWebAppDb;..." 
         providerName="System.Data.SqlClient" />
</connectionStrings>
```

```json
// Target (appsettings.json)
{
  "ConnectionStrings": {
    "SampleWebAppDb": "Data Source=(localdb)\\mssqllocaldb;Initial Catalog=SampleWebAppDb;..."
  },
  "Logging": {
    "LogLevel": {
      "Default": "Information"
    }
  }
}
```

#### 3.6 Views

| File/Folder | Description | Migration Impact |
|-------------|-------------|------------------|
| `Views/_ViewStart.cshtml` | View startup | **Low** - Compatible |
| `Views/Shared/_Layout.cshtml` | Main layout | **Medium** - Update bundling references |
| `Views/Shared/Error.cshtml` | Error page | **Low** - Compatible |
| `Views/Shared/EditorTemplates/*.cshtml` | Editor templates | **Low** - Compatible |
| `Views/Posts/*.cshtml` | Post views | **Low** - Compatible |
| `Views/PostsAsync/*.cshtml` | Async post views | **Low** - Compatible |
| `Views/Tags/*.cshtml` | Tag views | **Low** - Compatible |
| `Views/TagsAsync/*.cshtml` | Async tag views | **Low** - Compatible |
| `Views/Blogs/*.cshtml` | Blog views | **Low** - Compatible |
| `Views/Home/*.cshtml` | Home views | **Low** - Compatible |

**_Layout.cshtml Changes:**
```html
<!-- Current (MVC 5) -->
@Styles.Render("~/Content/css")
@Scripts.Render("~/bundles/javascript")

<!-- Target (ASP.NET Core) -->
<link rel="stylesheet" href="~/css/site.css" />
<script src="~/lib/jquery/jquery.min.js"></script>
<script src="~/lib/bootstrap/js/bootstrap.bundle.min.js"></script>
```

**New File: _ViewImports.cshtml:**
```cshtml
@using SampleWebApp
@using SampleWebApp.Models
@addTagHelper *, Microsoft.AspNetCore.Mvc.TagHelpers
```

#### 3.7 Project File

| File | Description | Migration Impact |
|------|-------------|------------------|
| `SampleWebApp.csproj` | Project file (old format) | **High** - Convert to SDK-style |
| `packages.config` | NuGet packages | **High** - Convert to PackageReference |

### SampleWebApp Files Summary

| File Path | Change Type | Priority |
|-----------|-------------|----------|
| `SampleWebApp.csproj` | Replace with SDK-style | High |
| `packages.config` | Delete (use PackageReference) | High |
| `Global.asax` | Delete | High |
| `Global.asax.cs` | Delete (move to Program.cs) | High |
| `Web.config` | Replace with appsettings.json | High |
| `Infrastructure/WebUiInitialise.cs` | Delete (move to Program.cs) | High |
| `Infrastructure/AutofacDi.cs` | Delete (move to Program.cs) | High |
| `Infrastructure/DiModelBinder.cs` | Delete | High |
| `App_Start/RouteConfig.cs` | Delete (move to Program.cs) | High |
| `App_Start/BundleConfig.cs` | Delete or replace | High |
| `App_Start/FilterConfig.cs` | Delete (move to Program.cs) | High |
| `Controllers/PostsController.cs` | Major refactor | High |
| `Controllers/PostsAsyncController.cs` | Major refactor | High |
| `Controllers/TagsController.cs` | Major refactor | High |
| `Controllers/TagsAsyncController.cs` | Major refactor | High |
| `Controllers/BlogsController.cs` | Major refactor | High |
| `Controllers/HomeController.cs` | Namespace changes | Medium |
| `Infrastructure/ValidationHelper.cs` | Namespace changes | Medium |
| `Infrastructure/Log4NetGenericLogger.cs` | Replace with ILogger | Medium |
| `Infrastructure/TraceGenericLogger.cs` | Replace with ILogger | Medium |
| `Views/Shared/_Layout.cshtml` | Update bundling | Medium |
| `Views/Web.config` | Delete | Medium |
| All other Views | Minor or no changes | Low |

### New Files to Create

| File Path | Description |
|-----------|-------------|
| `Program.cs` | Application entry point |
| `appsettings.json` | Configuration |
| `appsettings.Development.json` | Development configuration |
| `Views/_ViewImports.cshtml` | Tag helper imports |

---

## 4. Dependencies Between Layers

### Current Dependency Graph

```
SampleWebApp
    ├── References: ServiceLayer
    ├── References: DataLayer
    ├── Uses: GenericServices (via ServiceLayer)
    └── Uses: Autofac, Autofac.Mvc5

ServiceLayer
    ├── References: DataLayer
    ├── Uses: GenericServices
    ├── Uses: GenericLibsBase
    └── Uses: Autofac, AutoMapper

DataLayer
    ├── Uses: EntityFramework 6.1.3
    ├── Uses: GenericServices
    ├── Uses: GenericLibsBase
    └── Uses: Autofac, AutoMapper
```

### Target Dependency Graph

```
SampleWebApp
    ├── References: ServiceLayer
    ├── References: DataLayer (for DbContext registration)
    ├── Uses: MediatR
    └── Uses: Autofac.Extensions.DependencyInjection

ServiceLayer
    ├── References: DataLayer
    ├── Uses: MediatR
    ├── Uses: AutoMapper
    └── Uses: Autofac

DataLayer
    ├── Uses: Microsoft.EntityFrameworkCore
    ├── Uses: Microsoft.EntityFrameworkCore.SqlServer
    └── Uses: AutoMapper
```

---

## 5. Recommended Migration Order

### Phase 1: Project Infrastructure (Week 1)
**Objective**: Convert project files and establish .NET Core foundation

1. Convert all `.csproj` files to SDK-style format
2. Remove `packages.config` files
3. Add PackageReference for .NET Core packages
4. Create `Program.cs` skeleton
5. Create `appsettings.json`

**Risk Level**: Low
**Justification**: No functional changes; establishes foundation for subsequent phases

### Phase 2: DataLayer Migration (Week 2)
**Objective**: Migrate Entity Framework 6 to EF Core

1. Update `SampleWebAppDb.cs` for EF Core
2. Delete `EfConfiguration.cs`
3. Update `DataLayerInitialise.cs`
4. Update `DataLayerModule.cs`
5. Test database connectivity

**Risk Level**: Medium
**Justification**: DataLayer has no upstream dependencies; can be tested in isolation

### Phase 3: ServiceLayer Migration (Weeks 3-4)
**Objective**: Replace GenericServices with MediatR + custom services

1. Create MediatR infrastructure (queries, commands, handlers)
2. Convert DTOs to plain classes
3. Create AutoMapper profiles
4. Implement result pattern
5. Update `ServiceLayerModule.cs`
6. Test all service operations

**Risk Level**: High
**Justification**: Most complex phase; requires complete replacement of GenericServices

### Phase 4: Presentation Layer Migration (Week 5)
**Objective**: Migrate ASP.NET MVC 5 to ASP.NET Core MVC

1. Complete `Program.cs` with all middleware
2. Migrate controllers to constructor injection
3. Update views for ASP.NET Core
4. Delete obsolete files (Global.asax, Web.config, etc.)
5. Test all endpoints

**Risk Level**: Medium
**Justification**: Depends on ServiceLayer completion; mostly mechanical changes

### Phase 5: Testing and Cleanup (Week 6)
**Objective**: Validate migration and remove legacy code

1. Run all existing tests
2. Create integration tests
3. Remove unused packages
4. Performance testing
5. Documentation updates

**Risk Level**: Low
**Justification**: Validation phase; no new functionality

---

## 6. Migration Checklist

### Pre-Migration
- [ ] Backup existing codebase
- [ ] Document current functionality
- [ ] Set up .NET 6/8 SDK
- [ ] Create migration branch

### DataLayer
- [ ] Convert DataLayer.csproj to SDK-style
- [ ] Update SampleWebAppDb.cs for EF Core
- [ ] Delete EfConfiguration.cs
- [ ] Update DataLayerInitialise.cs
- [ ] Update DataLayerModule.cs
- [ ] Verify entity classes compile
- [ ] Test database connection

### ServiceLayer
- [ ] Convert ServiceLayer.csproj to SDK-style
- [ ] Install MediatR packages
- [ ] Create query/command classes
- [ ] Create handler classes
- [ ] Convert DTOs to plain classes
- [ ] Create AutoMapper profiles
- [ ] Implement Result pattern
- [ ] Update ServiceLayerModule.cs
- [ ] Test all CRUD operations

### SampleWebApp
- [ ] Convert SampleWebApp.csproj to SDK-style
- [ ] Create Program.cs
- [ ] Create appsettings.json
- [ ] Migrate controllers
- [ ] Update views
- [ ] Create _ViewImports.cshtml
- [ ] Delete obsolete files
- [ ] Test all endpoints

### Post-Migration
- [ ] Run all tests
- [ ] Performance validation
- [ ] Security review
- [ ] Documentation update
- [ ] Deployment verification
