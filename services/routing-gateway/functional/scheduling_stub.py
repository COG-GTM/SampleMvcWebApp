"""Stand-in for scheduling-service (MBA-3821) on :8090 until M2 merges.

Implements the slice of the integration contract the gateway uses:
  GET  /internal/routing/resolve?sessionId=|bookingId=|centerId=|centerCode=
  GET  /api/v2/sessions?centerCode=&examCode=&from=&to=, GET /api/v2/sessions/{id}
  POST /api/v2/bookings, GET|DELETE /api/v2/bookings/{id}, POST /api/v2/bookings/{id}/reschedule
  GET  /api/v2/candidates/{n}/eligibility, GET /api/v2/sessions/{id}/waitlist, PUT /api/v2/sessions/{id}/capacity
  GET  /app/** (a tiny SPA that calls /api/v2 with relative URLs)
Reference data (centers, sessions, legacy bookings) is read from AssessmentDelivery / booking-api, the way M2
snapshots it; modern bookings live in memory with ids from 50,000,000. Test-only: GET /__stub/bookings.
"""
import json
import re
import sys
import threading
from datetime import datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

from stub_common import sql_rows, http, BOOKING_API

LOCK = threading.Lock()
CENTERS = {}          # centerId -> code
SESSION_CENTER = {}   # sessionId -> centerId
MODERN = {}           # bookingId -> booking dict
NEXT_ID = [50_000_000]
SPA = open(__file__.replace("scheduling_stub.py", "modern_app.html"), encoding="utf-8").read()


def load_snapshots():
    for cid, code in sql_rows("SELECT TestCenterId, CenterCode FROM dbo.TestCenter"):
        CENTERS[int(cid)] = code
    for sid, cid in sql_rows("SELECT SessionId, TestCenterId FROM dbo.ExamSession"):
        SESSION_CENTER[int(sid)] = int(cid)


def code_to_id(code):
    return next((i for i, c in CENTERS.items() if c == code.upper()), None)


def session_json(sid):
    status, body = http("GET", f"{BOOKING_API}/api/v1/sessions/{sid}")
    if status != 200:
        return None
    s = json.loads(body)
    used = sum(1 for b in MODERN.values() if b["sessionId"] == sid and b["status"] == "BOOKED")
    s["seatsAvailable"] = max(0, s["seatsAvailable"] - used)
    s["bookedSeats"] = (s.get("bookedSeats") or 0) + used
    s["accommodationUsed"] = 0
    s["waitlistCount"] = 0
    return s


