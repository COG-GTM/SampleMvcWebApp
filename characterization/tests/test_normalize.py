import datetime as dt

from chartool.normalize import (IdMap, deep_norm, legacy_code, norm_datetime, norm_path, portal_date,
                                portal_error_code, portal_reason_code)
from chartool.db import split_batches


def test_idmap_labels_setup_and_new_ids_in_creation_order():
    m = IdMap(seed_max=3587, setup_max=3589, ids=[3592, 3588, 1601, 3590, 3589])
    assert m.label(1601) == 1601
    assert m.label(3588) == "setup#1" and m.label(3589) == "setup#2"
    assert m.label(3590) == "new#1" and m.label(3592) == "new#2"
    assert m.label(50000001) == "new#3"


def test_idmap_modern_ids_get_the_same_labels_as_legacy_ids():
    legacy = IdMap(3587, 3587, [3588, 3589])
    modern = IdMap(3587, 3587, [50000001, 50000002])
    assert [legacy.label(3588), legacy.label(3589)] == [modern.label(50000001), modern.label(50000002)]


def test_idmap_text_and_path_replace_only_volatile_ids():
    m = IdMap(3587, 3587, [3588])
    assert m.text("Booking 3588 confirmed (session 462, seed 3586)") == "Booking new#1 confirmed (session 462, seed 3586)"
    assert norm_path("/Booking/Cancel/3588", m) == "/Booking/Cancel/new#1"
    assert norm_path("/Booking/Cancel/3586", m) == "/Booking/Cancel/3586"


def test_norm_datetime_marks_scenario_timestamps():
    since = dt.datetime(2026, 11, 11, 1, 0, 0)
    assert norm_datetime(dt.datetime(2026, 11, 11, 1, 0, 5), since) == "<now>"
    assert norm_datetime(dt.datetime(2026, 10, 11, 16, 47, 0, 123000), since) == "2026-10-11T16:47:00"
    assert norm_datetime(None, since) is None


def test_deep_norm_handles_ids_timestamps_and_nested_lists():
    since = dt.datetime(2026, 11, 11)
    m = IdMap(3587, 3587)
    out = deep_norm({"bookingId": 3588, "bookedAt": "2026-11-11T09:00:00.000+0000",
                     "bumpedBookingIds": [3589], "nested": [{"BookingId": "3590"}]}, m, since)
    assert out == {"bookingId": "new#1", "bookedAt": "<now>", "bumpedBookingIds": ["new#2"],
                   "nested": [{"BookingId": "new#3"}]}


def test_portal_texts_map_to_booking_api_vocabulary():
    assert portal_error_code("Session 219 is not open for booking.") == "SESSION_NOT_OPEN"
    assert portal_error_code("Candidate 900 already has an active booking on session 462.") == "DUPLICATE_BOOKING"
    assert portal_error_code("No accommodation seat available on session 462.") == "NO_ACCOMMODATION_SEAT"
    assert portal_error_code("Invalid capacity 1 for session 198.") == "INVALID_CAPACITY"
    assert portal_error_code("Booking 3586 cannot be moved to session 191.") == "DIFFERENT_EXAM"
    assert portal_error_code("something else") == "PORTAL_ERROR"
    assert [legacy_code(c) for c in ("SESSION_NOT_OPEN", "DUPLICATE_BOOKING", "NO_ACCOMMODATION_SEAT",
                                     "INVALID_CAPACITY", "DIFFERENT_EXAM")] == [50001, 50002, 50003, 50005, 50006]
    assert portal_reason_code("No previous attempts.") == "FIRST_ATTEMPT"
    assert portal_date("29 Nov 2026") == "2026-11-29"
    assert portal_date("n/a") is None


def test_split_batches_uses_sqlcmd_go_semantics():
    assert split_batches("SELECT 1\nGO\n\nSELECT 2\ngo -- done\n") == ["SELECT 1", "SELECT 2"]
