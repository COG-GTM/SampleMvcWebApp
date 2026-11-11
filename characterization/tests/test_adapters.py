"""Adapter tests: HTML parsing of the portal pages, and the modern/partner adapters against a local stub server."""
import json
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

import pytest

from chartool.adapters_legacy import (PartnerV1Adapter, hidden_fields, parse_portal_message, rendered_rows,
                                      success_message, validation_errors)
from chartool.adapters_modern import detect_partner_api, modern_adapters

BOOK_PAGE = """<form action="/Booking/Book" method="post"><input name="__RequestVerificationToken" type="hidden"
 value="tok123" /><input data-val="true" id="SessionId" name="SessionId" type="hidden" value="462" />
<input id="CandidateNumber" name="CandidateNumber" type="text" value="" /></form>"""


def test_hidden_fields_reads_token_and_hidden_inputs_of_the_right_form():
    assert hidden_fields(BOOK_PAGE, "/Booking/Book") == {"__RequestVerificationToken": "tok123", "SessionId": "462"}
    assert hidden_fields(BOOK_PAGE, "/Booking/Cancel/1") == {}


def test_portal_messages_errors_and_rendered_rows():
    assert parse_portal_message("Booking 3588 confirmed.") == ("BOOKED", 3588)
    assert parse_portal_message("Session is full - booking 3589 has been placed on the waitlist.") == ("WAITLISTED", 3589)
    assert parse_portal_message("Booking moved. New booking 3590 (BOOKED).") == ("BOOKED", 3590)
    assert parse_portal_message("Booking cancelled.") == (None, None)
    page = ('<div class="validation-summary-errors text-danger" data-valmsg-summary="true"><ul>'
            '<li>Session 219 is not open for booking.</li></ul></div>')
    assert validation_errors(page) == ["Session 219 is not open for booking."]
    assert success_message('<div class="alert alert-success nw-alert">Booking 1 confirmed.</div>') == "Booking 1 confirmed."
    assert rendered_rows("<tr><th>Eligible</th><td><strong>YES</strong></td></tr>") == {"Eligible": "YES"}


class _Stub(BaseHTTPRequestHandler):
    calls = []
    gateway = False

    def _send(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _record(self):
        n = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(n)) if n else None
        _Stub.calls.append((self.command, self.path, body))
        return body

    def do_GET(self):
        self._record()
        if self.path.startswith("/api/v1/sessions"):
            return self._send(200 if _Stub.gateway else 404, [] if _Stub.gateway else {"error": "NOT_FOUND"})
        self._send(200, {"eligible": False, "reason": "RETAKE_WAIT_NOT_ELAPSED", "earliestRetakeDate": "2026-11-29"})

    def do_POST(self):
        body = self._record()
        if body and body.get("sessionId") == 281:
            return self._send(409, {"error": "SESSION_NOT_OPEN", "message": "closed", "legacyCode": 50001})
        if body and body.get("accommodationCode") == "XX":
            return self._send(400, {"error": "VALIDATION", "message": "unknown accommodation", "legacyCode": None})
        self._send(201, {"bookingId": 50000001, "status": "BOOKED", "seatsAvailable": 14})

    def do_PUT(self):
        self._record()
        self._send(200, {"session": {"sessionId": 191}, "bumpedBookingIds": [50000002]})

    def do_DELETE(self):
        self._record()
        self._send(200, {"bookingId": 50000001, "status": "CANCELLED"})

    def log_message(self, *args):
        pass


@pytest.fixture()
def stub():
    server = HTTPServer(("127.0.0.1", 0), _Stub)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    _Stub.calls = []
    yield "http://127.0.0.1:%d" % server.server_port
    server.shutdown()


def test_modern_portal_requests_map_to_v2_with_channel_portal(stub):
    a = modern_adapters(stub, "v2")
    r = a["PORTAL"].book(462, "NW00000901", "")
    assert _Stub.calls[-1] == ("POST", "/api/v2/bookings", {"sessionId": 462, "candidateNumber": "NW00000901",
                                                            "accommodationCode": None, "externalRef": None,
                                                            "channel": "PORTAL"})
    assert r["business"] == {"outcome": "BOOKED", "bookingId": 50000001, "error": None, "legacyCode": None}
    a["PARTNER"].book(462, "NW00000901")
    assert _Stub.calls[-1][2]["channel"] == "PARTNER"
    r = a["PORTAL"].book(281, "NW00000901")
    assert r["business"] == {"outcome": "ERROR", "error": "SESSION_NOT_OPEN", "legacyCode": 50001}
    a["PORTAL"].capacity(191, "4")
    assert _Stub.calls[-1] == ("PUT", "/api/v2/sessions/191/capacity", {"newCapacity": 4, "actor": "admin"})
    a["PORTAL"].cancel(50000001, "Candidate request")
    assert _Stub.calls[-1][:2] == ("DELETE", "/api/v2/bookings/50000001?reason=Candidate+request")
    a["PORTAL"].reschedule(50000001, 258)
    assert _Stub.calls[-1] == ("POST", "/api/v2/bookings/50000001/reschedule", {"newSessionId": 258, "channel": "PORTAL"})
    r = a["PORTAL"].eligibility("NW00000950", "CTA-NET1", "2026-11-20")
    assert _Stub.calls[-1][1].startswith("/api/v2/candidates/NW00000950/eligibility?")
    assert r["business"]["earliestRetakeDate"] == "2026-11-29"


def test_partner_calls_stay_v1_through_the_gateway(stub):
    _Stub.gateway = False
    assert detect_partner_api(stub) == "v2"
    _Stub.gateway = True
    try:
        assert detect_partner_api(stub) == "v1"
    finally:
        _Stub.gateway = False
    a = modern_adapters(stub, "v1")
    assert isinstance(a["PARTNER"], PartnerV1Adapter)
    a["PARTNER"].book(462, "NW00000901", None, "NAN-1")
    assert _Stub.calls[-1] == ("POST", "/api/v1/bookings", {"sessionId": 462, "candidateNumber": "NW00000901",
                                                            "accommodationCode": None, "externalRef": "NAN-1"})
    a["PARTNER"].cancel(3586, None)
    assert _Stub.calls[-1][:2] == ("DELETE", "/api/v1/bookings/3586")


def test_modern_portal_channel_reports_request_validation_as_the_portal_generic_error(stub):
    a = modern_adapters(stub, "v2")
    r = a["PORTAL"].book(462, "NW00000901", "XX")
    assert r["business"] == {"outcome": "ERROR", "error": "PORTAL_ERROR", "legacyCode": None}
    r = a["PARTNER"].book(462, "NW00000901", "XX")
    assert r["business"] == {"outcome": "ERROR", "error": "VALIDATION", "legacyCode": None}
    r = a["PORTAL"].book(281, "NW00000901")
    assert r["business"] == {"outcome": "ERROR", "error": "SESSION_NOT_OPEN", "legacyCode": 50001}
