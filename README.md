SampleMvcWebApp
===============

SampleMvcWebApp is an **ASP.NET Core MVC** web site running on **.NET 10**, designed to show a number of
useful methods for building enterprise grade web applications using ASP.NET Core MVC and **EF Core**.

It was originally written by [Jon Smith](http://www.thereformedprogrammer.net/about-me/) against
ASP.NET MVC 5 / .NET Framework 4.5.1 / Entity Framework 6 and the
[GenericServices](https://github.com/JonPSmith/GenericServices) library, and has since been re-platformed
to ASP.NET Core MVC / .NET 10 / EF Core 10 and
[EfCore.GenericServices](https://github.com/JonPSmith/EfCore.GenericServices).
Both the web app and the GenericServices libraries are open source under the
[MIT licence](http://opensource.org/licenses/MIT).

See [MIGRATION_NOTES.md](MIGRATION_NOTES.md) for the full record of the re-platform: every legacy API that
was removed, the replacement chosen for it, and the gotchas found along the way.

Running it locally
------------------

You need the [.NET 10 SDK](https://dotnet.microsoft.com/download) and a SQL Server the app can reach.
The quickest way to get one is Docker:

```bash
docker run -d --name mssql \
  -e ACCEPT_EULA=Y -e 'MSSQL_SA_PASSWORD=Str0ng!Passw0rd' -e MSSQL_PID=Developer \
  -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

That throwaway development password is the one already in `SampleWebApp/appsettings.Development.json`, so
in the Development environment the app runs with no further configuration:

```bash
dotnet run --project SampleWebApp        # http://localhost:5000
```

The app applies any outstanding EF Core migrations and seeds the blogs/posts/tags data on startup
(`DataLayerInitialise.MigrateAndSeed`), so the site is usable immediately.

In any other environment there is no connection string in `appsettings.json` and the app fails fast at
startup; supply one out of band, for example:

```bash
export ConnectionStrings__SampleWebAppDb="Server=...;Database=SampleWebAppDb;..."
```

The same `SampleWebAppDb` environment variable also overrides the connection string used by
`dotnet ef` (via `SampleWebAppDbDesignTimeFactory`) and by the tests (via `Tests/Helpers/TestDbHelper.cs`,
which uses its own `TestSampleWebAppDb` database).

```bash
dotnet build                             # whole solution
dotnet test                              # 45 tests, needs the SQL Server above
dotnet ef migrations add <Name> --project DataLayer
```

The specific features in the code in this example are:

### 1. Simple, but robust database services

Database access is normally a big part of enterprise systems built with ASP.NET Core.
However, my experience is that creating these services in a robust and comprehensive form can lead to
a lot of repetitive code that does the same thing, but for different data.
The aim has been to produce a generic framework that handles most of the cases, and is
easily extensible when special handling is required. Examples of its use on this web site are:

 - Synchronous access using a DTO for shaping in the [Posts Controller](SampleWebApp/Controllers/PostsController.cs)
 - Async access using a DTO for shaping in the [PostsAsync Controller](SampleWebApp/Controllers/PostsAsyncController.cs)
 - Synchronous access directly via a data class in the [Tags Controller](SampleWebApp/Controllers/TagsController.cs)
 - Async access directly via a data class in the [TagsAsync Controller](SampleWebApp/Controllers/TagsAsyncController.cs)

`EfCore.GenericServices` replaces the thirteen EF6-era service interfaces (`IListService`, `IDetailService`,
`ICreateService`, …) with `ICrudServices`/`ICrudServicesAsync`, and the `EfGenericDto<,>` base class with the
`ILinkToEntity<TEntity>` marker interface. The parts of the old DTOs that could not be expressed that way —
the blogger drop-down and tag multi-select on a Post — live in the hand-written
[`ServiceLayer/PostServices/PostDtoService.cs`](ServiceLayer/PostServices/PostDtoService.cs).

### 2. Use of Dependency Injection

DI is used throughout this web site. The original used Autofac plus a custom `DiModelBinder` that injected
services into action parameters; this version uses the built-in `IServiceCollection` container and
`[FromServices]`:

 - `ServiceLayer.Startup.ServiceLayerServiceExtensions.AddServiceLayer(connectionString)` registers the
   whole stack — `SampleWebAppDb`, the business layer, `ICrudServices`/`ICrudServicesAsync` and the post
   DTO services — and is the single call `Program.cs` makes.
 - Controllers take their services as `[FromServices]` action parameters, which keeps the original
   per-action service style without a custom model binder.
