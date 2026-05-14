# Development Workflow — .NET Upgrade

> Applies to every Jira ticket in the `NET` project that touches
> `SampleMvcWebApp`. Pair with [CodeReview-Guidelines.md](CodeReview-Guidelines.md).

This document captures **how** we ship code during the migration. It is
intentionally short; the rules are easy to follow and there are not many of
them.

---

## 1. Branching

### Naming

```
<author>/<unix-timestamp>-net<ticket>-<short-slug>
```

Examples:

- `alice/1778767055-net11-homecontroller-net6`
- `bob/1779999999-net20-blogs-controller-migration`
- `devin/1778767055-net14-phase1-docs`

Why a timestamp? It prevents collisions when two people work the same
ticket and makes ordering trivial in `git branch --sort=committerdate`.

### Base branch

- Branch from `origin/master`.
- Long-lived feature flags or stacked PRs use Graphite. Do not branch off
  another open feature branch unless the dependency is explicit and tracked
  in Jira.

### Lifetime

- Open a PR within 48 hours of the first commit (early draft is fine).
- Merge or close within two weeks. Long-lived branches drift.
- Delete the branch on merge — GitHub does this automatically when the
  repo setting is on; if your repo fork disables it, do it manually.

---

## 2. Commits

### Message format

```
NET-<ticket>: <imperative summary, ≤ 72 chars>

<wrapped body explaining *why*, not *what*. Optional.>

Refs: NET-<ticket>
```

Examples:

```
NET-14: Document Phase 1 migration patterns

Adds top-level docs/ folder covering the migration guide, configuration
migration, troubleshooting, onboarding, code-review guidelines, and the
team-training agenda. References netcore/docs/{testing,cicd}.md produced
by NET-12 and NET-13 rather than duplicating their content.

Refs: NET-14
```

```
NET-11: Migrate HomeController to .NET Core 6

Drops System.Web.Mvc dependency and ports the five Home actions to
IActionResult. Replaces the Windows-only PerformanceCounter usage in
InternalsInfo with GC.GetGCMemoryInfo() so the model is cross-platform.

Refs: NET-11
```

### Rules

- One logical change per commit. "Fix lint" and "Address review" commits
  should be squashed before merge (Graphite / GitHub squash-merge handles
  this automatically; configure your branch).
- Never commit generated files (bin/, obj/, .vs/, *.user). The repo
  `.gitignore` covers these; double-check before `git add`.
- Never commit secrets. `.env`, `appsettings.Development.json`
  containing real credentials, `*.pfx`, anything with `secret` in the
  filename — all banned.
- Never amend a commit that has been pushed and reviewed. Add a new
  commit instead.

---

## 3. Pull requests

### Title

```
[NET-<ticket>] <imperative summary>
```

Examples:

- `[NET-11] Migrate HomeController to .NET Core 6`
- `[NET-14] Document Phase 1 migration patterns`

### Description template

```markdown
## Summary
What changes and why. 2–5 sentences.

## Jira
[NET-<ticket>](https://cog-gtm.atlassian.net/browse/NET-<ticket>)

## Acceptance criteria
- [ ] (paste from the Jira ticket, ticked where this PR satisfies them)

## How to verify
1. Reproducible steps a reviewer can run locally
2. Expected output / screenshots / log snippets

## Assumptions / decisions
Any non-obvious decision the reviewer should know about.

## Out of scope
What the PR deliberately does not do (and which ticket owns it).
```

### Reviewers

- At least one human reviewer.
- Bot reviewers (CodeRabbit, Devin, Graphite) auto-assign on PR open and
  are not a substitute for human review.

### Draft vs ready

