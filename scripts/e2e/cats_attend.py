"""Category: end-to-end ATTENDED flow through results-service (JSON and CSV score file)."""
import uuid

from adlib import AS_OF, http


def cat_attended(c):
    s = c.session(12, 2)
    cs = c.candidates(4)
    p = c.book('PORTAL', s, cs[0])
    q_ = c.book('PORTAL', s, cs[1])
    w = c.book('PARTNER', s, cs[2])
    x = c.book('PARTNER', s, cs[3])
    c.cancel('PARTNER', x)
    seats_before = c.t.session(s)['seats']
    c.attend(p, 700, 'CREATED', 201)
    c.rec.eq('booking %s is ATTENDED after results ingest' % p, c.t.booking(p)['status'], 'ATTENDED')
    c.rec.eq('attendance leaves SeatsAvailable unchanged', c.t.session(s)['seats'], seats_before)
    r = c.t.ref.rows('SELECT Score, Passed, SessionId FROM dbo.Result WHERE BookingId = %d' % p)
    c.rec.eq('Result row stored, Passed frozen at ingest, future session accepted (HB-18)', r, [['700', '1', str(s)]])
    c.attend(p, 700, 'DUPLICATE', 200)
    r = c.t.results.post(p, 650)
    c.rec.eq('different score for an attended booking -> 409 DUPLICATE_RESULT', (r.status, r.err()), (409, 'DUPLICATE_RESULT'))
    c.rec.eq('booking stays ATTENDED after duplicate and conflict', c.t.booking(p)['status'], 'ATTENDED')
    r = c.t.results.post(w, 700)
    c.rec.eq('results on WAITLISTED -> 409 BOOKING_NOT_ACTIVE', (r.status, r.err()), (409, 'BOOKING_NOT_ACTIVE'))
    r = c.t.results.post(x, 700)
    c.rec.eq('results on CANCELLED -> 409 BOOKING_NOT_ACTIVE', (r.status, r.err()), (409, 'BOOKING_NOT_ACTIVE'))
    csv = ('candidateNumber,sessionId,score,scoredAt\r\n%s,%d,580,%sT10:00:00\r\n' % (cs[1][1], s, AS_OF[:10])).encode()
    r = c.t.results.upload('e2e-%s.csv' % uuid.uuid4().hex[:8], csv)
    attended = c.t.booking(q_)['status'] == 'ATTENDED'
    c.rec.check('CSV score-file upload marks booking %s ATTENDED' % q_, r.status == 200 and attended, repr(r))
    if attended:
        c.model.attend(q_)
    c.cancel('PORTAL', p)
    c.cancel('PARTNER', q_)
    r = http('GET', c.t.results.url + '/api/v1/results/candidate/' + cs[0][1])
    c.rec.check('GET /results/candidate lists the ingested result', r.status == 200 and str(p) in r.text, repr(r))
    pg = c.t.portal.get('/Booking/MyBookings?candidateNumber=' + cs[0][1])
    c.rec.check('portal My Bookings shows booking %s as ATTENDED' % p, pg.status == 200 and 'ATTENDED' in pg.text, 'HTTP %s' % pg.status)
    if c.t.sched:
        r = c.t.sched.call('POST', '/bookings/%d/attendance' % p, body={})
        c.rec.check('scheduling-service attendance on ATTENDED is idempotent (200, transitioned=false)',
                    r.status == 200 and (r.json or {}).get('transitioned') is False, repr(r))
        r = c.t.sched.call('POST', '/bookings/%d/attendance' % w, body={})
        c.rec.eq('scheduling-service attendance on WAITLISTED -> 409 BOOKING_NOT_ACTIVE', (r.status, r.err()), (409, 'BOOKING_NOT_ACTIVE'))
    else:
        c.rec.check('scheduling-service attendance endpoint', True, 'legacy: results-service UPDATEs Booking directly', na=True)
    c.verify(s)
