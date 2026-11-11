"""Runs one scenario against a target and produces a (normalized) transcript."""
import datetime as _dt
import os
import subprocess
import sys

import requests

from . import LEGACY_BASELINE_SHA, LEGACY_BASELINE_TAG, TRANSCRIPT_FORMAT
from .adapters_modern import Unsupported
from .db import legacy_tree
from .normalize import IdMap, deep_norm
from .snapshot import all_booking_ids, normalize_state


def resolve(value, db, step_results):
    """Step parameters may be '@sql:<query>' (first column of first row) or '@stepN' (booking created by step N)."""
    if isinstance(value, str) and value.startswith("@sql:"):
        return db.scalar(value[5:])
    if isinstance(value, str) and value.startswith("@step"):
        return step_results[int(value[5:]) - 1].get("booking_id")
    return value


def run_mixed_traffic(config, step, portal_url, booking_api_url, routing_url=None):
    """Legacy: the legacy-baseline script (real portal forms + booking-api). With routing_url (modern, through the
    routing gateway): this checkout's script, portal channel = POST/DELETE <routing_url>/api/v2/bookings (channel
    PORTAL, what the modern UI does; the portal booking forms were retired in MBA-3824), partner channel = booking-api
    v1 through the gateway."""
    env = dict(os.environ, PORTAL_URL=portal_url, BOOKING_API_URL=booking_api_url, AD_SQL_SERVER=config.sql_server,
               AD_SA_PASSWORD=config.sa_password, SQLCMD=config.sqlcmd)
    if routing_url:
        env["ROUTING_URL"] = routing_url
    for key, value in (step.get("env") or {}).items():
        env[key] = str(value)
    root = config.repo_root if routing_url else legacy_tree(config)
    if sys.platform.startswith("win"):
        cmd = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
               str(root / "scripts" / "mixed-traffic.ps1")]
    else:
        cmd = ["bash", str(root / "scripts" / "mixed-traffic.sh")]
    p = subprocess.run(cmd, env=env, capture_output=True, text=True)
    lines = [ln for ln in (p.stdout or "").splitlines() if ln.startswith("[mixed-traffic]")]
    return {"request": {"command": "scripts/mixed-traffic", "env": {k: str(v) for k, v in (step.get("env") or {}).items()}},
            "response": {"exitCode": p.returncode,
                         "summary": [ln for ln in lines if any(w in ln for w in ("portal :", "partner:", "cancelled:"))]},
            "business": {"outcome": "OK" if p.returncode == 0 else "ERROR"}}


class Target:
    """name: legacy|modern; adapters: channel -> adapter; state: LegacyState/SchedulingState."""

    def __init__(self, name, config, adapters, state, reset_fn, sync_url=None, mixed_urls=None, results_url=None,
                 routing_url=None):
        self.name, self.config, self.adapters, self.state = name, config, adapters, state
        self.reset_fn, self.sync_url, self.mixed_urls = reset_fn, sync_url, mixed_urls
        # results_url: where `result` steps POST /api/v1/results (legacy results-service, or the gateway route to it)
        # routing_url: set for the modern target behind the routing gateway (mixed traffic uses the modern write path)
        self.results_url = results_url or (config.results_url if name == "legacy" else None)
        self.routing_url = routing_url


def execute_step(target, step, db, sc, results):
    op = step["op"]
    channel = step.get("channel") or sc["channel"]
    p = {k: resolve(v, db, results) for k, v in step.items()}
    if op == "drift":
        rows = target.state.drift(legacy_tree(target.config))
        return {"request": {"sql": "db/checks/seat-drift.sql"}, "response": {"rows": rows},
                "business": {"driftSessions": len(rows), "totalDrift": sum(r["Drift"] for r in rows)}}
    if op == "sync":
        if not target.sync_url:
            raise Unsupported("capacity-sync has no equivalent on target %s" % target.name)
        r = requests.post(target.sync_url + "/sync/run", timeout=target.config.http_timeout)
        body = r.json() if r.headers.get("Content-Type", "").startswith("application/json") else r.text
        keep = {k: body[k] for k in ("examined", "updated", "skipped", "status") if isinstance(body, dict) and k in body}
        return {"request": {"method": "POST", "path": "/sync/run"}, "response": {"status": r.status_code, "body": keep},
                "business": {"outcome": "OK" if r.status_code == 200 else "ERROR"}}
    if op == "sql" and target.name != "legacy":
        raise Unsupported("sql steps act on the legacy estate directly; no equivalent on target %s" % target.name)
    if op == "result" and not target.results_url:
        raise Unsupported("result steps need results-service; no route to it on target %s" % target.name)
    if op in ("sql", "result"):
        if op == "sql":
            rows = db.rows("SET NOCOUNT ON; " + p["sql"])
            res = {"request": {"sql": p["sql"]}, "response": {"rows": rows}, "business": {"rows": rows}}
            if rows and rows[0].get("BookingId"):
                res["booking_id"] = rows[0]["BookingId"]
            return res
        body = {"bookingId": int(p["booking"]), "score": p["score"], "scoredAt": p.get("scoredAt")}
        r = requests.post(target.results_url + "/api/v1/results", json=body, timeout=target.config.http_timeout)
        out = r.json() if r.headers.get("Content-Type", "").startswith("application/json") else r.text
        return {"request": {"method": "POST", "path": "/api/v1/results", "json": body},
                "response": {"status": r.status_code, "body": out},
                "business": {"outcome": "INGESTED" if r.status_code == 201 else "ERROR",
                             "passed": out.get("passed") if isinstance(out, dict) else None}}
    if op == "mixed_traffic":
        if not target.mixed_urls:
            raise Unsupported("mixed traffic needs portal + v1 endpoints (use the routing gateway)")
        return run_mixed_traffic(target.config, step, *target.mixed_urls, routing_url=target.routing_url)
    adapter = target.adapters.get(channel)
    if adapter is None or not hasattr(adapter, op):
        raise Unsupported("%s has no %s operation on target %s" % (channel, op, target.name))
    if op == "book":
        return adapter.book(int(p["session"]), p["candidate"], p.get("accommodation"), p.get("externalRef"))
    if op == "cancel":
        extra = {"candidate": p["candidate"]} if p.get("candidate") else {}
        return adapter.cancel(int(p["booking"]), p.get("reason"), **extra)
    if op == "sessions":
        return adapter.sessions({k: str(p[k]) for k in ("centerCode", "examCode", "from", "to") if p.get(k)})
    if op == "session":
        return adapter.session(int(p["session"]))
    if op == "search":
        return adapter.search(p["centerCode"], [int(i) for i in p.get("probe", [])])
    if op == "reschedule":
        return adapter.reschedule(int(p["booking"]), int(p["newSession"]))
    if op == "capacity":
        return adapter.capacity(int(p["session"]), p["newCapacity"])
    return adapter.eligibility(p["candidate"], p["examCode"], str(p["date"]))


