#!/bin/bash
# Seeds the deterministic "Medium" data set through the app's own /Posts/Reset action (so the schema is
# whatever the app's ORM created), then confirms row counts via the LB.
set -euo pipefail
LB="${1:-http://localhost:8000}"
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 20 "$LB/healthz" || true)
  [ "$code" = "200" ] && break
  echo "[seed] waiting for LB health ($code) $i"; sleep 3
done
code=$(curl -s -o /dev/null -w '%{http_code}' -L --max-time 120 "$LB/Posts/Reset" || true)
if [ "$code" != "200" ]; then echo "[seed] FAIL: /Posts/Reset -> HTTP $code"; exit 1; fi
num=$(curl -s --max-time 30 "$LB/Posts/NumPosts" | grep -o 'total number of Posts is [0-9]*' | grep -o '[0-9]*$' || echo "?")
echo "[seed] OK: posts=$num"
