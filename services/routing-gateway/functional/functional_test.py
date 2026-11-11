"""Functional acceptance checks for MBA-3822 against a running estate (legacy :8080/:8081, scheduling-service
:8090, routing-gateway :8000). Edits the gateway's allow-list file and restores it to empty at the end.

    python services/routing-gateway/functional/functional_test.py   (or run-functional-tests.sh / .ps1)

Env: GATEWAY_URL, SCHEDULING_URL, ROUTING_CONFIG_FILE, GATEWAY_JAR, JAVA (JDK 21), SQLCMD, AD_SQL_SERVER,
MODERN_CENTER (BOS01), LEGACY_SESSION (154, a non-allow-listed centre with free seats).
"""
import json
import os
import pathlib
import subprocess
import sys
import time
import urllib.error
import urllib.request

HERE = pathlib.Path(__file__).resolve().parent
GW = os.environ.get("GATEWAY_URL", "http://localhost:8000")
SCHED = os.environ.get("SCHEDULING_URL", "http://localhost:8090")
CONFIG = pathlib.Path(os.environ.get("ROUTING_CONFIG_FILE", HERE.parent / "config" / "routing.properties"))
JAR = pathlib.Path(os.environ.get("GATEWAY_JAR", HERE.parent / "target" / "routing-gateway.jar"))
JAVA = os.environ.get("JAVA", str(pathlib.Path(os.environ.get("JAVA_HOME", "")) / "bin" / "java"))
SQLCMD = os.environ.get("SQLCMD", "sqlcmd")
SQL_SERVER = os.environ.get("AD_SQL_SERVER", "localhost,1433")
C1 = os.environ.get("MODERN_CENTER", "BOS01")
LEGACY_SESSION = int(os.environ.get("LEGACY_SESSION", "154"))
FLOOR = 50_000_000
results = []


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


OPENER = urllib.request.build_opener(NoRedirect)


