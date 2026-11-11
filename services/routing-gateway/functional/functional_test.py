"""Functional tests: routing-gateway against the REAL legacy booking-api (:8081, SQL Server in Docker), the
scheduling-service stand-in (:8090) and the portal stand-in (:8080). Run via run-functional-tests.sh.

Each check prints PASS/FAIL; the exit code is the number of failures. A JSON report goes to $REPORT (optional).
"""
import json
import os
import subprocess
import sys
import time

from stub_common import http, sql_rows

GW = "http://localhost:8000"
LEGACY = "http://localhost:8081"
SCHED = "http://localhost:8090"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
CONFIG = os.environ["ROUTING_CONFIG_FILE"]
C1, C2 = "BOS01", "NYC02"  # C1: seeded test/demo center, allow-listed; C2: production center, stays legacy
RESULTS = []
VOLATILE_HEADERS = {"date", "transfer-encoding", "content-length", "x-route", "connection", "keep-alive"}


def check(name, cond, detail=""):
    RESULTS.append({"check": name, "pass": bool(cond), "detail": detail})
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -- {detail}"), flush=True)


def raw(method, url, body=None, headers=None):
    """HTTP call without following redirects; returns (status, normalized headers, body bytes, x-route)."""
    import http.client
    from urllib.parse import urlparse
    u = urlparse(url)
    conn = http.client.HTTPConnection(u.hostname, u.port, timeout=15)
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    h = dict(headers or {})
    if data is not None:
        h.setdefault("Content-Type", "application/json")
    conn.request(method, u.path + (("?" + u.query) if u.query else ""), body=data, headers=h)
    r = conn.getresponse()
    b = r.read()
    hdrs = {k.lower(): v for k, v in r.getheaders() if k.lower() not in VOLATILE_HEADERS}
    route = r.getheader("X-Route")
    conn.close()
    return r.status, hdrs, b, route


def set_allow_list(centers, via="file"):
    with open(CONFIG, "w") as f:
        f.write(f"routing.modern-centers={','.join(centers)}\n")
    if via == "refresh":
        http("POST", f"{GW}/actuator/refresh")


def wait_route(method, path, body, want, timeout=5.0):
    start = time.monotonic()
    while True:
        st, _, b, route = raw(method, GW + path, body)
        if route == want or time.monotonic() - start > timeout:
            return route, time.monotonic() - start, st, b
        time.sleep(0.1)


def legacy_booking_count(candidate):
    return int(sql_rows(f"SELECT COUNT(*) FROM dbo.Booking WHERE CandidateId = "
                        f"(SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = '{candidate}')")[0][0])


def open_session(center, skip=0):
    rows = sql_rows(f"SELECT s.SessionId FROM dbo.ExamSession s JOIN dbo.TestCenter c ON c.TestCenterId = s.TestCenterId "
                    f"WHERE s.Status = 'OPEN' AND s.SeatsAvailable > 2 AND c.CenterCode = '{center}' "
                    f"ORDER BY s.StartsAt, s.SessionId")
    return int(rows[skip][0])


# Representative booking-api / services traffic for the transparency check (reads; writes compared separately)
READS = [
    ("GET", "/api/v1/sessions/1"), ("GET", "/api/v1/sessions/191"), ("GET", "/api/v1/sessions/999999"),
    ("GET", "/api/v1/sessions/abc"), ("GET", "/api/v1/sessions?centerCode=BOS01"),
    ("GET", "/api/v1/sessions?centerCode=NYC02&examCode=CTA-NET1"), ("GET", "/api/v1/sessions?from=2026-11-20&to=2026-11-25"),
    ("GET", "/api/v1/bookings/1"), ("GET", "/api/v1/bookings/3587"), ("GET", "/api/v1/bookings/49999999"),
    ("GET", "/api/v1/candidates/NW00000001/bookings"), ("GET", "/api/v1/candidates/NW00000001/eligibility?examCode=CTA-NET1"),
    ("GET", "/api/v1/candidates/NW99999999/bookings"), ("GET", "/api/v1/nope"),
    ("GET", "/api/v1/results/candidate/NW00000001"), ("GET", "/sync/last"),
]


def write_sequence(base, s_bos, s_nyc):
    """Booking writes in both centers incl. error paths; ids are deterministic after a DB reset."""
    out = []
    steps = [
        ("POST", "/api/v1/bookings", {"sessionId": s_bos, "candidateNumber": "NW00000951"}),
        ("POST", "/api/v1/bookings", {"sessionId": s_bos, "candidateNumber": "NW00000951"}),   # duplicate
        ("POST", "/api/v1/bookings", {"sessionId": 1, "candidateNumber": "NW00000952"}),       # not open
        ("POST", "/api/v1/bookings", {"sessionId": s_nyc, "candidateNumber": "NW00000952"}),
        ("POST", "/api/v1/bookings", {"candidateNumber": "NW00000953"}),                      # validation
        ("POST", "/api/v1/bookings", b"{not json"),
    ]
    for m, p, b in steps:
        st, h, body, _ = raw(m, base + p, b)
        out.append((m, p, st, h, body))
    new_id = json.loads(out[3][4])["bookingId"]
    for m, p, b in [("POST", f"/api/v1/bookings/{new_id}/reschedule", {"newSessionId": open_session(C2, 1)}),
                    ("DELETE", f"/api/v1/bookings/{json.loads(out[0][4])['bookingId']}?reason=Functional%20test", None),
                    ("DELETE", "/api/v1/bookings/49999999", None)]:
        st, h, body, _ = raw(m, base + p, b)
        out.append((m, p, st, h, body))
    return out


