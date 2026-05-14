# Code Review Guidelines — .NET Upgrade

> Applies to every PR that touches `netcore/` or this `docs/` folder.
> The legacy MVC5 app under `SampleWebApp/`, `BizLayer/`, etc. is frozen
> during the strangle — review there is just "should we be touching this at
> all in Phase 1?".

This document is a **checklist**, not a style guide. The intent is that a
reviewer can step through it linearly during a code review and the author
can self-review against it before requesting a review.

---

## 1. Scope and ticket linkage

- [ ] PR title is `[NET-XX] <imperative summary>` matching the Jira ticket.
- [ ] PR description links the Jira ticket.
- [ ] PR description states **why** the change is being made, not just what.
- [ ] PR description lists the migration-pattern decisions used (e.g.
      "uses constructor injection per Phase 1 Migration Guide §4.6").
- [ ] Diff is scoped to one ticket. Multi-ticket PRs are rejected.

## 2. Migration correctness

For controller / model / view migrations specifically:

- [ ] Controller inherits `Microsoft.AspNetCore.Mvc.Controller`, not
      `System.Web.Mvc.Controller`.
- [ ] Action signatures return `IActionResult` (or a specific subtype) —
      never `ActionResult`.
- [ ] No `System.Web.*` namespaces remain in migrated files.
- [ ] No `PerformanceCounter` (or any Windows-only API) without an
      `OperatingSystem.IsWindows()` guard.
- [ ] No `GC.GetTotalMemory(true)` — forcing a full GC in a request path
      is a bug.
- [ ] `Models/` and `Controllers/` namespaces follow the
      `SampleWebApp.<Layer>.<Subnamespace>` convention.
- [ ] Static assets live under `wwwroot/`, not `Content/` or `Scripts/`.
- [ ] Views use tag helpers (`asp-controller`, `asp-action`, `asp-route-*`)
      for new markup; `@Html.*` helpers are acceptable when copied
      verbatim from the legacy view.
- [ ] `_ViewImports.cshtml` exists in `Views/` and registers
      `@addTagHelper *, Microsoft.AspNetCore.Mvc.TagHelpers`.
- [ ] Routes are registered with `MapControllerRoute` — never `app.UseMvc`.

## 3. Configuration

- [ ] No new entries in `Web.config` (the legacy file is frozen).
- [ ] New configuration lives in `appsettings.json` and is read via
      `IOptions<T>` — not `IConfiguration` ad-hoc.
- [ ] Secrets are **not** committed. Use `dotnet user-secrets` or
      environment variables.
- [ ] Per-environment overrides use `appsettings.<env>.json`, never
      `Web.<Env>.config` transforms.

## 4. Dependency injection

- [ ] No Autofac registrations added to the new app in Phase 1.
- [ ] Constructor injection only — no service-locator anti-pattern
      (`HttpContext.RequestServices.GetService<T>()`).
- [ ] Service lifetimes match expectations: `AddScoped` for
      per-request, `AddSingleton` for stateless infra, `AddTransient` for
      lightweight stateless factories. Document non-obvious choices in a
      single comment on the registration line.

## 5. Testing

- [ ] Every public controller action has at least one unit test.
- [ ] Every public route has at least one integration test asserting
      status code and content type.
- [ ] Test names follow `MethodUnderTest_State_ExpectedBehavior`.
- [ ] Tests use FluentAssertions (`x.Should().Be(y)`) — not raw
      `Assert.Equal(...)` — for new test files. Legacy NUnit tests under
      `Tests/` are exempt; do not modify them.
- [ ] No `Thread.Sleep` in tests. Use `await Task.Delay` (which is also
      suspicious in tests — usually a deterministic alternative exists).
- [ ] No tests skipped (`Skip="..."`) without a TODO referencing a Jira
      ticket.
- [ ] Coverage on touched controller / model files is ≥ 90% line.

## 6. Code quality

