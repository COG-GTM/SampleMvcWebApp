# Test Results — NET-12

Baseline results captured while setting up the testing framework for the
.NET 6 migration. Reproduce with the commands in [`testing.md`](./testing.md).

## Unit tests (`SampleWebApp.UnitTests`)

- **13 / 13 tests passing**
- Duration: ~1.2 s
- Framework: xUnit 2.4 on .NET 6.0

Breakdown:

| Suite                              | Tests |
|------------------------------------|-------|
| `HomeControllerTests`              | 6     |
| `InternalsInfoTests`               | 3     |
| `InternalsInfoProviderTests`       | 4     |

## Coverage

Collected with Coverlet (msbuild integration), excluding `Program.cs` and
`*.cshtml` views per the project's `coverlet.runsettings`.

| Module        | Line | Branch | Method |
|---------------|------|--------|--------|
| `SampleWebApp`| 100% | 100%   | 100%   |

Per-file highlights:

| File                                  | Line rate |
|---------------------------------------|-----------|
| `Controllers/HomeController.cs`       | 100%      |
| `Models/InternalsInfo.cs`             | 100%      |

Exceeds the NET-12 acceptance criterion of **&gt;90% coverage** on
`HomeController`.

## Integration tests (`SampleWebApp.IntegrationTests`)

- **10 / 10 tests passing**
- Duration: ~1.5 s
- Host: `Microsoft.AspNetCore.Mvc.Testing` `WebApplicationFactory<Program>`

All `HomeController` endpoints (`/`, `/Home`, `/Home/Index`, `/Home/About`,
`/Home/Contact`, `/Home/Internals`, `/Home/CodeView`) return `200 OK` with
`Content-Type: text/html` and non-empty bodies. Content assertions cover the
About page description message and the Internals page runtime metrics block.
`/Home/DoesNotExist` correctly returns `404 Not Found`.

## Performance benchmarks (`SampleWebApp.PerformanceTests`)

The benchmark project ships with `HomeControllerBenchmarks` (one benchmark per
action method, `Index` as the `Baseline`) and `InternalsInfoBenchmarks`.
BenchmarkDotNet discovers all 6 benchmarks:

```
SampleWebApp.PerformanceTests.HomeControllerBenchmarks.Index
SampleWebApp.PerformanceTests.HomeControllerBenchmarks.About
SampleWebApp.PerformanceTests.HomeControllerBenchmarks.Contact
SampleWebApp.PerformanceTests.HomeControllerBenchmarks.Internals
SampleWebApp.PerformanceTests.HomeControllerBenchmarks.CodeView
SampleWebApp.PerformanceTests.InternalsInfoBenchmarks.CreateInternalsInfo
```

Full benchmark runs are intentionally not part of CI (they take minutes and
are sensitive to the host machine). They are intended as a nightly /
on-demand comparison harness: once the legacy MVC5 controller is retired,
the numbers produced here become the official baseline used when reviewing
future performance-sensitive PRs.

Sanity-check invocation (fast, just enumerates the benchmarks):

```bash
dotnet run --project netcore/tests/SampleWebApp.PerformanceTests -c Release -- --list flat
```
