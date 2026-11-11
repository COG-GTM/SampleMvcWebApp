"""Functional tests: routing-gateway against the REAL legacy booking-api (:8081, SQL Server in Docker), scheduling-service
on :8090 (the single writer since M4/M5) (the real one when SCHEDULING=real, else functional/scheduling_stub.py) and the portal stand-in (:8080).
Run via run-functional-tests.sh.

Each check prints PASS/FAIL; the exit code is the number of failures. A JSON report goes to $REPORT (optional).
"""
import json
import os
import subprocess
import sys
import time

from stub_common import SQLCMD, http, sql_rows

GW = "http://localhost:8000"
LEGACY = "http://localhost:8081"
SCHED = "http://localhost:8090"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
CONFIG = os.environ["ROUTING_CONFIG_FILE"]
C1, C2 = "BOS01", "NYC02"  # C1: seeded test/demo center, allow-listed; C2: production center, stays legacy
REAL_SCHEDULING = os.environ.get("SCHEDULING", "real") == "real"
SA_PASSWORD = os.environ.get("AD_SA_PASSWORD", "Str0ng!Passw0rd")
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


def set_allow_list(centers, via="file", relay=True):
    with open(CONFIG, "w") as f:
        f.write(f"routing.modern-centers={','.join(centers)}\n")
        if relay:  # post-M4/M5 shape (config/routing.properties): every write goes to the single writer, relay on
            f.write("routing.modern-write-centers=*\nrouting.coexistence-mode=relay\n")
    if via == "refresh":
        http("POST", f"{GW}/actuator/refresh")


def wait_route(method, path, body, want, timeout=5.0):
    start = time.monotonic()
    while True:
        st, _, b, route = raw(method, GW + path, body)
        if route == want or time.monotonic() - start > timeout:
            return route, time.monotonic() - start, st, b
        time.sleep(0.1)


def wait_for(cond, timeout=10.0):
    end = time.monotonic() + timeout
    while not cond():
        if time.monotonic() > end:
            return False
        time.sleep(0.25)
    return True


def legacy_booking_count(candidate):
    return int(sql_rows(f"SELECT COUNT(*) FROM dbo.Booking WHERE CandidateId = "
                        f"(SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = '{candidate}')")[0][0])


def modern_bookings():
    """bookingId -> {channel, status} of bookings created by scheduling-service (Scheduling DB, or the stub's memory)."""
    if not REAL_SCHEDULING:
        return {x["bookingId"]: x for x in json.loads(http("GET", f"{SCHED}/__stub/bookings")[1])}
    rows = sql_rows("SELECT booking_id, channel, status FROM dbo.booking WHERE origin = 'MODERN'",
                    db="Scheduling", user="sa", password=SA_PASSWORD)
    return {int(r[0]): {"bookingId": int(r[0]), "channel": r[1], "status": r[2]} for r in rows}


def open_session_same_exam(center, session_id):
    """Another open session at the center for the same exam (reschedule must keep the exam)."""
    rows = sql_rows(f"SELECT s.SessionId FROM dbo.ExamSession s JOIN dbo.TestCenter c ON c.TestCenterId = s.TestCenterId "
                    f"WHERE s.Status = 'OPEN' AND s.SeatsAvailable > 2 AND c.CenterCode = '{center}' AND s.SessionId <> {session_id} "
                    f"AND s.ExamId = (SELECT ExamId FROM dbo.ExamSession WHERE SessionId = {session_id}) ORDER BY s.StartsAt, s.SessionId")
    return int(rows[0][0])


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


LEGACY_LOGINS = ["ad_portal", "ad_booking_api", "ad_results", "ad_capacity_sync"]
RETIRED_PROCS = ["usp_BookSeat", "usp_CancelBooking", "usp_RescheduleBooking", "usp_ChangeCapacity"]


def sql_as(user, statement):
    """Run a statement as database user `user` (impersonated by sa); returns (exit code, combined output)."""
    q = (f"SET NOCOUNT ON; EXECUTE AS USER = '{user}'; BEGIN TRY {statement} END TRY BEGIN CATCH "
         "PRINT CONCAT('ERR ', ERROR_NUMBER(), ' ', ERROR_MESSAGE()); END CATCH; REVERT;")
    r = subprocess.run([SQLCMD, "-C", "-S", "localhost,1433", "-U", "sa", "-P", SA_PASSWORD, "-d", "AssessmentDelivery",
                        "-Q", q], capture_output=True, text=True)
    return r.returncode, (r.stdout + r.stderr).strip()


