#!/bin/bash
# make verify — proves the deployed app and the infrastructure it runs on still agree.
#  1. static runtime-contract checks (code vs terraform/Dockerfile/nginx/secrets/firewall/dns)
#  2. runtime checks through the load balancer (health, smoke, reporting consumer, DB schema, metrics)
#  3. load test gated against demo/BASELINE.json
# Every check runs (no early exit) and the summary table lists every FAIL; exit 1 if any failed.
set -uo pipefail
cd "$(dirname "$0")/.."
LB="${1:-http://localhost:${LB_PORT:-8000}}"
ROOT=..
COMPOSE="docker compose --env-file secrets/.env -f docker-compose.yml"
SKIP_LOAD="${SKIP_LOAD:-0}"

declare -a RESULTS; FAILED=0
pass() { RESULTS+=("PASS | $1 | $2"); echo "  [PASS] $1: $2"; }
fail() { RESULTS+=("FAIL | $1 | $2"); echo "  [FAIL] $1: $2"; FAILED=$((FAILED+1)); }
section() { echo; echo "== $1 =="; }

# ---------- discover the app's actual runtime contract from the code -----------------------------
CSPROJ=$ROOT/SampleWebApp/SampleWebApp.csproj
APP_TFM=$(grep -o '<TargetFramework[^>]*>[^<]*' "$CSPROJ" | head -1 | sed 's/.*>//')
case "$APP_TFM" in
  v4.*)      APP_FAMILY=netframework-4.x; APP_OS=windows; APP_PORT=80 ;;
  net8*)     APP_FAMILY=dotnet-8;         APP_OS=linux;   APP_PORT=8080 ;;
  net[0-9]*) APP_FAMILY=dotnet-${APP_TFM#net}; APP_FAMILY=${APP_FAMILY%%.*}; APP_OS=linux; APP_PORT=8080 ;;
  *)         APP_FAMILY=unknown; APP_OS=unknown; APP_PORT=0 ;;
esac
# Kestrel port override if the code/compose sets one
p=$(grep -rhoE 'ASPNETCORE_HTTP_PORTS[=:" ]+[0-9]+' $ROOT/Dockerfile docker-compose.yml 2>/dev/null | grep -oE '[0-9]+$' | head -1); [ -n "$p" ] && APP_PORT=$p
if [ -f $ROOT/SampleWebApp/Program.cs ] && grep -q 'MapHealthChecks' $ROOT/SampleWebApp/Program.cs; then
  APP_HEALTH=$(grep -oE 'MapHealthChecks\("[^"]+"' $ROOT/SampleWebApp/Program.cs | head -1 | sed 's/.*("//;s/"//')
elif grep -q 'ActionResult Health()' $ROOT/SampleWebApp/Controllers/HomeController.cs 2>/dev/null; then
  APP_HEALTH=/Home/Health
else
  APP_HEALTH="(none)"
fi
if [ -f $ROOT/SampleWebApp/Program.cs ] && grep -q 'GetConnectionString("SampleWebAppDb")' $ROOT/SampleWebApp/Program.cs; then
  APP_DB_ENV=ConnectionStrings__SampleWebAppDb
elif grep -q 'name="SampleWebAppDb"' $ROOT/SampleWebApp/Web.config 2>/dev/null; then
  APP_DB_ENV=SAMPLEWEBAPP_DB_CONNECTION
else
  APP_DB_ENV="(unknown)"
fi
echo "app runtime contract (from code): tfm=$APP_TFM family=$APP_FAMILY os=$APP_OS port=$APP_PORT health=$APP_HEALTH db_env=$APP_DB_ENV"

# ---------- infra pins ---------------------------------------------------------------------------
tfdefault() { awk -v v="$1" '$0 ~ "variable \""v"\"" {f=1} f && /^ *default/ {gsub(/.*= *"?|"$/,""); print; exit}' terraform/variables.tf; }
TF_FAMILY=$(tfdefault app_runtime_family); TF_IMAGE=$(tfdefault app_runtime_image); TF_OS=$(tfdefault app_os)
TF_PORT=$(tfdefault app_port); TF_HEALTH=$(tfdefault app_health_path); TF_DB_ENV=$(tfdefault app_db_connection_env)
DOCKERFILE_BASE=$(grep -E '^ARG RUNTIME_IMAGE=' $ROOT/Dockerfile | head -1 | cut -d= -f2-)
NGINX_UPSTREAM=$(grep -oE 'server +[a-zA-Z0-9.-]+:[0-9]+' nginx/conf.d/samplewebapp.conf | head -1 | awk '{print $2}')
NGINX_HOST=${NGINX_UPSTREAM%%:*}; NGINX_PORT=${NGINX_UPSTREAM##*:}
NGINX_HEALTH=$(grep -A3 'location = /healthz' nginx/conf.d/samplewebapp.conf | grep -oE 'proxy_pass http://samplewebapp[^;]*' | sed 's|proxy_pass http://samplewebapp||')

section "1. runtime pins (terraform / Dockerfile) vs code"
if [ "$TF_FAMILY" = "$APP_FAMILY" ] && [ "$TF_OS" = "$APP_OS" ]; then
  pass runtime-pin "terraform app_runtime_family=$TF_FAMILY os=$TF_OS matches code ($APP_TFM)"
else
  fail runtime-pin "code targets $APP_TFM ($APP_FAMILY/$APP_OS) but terraform/variables.tf pins app_runtime_family=$TF_FAMILY os=$TF_OS image=$TF_IMAGE"
fi
case "$APP_FAMILY:$DOCKERFILE_BASE" in
  netframework-4.x:*dotnet/framework/aspnet*|dotnet-8:*dotnet/aspnet:8*) pass dockerfile-base "Dockerfile RUNTIME_IMAGE=$DOCKERFILE_BASE matches $APP_FAMILY" ;;
  *) fail dockerfile-base "Dockerfile RUNTIME_IMAGE=$DOCKERFILE_BASE cannot host $APP_FAMILY ($APP_TFM)" ;;
