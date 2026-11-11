"""Target configuration: where the portal, booking-api, capacity-sync and database live."""
import os
import shutil
from dataclasses import dataclass, field
from pathlib import Path

CHAR_DIR = Path(__file__).resolve().parent.parent
REPO_DIR = CHAR_DIR.parent
SCENARIO_DIR = CHAR_DIR / "scenarios"
TRANSCRIPT_DIR = CHAR_DIR / "transcripts"
REPORT_DIR = CHAR_DIR / "reports"

# Seed clock (docs/as-is/hidden-behaviours.md HB-30): the seed is written as of 2026-11-10 and the
# first OPEN sessions start on 2026-11-11. All date-dependent choices are anchored here, never on
# the wall clock, so transcripts do not drift as real time moves.
SEED_AS_OF = os.environ.get("SEED_AS_OF", "2026-11-10")


def _find_sqlcmd():
    explicit = os.environ.get("SQLCMD")
    if explicit:
        return explicit
    found = shutil.which("sqlcmd")
    if found:
        return found
    for candidate in (r"C:\Program Files\SqlCmd\sqlcmd.exe", "/opt/mssql-tools18/bin/sqlcmd", "/opt/mssql-tools/bin/sqlcmd"):
        if os.path.exists(candidate):
            return candidate
    return "sqlcmd"


@dataclass
class Target:
    name: str = "legacy"
    portal_url: str = "http://localhost:8080"
    api_url: str = "http://localhost:8081"
    sync_url: str = "http://localhost:8084"   # scheduling-service reconciler (capacity-sync :8083 is retired)
    sql_server: str = field(default_factory=lambda: os.environ.get("AD_SQL_SERVER", "localhost,1433"))
    sql_user: str = field(default_factory=lambda: os.environ.get("AD_SQL_USER", "sa"))
    sql_password: str = field(default_factory=lambda: os.environ.get("AD_SA_PASSWORD", os.environ.get("SA_PASSWORD", "Str0ng!Passw0rd")))
    database: str = field(default_factory=lambda: os.environ.get("AD_DATABASE", "AssessmentDelivery"))
    sqlcmd: str = field(default_factory=_find_sqlcmd)
    reseed: str = "restore"          # restore | apply | command | none
    reseed_cmd: str = ""             # used when reseed == "command"
    compare: str = "all"             # all | http (skip DB snapshots, e.g. a modern target with its own schema)
    seed_as_of: str = SEED_AS_OF

    @classmethod
    def legacy(cls, **overrides):
        return cls(name="legacy", **{k: v for k, v in overrides.items() if v is not None})

    @classmethod
    def modern(cls, base_url, **overrides):
        base = base_url.rstrip("/")
        values = dict(name="modern", portal_url=base, api_url=base, sync_url=base, reseed="command" if overrides.get("reseed_cmd") else "none")
        values.update({k: v for k, v in overrides.items() if v is not None})
        return cls(**values)
