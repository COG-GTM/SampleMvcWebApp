"""Functional tests against the running legacy estate: python -m pytest -m integration."""
import json
import subprocess
import sys
from pathlib import Path

import pytest

CHAR = Path(__file__).resolve().parent.parent
pytestmark = pytest.mark.integration

SUBSET = ["book_open_session_portal", "book_open_session_partner", "cancel_waitlisted_portal",
          "capacity_decrease_below_active_admin", "retake_calendar_boundary_portal",
          "retake_calendar_boundary_partner", "accommodation_capacity_exhausted_partner"]


def _cli(*args):
    return subprocess.run([sys.executable, "-m", "chartool.cli", *args], cwd=CHAR, capture_output=True,
                          text=True, encoding="utf-8", timeout=900)


def _paths(names):
    return {n: next((CHAR / "transcripts").glob("*/%s.json" % n)) for n in names}


def test_capture_reproduces_the_committed_transcripts_byte_for_byte():
    paths = _paths(SUBSET)
    before = {n: p.read_bytes() for n, p in paths.items()}
    try:
        r = _cli("capture", "--only", *SUBSET)
        assert r.returncode == 0, r.stdout + r.stderr
        for n, p in paths.items():
            assert p.read_bytes() == before[n], n
    finally:
        for n, p in paths.items():
            p.write_bytes(before[n])


def test_replay_legacy_matches_and_writes_a_report():
    r = _cli("replay", "--target", "legacy", "--only", *SUBSET, "--report", "replay-legacy-pytest.md")
    report = CHAR / "reports" / "replay-legacy-pytest.md"
    try:
        assert r.returncode == 0, r.stdout + r.stderr
        text = report.read_text(encoding="utf-8")
        assert "**Overall: %d/%d MATCH**" % (len(SUBSET), len(SUBSET)) in text
        for n in SUBSET:
            row = next(ln for ln in text.splitlines() if ln.startswith("| %s |" % n))
            assert "| MATCH |" in row
    finally:
        report.unlink(missing_ok=True)


def test_replay_detects_a_tampered_transcript():
    p = _paths(["book_open_session_partner"])["book_open_session_partner"]
    original = p.read_bytes()
    t = json.loads(original)
    t["steps"][0]["expected"]["business_fields"]["seatsAvailable"] = 14
    p.write_text(json.dumps(t, indent=2) + "\n", encoding="utf-8")
    try:
        r = _cli("replay", "--target", "legacy", "--only", "book_open_session_partner", "--report", "replay-legacy-tamper.md")
        assert r.returncode == 1
        text = (CHAR / "reports" / "replay-legacy-tamper.md").read_text(encoding="utf-8")
        assert "MISMATCH" in text and "seatsAvailable" in text and "e458238" in text
    finally:
        p.write_bytes(original)
        (CHAR / "reports" / "replay-legacy-tamper.md").unlink(missing_ok=True)
