"""SQL Server access: batches, the per-scenario reset from a pristine copy, and small query helpers."""
import re
import subprocess
import sys

try:
    import pyodbc
except ImportError:  # unit tests run without a database driver
    pyodbc = None

# Tables restored by reset(), parents first. Scenario mutations only touch these.
RESET_TABLES = ["TestCenter", "Room", "Exam", "Candidate", "ExamSession", "Booking", "Waitlist", "Result", "AuditLog"]

_GO = re.compile(r"^\s*GO\s*(?:--.*)?$", re.IGNORECASE | re.MULTILINE)


def split_batches(sql):
    """Split a T-SQL script on GO lines (sqlcmd semantics) and drop empty batches."""
    return [b.strip() for b in _GO.split(sql) if b.strip()]


def pick_driver(preferred=""):
    if preferred:
        return preferred
    available = pyodbc.drivers() if pyodbc else []
    for name in ("ODBC Driver 18 for SQL Server", "ODBC Driver 17 for SQL Server", "SQL Server"):
        if name in available:
            return name
    raise RuntimeError("no SQL Server ODBC driver found (install 'ODBC Driver 18 for SQL Server')")


class Db:
    def __init__(self, config, database=None):
        if pyodbc is None:
            raise RuntimeError("pyodbc is not installed: pip install -r characterization/requirements.txt")
        self.config = config
        driver = pick_driver(config.odbc_driver)
        extra = ";Encrypt=yes;TrustServerCertificate=yes" if "ODBC Driver" in driver else ""
        self.conn = pyodbc.connect(
            "DRIVER={%s};SERVER=%s;DATABASE=%s;UID=sa;PWD=%s%s"
            % (driver, config.sql_server, database or config.database, config.sa_password, extra),
            autocommit=True, timeout=30)

    def close(self):
        self.conn.close()

    def execute(self, sql, params=()):
        cur = self.conn.cursor()
        cur.execute(sql, params)
        return cur

    def run_script(self, sql):
        for batch in split_batches(sql):
            cur = self.conn.cursor()
            cur.execute(batch)
            try:
                while cur.nextset():
                    pass
            except pyodbc.Error:
                pass

    def rows(self, sql, params=()):
        cur = self.conn.cursor()
        cur.execute(sql, params)
        while cur.description is None:
            if not cur.nextset():
                return []
        cols = [d[0] for d in cur.description]
        return [dict(zip(cols, r)) for r in cur.fetchall()]

    def scalar(self, sql, params=()):
        rows = self.rows(sql, params)
        if not rows:
            return None
        return next(iter(rows[0].values()))

    def now(self):
        return self.scalar("SELECT GETDATE() AS now")


def apply_seed(config):
    """Rebuild AssessmentDelivery with the repo's db/apply script (drops the database)."""
    root = config.repo_root
    if sys.platform.startswith("win"):
        cmd = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(root / "db" / "apply.ps1"),
               "-Sqlcmd", config.sqlcmd]
    else:
        cmd = ["bash", str(root / "db" / "apply.sh")]
    env = dict(__import__("os").environ, AD_SA_PASSWORD=config.sa_password, AD_SQL_SERVER=config.sql_server,
               SQLCMD=config.sqlcmd)
    subprocess.run(cmd, check=True, env=env, stdout=subprocess.DEVNULL)


def table_columns(db, database, table):
    rows = db.rows(
        "SELECT c.name, c.is_identity FROM [%s].sys.columns c "
        "JOIN [%s].sys.tables t ON t.object_id = c.object_id "
        "WHERE t.name = ? AND c.is_computed = 0 ORDER BY c.column_id" % (database, database), (table,))
    return [r["name"] for r in rows], any(r["is_identity"] for r in rows)


def create_baseline(config):
    """Copy the freshly seeded AssessmentDelivery into a pristine baseline database (tables + identity seeds)."""
    db = Db(config, "master")
    try:
        base, src = config.baseline_database, config.database
        db.execute("IF DB_ID('%s') IS NOT NULL BEGIN ALTER DATABASE [%s] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; "
                   "DROP DATABASE [%s]; END" % (base, base, base))
        db.execute("CREATE DATABASE [%s]" % base)
        for t in RESET_TABLES:
            db.execute("SELECT * INTO [%s].dbo.[%s] FROM [%s].dbo.[%s]" % (base, t, src, t))
        db.execute("CREATE TABLE [%s].dbo.CharIdent (TableName SYSNAME PRIMARY KEY, CurrentIdent BIGINT NULL)" % base)
        for t in RESET_TABLES:
            db.execute("INSERT INTO [%s].dbo.CharIdent VALUES (?, IDENT_CURRENT(?))" % base, (t, "%s.dbo.%s" % (src, t)))
    finally:
        db.close()


def reset(db, config):
    """Restore every scenario-mutable table from the baseline copy without dropping connections.

    The Java services and the portal keep their pooled connections, so no warm-up is needed.
    """
    base = config.baseline_database
    if db.scalar("SELECT DB_ID(?) AS id", (base,)) is None:
        raise RuntimeError("baseline database %s missing: run capture/replay with --fresh-seed first" % base)
    stmts = ["SET XACT_ABORT ON; BEGIN TRAN;"]
    for t in RESET_TABLES:
        stmts.append("ALTER TABLE dbo.[%s] NOCHECK CONSTRAINT ALL; DISABLE TRIGGER ALL ON dbo.[%s];" % (t, t))
    for t in reversed(RESET_TABLES):
        stmts.append("DELETE FROM dbo.[%s];" % t)
    for t in RESET_TABLES:
        cols, ident = table_columns(db, base, t)
        col_list = ", ".join("[%s]" % c for c in cols)
        body = "INSERT INTO dbo.[%s] (%s) SELECT %s FROM [%s].dbo.[%s];" % (t, col_list, col_list, base, t)
        if ident:
            body = "SET IDENTITY_INSERT dbo.[%s] ON; %s SET IDENTITY_INSERT dbo.[%s] OFF;" % (t, body, t)
        stmts.append(body)
    for t in RESET_TABLES:
        stmts.append("ALTER TABLE dbo.[%s] WITH CHECK CHECK CONSTRAINT ALL; ENABLE TRIGGER ALL ON dbo.[%s];" % (t, t))
    stmts.append("COMMIT;")
    db.run_script("\n".join(stmts))
    for r in db.rows("SELECT TableName, CurrentIdent FROM [%s].dbo.CharIdent" % base):
        if r["CurrentIdent"] is not None:
            db.execute("DBCC CHECKIDENT ('dbo.%s', RESEED, %d) WITH NO_INFOMSGS" % (r["TableName"], r["CurrentIdent"]))


def baseline_max_ids(db, config):
    rows = db.rows("SELECT TableName, CurrentIdent FROM [%s].dbo.CharIdent" % config.baseline_database)
    return {r["TableName"]: int(r["CurrentIdent"] or 0) for r in rows}


def baseline_exists(config):
    db = Db(config, "master")
    try:
        return db.scalar("SELECT DB_ID(?) AS id", (config.baseline_database,)) is not None
    finally:
        db.close()
