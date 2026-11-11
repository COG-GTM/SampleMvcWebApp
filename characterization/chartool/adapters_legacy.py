"""Legacy channel adapters: PORTAL (MVC form posts with antiforgery tokens) and PARTNER (booking-api v1)."""
import re
import urllib.parse

import requests

from .normalize import clean_html_text, legacy_code, portal_date, portal_error_code, portal_reason_code

_FORM_RE = r'<form[^>]*action="%s"[^>]*>(.*?)</form>'
_INPUT_RE = re.compile(r"<input([^>]*)>", re.I)
_ATTR_RE = re.compile(r'(\w[\w-]*)="([^"]*)"')
_ERRORS_RE = re.compile(r'<div class="validation-summary-errors[^"]*"[^>]*>(.*?)</div>', re.S)
_LI_RE = re.compile(r"<li[^>]*>(.*?)</li>", re.S)
_ALERT_RE = re.compile(r'<div class="alert alert-success nw-alert">(.*?)</div>', re.S)
_ROW_RE = re.compile(r"<tr><th>(.*?)</th><td>(.*?)</td></tr>", re.S)


def hidden_fields(html_text, action):
    """Hidden inputs (antiforgery token included) of the form posting to `action`."""
    m = re.search(_FORM_RE % re.escape(action), html_text, re.S | re.I)
    fields = {}
    if not m:
        return fields
    for attrs in _INPUT_RE.findall(m.group(1)):
        a = dict(_ATTR_RE.findall(attrs))
        if a.get("type", "").lower() == "hidden" and "name" in a:
            fields.setdefault(a["name"], a.get("value", ""))
    return fields


def validation_errors(html_text):
    m = _ERRORS_RE.search(html_text or "")
    return [clean_html_text(li) for li in _LI_RE.findall(m.group(1))] if m else []


def success_message(html_text):
    m = _ALERT_RE.search(html_text or "")
    return clean_html_text(m.group(1)) if m else None


def rendered_rows(html_text):
    return {clean_html_text(k): clean_html_text(v) for k, v in _ROW_RE.findall(html_text or "")}


def parse_portal_message(message):
    """'Booking 3588 confirmed.' / 'Session is full - booking 3588 has been placed on the waitlist.' /
    'Booking moved. New booking 3590 (WAITLISTED).'"""
    message = message or ""
    m = re.search(r"booking (\d+) has been placed on the waitlist", message, re.I)
    if m:
        return "WAITLISTED", int(m.group(1))
    m = re.search(r"New booking (\d+) \((\w+)\)", message)
    if m:
        return m.group(2), int(m.group(1))
    m = re.search(r"Booking (\d+) confirmed", message)
    if m:
        return "BOOKED", int(m.group(1))
    return None, None


