#!/bin/bash
# reporting/ — second consumer of SampleWebAppDb. Runs dbo.usp_PostSummaryByBlog (a "nightly digest")
# and writes /out/post-summary.json + /out/last-run.json. Loop mode = cron stand-in; `once` = verify hook.
set -uo pipefail
SQLCMD=/opt/mssql-tools18/bin/sqlcmd
HOST="${DB_HOST:-db}"
OUT="${REPORT_OUT:-/out}"
INTERVAL="${REPORT_INTERVAL_SECONDS:-30}"
mkdir -p "$OUT"

run_once() {
  local ts rows rc
  ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  rows=$($SQLCMD -S "$HOST" -d SampleWebAppDb -U reporting_svc -P "$REPORTING_DB_PASSWORD" -C -b -h -1 -W -s '|' \
        -Q "SET NOCOUNT ON; EXEC dbo.usp_PostSummaryByBlog" 2>&1)
  rc=$?
  if [ $rc -ne 0 ]; then
    printf '{"ok":false,"at":"%s","procedure":"dbo.usp_PostSummaryByBlog","error":%s}\n' "$ts" "$(printf '%s' "$rows" | tr -d '\r' | python3 -c 'import json,sys;print(json.dumps(sys.stdin.read().strip()))' 2>/dev/null || printf '"%s"' "$(printf '%s' "$rows" | tr -d '\r\n"')")" > "$OUT/last-run.json"
    echo "[reporting] FAIL usp_PostSummaryByBlog: $rows" >&2
    return 1
  fi
  {
    echo '['
    first=1
    while IFS='|' read -r blogid name posts taglinks last; do
      [ -z "$blogid" ] && continue
      [ $first -eq 0 ] && echo ','
      first=0
      printf '  {"blogId":%s,"blog":"%s","posts":%s,"tagLinks":%s,"lastPost":"%s"}' "$blogid" "$name" "$posts" "$taglinks" "$last"
    done <<< "$rows"
    echo; echo ']'
  } > "$OUT/post-summary.json"
  local n; n=$(grep -c blogId "$OUT/post-summary.json")
  printf '{"ok":true,"at":"%s","procedure":"dbo.usp_PostSummaryByBlog","blogs":%s}\n' "$ts" "$n" > "$OUT/last-run.json"
  echo "[reporting] OK usp_PostSummaryByBlog -> $n blog rows"
}

if [ "${1:-loop}" = "once" ]; then
  run_once; exit $?
fi
while true; do run_once; sleep "$INTERVAL"; done