def _waitlist_position(raw, booking_id):
    for w in raw["waitlist"]:
        if w["BookingId"] == booking_id:
            return w["Position"]
    return None


def run_scenario(sc, target, db, seed_max):
    # whole seconds and a small margin: booking-api truncates timestamps, SQL datetime rounds to 3 ms
    since = db.now().replace(microsecond=0) - _dt.timedelta(seconds=2)
    target.reset_fn(sc)
    state = target.state
    pre = state.max_ids()
    before = state.read(sc["sessions"], sc["candidates"], pre["AuditLog"])
    results, raw_steps = [], []
    for step in sc["steps"]:
        res = execute_step(target, step, db, sc, results)
        results.append(res)
        sessions = sorted(set(sc["sessions"]) | set(state.sessions_with_new_bookings(pre["Booking"])))
        after = state.read(sessions, sc["candidates"], pre["AuditLog"])
        biz = res["business"]
        if step["op"] in ("book", "reschedule", "capacity"):
            sid = step.get("newSession") or step.get("session")
            sid = resolve(sid, db, results)
            if sid is not None and int(sid) in after["sessions"]:
                biz["seatsAvailable"] = after["sessions"][int(sid)]["SeatsAvailable"]
        if step["op"] in ("book", "reschedule"):
            biz["waitlistPosition"] = _waitlist_position(after, res.get("booking_id"))
        if step["op"] == "capacity":
            prev = raw_steps[-1][1] if raw_steps else before
            was = {b["BookingId"]: b["Status"] for b in prev["bookings"]}
            biz["bumped"] = [b["BookingId"] for b in after["bookings"]
                             if b["Status"] == "WAITLISTED" and was.get(b["BookingId"]) == "BOOKED"]
        raw_steps.append((res, after))
    final = raw_steps[-1][1]
    idmap = IdMap(seed_max, pre["Booking"])
    idmap.add(all_booking_ids(before) + all_booking_ids(final) +
              [r.get("booking_id") for r in results if r.get("booking_id")])
    steps_out = []
    for step, (res, _after) in zip(sc["steps"], raw_steps):
        biz = dict(res["business"])
        if "bumped" in biz:
            biz["bumped"] = [idmap.label(i) for i in biz["bumped"]]
        steps_out.append({
            "op": step["op"], "channel": step.get("channel") or sc["channel"], "source": step,
            "request": deep_norm(res["request"], idmap, since),
            "expected": {"status": res["response"].get("status", res["response"].get("exitCode")),
                         "response": deep_norm(res["response"], idmap, since),
                         "business_fields": deep_norm(biz, idmap, since)}})
    return {
        "format": TRANSCRIPT_FORMAT,
        "scenario": sc["name"], "group": sc["group"], "channel": sc["channel"],
        "description": sc.get("description", ""), "oddity": bool(sc.get("oddity")),
        "oddity_note": sc.get("oddity_note"), "tags": sc.get("tags", []),
        "finding": sc.get("finding", []), "tests": sc.get("tests", []),
        "modern_expected_mismatch": sc.get("modern_expected_mismatch"),
        "legacy_baseline": {"tag": LEGACY_BASELINE_TAG, "sha": LEGACY_BASELINE_SHA},
        "setup_sql": sc.get("setup_sql", []),
        "steps": steps_out,
        "expected": {"status": steps_out[-1]["expected"]["status"],
                     "business_fields": steps_out[-1]["expected"]["business_fields"],
                     "db_state_before": normalize_state(before, idmap, since),
                     "db_state": normalize_state(final, idmap, since)},
    }
