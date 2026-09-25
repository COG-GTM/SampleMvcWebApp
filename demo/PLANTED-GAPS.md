# Planted gaps — what the "plugin" migration left behind

Branch `plugin/migration` is a plausible **code-only** ASP.NET MVC5 / .NET Framework 4.5.1 / EF6 →
**ASP.NET Core 8 MVC + EF Core 8** migration, staged as the Claude Code plugin would hand it over.
It was seeded from `origin/devin/1784175515-aspnetcore-net10-migration` (the most complete existing
port) and retargeted from `net10.0`/EF Core 10 to `net8.0`/EF Core 8.

The **code is green**; the **infrastructure declarations were not touched**. Do NOT fix these on this
branch — they are the demo. Devin discovers them in Act 1 (Assess) and fixes them in Act 3 (Execute → Verify).

```
$ dotnet build SampleWebApp.sln -c Release      →  Build succeeded. 0 Error(s)
$ dotnet test  SampleWebApp.sln -c Release      →  Passed! Failed: 0, Passed: 57, Skipped: 0, Total: 57 (net8.0)
$ cd infra && make verify                       →  make verify: 13 check(s) FAILED   (see §3)
```

## 1. The six gaps

| # | Gap | Where the legacy value still lives | What the migrated code now expects | Expected Devin finding |
|---|-----|-----------------------------------|------------------------------------|------------------------|
| 1 | **Runtime base image / Terraform pin** | `Dockerfile` (`RUNTIME_IMAGE=mcr.microsoft.com/dotnet/framework/aspnet:4.8-windowsservercore-ltsc2022`), `infra/terraform/variables.tf` (`app_runtime_family=netframework-4.x`, `os=windows`), `infra/aws/variables.tf` | `net8.0` SDK-style projects → `mcr.microsoft.com/dotnet/aspnet:8.0` (Linux) | Image build fails at the runtime-contract guard (`SampleWebApp targets 'net8.0' but the pinned runtime image is netframework-4.x`); ECR/EC2/ALB target runtime tags still say Windows |
| 2 | **Health-check path + port** | nginx `infra/nginx/conf.d/samplewebapp.conf` (`upstream app.svc.internal:80`, `/healthz → /Home/Health`); ALB target group `health_check.path=/Home/Health`, `port=80` | Kestrel `http://*:8080` (`appsettings.json`), `app.MapHealthChecks("/health")`; `HomeController.Health` was removed | LB health goes red: `GET /Home/Health → 404`, `/health → 200` on :8080 (verified live, §2) |
| 3 | **Renamed config / secret key** | `infra/secrets/.env.example`, compose `app.environment`, Secrets Manager `modernization-demo/dotnet/SAMPLEWEBAPP_DB_CONNECTION` | `builder.Configuration.GetConnectionString("SampleWebAppDb")` → `ConnectionStrings__SampleWebAppDb` | App crashes at startup: `InvalidOperationException: No connection string named 'SampleWebAppDb' was found` (verified live, §2) |
| 4 | **DB dependency: EF Core join-table naming vs stored procedure** | `infra/db/01-schema.sql` `dbo.usp_PostSummaryByBlog` joins `dbo.TagPosts(Tag_TagId, Post_PostId)` (EF6 convention); `reporting/report.sh` EXECs it nightly | EF Core migration `DataLayer/Migrations/…InitialCreate.cs` creates `dbo.PostTag(PostsPostId, TagsTagId)` and `Program.cs` runs `Database.Migrate()` on start | The app's 57 unit tests pass; the *other* consumer (`reporting/`) fails: `Invalid object name 'dbo.TagPosts'` → `verify` check `db-schema-contract` and `reporting-consumer` fail |
| 5 | **Firewall / DNS / SG entry for the new port & name** | `infra/firewall/allowlist.rules` (`app tcp/80` only), `infra/dns/hosts` (`samplewebapp.demo.internal`), app SG ingress `80` from ALB only | `tcp/8080`; `ServiceName=samplewebapp-core` → `samplewebapp-core.demo.internal` | LB → app traffic denied on 8080; renamed service has no DNS record |
| 6 | **NFR regression under load** | `demo/BASELINE.json` gates (`p95 ≤ 20 ms`, `p99 ≤ 30 ms`, `error_rate < 1 %`, 1.5× rule) | `System.Web` output cache / session gone; plain `AddDbContext` (no `AddDbContextPool`), EF Core `LogTo(Console.WriteLine, Information)` logs every SQL command | k6 `make loadtest` breaches the p95/p99 gate; Grafana `samplewebapp-nfr` shows the step change vs the legacy baseline |

