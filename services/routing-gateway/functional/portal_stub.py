"""Stand-in for the ASP.NET MVC 5 portal (:8080; the real one only runs on Windows, PR #52).

Mirrors the routes the gateway cares about (AdminController, Eligibility, Results) with the same query/form field
names as the MVC views. The /Booking pages are kept as a pre-MBA-3824 reference only: the real portal no longer has
them and the gateway redirects those URLs to the modern UI before they reach the portal.
"""
import html
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs, quote

from stub_common import http, sql_rows, BOOKING_API

STYLE = ("<style>body{font-family:Verdana,sans-serif;font-size:13px;margin:0;background:#fff}"
         ".navbar{background:#222;color:#9d9d9d;padding:12px 20px}.navbar a{color:#9d9d9d;margin-right:16px}"
         ".container{padding:20px}table{border-collapse:collapse}td,th{border:1px solid #ddd;padding:4px 8px}"
         ".alert{background:#dff0d8;border:1px solid #d6e9c6;padding:10px;margin:10px 0}</style>")


def page(title, body):
    return (f"<!DOCTYPE html><html><head><title>{title} - Northwind Assessment Network</title>{STYLE}</head><body>"
            "<div class='navbar'><b style='color:#fff;margin-right:20px'>Northwind Assessment Network</b>"
            "<a href='/Admin/Centers'>Admin</a><a href='/Booking'>Booking</a><a href='/Eligibility'>Eligibility</a>"
            f"<a href='/Results'>Results</a><span style='float:right'>legacy MVC 5 portal (stub)</span></div>"
            f"<div class='container'><h2>{title}</h2>{body}</div></body></html>")


def centers():
    return sql_rows("SELECT TestCenterId, CenterCode, Name FROM dbo.TestCenter ORDER BY CenterCode")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[portal-stub] " + fmt % args + "\n")

    def send(self, status, body, location=None):
        data = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        if location:
            self.send_header("Location", location)
        self.end_headers()
        self.wfile.write(data)

    def params(self):
        u = urlparse(self.path)
        p = {k.lower(): v[0] for k, v in parse_qs(u.query).items()}
        if self.command == "POST":
            n = int(self.headers.get("Content-Length") or 0)
            p.update({k.lower(): v[0] for k, v in parse_qs(self.rfile.read(n).decode()).items()})
        return u.path.rstrip("/").lower() or "/", p

    def do_POST(self):
        self.do_GET()

    def do_GET(self):
        path, p = self.params()
        e = html.escape
        if path in ("/booking", "/booking/index"):
            opts = "".join(f"<option value='{c[1]}' {'selected' if p.get('centercode') == c[1] else ''}>{c[1]} - {e(c[2])}</option>"
                           for c in centers())
            rows = ""
            if p.get("centercode"):
                _, body = http("GET", f"{BOOKING_API}/api/v1/sessions?centerCode={quote(p['centercode'])}")
                cn = quote(p.get("candidatenumber", ""))
                rows = "".join(f"<tr><td>{s['sessionId']}</td><td>{e(s['examCode'])}</td><td>{e(s['centerCode'])}</td>"
                               f"<td>{s['startsAt']}</td><td>{s['seatsAvailable']}</td><td><a id='book-{s['sessionId']}' "
                               f"href='/Booking/Book?sessionId={s['sessionId']}&candidateNumber={cn}'>Book</a></td></tr>"
                               for s in json.loads(body) if s["status"] == "OPEN")
                rows = f"<table><tr><th>Session</th><th>Exam</th><th>Center</th><th>Starts</th><th>Seats</th><th></th></tr>{rows}</table>"
            form = (f"<form method='get' action='/Booking'>Center <select name='CenterCode' id='CenterCode'><option value=''>(any)</option>{opts}</select> "
                    f"Candidate <input name='CandidateNumber' id='CandidateNumber' value='{e(p.get('candidatenumber', ''))}'> "
                    "<input type='submit' id='find' value='Find sessions'></form>")
            return self.send(200, page("Find exam sessions", form + rows))
        if path == "/booking/book":
            if self.command == "POST":
                status, body = http("POST", f"{BOOKING_API}/api/v1/bookings",
                                    {"sessionId": int(p["sessionid"]), "candidateNumber": p.get("candidatenumber")})
                r = json.loads(body)
                msg = (f"<div class='alert' id='confirmation'>Booking <b>{r['bookingId']}</b> {r['status']} (legacy dbo.Booking row)</div>"
                       if status == 201 else f"<div class='alert' style='background:#f2dede'>{e(r.get('error'))}: {e(r.get('message'))}</div>")
                return self.send(200, page("Booking result", msg))
            _, body = http("GET", f"{BOOKING_API}/api/v1/sessions/{p.get('sessionid')}")
            s = json.loads(body)
            return self.send(200, page("Book a seat", f"<p>{e(s.get('examCode'))} {e(s.get('examTitle'))} at {e(s.get('centerName'))} "
                                       f"({e(s.get('centerCode'))}), {s.get('startsAt')}, {s.get('seatsAvailable')} seats</p>"
                                       f"<form method='post' action='/Booking/Book'><input type='hidden' name='SessionId' value='{s.get('sessionId')}'>"
                                       f"Candidate <input name='CandidateNumber' id='CandidateNumber' value='{e(p.get('candidatenumber', ''))}'> "
                                       "<input type='submit' id='confirm' value='Book'></form>"))
        titles = {"/": "Home", "/admin/centers": "Test centers", "/eligibility": "Eligibility", "/results": "Results",
                  "/booking/mybookings": "My bookings", "/admin/sessions": "Sessions"}
        if path in titles or path.startswith(("/booking/", "/admin/")):
            return self.send(200, page(titles.get(path, path), f"<p>Legacy page {e(self.path)}</p>"))
        self.send(404, page("Not found", e(self.path)))


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    print(f"[portal-stub] listening on :{port}", flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
