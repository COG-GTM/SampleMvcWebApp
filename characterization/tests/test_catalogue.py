"""Offline checks of the scenario catalogue, the committed transcripts and ODDITIES.md (no estate needed)."""
import json
import re

import pytest

from harness.config import CHAR_DIR, TRANSCRIPT_DIR
from harness.runner import load_catalogue, read_transcript, transcript_path, validate

CATALOGUE = load_catalogue()
IDS = [s["id"] for s in CATALOGUE]
ODDITIES = (CHAR_DIR / "ODDITIES.md").read_text(encoding="utf-8")
ODD_ROWS = dict(re.findall(r"^\| (ODD-\d\d) \| [^|]+ \| [^|]+ \| ([^|]+) \|$", ODDITIES, re.M))
HB_DOC = (CHAR_DIR.parent / "docs" / "as-is" / "hidden-behaviours.md").read_text(encoding="utf-8")


def test_catalogue_is_valid_and_large_enough():
    assert {s["id"]: validate(s) for s in CATALOGUE if validate(s)} == {}
    assert len(IDS) >= 40
    assert len(set(IDS)) == len(IDS)


@pytest.mark.parametrize("area", ["booking", "cancel", "reschedule", "capacity", "retake", "accommodation", "mixed-traffic"])
def test_every_acceptance_area_is_covered(area):
    assert any(s["area"] == area for s in CATALOGUE)


@pytest.mark.parametrize("area", ["booking", "cancel", "reschedule", "accommodation"])
def test_both_channels_covered_per_area(area):
    channels = {s["channel"] for s in CATALOGUE if s["area"] == area}
    assert {"portal", "api"} <= channels


def test_every_scenario_has_a_committed_transcript():
    missing = [i for i in IDS if not transcript_path(i).exists()]
    assert missing == []
    orphans = sorted(p.stem for p in TRANSCRIPT_DIR.glob("*.json") if p.stem not in IDS)
    assert orphans == []


@pytest.mark.parametrize("sid", IDS)
def test_transcript_shape_and_normalization(sid):
    t = read_transcript(sid)
    assert t["id"] == sid
    assert t["seed_as_of"] == "2026-11-10"
    assert {"before", "after", "steps"} <= set(t)
    for snap in (t["before"], t["after"]):
        assert {"sessions", "bookings", "waitlist", "results", "audit"} <= set(snap)
    for step in t["steps"]:
        assert "request" in step and "response" in step
    text = json.dumps(t)
    # no raw wall-clock timestamps from the capture run survive normalization
    assert not re.search(r'"(BookedAt|LastModified|LoggedAt|AddedAt|startedAt|finishedAt)": "20(2[7-9]|[3-9]\d)', text)
    assert not re.search(r'"LoggedAt": "\d{4}-', text)


@pytest.mark.parametrize("sid", IDS)
def test_transcript_metadata_matches_catalogue(sid):
    scenario = next(s for s in CATALOGUE if s["id"] == sid)
    meta = read_transcript(sid)
    assert bool(meta.get("oddity")) == bool(scenario.get("oddity"))
    assert (meta.get("oddity_ids") or []) == (scenario.get("oddity_ids") or [])


def test_every_oddity_scenario_is_documented_and_cross_referenced():
    for s in CATALOGUE:
        if not s.get("oddity"):
            continue
        assert s.get("oddity_ids"), s["id"]
        for odd in s["oddity_ids"]:
            assert odd in ODD_ROWS, f"{s['id']} -> {odd} missing from ODDITIES.md"
            assert s["id"][:5] in ODD_ROWS[odd], f"{odd} row does not cite {s['id'][:5]}"


def test_every_documented_oddity_is_used_and_cites_existing_scenarios():
    used = {o for s in CATALOGUE for o in s.get("oddity_ids", [])}
    assert set(ODD_ROWS) == used
    prefixes = {i[:5] for i in IDS}
    for odd, cited in ODD_ROWS.items():
        for ref in re.findall(r"`([A-Z]{2}-\d\d)`", cited):
            assert ref in prefixes, f"{odd} cites unknown scenario {ref}"


def test_hb_references_exist_in_docs():
    for s in CATALOGUE:
        for hb in s.get("hb", []):
            assert f"| {hb} |" in HB_DOC, f"{s['id']} cites unknown {hb}"


def test_ticket_items_each_have_both_channels_where_applicable():
    channels = {}
    for s in CATALOGUE:
        for o in s.get("oddity_ids", []):
            channels.setdefault(o, set()).add(s["channel"])
    for odd in ("ODD-01", "ODD-04", "ODD-05", "ODD-07", "ODD-08"):
        assert {"portal", "api"} <= channels[odd] | ({"portal", "api"} if "both" in channels[odd] else set()), odd
    assert {"portal", "db"} <= channels["ODD-02"]
    assert {"portal", "db"} <= channels["ODD-06"]
    assert "sync" in channels["ODD-03"]
