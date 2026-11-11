"""Category: every error condition in docs/to-be/scheduling-service-contract.md section 4, per channel, plus
"rejected requests change nothing"."""
import datetime
import uuid

from adlib import AS_OF, http, q
from core import MISSING, portal_code


def cat_errors(c):
    s = c.session(6, 2, 1)
    other_exam = c.session(7, 2)
    same_exam = c.session(6, 2)
    closed = {st: c.session(6, 5, 0, st) for st in ('CLOSED', 'CANCELLED', 'DELIVERED')}
    cs = c.candidates(8)
    b = c.book('PORTAL', s, cs[0])
    bp = c.book('PARTNER', s, cs[1])
    for st, sid in closed.items():
        c.book('PORTAL', sid, cs[2], label='portal book on a %s session' % st)
        c.book('PARTNER', sid, cs[2], label='partner book on a %s session' % st)
    c.book('PORTAL', s, cs[0], label='portal duplicate')
    c.book('PARTNER', s, cs[0], label='partner duplicate of a portal booking (per session, HB-21)')
    c.book('PORTAL', s, cs[3], 'ET25')
    c.book('PARTNER', s, cs[4], 'ET50', label='partner ET50 with the ET seat taken')
    before = c.state(s)
    o = c.t.portal.book(s, 'NW99999999')
    c.rec.eq('portal unknown candidate -> CANDIDATE_NOT_FOUND', portal_code(o), 'CANDIDATE_NOT_FOUND')
    o = c.t.partner.book(s, 'NW99999999')
    c.rec.eq('partner unknown candidate -> 404 CANDIDATE_NOT_FOUND', (o.http_status, o.error), (404, 'CANDIDATE_NOT_FOUND'))
    c.rec.eq('portal book form for a missing session -> 404',
             c.t.portal.get('/Booking/Book?sessionId=%d&candidateNumber=%s' % (MISSING, cs[5][1])).status, 404)
    o = c.t.partner.book(MISSING, cs[5][1])
    c.rec.eq('partner missing session -> 404 SESSION_NOT_FOUND', (o.http_status, o.error), (404, 'SESSION_NOT_FOUND'))
    for body, why in (({'candidateNumber': cs[5][1]}, 'missing sessionId'), ({'sessionId': s}, 'missing candidateNumber'),
                      ({'sessionId': s, 'candidateNumber': cs[5][1], 'externalRef': 'X' * 41}, 'externalRef over 40 chars')):
        o = c.t.partner.book_raw(body)
        c.rec.eq('partner %s -> 400 VALIDATION' % why, (o.http_status, o.error), (400, 'VALIDATION'))
    o = c.t.partner.reschedule(bp, None)
    c.rec.eq('partner reschedule without newSessionId -> 400 VALIDATION', (o.http_status, o.error), (400, 'VALIDATION'))
    o = c.t.portal.reschedule(b, '')
    c.rec.eq('portal reschedule without a target -> VALIDATION', portal_code(o), 'VALIDATION')
    c.reschedule('PORTAL', b, other_exam, expect='DIFFERENT_EXAM')
    c.reschedule('PARTNER', bp, other_exam, expect='DIFFERENT_EXAM')
    c.reschedule('PARTNER', bp, s, expect='SAME_SESSION')
    c.reschedule('PORTAL', b, MISSING, expect='DIFFERENT_EXAM')  # usp_RescheduleBooking state 6 also covers a missing target
    o = c.t.partner.reschedule(bp, MISSING)
    c.rec.eq('partner reschedule to a missing session -> 404 SESSION_NOT_FOUND', (o.http_status, o.error), (404, 'SESSION_NOT_FOUND'))
    o = c.t.partner.reschedule(MISSING, same_exam)
    c.rec.eq('partner reschedule of a missing booking -> 404 BOOKING_NOT_FOUND', (o.http_status, o.error), (404, 'BOOKING_NOT_FOUND'))
    o = c.t.partner.cancel(MISSING)
    c.rec.eq('partner cancel of a missing booking -> 404 BOOKING_NOT_FOUND', (o.http_status, o.error), (404, 'BOOKING_NOT_FOUND'))
    c.rec.eq('partner GET of a missing booking -> 404', c.t.partner.booking(MISSING).status, 404)
    c.rec.eq('portal cancel form for a missing booking -> 404', c.t.portal.get('/Booking/Cancel/%d' % MISSING).status, 404)
    r = c.t.results.post(MISSING, 700)
    c.rec.eq('results for a missing booking -> 404 BOOKING_NOT_FOUND', (r.status, r.err()), (404, 'BOOKING_NOT_FOUND'))
    for score in (-1, 1001):
        r = c.t.results.post(b, score)
        c.rec.eq('results score %s -> 400 VALIDATION' % score, (r.status, r.err()), (400, 'VALIDATION'))
    r = http('POST', c.t.results.url + '/api/v1/results', body={'score': 700})
    c.rec.eq('results without bookingId -> 400 VALIDATION', (r.status, r.err()), (400, 'VALIDATION'))
    c.capacity(s, -1)
    c.capacity(s, 0)  # below AccommodationCapacity = 1
    o = c.t.portal.change_capacity(s, '')
    c.rec.eq('portal capacity left blank -> VALIDATION', portal_code(o), 'VALIDATION')
    c.rec.eq('portal capacity form for a missing session -> 404', c.t.portal.get('/Admin/Sessions/Capacity/%d' % MISSING).status, 404)
    c.rec.eq('rejected requests left session %s untouched' % s, c.state(s), before)
    c.cancel('PORTAL', b)
    c.cancel('PORTAL', b, expect='NOT_CANCELLABLE')
    c.cancel('PARTNER', b, expect='NOT_CANCELLABLE')
    # usp_RescheduleBooking has no same-session guard: the portal can move a booking onto its own session
    c.reschedule('PORTAL', c.book('PORTAL', same_exam, cs[6]), same_exam)
    not_eligible(c)
    for sid in (s, other_exam, same_exam):
        c.verify(sid)
    sched_errors(c, other_exam, cs)


