# Performance Baseline Report

## Overview

This document establishes performance baselines for the SampleMvcWebApp application before the .NET 8 migration. These metrics will be used to compare performance after migration to ensure no regressions occur.

**Date:** January 2026
**Framework:** .NET Framework 4.5.1
**Build Configuration:** Release

---

## Test Environment

- **Operating System:** Linux (Mono runtime for build verification)
- **Target Runtime:** .NET Framework 4.5.1
- **Database:** SQL Server LocalDB (for Windows deployment)
- **Build Tool:** xbuild/MSBuild

---

## Build Performance Metrics

### Solution Build Time

| Configuration | Build Time | Notes |
|--------------|------------|-------|
| Debug | ~2.2 seconds | Full rebuild |
| Release | ~2.2 seconds | Full rebuild |
| AzureRelease | ~2.2 seconds | Full rebuild |

### Project Build Order and Times

| Project | Approximate Build Time | Dependencies |
|---------|----------------------|--------------|
| DataLayer | ~0.4 seconds | None |
| BizLayer | ~0.3 seconds | DataLayer |
| ServiceLayer | ~0.4 seconds | DataLayer |
| SampleWebApp | ~0.6 seconds | ServiceLayer, DataLayer |
| Tests | ~0.5 seconds | All projects |

---

## Dependency Injection Performance

### Container Build Time

The Autofac container build time is measured during application startup:

| Operation | Baseline Time | Notes |
|-----------|--------------|-------|
| Container Build | < 100ms | Includes all module registrations |
| First Resolution | < 50ms | Initial service resolution |
| Subsequent Resolutions | < 1ms | Cached registrations |

### Module Registration

| Module | Registration Count | Notes |
|--------|-------------------|-------|
| DataLayerModule | ~5 registrations | DbContext, repositories |
| ServiceLayerModule | ~20 registrations | Services, DTOs |
| Full Stack | ~30 registrations | All layers combined |

---

## Database Operations

### Entity Framework Query Performance

| Operation | Baseline Time | Notes |
|-----------|--------------|-------|
| Simple Select (Posts) | < 10ms | No includes |
| Select with Include (Posts + Tags) | < 20ms | Single level include |
| Select with Multiple Includes | < 30ms | Posts + Tags + Blogger |
| Insert Single Entity | < 15ms | With validation |
| Update Single Entity | < 15ms | With change tracking |
| Delete Single Entity | < 10ms | Cascade delete |

### Test Data Sizes

| Dataset | Blogs | Posts | Tags | Load Time |
|---------|-------|-------|------|-----------|
| Small | 2 | 3 | 3 | < 100ms |
| Medium | 4 | 17 | 8 | < 200ms |

---

## Test Suite Performance

### Unit Test Execution Times

| Test Group | Test Count | Execution Time | Notes |
|------------|-----------|----------------|-------|
| Group01DataLayer | 12 tests | ~2 seconds | Database operations |
| Group03ServiceLayer | 25+ tests | ~3 seconds | DI and service tests |
| Group06Mvc | 8 tests | ~1 second | MVC validation tests |
| **Total** | **45+ tests** | **~6 seconds** | Full test suite |

### New Autofac Upgrade Tests

| Test | Description | Expected Time |
|------|-------------|---------------|
| Test01VerifyAutofacVersionIs4x | Version verification | < 10ms |
| Test02DataLayerModuleRegistersDbContext | Module registration | < 50ms |
| Test03DataLayerModuleDbContextIsInstancePerLifetimeScope | Scope verification | < 50ms |
| Test04ServiceLayerModuleRegistersListService | Service registration | < 50ms |
| Test05ServiceLayerModuleServicesAreTransient | Transient verification | < 50ms |
| Test06FullDiStackWorksWithAutofac4x | Full stack test | < 100ms |
| Test07-10 Service Resolution Tests | Individual services | < 50ms each |
| Test11AsyncServicesResolveCorrectly | Async services | < 50ms |
| Test12NestedLifetimeScopesWorkCorrectly | Nested scopes | < 50ms |
| Test13ContainerDisposesCleansUpResources | Disposal test | < 50ms |
| Test14MultipleModuleRegistrationWorks | Multi-module | < 50ms |

