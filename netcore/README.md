# SampleWebApp — .NET 6 migration (NET-1 / NET-12)

This directory contains the **.NET 6** port of `SampleMvcWebApp` and its
testing / quality-assurance infrastructure. It exists alongside the original
MVC5 / .NET Framework 4.5.1 solution at the repository root so the two can
evolve independently during the phased migration.

## Projects

| Project                                          | Purpose                                            |
|--------------------------------------------------|----------------------------------------------------|
| `src/SampleWebApp`                               | Migrated ASP.NET Core 6 MVC web app                |
| `tests/SampleWebApp.UnitTests`                   | xUnit unit tests (controller + model)              |
| `tests/SampleWebApp.IntegrationTests`            | HTTP-level endpoint tests via `TestServer`         |
| `tests/SampleWebApp.PerformanceTests`            | BenchmarkDotNet micro-benchmarks                   |

Solution file: `SampleWebApp.NetCore.sln`.

## Prerequisites

- [.NET 6 SDK](https://dotnet.microsoft.com/download/dotnet/6.0)

## Quick start

```bash
dotnet build netcore/SampleWebApp.NetCore.sln -c Release
dotnet test  netcore/tests/SampleWebApp.UnitTests/SampleWebApp.UnitTests.csproj -c Release
dotnet test  netcore/tests/SampleWebApp.IntegrationTests/SampleWebApp.IntegrationTests.csproj -c Release
dotnet run  --project netcore/src/SampleWebApp -c Release
```

See [`docs/testing.md`](./docs/testing.md) for the full testing guide and
[`docs/test-results.md`](./docs/test-results.md) for the baseline results
captured when the framework was established.
