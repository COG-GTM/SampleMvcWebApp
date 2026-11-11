"""run_scenario against a modern target whose DB state is the M4 relay's projection (no database needed)."""
import datetime as dt

from chartool.runner import Target, run_scenario

MODERN_ID, LEGACY_ID = 50000007, 3590


class FakeDb:
    def now(self):
        return dt.datetime(2026, 11, 10, 9, 0, 0)


class RelayedState:
    """AssessmentDelivery as the relay leaves it: the booking only appears once the outbox has been drained."""

    def __init__(self):
        self.relayed = False

    def max_ids(self):
        return {"Booking": 3589, "AuditLog": 0}

    def sessions_with_new_bookings(self, after_id):
        return [462] if self.relayed else []

    def read(self, sessions, candidates, audit_after):
        rows = [{"BookingId": LEGACY_ID, "SessionId": 462, "CandidateNumber": "NW00000901", "Status": "WAITLISTED",
                 "AccommodationCode": None, "Channel": "PORTAL", "ExternalRef": None, "BookedAt": None,
                 "CancelledAt": None, "CancelReason": None}] if self.relayed else []
        return {"sessions": {462: {"SessionId": 462, "Capacity": 1, "AccommodationCapacity": 0,
                                   "SeatsAvailable": 0, "Status": "OPEN"}},
                "bookings": rows, "waitlist": [{"SessionId": 462, "Position": 1, "BookingId": LEGACY_ID,
                                                "CandidateNumber": "NW00000901", "AddedAt": None}] if self.relayed else [],
                "results": [], "audit": []}


class ModernPortal:
    def book(self, session, candidate, accommodation, external_ref):
        return {"request": {"method": "POST", "path": "/api/v2/bookings"}, "response": {"status": 201},
                "business": {"outcome": "WAITLISTED", "bookingId": MODERN_ID, "error": None, "legacyCode": None},
                "booking_id": MODERN_ID}


def test_modern_steps_are_read_after_the_relay_drains_and_ids_map_to_legacy_ids():
    state = RelayedState()
    target = Target("modern", None, {"PORTAL": ModernPortal()}, state, lambda sc: None,
                    settle_fn=lambda: setattr(state, "relayed", True),
                    booking_id_fn=lambda i: LEGACY_ID if i == MODERN_ID else i)
    sc = {"name": "s", "group": "booking", "channel": "PORTAL", "sessions": [462], "candidates": [],
          "steps": [{"op": "book", "session": 462, "candidate": "NW00000901"}]}
    t = run_scenario(sc, target, FakeDb(), seed_max=3587)
    biz = t["expected"]["business_fields"]
    assert biz["bookingId"] == "new#1" and biz["waitlistPosition"] == 1
    assert [b["bookingId"] for b in t["expected"]["db_state"]["bookings.462"]] == ["new#1"]


def test_legacy_target_defaults_keep_response_ids():
    target = Target("legacy", type("C", (), {"results_url": None})(), {}, None, None)
    target.settle_fn()
    assert target.booking_id_fn(3590) == 3590
