#!/bin/sh
# Renders the `-> lb tcp/80` rules of allowlist.rules into nginx allow/deny directives.
# Usage: render-nginx-acl.sh allowlist.rules > lb-access.conf
set -eu
RULES="${1:-$(dirname "$0")/allowlist.rules}"
echo "# generated from firewall/allowlist.rules — do not edit"
grep -E '^ALLOW +[^ ]+ +-> +lb +tcp/80' "$RULES" | awk '{print $2}' | while read -r src; do
  if [ "$src" = "any" ]; then echo "allow all;"; else echo "allow $src;"; fi
done
echo "deny all;"
