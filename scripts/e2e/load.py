"""Category: continuous seat-drift invariant under concurrent mixed-channel load, plus a standalone sampler that
scripts/mixed-traffic.{sh,ps1} run in the background (python scripts/e2e/load.py sample --stop-file F --out J)."""
import argparse
import json
import os
import random
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from core import code_of  # noqa: E402

# Raw drift is db/checks/seat-drift.sql's formula. HB-01 (kept by contract section 6, scheduling.legacy-seat-accounting
# =true) adds exactly one seat of drift per PARTNER booking inserted as BOOKED (2 seats taken, 1 released on cancel),
# counted from its BOOK audit row in the same snapshot. Invariant: unexplained = raw - partner BOOKED inserts = 0.
DRIFT_SQL = """SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; SET DEADLOCK_PRIORITY LOW; SET LOCK_TIMEOUT 10000;
SELECT s.SessionId, (s.Capacity - ISNULL(b.Active, 0)) - s.SeatsAvailable, ISNULL(x.Excess, 0), ISNULL(w.Dup, 0)
FROM {es} s
LEFT JOIN (SELECT SessionId, COUNT(*) AS Active FROM {bk} WHERE Status IN ('BOOKED','ATTENDED') GROUP BY SessionId) b
       ON b.SessionId = s.SessionId
LEFT JOIN (SELECT bk.SessionId, COUNT(*) AS Excess FROM {bk} bk
             JOIN dbo.AuditLog a ON a.EntityName = 'Booking' AND a.EntityId = bk.BookingId AND a.Action = 'BOOK'
                                AND a.Detail LIKE 'session=% status=BOOKED%'
            WHERE bk.Channel = 'PARTNER' GROUP BY bk.SessionId) x ON x.SessionId = s.SessionId
LEFT JOIN (SELECT SessionId, COUNT(*) - COUNT(DISTINCT Position) AS Dup FROM {wl} GROUP BY SessionId) w
       ON w.SessionId = s.SessionId
WHERE {where}"""


class Sampler(threading.Thread):
    def __init__(self, target, ids=None, interval=0.25, stop_file=None):
        super().__init__(daemon=True)
        self.t, self.ids, self.interval, self.stop_file = target, ids, interval, stop_file
        self.samples, self.errors = [], []
        self.stop_evt = threading.Event()
        self.t0 = time.time()

    def sample(self):
        where = ('s.SessionId IN (%s)' % ','.join(map(str, self.ids))) if self.ids else (
            "s.SeatsAvailable <> s.Capacity - ISNULL(b.Active, 0) OR ISNULL(w.Dup, 0) > 0")
        rows = self.t.state.rows(DRIFT_SQL.format(es=self.t.es, bk=self.t.bk, wl=self.t.wl, where=where))
        k = getattr(self.t, 'partner_seats', 2) - 1  # extra seats per partner BOOKED insert: 1 with HB-01, 0 without
        per = {int(r[0]): (int(r[1]), int(r[2]) * k, int(r[3])) for r in rows}
        self.samples.append({'t': round(time.time() - self.t0, 2),
                             'raw': max([abs(v[0]) for v in per.values()] or [0]),
                             'unexplained': max([abs(v[0] - v[1]) for v in per.values()] or [0]),
                             'dup': sum(v[2] for v in per.values()), 'per': per})

    def run(self):
        while not self.stop_evt.is_set() and not (self.stop_file and os.path.exists(self.stop_file)):
            try:
                self.sample()
            except Exception as e:  # lock timeout / deadlock victim: record, keep sampling
                self.errors.append(str(e)[:200])
            self.stop_evt.wait(self.interval)

    def summary(self):
        s = self.samples
        return {'samples': len(s), 'seconds': round(time.time() - self.t0, 1), 'maxRawDrift': max([x['raw'] for x in s] or [0]),
                'maxUnexplainedDrift': max([x['unexplained'] for x in s] or [0]),
                'maxDupWaitlistPositions': max([x['dup'] for x in s] or [0]), 'samplerErrors': self.errors[:5],
                'series': [(x['t'], x['raw'], x['unexplained']) for x in s]}


