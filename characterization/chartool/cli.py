"""Command line: `capture` (write golden transcripts from the legacy estate) and `replay` (compare a target)."""
import argparse
import json
import subprocess
import sys
from pathlib import Path

from . import LEGACY_BASELINE_SHA
from .config import CHAR_DIR, Config

TRANSCRIPTS = CHAR_DIR / "transcripts"
REPORTS = CHAR_DIR / "reports"
SCENARIOS = CHAR_DIR / "scenarios"


KNOWN_DEFECTS = Path(__file__).resolve().parents[1] / "known-defects.yaml"


def load_known_defects(path=KNOWN_DEFECTS):
    """{scenario: reason} for modern-target defects that are known and tracked (counted separately, never as MATCH)."""
    import yaml
    if not Path(path).exists():
        return {}
    data = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    return {d["transcript"]: {"reason": d["reason"], "signature": d.get("signature") or {}}
            for d in data.get("known_defects", [])}


CLASSIFICATION = Path(__file__).resolve().parents[1] / "modern-classification.yaml"


def load_classification(path=CLASSIFICATION):
    """(adr, not_replayable): {scenario: entry} from modern-classification.yaml.

    adr_divergences: differences ADR 0002 records as approved ({adr, reason, signature?}); a run only counts as
    ADR-DIVERGENCE when every differing field is listed in the entry's `fields` prefixes (when given) and the
    `signature` business fields (when given) are present - anything else stays a MISMATCH.
    not_replayable: scenarios whose legacy entry point has no modern equivalent ({reason, covered_by}); the replay
    raises Unsupported for them and they are reported as RETIRED with the stated reason, never as MATCH."""
    import yaml
    if not Path(path).exists():
        return {}, {}
    data = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    adr = {d["transcript"]: d for d in data.get("adr_divergences", [])}
    retired = {d["transcript"]: d for d in data.get("not_replayable", [])}
    return adr, retired


def _diffs_within(diffs, prefixes):
    return all(any(str(path).startswith(p) for p in prefixes) for path, _e, _a in diffs)


def _has_signature(actual, signature):
    return any(all(s["expected"]["business_fields"].get(k) == v for k, v in signature.items())
               for s in actual["steps"])


def classify_modern_mismatch(transcript, actual, known, adr=None, diffs=()):
    """MISMATCH by default; KNOWN-DEFECT when listed in known-defects.yaml and the actual run shows the entry's
    `signature` business fields (so an unrelated regression in that scenario is still a MISMATCH); SKIPPED when the gateway refused to
    route the center (CENTER_NOT_MODERN: the scenario's center is not on the allow-list, nothing reached modern)."""
    entry = known.get(transcript["scenario"])
    if entry is not None:
        if isinstance(entry, str):
            entry = {"reason": entry, "signature": {}}
        if _has_signature(actual, entry["signature"]):
            return "KNOWN-DEFECT", entry["reason"]
        return "MISMATCH", "listed in known-defects.yaml, but the known-defect signature %s is absent: a different "                            "regression" % json.dumps(entry["signature"], sort_keys=True)
    if any(s["expected"]["business_fields"].get("error") == "CENTER_NOT_MODERN" for s in actual["steps"]):
        return "SKIPPED", "gateway: center not on routing.modern-centers (CENTER_NOT_MODERN)"
    entry = (adr or {}).get(transcript["scenario"])
    if entry is not None:
        ok_fields = not entry.get("fields") or _diffs_within(diffs, entry["fields"])
        ok_sig = not entry.get("signature") or _has_signature(actual, entry["signature"])
        if ok_fields and ok_sig:
            return "ADR-DIVERGENCE", "ADR 0002 %s: %s" % (entry["adr"], entry["reason"])
        return "MISMATCH", "listed as ADR 0002 %s, but the run differs beyond it (fields %s, signature %s)" % (
            entry["adr"], entry.get("fields"), json.dumps(entry.get("signature"), sort_keys=True))
    return "MISMATCH", None


def dump(obj):
    return json.dumps(obj, indent=2, sort_keys=True, ensure_ascii=False, default=str) + "\n"


def transcript_path(t):
    return TRANSCRIPTS / t["group"] / (t["scenario"] + ".json")


