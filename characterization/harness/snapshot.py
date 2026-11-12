"""DB-state snapshots of the rows a scenario touches (ExamSession, Booking, Waitlist, Result, AuditLog)."""


def _in(values, quote=False):
    vals = sorted({str(v) for v in values if v is not None})
    if not vals:
        return "(NULL)"
    return "(" + ",".join(f"'{v}'" if quote else str(int(v)) for v in vals) + ")"


def baseline(db):
    row = db.json("SELECT (SELECT ISNULL(MAX(BookingId),0) FROM dbo.Booking) AS booking, "
                  "(SELECT ISNULL(MAX(WaitlistId),0) FROM dbo.Waitlist) AS waitlist, "
                  "(SELECT ISNULL(MAX(AuditId),0) FROM dbo.AuditLog) AS audit, "
                  "(SELECT ISNULL(MAX(ResultId),0) FROM dbo.Result) AS result, "
                  "CONVERT(varchar(19), GETDATE(), 126) AS now, "
                  "DATEPART(TZOFFSET, SYSDATETIMEOFFSET()) AS tzOffset")[0]
    return row


def take(db, sessions, candidates, audit_after):
    s_in, c_in = _in(sessions), _in(candidates, quote=True)
    snap = {}
    snap["sessions"] = db.json(
        "SELECT s.SessionId, e.ExamCode, CONVERT(varchar(16), s.StartsAt, 126) AS StartsAt, s.Capacity, "
        "s.AccommodationCapacity, s.SeatsAvailable, s.Status, s.LastModified, "
        "(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED')) AS BookedOrAttended, "
        "(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status IN ('BOOKED','ATTENDED') AND b.AccommodationCode IN ('ET25','ET50')) AS ExtraTimeBooked, "
        "(SELECT COUNT(*) FROM dbo.Booking b WHERE b.SessionId = s.SessionId AND b.Status = 'WAITLISTED') AS Waitlisted "
        f"FROM dbo.ExamSession s JOIN dbo.Exam e ON e.ExamId = s.ExamId WHERE s.SessionId IN {s_in} ORDER BY s.SessionId")
    for s in snap["sessions"]:
        # db/checks/seat-drift.sql definition: drift = SeatsAvailable - (Capacity - (BOOKED + ATTENDED))
        s["SeatDrift"] = s["SeatsAvailable"] - (s["Capacity"] - s["BookedOrAttended"])
    snap["bookings"] = db.json(
        "SELECT b.BookingId, b.SessionId, c.CandidateNumber, b.Status, b.AccommodationCode, b.Channel, b.ExternalRef, "
        "b.BookedAt, b.CancelledAt, b.CancelReason, b.LastModified "
        "FROM dbo.Booking b JOIN dbo.Candidate c ON c.CandidateId = b.CandidateId "
        f"WHERE b.SessionId IN {s_in} OR c.CandidateNumber IN {c_in} ORDER BY b.SessionId, b.BookingId")
    snap["waitlist"] = db.json(
        "SELECT w.WaitlistId, w.SessionId, c.CandidateNumber, w.BookingId, w.Position, w.AddedAt "
        "FROM dbo.Waitlist w JOIN dbo.Candidate c ON c.CandidateId = w.CandidateId "
        f"WHERE w.SessionId IN {s_in} OR c.CandidateNumber IN {c_in} ORDER BY w.SessionId, w.Position, w.WaitlistId")
    snap["results"] = db.json(
        "SELECT r.ResultId, r.BookingId, c.CandidateNumber, e.ExamCode, r.SessionId, r.Score, r.Passed, r.ScoredAt, r.SourceFile "
        "FROM dbo.Result r JOIN dbo.Candidate c ON c.CandidateId = r.CandidateId JOIN dbo.Exam e ON e.ExamId = r.ExamId "
        f"WHERE c.CandidateNumber IN {c_in} ORDER BY r.ScoredAt, r.ResultId")
    snap["audit"] = db.json(
        "SELECT a.AuditId, a.EntityName, a.EntityId, a.Action, a.Detail, a.Actor, a.LoggedAt "
        f"FROM dbo.AuditLog a WHERE a.AuditId > {int(audit_after)} ORDER BY a.AuditId")
    return snap


def normalize(snap, norm):
    out = norm.apply(snap)
    for row in out.get("audit", []):
        if row.get("EntityName") == "Booking":
            row["EntityId"] = norm.placeholder("booking", row["EntityId"])
        row.pop("AuditId", None)
    # Ids created in the run are already placeholders; keep the seed ids for traceability.
    return out


def session_counters(db, sessions):
    return db.json(
        "SELECT s.SessionId, s.Capacity, s.AccommodationCapacity, s.SeatsAvailable, s.Status "
        f"FROM dbo.ExamSession s WHERE s.SessionId IN {_in(sessions)} ORDER BY s.SessionId")
