#!/bin/bash
# Injects the DB connection string secret into Web.config (the IIS-era equivalent of a web.config
# transform / SetParameters.xml) and starts the ASP.NET host.
set -euo pipefail

: "${SAMPLEWEBAPP_DB_CONNECTION:?SAMPLEWEBAPP_DB_CONNECTION is required (see infra/secrets/.env.example)}"
PORT="${APP_PORT:-80}"

conn="${SAMPLEWEBAPP_DB_CONNECTION//&/&amp;}"
conn="${conn//|/\\|}"
sed -i "s|\(<add name=\"SampleWebAppDb\" connectionString=\"\)[^\"]*\(\"\)|\1${conn}\2|" /app/Web.config

echo "[entrypoint] SampleWebApp (legacy .NET Framework / mono) listening on :${PORT}, health /Home/Health"
exec xsp4 --port "${PORT}" --address 0.0.0.0 --nonstop --root /app --applications /:/app
