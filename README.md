SampleMvcWebApp
===============

SampleMvcWebApp is a ASP.NET MVC5 web site designed to show number of useful methods for building enterprise
 grade web applications using ASP.NET MVC5 and Entity Framework 6. 
The code for this sample MVC web application, and the associated 
[GenericServices Framework](https://github.com/JonPSmith/GenericServices) are both an open source project 
by [Jon Smith](http://www.thereformedprogrammer.net/about-me/) 
under the [MIT licence](http://opensource.org/licenses/MIT).

## .NET Core 6 Migration

**NEW: HomeController Migration to .NET Core 6**

This repository now includes a .NET Core 6 version of the HomeController in the `SampleWebApp.Core` project, demonstrating the migration path from ASP.NET MVC 5 (.NET Framework 4.5.1) to .NET Core 6.

### Project Structure

- **SampleWebApp** - Original ASP.NET MVC 5 (.NET Framework 4.5.1) project
- **SampleWebApp.Core** - New .NET Core 6 project with migrated HomeController

### Key Migration Changes

The HomeController migration includes the following updates:

1. **Namespace Updates**: Changed from `System.Web.Mvc` to `Microsoft.AspNetCore.Mvc`
2. **Return Types**: Updated from `ActionResult` to `IActionResult`
3. **Memory Metrics**: Updated `InternalsInfo` model to use .NET Core compatible APIs (`GC.GetGCMemoryInfo()` instead of `PerformanceCounter`)
4. **Views**: Migrated to use tag helpers and .NET Core conventions
5. **Project Structure**: Created with modern .NET Core 6 project structure and configuration

### Running the .NET Core 6 Version

```bash
cd SampleWebApp.Core
dotnet run
```

The .NET Core 6 version includes all HomeController actions:
- **Index**: Welcome page with application information
- **About**: Application description page
- **Contact**: Contact information page
- **Internals**: System metrics display (WorkerThreads, AvailableThreads, Memory usage)
- **CodeView**: Migration explanation and code documentation

This code is available as a [live web site](http://samplemvcwebapp.net/) which includes explanations 
of the code - see an example of this on the [Posts code explanation](http://samplemvcwebapp.net/Posts/CodeView) page.

The GenericService Framework is available on [GitHub](https://github.com/JonPSmith/GenericServices) and soon via NuGet (when the release is stable).

**GenericServices is now available on NuGet.**
See [NuGet Package Page](https://www.nuget.org/packages/GenericServices/) for more details.

**An additinal, more complex example is now available.** 
Visit [Complex.SampleMvcWebApp](http://complex.samplemvcwebapp.net/) to see more.


## Original ASP.NET MVC5 Features

The specific features in the code in the original ASP.NET MVC5 example are:

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
