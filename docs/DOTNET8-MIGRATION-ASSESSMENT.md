# .NET 8 Migration Assessment — SampleMvcWebApp

Status: assessment + first migrated slice (see "Slice 1" below).
Toolchain used for the migrated projects: .NET SDK 8.0.425 on Linux. `global.json` constrains
the build to the .NET 8 SDK (`8.0.100` with `rollForward: latestFeature`), so any installed 8.0.x
feature band is accepted and .NET 9/10 SDKs are not; it does not pin an exact patch.

## 1. Inventory

| Project | Type | Target | Packages | .cs files | Notes |
| --- | --- | --- | --- | --- | --- |
| `DataLayer` | class library | net4.5.1 | 8 (packages.config) | 11 | EF6 `DbContext`, entities, XML seed loader |
| `BizLayer` | class library | net4.5.1 | 4 (packages.config) | 3 | Autofac module + startup wiring only |
| `ServiceLayer` | class library | net4.5.1 | 9 (packages.config) | 11 | DTOs on top of GenericServices |
| `SampleWebApp` | ASP.NET MVC5 web app | net4.5.1 | 45 (packages.config) | 20 | `System.Web`, Razor v3, OWIN, SignalR 2, Identity 2 |
| `Tests` | class library (NUnit 2.6.3) | net4.5.1 | 21 (packages.config) | 23 | Integration-style, needs a real SQL Server database |

Cross-cutting usage counts (files containing the namespace):
`System.Web` 16, `GenericServices` 23, `System.Data.Entity` (EF6) 8, `Autofac` 7.

All five projects are legacy (non-SDK) `.csproj` files with `packages.config`, hint-path
`<Reference>` elements and an explicit `<Compile Include=...>` list.

## 2. Blockers, ranked

1. **`System.Web` / ASP.NET MVC 5 (`SampleWebApp`)** — no .NET 8 equivalent. Controllers,
   filters, model binders, bundling (`Microsoft.AspNet.Web.Optimization`), OWIN startup and
   ASP.NET Identity 2 all have to be rewritten against ASP.NET Core MVC and
   `Microsoft.AspNetCore.Identity`. This is the largest single chunk of work.
2. **GenericServices 1.0.9** — the CRUD/DTO framework the ServiceLayer is built on is
   .NET Framework only. The maintained successor (`EfCore.GenericServices`) has a different
   API surface, so `ServiceLayer` is a rewrite rather than a retarget.
3. **Entity Framework 6** — EF 6.4+ runs on .NET Core/.NET 8 (`net standard 2.1`), so
   `SampleWebAppDb` can be lifted with modest changes; `DbConfiguration`/
   `SqlAzureExecutionStrategy` and `Database.SetInitializer` behave differently and the
   `DbEntityValidationResult` override in `SampleWebAppDb.ValidateEntity` does not exist in
   EF Core. Moving to EF Core is a second, separable decision.
4. **DelegateDecompiler / DelegateDecompiler.EntityFramework 0.18** — the computed-property
   to SQL translation used by the DTO layer. Needs the EF Core flavoured package, and each
   `[Computed]` property must be re-verified against generated SQL.
5. **NUnit 2.6.3 test project** — the runner is unsupported on .NET 8 and the tests are
   integration tests bound to a SQL Server instance. New work should land on xunit with
   unit-testable seams.
6. **`packages.config` everywhere** — blocks transitive restore, central version management
   and `dotnet` CLI builds. Conversion to `PackageReference` is a prerequisite for everything
   else and is safe to do incrementally.
7. **No Web Forms in this solution** — worth stating explicitly, because it removes the most
   expensive category of ASP.NET 4.8 migration work. (Where a Web Forms estate does exist, the
   only realistic paths are Blazor or an MVC/Razor Pages rewrite; nothing in this repo needs it.)

## 3. Phased plan

**Phase 0 — build hygiene (no behaviour change).**
Convert each `packages.config` to `PackageReference`, move each legacy `.csproj` to SDK-style
while still targeting `net481`, and get the whole solution building from the `dotnet` CLI on
Windows. Multi-target `net481;net8.0` where the code allows.

**Phase 1 — domain and seed data (this PR).**
Extract the framework-free core of `DataLayer` (entities, change-tracking base class, XML seed
loader) into an SDK-style `net8.0` library with xunit coverage. Nothing outside the new
projects changes, so the .NET Framework solution keeps building as-is.

