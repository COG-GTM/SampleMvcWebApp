# SampleMvcWebApp — .NET Framework 4.5.1 → .NET 8 modernization demo (15 min)

**Story.** The customer's App-Mod platform moves apps through *Submit → Assess → Plan → Execute → Verify → Complete*.
The **code** transform is already done — a Claude Code plugin produced the `plugin/migration` PR (ASP.NET Core 8 MVC + EF Core 8,
compiles, unit tests green). Devin picks up **after** that and does everything that needs the *running infrastructure*:

| # | Gap the platform team named | Where it shows up in this demo |
|---|---|---|
| 1 | Infrastructure discovery (LB, DNS, firewall, secrets) | Act 1a — live AWS dependency graph |
| 2 | Database dependencies | Act 1b — stored proc + `reporting/` consumer in the impact matrix |
| 3 | Cross-component / cross-repo impact | Act 1b — impact matrix app ↔ infra ↔ DB; Act 2 plan |
| 4 | NFR validation post-deployment | Act 3 — k6 vs `demo/BASELINE.json` |
| 5 | Observability baselining | Act 3 — Grafana before/after |
| 6 | Monitoring pipelines, iterating until green | Act 3 — CI red → fix → green |

**Prompt progression.** Acts 1–2 are lightweight **Ask Devin** prompts (discover, scope). Act 3 is the one full Devin session,
kicked off with the *Modernization Verify [v1]* playbook (`demo/PLAYBOOK.md`, saved as `@playbook:playbook-<id>`). Every prompt ≤ 2 sentences.

## Before you go on stage (T-20 min)

```bash
git checkout <main PR branch>            # legacy app + infra/ + demo/
make aws-up                              # ≈8-10 min. Real footprint for Act 1 (see cost below)
make up                                  # local compose stack (nginx, SQL Server, mono/xsp4 legacy app, Prometheus, Grafana, k6)
make verify                              # all green on legacy
open http://localhost:3000/d/samplewebapp-nfr   # Grafana, anonymous viewer
```
Have three tabs ready: the `plugin/migration` PR, Grafana, and a Devin "Ask" window. `demo/RESET.md` covers resets between runs.

Migration source: `plugin/migration` was derived from `origin/devin/1784175515-aspnetcore-net10-migration` (the most complete
existing Core migration on this repo), retargeted from net10 to **net8.0** / EF Core 8. The other `devin/*-net6-*` branches were partial.

---

## Act 1 — Assess (4 min) · gaps 1, 2, 3

### Beat 1a — "Wiz-style" agentless discovery of the live footprint

**Prompt (Ask Devin):**
> The legacy SampleWebApp is running in AWS us-east-1 tagged `Project=modernization-demo,Track=dotnet`. Map its live application dependency graph — LB, target group, compute, security groups, DNS, secrets, database, image — and render it.

**Devin does:** `aws sts get-caller-identity`, then `tools/discover/discover.py` walks ELBv2 → target groups → EC2 → SGs → Route53 →
Secrets Manager → RDS/ECR (read-only APIs, no agents) and writes `demo/dependency-graph.{json,md,png}`:
14 nodes / 19 typed edges (`routes_to`, `health_checks`, `allows_ingress`, `resolves_to`, `reads_secret`, `connects_to_db`, `runs_image`).

**Money moment:** the PNG. Internet → ALB SG(:80) → ALB → listener → target group (`/Home/Health`, :80, *runtime_image = .NET Framework 4.8 Windows*) →
EC2 → secret `SAMPLEWEBAPP_DB_CONNECTION` → `db.demo.internal:1433`. Point at the target-group node: "that is what the plugin never saw."

**Presenter note — the Wiz contrast:** "If you run Wiz you've seen this shape before. Wiz maps the *security graph* to find risk.
Devin maps the *application dependency graph* to plan change — same agentless API walk, different question."

### Beat 1b — diff the live graph against the migrated code

