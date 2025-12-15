# Migration Matrix Document

This document provides a comprehensive mapping of the migration strategy from .NET Framework 4.5.1 to .NET Core 8.0 using the Strangler Fig Pattern.

## Project Migration Overview

| Current Project | .NET Core Target | Framework | Status | Priority |
|-----------------|------------------|-----------|--------|----------|
| SampleWebApp | SampleWebApp.Core | net8.0 | Infrastructure Ready | High |
| DataLayer | DataLayer.Core | netstandard2.0 | Infrastructure Ready | High |
| ServiceLayer | ServiceLayer.Core | netstandard2.0 | Infrastructure Ready | High |
| Tests | Tests.Core | net8.0 | Infrastructure Ready | Medium |

## Detailed File Migration Matrix

### SampleWebApp -> SampleWebApp.Core

| Source Path | Target Path | Migration Complexity | Notes |
|-------------|-------------|---------------------|-------|
| Controllers/ | Controllers/ | Medium | Update base classes, action results |
| Controllers/PostsController.cs | Controllers/PostsController.cs | Medium | Update DI, async patterns |
| Controllers/PostsAsyncController.cs | Controllers/PostsController.cs | Low | Merge with sync controller |
| Controllers/TagsController.cs | Controllers/TagsController.cs | Medium | Update DI patterns |
| Controllers/TagsAsyncController.cs | Controllers/TagsController.cs | Low | Merge with sync controller |
| Controllers/BlogsController.cs | Controllers/BlogsController.cs | Medium | Update DI patterns |
| Controllers/HomeController.cs | Controllers/HomeController.cs | Low | Minimal changes |
| Views/ | Views/ | Medium | Update Razor syntax, tag helpers |
| Views/_ViewImports.cshtml | Views/_ViewImports.cshtml | Medium | Add tag helper imports |
| Views/Shared/_Layout.cshtml | Views/Shared/_Layout.cshtml | Medium | Update Bootstrap, scripts |
| Infrastructure/ | Infrastructure/ | High | Major DI changes |
| Infrastructure/AutofacDi.cs | Program.cs / Extensions/ | High | Convert to ASP.NET Core DI |
| Infrastructure/DiModelBinder.cs | Not needed | N/A | Use built-in DI |
| Infrastructure/WebUiInitialise.cs | Program.cs | High | Startup configuration |
| App_Start/ | Program.cs | High | Consolidate startup |
| App_Start/BundleConfig.cs | wwwroot/ or bundler | Medium | Use LibMan or npm |
| App_Start/FilterConfig.cs | Program.cs | Low | Register filters |
| App_Start/RouteConfig.cs | Program.cs | Low | Use attribute routing |
| Global.asax.cs | Program.cs | High | Application lifecycle |
| Web.config | appsettings.json | Medium | Configuration migration |
| packages.config | *.csproj | Low | PackageReference format |

### DataLayer -> DataLayer.Core

| Source Path | Target Path | Migration Complexity | Notes |
|-------------|-------------|---------------------|-------|
| DataClasses/ | DataClasses/ | Low | Entity classes mostly unchanged |
| DataClasses/Concrete/Post.cs | DataClasses/Concrete/Post.cs | Low | Minor attribute updates |
| DataClasses/Concrete/Tag.cs | DataClasses/Concrete/Tag.cs | Low | Minor attribute updates |
| DataClasses/Concrete/Blog.cs | DataClasses/Concrete/Blog.cs | Low | Minor attribute updates |
| SampleWebAppDb.cs | SampleWebAppDb.cs | High | EF6 to EF Core DbContext |
| Startup/ | Startup/ | Medium | Database initialization |
| Startup/DataLayerInitialise.cs | Extensions/ | Medium | EF Core migrations |
| App.config | N/A | N/A | Not needed in .NET Core |
| packages.config | DataLayer.Core.csproj | Low | PackageReference format |

### ServiceLayer -> ServiceLayer.Core

