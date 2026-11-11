#!/usr/bin/env bash
# Helpers used by docs/as-is/deep-dives/portal.md. Source from Git Bash on the Windows VM after
# `scripts\run-legacy.ps1` reports "all components up":   source docs/as-is/repros/portal/portal-repro.sh
# NOTE: most repros mutate seed data; reseed with scripts\run-legacy.ps1 (without -SkipDb) to repeat them.

PORTAL=${PORTAL:-http://localhost:8080}
SQLCMD=${SQLCMD:-sqlcmd}

# q "<T-SQL>"  - run a query as the Windows sysadmin against AssessmentDelivery (stdin redirected: go-sqlcmd hangs otherwise)
q() { "$SQLCMD" -S localhost,1433 -E -C -d AssessmentDelivery -W -s"|" -Q "SET NOCOUNT ON; $1" < /dev/null; }

# post <GET path rendering the form> <POST path> key=value ...
# Fetches the anti-forgery cookie + hidden token first, posts the form, prints status/redirect and any validation error.
post() {
  local g=$1 p=$2; shift 2
  local tok
  tok=$(curl -s -c cj.txt -b cj.txt "$PORTAL$g" | grep -o 'name="__RequestVerificationToken" type="hidden" value="[^"]*"' | head -1 | sed 's/.*value="//;s/"$//')
  local a=(); for kv in "$@"; do a+=(--data-urlencode "$kv"); done
  curl -s -c cj.txt -b cj.txt -o last.html -w "HTTP %{http_code} -> %{redirect_url}\n" "$PORTAL$p" \
       --data-urlencode "__RequestVerificationToken=$tok" "${a[@]}"
  grep -oE 'validation-summary-errors[^<]*<ul><li>[^<]*' last.html | sed 's/.*<li>/  error: /'
}

# strip <url> - fetch a portal page and print its visible text
strip() { curl -s "$1" | sed 's/<[^>]*>//g' | sed 's/^ *//' | grep -v '^\s*$'; }
