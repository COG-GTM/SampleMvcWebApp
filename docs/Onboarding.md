# Developer Onboarding — `SampleMvcWebApp` .NET Core 6 side

> First-time setup guide for engineers joining the .NET Upgrade. ~30 minutes
> from clone to first integration test pass. If anything in this guide fails,
> open a PR fixing it — onboarding docs are only useful if they are kept
> current.

This guide gets you productive on the **`netcore/` half** of the repository
(the migrated ASP.NET Core 6 app). The legacy MVC5 app under `SampleWebApp/`
keeps its existing Visual Studio workflow and is documented in the root
`README.md`.

---

## 1. Prerequisites

| Tool | Required version | How to verify |
|------|------------------|---------------|
| .NET SDK | **6.0.x** | `dotnet --version` shows `6.0.*` |
| Git | any recent | `git --version` |
| IDE | Visual Studio 2022 (17.0+), Rider 2022.1+, or VS Code with the C# Dev Kit | open this repo |
| OS  | Windows 10/11, macOS 12+, Ubuntu 20.04+ | `dotnet --info` |

Optional but recommended:

- **Docker Desktop** — for CI parity and the Dockerfile under `netcore/`.
- **SQL Server LocalDB** (Windows) or `mcr.microsoft.com/mssql/server` Docker
  image (Linux/macOS) — needed in Phase 2 when the data layer migrates.

### Pinning the SDK

`global.json` (at the repo root) pins the .NET SDK version used for the
`netcore/` build. If you have several SDKs installed, `dotnet --version`
inside the repo will report the pinned one. If it doesn't, install the exact
version listed in `global.json` from
[dotnet.microsoft.com/download/dotnet/6.0](https://dotnet.microsoft.com/download/dotnet/6.0).

---

## 2. Clone and build

```bash
git clone https://github.com/COG-GTM/SampleMvcWebApp.git
cd SampleMvcWebApp

# Restore packages and build only the new app
dotnet restore netcore/SampleWebApp.NetCore.sln
dotnet build  netcore/SampleWebApp.NetCore.sln -c Debug
```

Expected: `Build succeeded. 0 Warning(s), 0 Error(s)`. If you see warnings
treat them as a blocker — the CI gate uses `/warnaserror`.

---

## 3. Run the app

```bash
dotnet run --project netcore/src/SampleWebApp
```

Open:

| URL                                | Expected |
|------------------------------------|----------|
| `http://localhost:5000/`           | `Home/Index` page renders |
| `http://localhost:5000/Home/About` | "Your application description page." |
| `http://localhost:5000/Home/Contact` | static page |
| `http://localhost:5000/Home/Internals` | runtime metrics (worker threads, available threads, available MB, heap KB) |
| `http://localhost:5000/Home/CodeView`  | introduction to GenericServices |

If HTTPS redirection complains, accept the dev cert:

```bash
dotnet dev-certs https --trust
```

To run under a specific environment:

```bash
ASPNETCORE_ENVIRONMENT=Staging dotnet run --project netcore/src/SampleWebApp
```

---

## 4. Run the tests

```bash
dotnet test netcore/SampleWebApp.NetCore.sln \
  --collect:"XPlat Code Coverage" \
  --settings netcore/coverlet.runsettings
```

Three suites run:

| Project | What it does |
|---------|--------------|
| `SampleWebApp.UnitTests` | xUnit + Moq + FluentAssertions unit tests for controller, model, providers |
| `SampleWebApp.IntegrationTests` | Boots the app in-process via `WebApplicationFactory<Program>`; HTTP assertions on every route |
| `SampleWebApp.PerformanceTests` | BenchmarkDotNet benchmarks (run manually, not in CI) |

Run a single suite:

```bash
dotnet test netcore/tests/SampleWebApp.UnitTests/SampleWebApp.UnitTests.csproj
```

Run a single test by name:

```bash
dotnet test netcore/tests/SampleWebApp.UnitTests --filter "FullyQualifiedName~HomeControllerTests.Index"
```

Full test stack details: [`netcore/docs/testing.md`](../netcore/docs/testing.md).

---

## 5. Editor setup

### Visual Studio 2022

1. **Open**: `netcore/SampleWebApp.NetCore.sln`. Do **not** open
   `SampleWebApp.sln` (root) — that loads the legacy MVC5 solution.
2. Set `netcore/src/SampleWebApp` as the startup project.
3. The debug profile defaults to `https`. Switch to `http` if you don't
   want the dev cert prompt.

### Rider

1. **Open** the `SampleMvcWebApp` folder. Rider detects both solutions —
   pick `netcore/SampleWebApp.NetCore.sln`.
2. Enable "Auto-import" for using statements and "Run with Hot Reload" for
   the run configuration.

### VS Code

1. Install the **C# Dev Kit** extension (Microsoft).
2. Trust the workspace.
3. In the Status Bar, click "Select solution" and choose
   `netcore/SampleWebApp.NetCore.sln`.

A `.vscode/launch.json` for `dotnet run --project netcore/src/SampleWebApp`
will be auto-generated on first F5.

---

## 6. Project tour (5 minutes)

```
netcore/
├── SampleWebApp.NetCore.sln          ← open this one
├── src/SampleWebApp/
│   ├── Program.cs                    ← startup, DI, middleware
│   ├── Controllers/HomeController.cs ← 5 actions, no DI in Phase 1
│   ├── Models/InternalsInfo.cs       ← cross-platform runtime metrics
│   ├── Views/Home/*.cshtml           ← migrated from legacy SampleWebApp/Views/Home
│   ├── wwwroot/                      ← static assets (CSS, JS, fonts, favicon)
│   ├── appsettings*.json             ← replaces Web.config
│   └── SampleWebApp.csproj           ← Microsoft.NET.Sdk.Web, net6.0
├── tests/
│   ├── SampleWebApp.UnitTests/
│   ├── SampleWebApp.IntegrationTests/
│   └── SampleWebApp.PerformanceTests/
├── docs/
│   ├── testing.md                    ← NET-12
│   └── cicd.md                       ← NET-13
├── Dockerfile
├── docker-compose.yml
└── coverlet.runsettings
```

**Where to put new code as Phase 2 / 3 land:**

- New controller → `netcore/src/SampleWebApp/Controllers/<Name>Controller.cs`.
- New model / DTO → `netcore/src/SampleWebApp/Models/`.
- Domain or repository code (Phase 2) → `netcore/src/DataLayer/`,
  `netcore/src/BizLayer/`, `netcore/src/ServiceLayer/` (parallel structure
  to the legacy `*Layer/` directories at the root).
- Service registration → an `*ServiceExtensions.cs` file under
  `Startup/` in the relevant project, called from `Program.cs`.

---

## 7. The legacy app (for reference only)

Phase 1 does **not** modify the legacy MVC5 app. You will rarely need to
build it, but if you do:

- Open `SampleWebApp.sln` (root) with Visual Studio on Windows.
- Install the .NET Framework 4.5.1 targeting pack.
- Build target: `Debug` / `AnyCPU`.

The legacy app cannot be built from Linux/macOS or from `dotnet build` on
the command line. Treat it as a frozen reference until Phase 4.

Root `README.md` documents the legacy app's features (GenericServices,
DTOs, Action Runner, etc.).