def legacy_writes_refused(s_nyc):
    """M4 single writer (db/security/040): every legacy login is DENIED booking/waitlist/seat writes, and the legacy
    booking procs are retired (db/procs/090), so nothing but the relay can write a booking into AssessmentDelivery."""
    candidate = sql_rows("SELECT CandidateId FROM dbo.Candidate WHERE CandidateNumber = 'NW00000954'")[0][0]
    statements = {
        "INSERT dbo.Booking": f"BEGIN TRAN; INSERT INTO dbo.Booking (SessionId, CandidateId, Status, Channel) "
                              f"VALUES ({s_nyc}, {candidate}, 'BOOKED', 'PARTNER'); ROLLBACK;",
        "UPDATE dbo.Booking": "BEGIN TRAN; UPDATE dbo.Booking SET Status = 'CANCELLED' WHERE BookingId = 1; ROLLBACK;",
        "UPDATE dbo.ExamSession.SeatsAvailable":
            f"BEGIN TRAN; UPDATE dbo.ExamSession SET SeatsAvailable = SeatsAvailable - 1 WHERE SessionId = {s_nyc}; ROLLBACK;",
    }
    problems = []
    for login in LEGACY_LOGINS:
        for name, stmt in statements.items():
            rc, out = sql_as(login, stmt)
            if "ERR 229" not in out and "ERR 230" not in out:
                problems.append(f"{login} {name}: not refused ({rc}) {out[:160]!r}")
    procs = sql_rows("SELECT name FROM sys.procedures WHERE name IN (%s)" % ",".join(f"'{p}'" for p in RETIRED_PROCS),
                     user="sa", password=SA_PASSWORD)
    return problems, [r[0] for r in procs]


def write_sequence(s_nyc):
    """v1 booking writes (C2, empty allow-list) through :8000. Since M4/M5 the legacy route is booking-api, which only
    validates and forwards the write to scheduling-service; the error paths must answer exactly as booking-api direct."""
    out = {}
    before = modern_bookings()
    st, h, b, route = raw("POST", GW + "/api/v1/bookings", {"sessionId": s_nyc, "candidateNumber": "NW00000951"})
    out["book"] = (st, h, b, route)
    created = {k: v for k, v in modern_bookings().items() if k not in before}
    errors = [
        ("duplicate", "POST", "/api/v1/bookings", {"sessionId": s_nyc, "candidateNumber": "NW00000951"}, 409),
        ("not open", "POST", "/api/v1/bookings", {"sessionId": 1, "candidateNumber": "NW00000952"}, 409),
        ("validation", "POST", "/api/v1/bookings", {"candidateNumber": "NW00000953"}, 400),
        ("malformed JSON", "POST", "/api/v1/bookings", b"{not json", 400),
        ("unknown booking", "DELETE", "/api/v1/bookings/49999999", None, 404),
    ]
    diffs = []
    for name, m, p, body, want in errors:
        g = raw(m, GW + p, body)
        d = raw(m, LEGACY + p, body)
        if g[0] != want or "error" not in json.loads(g[2]):
            diffs.append(f"{name}: gateway {g[0]} {g[2][:160]!r} (want {want} with an error body)")
        if (g[0], g[1], normalize(g[2])) != (d[0], d[1], normalize(d[2])):
            diffs.append(f"{name}: direct={d[0]} {d[2][:120]!r} gateway={g[0]} {g[2][:120]!r}")
    out["errors"] = (len(errors), diffs)
    if st == 201:
        bid = json.loads(b)["bookingId"]
        out["reschedule"] = raw("POST", GW + f"/api/v1/bookings/{bid}/reschedule",
                                {"newSessionId": open_session_same_exam(C2, s_nyc)})
        if out["reschedule"][0] == 200:
            out["cancel"] = raw("DELETE", GW + f"/api/v1/bookings/{json.loads(out['reschedule'][2])['bookingId']}"
                                "?reason=Functional%20test")
    return out, created


