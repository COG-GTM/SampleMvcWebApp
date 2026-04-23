# Testing Framework & Quality Assurance (.NET 6 migration)

This document describes the testing framework put in place for the
`SampleWebApp` **.NET 6 migration** (see JIRA `NET-12`). The framework lives
entirely under `netcore/` and is independent of the legacy MVC5 `Tests` project
so both can coexist during the migration.

## Layout

```
netcore/
├── SampleWebApp.NetCore.sln
├── coverlet.runsettings
├── src/
│   └── SampleWebApp/                    # Migrated ASP.NET Core 6 MVC app
├── tests/
│   ├── SampleWebApp.UnitTests/          # xUnit + Moq + FluentAssertions
│   ├── SampleWebApp.IntegrationTests/   # WebApplicationFactory<Program>
│   └── SampleWebApp.PerformanceTests/   # BenchmarkDotNet benchmarks
└── docs/
    └── testing.md                       # This file
```

## Stack

| Concern               | Tool                                                    |
|-----------------------|---------------------------------------------------------|
| Test runner           | [xUnit](https://xunit.net/) 2.4                         |
| Mocking               | [Moq](https://github.com/moq/moq) 4.18                  |
| Assertions            | [FluentAssertions](https://fluentassertions.com/) 6.12  |
| Web host for tests    | `Microsoft.AspNetCore.Mvc.Testing` 6.0                  |
| Code coverage         | [Coverlet](https://github.com/coverlet-coverage/coverlet) 3.1 |
| Performance / bench   | [BenchmarkDotNet](https://benchmarkdotnet.org/) 0.13    |

## Running the tests

From the repository root:

```bash
# Restore & build everything
dotnet build netcore/SampleWebApp.NetCore.sln -c Release

# Run unit tests with coverage (Cobertura report at tests/SampleWebApp.UnitTests/coverage.cobertura.xml)
dotnet test netcore/tests/SampleWebApp.UnitTests/SampleWebApp.UnitTests.csproj \
    -c Release \
    /p:CollectCoverage=true \
    /p:CoverletOutputFormat=cobertura \
    '/p:Include="[SampleWebApp]*"' \
    '/p:ExcludeByFile="**/Program.cs,**/*.cshtml,**/obj/**/*.cs"'

# Run integration tests (boots the app in-process)
dotnet test netcore/tests/SampleWebApp.IntegrationTests/SampleWebApp.IntegrationTests.csproj -c Release

# Run performance benchmarks
dotnet run --project netcore/tests/SampleWebApp.PerformanceTests -c Release
```

Alternatively, use the runsettings file for `dotnet test --collect:"XPlat Code Coverage"`:

```bash
dotnet test netcore/SampleWebApp.NetCore.sln \
    --collect:"XPlat Code Coverage" \
    --settings netcore/coverlet.runsettings
```

## What is covered

### Unit tests — `SampleWebApp.UnitTests`

`HomeControllerTests`:

- Constructor argument validation (null provider throws)
- `Index`, `Contact`, `CodeView` return the default `ViewResult`
- `About` sets `ViewBag.Message` to the expected string
- `Internals` resolves an `InternalsInfo` model from the injected
  `IInternalsInfoProvider` and surfaces every metric onto the view

`InternalsInfoTests` / `InternalsInfoProviderTests`:

- Constructor argument validation
- All four counter properties are populated from the provider
- The default (parameterless) constructor exercises the real provider
- Each concrete provider method returns sane, runtime-independent values

### Integration tests — `SampleWebApp.IntegrationTests`

`HomeControllerEndpointTests` uses `WebApplicationFactory<Program>` to boot
the real app in-process and hits every endpoint over HTTP:

- `GET /`, `/Home`, `/Home/Index`, `/Home/About`, `/Home/Contact`,
  `/Home/Internals`, `/Home/CodeView` — each returns `200 OK` and `text/html`
- `GET /Home/About` contains the description message rendered from the view
- `GET /Home/Internals` contains all four runtime-metric labels
- `GET /Home/DoesNotExist` returns `404 Not Found`

### Performance benchmarks — `SampleWebApp.PerformanceTests`

`HomeControllerBenchmarks` measures every action method in isolation
(`Index` as the `Baseline`). `InternalsInfoBenchmarks` measures the per-call
cost of assembling an `InternalsInfo` snapshot. Benchmarks run with
`[MemoryDiagnoser]` enabled so allocations are reported alongside timings.

## Coverage expectations

The acceptance criteria for `NET-12` require **&gt;90% coverage on
`HomeController`**. The current suite achieves **100% line, branch, and method
coverage** on both `Controllers/HomeController.cs` and `Models/InternalsInfo.cs`.

`Program.cs` (the ASP.NET Core bootstrap / DI wiring) and all Razor
`.cshtml` views are excluded from coverage measurement since they are exercised
by the integration tests rather than unit tests.

## CI integration

The `dotnet test` commands above emit a `coverage.cobertura.xml` report that
can be consumed by any CI coverage tool (Codecov, Coveralls, Azure Pipelines,
ReportGenerator, etc.). A suggested CI pipeline looks like:

1. `dotnet restore netcore/SampleWebApp.NetCore.sln`
2. `dotnet build netcore/SampleWebApp.NetCore.sln -c Release --no-restore`
3. `dotnet test` for the unit + integration projects with coverage collection
4. Fail the build if the `HomeController` line-rate drops below 0.9
5. (Optional) run the benchmark project as a nightly job and archive the
   BenchmarkDotNet artifacts to track perf regressions across commits.
