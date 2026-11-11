#!/usr/bin/env python3
"""End-to-end suite for the scheduling cutover (Phase 3). Drives the real user-facing channels:

  PORTAL   the .NET portal over HTTP (form GET + antiforgery token + POST), :8080
  PARTNER  booking-api REST, :8081
  RESULTS  results-service REST (JSON + CSV upload), :8082
  SCHED    scheduling-service REST, :8084 (migrated target only: contract-only surfaces)

Categories: parity, waitlist, accommodation, attended, errors, drift (continuous seat-drift sampling under concurrent
load). Every step is checked against model.Model (legacy semantics with HB citations), so the same suite is the
oracle for --target legacy and --target migrated.

Fixtures are self-contained: each category creates its own sessions (legacy: INSERT into AssessmentDelivery like the
S1 Fixtures; migrated: POST /api/v1/sessions on scheduling-service) dated E2E_AS_OF + 45 days, and uses candidates
with no Result rows. Nothing depends on seed sessions or GETDATE(), so results do not move with the wall clock.

  python scripts/e2e/suite.py --target legacy   --out build/e2e/legacy.json
  python scripts/e2e/suite.py --target migrated --out build/e2e/migrated.json [--only parity,errors]
"""
import argparse
import json
import os
import subprocess
import sys
import traceback

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from adlib import AS_OF  # noqa: E402
from cats_attend import cat_attended  # noqa: E402
from cats_core import cat_accommodation, cat_parity, cat_waitlist  # noqa: E402
from cats_errors import cat_errors  # noqa: E402
from core import Ctx, Recorder  # noqa: E402
from load import cat_drift  # noqa: E402
from targets import Target  # noqa: E402

CATEGORIES = [('parity', cat_parity), ('waitlist', cat_waitlist), ('accommodation', cat_accommodation),
              ('attended', cat_attended), ('errors', cat_errors), ('drift', cat_drift)]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--target', choices=['legacy', 'migrated'], default=os.environ.get('E2E_TARGET', 'legacy'))
    ap.add_argument('--only', default=os.environ.get('E2E_ONLY', ''))
    ap.add_argument('--out', default='')
    a = ap.parse_args()
    t = Target(a.target)
    health = t.health()
    print('[e2e] target=%s as-of=%s health=%s' % (a.target, AS_OF, health), flush=True)
    rec = Recorder()
    if any(v != 200 for v in health.values()):
        rec.category = 'setup'
        rec.check('all services healthy', False, health)
    else:
        ctx = Ctx(t, rec)
        only = [x for x in a.only.split(',') if x]
        for name, fn in CATEGORIES:
            if only and name not in only:
                continue
            rec.category = name
            print('[e2e] == %s' % name, flush=True)
            try:
                fn(ctx)
            except Exception as e:  # a crash fails the category, the others still run
                traceback.print_exc()
                rec.check('%s raised' % name, False, repr(e))
    summary = {}
    for ck in rec.checks:
        s = summary.setdefault(ck['category'], {'pass': 0, 'fail': 0, 'na': 0})
        s['na' if ck['na'] else ('pass' if ck['ok'] else 'fail')] += 1
    head = subprocess.run(['git', 'rev-parse', 'HEAD'], stdout=subprocess.PIPE, cwd=os.path.dirname(os.path.abspath(__file__))
                          ).stdout.decode().strip()
    out = {'target': a.target, 'asOf': AS_OF, 'head': head, 'health': health, 'summary': summary, 'checks': rec.checks}
    out.update(rec.extra)
    if a.out:
        os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
        with open(a.out, 'w') as f:
            json.dump(out, f, indent=1, default=str)
    print('[e2e] summary: %s' % json.dumps(summary))
    if 'drift' in rec.extra:
        print('[e2e] max raw seat drift %(maxRawDrift)s, max unexplained %(maxUnexplainedDrift)s over %(samples)s samples' % rec.extra['drift'])
    sys.exit(1 if any(s['fail'] for s in summary.values()) else 0)


if __name__ == '__main__':
    main()