def not_eligible(c):
    """NOT_ELIGIBLE (booking-api) / portal "Not eligible": latest attempt failed shortly before the session."""
    r = c.t.ref.rows("""SELECT TOP 1 c.CandidateId, c.CandidateNumber, r.ExamId, CONVERT(VARCHAR(19), r.ScoredAt, 126)
        FROM dbo.Result r JOIN dbo.Candidate c ON c.CandidateId = r.CandidateId
        WHERE r.Passed = 0 AND r.ScoredAt < %s AND NOT EXISTS (SELECT 1 FROM dbo.Result r2 WHERE r2.CandidateId = r.CandidateId
              AND r2.ExamId = r.ExamId AND (r2.ScoredAt > r.ScoredAt OR (r2.ScoredAt = r.ScoredAt AND r2.ResultId > r.ResultId)))
        ORDER BY r.ScoredAt DESC, r.ResultId DESC""" % q(AS_OF))
    if not r:
        c.rec.check('a failed attempt exists in the copy for the NOT_ELIGIBLE check', False, 'none found')
        return
    cid, num, exam = int(r[0][0]), r[0][1], int(r[0][2])
    scored = datetime.datetime.fromisoformat(r[0][3])
    ne = c.session(exam, 5, 0, starts=(scored + datetime.timedelta(days=7)).replace(hour=9, minute=0, second=0))
    o = c.t.portal.book(ne, num)
    c.rec.eq('portal retake inside the wait -> Not eligible (%s, exam %s)' % (num, exam), portal_code(o), 'NOT_ELIGIBLE')
    o = c.t.partner.book(ne, num)
    c.rec.eq('partner retake inside the wait -> 409 NOT_ELIGIBLE', (o.http_status, o.error), (409, 'NOT_ELIGIBLE'))
    c.verify(ne)


