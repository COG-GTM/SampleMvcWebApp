import json
from pathlib import Path

import pytest

from chartool import LEGACY_BASELINE_SHA
from chartool.compare import compare, modern_business
from chartool.report import render, report_group
from chartool.scenarios import ScenarioError, load_all, select, validate

CHAR = Path(__file__).resolve().parent.parent


def test_catalogue_loads_and_has_at_least_40_scenarios_covering_every_group():
    scenarios = load_all(CHAR / "scenarios")
    assert len(scenarios) >= 40
    groups = {s["group"] for s in scenarios}
    assert {"booking", "full_session", "duplicate_closed", "cancellation", "reschedule", "capacity",
            "retake_eligibility", "accommodation", "mixed_traffic"} <= groups
    for group in ("booking", "full_session", "duplicate_closed", "cancellation", "reschedule",
                  "retake_eligibility", "accommodation"):
        assert {"PORTAL", "PARTNER"} <= {s["channel"] for s in scenarios if s["group"] == group}, group


def test_every_scenario_has_a_transcript_with_the_baseline_sha():
    names = {s["name"] for s in load_all(CHAR / "scenarios")}
    transcripts = {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in (CHAR / "transcripts").glob("*/*.json")}
    assert names == set(transcripts)
    for t in transcripts.values():
        assert t["legacy_baseline"]["sha"] == LEGACY_BASELINE_SHA
        assert {"status", "business_fields", "db_state", "db_state_before"} <= set(t["expected"])


def test_every_oddity_is_listed_in_oddities_md():
    text = (CHAR / "ODDITIES.md").read_text(encoding="utf-8")
    for s in load_all(CHAR / "scenarios"):
        if s.get("oddity"):
            assert "`%s`" % s["name"] in text, s["name"]


def test_validation_rejects_bad_scenarios():
    with pytest.raises(ScenarioError):
        validate({"name": "x", "group": "g", "channel": "FAX", "steps": [{"op": "book"}]})
    with pytest.raises(ScenarioError):
        validate({"name": "x", "group": "g", "channel": "PORTAL", "steps": [{"op": "teleport"}]})
    with pytest.raises(ScenarioError):
        validate({"name": "x", "group": "g", "channel": "PORTAL", "oddity": True, "steps": [{"op": "book"}]})


def test_select_by_glob_or_group():
    items = [{"name": "book_a", "group": "booking"}, {"name": "cancel_b", "group": "cancellation"}]
    assert select(items, ["book_*"]) == items[:1]
    assert select(items, ["cancellation"]) == items[1:]


def _t(business, db_state, group="booking", channel="PORTAL", tags=()):
    step = {"op": "book", "request": {}, "expected": {"status": 302, "response": {"status": 302},
                                                     "business_fields": business}}
    return {"scenario": "s1", "group": group, "channel": channel, "tags": list(tags), "steps": [step],
            "expected": {"status": 302, "business_fields": dict(business), "db_state_before": {}, "db_state": db_state}}


def test_compare_checks_the_transcript_level_summary():
    e = _t({"outcome": "BOOKED", "seatsAvailable": 13}, {})
    a = _t({"outcome": "BOOKED", "seatsAvailable": 13}, {})
    assert compare(e, a, "legacy") == compare(e, a, "modern") == []
    e["expected"]["business_fields"]["seatsAvailable"] = 999
    e["expected"]["status"] = 200
    assert compare(e, a, "legacy") == [("status", 200, 302), ("business.seatsAvailable", 999, 13)]
    assert compare(e, a, "modern") == [("business.seatsAvailable", 999, 13)]


def test_compare_legacy_is_exact_and_modern_uses_legacy_code():
    e = _t({"outcome": "ERROR", "error": "INVALID_CAPACITY", "legacyCode": 50005}, {"x": {"bookedAt": "<now>"}})
    a = _t({"outcome": "ERROR", "error": "CAPACITY_BELOW_FLOOR", "legacyCode": 50005}, {"x": {"bookedAt": "<now>"}})
    assert compare(e, a, "legacy")
    assert compare(e, a, "modern") == []
    assert modern_business({"error": "X", "legacyCode": None}) == {"error": "X", "legacyCode": None}
    a2 = _t({"outcome": "ERROR", "error": "INVALID_CAPACITY", "legacyCode": 50005}, {"x": {"bookedAt": "2026-01-01"}})
    assert compare(e, a2, "modern") == []
    assert compare(e, a2, "legacy") == [("db_state.x.bookedAt", "<now>", "2026-01-01")]


def test_modern_compare_ignores_legacy_audit_log_only():
    e = _t({"outcome": "BOOKED"}, {"auditLog.new": [{"action": "BOOK"}], "examSession.1": {"SeatsAvailable": 3}})
    a = _t({"outcome": "BOOKED"}, {"auditLog.new": [], "examSession.1": {"SeatsAvailable": 3}})
    assert compare(e, a, "modern") == []
    assert compare(e, a, "legacy")
    a["expected"]["db_state"]["examSession.1"]["SeatsAvailable"] = 2
    assert compare(e, a, "modern") == [("db_state.examSession.1.SeatsAvailable", 3, 2)]


def test_report_groups_and_lists_mismatches_with_sha():
    ok = {"transcript": _t({}, {}), "status": "MATCH", "diffs": []}
    t2 = _t({}, {}, group="retake_eligibility", channel="PARTNER")
    t2["scenario"] = "s2"
    bad = {"transcript": t2, "status": "MISMATCH", "diffs": [("step1.business.eligible", True, False)],
           "actual": t2}
    text, matched, total = render([ok, bad], "legacy", None, "abc", "target=legacy")
    assert (matched, total) == (1, 2)
    assert "| booking | 1/1 MATCH |" in text and "| retake_eligibility_partner | 0/1 MATCH |" in text
    assert LEGACY_BASELINE_SHA in text and "step1.business.eligible" in text
    assert report_group(_t({}, {}, group="capacity", tags=["increase"])) == "capacity_increase"


