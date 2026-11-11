#!/usr/bin/env bash
# Runs every repro against the local estate. Most scripts wrap their writes in BEGIN TRAN ... ROLLBACK.
set -u
cd "$(dirname "$0")"
SERVER="${AD_SQL_SERVER:-localhost,1433}"; PW="${AD_SA_PASSWORD:-Str0ng!Passw0rd}"
for f in $(ls *.sql | sort); do
  echo "===== $f"
  sqlcmd -S "$SERVER" -U sa -P "$PW" -C -W -s "|" -d AssessmentDelivery -i "$f" < /dev/null
done