def now():
    return datetime.now().strftime("%Y-%m-%dT%H:%M:%S")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[scheduling-stub] " + fmt % args + "\n")

    def send(self, status, obj=None, ctype="application/json", headers=None):
        body = b"" if obj is None else (obj.encode() if isinstance(obj, str) else json.dumps(obj).encode())
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)

    def err(self, status, code, msg, legacy=None):
        self.send(status, {"error": code, "message": msg, "legacyCode": legacy})

    def body(self):
        n = int(self.headers.get("Content-Length") or 0)
        return json.loads(self.rfile.read(n) or b"{}") if n else {}

    def route(self, method):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        p = u.path
        if p == "/app" or p.startswith("/app/"):
            return self.send(200, SPA, "text/html; charset=utf-8")
        if p == "/__stub/bookings":
            return self.send(200, list(MODERN.values()))
        if p == "/internal/routing/resolve" and method == "GET":
            return self.resolve(q)
        m = re.fullmatch(r"/api/v2/sessions/(\d+)", p)
        if m and method == "GET":
            s = session_json(int(m.group(1)))
            return self.send(200, s) if s else self.err(404, "SESSION_NOT_FOUND", f"Session {m.group(1)} not found")
        if p == "/api/v2/sessions" and method == "GET":
            qs = "&".join(f"{k}={v}" for k, v in q.items())
            status, body = http("GET", f"{BOOKING_API}/api/v1/sessions?{qs}")
            out = [session_json(s["sessionId"]) for s in json.loads(body)] if status == 200 else []
            return self.send(200, [s for s in out if s])
        if p == "/api/v2/bookings" and method == "POST":
            return self.book(self.body())
        m = re.fullmatch(r"/api/v2/bookings/(\d+)", p)
        if m:
            b = MODERN.get(int(m.group(1)))
            if not b:
                return self.err(404, "BOOKING_NOT_FOUND", f"Booking {m.group(1)} not found")
            if method == "GET":
                return self.send(200, b)
            if method == "DELETE":
                if b["status"] != "BOOKED":
                    return self.err(409, "BOOKING_NOT_ACTIVE", f"Booking {b['bookingId']} is {b['status']}")
                b.update(status="CANCELLED", cancelledAt=now(), cancelReason=q.get("reason"))
                return self.send(200, b)
        m = re.fullmatch(r"/api/v2/bookings/(\d+)/reschedule", p)
        if m and method == "POST":
            b = MODERN.get(int(m.group(1)))
            if not b or b["status"] != "BOOKED":
                return self.err(404, "BOOKING_NOT_FOUND", f"Booking {m.group(1)} not found")
            new = self.body().get("newSessionId")
            b.update(status="RESCHEDULED", cancelledAt=now(), cancelReason="Rescheduled")
            return self.book({"sessionId": new, "candidateNumber": b["candidateNumber"], "channel": b["channel"]})
        m = re.fullmatch(r"/api/v2/candidates/([^/]+)/eligibility", p)
        if m:
            return self.send(200, {"eligible": True, "reason": "No previous attempt", "earliestRetakeDate": None})
        if re.fullmatch(r"/api/v2/sessions/(\d+)/waitlist", p):
            return self.send(200, [])
        self.err(404, "NOT_FOUND", f"No stub route for {method} {p}")

    def resolve(self, q):
        if len(q) != 1:
            return self.err(400, "BAD_REQUEST", "exactly one of sessionId, bookingId, centerId, centerCode")
        (k, v), = q.items()
        cid, sid, bid, origin, cache = None, None, None, None, {"Cache-Control": "max-age=60"}
        if k == "centerCode":
            cid = code_to_id(v)
        elif k == "centerId" and v.isdigit():
            cid = int(v) if int(v) in CENTERS else None
        elif k == "sessionId" and v.isdigit():
            sid = int(v)
            cid = SESSION_CENTER.get(sid)
        elif k == "bookingId" and v.isdigit():
            bid, cache = int(v), {}
            if bid in MODERN:
                sid, origin = MODERN[bid]["sessionId"], "MODERN"
            else:
                rows = sql_rows(f"SELECT SessionId FROM dbo.Booking WHERE BookingId = {bid}")
                sid, origin = (int(rows[0][0]), "LEGACY_IMPORT") if rows else (None, None)
            cid = SESSION_CENTER.get(sid)
        if cid is None:
            return self.err(404, "NOT_FOUND", f"{k}={v} not found")
        self.send(200, {"centerCode": CENTERS[cid], "centerId": cid, "sessionId": sid, "bookingId": bid,
                        "bookingOrigin": origin}, headers=cache)

    def book(self, req):
        sid, cand = req.get("sessionId"), req.get("candidateNumber")
        if sid is None or not cand:
            return self.err(400, "VALIDATION", "sessionId and candidateNumber are required")
        s = session_json(int(sid))
        if not s:
            return self.err(404, "SESSION_NOT_FOUND", f"Session {sid} not found")
        if s["status"] != "OPEN":
            return self.err(409, "SESSION_NOT_OPEN", f"Session {sid} is {s['status']}", 50001)
        with LOCK:
            if any(b["sessionId"] == sid and b["candidateNumber"] == cand and b["status"] == "BOOKED"
                   for b in MODERN.values()):
                return self.err(409, "DUPLICATE_BOOKING",
                                f"Candidate {cand} already has an active booking for session {sid}", 50002)
            NEXT_ID[0] += 1
            b = {"bookingId": NEXT_ID[0], "sessionId": sid, "candidateNumber": cand, "examCode": s["examCode"],
                 "sessionStartsAt": s["startsAt"], "status": "BOOKED", "accommodationCode": req.get("accommodationCode"),
                 "channel": req.get("channel") or "PORTAL", "externalRef": req.get("externalRef"), "bookedAt": now(),
                 "cancelledAt": None, "cancelReason": None, "origin": "MODERN", "legacyBookingId": None,
                 "waitlistPosition": None}
            MODERN[b["bookingId"]] = b
        self.send(201, {k: b[k] for k in ("bookingId", "status", "sessionId", "candidateNumber", "accommodationCode",
                                          "channel", "waitlistPosition")} | {"seatsAvailable": s["seatsAvailable"] - 1})

    def do_GET(self):
        self.route("GET")

    def do_POST(self):
        self.route("POST")

    def do_DELETE(self):
        self.route("DELETE")

    def do_PUT(self):
        self.route("PUT")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
    load_snapshots()
    print(f"[scheduling-stub] {len(CENTERS)} centers, {len(SESSION_CENTER)} sessions; listening on :{port}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
