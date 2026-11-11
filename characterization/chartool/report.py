"""Markdown replay report: X/Y MATCH per scenario group, pass/fail per scenario, mismatch details."""
import json

from . import LEGACY_BASELINE_SHA, LEGACY_BASELINE_TAG

GROUP_ORDER = ["booking", "full_session", "duplicate_closed", "cancellation", "reschedule", "capacity_increase",
               "capacity_decrease", "retake_eligibility_portal", "retake_eligibility_partner", "accommodation",
               "mixed_traffic"]


STATUSES = ("MATCH", "MISMATCH", "KNOWN-DEFECT", "SKIPPED", "ERROR")


def report_group(transcript):
    """Report bucket: capacity split into increase/decrease, retake eligibility split per channel."""
    group = transcript["group"]
    if group == "capacity":
        return "capacity_increase" if "increase" in transcript.get("tags", []) else "capacity_decrease"
    if group == "retake_eligibility":
        return "retake_eligibility_%s" % transcript["channel"].lower()
    return group


def _block(obj):
    return "```json\n%s\n```" % json.dumps(obj, indent=2, sort_keys=True, default=str)


def render(results, target, base_url, estate_sha, options):
    """results: list of dicts {transcript, status: MATCH|MISMATCH|SKIPPED|ERROR, diffs, actual, note}."""
    groups = {}
    for r in results:
        g = report_group(r["transcript"])
        groups.setdefault(g, []).append(r)
    total = len(results)
    matched = sum(1 for r in results if r["status"] == "MATCH")
    lines = ["# Characterization replay: target `%s`" % target, "",
             "- Transcripts captured from tag `%s` = `%s`" % (LEGACY_BASELINE_TAG, LEGACY_BASELINE_SHA),
             "- Replayed estate commit: `%s`" % estate_sha,
             "- Target: `%s`%s" % (target, (" base url `%s`" % base_url) if base_url else ""),
             "- Options: `%s`" % options, "",
             "**Overall: %d/%d MATCH**" % (matched, total), "",
             "Status counts: %s" % ", ".join("%s %d" % (s, sum(1 for r in results if r["status"] == s))
                                             for s in STATUSES), "",
             "KNOWN-DEFECT = differs from the legacy transcript for a reason listed in `characterization/known-defects.yaml`;"
             " counted separately (never as MATCH, never hidden).", "",
             "## Totals per scenario group", "", "| Group | Result | Mismatch | Known defect | Skipped | Error |",
             "|---|---|---|---|---|---|"]
    for g in GROUP_ORDER + sorted(set(groups) - set(GROUP_ORDER)):
        rs = groups.get(g, [])
        if not rs:
            continue
        count = lambda s: sum(1 for r in rs if r["status"] == s)  # noqa: E731
        lines.append("| %s | %d/%d MATCH | %d | %d | %d | %d |" % (g, count("MATCH"), len(rs), count("MISMATCH"),
                                                                  count("KNOWN-DEFECT"), count("SKIPPED"), count("ERROR")))
    lines += ["", "## Per scenario", "", "| Scenario | Group | Channel | Oddity | Result | Note |", "|---|---|---|---|---|---|"]
    for r in sorted(results, key=lambda r: (GROUP_ORDER.index(report_group(r["transcript"]))
                                            if report_group(r["transcript"]) in GROUP_ORDER else 99,
                                            r["transcript"]["scenario"])):
        t = r["transcript"]
        note = r.get("note") or (t.get("modern_expected_mismatch") if r["status"] == "MISMATCH" and target != "legacy" else "")
        lines.append("| %s | %s | %s | %s | %s | %s |" % (t["scenario"], report_group(t), t["channel"],
                                                       "yes" if t.get("oddity") else "", r["status"], note or ""))
    bad = [r for r in results if r["status"] in ("MISMATCH", "KNOWN-DEFECT", "ERROR")]
    if bad:
        lines += ["", "## Mismatches", ""]
    for r in bad:
        t = r["transcript"]
        lines += ["### %s (%s)" % (t["scenario"], r["status"]), "",
                  "Baseline SHA `%s`; expected mismatch per ADR 0002: %s" % (LEGACY_BASELINE_SHA,
                                                                            t.get("modern_expected_mismatch") or "none"), ""]
        if r.get("note"):
            lines += ["Error: `%s`" % r["note"], ""]
        lines += ["Request (per step):", "", _block([s["request"] for s in t["steps"]]), ""]
        if r.get("diffs"):
            lines += ["| Field | Expected | Actual |", "|---|---|---|"]
            for path, e, a in r["diffs"][:60]:
                lines.append("| `%s` | `%s` | `%s` |" % (path, json.dumps(e, default=str), json.dumps(a, default=str)))
            if len(r["diffs"]) > 60:
                lines.append("| ... %d more | | |" % (len(r["diffs"]) - 60))
            lines.append("")
        if r.get("actual"):
            lines += ["<details><summary>Expected vs actual business fields</summary>", "",
                      _block({"expected": [s["expected"]["business_fields"] for s in t["steps"]],
                              "actual": [s["expected"]["business_fields"] for s in r["actual"]["steps"]]}),
                      "", "</details>", ""]
    return "\n".join(lines) + "\n", matched, total
