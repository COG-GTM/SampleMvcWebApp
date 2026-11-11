"""Suite plumbing: check recorder, fixture factory and model-verified channel operations."""
import datetime

from adlib import AS_OF, q  # noqa: F401
from model import Model, ModelError

import os

ROOM = int(os.environ.get('E2E_ROOM', '1'))
MISSING = 99999999


def portal_code(o):
    """Map a portal outcome (page message / HTTP status) onto the contract error code it corresponds to."""
    if o.ok:
        return None
    m = (o.message or '')
    for needle, code in (('is not open for booking', 'SESSION_NOT_OPEN'), ('already has an active booking', 'DUPLICATE_BOOKING'),
                         ('No accommodation seat', 'NO_ACCOMMODATION_SEAT'), ('cannot be cancelled', 'NOT_CANCELLABLE'),
                         ('Invalid capacity', 'INVALID_CAPACITY'), ('Capacity cannot be negative', 'INVALID_CAPACITY'),
                         ('cannot be moved', 'DIFFERENT_EXAM'),
                         ('was not found', 'CANDIDATE_NOT_FOUND'), ('Unknown accommodation code', 'VALIDATION'),
                         ('Enter the new capacity', 'VALIDATION'), ('Choose the new session', 'VALIDATION'),
                         ('Not eligible', 'NOT_ELIGIBLE')):
        if needle in m:
            return code
    if o.http_status == 404:
        return 'HTTP_404'
    return 'UNMAPPED:%s' % m[:160]


def code_of(channel, o):
    return portal_code(o) if channel == 'PORTAL' else (None if o.ok else o.error)


class Recorder:
    def __init__(self):
        self.checks = []
        self.category = None
        self.extra = {}

    def check(self, name, ok, detail=None, na=False):
        self.checks.append({'category': self.category, 'name': name, 'ok': bool(ok), 'na': na,
                            'detail': None if ok and not na else detail})
        mark = 'n/a ' if na else ('PASS' if ok else 'FAIL')
        print('  [%s] %s%s' % (mark, name, '' if ok and not na else '  -- %s' % (detail,)), flush=True)
        return ok

    def eq(self, name, actual, expected):
        return self.check(name, actual == expected, None if actual == expected else {'actual': actual, 'expected': expected})


