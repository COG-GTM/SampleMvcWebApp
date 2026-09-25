#!/usr/bin/env python3
"""Wraps a k6 summary with the runtime/infra context it was captured under -> demo/BASELINE.json."""
import json, re, subprocess, sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
summary = json.load(open(sys.argv[1]))

csproj = (ROOT / "SampleWebApp" / "SampleWebApp.csproj").read_text(encoding="utf-8", errors="ignore")
tfm = re.search(r"<TargetFramework(?:Version)?>([^<]+)<", csproj)
tfvars = (ROOT / "infra" / "terraform" / "variables.tf").read_text()
def tfdefault(name):
    m = re.search(r'variable "%s"[^}]*default\s*=\s*"?([^"\n]+)"?' % name, tfvars, re.S)
    return m.group(1).strip() if m else None

def git(*a):
    try:
        return subprocess.check_output(["git", *a], cwd=ROOT, text=True).strip()
    except Exception:
        return None

out = {
    "schema": "samplewebapp.nfr-baseline/v1",
    "captured_at": summary.get("generated_at"),
    "app": {
        "git_commit": git("rev-parse", "--short", "HEAD"),
        "git_branch": git("rev-parse", "--abbrev-ref", "HEAD"),
        "target_framework": tfm.group(1) if tfm else None,
        "runtime_family": tfdefault("app_runtime_family"),
        "runtime_image_pin": tfdefault("app_runtime_image"),
        "host": "linux/mono-xsp4 stand-in (infra/legacy/Dockerfile.mono)",
    },
    "load": {k: summary.get(k) for k in ("vus", "duration", "requests", "rps", "base_url")},
    "nfr": {k: summary.get(k) for k in ("p50_ms", "p95_ms", "p99_ms", "avg_ms", "max_ms", "error_rate")},
    "per_route": summary.get("per_route"),
    "gates_for_migration": {
        "p95_ms_max": round(summary["p95_ms"] * 1.5),
        "p99_ms_max": round(summary["p99_ms"] * 1.5),
        "error_rate_max": 0.01,
        "rule": "post-migration p95/p99 must stay within 1.5x of baseline; error rate < 1%",
    },
}
print(json.dumps(out, indent=2))
