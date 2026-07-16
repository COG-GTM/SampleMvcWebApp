SampleMvcWebApp
===============

SampleMvcWebApp is an **ASP.NET Core MVC** web site (targeting **.NET 10**) that demonstrates a
number of useful patterns for building enterprise‑grade web applications using ASP.NET Core MVC and
**Entity Framework Core**.

This project was originally an ASP.NET MVC 5 / .NET Framework 4.5.1 / Entity Framework 6 sample by
[Jon Smith](http://www.thereformedprogrammer.net/about-me/) built around the EF6
[GenericServices](https://github.com/JonPSmith/GenericServices) framework. It has been re‑platformed
to ASP.NET Core / .NET 10 / EF Core using the EF Core successor
[EfCore.GenericServices](https://github.com/JonPSmith/EfCore.GenericServices). It remains open source
under the [MIT licence](http://opensource.org/licenses/MIT).

See [`MIGRATION_NOTES.md`](./MIGRATION_NOTES.md) for the detailed record of what changed during the
migration (the gotchas, the affected files, and the chosen replacement for each legacy dependency).

Solution layout
---------------

| Project        | Purpose                                                                  |
| -------------- | ------------------------------------------------------------------------ |
| `DataLayer`    | EF Core `SampleWebAppDb` DbContext, entities, migrations and XML seeding. |
| `BizLayer`     | Business‑logic layer (DI extension point).                               |
| `ServiceLayer` | `EfCore.GenericServices` DTOs + `IPostCrudHelper`, DI registration.      |
| `SampleWebApp` | ASP.NET Core MVC web front end (minimal hosting `Program.cs`).           |
| `Tests`        | NUnit test project.                                                       |

Technology
----------

- ASP.NET Core MVC on .NET 10 (SDK‑style projects, `Program.cs` minimal hosting, endpoint routing).
- Entity Framework Core 10 with SQL Server, code‑first migrations.
- `EfCore.GenericServices` (`ICrudServices` / `ICrudServicesAsync`) for the generic CRUD/DTO layer.
- Built‑in `Microsoft.Extensions.DependencyInjection` (the legacy Autofac + `DiModelBinder`
  action‑parameter injection was replaced with constructor / `[FromServices]` injection).
- Static assets served from `wwwroot/` (the legacy `System.Web.Optimization` bundling was removed).

Features demonstrated
---------------------

- Synchronous DTO‑shaped access – `PostsController` (`ICrudServices`).
- Asynchronous DTO‑shaped access – `PostsAsyncController` (`ICrudServicesAsync`).
- Direct entity access – `TagsController` / `TagsAsyncController`.
- Dependency injection throughout, including the many‑to‑many Post/Tag "secondary data"
  (blogger dropdown + tags multi‑select) handled by `IPostCrudHelper`.

Running locally
---------------

Prerequisites: the [.NET 10 SDK](https://dotnet.microsoft.com/download) and a reachable SQL Server
instance (LocalDB on Windows, or SQL Server in Docker on Linux/macOS).

1. **Start SQL Server** (example, Docker – Linux/macOS):

   ```bash
   docker run -d --name sqlserver -e "ACCEPT_EULA=Y" \
     -e "MSSQL_SA_PASSWORD=Your_Strong_Passw0rd!" -p 1433:1433 \
     mcr.microsoft.com/mssql/server:2022-latest
   ```

2. **Set the connection string.** The app reads the connection string named `SampleWebAppDb`.
   Put it in `SampleWebApp/appsettings.Development.json`, or override it with an environment
   variable (recommended, keeps secrets out of source):

   ```bash
   export ConnectionStrings__SampleWebAppDb="Server=localhost,1433;Database=SampleWebAppDb;User Id=sa;Password=Your_Strong_Passw0rd!;TrustServerCertificate=True;MultipleActiveResultSets=True"
   ```

   On Windows with LocalDB you can instead use:
   `Server=(localdb)\\mssqllocaldb;Database=SampleWebAppDb;Trusted_Connection=True;MultipleActiveResultSets=True`.

3. **Run the app.** On startup `Program.cs` applies the EF Core migration and seeds the sample
   blogs/posts/tags if the database is empty:

   ```bash
   dotnet run --project SampleWebApp
   ```

   Then browse to the URL shown in the console (e.g. `http://localhost:5080`).

   To create/apply migrations manually you can use the EF Core tools:

   ```bash
   dotnet tool install --global dotnet-ef
   dotnet ef database update --project DataLayer --startup-project SampleWebApp
   ```

Running the tests
-----------------

```bash
dotnet test
```

The tests use an in‑memory SQLite `SampleWebAppDb` so they do not require a running SQL Server.