def load_transcripts(patterns=None):
    from .scenarios import select
    items = [json.loads(p.read_text(encoding="utf-8")) for p in sorted(TRANSCRIPTS.glob("*/*.json"))]
    if patterns:
        names = {s["name"] for s in select([{"name": t["scenario"], "group": t["group"]} for t in items], patterns)}
        items = [t for t in items if t["scenario"] in names]
    return items


def estate_sha(directory):
    try:
        return subprocess.run(["git", "-C", str(directory), "rev-parse", "HEAD"], capture_output=True, text=True,
                              check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def prepare_database(config, fresh_seed):
    from . import db as dbm
    if fresh_seed:
        print("[char] rebuilding AssessmentDelivery with db/apply (fresh seed)")
        dbm.apply_seed(config)
        dbm.create_baseline(config)
    elif not dbm.baseline_exists(config):
        print("[char] no %s yet: rebuilding the seed and taking the pristine copy" % config.baseline_database)
        dbm.apply_seed(config)
        dbm.create_baseline(config)
    db = dbm.Db(config)
    dbm.reset(db, config)
    return db, dbm.baseline_max_ids(db, config)["Booking"]


def legacy_reset_fn(db, config):
    from . import db as dbm

    def reset(sc):
        dbm.reset(db, config)
        for sql in sc.get("setup_sql", []):
            db.run_script(sql)
    return reset


def legacy_target(config, db):
    from .adapters_legacy import PartnerV1Adapter, PortalAdapter
    from .runner import Target
    from .snapshot import LegacyState
    portal = PortalAdapter(config)
    adapters = {"PORTAL": portal, "ADMIN": portal,
                "PARTNER": PartnerV1Adapter(config.booking_api_url, config.http_timeout)}
    return Target("legacy", config, adapters, LegacyState(db), legacy_reset_fn(db, config),
                  sync_url=config.capacity_sync_url, mixed_urls=(config.portal_url, config.booking_api_url))


def modern_target(config, db, args):
    import requests

    from .adapters_modern import detect_partner_api, modern_adapters
    from .db import Db
    from .runner import Target
    from .snapshot import LegacyState, SchedulingState
    partner_api = args.partner_api if args.partner_api != "auto" else detect_partner_api(args.base_url)
    adapters = modern_adapters(args.base_url, partner_api, config.http_timeout)
    adapters["ADMIN"] = adapters["PORTAL"]
    sched = Db(config, config.scheduling_database)
    state = SchedulingState(sched, db) if args.db_state == "scheduling" else LegacyState(db)
    base_reset = legacy_reset_fn(db, config)

    def reset(sc):
        base_reset(sc)
        if args.modern_reset_cmd:
            subprocess.run(args.modern_reset_cmd, shell=True, check=True)
            return
        # Re-import the freshly reset legacy state into scheduling-service (contract item 4).
        # Pending outbox rows from the previous scenario would be relayed (M4) into the freshly reset legacy DB.
        sched.run_script("IF OBJECT_ID('dbo.outbox_event') IS NOT NULL DELETE FROM dbo.outbox_event; "
                         "DELETE FROM waitlist_entry; DELETE FROM capacity_change; DELETE FROM booking; "
                         "DELETE FROM exam_session;")
        r = requests.post(config.scheduling_url + "/internal/import/sessions", timeout=300)
        r.raise_for_status()
    # Behind the routing gateway (v1 served) every legacy entry point has a route: /sync/** -> capacity-sync,
    # /api/v1/results/** -> results-service, /api/v1/** -> booking-api v1 translation, /api/v2/** -> scheduling-service.
    gateway = partner_api == "v1"
    mixed = (args.base_url, args.base_url) if gateway else None
    print("[char] modern target %s (partner calls via %s, db-state %s)" % (args.base_url, partner_api, args.db_state))
    return Target("modern", config, adapters, state, reset, sync_url=args.base_url if gateway else None, mixed_urls=mixed,
                  results_url=args.base_url if gateway else None,
                  routing_url=args.base_url if gateway else None), partner_api


def cmd_capture(args):
    from .runner import run_scenario
    from .scenarios import load_all, select
    config = Config()
    scenarios = select(load_all(SCENARIOS), args.only)
    db, seed_max = prepare_database(config, args.fresh_seed)
    target = legacy_target(config, db)
    print("[char] capturing %d scenarios from the legacy estate (baseline %s)" % (len(scenarios), LEGACY_BASELINE_SHA[:7]))
    for sc in scenarios:
        t = run_scenario(sc, target, db, seed_max)
        path = transcript_path(t)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(dump(t), encoding="utf-8", newline="\n")
        print("  captured %-55s %s" % (sc["name"], t["expected"]["status"]))
    db.close()
    return 0


def cmd_replay(args):
    from .adapters_modern import Unsupported
    from .compare import compare
    from .report import render
    from .runner import run_scenario
    config = Config()
    transcripts = load_transcripts(args.only)
    if not transcripts:
        print("no transcripts found under %s" % TRANSCRIPTS)
        return 2
    db, seed_max = prepare_database(config, args.fresh_seed)
    if args.target == "legacy":
        target, extra = legacy_target(config, db), ""
    else:
        target, partner = modern_target(config, db, args)
        extra = " partner-api=%s db-state=%s" % (partner, args.db_state)
    known = load_known_defects() if args.target == "modern" else {}
    adr, retired = load_classification() if args.target == "modern" else ({}, {})
    results = []
    for t in transcripts:
        sc = {"name": t["scenario"], "group": t["group"], "channel": t["channel"], "setup_sql": t["setup_sql"],
              "sessions": [int(k.split(".")[1]) for k in t["expected"]["db_state_before"] if k.startswith("examSession.")],
              "candidates": sorted({r["candidate"] for r in t["expected"]["db_state_before"].get("results", [])}),
              "steps": [dict(s["source"]) for s in t["steps"]], "tags": t.get("tags", [])}
        try:
            actual = run_scenario(sc, target, db, seed_max)
            diffs = compare(t, actual, args.target)
            status, note = ("MISMATCH" if diffs else "MATCH"), None
            if diffs and args.target == "modern":
                status, note = classify_modern_mismatch(t, actual, known, adr, diffs)
            results.append({"transcript": t, "actual": actual, "diffs": diffs, "status": status, "note": note})
        except Unsupported as e:
            entry = retired.get(t["scenario"])
            if entry is not None:
                results.append({"transcript": t, "status": "RETIRED", "diffs": [],
                                "note": "%s Covered by: %s. (%s)" % (entry["reason"], entry["covered_by"], e)})
            else:
                results.append({"transcript": t, "status": "SKIPPED", "diffs": [], "note": str(e)})
        except Exception as e:  # noqa: BLE001 - report and continue with the next transcript
            results.append({"transcript": t, "status": "ERROR", "diffs": [], "note": "%s: %s" % (type(e).__name__, e)})
        print("  %-8s %s" % (results[-1]["status"], t["scenario"]))
    options = "target=%s%s%s" % (args.target, extra, (" only=" + ",".join(args.only)) if args.only else "")
    text, matched, total = render(results, args.target, args.base_url if args.target == "modern" else None,
                                  estate_sha(args.estate_dir), options)
    REPORTS.mkdir(exist_ok=True)
    name = Path(args.report).name if args.report else "replay-%s-%s.md" % (args.target, LEGACY_BASELINE_SHA[:7])
    (REPORTS / name).write_text(text, encoding="utf-8", newline="\n")
    print("[char] %d/%d MATCH -> %s" % (matched, total, REPORTS / name))
    db.close()
    return 0 if matched == total else 1


def main(argv=None):
    p = argparse.ArgumentParser(prog="characterization")
    sub = p.add_subparsers(dest="cmd", required=True)
    for name in ("capture", "replay"):
        s = sub.add_parser(name)
        s.add_argument("--only", nargs="*", help="scenario names / globs / group names")
        s.add_argument("--fresh-seed", action="store_true", help="run db/apply first and retake the pristine copy")
        s.add_argument("--estate-dir", default=str(Config().repo_root), help="git checkout the running estate was built from")
    r = sub.choices["replay"]
    r.add_argument("--target", choices=("legacy", "modern"), default="legacy")
    r.add_argument("--base-url", default="http://localhost:8090", help="modern target: scheduling-service or routing gateway")
    r.add_argument("--db-state", choices=("legacy", "scheduling"), default="legacy")
    r.add_argument("--partner-api", choices=("auto", "v1", "v2"), default="auto")
    r.add_argument("--modern-reset-cmd", help="shell command that re-syncs the modern target after the legacy reset")
    r.add_argument("--report", help="report file name under characterization/reports/")
    args = p.parse_args(argv)
    return cmd_capture(args) if args.cmd == "capture" else cmd_replay(args)


if __name__ == "__main__":
    sys.exit(main())
