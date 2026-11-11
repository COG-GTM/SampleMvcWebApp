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
            "expected": {"db_state_before": {}, "db_state": db_state}}


def test_compare_legacy_is_exact_and_modern_uses_legacy_code():
    e = _t({"outcome": "ERROR", "error": "INVALID_CAPACITY", "legacyCode": 50005}, {"x": {"bookedAt": "<now>"}})
    a = _t({"outcome": "ERROR", "error": "CAPACITY_BELOW_FLOOR", "legacyCode": 50005}, {"x": {"bookedAt": "<now>"}})
    assert compare(e, a, "legacy")
    assert compare(e, a, "modern") == []
    assert modern_business({"error": "X", "legacyCode": None}) == {"error": "X", "legacyCode": None}
    a2 = _t({"outcome": "ERROR", "error": "INVALID_CAPACITY", "legacyCode": 50005}, {"x": {"bookedAt": "2026-01-01"}})
    assert compare(e, a2, "modern") == []
    assert compare(e, a2, "legacy") == [("db_state.x.bookedAt", "<now>", "2026-01-01")]


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