class Ctx:
    def __init__(self, target, rec):
        self.t, self.rec = target, rec
        self.model = Model()
        self._hour = 0
        cands = target.ref.rows("""SELECT c.CandidateId, c.CandidateNumber, c.DefaultAccommodationCode FROM dbo.Candidate c
            WHERE NOT EXISTS (SELECT 1 FROM dbo.Result r WHERE r.CandidateId = c.CandidateId) ORDER BY c.CandidateNumber""")
        self.plain = [(int(a), b) for a, b, d in cands if d is None]
        self.et_default = [(int(a), b, d) for a, b, d in cands if d in ('ET25', 'ET50')]
        self._next = 0
        self.ext = 0
        self.center = int(target.ref.scalar('SELECT TestCenterId FROM dbo.Room WHERE RoomId = %d' % ROOM))
        self.model.partner_seats = self.probe_partner_seats()
        target.partner_seats = self.model.partner_seats
        rec.extra['hb01'] = {'partnerSeatsPerBooking': self.model.partner_seats,
                             'status': 'kept (double decrement)' if self.model.partner_seats == 2 else 'removed (single writer)'}
        print('[e2e] HB-01 partner seats per BOOKED booking: %d' % self.model.partner_seats, flush=True)

    def probe_partner_seats(self):
        """HB-01 is a deployment property (legacy: booking-api + trg_Booking_SeatSync; migrated: scheduling.legacy-seat-
        accounting). Observe it once on a throwaway session instead of assuming it: one partner booking, read the seat delta."""
        self._hour += 1
        sid = self.session(1, 4, 0, _probe=True)
        cid, num = self.plain[-1]
        o = self.t.partner.book(sid, num, None, 'E2E-PROBE')
        if not o.ok:
            raise RuntimeError('HB-01 probe booking failed: %r' % o)
        seats = 4 - self.t.session(sid)['seats']
        self.t.partner.cancel(o.booking_id, 'probe')
        return seats

    def candidates(self, n):
        """Candidates with no Result rows (always retake-eligible). Pool is reused across categories: duplicate checks
        are per session (HB-21) and every category books onto its own fresh sessions."""
        out = []
        for _ in range(n):
            out.append(self.plain[self._next % len(self.plain)])
            self._next += 1
        return out

    def session(self, exam, capacity, acc_cap=0, status='OPEN', starts=None, _probe=False):
        """Self-contained fixture session, dated off the pinned clock (E2E_AS_OF + 45 days + n hours)."""
        self._hour += 1
        starts = starts or (datetime.datetime.fromisoformat(AS_OF) + datetime.timedelta(days=45, hours=self._hour))
        stamp = starts.strftime('%Y-%m-%dT%H:%M:%S')
        if self.t.sched:
            r = self.t.sched.call('POST', '/sessions', body={
                'examId': exam, 'testCenterId': self.center, 'roomId': ROOM, 'startsAt': stamp, 'capacity': capacity,
                'accommodationCapacity': acc_cap, 'seatsAvailable': capacity, 'status': status})
            if r.status != 201:
                raise RuntimeError('create session failed: %r' % r)
            sid = int(r.json['sessionId'])
        else:
            sid = int(self.t.state.scalar(
                "DECLARE @t TABLE (id INT); INSERT INTO dbo.ExamSession (ExamId, TestCenterId, RoomId, StartsAt, Capacity, "
                "AccommodationCapacity, SeatsAvailable, Status) OUTPUT INSERTED.SessionId INTO @t "
                "VALUES (%d, %d, %d, '%s', %d, %d, %d, '%s'); SELECT id FROM @t"
                % (exam, self.center, ROOM, stamp, capacity, acc_cap, capacity, status)))
        if not _probe:
            self.model.add_session(sid, exam, capacity, acc_cap, status)
        return sid

    # ---- channel operations: each is applied to the model and the outcome/row checked against it
    def book(self, channel, sid, cand, acc=None, expect=None, label=None, default_acc=None):
        cid, number = cand[0], cand[1]
        ext = None
        if channel == 'PORTAL':
            o = self.t.portal.book(sid, number, acc or '')
        else:
            self.ext += 1
            ext = 'E2E-%05d' % self.ext
            o = self.t.partner.book(sid, number, acc, ext)
        macc = acc.upper() if (channel == 'PARTNER' and acc) else acc
        try:
            self.model.check_book(sid, cid, macc, channel, default_acc)
            exp_err = None
        except ModelError as e:
            exp_err = e.code
        label = label or '%s book %s on %s%s' % (channel.lower(), number, sid, ' acc=%s' % acc if acc else '')
        if exp_err or expect:
            want = expect or exp_err
            got = o.status if o.ok else code_of(channel, o)
            self.rec.check('%s -> %s' % (label, want), got == want, None if got == want else repr(o))
            if o.ok and not exp_err:
                self.model.book(o.booking_id, sid, cid, macc, channel, default_acc)
                return o.booking_id
            return None
        if not o.ok:
            self.rec.check(label, False, repr(o))
            return None
        status = self.model.book(o.booking_id, sid, cid, macc, channel, default_acc)
        row = self.t.booking(o.booking_id)
        exp_acc = macc or (default_acc if channel == 'PORTAL' else None)
        want = {'status': status, 'channel': channel, 'acc': exp_acc, 'externalRef': ext, 'sessionId': sid, 'candidateId': cid}
        self.rec.check('%s -> %s' % (label, status), o.status == status and row == want,
                       None if o.status == status and row == want else {'outcome': repr(o), 'row': row, 'expected': want})
        return o.booking_id

    def cancel(self, channel, bid, expect=None):
        o = self.t.portal.cancel(bid, 'e2e') if channel == 'PORTAL' else self.t.partner.cancel(bid, 'e2e')
        try:
            if bid not in self.model.bookings:
                raise ModelError('BOOKING_NOT_FOUND')
            self.model.cancel(bid)
            exp = None
        except ModelError as e:
            exp = e.code
        exp = expect or exp
        got = code_of(channel, o)
        return self.rec.check('%s cancel %s -> %s' % (channel.lower(), bid, exp or 'CANCELLED'), got == exp,
                              None if got == exp else repr(o))

    def reschedule(self, channel, bid, new_sid, expect=None):
        label = '%s reschedule %s -> session %s' % (channel.lower(), bid, new_sid)
        old_ext = self.t.booking(bid)['externalRef'] if bid in self.model.bookings else None
        o = self.t.portal.reschedule(bid, new_sid) if channel == 'PORTAL' else self.t.partner.reschedule(bid, new_sid)
        if expect:
            got = code_of(channel, o)
            return self.rec.check('%s -> %s' % (label, expect), got == expect, None if got == expect else repr(o))
        if not o.ok:
            return self.rec.check(label, False, repr(o))
        b = self.model.bookings[bid]
        self.model.cancel(bid)
        status = self.model.book(o.booking_id, new_sid, b['cand'], b['acc'], channel)
        row = self.t.booking(o.booking_id)
        # HB-10: portal reschedule books a fresh PORTAL booking without externalRef; HB-11: partner keeps PARTNER + ref
        want = (status, channel, b['acc'], old_ext if channel == 'PARTNER' else None)
        got = (o.status, row['channel'], row['acc'], row['externalRef'])
        self.rec.check('%s -> new booking %s (HB-10/HB-11 channel/acc/ref)' % (label, status), got == want and o.booking_id != bid,
                       None if got == want else {'actual': got, 'expected': want})
        return o.booking_id

    def capacity(self, sid, new):
        o = self.t.portal.change_capacity(sid, new)
        try:
            self.model.change_capacity(sid, new)
            exp = None
        except ModelError as e:
            exp = e.code
        got = portal_code(o)
        return self.rec.check('portal capacity %s -> %s: %s' % (sid, new, exp or 'UPDATED'), got == exp,
                              None if got == exp else repr(o))

    def attend(self, bid, score, expect_status, expect_http):
        r = self.t.results.post(bid, score)
        j = r.json if isinstance(r.json, dict) else {}
        ok = r.status == expect_http and j.get('status', j.get('error')) == expect_status
        if ok and expect_http in (200, 201):
            self.model.attend(bid)
        return self.rec.check('results POST booking %s score %s -> %s %s' % (bid, score, expect_http, expect_status), ok,
                              None if ok else repr(r))

    def state(self, sid):
        s = self.t.session(sid)
        exp = self.model.expected(sid)
        return {'capacity': s['capacity'], 'seats': s['seats'], 'status': s['status'],
                'waitlist': [tuple(w) for w in self.t.waitlist(sid)],
                'bookings': {bid: self.t.booking(bid)['status'] for bid in exp['bookings']}}

    def verify(self, sid, label=''):
        return self.rec.eq('session %s state matches model%s' % (sid, label), self.state(sid), self.model.expected(sid))