class PortalAdapter:
    channel = "PORTAL"

    def __init__(self, config):
        self.url = config.portal_url
        self.timeout = config.http_timeout

    def _form(self, get_path, post_path, fields):
        s = requests.Session()
        page = s.get(self.url + get_path, timeout=self.timeout)
        form = hidden_fields(page.text, post_path)
        if "__RequestVerificationToken" not in form:
            raise RuntimeError("no antiforgery token on %s (HTTP %s)" % (get_path, page.status_code))
        token = form.pop("__RequestVerificationToken")
        form.update({k: ("" if v is None else str(v)) for k, v in fields.items()})
        post = dict(form, __RequestVerificationToken=token)
        r = s.post(self.url + post_path, data=post, allow_redirects=False, timeout=self.timeout)
        request = {"method": "POST", "path": post_path, "form": form}
        response = {"status": r.status_code}
        message = None
        if r.status_code in (301, 302, 303):
            loc = urllib.parse.urlsplit(r.headers.get("Location", ""))
            response["location"] = loc.path + ("?" + loc.query if loc.query else "")
            follow = s.get(self.url + response["location"], timeout=self.timeout)
            message = success_message(follow.text)
            response["message"] = message
        else:
            response["errors"] = validation_errors(r.text)
        return request, response, message

    def _error_business(self, response, outcome_ok):
        if response["status"] in (301, 302, 303):
            return outcome_ok
        errors = response.get("errors") or ["(no validation message)"]
        error = portal_error_code(errors[0])
        return {"outcome": "ERROR", "error": error, "legacyCode": legacy_code(error)}

    def book(self, session, candidate, accommodation=None, external_ref=None):
        req, resp, msg = self._form("/Booking/Book?sessionId=%d" % session, "/Booking/Book",
                                    {"SessionId": session, "CandidateNumber": candidate,
                                     "AccommodationCode": accommodation or ""})
        status, booking_id = parse_portal_message(msg)
        biz = self._error_business(resp, {"outcome": status, "bookingId": booking_id, "error": None,
                                          "legacyCode": None})
        return {"request": req, "response": resp, "business": biz, "booking_id": booking_id}

    def cancel(self, booking, reason=None):
        path = "/Booking/Cancel/%d" % booking
        req, resp, _ = self._form(path, path, {"Reason": reason or ""})
        biz = self._error_business(resp, {"outcome": "CANCELLED", "error": None, "legacyCode": None})
        return {"request": req, "response": resp, "business": biz}

    def reschedule(self, booking, new_session):
        path = "/Booking/Reschedule/%d" % booking
        req, resp, msg = self._form(path, path, {"NewSessionId": new_session})
        status, booking_id = parse_portal_message(msg)
        biz = self._error_business(resp, {"outcome": status, "bookingId": booking_id, "error": None,
                                          "legacyCode": None})
        return {"request": req, "response": resp, "business": biz, "booking_id": booking_id}

    def capacity(self, session, new_capacity):
        path = "/Admin/Sessions/Capacity/%d" % session
        req, resp, _ = self._form(path, path, {"NewCapacity": new_capacity})
        biz = self._error_business(resp, {"outcome": "UPDATED", "error": None, "legacyCode": None})
        return {"request": req, "response": resp, "business": biz}

    def eligibility(self, candidate, exam_code, date):
        params = {"candidateNumber": candidate, "examCode": exam_code, "date": date}
        r = requests.get(self.url + "/Eligibility", params=params, timeout=self.timeout)
        rows = rendered_rows(r.text)
        req = {"method": "GET", "path": "/Eligibility", "query": params}
        resp = {"status": r.status_code, "rendered": rows, "errors": validation_errors(r.text)}
        biz = {"eligible": rows.get("Eligible") == "YES", "reason": portal_reason_code(rows.get("Reason")),
               "earliestRetakeDate": portal_date(rows.get("Earliest retake date"))}
        return {"request": req, "response": resp, "business": biz}


def json_or_text(r):
    try:
        return r.json()
    except ValueError:
        return r.text


def v1_business(op, status, body):
    """Business fields from a booking-api v1 (or gateway-translated) response."""
    if status >= 400:
        error = body.get("error") if isinstance(body, dict) else "HTTP_%d" % status
        return {"outcome": "ERROR", "error": error, "legacyCode": legacy_code(error)}
    if op in ("book", "reschedule"):
        return {"outcome": body.get("status"), "bookingId": body.get("bookingId"), "error": None, "legacyCode": None}
    if op == "cancel":
        return {"outcome": "CANCELLED", "error": None, "legacyCode": None}
    if op == "eligibility":
        return {"eligible": body.get("eligible"), "reason": body.get("reason"),
                "earliestRetakeDate": body.get("earliestRetakeDate")}
    return {"outcome": "UPDATED", "error": None, "legacyCode": None}


class PartnerV1Adapter:
    """booking-api v1 calls; also replayed unchanged against the routing gateway (:8000)."""
    channel = "PARTNER"

    def __init__(self, base_url, timeout=60.0):
        self.url = base_url.rstrip("/")
        self.timeout = timeout

    def _call(self, op, method, path, json=None, params=None):
        r = requests.request(method, self.url + path, json=json, params=params, timeout=self.timeout)
        body = json_or_text(r)
        req = {"method": method, "path": path}
        if json is not None:
            req["json"] = json
        if params:
            req["query"] = params
        biz = v1_business(op, r.status_code, body)
        result = {"request": req, "response": {"status": r.status_code, "body": body}, "business": biz}
        if op in ("book", "reschedule") and r.status_code < 400 and isinstance(body, dict):
            result["booking_id"] = body.get("bookingId")
        return result

    def book(self, session, candidate, accommodation=None, external_ref=None):
        return self._call("book", "POST", "/api/v1/bookings", json={
            "sessionId": session, "candidateNumber": candidate, "accommodationCode": accommodation,
            "externalRef": external_ref})

    def cancel(self, booking, reason=None):
        return self._call("cancel", "DELETE", "/api/v1/bookings/%d" % booking,
                          params={"reason": reason} if reason else None)

    def reschedule(self, booking, new_session):
        return self._call("reschedule", "POST", "/api/v1/bookings/%d/reschedule" % booking,
                          json={"newSessionId": new_session})

    def eligibility(self, candidate, exam_code, date):
        return self._call("eligibility", "GET", "/api/v1/candidates/%s/eligibility" % candidate,
                          params={"examCode": exam_code, "date": date})
