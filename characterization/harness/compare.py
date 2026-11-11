"""Structural diff of a golden transcript against a fresh replay."""

COMPARED_KEYS = ("setup", "before", "steps", "after")


def diff(expected, actual, path="$", out=None, limit=50):
    out = [] if out is None else out
    if len(out) >= limit:
        return out
    if isinstance(expected, dict) and isinstance(actual, dict):
        for k in sorted(set(expected) | set(actual)):
            if k not in actual:
                out.append(f"{path}.{k}: missing in replay (expected {short(expected[k])})")
            elif k not in expected:
                out.append(f"{path}.{k}: unexpected in replay ({short(actual[k])})")
            else:
                diff(expected[k], actual[k], f"{path}.{k}", out, limit)
    elif isinstance(expected, list) and isinstance(actual, list):
        if len(expected) != len(actual):
            out.append(f"{path}: length {len(expected)} -> {len(actual)}")
        for i, (e, a) in enumerate(zip(expected, actual)):
            diff(e, a, f"{path}[{i}]", out, limit)
    elif expected != actual:
        out.append(f"{path}: {short(expected)} -> {short(actual)}")
    return out


def compare_transcripts(golden, replay, include_db=True):
    keys = COMPARED_KEYS if include_db else ("setup", "steps")
    differences = []
    for key in keys:
        g, r = golden.get(key), replay.get(key)
        if not include_db and key == "steps":
            g = [_http_only(s) for s in g or []]
            r = [_http_only(s) for s in r or []]
        differences += diff(g, r, f"$.{key}")
    return differences


def _http_only(step):
    return {k: v for k, v in step.items() if k in ("name", "request", "response")}


def short(value, width=120):
    s = repr(value)
    return s if len(s) <= width else s[: width - 3] + "..."
