SampleMvcWebApp
===============

## 🚀 .NET 6 Migration Progress

**Current Status**: Foundation Phase Complete ✅

This repository is currently undergoing migration from .NET Framework 4.5.1 to .NET 6. The migration is being done incrementally to maintain stability and allow for thorough testing at each phase.

### ✅ Completed (Phase 1 - Foundation)
- **New .NET 6 Project**: Created `SampleWebApp.Core` project with modern SDK-style format
- **Project Structure**: Established standard .NET 6 folder structure (Controllers/, Views/, Models/, wwwroot/)
- **Configuration**: Migrated from Web.config to appsettings.json with environment-specific configurations
- **Hosting Model**: Implemented minimal hosting pattern with modern ASP.NET Core middleware pipeline
- **Build Verification**: Project builds successfully and runs locally
- **Environment Support**: Development, Staging, and Production environment configurations

### 🔄 In Progress / Next Steps
- Migrate existing controllers and views from `SampleWebApp` to `SampleWebApp.Core`
- Update Entity Framework 6 to Entity Framework Core
- Migrate dependency injection from AutoFac to built-in .NET 6 DI container
- Port GenericServices framework to work with .NET 6
- Update authentication and authorization mechanisms
- Migrate static assets and client-side resources

### 📁 Project Structure
```
SampleMvcWebApp/
├── SampleWebApp/          # Original .NET Framework 4.5.1 MVC5 application
├── SampleWebApp.Core/     # New .NET 6 Web Application (migration target)
├── DataLayer/             # Shared data layer (to be migrated to EF Core)
├── ServiceLayer/          # Shared service layer
├── BizLayer/              # Business logic layer
└── Tests/                 # Test projects
```

### 🔗 Related Work
- **Ticket**: NET-15 - Create .NET Core 6 Web Application Project
- **Pull Request**: [#1](https://github.com/COG-GTM/SampleMvcWebApp/pull/1)

---

## About the Original Application

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
