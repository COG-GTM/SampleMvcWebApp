"""Thin go-sqlcmd/sqlcmd wrapper (no ODBC driver needed, works on Windows and Linux)."""
import json
import os
import re
import subprocess
import sys
import time

from .config import REPO_DIR

MSG_RE = re.compile(r"Msg (\d+), Level (\d+), State (\d+)[^\n]*\n([^\n]*)")


class SqlError(RuntimeError):
    pass


class Db:
    def __init__(self, target):
        self.t = target

    def _base(self, database=None):
        args = [self.t.sqlcmd, "-S", self.t.sql_server, "-C", "-U", self.t.sql_user, "-P", self.t.sql_password]
        if database is not False:
            args += ["-d", database or self.t.database]
        return args

    @staticmethod
    def _sqlcmd(args, timeout=None):
        try:
            return subprocess.run(args, stdin=subprocess.DEVNULL, capture_output=True, text=True, encoding="utf-8",
                                  errors="replace", timeout=timeout)
        except OSError as e:
            raise SqlError(f"cannot run sqlcmd ({args[0]}): {e}") from e

    def run(self, sql, database=None, check=True, timeout=600):
        args = self._base(database) + ["-h", "-1", "-W", "-y", "0", "-Q", sql]
        proc = self._sqlcmd(args, timeout=timeout)
        out = (proc.stdout or "") + (proc.stderr or "")
        if check and (proc.returncode != 0 or MSG_RE.search(out)):
            raise SqlError(f"sqlcmd failed ({proc.returncode}): {out.strip()[:2000]}\nSQL: {sql[:500]}")
        return out

    def run_rows(self, script):
        """Run a sqlcmd script (e.g. db/checks/seat-drift.sql) and return its rows as lists of trimmed column values.
        Batch separators and USE are dropped: the connection already targets the configured database."""
        body = "\n".join(line for line in script.splitlines()
                         if not re.match(r"\s*(GO|USE\s+\w+;?)\s*$", line, re.IGNORECASE))
        args = self._base() + ["-h", "-1", "-W", "-s", "|", "-Q", "SET NOCOUNT ON;\n" + body]
        proc = self._sqlcmd(args)
        out = (proc.stdout or "") + (proc.stderr or "")
        if proc.returncode != 0 or MSG_RE.search(out):
            raise SqlError(out[:2000])
        return [[c.strip() for c in line.split("|")] for line in out.splitlines() if line.strip()]

    def errors(self, output):
        return [dict(number=int(m.group(1)), state=int(m.group(3)), message=m.group(4).strip()) for m in MSG_RE.finditer(output)]

    def json(self, select_sql):
        """Run a SELECT and return its rows as a list of dicts (via FOR JSON)."""
        sql = f"SET NOCOUNT ON; SELECT ISNULL(({select_sql} FOR JSON PATH, INCLUDE_NULL_VALUES), '[]');"
        out = self.run(sql)
        text = "".join(line.strip() for line in out.splitlines() if line.strip())
        return json.loads(text)

    def scalar(self, select_sql):
        out = self.run("SET NOCOUNT ON; " + select_sql)
        lines = [line.strip() for line in out.splitlines() if line.strip()]
        return lines[0] if lines else None

    # ---- reseeding -------------------------------------------------------------------------
    def apply_seed(self):
        """Rebuild AssessmentDelivery from db/ (schema, procs, triggers, deterministic seed 040-049)."""
        if self.t.database != "AssessmentDelivery":
            raise SqlError(f"db/apply only rebuilds AssessmentDelivery, not --database {self.t.database}; "
                           "use --reseed command --reseed-cmd ... to reseed another database")
        env = dict(os.environ, AD_SA_PASSWORD=self.t.sql_password, AD_SQL_SERVER=self.t.sql_server,
                   AD_SQL_USER=self.t.sql_user, SQLCMD=self.t.sqlcmd)
        if os.name == "nt":
            cmd = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(REPO_DIR / "db" / "apply.ps1")]
        else:
            cmd = ["bash", str(REPO_DIR / "db" / "apply.sh")]
        proc = subprocess.run(cmd, cwd=str(REPO_DIR), stdin=subprocess.DEVNULL, capture_output=True, text=True, errors="replace", env=env)
        if proc.returncode != 0:
            raise SqlError(f"db/apply failed: {proc.stdout[-2000:]}\n{proc.stderr[-2000:]}")

    def backup_path(self):
        base = self.scalar("SELECT CONVERT(nvarchar(4000), SERVERPROPERTY('InstanceDefaultBackupPath'));")
        sep = "\\" if "\\" in (base or "") else "/"
        return f"{base.rstrip(sep)}{sep}{self.t.database}_characterization_seed.bak"

    def backup(self):
        path = self.backup_path()
        self.run(f"BACKUP DATABASE [{self.t.database}] TO DISK = N'{path}' WITH INIT, FORMAT, COPY_ONLY;", database="master")
        return path

    def restore(self, path):
        db = self.t.database
        self.run(
            f"IF DB_ID(N'{db}') IS NOT NULL ALTER DATABASE [{db}] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; "
            f"RESTORE DATABASE [{db}] FROM DISK = N'{path}' WITH REPLACE; "
            f"ALTER DATABASE [{db}] SET MULTI_USER;",
            database="master",
        )

    def run_command(self, command):
        proc = subprocess.run(command, shell=True, cwd=str(REPO_DIR), stdin=subprocess.DEVNULL)
        if proc.returncode != 0:
            raise SqlError(f"reseed command failed: {command}")


def log(msg):
    print(f"[characterization {time.strftime('%H:%M:%S')}] {msg}", file=sys.stderr, flush=True)
