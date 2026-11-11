"""Normalization of volatile values so transcripts compare across runs and targets.

* Identity ids created during a scenario (BookingId, WaitlistId, AuditId, ResultId above the
  pre-scenario maximum) become stable placeholders such as ``<booking:1>`` in order of first
  appearance. Seed ids (<= baseline) are kept because the deterministic seed fixes them.
* Run-time timestamps (GETDATE()/now() columns such as BookedAt, CancelledAt, LastModified,
  LoggedAt, AddedAt, startedAt) that are not older than the scenario start become ``<now>``.
  Seed timestamps and domain dates (StartsAt, earliestRetakeDate, ...) are never touched.
"""
import re

ID_KINDS = {
    "bookingid": "booking", "newbookingid": "booking", "oldbookingid": "booking", "originalbookingid": "booking",
    "waitlistid": "waitlist", "auditid": "audit", "resultid": "result",
}
VOLATILE_TS = {"bookedat", "cancelledat", "lastmodified", "loggedat", "addedat", "receivedat", "startedat",
               "finishedat", "createdat", "updatedat", "timestamp", "lastrunat", "ranat"}
TS_RE = re.compile(r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}")
NUM_IN_TEXT_RE = re.compile(r"(?<![\w.-])(\d{3,})(?![\w.-])")


class Normalizer:
    def __init__(self, baseline, scenario_start):
        """baseline: {"booking": max id, ...}; scenario_start: 'YYYY-MM-DDTHH:MM:SS' from the DB clock."""
        self.baseline = baseline
        self.start = scenario_start.replace(" ", "T")[:19] if scenario_start else None
        self.ids = {}  # (kind, raw id) -> placeholder
        self.counters = {}

    def placeholder(self, kind, value):
        try:
            raw = int(value)
        except (TypeError, ValueError):
            return value
        if raw <= self.baseline.get(kind, 0):
            return raw
        key = (kind, raw)
        if key not in self.ids:
            self.counters[kind] = self.counters.get(kind, 0) + 1
            self.ids[key] = f"<{kind}:{self.counters[kind]}>"
        return self.ids[key]

    def register(self, kind, value):
        """Reserve a placeholder (e.g. for an id captured with save_as) without changing it."""
        return self.placeholder(kind, value)

    def timestamp(self, value):
        if not isinstance(value, str) or not TS_RE.match(value) or not self.start:
            return value
        return "<now>" if value.replace(" ", "T")[:19] >= self.start else value

    def text(self, value):
        """Replace raw new booking ids that appear inside free text (portal alerts, API messages)."""
        def sub(m):
            raw = int(m.group(1))
            ph = self.ids.get(("booking", raw))
            if ph is None and raw > self.baseline.get("booking", 0) and raw <= self.baseline.get("booking", 0) + 10000:
                ph = self.placeholder("booking", raw)
            return ph if isinstance(ph, str) else m.group(0)
        return NUM_IN_TEXT_RE.sub(sub, value)

    def apply(self, obj, key=None):
        k = (key or "").lower()
        if isinstance(obj, dict):
            return {kk: self.apply(v, kk) for kk, v in obj.items()}
        if isinstance(obj, list):
            return [self.apply(v, key) for v in obj]
        if k in ID_KINDS and isinstance(obj, (int, str)) and str(obj).isdigit():
            return self.placeholder(ID_KINDS[k], obj)
        if k == "entityid" and isinstance(obj, (int, str)) and str(obj).isdigit():
            return obj  # resolved by the snapshot layer, which knows the EntityName
        if k in VOLATILE_TS:
            return self.timestamp(obj)
        if isinstance(obj, str):
            return self.text(obj)
        return obj
