# Package Migration Strategy

This document outlines the migration strategy for NuGet packages from .NET Framework 4.5.1 to .NET Core 8.0.

## Overview

The existing SampleMvcWebApp uses various NuGet packages that need to be migrated to their .NET Core equivalents. This document provides a comprehensive mapping of current packages to their recommended .NET Core replacements.

## Package Migration Matrix

### Core Framework Packages

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| EntityFramework | 6.1.3 | Microsoft.EntityFrameworkCore | 8.0.x | Complete rewrite required for DbContext |
| EntityFramework | 6.1.3 | Microsoft.EntityFrameworkCore.SqlServer | 8.0.x | SQL Server provider |
| EntityFramework | 6.1.3 | Microsoft.EntityFrameworkCore.Tools | 8.0.x | EF Core CLI tools |

### Dependency Injection

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| Autofac | 3.5.0 | Autofac | 8.0.x | Compatible with .NET Core |
| Autofac.Mvc5 | 3.3.1 | Autofac.Extensions.DependencyInjection | 9.0.x | Integration with ASP.NET Core DI |

### Object Mapping

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| AutoMapper | 4.2.1 | AutoMapper | 12.0.x | API changes in newer versions |
| AutoMapper | 4.2.1 | AutoMapper.Extensions.Microsoft.DependencyInjection | 12.0.x | DI integration |

### ASP.NET MVC

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| Microsoft.AspNet.Mvc | 5.2.3 | Built-in ASP.NET Core MVC | N/A | Part of framework |
| Microsoft.AspNet.Razor | 3.2.3 | Built-in ASP.NET Core Razor | N/A | Part of framework |
| Microsoft.AspNet.WebPages | 3.2.3 | Built-in ASP.NET Core | N/A | Part of framework |

### Identity & Authentication

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| Microsoft.AspNet.Identity.Core | 2.1.0 | Microsoft.AspNetCore.Identity | 8.0.x | Built into ASP.NET Core |
| Microsoft.AspNet.Identity.EntityFramework | 2.1.0 | Microsoft.AspNetCore.Identity.EntityFrameworkCore | 8.0.x | EF Core integration |
| Microsoft.AspNet.Identity.Owin | 2.1.0 | Built-in ASP.NET Core Identity | N/A | OWIN not needed |

### OWIN Middleware

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| Microsoft.Owin | 3.0.0 | Built-in ASP.NET Core middleware | N/A | Not needed |
| Microsoft.Owin.Security | 3.0.0 | Microsoft.AspNetCore.Authentication | 8.0.x | Built-in |
| Microsoft.Owin.Security.Cookies | 3.0.0 | Microsoft.AspNetCore.Authentication.Cookies | 8.0.x | Cookie auth |
| Microsoft.Owin.Security.OAuth | 3.0.0 | Microsoft.AspNetCore.Authentication.OAuth | 8.0.x | OAuth support |
| Microsoft.Owin.Security.Facebook | 3.0.0 | Microsoft.AspNetCore.Authentication.Facebook | 8.0.x | Facebook auth |
| Microsoft.Owin.Security.Google | 3.0.0 | Microsoft.AspNetCore.Authentication.Google | 8.0.x | Google auth |
| Microsoft.Owin.Security.MicrosoftAccount | 3.0.0 | Microsoft.AspNetCore.Authentication.MicrosoftAccount | 8.0.x | Microsoft auth |
| Microsoft.Owin.Security.Twitter | 2.1.0 | Microsoft.AspNetCore.Authentication.Twitter | 8.0.x | Twitter auth |

### SignalR

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| Microsoft.AspNet.SignalR | 2.0.3 | Microsoft.AspNetCore.SignalR | 8.0.x | Complete rewrite |
| Microsoft.AspNet.SignalR.Core | 2.0.3 | Microsoft.AspNetCore.SignalR | 8.0.x | Part of core package |
| Microsoft.AspNet.SignalR.JS | 2.0.3 | @microsoft/signalr | 8.0.x | npm package |
| Microsoft.AspNet.SignalR.SystemWeb | 2.0.3 | Not needed | N/A | Integrated in ASP.NET Core |