def sched_errors(c, other_exam, cs):
    """Contract-only surfaces, called on scheduling-service directly. Legacy has no such surface."""
    if not c.t.sched:
        for n in ('IDEMPOTENCY_KEY_REUSED', 'VERSION_MISMATCH', 'INVALID_CAPACITY', 'VALIDATION', 'SESSION_NOT_FOUND',
                  'BOOKING_NOT_FOUND', 'SAME_SESSION', 'DIFFERENT_EXAM', 'DUPLICATE_BOOKING', 'NOT_CANCELLABLE', 'SESSION_NOT_OPEN'):
            c.rec.check('scheduling-service %s' % n, True, 'legacy target: no scheduling-service', na=True)
        return
    api = c.t.sched
    sid = c.session(8, 3)
    key = str(uuid.uuid4())
    body = {'sessionId': sid, 'candidateNumber': cs[7][1], 'channel': 'PARTNER'}
    r1 = api.call('POST', '/bookings', body=body, headers={'Idempotency-Key': key, 'X-Actor': 'e2e'})
    r2 = api.call('POST', '/bookings', body=body, headers={'Idempotency-Key': key, 'X-Actor': 'e2e'})
    replayed = any(k.lower() == 'idempotent-replayed' and v == 'true' for k, v in r2.headers.items())
    c.rec.check('same Idempotency-Key + body replays the original booking (Idempotent-Replayed: true)',
                r1.status == 201 and r2.status == 201 and r1.json == r2.json and replayed, (repr(r1), repr(r2)))
    if r1.status == 201:
        c.model.book(r1.json['bookingId'], sid, cs[7][0], None, 'PARTNER')
    r = api.call('POST', '/bookings', body=dict(body, accommodationCode='SR'), headers={'Idempotency-Key': key})
    c.rec.eq('scheduling-service IDEMPOTENCY_KEY_REUSED -> 422', (r.status, r.err()), (422, 'IDEMPOTENCY_KEY_REUSED'))
    r = api.call('POST', '/sessions/%d/capacity' % sid, body={'newCapacity': 4}, headers={'If-Match': '"stale"'})
    c.rec.eq('scheduling-service VERSION_MISMATCH -> 412', (r.status, r.err()), (412, 'VERSION_MISMATCH'))
    r = api.call('POST', '/sessions/%d/capacity' % sid, body={'newCapacity': -1})
    c.rec.eq('scheduling-service INVALID_CAPACITY -> 422', (r.status, r.err()), (422, 'INVALID_CAPACITY'))
    r = api.call('POST', '/bookings', body={'sessionId': sid, 'candidateNumber': cs[6][1]})
    c.rec.eq('scheduling-service missing channel -> 400 VALIDATION', (r.status, r.err()), (400, 'VALIDATION'))
    r = api.call('POST', '/bookings', body={'sessionId': MISSING, 'candidateNumber': cs[6][1], 'channel': 'PORTAL'})
    c.rec.eq('scheduling-service SESSION_NOT_FOUND -> 404', (r.status, r.err()), (404, 'SESSION_NOT_FOUND'))
    r = api.call('POST', '/bookings/%d/cancel' % MISSING, body={'reason': 'e2e'})
    c.rec.eq('scheduling-service BOOKING_NOT_FOUND -> 404', (r.status, r.err()), (404, 'BOOKING_NOT_FOUND'))
    if r1.status == 201:
        bid = r1.json['bookingId']
        r = api.call('POST', '/bookings/%d/reschedule' % bid, body={'newSessionId': sid, 'channel': 'PARTNER'})
        c.rec.eq('scheduling-service SAME_SESSION -> 409', (r.status, r.err()), (409, 'SAME_SESSION'))
        r = api.call('POST', '/bookings/%d/reschedule' % bid, body={'newSessionId': other_exam, 'channel': 'PORTAL'})
        c.rec.eq('scheduling-service DIFFERENT_EXAM -> 409', (r.status, r.err()), (409, 'DIFFERENT_EXAM'))
        r = api.call('POST', '/bookings', body=body)
        c.rec.eq('scheduling-service DUPLICATE_BOOKING -> 409', (r.status, r.err()), (409, 'DUPLICATE_BOOKING'))
        r = api.call('POST', '/bookings/%d/cancel' % bid, body={'reason': 'e2e'})
        if r.status == 200:
            c.model.cancel(bid)
        r = api.call('POST', '/bookings/%d/cancel' % bid, body={'reason': 'e2e'})
        c.rec.eq('scheduling-service NOT_CANCELLABLE -> 409', (r.status, r.err()), (409, 'NOT_CANCELLABLE'))
    closed = c.session(8, 3, 0, 'CLOSED')
    r = api.call('POST', '/bookings', body={'sessionId': closed, 'candidateNumber': cs[6][1], 'channel': 'PORTAL'})
    c.rec.eq('scheduling-service SESSION_NOT_OPEN -> 409', (r.status, r.err()), (409, 'SESSION_NOT_OPEN'))
    c.verify(sid)
