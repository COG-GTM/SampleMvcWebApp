# Modernization Verify [v1]

## Overview
Infra-aware Assess → Plan → Execute → Verify → Complete for an application whose *code* has already been
migrated by the code-transformation plugin (open PR on `plugin/migration`). The plugin only sees the repo;
this playbook covers everything that requires the running infrastructure: load balancer, DNS, firewall,
secrets, database consumers, NFR baseline vs post-migration, dashboards, and CI until green.

## What's Needed From User
- Repository + the plugin's migration branch / PR (default `plugin/migration`)
- Where the legacy app runs today: AWS (`Project=modernization-demo` tag, region) and/or the `infra/` compose stack
- The legacy NFR baseline (`demo/BASELINE.json`) — or permission to capture one with `make baseline`
- NFR gates if different from the baseline file (`gates_for_migration`)

Only ever create the TODO list for the current phase.

<phase name="Assess" id="1">
## Assess — discover the live footprint and diff it against the migrated code

1. `aws sts get-caller-identity`; stop and report the exact failing call if credentials are missing.
2. Run `python3 tools/discover/discover.py --project <tag> --track <track> --out demo` (ELBv2, target groups,
   EC2/ECS, security groups, Route53, Secrets Manager, RDS, ECR) → `demo/dependency-graph.{json,md,png}`.
3. Check out the migration branch and run `python3 tools/discover/diff.py --graph demo/dependency-graph.json --repo .`.
4. Trace every DB consumer: the app's ORM mapping *and* non-app consumers (`reporting/`, stored procedures in `infra/db`).
5. Read `demo/BASELINE.json`; note the p95/p99/error-rate gates.
6. Post the impact matrix (app ↔ infra ↔ DB) as a comment on the plugin PR.

<verification>
- Dependency graph rendered from live cloud APIs (not from the repo) and committed under demo/
- Impact matrix lists every DRIFT row with the affected infra resource ARN/name and the code file that declares the new value
- Every DB consumer outside the app (reporting job, stored procedure) is listed with the columns/tables it depends on
- Legacy NFR baseline located and its gates recorded
</verification>
</phase>

<phase name="Plan" id="2">
## Plan — open the infra PR stacked on the plugin PR

1. Create a branch from the migration branch; open a **draft PR** targeting it (stacked), titled `infra: <app> .NET 8 cutover`.
2. In the PR description write the plan as a checklist, one item per DRIFT row: Dockerfile/terraform runtime pin,
   nginx + target-group health path/port, secret/config key rename, firewall + DNS entries, DB mapping/stored-proc fix,
   NFR gate. Link the plugin PR and the impact matrix.
3. Mark anything that changes a shared resource (DB schema, DNS zone, secret name) as **requires human approval** in the plan.

<verification>
- Draft PR exists, is stacked on the plugin PR, and its checklist has one entry per DRIFT row from Assess
- Shared-resource changes are flagged for human approval
- No infrastructure has been changed yet
</verification>
</phase>

<phase name="Execute and Verify" id="3">
## Execute → Verify — fix, deploy to infra/, measure against the baseline, iterate until green

1. Apply the checklist items as commits on the infra PR (each commit message contains `feature` or `bug`).
2. `make up && make verify` on the compose stack; fix until health (via nginx), smoke, and the DB consumer pass.
3. `make loadtest` and `python3 infra/scripts/compare.py demo/BASELINE.json load/out/loadtest.json`; open Grafana
   (`samplewebapp-nfr` dashboard) and compare p95/p99/error-rate against the legacy baseline.
4. If the NFR gate fails, find the regression (pooling, caching, logging), fix, and re-run `make loadtest` until within gates.
5. Push; watch CI (`modernization` workflow) and iterate until every job is green.
6. **Frontend verification (mandatory):** with the stack up, open `http://localhost:8000/` in a browser, navigate Home →
   Posts → Blogs → Tags → About, and record the screen. Confirm pages render with seeded data and no errors.
   Attach the recording and a Grafana screenshot to the PR.

<verification>
- `make verify` passes on the migrated branch (health via nginx, smoke, reporting consumer, load gate)
- p95/p99/error rate within the gates in demo/BASELINE.json; compare.py output posted on the PR
- CI is green on the infra PR
- Screen recording of the frontend (Home, Posts, Blogs, Tags, About) attached, showing no negative impact
- Grafana screenshot with post-migration data attached
</verification>
</phase>

<phase name="Complete" id="4">
## Complete — Verify report

1. Fill `demo/VERIFY-REPORT-TEMPLATE.md`: inventory, impact matrix (before/after), NFR before/after table,
   observability deltas (dashboard panels / Datadog tags changed), residual risks, rollback.
2. Commit the report to the infra PR as `demo/VERIFY-REPORT.md`, attach it (plus the graph PNG, Grafana screenshot and
   recording) directly in the session message so the user can read it without opening the repo, and mark the PR ready for review.
3. If AWS was used for Assess, confirm whether the footprint should remain; if not, `make aws-down` and confirm zero tagged resources.

<verification>
- VERIFY-REPORT.md committed with every section of the template filled from real command output
- PR marked ready for review; description ends with `Devin-Org: engineering`
- AWS footprint state (kept or destroyed with zero tagged resources) stated in the final message
</verification>
</phase>

## Specifications
- Never edit the migrated application code beyond what a DRIFT row requires; the plugin owns the code transform.
- Every finding must cite the live resource (ARN/name) *and* the repo file — never one without the other.
- Baseline comparison rule: post-migration p95/p99 within 1.5× baseline; error rate < 1%.

## Advice and Pointers
- `make help` in the repo root lists all targets; `demo/RESET.md` resets the stack between attempts.
- The stored procedure `dbo.usp_PostSummaryByBlog` and `reporting/report.sh` are the cross-component DB consumers.
- Grafana: http://localhost:3000 (anonymous viewer), Prometheus: http://localhost:9091.

## Forbidden Actions
- Do not push to `master`.
- Do not change the legacy baseline file `demo/BASELINE.json`.
- Do not delete or rename shared resources (DNS zone, secret, DB) without the human approval flagged in Plan.
- Do not disable a failing check or raise an NFR gate to make verify pass.
