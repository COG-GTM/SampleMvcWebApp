import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from harness.compare import compare_transcripts, diff  # noqa: E402
from harness.normalize import Normalizer  # noqa: E402
from harness.web import rendered_fields  # noqa: E402

BASE = {"booking": 3587, "waitlist": 40, "audit": 100, "result": 900}


def test_new_ids_become_placeholders_in_order_and_seed_ids_stay():
    n = Normalizer(BASE, "2026-11-11T10:00:00")
    out = n.apply({"bookingId": 3590, "rows": [{"BookingId": 3589}, {"BookingId": 12}, {"BookingId": 3590}]})
    assert out == {"bookingId": "<booking:1>", "rows": [{"BookingId": "<booking:2>"}, {"BookingId": 12}, {"BookingId": "<booking:1>"}]}


def test_ids_inside_text_are_replaced_consistently():
    n = Normalizer(BASE, "2026-11-11T10:00:00")
    n.register("booking", 3600)
    assert n.apply("Booking 3600 confirmed.") == "Booking <booking:1> confirmed."
    assert n.apply("/Booking/Cancel/3600") == "/Booking/Cancel/<booking:1>"
    assert n.apply("Session 154 is not open for booking.") == "Session 154 is not open for booking."


def test_only_runtime_timestamps_of_volatile_columns_are_normalized():
    n = Normalizer(BASE, "2026-11-11T10:00:00")
    row = {"BookedAt": "2026-11-11T10:00:03.120", "CancelledAt": "2026-11-09T09:00:00", "StartsAt": "2026-11-12T09:00",
           "LoggedAt": "2026-12-01T00:00:00", "earliestRetakeDate": "2026-12-18"}
    assert n.apply(row) == {"BookedAt": "<now>", "CancelledAt": "2026-11-09T09:00:00", "StartsAt": "2026-11-12T09:00",
                            "LoggedAt": "<now>", "earliestRetakeDate": "2026-12-18"}


def test_diff_reports_paths():
    assert diff({"a": [1, 2]}, {"a": [1, 3]}) == ["$.a[1]: 2 -> 3"]
    assert diff({"a": 1}, {}) == ["$.a: missing in replay (expected 1)"]


def test_compare_http_only_ignores_db_snapshots():
    g = {"before": {"x": 1}, "steps": [{"name": "s", "request": {}, "response": {"status": 201}, "sessions_after": [1]}], "after": {}}
    a = {"before": {"x": 2}, "steps": [{"name": "s", "request": {}, "response": {"status": 201}, "sessions_after": [2]}], "after": {}}
    assert compare_transcripts(g, a, include_db=False) == []
    assert len(compare_transcripts(g, a, include_db=True)) == 2


def test_rendered_fields_extracts_alerts_and_cells():
    page = ('<h1>Retake eligibility</h1><div class="alert alert-success nw-alert">Booking 5 confirmed.</div>'
            '<div class="validation-summary-errors alert alert-danger nw-alert"><ul><li>Session full</li></ul></div>'
            '<table><tr><th>Eligible</th><td>No</td></tr><tr><th>Reason</th><td>RETAKE_WAIT</td></tr></table>')
    f = rendered_fields(page)
    assert f["heading"] == "Retake eligibility"
    assert f["alerts"] == ["Booking 5 confirmed.", "Session full"]
    assert f["cells"] == {"Eligible": "No", "Reason": "RETAKE_WAIT"}
