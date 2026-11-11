#!/usr/bin/env bash
# Runs every repro in order on a freshly seeded DB (scripts/run-legacy.ps1 -NoPortal) and writes output/<name>.txt.
cd "$(dirname "$0")"; mkdir -p output
for s in [0-9][0-9]-*.sh; do echo "== $s"; "$BASH" "$s" > "output/${s%.sh}.txt" 2>&1; done
