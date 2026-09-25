#!/bin/bash
# Runs k6 through the LB with thresholds derived from demo/BASELINE.json (gates_for_migration).
set -euo pipefail
cd "$(dirname "$0")/.."
LABEL="${1:-loadtest}"; VUS="${2:-20}"; DURATION="${3:-60s}"
BASELINE=../demo/BASELINE.json
if [ -f "$BASELINE" ]; then
  P95=$(python3 -c "import json;print(json.load(open('$BASELINE'))['gates_for_migration']['p95_ms_max'])")
  P99=$(python3 -c "import json;print(json.load(open('$BASELINE'))['gates_for_migration']['p99_ms_max'])")
  ERR=$(python3 -c "import json;print(json.load(open('$BASELINE'))['gates_for_migration']['error_rate_max'])")
  echo "[loadtest] gates from demo/BASELINE.json: p95<${P95}ms p99<${P99}ms error_rate<${ERR}"
else
  P95=1500; P99=3000; ERR=0.01
  echo "[loadtest] no demo/BASELINE.json — using default gates p95<${P95}ms p99<${P99}ms"
fi
export K6_UID="${K6_UID:-$(id -u)}"
docker compose --env-file secrets/.env -f docker-compose.yml run --rm \
  -e LABEL="$LABEL" -e VUS="$VUS" -e DURATION="$DURATION" \
  -e P95_MAX_MS="$P95" -e P99_MAX_MS="$P99" -e ERR_RATE_MAX="$ERR" \
  k6 run /scripts/k6-main.js && rc=0 || rc=$?
if [ -f "$BASELINE" ] && [ -f "../load/out/$LABEL.json" ]; then
  python3 scripts/compare.py "$BASELINE" "../load/out/$LABEL.json" || true
fi
exit $rc