def cat_drift(c):
    n_sessions = int(os.environ.get('E2E_LOAD_SESSIONS', '6'))
    ops = int(os.environ.get('E2E_LOAD_OPS', '640'))
    workers = int(os.environ.get('E2E_LOAD_WORKERS', '8'))
    ids = [c.session(5, 10, 2) for _ in range(n_sessions)]
    pool = c.candidates(int(os.environ.get('E2E_LOAD_CANDIDATES', '40')))
    sampler = Sampler(c.t, ids, float(os.environ.get('E2E_SAMPLE_INTERVAL', '0.1')))
    sampler.sample()
    c.rec.eq('baseline drift on the load sessions is 0', sampler.samples[0]['raw'], 0)
    stats = {'portalBook': 0, 'partnerBook': 0, 'cancel': 0, 'attend': 0, 'reschedule': 0, 'rejected': {}, 'transport': 0}
    deadlocks_before = instance_deadlocks(c.t)
    lock = threading.Lock()

    def note(kind, ok, code=None, http_status=None):
        with lock:
            if ok:
                stats[kind] += 1
            else:
                if code and 'deadlock' in code.lower():
                    code = 'DEADLOCK_VICTIM'
                stats['rejected'][code or 'ERR'] = stats['rejected'].get(code or 'ERR', 0) + 1
                if http_status in (0, 500, 502, 503) or code == 'DEADLOCK_VICTIM':
                    stats['transport'] += 1

    def work(w):
        rnd = random.Random(1000 + w)
        mine = []
        for _ in range(ops // workers):
            roll, sid, cand = rnd.random(), rnd.choice(ids), rnd.choice(pool)
            acc = rnd.choice([None] * 8 + ['ET25', 'SR'])
            if roll < 0.35 or (roll >= 0.70 and not mine):
                ch = 'PORTAL' if roll < 0.35 or rnd.random() < 0.5 else 'PARTNER'
                o = c.t.portal.book(sid, cand[1], acc or '') if ch == 'PORTAL' else c.t.partner.book(sid, cand[1], acc, 'LOAD-%d' % w)
                note('portalBook' if ch == 'PORTAL' else 'partnerBook', o.ok, code_of(ch, o), o.http_status)
                if o.ok:
                    mine.append((ch, o.booking_id, sid))
            elif roll < 0.70:
                o = c.t.partner.book(sid, cand[1], acc, 'LOAD-%d' % w)
                note('partnerBook', o.ok, o.error, o.http_status)
                if o.ok:
                    mine.append(('PARTNER', o.booking_id, sid))
            elif roll < 0.85:
                ch, bid, _ = mine.pop(rnd.randrange(len(mine)))
                o = c.t.portal.cancel(bid, 'load') if ch == 'PORTAL' else c.t.partner.cancel(bid, 'load')
                note('cancel', o.ok, code_of(ch, o), o.http_status)
            elif roll < 0.93:
                ch, bid, _ = mine[rnd.randrange(len(mine))]
                r = c.t.results.post(bid, rnd.randint(400, 900))
                note('attend', r.status in (200, 201), r.err() or 'HTTP%s' % r.status, r.status)
            else:
                i = rnd.randrange(len(mine))
                ch, bid, old = mine[i]
                new = rnd.choice([x for x in ids if x != old])
                o = c.t.portal.reschedule(bid, new) if ch == 'PORTAL' else c.t.partner.reschedule(bid, new)
                note('reschedule', o.ok, code_of(ch, o), o.http_status)
                if o.ok:
                    mine[i] = (ch, o.booking_id, new)

    sampler.start()
    threads = [threading.Thread(target=work, args=(w,)) for w in range(workers)]
    for th in threads:
        th.start()
    for th in threads:
        th.join()
    sampler.stop_evt.set()
    sampler.join()
    sampler.sample()
    stats['sqlDeadlocks'] = instance_deadlocks(c.t) - deadlocks_before
    stats['samplerDeadlockVictims'] = sum(1 for e in sampler.errors if 'deadlock' in e.lower())
    summ = sampler.summary()
    final = sampler.samples[-1]['per']
    summ.update({'ops': ops, 'workers': workers, 'sessions': ids, 'stats': stats,
                 'final': {str(k): {'rawDrift': v[0], 'hb01PartnerBookedInserts': v[1]} for k, v in final.items()}})
    c.rec.extra['drift'] = summ
    print('  load: %d ops / %d workers in %ss, %d samples, stats=%s' % (ops, workers, summ['seconds'], summ['samples'], json.dumps(stats)))
    c.rec.check('drift sampled continuously during load (>= 10 samples)', summ['samples'] >= 10, summ['samples'])
    # Both estates deadlock under this load (legacy procs: message 1205 to the caller; scheduling-service: SYSTEM_ERROR 500,
    # which booking-api reports as SCHEDULING_UNAVAILABLE without the SQL text). A victim is rolled back, so it cannot
    # move seats. Every 5xx / transport failure must be accounted for by a SQL Server deadlock in the same window.
    victims = stats['sqlDeadlocks'] - stats['samplerDeadlockVictims']
    c.rec.check('every transport / 5xx failure under load is a rolled-back SQL deadlock victim (%d failures, %d client-side '
                'deadlocks)' % (stats['transport'], victims), stats['transport'] <= victims, stats)
    c.rec.check('unexplained seat drift 0 at every sample (max raw %d; raw drift allowed only as HB-01 partner inserts when HB-01 is kept)' % summ['maxRawDrift'],
                summ['maxUnexplainedDrift'] == 0, [x for x in sampler.samples if x['unexplained']][:3])
    full = Sampler(c.t, None)
    full.sample()
    stray = {k: v for k, v in full.samples[-1]['per'].items() if v[0] != v[1]}
    c.rec.check('full seat-drift check over every session after load: every drift row is HB-01-explained '
                '(%d rows, all raw == partner BOOKED inserts)' % len(full.samples[-1]['per']), not stray, stray)


def instance_deadlocks(t):
    """Cumulative SQL Server deadlock count for the instance (both databases share it)."""
    return int(t.ref.scalar("SELECT cntr_value FROM sys.dm_os_performance_counters WHERE counter_name LIKE "
                            "'Number of Deadlocks/sec%' AND instance_name = '_Total'"))


def main():
    """Background sampler for scripts/mixed-traffic.*: samples every session with drift until --stop-file appears."""
    from targets import Target
    ap = argparse.ArgumentParser()
    ap.add_argument('cmd', choices=['sample'])
    ap.add_argument('--target', default=os.environ.get('E2E_TARGET', 'legacy'))
    ap.add_argument('--stop-file', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--interval', type=float, default=0.5)
    a = ap.parse_args()
    s = Sampler(Target(a.target), None, a.interval, a.stop_file)
    s.run()
    s.sample()
    with open(a.out, 'w') as f:
        json.dump(s.summary(), f)


if __name__ == '__main__':
    main()
