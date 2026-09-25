# Reset between demo runs

All commands from the repo root (`make <target>` proxies to `infra/Makefile`).

## Local compose stack (Acts 2–4)

| Situation | Command | What it does |
|---|---|---|
| Between acts, keep containers | `make seed` | Re-seeds through the app (`/Posts/Reset`) → exactly 17 posts; deterministic data set |
| Fresh stack | `make reset && make up` | Drops all containers + volumes (SQL data, Grafana state, nginx logs, k6 output) and rebuilds |
| Stop only | `make down` | Containers stop, volumes kept |
| Switch legacy ⇄ migrated | `git checkout master` / `git checkout plugin/migration` then `make down && make up` | The app image is built from the checked-out tree (`infra/legacy/Dockerfile.mono` for .NET Framework; set `APP_DOCKERFILE=Dockerfile` for the .NET 8 image) |
| Grafana looks empty | wait ≥30 s after `make loadtest`, then reload with `from=now-15m` | Prometheus scrapes every 5 s; panels use 1 m rate windows |
| Port clash (9090 / 8000 / 3000) | `LB_PORT=8001 PROM_PORT=9092 make up` | Host ports are variables; container ports are fixed |

Never edit `demo/BASELINE.json` during a run — it is the legacy reference. Re-capture only from `master`
with `make baseline` (60 s, 20 VUs) if the host changes materially.

## Planted gaps

The gaps live in the `plugin/migration` branch and in the *unchanged* infra files; there is nothing to "un-fix"
as long as the fix commits made during Act 3 are on a throwaway branch:

```bash
git checkout -b demo/run-$(date +%s) plugin/migration   # Act 3 fixes go here
# ... demo ...
git checkout plugin/migration && make reset && make up   # gaps are back
```

## AWS footprint (Act 1)

| Situation | Command |
|---|---|
| Bring up (≈8–10 min incl. EC2 bootstrap) | `make aws-up` |
| Re-run discovery + impact matrix | `make aws-discover` |
| Re-run k6 against the ALB | `make aws-baseline` |
| List what is tagged | `make aws-status` |
| Tear down and prove it is gone | `make aws-down` (fails if any `Project=modernization-demo,Track=dotnet` resource remains) |

The EC2 host re-seeds SQL Server from `infra/db/01-schema.sql` on boot; to reset app data without a redeploy hit
`http://<alb>/Posts/Reset`. Terraform state is local (`infra/aws/terraform.tfstate`, git-ignored) — if it is lost,
`make aws-status` lists the ARNs to delete by hand.
