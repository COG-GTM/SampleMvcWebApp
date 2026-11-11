"""Cutover rehearsal workload: one deterministic, sequential script of real channel calls (portal HTTP, booking-api,
results-service) against the production copy. Inputs are chosen by query from the copy itself, relative to the pinned
clock E2E_AS_OF, so both sides pick exactly the same sessions and candidates. Sequential on purpose: concurrent
interleavings are not reproducible across two runs, and the diff has to be exact (concurrency is covered by the e2e
drift category)."""
import uuid

from adlib import AS_OF, q


def pick(t):
    """Seeded sessions (identical ids on both sides: the import preserves identities)."""
    st = t.state
    pairs = st.rows("""SELECT TOP 4 a.SessionId, MIN(b.SessionId) FROM {es} a JOIN {es} b ON b.ExamId = a.ExamId AND b.SessionId > a.SessionId
        AND b.Status = 'OPEN' AND b.StartsAt > {asof} AND b.SeatsAvailable >= 3
        WHERE a.Status = 'OPEN' AND a.StartsAt > {asof} AND a.SeatsAvailable BETWEEN 2 AND 6 AND a.AccommodationCapacity >= 1
        GROUP BY a.SessionId ORDER BY a.SessionId""".format(es=t.es, asof=q(AS_OF)))
    big = st.scalar("""SELECT TOP 1 SessionId FROM {es} WHERE Status = 'OPEN' AND StartsAt > {asof} AND SeatsAvailable >= 12
        ORDER BY SessionId""".format(es=t.es, asof=q(AS_OF)))
    other_exam = st.scalar("""SELECT TOP 1 s.SessionId FROM {es} s WHERE s.Status = 'OPEN' AND s.StartsAt > {asof}
        AND s.ExamId <> (SELECT ExamId FROM {es} WHERE SessionId = {a}) ORDER BY s.SessionId""".format(es=t.es, asof=q(AS_OF), a=pairs[0][0]))
    closed = st.scalar("SELECT TOP 1 SessionId FROM {es} WHERE Status <> 'OPEN' ORDER BY SessionId".format(es=t.es))
    seeded_booked = st.ints("""SELECT TOP 3 b.BookingId FROM {bk} b JOIN {es} s ON s.SessionId = b.SessionId
        WHERE b.Status = 'BOOKED' AND s.Status = 'OPEN' AND s.StartsAt > {asof} ORDER BY b.BookingId""".format(bk=t.bk, es=t.es, asof=q(AS_OF)))
    seeded_wl = st.ints("SELECT TOP 2 BookingId FROM {bk} WHERE Status = 'WAITLISTED' ORDER BY BookingId".format(bk=t.bk))
    seeded_done = st.ints("SELECT TOP 1 BookingId FROM {bk} WHERE Status IN ('CANCELLED','ATTENDED') ORDER BY BookingId".format(bk=t.bk))
    # candidates with no booking anywhere and no result: every booking attempt is decided by the session alone
    fresh = ("NOT EXISTS (SELECT 1 FROM dbo.Result r WHERE r.CandidateId = c.CandidateId) AND NOT EXISTS "
             "(SELECT 1 FROM %s.dbo.Booking b WHERE b.CandidateId = c.CandidateId)" % t.state.database)
    cands = t.ref.rows("SELECT TOP 60 c.CandidateNumber FROM dbo.Candidate c WHERE c.DefaultAccommodationCode IS NULL AND %s "
                       "ORDER BY c.CandidateNumber" % fresh)
    et_default = t.ref.scalar("SELECT TOP 1 c.CandidateNumber FROM dbo.Candidate c WHERE c.DefaultAccommodationCode IN "
                              "('ET25','ET50') AND %s ORDER BY c.CandidateNumber" % fresh)
    return {'pairs': [(int(a), int(b)) for a, b in pairs], 'big': int(big), 'otherExam': int(other_exam), 'closed': int(closed),
            'seededBooked': seeded_booked, 'seededWaitlisted': seeded_wl, 'seededDone': seeded_done,
            'cands': [r[0] for r in cands], 'etDefault': et_default}


