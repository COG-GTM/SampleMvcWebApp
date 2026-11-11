"""DB-state readers. Both return the same raw shape so one normalizer serves legacy and modern runs.

legacy     -> the AssessmentDelivery tables (legacy estate, or modern writes projected back by the M4 relay)
scheduling -> scheduling-service's own Scheduling tables (exam_session, booking, waitlist_entry)
"""


def _in(ids):
    ids = sorted(set(int(i) for i in ids))
    return ",".join(str(i) for i in ids) if ids else "NULL"


class LegacyState:
    def __init__(self, db):
        self.db = db

    def max_ids(self):
        r = self.db.rows("SELECT (SELECT ISNULL(MAX(BookingId),0) FROM dbo.Booking) AS Booking, "
                         "(SELECT ISNULL(MAX(AuditId),0) FROM dbo.AuditLog) AS AuditLog")[0]
        return {k: int(v) for k, v in r.items()}

    def booking_sessions(self, booking_ids):
        return [r["SessionId"] for r in self.db.rows(
            "SELECT DISTINCT SessionId FROM dbo.Booking WHERE BookingId IN (%s)" % _in(booking_ids))]

    def sessions_with_new_bookings(self, after_id):
        return [r["SessionId"] for r in self.db.rows(
            "SELECT DISTINCT SessionId FROM dbo.Booking WHERE BookingId > ?", (after_id,))]

    def read(self, sessions, candidates, audit_after):
        db, s = self.db, _in(sessions)
        out = {"sessions": {}, "bookings": [], "waitlist": [], "results": [], "audit": []}
        for r in db.rows("SELECT SessionId, Capacity, AccommodationCapacity, SeatsAvailable, Status "
                         "FROM dbo.ExamSession WHERE SessionId IN (%s)" % s):
            out["sessions"][int(r.pop("SessionId"))] = r
        out["bookings"] = db.rows(
            "SELECT b.BookingId, b.SessionId, c.CandidateNumber, b.Status, b.AccommodationCode, b.Channel, "
            "b.ExternalRef, b.BookedAt, b.CancelledAt, b.CancelReason FROM dbo.Booking b "
            "JOIN dbo.Candidate c ON c.CandidateId = b.CandidateId WHERE b.SessionId IN (%s) "
            "ORDER BY b.BookingId" % s)
        out["waitlist"] = db.rows(
            "SELECT w.SessionId, w.Position, w.BookingId, c.CandidateNumber, w.AddedAt FROM dbo.Waitlist w "
            "JOIN dbo.Candidate c ON c.CandidateId = w.CandidateId WHERE w.SessionId IN (%s) "
            "ORDER BY w.SessionId, w.Position, w.BookingId" % s)
        if candidates:
            marks = ",".join("?" for _ in candidates)
            out["results"] = db.rows(
                "SELECT c.CandidateNumber, e.ExamCode, r.SessionId, s.StartsAt AS SessionStartsAt, r.Score, r.Passed "
                "FROM dbo.Result r JOIN dbo.Candidate c ON c.CandidateId = r.CandidateId "
                "JOIN dbo.Exam e ON e.ExamId = r.ExamId JOIN dbo.ExamSession s ON s.SessionId = r.SessionId "
                "WHERE c.CandidateNumber IN (%s) ORDER BY c.CandidateNumber, s.StartsAt, r.ResultId" % marks,
                tuple(candidates))
        out["audit"] = db.rows(
            "SELECT EntityName, EntityId, Action, Detail, Actor FROM dbo.AuditLog WHERE AuditId > ? "
            "ORDER BY AuditId", (audit_after,))
        return out

    def drift(self, repo_root):
        sql = (repo_root / "db" / "checks" / "seat-drift.sql").read_text(encoding="utf-8")
        from .db import split_batches
        batch = [b for b in split_batches(sql) if "SELECT" in b.upper()][-1]
        return self.db.rows(batch)