esac

section "2. load balancer health contract (nginx) vs code"
if [ "$NGINX_PORT" = "$APP_PORT" ] && [ "$TF_PORT" = "$APP_PORT" ]; then
  pass lb-upstream-port "nginx upstream $NGINX_UPSTREAM and terraform app_port=$TF_PORT match app listen port $APP_PORT"
else
  fail lb-upstream-port "app listens on $APP_PORT ($APP_FAMILY) but nginx upstream is $NGINX_UPSTREAM and terraform app_port=$TF_PORT"
fi
if [ "$NGINX_HEALTH" = "$APP_HEALTH" ] && [ "$TF_HEALTH" = "$APP_HEALTH" ]; then
  pass lb-health-path "nginx /healthz -> $NGINX_HEALTH matches app health endpoint"
else
  fail lb-health-path "nginx /healthz probes $NGINX_HEALTH (terraform app_health_path=$TF_HEALTH) but the app exposes $APP_HEALTH"
fi

section "3. secrets / config keys vs code"
if grep -q "^$APP_DB_ENV=" secrets/.env.example && grep -qE "^\s+$APP_DB_ENV:" docker-compose.yml && [ "$TF_DB_ENV" = "$APP_DB_ENV" ]; then
  pass secrets-key "app reads $APP_DB_ENV; present in secrets/.env.example, docker-compose.yml app env and terraform"
else
  have=$(grep -oE '^(SAMPLEWEBAPP_DB_CONNECTION|ConnectionStrings__[A-Za-z]+)=' secrets/.env.example | tr -d = | paste -sd, -)
  fail secrets-key "app reads connection string from $APP_DB_ENV but secrets/.env.example defines [$have], compose app env has $(grep -qE "^\s+$APP_DB_ENV:" docker-compose.yml && echo it || echo 'no such key'), terraform app_db_connection_env=$TF_DB_ENV"
fi

section "4. firewall allowlist vs listening ports"
if grep -qE "^ALLOW .*-> +app +tcp/$APP_PORT\b" firewall/allowlist.rules; then
  pass firewall-app-port "firewall/allowlist.rules allows app tcp/$APP_PORT"
else
  allowed=$(grep -E '^ALLOW .*-> +app ' firewall/allowlist.rules | grep -oE 'tcp/[0-9]+' | paste -sd, -)
  fail firewall-app-port "app listens on tcp/$APP_PORT but firewall/allowlist.rules only allows app [$allowed] — traffic from the LB is denied"
fi
if grep -qE "^ALLOW .*-> +lb +tcp/80\b" firewall/allowlist.rules; then pass firewall-lb "lb tcp/80 allowlisted"; else fail firewall-lb "lb tcp/80 not allowlisted"; fi

section "5. dns entries vs nginx upstream"
if grep -qE "\s$NGINX_HOST(\s|$)" dns/hosts; then
  pass dns-upstream "$NGINX_HOST resolves via dns/hosts ($(grep -E "\s$NGINX_HOST(\s|$)" dns/hosts | awk '{print $1}'))"
else
  fail dns-upstream "nginx upstream host $NGINX_HOST has no entry in dns/hosts"
fi

# ---------- runtime -----------------------------------------------------------------------------
section "6. runtime: containers"
APP_STATE=$($COMPOSE ps --format '{{.Service}} {{.State}} {{.Health}}' 2>/dev/null | awk '$1=="app"{print $2"/"$3}')
if [ "$APP_STATE" = "running/healthy" ]; then pass app-container "app container running/healthy"; else
  fail app-container "app container state: ${APP_STATE:-not running} — last log lines:"$'\n'"$($COMPOSE logs --tail=8 --no-log-prefix app 2>/dev/null | sed 's/^/        /')"