- Open as **draft** while you're still pushing fixups.
- Mark **ready for review** only after running the author's checklist in
  [CodeReview-Guidelines.md §12](CodeReview-Guidelines.md#12-authors-pre-request-review-pass).

### Stacked PRs

For multi-ticket work, prefer **one PR per ticket** stacked via Graphite.
Avoid combining tickets into a single PR — it slows review and makes revert
harder.

---

## 4. CI expectations

Every PR that touches `netcore/` or `docs/` triggers the workflows in
`.github/workflows/`:

| Workflow         | What it does | Must pass before merge |
|------------------|--------------|------------------------|
| `ci.yml`         | Build + unit/integration tests + coverage | Yes |
| `code-quality.yml` | `dotnet format`, NuGet audit, build with `/warnaserror` | Yes |
| `cd.yml`         | Staging/production deploys (post-merge to `master`) | n/a for PR |

Bot CI:

- **CodeRabbit** posts inline review comments. Treat them like a human
  reviewer — resolve or explicitly waive.
- **Devin Review** posts a PR-wide review comment. Same handling.
- **Graphite Diamond** flags risky diffs. Same handling.

CI checks reporting "passed" with bot reviewers does **not** mean the bot
liked the PR. Read the inline comments.

---

## 5. Ticket linkage

### On branch creation

1. Move the Jira ticket to **In Progress** before pushing your first commit.
2. The Devin GTM service account auto-comments the session URL on the
   ticket when working from the playbook.

### On PR open

1. Add the PR URL as a remote link on the ticket (Atlassian MCP
   `addRemoteIssueLink` or via the GitHub-Jira integration if installed).
2. Post a brief comment on the ticket: `PR: <link>`. No body needed.

### On merge

1. Move the ticket to **Done** **after** the PR merges to `master` and
   CD has been observed green on staging. Do **not** mark Done on PR open.
2. If the PR introduces follow-ups, file them as separate tickets linked
   to the parent epic (`NET-1` for Phase 1).

### On revert

If a PR is reverted post-merge:

1. Open a follow-up ticket linked to the original.
2. Re-open or clone the original ticket; do not silently leave it at
   "Done" while the change is no longer in `master`.

---

## 6. Working with the legacy MVC5 app

The legacy app stays frozen during Phase 1–3. The only acceptable diffs
under `SampleWebApp/`, `BizLayer/`, `DataLayer/`, `ServiceLayer/`,
`Tests/`, `Licence.txt`, `packages/` are:

- Build-breaking fixes that block CI.
- Security patches to NuGet packages with public CVEs.

Anything else — including "tiny refactor", "delete obviously dead code",
"upgrade jQuery while we're here" — is rejected. File a separate ticket.

---

## 7. Working with Confluence

The long-form planning artifacts live in Confluence:

- [Phase 1: Foundation - HomeController Migration](https://cog-gtm.atlassian.net/wiki/spaces/NU/pages/19562813)
- [ASP.NET MVC to .NET Core 6 Controller Migration Assessment (parent)](https://cog-gtm.atlassian.net/wiki/spaces/NU/pages/19071008)

Rule of thumb:

- **Repo docs (`docs/`)**: technical reference that a developer touching
  the code needs.
- **Confluence pages**: planning, scope, sign-offs, cross-team
  communication.

When repo docs land on `master`, mirror the new content (or just a link)
to the relevant Confluence page so non-engineers know it exists.

---

## 8. Communication

- Per-PR questions: GitHub PR comments.
- Per-ticket questions: Jira comments.
- Broad architectural questions: `#net-upgrade` Slack channel.
- Cross-team alignment: Confluence + scheduled meeting; not Slack.

Avoid DMs for technical decisions — they don't survive personnel changes.

---

## 9. Cheat sheet

```bash
# Start work on NET-XX
git checkout master
git pull
git checkout -b $USER/$(date +%s)-net<XX>-<slug>

# ...do work...

git status                                    # double-check what's staged
git add <files>                               # never `git add .`
git commit -m "NET-<XX>: <summary>" -m "<body>"
git push -u origin HEAD

# Open PR via gh CLI
gh pr create --fill --draft \
  --title "[NET-<XX>] <summary>" \
  --base master

# Run the full local pipeline
dotnet format netcore/SampleWebApp.NetCore.sln
dotnet build  netcore/SampleWebApp.NetCore.sln -c Release /warnaserror
dotnet test   netcore/SampleWebApp.NetCore.sln \
  --collect:"XPlat Code Coverage" \
  --settings netcore/coverlet.runsettings
```