def run(t):
    """Returns (inputs, outcomes). Outcomes are [op, result] where result is status / error code, never an id."""
    from core import portal_code
    p = pick(t)
    out = []
    ids = {}
    cands = iter(p['cands'])

    def rec(op, o, key=None):
        if hasattr(o, 'ok'):
            res = o.status if o.ok else (portal_code(o) if op.startswith('portal') else o.error)
            if o.ok and key and o.booking_id:
                ids[key] = o.booking_id
        else:  # results-service Resp
            res = '%s %s' % (o.status, (o.json or {}).get('status') if isinstance(o.json, dict) and o.status < 300 else o.err())
        out.append([op, res])
        print('  %-58s %s' % (op, res), flush=True)

    (s1, s1b), (s2, s2b), (s3, _), (s4, _) = p['pairs'][:4]
    # portal fills s1 past capacity -> waitlist; partner books on a session with ample seats (no HB-01 cascade)
    for i in range(6):
        rec('portal book s1 #%d' % i, t.portal.book(s1, next(cands), 'ET25' if i == 1 else ''), 'p%d' % i)
    for i in range(5):
        rec('partner book big #%d' % i, t.partner.book(p['big'], next(cands), 'ET50' if i == 2 else ('sr' if i == 3 else None),
                                                    'CUT-%02d' % i), 'q%d' % i)
    # s2: the portal fills it, then partner and portal interleave on its waitlist (outcomes independent of HB-01)
    for i in range(8):
        rec('portal fill s2 #%d' % i, t.portal.book(s2, next(cands), ''), 'f%d' % i)
        if out[-1][1] == 'WAITLISTED':
            break
    for i in range(4):
        ch = 'partner' if i % 2 == 0 else 'portal'
        c = next(cands)
        o = t.portal.book(s2, c, '') if ch == 'portal' else t.partner.book(s2, c, None, 'CUT-W%d' % i)
        rec('%s waitlist s2 #%d' % (ch, i), o, 'w%d' % i)
    if p['etDefault']:
        rec('portal book big blank acc (default ET, HB-09)', t.portal.book(p['big'], p['etDefault'], ''), 'etp')
        rec('partner book s3 blank acc (stays NULL, HB-09)', t.partner.book(s3, p['etDefault'], None, 'CUT-ET'), 'etq')
    rec('portal duplicate s1 #0', t.portal.book(s1, p['cands'][0], ''))
    rec('partner duplicate s1 #0 (HB-21)', t.partner.book(s1, p['cands'][0], None, 'CUT-DUP'))
    rec('partner ET25 over cap s1', t.partner.book(s1, next(cands), 'ET25', 'CUT-ET2'))
    rec('portal book closed session', t.portal.book(p['closed'], next(cands), ''))
    rec('partner book closed session', t.partner.book(p['closed'], next(cands), None, 'CUT-CL'))
    # cancels: BOOKED (seat release, no promotion), WAITLISTED (gap), seeded rows
    rec('portal cancel s1 #0 (BOOKED)', t.portal.cancel(ids['p0'], 'cutover'))
    rec('partner cancel big #1 (BOOKED, HB-02)', t.partner.cancel(ids['q1'], 'cutover'))
    if 'w1' in ids:
        rec('partner cancel s2 #1', t.partner.cancel(ids['w1'], 'cutover'))
    for i, bid in enumerate(p['seededBooked'][:2]):
        o = t.portal.cancel(bid, 'cutover') if i == 0 else t.partner.cancel(bid, 'cutover')
        rec('%s cancel seeded BOOKED #%d' % ('portal' if i == 0 else 'partner', i), o)
    for bid in p['seededWaitlisted'][:1]:
        rec('portal cancel seeded WAITLISTED', t.portal.cancel(bid, 'cutover'))
    for bid in p['seededDone']:
        rec('portal cancel seeded CANCELLED/ATTENDED', t.portal.cancel(bid, 'cutover'))
        rec('partner cancel seeded CANCELLED/ATTENDED', t.partner.cancel(bid, 'cutover'))
    rec('portal cancel again s1 #0', t.portal.cancel(ids['p0'], 'cutover'))
    # reschedules
    rec('portal reschedule s1 #2 -> same exam', t.portal.reschedule(ids['p2'], s1b), 'p2r')
    rec('partner reschedule s2 waitlisted #2 -> same exam (HB-11)', t.partner.reschedule(ids['w2'], s2b), 'w2r')
    rec('portal reschedule s1 #3 -> other exam', t.portal.reschedule(ids['p3'], p['otherExam']))
    rec('partner reschedule big #0 -> other exam', t.partner.reschedule(ids['q0'], p['otherExam']))
    rec('partner reschedule big #0 -> same session', t.partner.reschedule(ids['q0'], p['big']))
    if p['seededBooked'][2:]:
        rec('portal reschedule seeded BOOKED -> s4', t.portal.reschedule(p['seededBooked'][2], s4))
    # capacity: cut s1 (HB-05 demotion), raise s2 (no promotion), invalid values
    cap = int(t.state.scalar('SELECT Capacity FROM %s WHERE SessionId = %d' % (t.es, s1)))
    rec('portal capacity s1 cut to %d' % max(1, cap - 3), t.portal.change_capacity(s1, max(1, cap - 3)))
    cap2 = int(t.state.scalar('SELECT Capacity FROM %s WHERE SessionId = %d' % (t.es, s2)))
    rec('portal capacity s2 raise to %d' % (cap2 + 2), t.portal.change_capacity(s2, cap2 + 2))
    rec('portal capacity s3 -> -1', t.portal.change_capacity(s3, -1))
    rec('portal capacity s3 -> 0 (< AccommodationCapacity)', t.portal.change_capacity(s3, 0))
    rec('portal book s2 after raise', t.portal.book(s2, next(cands), ''), 'after')
    # results-service: ATTENDED (JSON + CSV), duplicate, conflict, inactive
    rec('results JSON big #4 700', t.results.post(ids['q4'], 700))
    rec('results JSON big #4 700 again', t.results.post(ids['q4'], 700))
    rec('results JSON big #4 650 conflict', t.results.post(ids['q4'], 650))
    if 'w3' in ids:
        rec('results JSON s2 #3 (WAITLISTED)', t.results.post(ids['w3'], 700))
    rec('results JSON big #1 (CANCELLED)', t.results.post(ids['q1'], 700))
    csv = 'candidateNumber,sessionId,score,scoredAt\r\n%s,%d,610,%sT09:30:00\r\n' % (p['cands'][4], s1, AS_OF[:10])
    rec('results CSV s1 #4', t.results.upload('CUTOVER-%s.csv' % uuid.uuid4().hex[:6], csv.encode()))
    rec('portal cancel ATTENDED big #4', t.portal.cancel(ids['q4'], 'cutover'))
    return p, out
