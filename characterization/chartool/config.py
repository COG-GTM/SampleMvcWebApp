"""Runtime configuration, read from the environment (same variable names as the repo's run scripts)."""
import os
from dataclasses import dataclass, field
from pathlib import Path

CHAR_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = CHAR_DIR.parent


def _env(name, default):
    value = os.environ.get(name)
    return value if value not in (None, "") else default


def resolve_sqlcmd(preferred=""):
    """SQLCMD env, else sqlcmd on PATH, else the default Windows install location used by scripts/run-legacy.ps1."""
    import shutil
    if preferred:
        return preferred
    found = shutil.which("sqlcmd")
    if found:
        return found
    for candidate in (Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "SqlCmd" / "sqlcmd.exe",
                      Path("/opt/mssql-tools18/bin/sqlcmd")):
        if candidate.exists():
            return str(candidate)
    return "sqlcmd"


@dataclass
class Config:
    sql_server: str = field(default_factory=lambda: _env("AD_SQL_SERVER", "localhost,1433"))
    sa_password: str = field(default_factory=lambda: _env("AD_SA_PASSWORD", "Str0ng!Passw0rd"))
    database: str = field(default_factory=lambda: _env("AD_DATABASE", "AssessmentDelivery"))
    baseline_database: str = field(default_factory=lambda: _env("CHAR_BASELINE_DB", "AssessmentDelivery_CharBaseline"))
    scheduling_database: str = field(default_factory=lambda: _env("SCHEDULING_DATABASE", "Scheduling"))
    portal_url: str = field(default_factory=lambda: _env("PORTAL_URL", "http://localhost:8080").rstrip("/"))
    booking_api_url: str = field(default_factory=lambda: _env("BOOKING_API_URL", "http://localhost:8081").rstrip("/"))
    results_url: str = field(default_factory=lambda: _env("RESULTS_URL", "http://localhost:8082").rstrip("/"))
    capacity_sync_url: str = field(default_factory=lambda: _env("CAPACITY_SYNC_URL", "http://localhost:8083").rstrip("/"))
    scheduling_url: str = field(default_factory=lambda: _env("SCHEDULING_URL", "http://localhost:8090").rstrip("/"))
    odbc_driver: str = field(default_factory=lambda: _env("CHAR_ODBC_DRIVER", ""))
    sqlcmd: str = field(default_factory=lambda: resolve_sqlcmd(_env("SQLCMD", "")))
    repo_root: Path = REPO_ROOT
    legacy_root: str = field(default_factory=lambda: _env("LEGACY_ROOT", ""))
    http_timeout: float = 60.0
