"""Normalization of volatile values and the channel-independent business vocabulary."""
import datetime as _dt
import html
import re

NOW = "<now>"

# usp_* RAISERROR states (50000 + state) and the booking-api v1 error codes for the same condition.
LEGACY_CODES = {
    "SESSION_NOT_OPEN": 50001,
    "DUPLICATE_BOOKING": 50002,
    "NO_ACCOMMODATION_SEAT": 50003,
    "NOT_CANCELLABLE": 50004,
    "INVALID_CAPACITY": 50005,
    "DIFFERENT_EXAM": 50006,
}

# Portal error texts (usp_* RAISERROR messages surfaced by SqlErrorHelper / PortalException).
PORTAL_ERRORS = [
    (re.compile(r"is not open for booking", re.I), "SESSION_NOT_OPEN"),
    (re.compile(r"already has an active booking", re.I), "DUPLICATE_BOOKING"),
    (re.compile(r"No accommodation seat available", re.I), "NO_ACCOMMODATION_SEAT"),
    (re.compile(r"cannot be cancelled", re.I), "NOT_CANCELLABLE"),
    (re.compile(r"Invalid capacity", re.I), "INVALID_CAPACITY"),
    (re.compile(r"cannot be moved to session", re.I), "DIFFERENT_EXAM"),
    (re.compile(r"Not eligible", re.I), "NOT_ELIGIBLE"),
    (re.compile(r"Candidate number .* was not found", re.I), "CANDIDATE_NOT_FOUND"),
    (re.compile(r"Session .* was not found|Session .* not found", re.I), "SESSION_NOT_FOUND"),
    (re.compile(r"Booking .* was not found|Booking .* not found", re.I), "BOOKING_NOT_FOUND"),
    (re.compile(r"same session", re.I), "SAME_SESSION"),
]

# Portal RetakeRules texts -> booking-api RetakeEligibilityService reason codes.
PORTAL_REASONS = [
    (re.compile(r"^No previous attempts", re.I), "FIRST_ATTEMPT"),
    (re.compile(r"already passed", re.I), "ALREADY_PASSED"),
    (re.compile(r"wait period applies", re.I), "RETAKE_WAIT_NOT_ELAPSED"),
    (re.compile(r"wait period has elapsed", re.I), "RETAKE_ALLOWED"),
]


def portal_error_code(message):
    for pattern, code in PORTAL_ERRORS:
        if pattern.search(message or ""):
            return code
    return "PORTAL_ERROR"


def portal_reason_code(text):
    for pattern, code in PORTAL_REASONS:
        if pattern.search(text or ""):
            return code
    return text


def legacy_code(error):
    return LEGACY_CODES.get(error)


def portal_date(text):
    """'29 Nov 2026' -> '2026-11-29'; 'n/a' or blank -> None."""
    text = (text or "").strip()
    if not text or text.lower() == "n/a":
        return None
    return _dt.datetime.strptime(text, "%d %b %Y").date().isoformat()


def clean_html_text(fragment):
    text = re.sub(r"<[^>]+>", " ", fragment or "")
    return re.sub(r"\s+", " ", html.unescape(text)).strip()


def norm_datetime(value, since):
    """Timestamps written during the scenario (>= since) become <now>; seed literals stay (to the second)."""
    if value is None:
        return None
    if isinstance(value, _dt.datetime):
        if since is not None and value >= since:
            return NOW
        return value.replace(microsecond=0).isoformat()
    if isinstance(value, _dt.date):
        return value.isoformat()
    return value


class IdMap:
    """Maps volatile identity values to stable labels.

    ids <= seed_max (from the deterministic seed) are kept; ids created by scenario setup SQL become
    setup#k and ids created by the replayed requests become new#k, numbered in ascending id order
    (= creation order on legacy and on scheduling-service, whose ids start at 50,000,001).
    """

    def __init__(self, seed_max, setup_max, ids=()):
        self.seed_max = seed_max
        self.setup_max = setup_max
        self.labels = {}
        self.add(ids)

    def add(self, ids):
        known = set(self.labels)
        for i in sorted(set(int(x) for x in ids if x is not None) - known):
            if i <= self.seed_max:
                self.labels[i] = i
            elif i <= self.setup_max:
                self.labels[i] = "setup#%d" % (1 + sum(1 for v in self.labels.values() if str(v).startswith("setup#")))
            else:
                self.labels[i] = "new#%d" % (1 + sum(1 for v in self.labels.values() if str(v).startswith("new#")))

    def label(self, value):
        if value is None:
            return None
        try:
            i = int(value)
        except (TypeError, ValueError):
            return value
        if i not in self.labels:
            self.add([i])
        return self.labels[i]

    def text(self, value):
        """Replace any known volatile id that appears as a number token inside free text."""
        if not isinstance(value, str):
            return value

        def sub(m):
            i = int(m.group(0))
            lab = self.labels.get(i)
            return str(lab) if lab is not None and i > self.seed_max else m.group(0)
        return re.sub(r"(?<![\d.])\d{4,9}(?![\d.])", sub, value)


def norm_path(value, idmap):
    if not isinstance(value, str):
        return value
    return re.sub(r"(?<=[/=])(\d{4,9})(?=$|[/?&])", lambda m: str(idmap.label(int(m.group(1))))
                  if int(m.group(1)) > idmap.seed_max else m.group(1), value)


def deep_norm(obj, idmap, since, id_keys=("bookingId", "BookingId", "legacyBookingId")):
    """Recursively normalize a JSON-like value: ids under id_keys, timestamps, free text."""
    if isinstance(obj, dict):
        out = {}
        for k, v in obj.items():
            if k in id_keys and isinstance(v, (int, str)) and str(v).isdigit():
                out[k] = idmap.label(int(v))
            elif k == "bumpedBookingIds" and isinstance(v, list):
                out[k] = [idmap.label(x) for x in v]
            else:
                out[k] = deep_norm(v, idmap, since, id_keys)
        return out
    if isinstance(obj, list):
        return [deep_norm(v, idmap, since, id_keys) for v in obj]
    if isinstance(obj, (_dt.datetime, _dt.date)):
        return norm_datetime(obj, since)
    if isinstance(obj, str):
        if since is not None and re.match(r"^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d", obj):
            try:
                parsed = _dt.datetime.fromisoformat(obj[:19])
                if parsed >= since:
                    return NOW
            except ValueError:
                pass
        return idmap.text(obj)
    return obj
