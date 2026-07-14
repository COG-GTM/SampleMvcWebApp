SampleMvcWebApp
===============

> ### .NET 8 modernization
>
> This repository has been migrated from **ASP.NET MVC 5 / .NET Framework 4.5.1** to
> **cross-platform ASP.NET Core MVC on .NET 8**, while preserving the app's pages, CRUD
> flows, and business rules. The historical description below is retained for context.
>
> **What changed**
>
> | Area | Before | After |
> |------|--------|-------|
> | Projects | old MSBuild + `packages.config` | SDK-style `.csproj`, `net8.0` |
> | Web framework | ASP.NET MVC 5 (`System.Web`, `Global.asax`) | ASP.NET Core MVC (`Program.cs`, middleware) |
> | ORM | Entity Framework 6.1.3 | EF Core 8 (SQLite, cross-platform, with migrations) |
> | DI | Autofac 3.5 | built-in ASP.NET Core DI |
> | Mapping | AutoMapper 4.2 | AutoMapper 14 |
> | Logging | log4net | Microsoft.Extensions.Logging |
> | Config | `web.config` | `appsettings.json` |
> | GenericServices | EF6-only NuGet package | in-repo `GenericServices`/`GenericLibsBase` compatibility port on EF Core 8 |
> | Tests | NUnit 2.6 (.NET 4.5.1) | NUnit 4 (`net8.0`) |
>
> **Prerequisites:** [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0).
>
> **Build, test and run:**
> ```bash
> dotnet build SampleWebApp.sln
> dotnet test SampleWebApp.sln
> dotnet run --project SampleWebApp
> ```
> On first run the app creates and seeds a local SQLite database (`SampleWebAppDb.db`) via
> EF Core migrations. The connection string and host type live in
> `SampleWebApp/appsettings.json`.
>
> **CI:** `.github/workflows/ci.yml` builds and tests the solution on .NET 8.
>
> **Remaining risks / review notes:**
> - The default database provider is now **SQLite** for cross-platform demo use; the original
>   used SQL Server LocalDB. Production SQL Server compatibility is not re-validated.
> - EF Core validation/tracking/cascade behaviour can differ subtly from EF6.
> - AutoMapper is pinned to **14.0.0** (last MIT-licensed line); a single, scoped NuGet audit
>   suppression is applied for advisory `GHSA-rvv3-g6hj-g44x`.
> - The legacy test suite (which asserted EF6/Autofac/MVC5 internals that no longer exist) was
>   replaced with focused regression tests covering the preserved CRUD/service-layer behaviour.
>
> ---

SampleMvcWebApp is a ASP.NET MVC5 web site designed to show number of useful methods for building enterprise
 grade web applications using ASP.NET MVC5 and Entity Framework 6. 
The code for this sample MVC web application, and the associated 
[GenericServices Framework](https://github.com/JonPSmith/GenericServices) are both an open source project 
by [Jon Smith](http://www.thereformedprogrammer.net/about-me/) 
under the [MIT licence](http://opensource.org/licenses/MIT).

This code is available as a [live web site](http://samplemvcwebapp.net/) which includes explanations 
of the code - see an example of this on the [Posts code explanation](http://samplemvcwebapp.net/Posts/CodeView) page.

The GenericService Framework is available on [GitHub](https://github.com/JonPSmith/GenericServices) and soon via NuGet (when the release is stable).

**GenericServices is now available on NuGet.**
See [NuGet Package Page](https://www.nuget.org/packages/GenericServices/) for more details.

**An additinal, more complex example is now available.** 
Visit [Complex.SampleMvcWebApp](http://complex.samplemvcwebapp.net/) to see more.


The specific features in the code in this example are:

### 1. Simple, but robust database services

Database accesses are normally a big part of enterprise systems build with APS.NET MVC. 
However, my experience is that creating these services in a robust and comprehensive form can lead to 
a lot of repetative code that does the same thing, but for different data. 
My aim has been to produce a generic framework that handles most of the cases, and is 
easily extensible when special handling is required. Examples of there use on this web site are:

 - See normal, synchronous access using a DTO for shaping in the [Posts Controller](https://github.com/JonPSmith/SampleMvcWebApp/blob/master/SampleWebApp/Controllers/PostsController.cs)
 - See new EF6 async access using a DTO for shaping in the [PostsAsync Controller](https://github.com/JonPSmith/SampleMvcWebApp/blob/master/SampleWebApp/Controllers/PostsAsyncController.cs)
 - See normal, synchronous access directly via data class in the [Tags Controller](https://github.com/JonPSmith/SampleMvcWebApp/blob/master/SampleWebApp/Controllers/TagsController.cs)
 - See new EF6 async access directly via data class in the [TagsAsync Controller](https://github.com/JonPSmith/SampleMvcWebApp/blob/master/SampleWebApp/Controllers/TagsAsyncController.cs)

### 1. Use of Dependency Injection

The GenericService framework is designed specifically to work with Dependency Injection (DI). 
DI is used throughout this web site, but specific examples are:

 - Inserting the required services into a controller by action parameter injection.
 - DI is also used for creating the GenericService etc. See Code Explanation for more information.

Note that the SampleMvcWebApp uses AutoFac dependency injection framework, 
but the framework allows you to replace AutoFac with your own favourite DI tool.