| Source Path | Target Path | Migration Complexity | Notes |
|-------------|-------------|---------------------|-------|
| PostServices/ | PostServices/ | Medium | DTO updates |
| PostServices/DetailPostDto.cs | PostServices/DetailPostDto.cs | Medium | GenericServices migration |
| PostServices/DetailPostDtoAsync.cs | PostServices/DetailPostDto.cs | Low | Merge with sync DTO |
| PostServices/SimplePostDto.cs | PostServices/SimplePostDto.cs | Low | Minimal changes |
| TagServices/ | TagServices/ | Medium | DTO updates |
| TagServices/TagListDto.cs | TagServices/TagListDto.cs | Low | Minimal changes |
| BlogServices/ | BlogServices/ | Medium | DTO updates |
| BlogServices/BlogListDto.cs | BlogServices/BlogListDto.cs | Low | Minimal changes |
| UiClasses/ | UiClasses/ | Low | UI helper classes |
| Startup/ | Extensions/ | Medium | Service registration |
| App.config | N/A | N/A | Not needed |
| packages.config | ServiceLayer.Core.csproj | Low | PackageReference format |

### Tests -> Tests.Core

| Source Path | Target Path | Migration Complexity | Notes |
|-------------|-------------|---------------------|-------|
| UnitTests/ | UnitTests/ | Medium | Update test framework |
| IntegrationTests/ | IntegrationTests/ | High | EF Core InMemory provider |
| Helpers/ | Helpers/ | Low | Test utilities |
| packages.config | Tests.Core.csproj | Low | xUnit already configured |

## Dependency Chain

```
SampleWebApp.Core
    ├── ServiceLayer.Core
    │   └── DataLayer.Core
    └── DataLayer.Core

Tests.Core
    ├── SampleWebApp.Core
    ├── ServiceLayer.Core
    └── DataLayer.Core
```

## Migration Phases

### Phase 1: Infrastructure Setup (Current)
- [x] Create .NET Core solution structure
- [x] Configure project references
- [x] Set up CI/CD pipeline
- [x] Create Docker configuration
- [x] Document migration strategy

### Phase 2: Data Layer Migration
- [ ] Migrate entity classes to DataLayer.Core
- [ ] Convert DbContext to EF Core
- [ ] Create EF Core migrations
- [ ] Update connection string handling
- [ ] Test database operations

### Phase 3: Service Layer Migration
- [ ] Migrate DTOs to ServiceLayer.Core
- [ ] Update AutoMapper configurations
- [ ] Replace GenericServices with EF Core patterns
- [ ] Implement service interfaces
- [ ] Test service operations

### Phase 4: Web Application Migration
- [ ] Migrate controllers to SampleWebApp.Core
- [ ] Convert views to ASP.NET Core Razor
- [ ] Update dependency injection
- [ ] Migrate authentication/authorization
- [ ] Update static file handling
- [ ] Test web endpoints

### Phase 5: Testing & Validation
- [ ] Migrate unit tests to Tests.Core
- [ ] Create integration tests
- [ ] Perform side-by-side testing
- [ ] Validate feature parity
- [ ] Performance testing

### Phase 6: Cutover
- [ ] Deploy .NET Core application
- [ ] Configure reverse proxy routing
- [ ] Gradual traffic migration
- [ ] Monitor and validate
- [ ] Decommission legacy application

## Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| GenericServices incompatibility | High | High | Custom implementation or alternative library |
| EF6 to EF Core data loss | High | Low | Comprehensive testing, backup strategy |
| Authentication migration issues | Medium | Medium | Parallel authentication during transition |
| Performance regression | Medium | Medium | Load testing, performance benchmarks |
| Breaking API changes | Medium | Low | API versioning, backward compatibility |

## Success Criteria

1. All unit tests pass in .NET Core
2. Feature parity with legacy application
3. No data loss during migration
4. Performance equal to or better than legacy
5. Successful deployment to production
6. Zero downtime during cutover
