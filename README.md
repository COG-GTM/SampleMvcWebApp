SampleMvcWebApp
===============

SampleMvcWebApp is an **ASP.NET Core MVC** web site running on **.NET 10** and **Entity Framework Core 10**,
designed to show a number of useful methods for building enterprise grade web applications.
It was originally written by [Jon Smith](http://www.thereformedprogrammer.net/about-me/) for ASP.NET MVC 5 /
.NET Framework 4.5.1 / EF6 with the [GenericServices](https://github.com/JonPSmith/GenericServices) library, and has
been re-platformed to ASP.NET Core using [EfCore.GenericServices](https://github.com/JonPSmith/EfCore.GenericServices).
Licensed under the [MIT licence](http://opensource.org/licenses/MIT).

See [`MIGRATION_NOTES.md`](MIGRATION_NOTES.md) for the migration gotchas, the replacement chosen for every legacy
dependency, and the cross-layer API.

## Solution layout

| Project | Purpose |
|---|---|
| `DataLayer` | EF Core `SampleWebAppDb` (`DbContextOptions` ctor), entities (`Blog`, `Post`, `Tag`), EF Core migrations, XML seed data |
| `BizLayer` | Business layer placeholder (`AddBizLayer()`) |
| `ServiceLayer` | EfCore.GenericServices DTOs (`ILinkToEntity<>`), `IDetailPostService(Async)`, `AddServiceLayer(...)` DI registration |
| `SampleWebApp` | ASP.NET Core MVC app (`Program.cs` minimal hosting, controllers, Razor views, `wwwroot` static assets) |
| `Tests` | NUnit 4 tests against a real SQL Server database |

## Prerequisites

- [.NET 10 SDK](https://dotnet.microsoft.com/download/dotnet/10.0)
- SQL Server reachable on `localhost,1433` (LocalDB is Windows-only). For local development use Docker:

```bash
docker start mssql 2>/dev/null || docker run -d --name mssql \
  -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=Str0ng!Passw0rd' -e MSSQL_PID=Developer \
  -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

The development connection string lives in `SampleWebApp/appsettings.Development.json`; `appsettings.json` ships with an
empty connection string. Override it with the `ConnectionStrings__SampleWebAppDb` environment variable.

## Build, run, test

```bash
dotnet tool restore     # dotnet-ef
dotnet build
ASPNETCORE_ENVIRONMENT=Development dotnet run --project SampleWebApp --urls http://localhost:5000
dotnet test
```

On startup the app applies the EF Core migrations (`Database.Migrate()`) and seeds the blogs/posts/tags if the database is
empty. The **Posts → Reset** action reloads the seed data.

Adding a migration after a model change:

```bash
dotnet tool run dotnet-ef migrations add <Name> --project DataLayer --startup-project DataLayer
```

## Features demonstrated

### 1. Simple, but robust database services

CRUD is done through EfCore.GenericServices' `ICrudServices` / `ICrudServicesAsync`:

 - Synchronous access using a DTO for shaping in the [Posts Controller](SampleWebApp/Controllers/PostsController.cs)
 - Async access using a DTO for shaping in the [PostsAsync Controller](SampleWebApp/Controllers/PostsAsyncController.cs)
 - Synchronous access directly via the entity class in the [Tags Controller](SampleWebApp/Controllers/TagsController.cs)
 - Async access directly via the entity class in the [TagsAsync Controller](SampleWebApp/Controllers/TagsAsyncController.cs)

### 2. Use of Dependency Injection

Services are registered in ASP.NET Core's built-in container (`builder.Services.AddServiceLayer(...)`) and injected into
controller actions with `[FromServices]`, replacing the MVC 5 `DiModelBinder` / Autofac action-parameter injection.
