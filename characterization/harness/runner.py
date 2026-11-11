"""Loads the scenario catalogue, runs scenarios from a known DB state and builds normalized transcripts."""
import copy
import json
import re

import yaml

from . import mixed, snapshot
from .config import SCENARIO_DIR, TRANSCRIPT_DIR
from .db import Db, log
from .normalize import Normalizer
from .steps import Steps
from .web import wait_until_up

STEP_TYPES = {"portal_book", "portal_cancel", "portal_reschedule", "portal_capacity", "portal_eligibility",
              "api_book", "api_cancel", "api_reschedule", "api_eligibility", "api_get", "capacity_sync",
              "seat_drift", "db_proc", "db_query", "sql", "mixed_traffic"}
VAR_RE = re.compile(r"\$\{(\w+)\}")
REQUIRED = ("id", "title", "area", "channel", "steps")


def load_catalogue(directory=SCENARIO_DIR):
    scenarios = []
    for path in sorted(directory.glob("*.yaml")):
        doc = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        for sc in doc.get("scenarios", []):
            sc = dict(sc)
            sc["_file"] = path.name
            scenarios.append(sc)
    return scenarios


def validate(sc):
    problems = [f"missing {k}" for k in REQUIRED if k not in sc]
    for phase in ("setup", "steps"):
        for st in sc.get(phase, []) or []:
            kinds = [k for k in st if k in STEP_TYPES]
            if len(kinds) != 1:
                problems.append(f"{phase} step must have exactly one of {sorted(STEP_TYPES)}: {st}")
    if sc.get("oddity") and not sc.get("oddity_ids"):
        problems.append("oddity: true needs oddity_ids")
    return problems


class Runner:
    def __init__(self, target):
        self.t = target
        # Needed even with --compare http: setup SQL, db_proc/db_query steps and mixed-traffic session
        # selection run against --sql-server whatever is compared.
        self.db = Db(target)
        self.steps = Steps(target, self.db)
        self.backup = None

    # ---- reseeding ----------------------------------------------------------------------------
    def prepare(self):
        if self.t.reseed == "restore":
            log("rebuilding AssessmentDelivery with db/apply (deterministic seed), then taking a backup")
            self.db.apply_seed()
            self.backup = self.db.backup()

    def reseed(self):
        mode = self.t.reseed
        if mode == "restore":
            self.db.restore(self.backup)
        elif mode == "apply":
            self.db.apply_seed()
        elif mode == "command":
            self.db_or_none_cmd(self.t.reseed_cmd)
        self.warm_up()

    def db_or_none_cmd(self, cmd):
        self.db.run_command(cmd)

    def warm_up(self):
        # A restore kills pooled connections in the portal (ADO.NET) and the Java services; burn the
        # stale ones with harmless reads before the scenario's first real request.
        for _ in range(4):
            wait_until_up(f"{self.t.api_url}/api/v1/sessions/1")
            wait_until_up(f"{self.t.portal_url}/Booking/MyBookings?candidateNumber=NW00000001")

    # ---- execution ----------------------------------------------------------------------------
    def _subst(self, value, vars_):
        if isinstance(value, str):
            full = VAR_RE.fullmatch(value)
            if full:
                return vars_[full.group(1)]
            return VAR_RE.sub(lambda m: str(vars_[m.group(1)]), value)
        if isinstance(value, dict):
            return {k: self._subst(v, vars_) for k, v in value.items()}
        if isinstance(value, list):
            return [self._subst(v, vars_) for v in value]
        return value

    def _exec(self, st, vars_, sessions, candidates):
        kind = next(k for k in st if k in STEP_TYPES)
        params = self._subst(copy.deepcopy(st[kind]) or {}, vars_)
        if kind == "mixed_traffic":
            res = mixed.run(self.steps, self.t.seed_as_of, **params)
            sessions.update(res.pop("sessions"))
            candidates.update(res.pop("candidates"))
        elif kind in ("sql", "db_proc", "db_query"):
            res = getattr(self.steps, kind)(params if isinstance(params, str) else params["sql"])
        else:
            res = getattr(self.steps, kind)(**params)
        if st.get("save_as"):
            if res.get("saved") is None:
                raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
            vars_[st["save_as"]] = res["saved"]
        return kind, res

    def run(self, sc):
        self.reseed()
        use_db = self.t.compare == "all"
        base = snapshot.baseline(self.db) if use_db else {"now": None}
        norm = Normalizer(base, base.get("now"))
        sessions = set(sc.get("sessions", []))
        candidates = set(sc.get("candidates", []))
        vars_ = {}
        setup_log = []
        for st in sc.get("setup", []) or []:
            kind, res = self._exec(st, vars_, sessions, candidates)
            if res.get("saved") is not None:
                norm.register("booking", res["saved"])
            setup_log.append({"name": st.get("name", kind), "request": res["request"],
                              "status": res["response"].get("status") if isinstance(res["response"], dict) else None})
        before = snapshot.take(self.db, sessions, candidates, base["audit"]) if use_db else None
        steps_out = []
        for st in sc["steps"]:
            kind, res = self._exec(st, vars_, sessions, candidates)
            entry = {"name": st.get("name", kind), "request": res["request"], "response": res["response"]}
            if use_db and sessions:
                entry["sessions_after"] = snapshot.session_counters(self.db, sessions)
            steps_out.append(entry)
        after = snapshot.take(self.db, sessions, candidates, base["audit"]) if use_db else None
        transcript = {k: sc.get(k) for k in ("id", "title", "area", "channel", "hb", "oddity", "oddity_ids", "notes")}
        transcript["seed_as_of"] = self.t.seed_as_of
        transcript["setup"] = norm.apply(setup_log)
        if use_db:
            transcript["before"] = snapshot.normalize(before, norm)
        transcript["steps"] = norm.apply(steps_out)
        if use_db:
            transcript["after"] = snapshot.normalize(after, norm)
        return transcript


def transcript_path(sid):
    return TRANSCRIPT_DIR / f"{sid}.json"


def write_transcript(path, transcript):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(transcript, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")


def read_transcript(sid):
    return json.loads(transcript_path(sid).read_text(encoding="utf-8"))
