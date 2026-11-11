#!/usr/bin/env python3
"""Cutover rehearsal: run the same workload on the legacy estate and on the migrated stack, each started from the same
production copy, then diff Booking / ExamSession / Waitlist / AuditLog after normalizing surrogate ids and timestamps.

  python scripts/e2e/cutover.py capture --target legacy   --out build/cutover/legacy.json
  python scripts/e2e/cutover.py capture --target migrated --out build/cutover/migrated.json
  python scripts/e2e/cutover.py diff build/cutover/legacy.json build/cutover/migrated.json --out build/cutover/diff.json

scripts/e2e/cutover-rehearsal.ps1 does the whole thing (copy rebuild, both stacks, capture, diff). Exit code of `diff`
is 0 only when every table has zero drift, apart from individually justified rows for a legacy defect the migrated
stack intentionally removed (HB-01, only if scheduling-service runs with legacy-seat-accounting off)."""
import argparse
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from adlib import AS_OF  # noqa: E402
import cutover_diff as cd  # noqa: E402
import cutover_workload as cw  # noqa: E402
from targets import Target  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))


def git_head(path):
    return subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=path, stdout=subprocess.PIPE).stdout.decode().strip()


def capture(a):
    t = Target(a.target)
    health = t.health()
    if any(v != 200 for v in health.values()):
        sys.exit('unhealthy: %s' % health)
    mk = cd.marks(t)
    before = cd.snapshot(t)
    print('[cutover] %s: pre-workload marks %s; running workload' % (a.target, mk), flush=True)
    inputs, outcomes = cw.run(t)
    after = cd.snapshot(t)
    out = {'target': a.target, 'asOf': AS_OF, 'head': git_head(a.repo or HERE), 'health': health, 'marks': mk, 'inputs': inputs,
           'outcomes': outcomes, 'before': before, 'after': after}
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, 'w') as f:
        json.dump(out, f, default=str)
    print('[cutover] %s: %d ops, snapshot %s' % (a.target, len(outcomes), {k: len(v) for k, v in after.items()}))


def hb01_rows(L, M, d):
    """ExamSession rows whose only difference is SeatsAvailable, by exactly the number of PARTNER bookings that were
    inserted BOOKED on that session during the workload (legacy took 2 seats each, migrated 1): HB-01 removed."""
    partner = {}
    for b in L['after']['Booking']:
        if b['BookingId'] > L['marks']['booking'] and b['Channel'] == 'PARTNER':
            partner.setdefault(b['SessionId'], []).append(b['BookingId'])
    booked_ins = {}
    for r in L['after']['AuditLog']:
        if r['EntityName'] == 'Booking' and r['Action'] == 'BOOK' and (r['Detail'] or '').find('status=BOOKED') >= 0 \
                and r['EntityId'] > L['marks']['booking'] and 'channel=PARTNER' in (r['Detail'] or ''):
            sid = int(r['Detail'].split('session=')[1].split()[0])
            booked_ins[sid] = booked_ins.get(sid, 0) + 1
    out = []
    for det in d['ExamSession']['details']:
        lg, mg = det['legacy'], det['migrated']
        if lg and mg and set(det['columns']) <= {'SeatsAvailable', 'LastModified'} and 'SeatsAvailable' in det['columns']:
            n = booked_ins.get(det['key'], 0)
            if n and mg['SeatsAvailable'] - lg['SeatsAvailable'] == n:
                out.append({'sessionId': det['key'], 'legacySeats': lg['SeatsAvailable'], 'migratedSeats': mg['SeatsAvailable'],
                            'partnerBookedInserts': n, 'columns': det['columns']})
    return out


def run_diff(a):
    L, M = (json.load(open(p)) for p in (a.legacy, a.migrated))
    base = cd.diff(cd.normalize(L['before'], L['marks']), cd.normalize(M['before'], M['marks']))
    final = cd.diff(cd.normalize(L['after'], L['marks']), cd.normalize(M['after'], M['marks']))
    ops = [{'op': x[0], 'legacy': x[1], 'migrated': y[1]} for x, y in zip(L['outcomes'], M['outcomes']) if x != y]
    if len(L['outcomes']) != len(M['outcomes']):
        ops.append({'op': '<count>', 'legacy': len(L['outcomes']), 'migrated': len(M['outcomes'])})
    hb01 = hb01_rows(L, M, final)
    justified = {r['sessionId'] for r in hb01}
    unexplained = {}
    for name, d in final.items():
        n = d['onlyLegacy'] + d['onlyMigrated'] + d['changed']
        if name == 'ExamSession':
            n -= len([x for x in d['details'] if x['key'] in justified])
        unexplained[name] = n
    drift = sum(unexplained.values()) + len(ops) + sum(b['onlyLegacy'] + b['onlyMigrated'] + b['changed'] for b in base.values())
    res = {'legacyHead': L['head'], 'migratedHead': M['head'], 'asOf': L['asOf'], 'inputsEqual': L['inputs'] == M['inputs'],
           'inputs': L['inputs'], 'outcomes': [{'op': x[0], 'legacy': x[1], 'migrated': y[1]} for x, y in zip(L['outcomes'], M['outcomes'])],
           'outcomeMismatches': ops, 'baseline': {k: {kk: vv for kk, vv in v.items() if kk != 'details'} for k, v in base.items()},
           'baselineDetails': {k: v['details'][:20] for k, v in base.items()},
           'tables': {k: {kk: vv for kk, vv in v.items() if kk != 'details'} for k, v in final.items()},
           'details': {k: v['details'] for k, v in final.items()}, 'hb01Justified': hb01, 'unexplained': unexplained,
           'zeroDrift': drift == 0 and L['inputs'] == M['inputs']}
    with open(a.out, 'w') as f:
        json.dump(res, f, indent=1, default=str)
    print('[cutover] baseline (copy vs import): %s' % json.dumps(res['baseline']))
    for k, v in res['tables'].items():
        print('[cutover] %-11s legacy=%-5d migrated=%-5d identical=%-5d onlyLegacy=%d onlyMigrated=%d changed=%d unexplained=%d'
              % (k, v['legacyRows'], v['migratedRows'], v['identical'], v['onlyLegacy'], v['onlyMigrated'], v['changed'], unexplained[k]))
    print('[cutover] outcome mismatches: %d; HB-01 justified ExamSession rows: %d; ZERO DRIFT: %s'
          % (len(ops), len(hb01), res['zeroDrift']))
    for k, v in res['details'].items():
        for x in v[:8]:
            print('   %s %s' % (k, json.dumps(x, default=str)[:400]))
    for x in ops[:20]:
        print('   op %s' % x)
    sys.exit(0 if res['zeroDrift'] else 1)


def main():
    ap = argparse.ArgumentParser()
    sp = ap.add_subparsers(dest='cmd', required=True)
    c = sp.add_parser('capture')
    c.add_argument('--target', choices=['legacy', 'migrated'], required=True)
    c.add_argument('--out', required=True)
    c.add_argument('--repo', default='', help='worktree whose HEAD is recorded (the stack under test)')
    d = sp.add_parser('diff')
    d.add_argument('legacy')
    d.add_argument('migrated')
    d.add_argument('--out', required=True)
    a = ap.parse_args()
    capture(a) if a.cmd == 'capture' else run_diff(a)


if __name__ == '__main__':
    main()
