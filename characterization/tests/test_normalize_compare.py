import sys
from datetime import datetime, timezone
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


def test_runtime_timestamps_from_another_clock_are_normalized():
    # MBA-3825: the modern service stamps bookedAt/cancelledAt with its JVM clock; the scenario start is the DB clock
    # truncated to the second, so the service value can land just before it or arrive with an offset
    n = Normalizer(BASE, "2026-11-11T10:00:01")
    local = datetime(2026, 11, 11, 10, 0, 5).astimezone()
    utc = local.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    body = {"bookedAt": "2026-11-11T10:00:00.998", "cancelledAt": "2026-11-11 09:59:58.4", "lastModified": utc,
            "addedAt": local.isoformat(), "BookedAt": "2026-11-11T09:58:00", "CancelledAt": "2026-11-09T09:00:00",
            "StartsAt": "2026-11-11T10:00:00", "cancelReason": "2026-11-11T10:00:00"}
    assert n.apply(body) == {"bookedAt": "<now>", "cancelledAt": "<now>", "lastModified": "<now>", "addedAt": "<now>",
                             "BookedAt": "2026-11-11T09:58:00", "CancelledAt": "2026-11-09T09:00:00",
                             "StartsAt": "2026-11-11T10:00:00", "cancelReason": "2026-11-11T10:00:00"}
    assert n.apply({"bookedAt": "not a date", "cancelledAt": None}) == {"bookedAt": "not a date", "cancelledAt": None}


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


def test_compare_checks_setup_responses_in_both_modes():
    g = {"setup": [{"name": "book", "status": 200}], "steps": []}
    a = {"setup": [{"name": "book", "status": 409}], "steps": []}
    for include_db in (True, False):
        assert compare_transcripts(g, a, include_db=include_db) == ["$.setup[0].status: 200 -> 409"]


def test_apply_seed_refuses_a_database_db_apply_does_not_build():
    import types
    import pytest
    from harness.db import Db, SqlError
    db = Db.__new__(Db)
    db.t = types.SimpleNamespace(database="ParityTest")
    with pytest.raises(SqlError, match="only rebuilds AssessmentDelivery"):
        db.apply_seed()


def test_missing_sqlcmd_is_a_sql_error():
    import types
    import pytest
    from harness.db import Db, SqlError
    db = Db(types.SimpleNamespace(sqlcmd="no-such-sqlcmd-binary", sql_server="localhost", sql_user="sa",
                                  sql_password="x", database="AssessmentDelivery"))
    with pytest.raises(SqlError, match="cannot run sqlcmd"):
        db.run("SELECT 1")


def test_apply_seed_accepts_assessmentdelivery_in_any_case(monkeypatch):
    import subprocess
    import types
    from harness import db as dbmod
    calls = []
    monkeypatch.setattr(dbmod.subprocess, "run",
                        lambda cmd, **kw: calls.append(cmd) or subprocess.CompletedProcess(cmd, 0, "", ""))
    db = dbmod.Db(types.SimpleNamespace(sqlcmd="sqlcmd", sql_server="localhost", sql_user="sa", sql_password="x",
                                        database="assessmentdelivery"))
    db.apply_seed()
    assert len(calls) == 1


def test_wait_settled_polls_until_nothing_is_pending():
    from harness.web import wait_settled
    answers = iter([ConnectionError("restarting"), {"pending": 3}, {"pending": "0", "total": 9}])
    sleeps = []
    assert wait_settled(lambda: next(answers), sleep=sleeps.append) == {"pending": "0", "total": 9}
    assert len(sleeps) == 2


def test_wait_settled_gives_up_after_the_timeout():
    import pytest
    from harness.web import wait_settled
    ticks = iter([0.0, 0.5, 1.5])
    with pytest.raises(RuntimeError, match="did not settle"):
        wait_settled(lambda: {"pending": 1}, timeout=1.0, clock=lambda: next(ticks), sleep=lambda _: None)


def test_wait_settled_needs_a_pending_count_and_accepts_an_empty_outbox():
    from harness.web import wait_settled
    # an error body is not "settled"; the relay reports pending null (SUM over no rows) when the outbox is empty
    answers = iter([{"status": 500, "error": "Internal Server Error"}, {"total": 0, "pending": None}])
    sleeps = []
    assert wait_settled(lambda: next(answers), sleep=sleeps.append) == {"total": 0, "pending": None}
    assert len(sleeps) == 1


def test_offset_timestamps_are_compared_in_the_db_timezone():
    # DB at UTC-6 (scenario start 10:00:01 local = 16:00:01Z), whatever the harness machine's timezone is
    n = Normalizer(BASE, "2026-11-11T10:00:01", -360)
    assert n.apply({"bookedAt": "2026-11-11T16:00:03Z", "cancelledAt": "2026-11-11T10:00:02-06:00",
                    "lastModified": "2026-11-11T15:00:03Z", "BookedAt": "2026-11-11T10:00:05"}) == \
        {"bookedAt": "<now>", "cancelledAt": "<now>", "lastModified": "2026-11-11T15:00:03Z", "BookedAt": "<now>"}