def http(method, url, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        r = OPENER.open(req, timeout=30)
        status, headers, raw = r.status, r.headers, r.read()
    except urllib.error.HTTPError as e:
        status, headers, raw = e.code, e.headers, e.read()
    text = raw.decode("utf-8", "replace")
    try:
        parsed = json.loads(text) if text else None
    except ValueError:
        parsed = text
    return status, headers, parsed


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(("PASS " if cond else "FAIL ") + name + (f"  [{detail}]" if detail else ""), flush=True)


def sql(query):
    out = subprocess.run([SQLCMD, "-S", SQL_SERVER, "-E", "-C", "-d", "AssessmentDelivery", "-h", "-1", "-W",
                          "-Q", "SET NOCOUNT ON; " + query], capture_output=True, text=True, stdin=subprocess.DEVNULL)
    return out.stdout.strip()


def set_allow_list(codes):
    CONFIG.write_text("# functional test\nrouting.modern-centers=" + ",".join(codes) + "\n", encoding="utf-8")


def wait_route(url, want, limit=5.0):
    t0 = time.time()
    while time.time() - t0 < limit:
        _, h, _ = http("GET", url)
        if h.get("X-Route") == want:
            return time.time() - t0
        time.sleep(0.1)
    return None


def free_sessions(center, n):
    _, _, sessions = http("GET", f"{SCHED}/api/v2/centers/{center}/sessions")
    ok = [s for s in sessions or [] if s.get("status") == "OPEN" and s.get("seatsAvailable", 0) > 0
          and str(s.get("startsAt")) > "2026-11-12"]
    by_exam = {}
    for s in ok:
        by_exam.setdefault(s.get("examCode"), []).append(s["sessionId"])
    same_exam = [ids for ids in by_exam.values() if len(ids) >= n]  # reschedule needs the same exam
    return same_exam[0][:n] if same_exam else [s["sessionId"] for s in ok[:n]]


def main():
    stamp = int(time.time())
    cand = lambda i: f"NW{900 + (stamp + i) % 90 + 10:08d}"
    s, _, health = http("GET", f"{GW}/actuator/health")
    check("gateway healthy on :8000", s == 200 and health.get("status") == "UP")
    set_allow_list([])
    check("empty allow-list: /Booking?CenterCode=C1 renders legacy",
          wait_route(f"{GW}/Booking?CenterCode={C1}", "legacy") is not None)

    set_allow_list([C1])
    t = wait_route(f"{GW}/Booking?CenterCode={C1}", "modern")
    check(f"file edit adds {C1}: routed modern without restart", t is not None, f"{t and round(t, 2)} s")

    s, h, _ = http("GET", f"{GW}/Booking?CenterCode={C1}&ExamCode=CTA-SEC2")
    check("portal: C1 /Booking redirects to modern UI", s == 302 and h.get("Location", "").startswith(f"/app/?center={C1}"),
          h.get("Location"))
    legacy_center = sql(f"SELECT t.CenterCode FROM ExamSession s JOIN TestCenter t ON t.TestCenterId=s.TestCenterId WHERE s.SessionId={LEGACY_SESSION}")
    s, h, body = http("GET", f"{GW}/Booking?CenterCode={legacy_center}")
    check(f"portal: C2 ({legacy_center}) /Booking renders MVC 5 page", s == 200 and h.get("X-Route") == "legacy"
          and isinstance(body, str) and "<html" in body.lower())
    s, h, body = http("GET", f"{GW}/app/")
    check("modern UI proxied through :8000", s == 200 and h.get("X-Route") == "modern" and "root" in str(body))

    sessions = free_sessions(C1, 2)
    check(f"{C1} has two bookable same-exam sessions in scheduling-service", len(sessions) == 2, str(sessions))
    s, h, b1 = http("POST", f"{GW}/api/v1/bookings", {"sessionId": sessions[0], "candidateNumber": cand(0), "externalRef": f"FT-{stamp}"})
    check("v1 book on C1 session: 201, X-Route modern, v1 shape {bookingId,status}",
          s == 201 and h.get("X-Route") == "modern" and isinstance(b1, dict) and set(b1) == {"bookingId", "status"}, f"{s} {b1}")
    modern_id = b1.get("bookingId") if isinstance(b1, dict) else None
    check("modern booking id >= routing.modern-booking-id-floor", modern_id is not None and modern_id >= FLOOR, str(modern_id))
    s, _, mb = http("GET", f"{SCHED}/api/v2/bookings/{modern_id}")
    check("booking exists in scheduling-service", s == 200 and mb.get("sessionId") == sessions[0])
    check("no legacy Booking row for the modern booking", sql(f"SELECT COUNT(*) FROM Booking WHERE BookingId={modern_id}") == "0")

    before = sql("SELECT COUNT(*) FROM Booking")
    s, h, b2 = http("POST", f"{GW}/api/v1/bookings", {"sessionId": LEGACY_SESSION, "candidateNumber": cand(1)})
    legacy_id = b2.get("bookingId") if isinstance(b2, dict) else None
    check("v1 book on C2 session: 201, X-Route legacy, v1 shape", s == 201 and h.get("X-Route") == "legacy"
          and isinstance(b2, dict) and set(b2) == {"bookingId", "status"}, f"{s} {b2}")
    check("legacy Booking row written", legacy_id is not None and
          sql(f"SELECT COUNT(*) FROM Booking WHERE BookingId={legacy_id} AND SessionId={LEGACY_SESSION}") == "1",
          f"Booking count {before} -> {sql('SELECT COUNT(*) FROM Booking')}")

    s, _, err = http("POST", f"{GW}/api/v2/bookings", {"sessionId": LEGACY_SESSION, "candidateNumber": cand(2)})
    check("v2 write on non-allow-listed centre: 403 CENTER_NOT_MODERN", s == 403 and err.get("error") == "CENTER_NOT_MODERN")

    s, h, b3 = http("POST", f"{GW}/api/v1/bookings", {"sessionId": sessions[1], "candidateNumber": cand(3)})
    second_id = b3.get("bookingId") if isinstance(b3, dict) else None

    set_allow_list([])
    t = wait_route(f"{GW}/Booking?CenterCode={C1}", "legacy")
    check("rollback: C1 removed from file -> next C1 request legacy within 5 s, no restart", t is not None, f"{t and round(t, 2)} s")
    s, h, _ = http("GET", f"{GW}/api/v1/sessions?centerCode={C1}")
    check("rollback: v1 sessions for C1 now legacy", h.get("X-Route") == "legacy")

    s, h, c = http("DELETE", f"{GW}/api/v1/bookings/{modern_id}")
    check("ownership: v1 cancel of modern booking routes modern after rollback", h.get("X-Route") == "modern" and s == 200
          and isinstance(c, dict) and c.get("status") == "CANCELLED", f"{s} {c}")
    s, h, r = http("POST", f"{GW}/api/v1/bookings/{second_id}/reschedule", {"newSessionId": sessions[0]})
    check("ownership: v1 reschedule of modern booking routes modern after rollback", h.get("X-Route") == "modern"
          and s == 200 and isinstance(r, dict) and set(r) == {"bookingId", "status"}, f"{s} {r}")

    set_allow_list([C1])
    http("POST", f"{GW}/actuator/refresh")
    _, h, _ = http("GET", f"{GW}/Booking?CenterCode={C1}")
    check("POST /actuator/refresh applies the allow-list immediately", h.get("X-Route") == "modern")
    set_allow_list([])
    t0 = time.time()
    http("POST", f"{GW}/actuator/refresh")
    _, h, _ = http("GET", f"{GW}/Booking?CenterCode={C1}")
    check("rollback via POST /actuator/refresh within 5 s", h.get("X-Route") == "legacy" and time.time() - t0 < 5,
          f"{round(time.time() - t0, 2)} s")

    if JAR.exists() and pathlib.Path(JAVA).parent.exists():
        p = subprocess.run([JAVA, "-jar", str(JAR), "--server.port=0", "--routing.config-file=none",
                            "--routing.modern-centers=LON01"], capture_output=True, text=True, timeout=120,
                           stdin=subprocess.DEVNULL)
        check("startup guard: non-test centre without relay refuses to start", p.returncode != 0
              and "coexistence-mode=relay" in (p.stdout + p.stderr), f"exit {p.returncode}")

    failed = [n for n, ok, _ in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    finally:
        set_allow_list([])
