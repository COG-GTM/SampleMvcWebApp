# Phase 1 Team Training — Agenda & Slide Notes

> Target audience: every engineer who will work on the .NET Upgrade beyond
> Phase 1. Total time: **90 minutes**. Format: live session + hands-on lab.
> Pre-reading: [Onboarding.md](../Onboarding.md) and the first half of
> [Phase1-Migration-Guide.md](../Phase1-Migration-Guide.md).

This document is the script for the Phase 1 knowledge-transfer session
mandated by Jira ticket [NET-14](https://cog-gtm.atlassian.net/browse/NET-14).
It doubles as the source-of-truth slide notes — record the session and
attach the recording link in the "Recording" section at the bottom.

---

## Pre-session checklist (presenter)

- [ ] All Phase 1 PRs (NET-10, NET-11, NET-12, NET-13) are merged or at
      least demo-able on a feature branch.
- [ ] Demo machine has the .NET 6 SDK, the repo cloned, and the netcore
      app running on `http://localhost:5000`.
- [ ] [`netcore/docs/testing.md`](../../netcore/docs/testing.md) and
      [`netcore/docs/cicd.md`](../../netcore/docs/cicd.md) are open in
      tabs for live reference.
- [ ] Slack `#net-upgrade` is open for Q&A and parking-lot questions.
- [ ] Recording started (Zoom / Teams / Granola).

---

## Section 1 — Why are we migrating? (10 min)

Speaker notes:

- ASP.NET MVC 5 + .NET Framework 4.5.1 is out of mainstream support.
- Security patches only via paid extended support; no new features.
- Hosting cost: Windows-only IIS vs Linux containers under .NET Core.
- Strategic alignment: rest of the org is on .NET 6 / .NET 8.

Key slide ("Why now"):

> "The legacy stack costs us money and risk. .NET Core 6 gives us
> cross-platform hosting, modern DI/logging/configuration, and a
> supported LTS runtime until November 2024 — at which point we hop to
> .NET 8 in Phase 5."

Audience question to ask:

- "What's one thing the legacy app currently does that worries you about
  the migration?" → write answers in the parking lot, address in Section 5.

---

## Section 2 — The Strangler Fig pattern (10 min)

Show the directory tree from
[Phase1-Migration-Guide.md §2](../Phase1-Migration-Guide.md#2-migration-pattern-strangler-fig).

Speaker notes:

- Both apps live in the same repo. Both build on `master`.
- We migrate one controller at a time. After each merge, the new app
  is functionally a tiny subset; the legacy app stays whole.
- Cut-over is the **last** step, after every controller is migrated and
  the data layer is on EF Core. Not in Phase 1.
- Why this pattern? Reversible at every step. Small PRs, easy reverts,
  no big-bang weekend.

Anti-pattern to call out:

- "Don't refactor the legacy app to make the new app easier." Legacy is
  frozen. Period.

---

## Section 3 — What changed in Phase 1 (15 min, live walk-through)

Walk the audience through the four merged tickets:

### NET-10 — Project scaffolding
- New `netcore/` folder, solution, csproj layout.
- `Microsoft.NET.Sdk.Web` + top-level `Program.cs`.
- `global.json` pinning the .NET 6 SDK.

### NET-11 — HomeController migration
- 5 actions ported: `Index`, `About`, `Contact`, `Internals`, `CodeView`.
- `System.Web.Mvc.ActionResult` → `Microsoft.AspNetCore.Mvc.IActionResult`.
- `InternalsInfo` ported with cross-platform GC API (no `PerformanceCounter`).
- Views moved into `netcore/src/SampleWebApp/Views/Home/`.
- Static assets relocated under `wwwroot/`.

### NET-12 — Testing framework
- xUnit + Moq + FluentAssertions.
- `WebApplicationFactory<Program>` for integration tests.
- BenchmarkDotNet for performance tests.
- Coverage via Coverlet. Phase 1 target ≥ 90% on touched files; the
  actual achievement is 100% on `HomeController` and `InternalsInfo`.

### NET-13 — CI/CD
- Three GitHub Actions workflows: `ci.yml`, `code-quality.yml`, `cd.yml`.
- Docker support: `netcore/Dockerfile`, `netcore/docker-compose.yml`.
- Health-check endpoints: `/health`, `/health/startup`, `/health/live`.
- Per-environment configuration via `appsettings.<env>.json`.

For each ticket, show the actual diff (`git diff --merge-base origin/master`)
in the editor. Don't read code aloud — narrate the **shape** of the change.

---

## Section 4 — The patterns you will reuse (20 min)

These are the deltas every later phase will replay. Reference
[Phase1-Migration-Guide.md §4](../Phase1-Migration-Guide.md#4-migration-patterns-apply-to-every-controller-in-later-phases).

For each pattern, show a before/after on screen:

1. **Controller signature** — `ActionResult` → `IActionResult`,
   `System.Web.Mvc` → `Microsoft.AspNetCore.Mvc`.
2. **ViewBag / ViewData / TempData** — mostly unchanged; flag `TempData`
   needing session.
3. **Cross-platform model APIs** — `PerformanceCounter` → `GC.GetGCMemoryInfo`,
   `GC.GetTotalMemory(true)` → `GC.GetTotalMemory(false)`.
4. **Views** — `_ViewImports.cshtml`, tag helpers, asset paths.
5. **Static assets** — `wwwroot/` is the only served root.
6. **DI** — built-in container, constructor injection. Autofac defers to
   Phase 2 if needed.
7. **Configuration** — `Web.config` → `appsettings.json`, `IOptions<T>`.
8. **Logging** — log4net → built-in `ILogger<T>`.
9. **Routing** — `App_Start/RouteConfig` → `app.MapControllerRoute`.
10. **Bundling** — gone in Phase 1; LibMan / npm if reintroduced later.

Pause after each pattern: "Any questions on this one before we move on?"

---

## Section 5 — Live troubleshooting drill (15 min)

Open [Phase1-Troubleshooting.md](../Phase1-Troubleshooting.md). Pick three
real symptoms and have the audience diagnose them before revealing the
fix. Suggested picks:

1. `/Home/Internals` throws `PlatformNotSupportedException` on Linux.
2. Integration test fails with `Program is not accessible`.
3. Static files return 404 after migrating views.

For each, ask: "What's the first thing you'd check?" Reveal the fix from
the troubleshooting doc only after the room has guessed.

---

## Section 6 — Hands-on lab (15 min)

Each attendee runs the smoke-test checklist from
[Onboarding.md §9](../Onboarding.md#9-smoke-test-checklist-before-your-first-pr)
on their own machine while the presenter watches the room.

Success criteria:

- App runs on `http://localhost:5000/`.
- All five Home routes respond `200 OK`.
- `dotnet test` is green.

Pair attendees who hit setup issues; capture every new troubleshooting
case into [Phase1-Troubleshooting.md](../Phase1-Troubleshooting.md) as a
follow-up PR.

---

## Section 7 — Workflow and review (10 min)

Walk through:

- [Development-Workflow.md](../Development-Workflow.md) — branching,
  commits, PRs, CI expectations.
- [CodeReview-Guidelines.md](../CodeReview-Guidelines.md) — the checklist
  every reviewer (and author) runs against the diff.
- Bot reviewers (CodeRabbit, Devin, Graphite) — treat as human, resolve
  or explicitly waive.

Make the point: "If you do nothing else, run the author's pre-review
checklist in CodeReview-Guidelines §12 before clicking 'Ready for review'.
Reviewers are not your lint runner."

---

## Section 8 — Q&A and parking lot (5 min)

Address the parking-lot items from Section 1. Anything that can't be
answered live becomes a Slack follow-up — capture it in
`#net-upgrade` and link to the answer here in a follow-up PR.

---

## Post-session checklist (presenter)

- [ ] Upload the recording. Add the link to the **Recording** section below.
- [ ] File any "new troubleshooting case" PRs against
      [Phase1-Troubleshooting.md](../Phase1-Troubleshooting.md).
- [ ] File follow-up Jira tickets for anything from the parking lot.
- [ ] Update the NET-14 ticket: tick the "Team training session conducted"
      and "All team members understand migration process" acceptance
      criteria boxes once attendees confirm.
- [ ] Schedule a 30-minute follow-up retrospective two weeks after the
      first Phase-2-style PR lands, to confirm the patterns from Section 4
      are actually being applied.

---

## Recording

| Session | Date | Recording | Attendees |
|---------|------|-----------|-----------|
| _to fill in after the live session_ | YYYY-MM-DD | (link) | (names) |

---

## Hands-on lab — quick commands

For copy/paste during Section 6:

```bash
git clone https://github.com/COG-GTM/SampleMvcWebApp.git
cd SampleMvcWebApp

dotnet --version                                         # expect 6.0.*
dotnet build netcore/SampleWebApp.NetCore.sln -c Debug   # expect 0 warnings
dotnet test  netcore/SampleWebApp.NetCore.sln \
  --collect:"XPlat Code Coverage" \
  --settings netcore/coverlet.runsettings                # expect all green
dotnet run --project netcore/src/SampleWebApp            # browse to http://localhost:5000/
```