Deterministic + reset-able: every gap is a static declaration diff (no randomness); `make reset && make up`
returns the stack to the legacy state, `git checkout plugin/migration` re-introduces all six.

## 2. Impact matrix — `tools/discover/diff.py` against the **live AWS footprint**

Produced with the legacy app deployed on AWS (`make aws-up && make aws-discover`) and this branch checked out:

```
$ python3 tools/discover/diff.py --graph demo/dependency-graph.json --repo .
```

## Impact matrix — live infra vs code (net8.0, dotnet-8.0)

| attribute | infra (live) | code declares | status | affected resources | gap# |
|---|---|---|---|---|---|
| runtime base image | `mcr.microsoft.com/dotnet/framework/aspnet:4.8-windowsservercore-ltsc2022` | `mcr.microsoft.com/dotnet/aspnet:8.0` | **DRIFT** | samplewebapp-dotnet, samplewebapp-dotnet-app, Dockerfile, infra/terraform, infra/aws | 1 |
| health check path | `/Home/Health` | `/health` | **DRIFT** | samplewebapp-dotnet, nginx upstream (infra/nginx) | 2 |
| app listen port | `80` | `8080` | **DRIFT** | samplewebapp-dotnet, samplewebapp-dotnet-app, nginx upstream | 2 |
| firewall / SG ingress for app port | `sg ingress ports ['1433', '80']; compose allowlist app tcp/[80]` | `tcp/8080` | **DRIFT** | samplewebapp-dotnet-app, infra/firewall/allowlist.rules | 5 |
| DB connection secret key name | `SAMPLEWEBAPP_DB_CONNECTION` | `ConnectionStrings__SampleWebAppDb` | **DRIFT** | modernization-demo/dotnet/SAMPLEWEBAPP_DB_CONNECTION, infra/secrets/.env.example, docker-compose app.environment | 3 |
| DB driver / dialect vs stored procedure consumer | `sqlserver-2022 + dbo.usp_PostSummaryByBlog (reporting/)` | `Microsoft.EntityFrameworkCore.SqlServer (EF Core) / Microsoft.Data.SqlClient` | **DRIFT** | db.demo.internal:1433, reporting/report.sh, infra/db/01-schema.sql | 4 |
| DNS record for (renamed) service | `['db.demo.internal', 'samplewebapp.demo.internal']` | `samplewebapp-core.demo.internal` | **DRIFT** | db.demo.internal, samplewebapp.demo.internal, infra/dns/hosts | 5 |
| under-load NFR (System.Web cache/session removed; EF Core pooling) | `baseline demo/BASELINE.json p95/p99` | `no output caching / DbContext pool declared` | **DRIFT** | Grafana samplewebapp-nfr, load/k6-main.js | 6 |

**8 DRIFT / 8 checks**

(The same command on `master` / the main fixture branch prints **0 DRIFT / 8 checks**.)

### Runtime confirmation of gaps 2 and 3 (modern build against the compose SQL Server)

```
$ ASPNETCORE_ENVIRONMENT=Production dotnet SampleWebApp.dll          # legacy env var SAMPLEWEBAPP_DB_CONNECTION only
Unhandled exception. System.InvalidOperationException: No connection string named 'SampleWebAppDb' was found.
  Set it via `dotnet user-secrets set "ConnectionStrings:SampleWebAppDb" "..."` or the
  ConnectionStrings__SampleWebAppDb environment variable.

$ ConnectionStrings__SampleWebAppDb='Server=localhost,1433;…' dotnet SampleWebApp.dll
      Now listening on: http://[::]:8080
$ curl -o /dev/null -w '%{http_code}' http://localhost:8080/Home/Health   → 404      # what nginx + the ALB target group probe
$ curl -o /dev/null -w '%{http_code}' http://localhost:8080/health        → 200      # what the code now exposes
$ curl -o /dev/null -w '%{http_code}' http://localhost:8080/Posts         → 200
```