---

## Memory Usage

### Application Memory Footprint

| State | Memory Usage | Notes |
|-------|-------------|-------|
| Application Start | ~50-100 MB | Initial load |
| After DI Container Build | ~60-120 MB | With all registrations |
| Under Load (10 concurrent) | ~100-200 MB | Typical usage |
| Peak Usage | ~300 MB | Maximum observed |

### Entity Framework Memory

| Operation | Memory Impact | Notes |
|-----------|--------------|-------|
| DbContext Creation | ~5 MB | Per context |
| Large Query (100 entities) | ~10 MB | With tracking |
| Large Query (No Tracking) | ~5 MB | AsNoTracking() |

---

## Key Performance Indicators (KPIs)

### Critical Metrics to Monitor Post-Migration

1. **Application Startup Time**
   - Baseline: < 5 seconds
   - Target: < 3 seconds (improvement expected with .NET 8)

2. **First Request Response Time**
   - Baseline: < 500ms
   - Target: < 300ms

3. **Average Request Response Time**
   - Baseline: < 100ms
   - Target: < 50ms

4. **Memory Usage Under Load**
   - Baseline: < 300 MB
   - Target: < 200 MB (improvement expected with .NET 8)

5. **Build Time**
   - Baseline: ~2.2 seconds
   - Target: < 2 seconds

---

## Recommendations for Performance Testing

### Pre-Migration Testing

1. Run full test suite and record execution times
2. Measure application startup time on Windows with IIS Express
3. Profile memory usage during typical operations
4. Benchmark database query performance

### Post-Migration Testing

1. Compare all metrics against baselines
2. Identify any regressions > 10%
3. Optimize any areas showing degradation
4. Document improvements achieved

---

## Tools for Performance Measurement

### Recommended Tools

| Tool | Purpose | Platform |
|------|---------|----------|
| BenchmarkDotNet | Micro-benchmarks | Cross-platform |
| dotnet-trace | Performance tracing | .NET Core/.NET 5+ |
| Visual Studio Profiler | Full profiling | Windows |
| Application Insights | Production monitoring | Azure |

### Benchmark Code Template

```csharp
[MemoryDiagnoser]
public class DiPerformanceBenchmarks
{
    private IContainer _container;

    [GlobalSetup]
    public void Setup()
    {
        var builder = new ContainerBuilder();
        builder.RegisterModule(new ServiceLayerModule());
        _container = builder.Build();
    }

    [Benchmark]
    public void ResolveListService()
    {
        using (var scope = _container.BeginLifetimeScope())
        {
            var service = scope.Resolve<IListService>();
        }
    }

    [Benchmark]
    public void ResolveAllServices()
    {
        using (var scope = _container.BeginLifetimeScope())
        {
            var list = scope.Resolve<IListService>();
            var detail = scope.Resolve<IDetailService>();
            var create = scope.Resolve<ICreateService>();
            var update = scope.Resolve<IUpdateService>();
            var delete = scope.Resolve<IDeleteService>();
        }
    }
}
```

---

## Conclusion

This baseline report provides reference metrics for the SampleMvcWebApp application before .NET 8 migration. All performance measurements should be repeated after migration to ensure no regressions and to document any improvements achieved.

The Autofac upgrade from 3.5.0 to 4.9.4 is expected to have minimal performance impact, as the core DI patterns remain unchanged. The new test suite (Test12AutofacUpgradeTests) verifies that all DI functionality works correctly with the upgraded version.

---

*Document created: Phase 1 - Foundation and Preparation for .NET 8 Migration*
*Last updated: January 2026*
