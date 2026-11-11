"""Categories: channel parity, waitlist ordering, accommodation caps."""
from core import portal_code


def cat_parity(c):
    """The same scenario on each channel; both must match the model step by step. The parity table records every
    column where the two channels differ; the check fails if anything other than the documented HB-01/HB-02 seat
    accounting (and its consequences on BOOKED vs WAITLISTED) differs."""
    trace = {}
    for ch in ('PORTAL', 'PARTNER'):
        s1, s2 = c.session(2, 3, 1), c.session(2, 3, 1)
        cs = c.candidates(7)
        steps = []

        def snap(step, bid=None, outcome=None):
            st = c.t.session(s1)
            steps.append({'step': step, 'outcome': outcome or (c.model.bookings[bid]['status'] if bid in c.model.bookings else None),
                          'seats': st['seats'], 'waitlist': [p for _, p in c.t.waitlist(s1)], 'drift': st['drift']})
        b0 = c.book(ch, s1, cs[0]); snap('book c0', b0)
        b1 = c.book(ch, s1, cs[1], 'ET25'); snap('book c1 ET25', b1)
        b2 = c.book(ch, s1, cs[2]); snap('book c2', b2)
        b3 = c.book(ch, s1, cs[3]); snap('book c3', b3)
        b4 = c.book(ch, s1, cs[4]); snap('book c4', b4)
        c.book(ch, s1, cs[0], label='%s duplicate c0' % ch.lower()); snap('duplicate c0', outcome='DUPLICATE_BOOKING')
        c.cancel(ch, b3); snap('cancel c3', outcome='CANCELLED')
        c.cancel(ch, b0); snap('cancel c0', outcome='CANCELLED')
        b5 = c.book(ch, s1, cs[5]); snap('book c5', b5)
        c.reschedule(ch, b1, s2); snap('reschedule c1 to s2', outcome='MOVED')
        b6 = c.book(ch, s1, cs[6], 'ET50'); snap('book c6 ET50', b6)
        c.verify(s1, ' (%s scenario)' % ch.lower())
        c.verify(s2, ' (%s reschedule target)' % ch.lower())
        trace[ch] = steps
    table = []
    for a, b in zip(trace['PORTAL'], trace['PARTNER']):
        diff = [k for k in ('outcome', 'seats', 'waitlist', 'drift') if a[k] != b[k]]
        table.append({'step': a['step'], 'portal': a, 'partner': b, 'differs': diff})
    c.rec.extra['parity_table'] = table
    # error outcomes (duplicate) must be identical; seat-driven differences only where seats already differed before
    errors_equal = all(a['outcome'] == b['outcome'] for a, b in zip(trace['PORTAL'], trace['PARTNER'])
                       if a['outcome'] not in ('BOOKED', 'WAITLISTED'))
    c.rec.check('portal vs partner: non-seat outcomes identical, differences confined to HB-01/HB-02 seat accounting (none when HB-01 is removed)',
                errors_equal and trace['PORTAL'][0]['drift'] == 0 and trace['PARTNER'][0]['drift'] == c.model.partner_seats - 1
                and (c.model.partner_seats == 2 or not any(r['differs'] for r in table)),
                [r for r in table if r['differs']])


def cat_waitlist(c):
    s = c.session(3, 2)
    cs = c.candidates(8)
    a = c.book('PORTAL', s, cs[0])
    c.book('PORTAL', s, cs[1])
    bc = c.book('PARTNER', s, cs[2])
    bd = c.book('PORTAL', s, cs[3])
    c.book('PARTNER', s, cs[4])
    c.book('PORTAL', s, cs[5])
    c.rec.eq('waitlist positions 1..4 in arrival order across both channels', [p for _, p in c.t.waitlist(s)], [1, 2, 3, 4])
    c.cancel('PORTAL', bd)
    c.rec.eq('cancelling a waitlisted booking leaves a gap, no renumbering (HB-06)', [p for _, p in c.t.waitlist(s)], [1, 3, 4])
    c.cancel('PORTAL', a)
    c.rec.eq('cancelling a BOOKED seat promotes nobody (HB-06)', c.t.booking(bc)['status'], 'WAITLISTED')
    c.book('PARTNER', s, cs[6], label='partner book after a release takes the free seat ahead of the queue (HB-06)')
    c.book('PORTAL', s, cs[7], label='next booking appends at MAX(Position)+1')
    c.verify(s)
    # HB-05: a capacity cut demotes the newest BOOKED (BookedAt DESC) and appends them in BookedAt order
    s2 = c.session(3, 4)
    cs = c.candidates(6)
    for i in range(5):
        c.book('PORTAL', s2, cs[i])
    c.capacity(s2, 2)
    c.verify(s2, ' after capacity cut 4 -> 2 (HB-05 demotion order)')
    c.capacity(s2, 5)
    c.verify(s2, ' after raise 2 -> 5 (no promotion, HB-06)')
    c.book('PORTAL', s2, cs[5], label='booking after the raise is BOOKED while demoted entries stay waitlisted')
    c.verify(s2)


def cat_accommodation(c):
    s = c.session(4, 8, 1)
    cs = c.candidates(6)
    a1 = c.book('PORTAL', s, cs[0], 'ET25')
    c.book('PARTNER', s, cs[1], 'ET50', label='partner ET50 beyond AccommodationCapacity=1')
    c.book('PORTAL', s, cs[2], 'SR', label='portal SR is not capped')
    c.book('PARTNER', s, cs[3], 'rb', label='partner rb (lower case) is upper-cased and not capped')
    if c.et_default:
        etc = c.et_default[0]
        c.book('PORTAL', s, etc, None, default_acc=etc[2], label='portal blank acc falls back to default %s (HB-09)' % etc[2])
        c.book('PARTNER', s, etc, None, label='partner blank acc stays NULL (HB-09)')
    c.cancel('PORTAL', a1)
    c.book('PARTNER', s, cs[1], 'ET50', label='partner ET50 after the ET seat is released')
    c.book('PORTAL', s, cs[4], 'ET25', label='portal ET25 once the ET seat is taken again')
    c.verify(s)
    s2 = c.session(4, 1, 1)
    cs = c.candidates(3)
    c.book('PORTAL', s2, cs[0])
    c.book('PORTAL', s2, cs[1], 'ET25', label='ET25 on a full session waitlists (cap counts BOOKED+ATTENDED only)')
    c.book('PARTNER', s2, cs[2], 'ET50', label='a second waitlisted ET booking is still accepted')
    c.verify(s2)
    s3 = c.session(4, 3, 1)
    cs = c.candidates(4)
    b = c.book('PORTAL', s3, cs[0], 'ET25')
    c.attend(b, 720, 'CREATED', 201)
    c.book('PARTNER', s3, cs[1], 'ET50', label='an ATTENDED ET booking keeps its accommodation seat')
    c.capacity(s3, 0)
    o = c.t.portal.book(s3, cs[2][1], 'XX')
    c.rec.eq('portal unknown accommodation code -> VALIDATION', portal_code(o), 'VALIDATION')
    o = c.t.partner.book(s3, cs[3][1], 'XX')
    c.rec.eq('partner unknown accommodation code -> 400 VALIDATION', (o.http_status, o.error), (400, 'VALIDATION'))
    c.verify(s3, ' (rejected requests changed nothing)')