---

## 8. Read these next

In priority order:

1. [Phase1-Migration-Guide.md](Phase1-Migration-Guide.md) — patterns and
   procedures you'll reuse in every later phase.
2. [Phase1-Configuration-Migration.md](Phase1-Configuration-Migration.md) —
   `Web.config` ↔ `appsettings.json` mapping.
3. [Phase1-Troubleshooting.md](Phase1-Troubleshooting.md) — error messages
   you'll inevitably see.
4. [CodeReview-Guidelines.md](CodeReview-Guidelines.md) — what we look for
   in PRs.
5. [Development-Workflow.md](Development-Workflow.md) — branching, commits,
   PRs, ticket linkage.
6. [`netcore/docs/testing.md`](../netcore/docs/testing.md) — the test stack
   and coverage expectations.
7. [`netcore/docs/cicd.md`](../netcore/docs/cicd.md) — CI workflows, Docker,
   health checks.

---

## 9. Smoke-test checklist before your first PR

You're set up correctly if **all** of the following work without errors:

- [ ] `dotnet --version` inside the repo reports `6.0.*`.
- [ ] `dotnet build netcore/SampleWebApp.NetCore.sln -c Debug` succeeds with
      0 warnings.
- [ ] `dotnet run --project netcore/src/SampleWebApp` serves
      `http://localhost:5000/` and `/Home/About`, `/Home/Contact`,
      `/Home/Internals`, `/Home/CodeView`.
- [ ] `dotnet test netcore/SampleWebApp.NetCore.sln` is green.
- [ ] `dotnet format netcore/SampleWebApp.NetCore.sln` makes zero changes.
- [ ] You can open the repo in your chosen IDE and step through
      `HomeController.Index` under the debugger.

If any step fails, fix it locally, then update
[Phase1-Troubleshooting.md](Phase1-Troubleshooting.md) with the fix so the
next person has it easier.