fi

section "7. runtime: health via load balancer ($LB)"
LB_HEALTH_CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$LB/healthz" || echo 000)
if [ "$LB_HEALTH_CODE" = "200" ]; then pass lb-health "GET $LB/healthz -> 200"; else fail lb-health "GET $LB/healthz -> HTTP $LB_HEALTH_CODE (nginx upstream $NGINX_UPSTREAM$NGINX_HEALTH)"; fi

section "8. runtime: smoke test via load balancer"
smoke_fail=0
for path in / /Posts /Blogs /Tags /Posts/NumPosts; do
  code=$(curl -s -o /tmp/verify_body -w '%{http_code}' --max-time 30 "$LB$path" || echo 000)
  if [ "$code" = "200" ]; then echo "     200 $path"; else echo "     $code $path"; smoke_fail=1; fi
done
num=$(curl -s --max-time 30 "$LB/Posts/NumPosts" | grep -o 'total number of Posts is [0-9]*' | grep -o '[0-9]*$' || echo 0)
if [ $smoke_fail -eq 0 ] && [ "${num:-0}" -gt 0 ]; then pass smoke "all pages 200, NumPosts=$num"; else fail smoke "page(s) not 200 or NumPosts=${num:-0}"; fi

section "9. runtime: database dependency (reporting consumer + stored procedure)"
rep=$($COMPOSE exec -T reporting bash /report.sh once 2>&1); rc=$?
if [ $rc -eq 0 ]; then pass reporting-consumer "$(echo "$rep" | tail -1)"; else fail reporting-consumer "reporting/report.sh once exit $rc:"$'\n'"$(echo "$rep" | sed 's/^/        /')"; fi
cols=$($COMPOSE exec -T -e Q="SET NOCOUNT ON; SELECT STRING_AGG(name, ',') FROM sys.columns WHERE object_id = OBJECT_ID('dbo.TagPosts')" db bash -c '/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -h -1 -W -d SampleWebAppDb -Q "$Q"' 2>/dev/null | tr -d '\r' | grep -v '^$' | head -1)
if echo "$cols" | grep -q 'Tag_TagId' && echo "$cols" | grep -q 'Post_PostId'; then
  pass db-schema-contract "dbo.TagPosts columns [$cols] match usp_PostSummaryByBlog"
else
  fail db-schema-contract "dbo.TagPosts columns are [${cols:-<table missing>}] but infra/db/01-schema.sql usp_PostSummaryByBlog joins on Tag_TagId/Post_PostId"
fi

section "10. observability: LB request metrics reach Prometheus"
val=""
for _ in $(seq 1 12); do  # prometheus scrape interval + exporter tail lag right after `make up`
  val=$(curl -s --max-time 10 "http://localhost:${PROM_PORT:-9091}/api/v1/query?query=sum(lb_http_response_count_total)" | grep -oE '"value":\[[^,]+,"[0-9.]+"' | grep -oE '[0-9.]+"$' | tr -d '"')
  [ -n "$val" ] && [ "${val%.*}" -gt 0 ] 2>/dev/null && break
  curl -s -o /dev/null "$LB_URL/healthz" || true; sleep 5
done
if [ -n "$val" ] && [ "${val%.*}" -gt 0 ] 2>/dev/null; then pass prometheus-lb-metrics "lb_http_response_count_total=$val"; else fail prometheus-lb-metrics "no LB request metrics in Prometheus (value='${val:-}')"; fi

section "11. load test gated against demo/BASELINE.json"
if [ "$SKIP_LOAD" = "1" ]; then
  RESULTS+=("SKIP | loadtest | SKIP_LOAD=1")
elif [ "$LB_HEALTH_CODE" != "200" ]; then
  fail loadtest "skipped: LB health failing, nothing to load test"
else
  if bash scripts/loadtest.sh verify "${VUS:-20}" "${DURATION:-60s}"; then
    pass loadtest "$(python3 -c "import json;d=json.load(open('../load/out/verify.json'));print(f\"p50={d['p50_ms']} p95={d['p95_ms']} p99={d['p99_ms']} err={d['error_rate']} within gates\")")"
  else
    fail loadtest "$(python3 -c "import json;d=json.load(open('../load/out/verify.json'));print(f\"p50={d['p50_ms']} p95={d['p95_ms']} p99={d['p99_ms']} err={d['error_rate']} thresholds={d['thresholds']}\")" 2>/dev/null || echo 'k6 run failed')"
  fi
fi

echo; echo "================ make verify summary ================"
printf '%s\n' "${RESULTS[@]}" | awk -F' \\| ' '{printf "  %-4s %-22s %s\n", $1, $2, substr($3,1,140)}'
echo "====================================================="
if [ $FAILED -gt 0 ]; then echo "make verify: $FAILED check(s) FAILED"; exit 1; fi
echo "make verify: all checks passed"
