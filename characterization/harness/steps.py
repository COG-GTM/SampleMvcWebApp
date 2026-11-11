"""Step executors. Each returns {"request": ..., "response": ...} (raw, normalized later) and may
return a "saved" id for `save_as`. Portal steps behave like a browser (GET form, POST with the
antiforgery token, follow the redirect); api steps call booking-api REST; db steps call the stored
procedures directly so the raw SQL error number/state is visible (portal shows only the message)."""
import re
import urllib.parse

from .web import Client, rendered_fields

BOOKING_ID_RE = re.compile(r"[Bb]ooking (\d+)")


class StepError(RuntimeError):
    pass


def _path(url):
    p = urllib.parse.urlsplit(url)
    return p.path + (("?" + p.query) if p.query else "")


def _portal_response(resp):
    return {"status": resp.status, "path": _path(resp.url), "rendered": rendered_fields(resp.body)}


def _api_response(resp):
    return {"status": resp.status, "body": resp.json()}


class Steps:
    def __init__(self, target, db):
        self.t, self.db = target, db

    # ---- portal (ASP.NET MVC 5, :8080) ------------------------------------------------------
    def portal_book(self, session, candidate, accommodation=""):
        q = urllib.parse.urlencode({"sessionId": session, "candidateNumber": candidate})
        fields = {"SessionId": session, "CandidateNumber": candidate, "AccommodationCode": accommodation or ""}
        resp, ok = Client().form_post(f"{self.t.portal_url}/Booking/Book?{q}", f"{self.t.portal_url}/Booking/Book", fields)
        out = {"request": {"channel": "portal", "method": "POST", "path": "/Booking/Book", "form": fields},
               "response": _portal_response(resp)}
        out["saved"] = self._id_from_alert(out, r"Booking (\d+) confirmed|booking (\d+) has been placed")
        return out

    def portal_cancel(self, booking, candidate="", reason="characterization"):
        q = urllib.parse.urlencode({"candidateNumber": candidate})
        fields = {"CandidateNumber": candidate, "Reason": reason}
        url = f"{self.t.portal_url}/Booking/Cancel/{booking}"
        resp, _ = Client().form_post(f"{url}?{q}", url, fields)
        return {"request": {"channel": "portal", "method": "POST", "path": f"/Booking/Cancel/{booking}", "form": fields},
                "response": _portal_response(resp)}

    def portal_reschedule(self, booking, session, candidate=""):
        q = urllib.parse.urlencode({"candidateNumber": candidate})
        fields = {"CandidateNumber": candidate, "NewSessionId": session}
        url = f"{self.t.portal_url}/Booking/Reschedule/{booking}"
        resp, _ = Client().form_post(f"{url}?{q}", url, fields)
        out = {"request": {"channel": "portal", "method": "POST", "path": f"/Booking/Reschedule/{booking}", "form": fields},
               "response": _portal_response(resp)}
        out["saved"] = self._id_from_alert(out, r"New booking (\d+)")
        return out

    def portal_capacity(self, session, capacity):
        url = f"{self.t.portal_url}/Admin/Sessions/Capacity/{session}"
        fields = {"NewCapacity": capacity}
        resp, _ = Client().form_post(url, url, fields)
        return {"request": {"channel": "portal", "method": "POST", "path": f"/Admin/Sessions/Capacity/{session}", "form": fields},
                "response": _portal_response(resp)}

    def portal_eligibility(self, candidate, exam, date):
        q = urllib.parse.urlencode({"candidateNumber": candidate, "examCode": exam, "date": date})
        resp = Client().request("GET", f"{self.t.portal_url}/Eligibility?{q}")
        r = _portal_response(resp)
        cells = r["rendered"].get("cells", {})
        r["eligibility"] = {"eligible": cells.get("Eligible"), "reason": cells.get("Reason"),
                            "earliestRetakeDate": cells.get("Earliest retake date")}
        return {"request": {"channel": "portal", "method": "GET", "path": f"/Eligibility?{q}"}, "response": r}

    # ---- booking-api (Spring Boot 1.5, :8081) -----------------------------------------------
    def api_book(self, session, candidate, accommodation=None, external_ref=None):
        body = {"sessionId": session, "candidateNumber": candidate}
        if accommodation is not None:
            body["accommodationCode"] = accommodation
        if external_ref is not None:
            body["externalRef"] = external_ref
        resp = Client().json_call("POST", f"{self.t.api_url}/api/v1/bookings", body)
        out = {"request": {"channel": "api", "method": "POST", "path": "/api/v1/bookings", "json": body},
               "response": _api_response(resp)}
        out["saved"] = self._id_from_json(out)
        return out

    def api_cancel(self, booking, reason=None):
        path = f"/api/v1/bookings/{booking}" + (f"?{urllib.parse.urlencode({'reason': reason})}" if reason else "")
        resp = Client().json_call("DELETE", f"{self.t.api_url}{path}")
        return {"request": {"channel": "api", "method": "DELETE", "path": path}, "response": _api_response(resp)}

    def api_reschedule(self, booking, session):
        body = {"newSessionId": session}
        path = f"/api/v1/bookings/{booking}/reschedule"
        resp = Client().json_call("POST", f"{self.t.api_url}{path}", body)
        out = {"request": {"channel": "api", "method": "POST", "path": path, "json": body}, "response": _api_response(resp)}
        out["saved"] = self._id_from_json(out)
        return out

    def api_eligibility(self, candidate, exam, date):
        q = urllib.parse.urlencode({"examCode": exam, "date": date})
        path = f"/api/v1/candidates/{candidate}/eligibility?{q}"
        resp = Client().json_call("GET", f"{self.t.api_url}{path}")
        r = _api_response(resp)
        b = r["body"] if isinstance(r["body"], dict) else {}
        r["eligibility"] = {"eligible": b.get("eligible"), "reason": b.get("reason"), "earliestRetakeDate": b.get("earliestRetakeDate")}
        return {"request": {"channel": "api", "method": "GET", "path": path}, "response": r}

    def api_get(self, path):
        resp = Client().json_call("GET", f"{self.t.api_url}{path}")
        return {"request": {"channel": "api", "method": "GET", "path": path}, "response": _api_response(resp)}

    # ---- seat reconciler (scheduling-service, absorbed capacity-sync :8083) and DB checks ---
    def capacity_sync(self):
        resp = Client(timeout=300).json_call("POST", f"{self.t.sync_url}/api/v1/reconciler/runs")
        return {"request": {"channel": "sync", "method": "POST", "path": "/sync/run"}, "response": _api_response(resp)}

    def seat_drift(self):
        from .config import REPO_DIR
        sql = (REPO_DIR / "db" / "checks" / "seat-drift.sql").read_text(encoding="utf-8-sig")
        rows = self.db.run_rows(sql)
        cols = ["SessionId", "ExamCode", "CenterCode", "StartsAt", "Capacity", "SeatsAvailable", "ActiveBookings", "Drift"]
        ints = {"SessionId", "Capacity", "SeatsAvailable", "ActiveBookings", "Drift"}
        out = [{c: (int(v) if c in ints else v) for c, v in zip(cols, r)} for r in rows]
        return {"request": {"channel": "db", "check": "db/checks/seat-drift.sql"},
                "response": {"drifting_sessions": len(out), "total_drift": sum(r["Drift"] for r in out), "rows": out}}

    def db_proc(self, sql):
        """Call a stored procedure directly; records the raw RAISERROR number/state and any result rows."""
        out = self.db.run("SET NOCOUNT ON; " + sql, check=False)
        errors = self.db.errors(out)
        rows = [l.strip() for l in out.splitlines() if l.strip() and not l.startswith("Msg ")
                and all(l.strip() != e["message"] for e in errors)]
        return {"request": {"channel": "db", "sql": sql}, "response": {"errors": errors, "rows": rows}}

    def sql(self, sql):
        self.db.run("SET NOCOUNT ON; " + sql)
        return {"request": {"channel": "setup-sql", "sql": sql}, "response": {"ok": True}}

    def db_query(self, sql):
        return {"request": {"channel": "db", "sql": sql}, "response": {"rows": self.db.json(sql)}}

    # ---- helpers ----------------------------------------------------------------------------
    def _id_from_alert(self, out, pattern):
        for alert in out["response"]["rendered"].get("alerts", []):
            m = re.search(pattern, alert)
            if m:
                return int(next(g for g in m.groups() if g))
        return None

    def _id_from_json(self, out):
        body = out["response"].get("body")
        if isinstance(body, dict) and isinstance(body.get("bookingId"), int):
            return body["bookingId"]
        return None