class SchedulingState:
    """Reads scheduling-service's own tables and maps them onto the legacy shape (formula A seats)."""

    def __init__(self, db, legacy_db):
        self.db = db              # connection to the Scheduling database
        self.legacy = legacy_db   # AssessmentDelivery, for candidate numbers / exam codes / results

    def max_ids(self):
        legacy = LegacyState(self.legacy).max_ids()
        legacy["Booking"] = max(legacy["Booking"], int(self.db.scalar(
            "SELECT ISNULL(MAX(booking_id),0) AS m FROM booking WHERE booking_id < 50000000") or 0))
        return legacy

    def booking_sessions(self, booking_ids):
        return [r["session_id"] for r in self.db.rows(
            "SELECT DISTINCT session_id FROM booking WHERE booking_id IN (%s)" % _in(booking_ids))]

    def sessions_with_new_bookings(self, after_id):
        return [r["session_id"] for r in self.db.rows(
            "SELECT DISTINCT session_id FROM booking WHERE booking_id > ?", (after_id,))]

    def _numbers(self, ids):
        rows = self.legacy.rows("SELECT CandidateId, CandidateNumber FROM dbo.Candidate WHERE CandidateId IN (%s)"
                                % _in(ids))
        return {r["CandidateId"]: r["CandidateNumber"] for r in rows}

    def read(self, sessions, candidates, audit_after):
        db, s = self.db, _in(sessions)
        out = {"sessions": {}, "bookings": [], "waitlist": [], "results": [], "audit": []}
        for r in db.rows(
                "SELECT es.session_id, es.capacity, es.accommodation_capacity, es.status, "
                "(SELECT COUNT(*) FROM booking b WHERE b.session_id = es.session_id "
                " AND b.status IN ('BOOKED','ATTENDED')) AS active FROM exam_session es "
                "WHERE es.session_id IN (%s)" % s):
            out["sessions"][int(r["session_id"])] = {
                "Capacity": r["capacity"], "AccommodationCapacity": r["accommodation_capacity"],
                "SeatsAvailable": max(0, r["capacity"] - r["active"]), "Status": r["status"]}
        bookings = db.rows("SELECT booking_id, session_id, candidate_id, status, accommodation_code, channel, "
                           "external_ref, booked_at, cancelled_at, cancel_reason FROM booking "
                           "WHERE session_id IN (%s) ORDER BY booking_id" % s)
        waitlist = db.rows("SELECT session_id, position, booking_id, candidate_id, added_at FROM waitlist_entry "
                           "WHERE session_id IN (%s) ORDER BY session_id, position, booking_id" % s)
        numbers = self._numbers([b["candidate_id"] for b in bookings] + [w["candidate_id"] for w in waitlist])
        for b in bookings:
            out["bookings"].append({
                "BookingId": b["booking_id"], "SessionId": b["session_id"],
                "CandidateNumber": numbers.get(b["candidate_id"]), "Status": b["status"],
                "AccommodationCode": b["accommodation_code"], "Channel": b["channel"],
                "ExternalRef": b["external_ref"], "BookedAt": b["booked_at"], "CancelledAt": b["cancelled_at"],
                "CancelReason": b["cancel_reason"]})
        for w in waitlist:
            out["waitlist"].append({"SessionId": w["session_id"], "Position": w["position"],
                                    "BookingId": w["booking_id"], "CandidateNumber": numbers.get(w["candidate_id"]),
                                    "AddedAt": w["added_at"]})
        # Results and audit rows are not owned by scheduling-service: read them from AssessmentDelivery.
        legacy = LegacyState(self.legacy).read([], candidates, audit_after)
        out["results"], out["audit"] = legacy["results"], legacy["audit"]
        return out

    def drift(self, repo_root):
        return LegacyState(self.legacy).drift(repo_root)


def normalize_state(raw, idmap, since):
    from .normalize import norm_datetime
    state = {}
    for sid in sorted(raw["sessions"]):
        state["examSession.%d" % sid] = dict(raw["sessions"][sid])
    by_session = {}
    for b in raw["bookings"]:
        row = {"bookingId": idmap.label(b["BookingId"]), "candidate": b["CandidateNumber"], "status": b["Status"],
               "accommodation": b["AccommodationCode"], "channel": b["Channel"], "externalRef": b["ExternalRef"],
               "bookedAt": norm_datetime(b["BookedAt"], since), "cancelledAt": norm_datetime(b["CancelledAt"], since),
               "cancelReason": b["CancelReason"]}
        by_session.setdefault("bookings.%d" % b["SessionId"], []).append(row)
    for sid in sorted(raw["sessions"]):
        key = "bookings.%d" % sid
        state[key] = by_session.get(key, [])
    wl = {}
    for w in raw["waitlist"]:
        wl.setdefault(w["SessionId"], []).append({
            "position": w["Position"], "bookingId": idmap.label(w["BookingId"]), "candidate": w["CandidateNumber"],
            "addedAt": norm_datetime(w["AddedAt"], since)})
    for sid in sorted(raw["sessions"]):
        state["waitlist.%d" % sid] = wl.get(sid, [])
    if raw["results"]:
        state["results"] = [{"candidate": r["CandidateNumber"], "examCode": r["ExamCode"], "sessionId": r["SessionId"],
                             "sessionStartsAt": norm_datetime(r["SessionStartsAt"], None), "score": r["Score"],
                             "passed": bool(r["Passed"])} for r in raw["results"]]
    state["auditLog.new"] = [{"entity": a["EntityName"], "entityId": idmap.label(a["EntityId"])
                              if a["EntityName"] == "Booking" else a["EntityId"], "action": a["Action"],
                              "detail": idmap.text(a["Detail"]), "actor": a["Actor"]} for a in raw["audit"]]
    return state


def all_booking_ids(raw):
    return [b["BookingId"] for b in raw["bookings"]] + [w["BookingId"] for w in raw["waitlist"]]
