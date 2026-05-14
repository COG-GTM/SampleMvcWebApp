# SampleMvcWebApp — Documentation

Reference documentation for the ASP.NET MVC 5 → ASP.NET Core 6 migration of
`SampleMvcWebApp` (Jira project [.NET Upgrade](https://cog-gtm.atlassian.net/browse/NET-1)).

The docs in this folder cover **Phase 1: Foundation — HomeController Migration**
(Jira epic [NET-1](https://cog-gtm.atlassian.net/browse/NET-1)). Later phases
will extend this index.

## Phase 1 documentation index

| Doc | What it covers | Primary Phase 1 ticket |
|-----|----------------|------------------------|
| [Phase1-Migration-Guide.md](Phase1-Migration-Guide.md) | Migration overview, target architecture, repo layout, Strangler Fig approach, controller / view / model migration patterns, build & run procedures | [NET-14](https://cog-gtm.atlassian.net/browse/NET-14) |
| [Phase1-Configuration-Migration.md](Phase1-Configuration-Migration.md) | `Web.config` → `appsettings.json`, connection strings, log4net → `ILogger`, Autofac → ASP.NET Core DI, environment-specific configuration | [NET-14](https://cog-gtm.atlassian.net/browse/NET-14) |
| [Phase1-Troubleshooting.md](Phase1-Troubleshooting.md) | Common issues encountered during HomeController migration and their fixes | [NET-14](https://cog-gtm.atlassian.net/browse/NET-14) |
| [Onboarding.md](Onboarding.md) | Prerequisites, clone-to-running-app walkthrough, debug profiles, project tour for the .NET Core side | [NET-14](https://cog-gtm.atlassian.net/browse/NET-14) |
| [CodeReview-Guidelines.md](CodeReview-Guidelines.md) | PR review checklist for controller migrations in Phase 2+ | [NET-14](https://cog-gtm.atlassian.net/browse/NET-14) |
| [Development-Workflow.md](Development-Workflow.md) | Branching, commits, PRs, ticket linkage, CI expectations | [NET-14](https://cog-gtm.atlassian.net/browse/NET-14) |
| [training/Phase1-Training-Agenda.md](training/Phase1-Training-Agenda.md) | Team training session agenda, slide notes, hands-on exercises | [NET-14](https://cog-gtm.atlassian.net/browse/NET-14) |

## Companion documentation produced by sibling tickets

The Phase 1 work is split across multiple tickets. Where another ticket already
owns a specific area, this index links to its documentation rather than
duplicating it:

| Area | Document | Ticket |
|------|----------|--------|
| Testing framework, coverage, integration tests | [`netcore/docs/testing.md`](../netcore/docs/testing.md) | [NET-12](https://cog-gtm.atlassian.net/browse/NET-12) |
| CI/CD, Docker, health checks, environment config | [`netcore/docs/cicd.md`](../netcore/docs/cicd.md) | [NET-13](https://cog-gtm.atlassian.net/browse/NET-13) |
| Project / EF Core scaffolding decisions | [`netcore/`](../netcore/) project structure | [NET-10](https://cog-gtm.atlassian.net/browse/NET-10) |
| HomeController, views, static assets migration | [`netcore/src/SampleWebApp/`](../netcore/src/SampleWebApp/) | [NET-11](https://cog-gtm.atlassian.net/browse/NET-11) |

If a file referenced above does not yet exist on `master`, it is being produced
on the corresponding feature branch and will land as that ticket merges.

## Canonical Phase 1 decisions

These decisions are fixed for Phase 1 and govern every document in this folder.
Phase 2 may revisit them.

| Decision | Value | Source |
|----------|-------|--------|
| Target framework | **.NET 6** | Jira epic [NET-1](https://cog-gtm.atlassian.net/browse/NET-1) |
| Migration pattern | **Strangler Fig** — legacy MVC5 app and new ASP.NET Core 6 app coexist in the same repo until cut-over | Jira epic NET-1 + Phase 1 Confluence |
| New-app layout | `netcore/` at the repo root (sln, src/, tests/, docs/, Dockerfile) | Jira ticket [NET-12](https://cog-gtm.atlassian.net/browse/NET-12) / [NET-13](https://cog-gtm.atlassian.net/browse/NET-13) |
| Legacy app layout | Unchanged — stays under `SampleWebApp/`, `BizLayer/`, `DataLayer/`, `ServiceLayer/`, `Tests/` | Repo `master` |
| First controller migrated | `HomeController` (5 actions: `Index`, `About`, `Contact`, `Internals`, `CodeView`) | Jira ticket [NET-11](https://cog-gtm.atlassian.net/browse/NET-11) |
| DI container | Built-in `Microsoft.Extensions.DependencyInjection`. Autofac may return in a later phase if Phase 2 needs its richer registration features | Phase 1 decision |
| Test stack | xUnit + Moq + FluentAssertions + `Microsoft.AspNetCore.Mvc.Testing` + Coverlet | NET-12 |
| Build / CI | GitHub Actions workflows under `.github/workflows/` (ci.yml, cd.yml, code-quality.yml) | NET-13 |

## Confluence

The longer-form planning artifacts live in Confluence and stay the source of
truth for Phase planning:

- [Phase 1: Foundation - HomeController Migration](https://cog-gtm.atlassian.net/wiki/spaces/NU/pages/19562813)
- [ASP.NET MVC to .NET Core 6 Controller Migration Assessment (parent)](https://cog-gtm.atlassian.net/wiki/spaces/NU/pages/19071008)

When changes land on `master`, mirror the relevant new content back to those
pages and link to the doc file from here.
