"""Modern target adapters (scheduling-service directly on :8090, or the routing gateway on :8000).

PORTAL operations are mapped to the equivalent /api/v2 call with channel PORTAL (the integration
contract, item 5). PARTNER operations are replayed unchanged as booking-api v1 calls when the base url
is the routing gateway (it translates v1 -> v2 and keeps v1 shapes); against scheduling-service directly
they are sent to /api/v2 with channel PARTNER.
"""
import requests

from .adapters_legacy import PartnerV1Adapter, json_or_text
from .normalize import legacy_code


class Unsupported(Exception):
    """The operation has no equivalent on this target (reported as SKIPPED, not as a match)."""


def v2_business(op, status, body):
    if status >= 400:
        if isinstance(body, dict):
            error = body.get("error")
            code = body.get("legacyCode")
            return {"outcome": "ERROR", "error": error, "legacyCode": code if code is not None else legacy_code(error)}
        return {"outcome": "ERROR", "error": "HTTP_%d" % status, "legacyCode": None}
    if op in ("book", "reschedule"):
        return {"outcome": body.get("status"), "bookingId": body.get("bookingId"), "error": None, "legacyCode": None}
    if op == "cancel":
        return {"outcome": "CANCELLED", "error": None, "legacyCode": None}
    if op == "eligibility":
        return {"eligible": body.get("eligible"), "reason": body.get("reason"),
                "earliestRetakeDate": body.get("earliestRetakeDate")}
    return {"outcome": "UPDATED", "error": None, "legacyCode": None,
            "bumped": [b for b in body.get("bumpedBookingIds", [])] if isinstance(body, dict) else None}


class V2Adapter:
    def __init__(self, base_url, channel, timeout=60.0):
        self.url = base_url.rstrip("/")
        self.channel = channel
        self.timeout = timeout

    def _call(self, op, method, path, json=None, params=None):
        r = requests.request(method, self.url + path, json=json, params=params, timeout=self.timeout)
        body = json_or_text(r)
        req = {"method": method, "path": path}
        if json is not None:
            req["json"] = json
        if params:
            req["query"] = params
        biz = v2_business(op, r.status_code, body)
        biz.pop("bumped", None)
        result = {"request": req, "response": {"status": r.status_code, "body": body}, "business": biz}
        if op in ("book", "reschedule") and r.status_code < 400 and isinstance(body, dict):
            result["booking_id"] = body.get("bookingId")
        return result

    def book(self, session, candidate, accommodation=None, external_ref=None):
        return self._call("book", "POST", "/api/v2/bookings", json={
            "sessionId": session, "candidateNumber": candidate, "accommodationCode": accommodation or None,
            "externalRef": external_ref, "channel": self.channel})

    def cancel(self, booking, reason=None, candidate=None):
        """`candidate` is the portal's (unchecked) owner field; /api/v2 has no such parameter, so it is not sent."""
        return self._call("cancel", "DELETE", "/api/v2/bookings/%d" % booking,
                          params={"reason": reason} if reason else None)

    def reschedule(self, booking, new_session):
        return self._call("reschedule", "POST", "/api/v2/bookings/%d/reschedule" % booking,
                          json={"newSessionId": new_session})

    def capacity(self, session, new_capacity):
        return self._call("capacity", "PUT", "/api/v2/sessions/%d/capacity" % session,
                          json={"newCapacity": int(new_capacity), "actor": "admin"})

    def search(self, center_code, probe=()):
        """Modern equivalent of the retired portal search page (/Booking, MBA-3824): the /app session finder lists the
        active centers (GET /api/v2/centers) and the bookable (OPEN) sessions of the chosen center."""
        centers = requests.get(self.url + "/api/v2/centers", timeout=self.timeout)
        r = requests.get(self.url + "/api/v2/centers/%s/sessions" % center_code, timeout=self.timeout)
        rows = json_or_text(r)
        ids = {s.get("sessionId") for s in rows if s.get("status") == "OPEN"} if isinstance(rows, list) else set()
        codes = json_or_text(centers)
        biz = {"centerFilter": sorted(c.get("centerCode") for c in codes) if isinstance(codes, list) else [],
               "listed": {str(i): i in ids for i in probe}}
        req = {"method": "GET", "path": "/api/v2/centers/%s/sessions" % center_code}
        return {"request": req, "response": {"status": r.status_code}, "business": biz}

    def eligibility(self, candidate, exam_code, date):
        return self._call("eligibility", "GET", "/api/v2/candidates/%s/eligibility" % candidate,
                          params={"examCode": exam_code, "date": date})


def modern_adapters(base_url, partner_api, timeout=60.0):
    """partner_api: 'v1' (routing gateway) or 'v2' (scheduling-service directly)."""
    portal = V2Adapter(base_url, "PORTAL", timeout)
    if partner_api == "v1":
        partner = PartnerV1Adapter(base_url, timeout)
    else:
        partner = V2Adapter(base_url, "PARTNER", timeout)
    return {"PORTAL": portal, "PARTNER": partner}


def detect_partner_api(base_url, timeout=10.0):
    """'v1' when base_url serves booking-api v1 paths (the gateway), else 'v2'."""
    try:
        r = requests.get(base_url.rstrip("/") + "/api/v1/sessions?from=2099-01-01", timeout=timeout)
        if r.status_code == 200:
            return "v1"
    except requests.RequestException:
        pass
    return "v2"