**Prompt (Ask Devin):**
> Compare that dependency graph against what the `plugin/migration` branch declares (health path, port, config keys, DB driver, base image). Give me an impact matrix of what breaks if we deploy it as-is.

**Devin does:** `tools/discover/diff.py --graph demo/dependency-graph.json --repo .` on the migration branch.

**Money moment:** the matrix — **8 DRIFT / 8 checks** (full output in `demo/PLANTED-GAPS.md`):

| attribute | live infra | migrated code | |
|---|---|---|---|
| runtime base image | `dotnet/framework/aspnet:4.8-windowsservercore` | `dotnet/aspnet:8.0` (Linux) | DRIFT |
| health check path | `/Home/Health` | `/health` | DRIFT |
| app listen port | 80 | 8080 | DRIFT |
| SG / firewall ingress | tcp/80 only | tcp/8080 needed | DRIFT |
| DB secret key | `SAMPLEWEBAPP_DB_CONNECTION` | `ConnectionStrings__SampleWebAppDb` | DRIFT |
| DB driver vs stored proc | EF6 join table `TagPosts(TagId,PostId)` | EF Core `PostTag(PostsPostId,TagsTagId)` → `usp_PostSummaryByBlog` + `reporting/` break | DRIFT |
| DNS for renamed service | `samplewebapp.demo.internal` | `samplewebapp-core.demo.internal` | DRIFT |
| under-load NFR | `BASELINE.json` p95 13 ms / p99 20 ms | no `AddDbContextPool`, no output cache, SQL logged per command | DRIFT |

Then: "Note row 6 — nothing in the app's own tests touches that stored procedure. Only the *other* consumer does." (gap 2/3)

**Takeaway:** *Claude Code sees the repo; Devin sees the repo **and** the ALB, SG, DNS record and secret it depends on.*

#### Sidebar — how this integrates with Wiz
If the customer runs Wiz, Devin can ingest the Wiz Security Graph via the Wiz GraphQL API as an additional Assess input: the same
resources, already normalised with exposure and vulnerability context, which lets Devin order the plan by blast radius (e.g. fix the
public-facing ALB rule before the private DNS record). No implementation is needed for this demo — one query is enough to show the shape:

```graphql
query AppDependencies($projectId: String!) {
  graphSearch(projectId: $projectId, first: 100, query: {
    type: ["LOAD_BALANCER"],
    where: { tags: { EQUALS: [{ key: "Project", value: "modernization-demo" }] } },
    relationships: [{ type: [{ type: ROUTES_TO }, { type: PROTECTS }, { type: USES_SECRET }], with: { type: ["VIRTUAL_MACHINE","SECURITY_GROUP","SECRET","DATABASE"] } }]
  }) {
    nodes { entities { id name type properties } }
  }
}
```

---

## Act 2 — Plan (2 min) · gap 3

**Prompt (Ask Devin):**
> Turn that impact matrix into an infra cut-over plan stacked on the plugin's PR, one checklist item per drift row, flagging anything that touches a shared resource. Don't change anything yet.

**Devin does:** opens a draft PR on top of `plugin/migration` whose description is the plan: Dockerfile + `infra/terraform` runtime pin →
nginx/target-group `/health` on :8080 → secret key rename in `secrets/` + compose → firewall + DNS entries → EF Core join-table mapping
for `usp_PostSummaryByBlog` → NFR gate vs `demo/BASELINE.json`. Secret rename and DNS record are flagged *requires approval*.

**Money moment:** the stacked PR view — plugin PR (code) below, Devin PR (infra) above, each checklist line linking back to a matrix row.

**Takeaway:** the plan is derived from the live graph, so nothing that the app depends on is missing from it.

---

## Act 3 — Execute → Verify (7 min) · gaps 4, 5, 6

**Prompt (full Devin session):**
> Execute the cut-over plan on the infra PR using @playbook:playbook-<id>: deploy to `infra/`, verify against `demo/BASELINE.json`, and iterate until CI is green. Attach the Verify report and a screen recording of the app when done.

