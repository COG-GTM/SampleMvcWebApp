SampleMvcWebApp
===============

SampleMvcWebApp is an ASP.NET Core MVC web site, running on .NET 10 with Entity Framework Core 10, designed to show a number of useful methods for building enterprise
 grade web applications. It was originally written for ASP.NET MVC5 / .NET Framework 4.5.1 / Entity Framework 6
 and has been re-platformed - see [MIGRATION_NOTES.md](MIGRATION_NOTES.md) for the migration gotchas and the chosen replacement for each legacy dependency.

### Running locally (.NET 10)

Requirements: [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0) and a SQL Server instance (LocalDB on Windows, or SQL Server in Docker on Linux/macOS).

```bash
docker run -d --name mssql -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=Str0ng!Passw0rd' -e MSSQL_PID=Developer \
  -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
dotnet build
dotnet test
ASPNETCORE_ENVIRONMENT=Development dotnet run --project SampleWebApp --urls http://localhost:5000
```

The connection string `ConnectionStrings:SampleWebAppDb` lives in `SampleWebApp/appsettings.Development.json`.
On startup the app applies the EF Core migrations (`DataLayer/Migrations`) and seeds the database from the XML seed data if it is empty.
To change the model, add a migration with `dotnet tool restore && dotnet ef migrations add <Name> --project DataLayer`.

The code for this sample MVC web application, and the associated 
[EfCore.GenericServices library](https://github.com/JonPSmith/EfCore.GenericServices) (the EF Core successor of [GenericServices](https://github.com/JonPSmith/GenericServices)) are both an open source project 
by [Jon Smith](http://www.thereformedprogrammer.net/about-me/) 
under the [MIT licence](http://opensource.org/licenses/MIT).

This code is available as a [live web site](http://samplemvcwebapp.net/) which includes explanations 
of the code - see an example of this on the [Posts code explanation](http://samplemvcwebapp.net/Posts/CodeView) page.

This version uses the [EfCore.GenericServices NuGet package](https://www.nuget.org/packages/EfCore.GenericServices/).

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
 - See EF Core async access using a DTO for shaping in the [PostsAsync Controller](https://github.com/JonPSmith/SampleMvcWebApp/blob/master/SampleWebApp/Controllers/PostsAsyncController.cs)
 - See normal, synchronous access directly via data class in the [Tags Controller](https://github.com/JonPSmith/SampleMvcWebApp/blob/master/SampleWebApp/Controllers/TagsController.cs)
 - See EF Core async access directly via data class in the [TagsAsync Controller](https://github.com/JonPSmith/SampleMvcWebApp/blob/master/SampleWebApp/Controllers/TagsAsyncController.cs)

### 1. Use of Dependency Injection

The GenericService framework is designed specifically to work with Dependency Injection (DI). 
DI is used throughout this web site, but specific examples are:

 - Inserting the required services into a controller by action parameter injection (`[FromServices]`).
 - DI is also used for creating the GenericService etc. See Code Explanation for more information.

Services are registered with the built-in ASP.NET Core container (`AddDataLayer`, `AddBizLayer`, `AddServiceLayer`)
and injected into controller actions with `[FromServices]`.
