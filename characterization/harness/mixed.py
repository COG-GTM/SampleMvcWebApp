"""Mixed portal/partner traffic: the workload of scripts/mixed-traffic.ps1 / .sh, reproduced step for step
(same defaults, candidate and session queries, interleaving, external refs and cancels) except that the
"future session" filter is anchored on SEED_AS_OF instead of GETDATE(), so the run is deterministic
however far the wall clock has moved past the seed (docs/as-is/hidden-behaviours.md HB-30)."""
from .steps import Steps


def run(steps: Steps, seed_as_of, portal_bookings=20, partner_bookings=20, cancels=3, sessions=4, first_candidate=901):
    db = steps.db
    total = portal_bookings + partner_bookings
    session_ids = [r["SessionId"] for r in db.json(
        f"SELECT TOP ({int(sessions)}) s.SessionId FROM dbo.ExamSession s WHERE s.Status = 'OPEN' "
        f"AND s.StartsAt > '{seed_as_of}' AND s.SeatsAvailable >= 5 ORDER BY s.SessionId")]
    first = "NW%08d" % first_candidate
    cands = [r["CandidateNumber"] for r in db.json(
        f"SELECT TOP ({total}) c.CandidateNumber FROM dbo.Candidate c WHERE c.CandidateNumber >= '{first}' "
        "AND (c.DefaultAccommodationCode IS NULL OR c.DefaultAccommodationCode NOT IN ('ET25','ET50')) ORDER BY c.CandidateNumber")]
    calls, portal_ids, partner_ids = [], [], []
    i = p = q = 0
    while p < portal_bookings or q < partner_bookings:
        if p < portal_bookings:
            sid = session_ids[i % len(session_ids)]
            r = steps.portal_book(sid, cands[i])
            calls.append({"op": "portal_book", "session": sid, "candidate": cands[i], "status": r["response"]["status"],
                          "alerts": r["response"]["rendered"]["alerts"]})
            if r.get("saved"):
                portal_ids.append(r["saved"])
            p += 1
            i += 1
        if q < partner_bookings:
            sid = session_ids[i % len(session_ids)]
            r = steps.api_book(sid, cands[i], external_ref="MT-%04d" % (q + 1))
            calls.append({"op": "api_book", "session": sid, "candidate": cands[i], "status": r["response"]["status"],
                          "body": r["response"]["body"]})
            if r.get("saved"):
                partner_ids.append(r["saved"])
            q += 1
            i += 1
    for bid in portal_ids[:cancels]:
        r = steps.portal_cancel(bid, "", reason="mixed-traffic")
        calls.append({"op": "portal_cancel", "bookingId": bid, "status": r["response"]["status"], "alerts": r["response"]["rendered"]["alerts"]})
    for bid in partner_ids[:cancels]:
        r = steps.api_cancel(bid, reason="mixed-traffic")
        calls.append({"op": "api_cancel", "bookingId": bid, "status": r["response"]["status"]})
    request = {"channel": "mixed", "workload": "scripts/mixed-traffic.ps1", "PORTAL_BOOKINGS": portal_bookings,
               "PARTNER_BOOKINGS": partner_bookings, "CANCELS": cancels, "SESSIONS": sessions,
               "FIRST_CANDIDATE": first_candidate, "SEED_AS_OF": seed_as_of}
    return {"request": request, "response": {"sessions": session_ids, "calls": calls}, "sessions": session_ids, "candidates": cands}