- [ ] No dead code (unused `using`s, unreachable branches, leftover
      `Console.WriteLine`, debug attributes).
- [ ] No `// TODO` without an associated Jira ticket reference.
- [ ] No commented-out code. Delete it — git remembers.
- [ ] Naming follows .NET conventions: PascalCase types and methods,
      `_camelCase` private fields, `camelCase` locals/parameters.
- [ ] Nullable annotations are honoured — no `!` (null-forgiving) operator
      to silence warnings; fix the root cause.
- [ ] `async` methods end in `Async` and return `Task`/`Task<T>` (or
      `ValueTask` for hot paths). No `async void` outside event handlers.
- [ ] No new dependencies added unless justified in the PR description.
      Each new NuGet package adds attack surface and version drift risk.

## 7. Formatting and lint

- [ ] `dotnet format --verify-no-changes netcore/SampleWebApp.NetCore.sln`
      reports clean.
- [ ] No mixed line endings — `.gitattributes` enforces this, but check
      if you committed from a tool that ignores it.
- [ ] No trailing whitespace.
- [ ] `<PackageReference>` versions match across projects; central package
      management via `Directory.Packages.props` if version drift starts.

## 8. Documentation

- [ ] If the PR establishes a new pattern, the relevant `docs/` file is
      updated in the same PR — not deferred.
- [ ] Public APIs and non-obvious internal methods have XML doc comments.
- [ ] Onboarding-affecting changes (new prereqs, new env vars, new run
      commands) update [Onboarding.md](Onboarding.md).
- [ ] Configuration changes update
      [Phase1-Configuration-Migration.md](Phase1-Configuration-Migration.md).
- [ ] New troubleshooting cases land in
      [Phase1-Troubleshooting.md](Phase1-Troubleshooting.md).

## 9. Security

- [ ] No SQL string concatenation. EF Core parameterised queries only.
- [ ] Antiforgery tokens on every state-changing endpoint (`[HttpPost]`,
      `[HttpDelete]`, etc.).
- [ ] No user input flowing into `Response.Redirect` without
      `Url.IsLocalUrl` validation.
- [ ] Logging never includes PII or secrets (connection strings, tokens,
      Authorization headers).
- [ ] No `dynamic` on data-bound input.
- [ ] If `Autofac` is reintroduced later, it must be the
      `Autofac.Extensions.DependencyInjection` integration — not a parallel
      container.

## 10. Performance

- [ ] No synchronous I/O in async paths (`File.ReadAllText` in an action
      method, `Task.Result`, `Task.Wait`).
- [ ] No `.ToList()` immediately after `IQueryable<T>` unless the result
      is consumed eagerly with intent.
- [ ] No N+1 query patterns in EF Core (Phase 2 onward).
- [ ] Long-running endpoints use `CancellationToken` and pass it through.

## 11. Reviewer's final pass

Before approving:

- [ ] Read the diff top-to-bottom one more time, assuming you've never
      seen the ticket. Does the change still make sense?
- [ ] Do all CI checks pass? Including code-quality (`dotnet format`,
      security audit, build warnings).
- [ ] Is there a single coherent commit message, or has the author left
      "fix lint", "wip", "address review" commits? Prefer a clean rebase
      before merge.
- [ ] Are CodeRabbit / Devin / Graphite bot comments resolved or
      explicitly waived with reasoning?
- [ ] Did the author tick the Jira acceptance-criteria boxes in the PR
      description?

## 12. Author's pre-request-review pass

Run this verbatim before clicking "Ready for review":

```bash
git fetch origin
git diff --merge-base origin/master
dotnet format netcore/SampleWebApp.NetCore.sln
dotnet build  netcore/SampleWebApp.NetCore.sln -c Release /warnaserror
dotnet test   netcore/SampleWebApp.NetCore.sln \
  --collect:"XPlat Code Coverage" \
  --settings netcore/coverlet.runsettings
```

If any step is red, fix before requesting review. Reviewers should not be
the lint runner.