def normalize(body):
    """Mask values that differ between any two calls even to the same server: Spring Boot's error 'timestamp' and
    the PushbackInputStream@<hash> identity in Jackson parse errors."""
    import re
    body = re.sub(rb'"timestamp" : "[^"]*"', b'"timestamp" : "<ts>"', body)
    return re.sub(rb"@[0-9a-f]{4,}", b"@<hash>", body)


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

    # 1b. Single writer (M4/M5): direct legacy writes are refused; v1 writes through :8000 reach scheduling-service
    problems, procs = legacy_writes_refused(s_nyc)
    check(f"single writer: direct booking/seat writes refused (permission denied) for {', '.join(LEGACY_LOGINS)}",
          not problems, "; ".join(problems))
    check(f"single writer: legacy booking procs retired ({', '.join(RETIRED_PROCS)} absent)", not procs, f"{procs}")
    w, created = write_sequence(s_nyc)
    st, h, b, route = w["book"]
    j = json.loads(b) if b else {}
    check(f"C2 ({C2}) v1 booking through :8000 (empty allow-list) -> 201 v1 {{bookingId,status}}, written by "
          "scheduling-service with channel PARTNER",
          st == 201 and list(j) == ["bookingId", "status"] and len(created) == 1
          and list(created.values())[0]["channel"] == "PARTNER", f"{st} {route} {b!r} {created}")
    n, diffs = w["errors"]
    check(f"write error paths through :8000 ({n}: duplicate/not-open/validation/malformed JSON/unknown booking) answer "
          "with the v1 error status and body, identical to booking-api direct", not diffs, "; ".join(diffs))
    rs = w.get("reschedule")
    check("C2 v1 reschedule through :8000 -> 200 {bookingId,status}",
          rs is not None and rs[0] == 200 and list(json.loads(rs[2])) == ["bookingId", "status"], f"{rs}")
    c = w.get("cancel")
    check("C2 v1 cancel through :8000 -> 200 CANCELLED with the given reason",
          c is not None and c[0] == 200 and json.loads(c[2]).get("status") == "CANCELLED"
          and json.loads(c[2]).get("cancelReason") == "Functional test", f"{c}")

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
    stub = modern_bookings()
    # M4: the outbox relay projects every scheduling-service booking into dbo.Booking (the stub has no relay)
    projected = (wait_for(lambda: legacy_booking_count("NW00000901") == before + 1) if REAL_SCHEDULING
                 else legacy_booking_count("NW00000901") == before)
    check("C1 booking created in scheduling-service" + (" (Scheduling DB row, projected into dbo.Booking by the relay)"
          if REAL_SCHEDULING else ", no legacy Booking row (stub, no relay)") + " with channel PARTNER",
          modern_id in stub and stub[modern_id]["channel"] == "PARTNER" and projected,
          f"{stub.get(modern_id)} legacy rows {legacy_booking_count('NW00000901')} (before {before})")
    before = legacy_booking_count("NW00000902")
    st, h, b, route = raw("POST", GW + "/api/v1/bookings", {"sessionId": s_nyc, "candidateNumber": "NW00000902"})
    j = json.loads(b)
    relayed = wait_for(lambda: legacy_booking_count("NW00000902") == before + 1)
    check(f"C2 ({C2}, not on modern-centers) partner booking -> 201 via the gateway v1 translation (modern-write-centers=*), "
          "written by scheduling-service and projected into dbo.Booking by the relay",
          route == "modern" and st == 201 and j.get("bookingId") in modern_bookings() and relayed, f"{st} {route} {b!r}")
    legacy_id = j["bookingId"]
    st, h, b, route = raw("POST", GW + "/api/v1/bookings", {"sessionId": s_bos, "candidateNumber": "NW00000901"})
    check("C1 duplicate booking -> modern 409 with v1 error body {error,message} (legacyCode stripped)",
          route == "modern" and st == 409 and list(json.loads(b)) == ["error", "message"], f"{st} {b!r}")
    st, h, b, route = raw("GET", GW + f"/api/v1/sessions?centerCode={C1}")
    check("C1 session list -> modern, v1 SessionInfo fields only",
          route == "modern" and st == 200 and list(json.loads(b)[0]) == list(json.loads(raw("GET", LEGACY + f"/api/v1/sessions/{s_bos}")[2])),
          f"{st} {b[:200]!r}")

    day = sql_rows(f"SELECT CONVERT(char(10), StartsAt, 23) FROM dbo.ExamSession WHERE SessionId = {s_bos}")[0][0]
    q = f"/api/v1/sessions?centerCode={C1}&from={day}&to={day}"
    st, h, b, route = raw("GET", GW + q)
    ids = [x["sessionId"] for x in json.loads(b)] if st == 200 else None
    want = [x["sessionId"] for x in json.loads(raw("GET", LEGACY + q)[2])]
    check(f"C1 session search from={day}&to={day} -> modern, same sessions as legacy (v1 'to' includes the whole day)",
          route == "modern" and ids == want and s_bos in want, f"{st} {route} {ids} vs {want}")

    # 3. Portal
    st, h, b, route = raw("GET", GW + f"/Booking?CenterCode={C1}&CandidateNumber=NW00000903")
    check(f"portal /Booking?CenterCode={C1} -> 302 to modern UI /app?centerCode={C1}",
          st == 302 and h.get("location", "").startswith(f"/app?centerCode={C1}") and route == "modern", f"{st} {h}")
    st, h, b, route = raw("GET", GW + h.get("location", "/app"))
    check("modern UI served through :8000 (X-Route: modern)", st == 200 and b"Scheduling" in b and route == "modern", f"{st}")
    # MBA-3824: the .NET booking pages are retired, so C2 (never allow-listed) and center-less URLs go modern too
    for p, loc in [(f"/Booking?CenterCode={C2}&CandidateNumber=NW00000903", f"/app?centerCode={C2}"),
                   ("/Booking", "/app"),
                   ("/Booking/MyBookings?candidateNumber=NW00000042", "/app/my-bookings?candidateNumber=NW00000042"),
                   (f"/Booking/Book?sessionId={s_nyc}", f"/app/book?sessionId={s_nyc}"),
                   (f"/Admin/Sessions/Capacity/{s_nyc}", f"/app/admin/sessions/{s_nyc}")]:
        st, h, b, route = raw("GET", GW + p)
        st2 = raw("GET", GW + h.get("location", "/app"))[0]
        check(f"retired portal {p} -> 302 {loc} then 200 (X-Route: modern)",
              st == 302 and h.get("location", "").startswith(loc) and route == "modern" and st2 == 200, f"{st} {h} {st2}")
    for p in ["/Admin/Centers", "/Eligibility", "/Results"]:
        st, h, b, route = raw("GET", GW + p)
        check(f"portal {p} passes through unchanged", st == 200 and route == "legacy" and b == raw("GET", "http://localhost:8080" + p)[2])

    # 4. Ownership: modern booking ids go modern regardless of the allow-list
    set_allow_list([])
    route, secs, st, b = wait_route("GET", f"/api/v1/sessions/{s_bos}", None, "legacy")
    check(f"rollback via watched file: next {C1} request legacy after {secs:.2f}s (< 5 s, no restart)",
          route == "legacy" and secs < 5)
    st, h, b, route = raw("POST", GW + f"/api/v1/bookings/{modern_id}/reschedule", {"newSessionId": open_session_same_exam(C1, s_bos)})
    j = json.loads(b)
    check("modern booking reschedule via v1 with EMPTY allow-list -> modern, 200 {bookingId,status}",
          route == "modern" and st == 200 and list(j) == ["bookingId", "status"], f"{st} {route} {b!r}")
    st, h, b, route = raw("DELETE", GW + f"/api/v1/bookings/{j['bookingId']}")
    check("modern booking cancel via v1 with EMPTY allow-list -> modern, v1 BookingInfo, default reason",
          route == "modern" and st == 200 and json.loads(b)["cancelReason"] == "Cancelled by partner"
          and list(json.loads(b)) == list(json.loads(raw("GET", LEGACY + "/api/v1/bookings/1")[2]))
          and modern_bookings().get(j["bookingId"], {}).get("status") == "CANCELLED", f"{st} {route} {b!r}")
    st, h, b, route = raw("DELETE", GW + f"/api/v1/bookings/{legacy_id}")
    # ids written since M4 are scheduling-service ids, so ownership routing decides, not the (empty) allow-list
    want = "modern" if legacy_id >= 50_000_000 else "legacy"
    check(f"C2 booking cancel with empty allow-list -> {want} by booking id, 200", route == want and st == 200, f"{st} {route}")

    # 5. Rollback via /actuator/refresh
    set_allow_list([C1], via="refresh")
    st, h, b, r1 = raw("GET", GW + f"/api/v1/sessions/{s_bos}")
    set_allow_list([], via="refresh")
    t0 = time.monotonic()
    st, h, b, r2 = raw("GET", GW + f"/api/v1/sessions/{s_bos}")
    check(f"rollback via POST /actuator/refresh: modern -> legacy on the next request ({time.monotonic() - t0:.2f}s)",
          r1 == "modern" and r2 == "legacy", f"{r1} {r2}")

    # 6. Rejected refresh keeps the previous list
    set_allow_list([C1, C2], via="refresh", relay=False)
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
