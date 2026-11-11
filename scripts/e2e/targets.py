"""Stack under test. `legacy` = estate/legacy-baseline (procs + triggers in AssessmentDelivery). `migrated` = the
Phase 2 stack: scheduling-service owns ExamSession/Booking/Waitlist in its own database, portal/booking-api/
results-service call it over HTTP. Reference data (Exam, TestCenter, Room, Candidate) and Result stay in
AssessmentDelivery for both."""
import os

from adlib import Sql, http, q
from channels import Partner, Portal, Results, Scheduling

# Core of db/checks/seat-drift.sql (same formula: Capacity - (BOOKED + ATTENDED) - SeatsAvailable) without the
# Exam/TestCenter display joins, so it runs on whichever database owns the scheduling tables. SERIALIZABLE +
# DEADLOCK_PRIORITY LOW gives one consistent snapshot per sample under load and makes the sampler, never the
# workload, the deadlock victim.
DRIFT_SQL = """SET TRANSACTION ISOLATION LEVEL SERIALIZABLE; SET DEADLOCK_PRIORITY LOW; SET LOCK_TIMEOUT 10000;
SELECT s.SessionId, s.Capacity, s.SeatsAvailable, ISNULL(b.ActiveBookings, 0),
       (s.Capacity - ISNULL(b.ActiveBookings, 0)) - s.SeatsAvailable AS Drift
FROM {es} s
LEFT JOIN (SELECT SessionId, COUNT(*) AS ActiveBookings FROM {bk}
            WHERE Status IN ('BOOKED','ATTENDED') GROUP BY SessionId) b ON b.SessionId = s.SessionId
WHERE s.SeatsAvailable <> s.Capacity - ISNULL(b.ActiveBookings, 0) {extra}
ORDER BY s.SessionId"""


class Target:
    def __init__(self, name):
        self.name = name
        env = os.environ.get
        self.portal = Portal(env('PORTAL_URL', 'http://localhost:8080'))
        self.partner = Partner(env('BOOKING_API_URL', 'http://localhost:8081'))
        self.results = Results(env('RESULTS_URL', 'http://localhost:8082'))
        self.ref = Sql(env('AD_DB', 'AssessmentDelivery'))
        if name == 'legacy':
            self.state = self.ref
            self.sched = None
            self.audit_tables = [(self.ref, 'dbo.AuditLog')]
        elif name == 'migrated':
            self.state = Sql(env('SCHED_DB', 'Scheduling'))
            self.sched = Scheduling(env('SCHEDULING_URL', 'http://localhost:8084'))
            self.audit_tables = [(self.ref, 'dbo.AuditLog')] + [
                (self.state, t) for t in env('SCHED_AUDIT_TABLES', 'dbo.AuditLog').split(',') if t]
        else:
            raise ValueError('unknown target %s' % name)
        self.es = env('SCHED_TABLE_SESSION', 'dbo.ExamSession')
        self.bk = env('SCHED_TABLE_BOOKING', 'dbo.Booking')
        self.wl = env('SCHED_TABLE_WAITLIST', 'dbo.Waitlist')

    # -------- state reads (scheduling tables live in self.state)
    def session(self, sid):
        r = self.state.rows("""SELECT s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, s.Status, s.ExamId,
            (SELECT COUNT(*) FROM {bk} b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED')),
            (SELECT COUNT(*) FROM {bk} b WHERE b.SessionId = s.SessionId AND b.Status = 'WAITLISTED'),
            (SELECT COUNT(*) FROM {wl} w WHERE w.SessionId = s.SessionId),
            (SELECT COUNT(*) FROM {bk} b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED')
                AND b.AccommodationCode IN ('ET25','ET50'))
            FROM {es} s WHERE s.SessionId = {sid}""".format(es=self.es, bk=self.bk, wl=self.wl, sid=int(sid)))
        if not r:
            return None
        c, acc, seats, st, exam, booked, wlb, wl, et = r[0]
        cap = int(c)
        return {'capacity': cap, 'accCap': int(acc), 'seats': int(seats), 'status': st, 'examId': int(exam),
                'booked': int(booked), 'waitlistedBookings': int(wlb), 'waitlistRows': int(wl), 'etBooked': int(et),
                'drift': cap - int(booked) - int(seats)}

    def booking(self, bid):
        r = self.state.rows("SELECT Status, Channel, ISNULL(AccommodationCode,'NULL'), ISNULL(ExternalRef,'NULL'), SessionId, CandidateId "
                            "FROM %s WHERE BookingId = %d" % (self.bk, int(bid)))
        if not r:
            return None
        st, ch, acc, ext, sid, cid = r[0]
        return {'status': st, 'channel': ch, 'acc': None if acc == 'NULL' else acc,
                'externalRef': None if ext == 'NULL' else ext, 'sessionId': int(sid), 'candidateId': int(cid)}

    def waitlist(self, sid):
        """[(BookingId, Position)] in position order."""
        return [(int(a), int(b)) for a, b in self.state.rows(
            "SELECT BookingId, Position FROM %s WHERE SessionId = %d ORDER BY Position, WaitlistId" % (self.wl, int(sid)))]

    def drift(self, session_ids=None):
        extra = ''
        if session_ids:
            extra = 'AND s.SessionId IN (%s)' % ','.join(str(int(s)) for s in session_ids)
        rows = self.state.rows(DRIFT_SQL.format(es=self.es, bk=self.bk, extra=extra))
        return {int(r[0]): int(r[4]) for r in rows}

    def candidate_id(self, number):
        return int(self.ref.scalar("SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = %s" % q(number)))

    def health(self):
        urls = {'portal': self.portal.url + '/Booking', 'booking-api': self.partner.url + '/api/v1/sessions/1',
                'results-service': self.results.url + '/api/v1/results/candidate/NW00000001'}
        if self.sched:
            urls['scheduling-service'] = self.sched.base + '/sessions/1'
        return {k: http('GET', u, timeout=60).status for k, u in urls.items()}