## 3. Exact `make verify` output on this branch

`make up` refuses to build the image (gap 1) — the runtime-contract guard in `infra/legacy/Dockerfile.mono` trips:

```
$ cd infra && make reset && make up
 => ERROR [app 6/9] RUN set -e; tfm=$(grep -o '<TargetFramework[^>]*>[^<]*' SampleWebApp/SampleWebApp.csproj | head -1 | sed 's/.*>//'); …
0.106  ERROR: SampleWebApp targets 'net8.0' but the pinned runtime image is netframework-4.x (see Dockerfile, infra/terraform/variables.tf).
0.106         Update the runtime base image pin before deploying.
failed to solve: process "/bin/sh -c set -e; tfm=…" did not complete successfully: exit code: 1
make: *** [Makefile:28: up] Error 1
```

```
$ make verify
================ make verify summary ================
  FAIL runtime-pin            code targets net8.0 (dotnet-8/linux) but terraform/variables.tf pins app_runtime_family=netframework-4.x os=windows image=mcr.microsoft.com/dotnet/framework/aspnet:4.8-windowsservercore-ltsc2022
  FAIL dockerfile-base        Dockerfile RUNTIME_IMAGE=mcr.microsoft.com/dotnet/framework/aspnet:4.8-windowsservercore-ltsc2022 cannot host dotnet-8 (net8.0)
  FAIL lb-upstream-port       app listens on 8080 (dotnet-8) but nginx upstream is app.svc.internal:80 and terraform app_port=80
  FAIL lb-health-path         nginx /healthz probes /Home/Health (terraform app_health_path=/Home/Health) but the app exposes /health
  FAIL secrets-key            app reads connection string from ConnectionStrings__SampleWebAppDb but secrets/.env.example defines [SAMPLEWEBAPP_DB_CONNECTION], compose app.environment passes SAMPLEWEBAPP_DB_CONNECTION
  FAIL firewall-app-port      app listens on tcp/8080 but firewall/allowlist.rules only allows app [tcp/80] — traffic from the LB is denied
  PASS firewall-lb            lb tcp/80 allowlisted
  PASS dns-upstream           app.svc.internal resolves via dns/hosts (172.28.0.10)
  FAIL app-container          app container state: not running — last log lines:
  FAIL lb-health              GET http://localhost:8000/healthz -> HTTP 000000 (nginx upstream app.svc.internal:80/Home/Health)
  FAIL smoke                  page(s) not 200 or NumPosts=0
  FAIL reporting-consumer     reporting/report.sh once exit 1: service "reporting" is not running
  FAIL db-schema-contract     dbo.TagPosts columns are [<table missing>] but infra/db/01-schema.sql usp_PostSummaryByBlog joins on Tag_TagId/Post_PostId
  FAIL prometheus-lb-metrics  no LB request metrics in Prometheus (value='')
  FAIL loadtest               skipped: LB health failing, nothing to load test
=====================================================
make verify: 13 check(s) FAILED
make: *** [Makefile:57: verify] Error 1
```

The first six `FAIL` lines are the **static** declaration checks (gaps 1, 1, 2, 2, 3, 5); the rest cascade from the
stack not coming up. If the runtime pin alone is fixed (so the image builds), the stack starts and the static six still
fail while `lb-health`/`smoke` fail on `/Home/Health → 404` and `reporting-consumer`/`db-schema-contract` fail on the
missing `dbo.TagPosts` (gap 4) — that intermediate state is what Devin walks through in Act 3.

## 4. What "fixed" looks like (Act 3 exit criteria)

`make verify` → `0 check(s) FAILED`; `tools/discover/diff.py` → `0 DRIFT / 8 checks`; `make loadtest` within
`demo/BASELINE.json` gates; Grafana `samplewebapp-nfr` p95 back on the legacy line; `reporting/report.sh` → `OK usp_PostSummaryByBlog`.