def normalize(body):
    """Mask values that differ between any two calls even to the same server: Spring Boot's error 'timestamp' and
    the PushbackInputStream@<hash> identity in Jackson parse errors."""
    import re
    body = re.sub(rb'"timestamp" : "[^"]*"', b'"timestamp" : "<ts>"', body)
    return re.sub(rb"@[0-9a-f]{4,}", b"@<hash>", body)


def mask_wall_clock(body):
    """cancelledAt of a booking cancelled during the run is 'now' and differs between the two runs."""
    body = normalize(body)
    try:
        j = json.loads(body)
    except ValueError:
        return body
    if isinstance(j, dict) and j.get("cancelledAt"):
        return body.replace(j["cancelledAt"].encode(), b"<now>")
    return body


def reset_db():
    subprocess.run([os.path.join(ROOT, "db", "apply.sh")], check=True, capture_output=True,
                   env=dict(os.environ, AD_SA_PASSWORD=os.environ.get("AD_SA_PASSWORD", "Str0ng!Passw0rd")))


def main():
    s_bos, s_nyc = open_session(C1), open_session(C2)

    # 1. Transparency with an empty allow-list
    set_allow_list([])
    time.sleep(1.5)
    diffs = []
    for m, p in READS:
        base = "http://localhost:8082" if p.startswith("/api/v1/results") else "http://localhost:8083" if p.startswith("/sync") else LEGACY
        d = raw(m, base + p)
        g = raw(m, GW + p)
        same = d[0] == g[0] and d[1] == g[1] and normalize(d[2]) == normalize(g[2])
        if not same:
            diffs.append(f"{m} {p}: direct={d[0]} {d[1]} {d[2][:120]!r} gateway={g[0]} {g[1]} {g[2][:120]!r}")
        if g[3] != "legacy":
            diffs.append(f"{m} {p}: X-Route={g[3]}")
    check(f"transparency: {len(READS)} read requests byte-identical direct vs :8000 (headers normalized), all X-Route: legacy",
          not diffs, "; ".join(diffs))
    sched_before = json.loads(http("GET", f"{SCHED}/__stub/bookings")[1])

    reset_db()
    direct = write_sequence(LEGACY, s_bos, s_nyc)
    reset_db()
    via_gw = write_sequence(GW, s_bos, s_nyc)
    wdiffs = [f"{a[0]} {a[1]}: direct={a[2]} {a[4][:150]!r} gateway={b[2]} {b[4][:150]!r}"
              for a, b in zip(direct, via_gw)
              if (a[2], a[3], mask_wall_clock(a[4])) != (b[2], b[3], mask_wall_clock(b[4]))]
    check(f"transparency: {len(direct)} write requests (book/duplicate/not-open/validation/malformed/reschedule/cancel/404) "
          "identical direct vs :8000 after DB reset", not wdiffs, "; ".join(wdiffs))
    check("transparency: no scheduling-service writes with empty allow-list",
          json.loads(http("GET", f"{SCHED}/__stub/bookings")[1]) == sched_before)
    reset_db()

    # 2. C1 allow-listed, C2 not
    set_allow_list([C1])
    route, secs, st, b = wait_route("GET", f"/api/v1/sessions/{s_bos}", None, "modern")
    check(f"allow-list file change picked up without restart ({secs:.2f}s)", route == "modern" and secs < 5, f"{route}")
    before = legacy_booking_count("NW00000901")
    st, h, b, route = raw("POST", GW + "/api/v1/bookings", {"sessionId": s_bos, "candidateNumber": "NW00000901"})
    j = json.loads(b)
    check(f"C1 ({C1}) partner booking -> X-Route: modern, 201, v1 shape {{bookingId,status}}, id >= 50,000,000",
          route == "modern" and st == 201 and list(j) == ["bookingId", "status"] and j["bookingId"] >= 50_000_000
          and h.get("content-type") == "application/json;charset=UTF-8", f"{st} {route} {b!r} {h}")
    modern_id = j["bookingId"]
    stub = {x["bookingId"]: x for x in json.loads(http("GET", f"{SCHED}/__stub/bookings")[1])}
    check("C1 booking created in scheduling-service with channel PARTNER, no legacy Booking row",
          modern_id in stub and stub[modern_id]["channel"] == "PARTNER" and legacy_booking_count("NW00000901") == before,
          f"{stub.get(modern_id)}")
    before = legacy_booking_count("NW00000902")
    st, h, b, route = raw("POST", GW + "/api/v1/bookings", {"sessionId": s_nyc, "candidateNumber": "NW00000902"})
    j = json.loads(b)
    check(f"C2 ({C2}) partner booking -> X-Route: legacy, 201, legacy dbo.Booking row written",
          route == "legacy" and st == 201 and j["bookingId"] < 50_000_000 and legacy_booking_count("NW00000902") == before + 1,
          f"{st} {route} {b!r}")
    legacy_id = j["bookingId"]
    st, h, b, route = raw("POST", GW + "/api/v1/bookings", {"sessionId": s_bos, "candidateNumber": "NW00000901"})
    check("C1 duplicate booking -> modern 409 with v1 error body {error,message} (legacyCode stripped)",
          route == "modern" and st == 409 and list(json.loads(b)) == ["error", "message"], f"{st} {b!r}")
    st, h, b, route = raw("GET", GW + f"/api/v1/sessions?centerCode={C1}")
    check("C1 session list -> modern, v1 SessionInfo fields only",
          route == "modern" and st == 200 and list(json.loads(b)[0]) == list(json.loads(raw("GET", LEGACY + f"/api/v1/sessions/{s_bos}")[2])),
          f"{st} {b[:200]!r}")

    # 3. Portal
    st, h, b, route = raw("GET", GW + f"/Booking?CenterCode={C1}&CandidateNumber=NW00000903")
    check(f"portal /Booking?CenterCode={C1} -> 302 to modern UI /app?centerCode={C1}",
          st == 302 and h.get("location", "").startswith(f"/app?centerCode={C1}") and route == "modern", f"{st} {h}")
    st, h, b, route = raw("GET", GW + h.get("location", "/app"))
    check("modern UI served through :8000 (X-Route: modern)", st == 200 and b"Scheduling" in b and route == "modern", f"{st}")
    st, h, b, route = raw("GET", GW + f"/Booking?CenterCode={C2}&CandidateNumber=NW00000903")
    check(f"portal /Booking?CenterCode={C2} -> MVC page as before (X-Route: legacy)",
          st == 200 and b"Find exam sessions" in b and route == "legacy", f"{st} {route}")
    for p in ["/Admin/Centers", "/Eligibility", "/Results"]:
        st, h, b, route = raw("GET", GW + p)
        check(f"portal {p} passes through unchanged", st == 200 and route == "legacy" and b == raw("GET", "http://localhost:8080" + p)[2])

    # 4. Ownership: modern booking ids go modern regardless of the allow-list
    set_allow_list([])
    route, secs, st, b = wait_route("GET", f"/api/v1/sessions/{s_bos}", None, "legacy")
    check(f"rollback via watched file: next {C1} request legacy after {secs:.2f}s (< 5 s, no restart)",
          route == "legacy" and secs < 5)
    st, h, b, route = raw("POST", GW + f"/api/v1/bookings/{modern_id}/reschedule", {"newSessionId": open_session(C1, 1)})
    j = json.loads(b)
    check("modern booking reschedule via v1 with EMPTY allow-list -> modern, 200 {bookingId,status}",
          route == "modern" and st == 200 and list(j) == ["bookingId", "status"], f"{st} {route} {b!r}")
    st, h, b, route = raw("DELETE", GW + f"/api/v1/bookings/{j['bookingId']}")
    check("modern booking cancel via v1 with EMPTY allow-list -> modern, v1 BookingInfo, default reason",
          route == "modern" and st == 200 and json.loads(b)["cancelReason"] == "Cancelled by partner"
          and list(json.loads(b)) == list(json.loads(raw("GET", LEGACY + "/api/v1/bookings/1")[2])), f"{st} {route} {b!r}")
    st, h, b, route = raw("DELETE", GW + f"/api/v1/bookings/{legacy_id}")
    check("legacy booking cancel with empty allow-list -> legacy", route == "legacy" and st == 200, f"{st} {route}")

    # 5. Rollback via /actuator/refresh
    set_allow_list([C1], via="refresh")
    st, h, b, r1 = raw("GET", GW + f"/api/v1/sessions/{s_bos}")
    set_allow_list([], via="refresh")
    t0 = time.monotonic()
    st, h, b, r2 = raw("GET", GW + f"/api/v1/sessions/{s_bos}")
    check(f"rollback via POST /actuator/refresh: modern -> legacy on the next request ({time.monotonic() - t0:.2f}s)",
          r1 == "modern" and r2 == "legacy", f"{r1} {r2}")

    # 6. Rejected refresh keeps the previous list
    set_allow_list([C1, C2], via="refresh")
    time.sleep(1.5)
    st, h, b, route = raw("GET", GW + f"/api/v1/sessions/{open_session(C2)}")
    check(f"refresh adding production center {C2} without relay is rejected (C2 stays legacy)", route == "legacy")
    set_allow_list([])
    time.sleep(1.5)

    failures = sum(1 for r in RESULTS if not r["pass"])
    print(f"\n{len(RESULTS) - failures} passed, {failures} failed", flush=True)
    if os.environ.get("REPORT"):
        with open(os.environ["REPORT"], "w") as f:
            json.dump(RESULTS, f, indent=2)
    return failures


if __name__ == "__main__":
    sys.exit(main())