**Devin does (watch the session; narrate over it):**
1. Fixes rows 1–5, `make up && make verify` → health via nginx, smoke, and `reporting/report.sh` pass.
2. Pushes; CI job *container + infra/ + make verify* runs. First run **red** on the load gate:
   `make loadtest` → `compare.py` shows p95/p99 well above the 1.5× baseline gate (System.Web output cache gone, no DbContext pooling).
3. Grafana `samplewebapp-nfr`: p95 line steps up, app RSS climbs, throughput drops vs the legacy baseline window.
4. Devin adds `AddDbContextPool` + response caching on `/Posts`, re-runs `make loadtest` → within gates; CI **green**.
5. Frontend check: opens `http://localhost:8000`, walks Home → Posts → Blogs → Tags → About, records the screen, attaches it.

**Money moments:** (a) the CI check flipping red → green without a human touching it; (b) Grafana before/after with the baseline
annotation; (c) `compare.py` table with PASS on every gate.

**Takeaway:** the plugin proved the code compiles; Devin proved it runs, at load, on the real topology — and fixed what didn't.

---

## Act 4 — Complete (2 min)

**Prompt (same session, if not already produced):**
> Fill demo/VERIFY-REPORT-TEMPLATE.md from what you measured and attach it here.

**Devin does:** `demo/VERIFY-REPORT.md` — inventory (from the live graph), impact matrix before/after, NFR table, observability deltas
(app `/metrics` target now UP; Datadog tags updated), residual risks (secret rename approval, Windows-only ops runbooks), rollback.

**Money moment:** the impact-matrix column flipping from 7 DRIFT to 0 DRIFT, next to the NFR table with the legacy baseline numbers.

**Takeaway:** Complete means *verified on infrastructure*, not *compiled* — and the evidence is a PR artefact, not a slide.

---

## Legacy baseline you are comparing against (`demo/BASELINE.json`)

Captured on `master` with `make baseline` (20 VUs, 60 s, through nginx, Mono/xsp4 stand-in for IIS):
p50 4.6 ms · p95 13.1 ms · p99 19.9 ms · 513 req/s · 0 % errors. Gates for the migrated app: p95 ≤ 20 ms, p99 ≤ 30 ms, errors < 1 %.
The same script against the real ALB (`make aws-baseline`, `demo/BASELINE.aws.json`): p95 124 ms, p99 206 ms, 173 req/s, 0 % errors
(the ~70 ms floor is the Internet → ALB round trip).

## AWS footprint — cost and teardown

`infra/aws/` (Terraform, default VPC): ALB + listener + target group, 2 SGs, EC2 **t3.medium** running SQL Server 2022 + the legacy
app as containers (t3.small was requested; SQL Server 2022 needs ≥2 GB and the app ~350 MB, so t3.small's 2 GB is not enough — change
`instance_type` if you accept slower boots), private Route53 zone `demo.internal`, Secrets Manager secret, ECR repo.

Approximate cost, us-east-1, on-demand: ALB ≈ $0.023/h + LCU · t3.medium ≈ $0.042/h · EBS 30 GB ≈ $0.003/h · Route53 private zone
$0.50/mo · Secrets Manager $0.40/mo · ECR <$0.01 → **≈ $0.07–0.08 per hour** (≈ $1.80/day). Provisioning ≈ 8–10 min, mostly EC2 bootstrap.

```bash
make aws-down     # terraform destroy + asserts zero resources tagged Project=modernization-demo,Track=dotnet
```

## Timing

| Act | Min | Cumulative |
|---|---|---|
| Setup on stage (tabs, one line of story) | 0:30 | 0:30 |
| 1 Assess (two beats) | 4:00 | 4:30 |
| 2 Plan | 2:00 | 6:30 |
| 3 Execute → Verify | 6:30 | 13:00 |
| 4 Complete | 1:30 | 14:30 |
| Q&A buffer | 0:30 | 15:00 |
