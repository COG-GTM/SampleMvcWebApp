"""Snapshot + normalized diff of Booking / ExamSession / Waitlist / AuditLog for the cutover rehearsal.

Normalization (the only transformations applied before comparing; see docs/proof/s6-e2e-cutover.md):
  N1 surrogate ids   BookingId / WaitlistId created during the workload (id > the pre-workload MAX on that side) are
                     replaced by their rank, n1, n2, ... in id order. References follow: Waitlist.BookingId,
                     AuditLog.EntityId for EntityName='Booking', and 'booking=<id>' inside AuditLog.Detail. Ids that
                     existed before the workload (seed / import, identities preserved) are compared verbatim.
                     AuditId is dropped: the migrated stack writes Booking/ExamSession audit rows to Scheduling.AuditLog
                     and the rest to AssessmentDelivery.AuditLog (two identity spaces), so audit rows are compared as a
                     multiset of their remaining columns.
  N2 timestamps      DATETIME values >= the workload start on that side become '<run>'. Older values (seed / import)
                     are compared verbatim, so an import that shifted a timestamp would show as drift.
Nothing else is normalized: every other column of every row is compared exactly. RowVer (migrated-only ROWVERSION) is
not a legacy column and is not selected."""
import re

COLS = {
    'ExamSession': ['SessionId', 'ExamId', 'TestCenterId', 'RoomId', 'StartsAt', 'Capacity', 'AccommodationCapacity',
                    'SeatsAvailable', 'Status', 'LastModified'],
    'Booking': ['BookingId', 'SessionId', 'CandidateId', 'Status', 'AccommodationCode', 'Channel', 'ExternalRef', 'BookedAt',
                'CancelledAt', 'CancelReason', 'LastModified'],
    'Waitlist': ['WaitlistId', 'SessionId', 'CandidateId', 'BookingId', 'Position', 'AddedAt'],
    'AuditLog': ['EntityName', 'EntityId', 'Action', 'Detail', 'Actor', 'LoggedAt'],
}
TS = {'StartsAt', 'LastModified', 'BookedAt', 'CancelledAt', 'AddedAt', 'LoggedAt'}


# results-service DUPLICATE_REJECTED detail embeds the stored row's ScoredAt, written during the run:
# 'stored=700@2026-11-11T19:20:08 submitted=650@...'. Only that stored value is a run timestamp; submitted values are
# compared verbatim.
DETAIL_TS = re.compile(r'(stored=\d+)@(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)')


def _select(cols):
    return ', '.join("CONVERT(VARCHAR(23), %s, 126) AS %s" % (c, c) if c in TS else c for c in cols)


def marks(t):
    """Pre-workload high-water marks and clock on the state database."""
    r = t.state.json("SELECT (SELECT ISNULL(MAX(BookingId),0) FROM %s) AS b, (SELECT ISNULL(MAX(WaitlistId),0) FROM %s) AS w, "
                     "CONVERT(VARCHAR(23), DATEADD(SECOND, -1, GETDATE()), 126) AS ts" % (t.bk, t.wl))[0]
    return {'booking': int(r['b']), 'waitlist': int(r['w']), 'start': r['ts']}


def snapshot(t):
    snap = {}
    for name, tbl in (('ExamSession', t.es), ('Booking', t.bk), ('Waitlist', t.wl)):
        snap[name] = t.state.json('SELECT %s FROM %s ORDER BY 1' % (_select(COLS[name]), tbl))
    rows = []
    for db, tbl in t.audit_tables:
        rows += db.json('SELECT %s FROM %s' % (_select(COLS['AuditLog']), tbl))
    snap['AuditLog'] = rows
    return snap


def normalize(snap, mk):
    newb = sorted(b['BookingId'] for b in snap['Booking'] if b['BookingId'] > mk['booking'])
    newb = {bid: 'n%d' % (i + 1) for i, bid in enumerate(newb)}
    neww = sorted(w['WaitlistId'] for w in snap['Waitlist'] if w['WaitlistId'] > mk['waitlist'])
    neww = {wid: 'n%d' % (i + 1) for i, wid in enumerate(neww)}
    bmap = lambda v: newb.get(v, v)  # noqa: E731
    start = mk['start']

    def ts(row):
        for c in TS:
            if c in row and row[c] is not None and row[c] >= start:
                row[c] = '<run>'
        return row

    out = {}
    out['ExamSession'] = {r['SessionId']: ts(dict(r)) for r in snap['ExamSession']}
    out['Booking'] = {}
    for r in snap['Booking']:
        r = ts(dict(r))
        r['BookingId'] = bmap(r['BookingId'])
        out['Booking'][r['BookingId']] = r
    out['Waitlist'] = {}
    for r in snap['Waitlist']:
        r = ts(dict(r))
        r['WaitlistId'] = neww.get(r['WaitlistId'], r['WaitlistId'])
        r['BookingId'] = bmap(r['BookingId'])
        out['Waitlist'][r['WaitlistId']] = r
    audit = {}
    for r in snap['AuditLog']:
        r = ts(dict(r))
        if r['EntityName'] == 'Booking':
            r['EntityId'] = bmap(r['EntityId'])
        if r.get('Detail'):
            r['Detail'] = re.sub(r'booking=(\d+)', lambda m: 'booking=%s' % bmap(int(m.group(1))), r['Detail'])
            r['Detail'] = DETAIL_TS.sub(lambda m: m.group(1) + '@<run>' if m.group(2) >= start[:19] else m.group(0), r['Detail'])
        k = tuple((c, r.get(c)) for c in COLS['AuditLog'])
        audit[k] = audit.get(k, 0) + 1
    out['AuditLog'] = audit
    return out


def diff(a, b):
    """{table: {rows, identical, onlyLegacy, onlyMigrated, changed, details}} for normalized snapshots a (legacy), b."""
    res = {}
    for name in ('Booking', 'ExamSession', 'Waitlist'):
        A, B = a[name], b[name]
        keys = sorted(set(A) | set(B), key=str)
        ol = [k for k in keys if k not in B]
        om = [k for k in keys if k not in A]
        ch = [k for k in keys if k in A and k in B and A[k] != B[k]]
        det = [{'key': k, 'legacy': A.get(k), 'migrated': B.get(k),
                'columns': sorted(c for c in COLS[name] if (A.get(k) or {}).get(c) != (B.get(k) or {}).get(c))} for k in ol + om + ch]
        res[name] = {'legacyRows': len(A), 'migratedRows': len(B), 'identical': len(keys) - len(ol) - len(om) - len(ch),
                     'onlyLegacy': len(ol), 'onlyMigrated': len(om), 'changed': len(ch), 'details': det}
    A, B = a['AuditLog'], b['AuditLog']
    keys = set(A) | set(B)
    ol = {k: A.get(k, 0) - B.get(k, 0) for k in keys if A.get(k, 0) > B.get(k, 0)}
    om = {k: B.get(k, 0) - A.get(k, 0) for k in keys if B.get(k, 0) > A.get(k, 0)}
    res['AuditLog'] = {'legacyRows': sum(A.values()), 'migratedRows': sum(B.values()),
                       'identical': sum(min(A.get(k, 0), B.get(k, 0)) for k in keys),
                       'onlyLegacy': sum(ol.values()), 'onlyMigrated': sum(om.values()), 'changed': 0,
                       'details': [{'side': 'legacy', 'n': n, 'row': dict(k)} for k, n in ol.items()] +
                                  [{'side': 'migrated', 'n': n, 'row': dict(k)} for k, n in om.items()]}
    return res
