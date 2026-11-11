"""Transcript comparison (expected vs actual) for the legacy and modern replay targets."""

TIMESTAMP_KEYS = {"bookedAt", "cancelledAt", "addedAt"}
MODERN_IGNORED_BOOKING_KEYS = {"channel"}  # scheduling-service may store IMPORT for imported rows
# AuditLog rows are written by the legacy usp_* procs; scheduling-service audits capacity changes in
# capacity_change instead (ADR 0002 section 3), so they are not a channel-independent business field.
MODERN_IGNORED_STATE_PREFIXES = ("auditLog.",)


def _strip(obj, keys):
    if isinstance(obj, dict):
        return {k: _strip(v, keys) for k, v in obj.items() if k not in keys}
    if isinstance(obj, list):
        return [_strip(v, keys) for v in obj]
    return obj


def modern_business(biz):
    """Channel-independent fields only; when a usp_* legacyCode exists it is compared instead of the error text."""
    out = dict(biz)
    if out.get("legacyCode") is not None:
        out.pop("error", None)
    return out


def diff(expected, actual, path=""):
    """List of (path, expected, actual) for every difference."""
    out = []
    if isinstance(expected, dict) and isinstance(actual, dict):
        for k in sorted(set(expected) | set(actual), key=str):
            out += diff(expected.get(k, "<missing>"), actual.get(k, "<missing>"), "%s.%s" % (path, k) if path else str(k))
    elif isinstance(expected, list) and isinstance(actual, list) and len(expected) == len(actual):
        for i, (e, a) in enumerate(zip(expected, actual)):
            out += diff(e, a, "%s[%d]" % (path, i))
    elif expected != actual:
        out.append((path, expected, actual))
    return out


def compare(expected, actual, target):
    """Returns the list of differences; empty list means MATCH."""
    diffs = []
    if len(expected["steps"]) != len(actual["steps"]):
        return [("steps", len(expected["steps"]), len(actual["steps"]))]
    for i, (e, a) in enumerate(zip(expected["steps"], actual["steps"]), 1):
        prefix = "step%d" % i
        if target == "legacy":
            diffs += diff(e["expected"]["status"], a["expected"]["status"], prefix + ".status")
            diffs += diff(e["expected"]["response"], a["expected"]["response"], prefix + ".response")
            diffs += diff(e["expected"]["business_fields"], a["expected"]["business_fields"], prefix + ".business")
        else:
            diffs += diff(modern_business(e["expected"]["business_fields"]),
                          modern_business(a["expected"]["business_fields"]), prefix + ".business")
    # Transcript-level summary (copied from the last step at capture time): checked too, so editing it is not silent.
    top_e, top_a = expected["expected"], actual["expected"]
    if target == "legacy":
        diffs += diff(top_e.get("status"), top_a.get("status"), "status")
        diffs += diff(top_e.get("business_fields"), top_a.get("business_fields"), "business")
    else:
        diffs += diff(modern_business(top_e.get("business_fields") or {}),
                      modern_business(top_a.get("business_fields") or {}), "business")
    ignore = set() if target == "legacy" else TIMESTAMP_KEYS | MODERN_IGNORED_BOOKING_KEYS
    for key in ("db_state_before", "db_state"):
        exp, act = expected["expected"][key], actual["expected"][key]
        if target != "legacy":
            exp, act = ({k: v for k, v in d.items() if not k.startswith(MODERN_IGNORED_STATE_PREFIXES)} for d in (exp, act))
        diffs += diff(_strip(exp, ignore), _strip(act, ignore), key)
    return diffs
