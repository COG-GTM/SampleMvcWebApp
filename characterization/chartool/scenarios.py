"""Scenario catalogue (characterization/scenarios/*.yaml) loading and validation."""
from pathlib import Path

import yaml

import re

OPS = {"book", "cancel", "reschedule", "capacity", "eligibility", "sync", "drift", "mixed_traffic",
       "sql", "sessions", "session", "search", "result"}
CHANNELS = {"PORTAL", "PARTNER", "ADMIN", "SYSTEM", "DB"}
FINDING_RE = re.compile(r"^(HB-\d\d|data-ownership)$")
TEST_RE = re.compile(r"^CT-\d\d$")


class ScenarioError(ValueError):
    pass


def validate(sc, source=""):
    where = "%s:%s" % (source, sc.get("name", "?"))
    for key in ("name", "group", "channel", "steps"):
        if key not in sc:
            raise ScenarioError("%s: missing '%s'" % (where, key))
    if sc["channel"] not in CHANNELS:
        raise ScenarioError("%s: bad channel %r" % (where, sc["channel"]))
    if not sc["steps"]:
        raise ScenarioError("%s: no steps" % where)
    for i, step in enumerate(sc["steps"], 1):
        if step.get("op") not in OPS:
            raise ScenarioError("%s: step %d has unknown op %r" % (where, i, step.get("op")))
    findings = sc.get("finding") or []
    if not findings or not all(FINDING_RE.match(f) for f in findings):
        raise ScenarioError("%s: 'finding' must list docs/as-is/hidden-behavior.md ids (HB-nn)" % where)
    if not all(TEST_RE.match(t) for t in sc.get("tests") or []):
        raise ScenarioError("%s: 'tests' must list docs/as-is/migration-risks.md ids (CT-nn)" % where)
    if sc.get("oddity") and not sc.get("oddity_note"):
        raise ScenarioError("%s: oddity without oddity_note" % where)
    return sc


def load_file(path):
    data = yaml.safe_load(Path(path).read_text(encoding="utf-8")) or {}
    group = data.get("group")
    out = []
    for sc in data.get("scenarios", []):
        sc = dict(sc)
        sc.setdefault("group", group)
        sc.setdefault("sessions", [])
        sc.setdefault("candidates", [])
        sc.setdefault("setup_sql", [])
        sc.setdefault("tags", [])
        sc.setdefault("oddity", False)
        out.append(validate(sc, Path(path).name))
    return out


def load_all(directory):
    scenarios, seen = [], set()
    for path in sorted(Path(directory).glob("*.yaml")):
        for sc in load_file(path):
            if sc["name"] in seen:
                raise ScenarioError("duplicate scenario name %s" % sc["name"])
            seen.add(sc["name"])
            scenarios.append(sc)
    return scenarios


def select(scenarios, patterns):
    if not patterns:
        return scenarios
    import fnmatch
    return [s for s in scenarios if any(fnmatch.fnmatch(s["name"], p) or s["group"] == p for p in patterns)]
