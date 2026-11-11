"""Reference model of legacy booking semantics, used as the oracle for the e2e checks.

Every rule cites the characterized behaviour it reproduces (docs/as-is/hidden-behaviours.md):
  usp_BookSeat / booking-api book   OPEN check, per-session duplicate check (HB-21), ET25/ET50 accommodation cap counted
                                    over BOOKED+ATTENDED, then BOOKED if SeatsAvailable > 0 else WAITLISTED at MAX+1.
  seat accounting                   PORTAL BOOKED takes 1 seat; PARTNER BOOKED takes 2 (booking-api decrement +
                                    trg_Booking_SeatSync, HB-01); cancel of BOOKED releases 1 on both channels (HB-02).
  usp_CancelBooking                 BOOKED -> +1 seat; WAITLISTED -> Waitlist row deleted, no renumbering, no
                                    promotion (HB-06); anything else NOT_CANCELLABLE (HB-22: session state ignored).
  usp_ChangeCapacity + trigger 033  INVALID_CAPACITY when new < 0 or new < AccommodationCapacity; Seats += delta with a
                                    zero floor; on a cut, newest BOOKED (BookedAt DESC, BookingId DESC) beyond the new
                                    capacity are demoted to WAITLISTED, appended at MAX+1 in BookedAt order, and Seats
                                    recomputed as Capacity - BOOKED (HB-05).
  attendance                        BOOKED -> ATTENDED, idempotent, seats/waitlist untouched (HB-18).
  portal blank accommodation        falls back to Candidate.DefaultAccommodationCode; partner blank stays NULL (HB-09).
"""
ET = ('ET25', 'ET50')


class ModelError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


class MSession:
    def __init__(self, sid, exam, capacity, acc_cap, status='OPEN', seats=None):
        self.sid, self.exam, self.capacity, self.acc_cap, self.status = sid, exam, capacity, acc_cap, status
        self.seats = capacity if seats is None else seats
        self.waitlist = []  # [bookingId, position]


class Model:
    def __init__(self):
        self.sessions = {}
        self.bookings = {}  # id -> dict(session, cand, status, acc, channel, order)
        self._order = 0

    def add_session(self, sid, exam, capacity, acc_cap, status='OPEN'):
        self.sessions[sid] = MSession(sid, exam, capacity, acc_cap, status)

    def _active(self, sid, statuses=('BOOKED', 'ATTENDED')):
        return [b for b in self.bookings.values() if b['session'] == sid and b['status'] in statuses]

    def check_book(self, sid, cand, acc, channel, default_acc=None):
        s = self.sessions.get(sid)
        if channel == 'PORTAL' and not acc:
            acc = default_acc
        if s is None:
            raise ModelError('SESSION_NOT_FOUND')
        if s.status != 'OPEN':
            raise ModelError('SESSION_NOT_OPEN')
        if any(b['cand'] == cand for b in self._active(sid, ('BOOKED', 'WAITLISTED'))):
            raise ModelError('DUPLICATE_BOOKING')
        if acc in ET and len([b for b in self._active(sid) if b['acc'] in ET]) >= s.acc_cap:
            raise ModelError('NO_ACCOMMODATION_SEAT')
        return s, acc

    def book(self, bid, sid, cand, acc, channel, default_acc=None):
        """Apply a booking the system reported as bookingId `bid`; returns the expected status."""
        s, acc = self.check_book(sid, cand, acc, channel, default_acc)
        self._order += 1
        if s.seats > 0:
            status = 'BOOKED'
            s.seats -= 2 if channel == 'PARTNER' else 1
        else:
            status = 'WAITLISTED'
            s.waitlist.append([bid, max([p for _, p in s.waitlist] or [0]) + 1])
        self.bookings[bid] = dict(session=sid, cand=cand, status=status, acc=acc, channel=channel, order=self._order)
        return status

    def cancel(self, bid):
        b = self.bookings.get(bid)
        if b is None:
            raise ModelError('BOOKING_NOT_FOUND')
        if b['status'] not in ('BOOKED', 'WAITLISTED'):
            raise ModelError('NOT_CANCELLABLE')
        s = self.sessions[b['session']]
        if b['status'] == 'BOOKED':
            s.seats += 1
        else:
            s.waitlist = [w for w in s.waitlist if w[0] != bid]
        b['status'] = 'CANCELLED'

    def attend(self, bid):
        b = self.bookings.get(bid)
        if b is None:
            raise ModelError('BOOKING_NOT_FOUND')
        if b['status'] not in ('BOOKED', 'ATTENDED'):
            raise ModelError('BOOKING_NOT_ACTIVE')
        transitioned = b['status'] == 'BOOKED'
        b['status'] = 'ATTENDED'
        return transitioned

    def change_capacity(self, sid, new):
        s = self.sessions.get(sid)
        if s is None:
            raise ModelError('SESSION_NOT_FOUND')
        if new < 0 or new < s.acc_cap:
            raise ModelError('INVALID_CAPACITY')
        old = s.capacity
        s.seats = max(0, s.seats + new - old)
        s.capacity = new
        if new < old:
            booked = sorted(self._active(sid, ('BOOKED',)), key=lambda b: b['order'], reverse=True)
            demote = booked[:max(0, len(booked) - new)]
            if demote:
                ids = {bid for bid, b in self.bookings.items() if any(b is d for d in demote)}
                pos = max([p for _, p in s.waitlist] or [0])
                for bid in sorted(ids, key=lambda i: self.bookings[i]['order']):
                    self.bookings[bid]['status'] = 'WAITLISTED'
                    pos += 1
                    s.waitlist.append([bid, pos])
                s.seats = new - len(self._active(sid, ('BOOKED',)))

    def expected(self, sid):
        s = self.sessions[sid]
        return {'capacity': s.capacity, 'seats': s.seats, 'status': s.status,
                'waitlist': [tuple(w) for w in sorted(s.waitlist, key=lambda w: w[1])],
                'bookings': {bid: b['status'] for bid, b in sorted(self.bookings.items()) if b['session'] == sid}}
