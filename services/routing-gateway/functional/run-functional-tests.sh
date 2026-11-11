#!/usr/bin/env bash
# Functional acceptance checks for the routing gateway (MBA-3822) against a running estate:
# legacy (:8080/:8081), scheduling-service (:8090) and routing-gateway (:8000). See functional_test.py.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="$(command -v python3 || command -v python)"
PYTHONUTF8=1 exec "$PY" "$HERE/functional_test.py" "$@"
