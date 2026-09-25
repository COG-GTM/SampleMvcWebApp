# Modernization Verify Report — <app> (<legacy runtime> → <target runtime>)

**Migration PR:** <plugin PR URL> · **Infra PR:** <infra PR URL> · **Session:** <Devin session URL> · **Date:** <yyyy-mm-dd>
**Verdict:** ☐ GO ☐ GO with conditions ☐ NO-GO

## 1. Inventory (from live discovery — `demo/dependency-graph.json`, generated <timestamp>)

| Type | Name / ARN | Key attributes | Owner |
|---|---|---|---|
| alb | | dns_name | |
| listener | | port | |
| target_group | | port, health_check_path, healthy targets | |
| ec2 / ecs | | instance_type / task def, runtime_image | |
| security_group | | ingress ports + sources | |
| route53_record | | name → target | |
| secret | | key_name (value **not** recorded) | |
| database | | engine, host:port, consumers | |
| ecr_repository | | image tags | |

Attach `demo/dependency-graph.png`.

## 2. Impact matrix — app ↔ infra ↔ DB (`tools/discover/diff.py`)

| # | Attribute | Live infra (before) | Migrated code declares | Status before | Fix (commit) | Status after |
|---|---|---|---|---|---|---|
| 1 | runtime base image | | | DRIFT | | MATCH |
| 2 | health check path | | | | | |
| 2 | app listen port | | | | | |
| 3 | DB secret / config key | | | | | |
| 4 | DB driver/dialect vs stored procedure consumer | | | | | |
| 5 | firewall / SG / DNS entry | | | | | |
| 6 | under-load NFR | | | | | |

Paste the raw `diff.py` output for before and after in a collapsed block.

## 3. Database dependencies

| Consumer | Objects used | Change in migration | Verified how |
|---|---|---|---|
| app (ORM) | tables, join tables | column / mapping changes | `make verify` smoke |
| `reporting/report.sh` | `dbo.usp_PostSummaryByBlog` | | `docker compose run reporting` output |
| stored procedures / views | | | |

## 4. NFR before / after (20 VUs, 60 s, via load balancer)

| Metric | Legacy baseline (`demo/BASELINE.json`) | Migrated — first run | Migrated — after fix | Gate | Pass |
|---|---|---|---|---|---|
| p50 ms | | | | — | |
| p95 ms | | | | ≤ 1.5× baseline (<p95_ms_max>) | |
| p99 ms | | | | ≤ 1.5× baseline (<p99_ms_max>) | |
| error rate | | | | < 1 % | |
| throughput req/s | | | | ≥ 0.8× baseline (informational) | |
| app memory RSS | | | | informational | |

Root cause of the regression (if any): <pool size / caching / logging …>. Fix commit: <sha>.

## 5. Observability deltas

| Item | Before (legacy) | After (migrated) |
|---|---|---|
| app `/metrics` target | DOWN (IIS app exposes none) | |
| Grafana dashboard panels added / changed | | |
| Datadog tags (`DD_SERVICE`, `DD_ENV`, `DD_VERSION`) | | |
| Log format / location | | |

Attach Grafana screenshots before and after.

## 6. CI

| Job | Result | Link |
|---|---|---|
| build + unit tests | | |
| container + infra/ + `make verify` | | |
| actionlint | | |

## 7. Residual risks & follow-ups

- <risk> — likelihood / impact — owner — mitigation
- Shared-resource changes that still need human approval: <DNS zone / secret rename / schema>

## 8. Rollback

Steps to return traffic to the legacy target group / image, and the data implications (schema changes, if any).

## 9. Evidence

- Frontend screen recording (Home → Posts → Blogs → Tags → About) — <attachment>
- `make verify` output — <attachment>
- `compare.py` output — <attachment>