### Frontend Libraries

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| Bootstrap | 3.3.2 | Bootstrap (via npm/LibMan) | 5.3.x | Major version upgrade |
| jQuery | 1.10.2 | jQuery (via npm/LibMan) | 3.7.x | Security updates |
| jQuery.UI.Combined | 1.10.4 | jQuery UI (via npm/LibMan) | 1.13.x | Optional |
| jQuery.Validation | 1.11.1 | jquery-validation (via npm/LibMan) | 1.19.x | Form validation |
| Modernizr | 2.6.2 | modernizr (via npm/LibMan) | 3.13.x | Feature detection |
| Respond | 1.2.0 | Not needed | N/A | IE8 support, deprecated |

### Utility Libraries

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| Newtonsoft.Json | 6.0.4 | System.Text.Json or Newtonsoft.Json | 13.0.x | Built-in JSON or upgrade |
| log4net | 2.0.3 | Microsoft.Extensions.Logging + Serilog | 8.0.x | Modern logging |
| MarkdownSharp | 1.13.0.0 | Markdig | 0.34.x | Modern Markdown parser |
| DelegateDecompiler | 0.18.0 | DelegateDecompiler | 0.32.x | Compatible version |
| DelegateDecompiler.EntityFramework | 0.18.0 | DelegateDecompiler.EntityFrameworkCore | 0.32.x | EF Core version |

### Bundling & Optimization

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| Microsoft.AspNet.Web.Optimization | 1.1.3 | WebOptimizer.Core | 3.0.x | Or use bundler tools |
| WebGrease | 1.5.2 | Not needed | N/A | Use modern bundlers |
| Antlr | 3.4.1.9004 | Not needed | N/A | WebGrease dependency |

### GenericServices (Custom Library)

| Current Package | Version | .NET Core Replacement | Target Version | Notes |
|-----------------|---------|----------------------|----------------|-------|
| GenericServices | 1.0.9 | Custom implementation or EfCore.GenericServices | 5.x | May need custom port |
| GenericLibsBase | 1.0.1 | Custom implementation | N/A | May need custom port |

## Migration Priority

### Phase 1 - Critical (Infrastructure)
1. Entity Framework to EF Core
2. ASP.NET MVC to ASP.NET Core MVC
3. Autofac DI integration

### Phase 2 - Authentication
1. ASP.NET Identity to ASP.NET Core Identity
2. OWIN middleware to ASP.NET Core middleware
3. External authentication providers

### Phase 3 - Features
1. SignalR migration
2. AutoMapper upgrade
3. Logging infrastructure

### Phase 4 - Frontend
1. Bootstrap upgrade
2. jQuery updates
3. Bundling/optimization

## Recommended Package Installation Commands

```bash
# Core packages for DataLayer.Core
dotnet add DataLayer.Core package Microsoft.EntityFrameworkCore --version 8.0.0
dotnet add DataLayer.Core package Microsoft.EntityFrameworkCore.SqlServer --version 8.0.0

# Core packages for ServiceLayer.Core
dotnet add ServiceLayer.Core package AutoMapper --version 12.0.1

# Core packages for SampleWebApp.Core
dotnet add SampleWebApp.Core package Autofac.Extensions.DependencyInjection --version 9.0.0
dotnet add SampleWebApp.Core package AutoMapper.Extensions.Microsoft.DependencyInjection --version 12.0.1
dotnet add SampleWebApp.Core package Microsoft.AspNetCore.Identity.EntityFrameworkCore --version 8.0.0
dotnet add SampleWebApp.Core package Serilog.AspNetCore --version 8.0.0

# Test packages for Tests.Core
dotnet add Tests.Core package Microsoft.EntityFrameworkCore.InMemory --version 8.0.0
dotnet add Tests.Core package Moq --version 4.20.70
```

## Breaking Changes to Address

1. **Entity Framework**: Complete DbContext rewrite, LINQ query changes, migration format changes
2. **AutoMapper**: Profile-based configuration, projection changes
3. **Identity**: Different user/role management APIs
4. **SignalR**: Hub method signatures, client library changes
5. **Bundling**: Different approach to static file handling

## References

- [Microsoft .NET Upgrade Assistant](https://docs.microsoft.com/en-us/dotnet/core/porting/upgrade-assistant-overview)
- [Porting from .NET Framework to .NET Core](https://docs.microsoft.com/en-us/dotnet/core/porting/)
- [EF6 to EF Core Migration](https://docs.microsoft.com/en-us/ef/efcore-and-ef6/)