**Phase 2 — persistence.**
Port `SampleWebAppDb` onto EF 6.4 targeting `net8.0` (smallest diff) or EF Core 8 (better long
term). Re-implement `ValidateEntity`'s Tag-slug uniqueness rule as an explicit check or a
unique index, and replace `Database.SetInitializer` with migrations. Validate against the
existing seed XML.

**Phase 3 — services.**
Replace GenericServices with either `EfCore.GenericServices` or hand-written service classes
behind the same interfaces the controllers use, one DTO group at a time (Blog, Post, Tag).
AutoMapper stays but the configuration API changes (static `Mapper` → `IMapper`).

**Phase 4 — web.**
Stand up an ASP.NET Core 8 host, port Razor views (`@Html` mostly survives; bundling, OWIN
startup, SignalR 2 → SignalR Core and Identity 2 → ASP.NET Core Identity do not), and move DI
from Autofac modules to `IServiceCollection` (Autofac itself has a .NET 8 adapter if the module
structure is worth keeping).

**Phase 5 — tests and cutover.**
Move the NUnit suite to xunit against the ported stack, run both stacks side by side against
the same database, then retire the .NET Framework projects.

Ordering rationale: phases 1–3 are all independently shippable and leave the running MVC5 app
untouched, which is what makes the effort financeable — the risky Phase 4 cutover happens once
the domain and services are already proven on .NET 8.

## 4. Slice 1 — what was actually migrated in this PR

New projects (added, nothing deleted or retargeted):

- `src/DataLayer.Domain/DataLayer.Domain.csproj` — SDK-style, `net8.0`, no `packages.config`,
  no package references at all. Contains the ported `Blog`, `Post`, `Tag`,
  `TrackUpdate` and the `LoadDbDataFromXml` seed reader, under their original namespaces
  (`DataLayer.DataClasses.Concrete`, `DataLayer.Startup.Internal`) so that callers compile
  unchanged when the legacy library is retired.
- `tests/DataLayer.Domain.Tests/DataLayer.Domain.Tests.csproj` — xunit 2.6.6, `net8.0`.
- `SampleWebApp.Net8.sln` — holds only the migrated projects; `SampleWebApp.sln` is untouched.
- `global.json` — constrains the migrated projects to a .NET 8 SDK (8.0.100 or a later 8.0.x band).

The seed XML files are **not** copied: the new project embeds the originals from
`DataLayer/Startup/Internal/` with `LogicalName` set to the manifest names the .NET Framework
build produces, so both builds read the same bytes and the resource lookup strings in existing
code keep working.

Code changes required during the port: removal of one `[assembly: InternalsVisibleTo("Tests")]`
attribute (now declared in the csproj, pointing at the new test project) and its
`System.Runtime.CompilerServices` using. The entity and loader logic is otherwise
character-for-character identical to the .NET Framework source.

### Verification

```
$ dotnet build SampleWebApp.Net8.sln
Build succeeded.  0 Warning(s)  0 Error(s)

$ dotnet test SampleWebApp.Net8.sln
Passed!  - Failed: 0, Passed: 22, Skipped: 0, Total: 22 - DataLayer.Domain.Tests.dll (net8.0)
```

The 22 tests cover the seed-loader expectations carried over from
`Tests/UnitTests/Group01DataLayer/Test10SetupBlogs.cs` (2/3/3 for the simple file, 4/17/8 for
the medium file, the `NullReferenceException` message for a missing resource, tag instance
sharing, content line trimming), the `IValidatableObject` error strings asserted in
`Test13Validation.cs`, the data annotations on all three entities, the `ToString()` formats,
and the `TrackUpdate` timestamp behaviour including its `protected` setter.

### Limits of this evidence — read before quoting the result

- The legacy projects **do not build in this Linux environment** and were not built here.
  `dotnet build DataLayer/DataLayer.csproj` fails with
  `MSB3644: The reference assemblies for .NETFramework,Version=v4.5.1 were not found`, and the
  repo's `packages/` folder is empty, so a Mono build additionally fails on unresolved
  EF6/GenericServices/Autofac references. Verifying the .NET Framework side needs Windows with
  the 4.5.1/4.8 targeting pack and a `nuget restore`.
- Consequently the parity claim is "the ported code reproduces the behaviour the legacy NUnit
  suite asserts", not "both builds were executed and diffed". The legacy assertions were read
  from source, not re-run.
- The database-dependent legacy tests (`Check10BlogsResetSmallOk`, the EF `SaveChanges`
  validation tests) are out of scope for this slice because they exercise EF6, which is
  Phase 2.
- Nothing in `BizLayer`, `ServiceLayer`, `SampleWebApp` or `Tests` was modified, so the MVC5
  application's behaviour is unchanged by this PR.