def test_every_checklist_test_is_covered_or_declared_not_applicable():
    from chartool.catalog import CHECKLIST, coverage, not_applicable
    na = not_applicable((CHAR / "README.md").read_text(encoding="utf-8"))
    cov = coverage(load_all(CHAR / "scenarios"))
    missing = [ct for ct in CHECKLIST if not cov[ct] and ct not in na]
    assert not missing, missing
    assert not (na & {ct for ct in CHECKLIST if cov[ct]}), "covered CT also declared N/A"


def test_every_transcript_links_a_finding_and_catalog_is_current():
    from chartool.catalog import render
    scenarios = load_all(CHAR / "scenarios")
    for s in scenarios:
        t = json.loads((CHAR / "transcripts" / s["group"] / ("%s.json" % s["name"])).read_text(encoding="utf-8"))
        assert t["finding"] == s["finding"] and t["finding"], s["name"]
    expected = render(scenarios, (CHAR / "README.md").read_text(encoding="utf-8"))
    assert (CHAR / "CATALOG.md").read_text(encoding="utf-8") == expected, "run: python -m chartool.catalog"


def test_known_defects_reference_real_transcripts():
    from chartool.cli import load_known_defects
    names = {s["name"] for s in load_all(CHAR / "scenarios")}
    known = load_known_defects()
    assert known and set(known) <= names
    assert all("#57" in d["reason"] and d["signature"] for d in known.values())


def test_known_defect_is_counted_separately_not_as_match():
    from chartool.cli import classify_modern_mismatch
    from chartool.report import render as render_report
    t = {"scenario": "s1", "group": "capacity", "channel": "ADMIN", "tags": ["decrease"], "steps": [{"request": {}}]}
    actual = {"steps": [{"expected": {"business_fields": {"error": "CAPACITY_BELOW_ACCOMMODATION_FLOOR"}}}]}
    status, note = classify_modern_mismatch(t, actual, {"s1": "on-call defect #57"})
    assert (status, note) == ("KNOWN-DEFECT", "on-call defect #57")
    assert classify_modern_mismatch(t, actual, {})[0] == "MISMATCH"
    entry = {"s1": {"reason": "on-call defect #57", "signature": {"error": "CAPACITY_BELOW_ACCOMMODATION_FLOOR"}}}
    assert classify_modern_mismatch(t, actual, entry)[0] == "KNOWN-DEFECT"
    other = {"steps": [{"expected": {"business_fields": {"error": None, "outcome": "UPDATED"}}}]}
    assert classify_modern_mismatch(t, other, entry)[0] == "MISMATCH"
    routed = {"steps": [{"expected": {"business_fields": {"error": "CENTER_NOT_MODERN"}}}]}
    assert classify_modern_mismatch(t, routed, {})[0] == "SKIPPED"
    text, matched, total = render_report([{"transcript": t, "status": status, "note": note, "diffs": [("x", 1, 2)]}],
                                         "modern", "http://localhost:8090", "abc", "opts")
    assert (matched, total) == (0, 1)
    assert "KNOWN_ONCALL_DEFECT (#57) 1" in text and "| 0/1 MATCH | 0 | 1 | 0 | 0 | 0 | 0 |" in text
    assert "on-call defect #57" in text


def test_adr_divergence_only_when_the_diff_stays_inside_the_recorded_fields():
    from chartool.cli import classify_modern_mismatch
    from chartool.report import render as render_report
    t = {"scenario": "s2", "group": "booking", "channel": "PARTNER", "tags": [], "steps": [{"request": {}}]}
    actual = {"steps": [{"expected": {"business_fields": {"outcome": "BOOKED"}}}]}
    adr = {"s2": {"adr": "section 1", "reason": "no partner double decrement", "fields": ["steps[0].business.seats",
                                                                                        "db_state.examSession."]}}
    inside = [("steps[0].business.seatsAvailable", 7, 8), ("db_state.examSession.152.SeatsAvailable", 7, 8)]
    status, note = classify_modern_mismatch(t, actual, {}, adr, inside)
    assert status == "ADR-DIVERGENCE" and "ADR 0002 section 1" in note
    outside = inside + [("steps[0].business.outcome", "BOOKED", "WAITLISTED")]
    assert classify_modern_mismatch(t, actual, {}, adr, outside)[0] == "MISMATCH"
    assert classify_modern_mismatch(t, actual, {}, {}, inside)[0] == "MISMATCH"
    text, matched, total = render_report([{"transcript": t, "status": status, "note": note, "diffs": inside}],
                                         "modern", "http://localhost:8000", "abc", "opts")
    assert (matched, total) == (0, 1) and "ADR-APPROVED DIVERGENCE 1" in text and "| 0/1 MATCH | 1 | 0 | 0 | 0 | 0 | 0 |" in text


def test_classification_file_references_real_transcripts_and_states_reasons():
    from chartool.cli import load_classification, load_known_defects
    names = {s["name"] for s in load_all(CHAR / "scenarios")}
    adr, retired = load_classification()
    assert set(adr) <= names and set(retired) <= names
    assert not (set(adr) & set(retired)) and not (set(adr) & set(load_known_defects()))
    assert all(d["adr"] and d["reason"] for d in adr.values())
    assert all(d["reason"] and d["covered_by"] for d in retired.values())


def test_legacy_tree_override(tmp_path):
    from chartool import db
    from chartool.config import Config
    assert db.legacy_tree(Config(legacy_root=str(tmp_path))) == tmp_path
