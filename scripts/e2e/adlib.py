"""Shared plumbing for the e2e suite and the cutover rehearsal (Python 3 stdlib only).

SQL goes through the same sqlcmd the other repo scripts use (go-sqlcmd on Windows, mssql-tools18 on Linux), so
there is no driver to install. Output is parsed line by line with CR stripped, which is what breaks the bash
mixed-traffic.sh on Git Bash.
"""
import http.cookiejar
import json
import os
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

# Pinned clock. The seed is frozen around its own cut-off (042_seed_sessions.sql @AsOf = 2026-11-11, HB-30), so
# "future" sessions are chosen with StartsAt > AS_OF instead of StartsAt > GETDATE(), and the picks do not move
# with the wall clock. No proc checks StartsAt (HB-31), so booking a session that has started is still legal.
AS_OF = os.environ.get('E2E_AS_OF', '2026-11-11T00:00:00')


class SqlError(RuntimeError):
    pass


def find_sqlcmd():
    env = os.environ.get('SQLCMD')
    if env and (os.path.exists(env) or shutil.which(env)):
        return env
    for c in ('sqlcmd', r'C:\Program Files\SqlCmd\sqlcmd.exe', '/opt/mssql-tools18/bin/sqlcmd', '/opt/mssql-tools/bin/sqlcmd'):
        if shutil.which(c) or os.path.exists(c):
            return c
    raise SqlError('sqlcmd not found (set SQLCMD)')


class Sql:
    """One database on one server. rows() returns lists of strings (NULL -> None)."""

    def __init__(self, database, server=None, user=None, password=None, sqlcmd=None):
        self.database = database
        self.server = server or os.environ.get('AD_SQL_SERVER', 'localhost,1433')
        self.user = user or os.environ.get('AD_SQL_USER', 'sa')
        self.password = password or os.environ.get('AD_SA_PASSWORD') or os.environ.get('MSSQL_SA_PASSWORD') or 'Str0ng!Passw0rd'
        self.sqlcmd = sqlcmd or find_sqlcmd()

    def _run(self, args, timeout=300):
        cmd = [self.sqlcmd, '-S', self.server, '-U', self.user, '-P', self.password, '-C', '-b', '-d', self.database] + args
        last = None
        for attempt in range(3):
            try:
                p = subprocess.run(cmd, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   timeout=timeout)
            except subprocess.TimeoutExpired as e:
                last = SqlError('sqlcmd timed out: %s' % e)
                continue
            out = p.stdout.decode('utf-8', 'replace').replace('\r', '')
            err = p.stderr.decode('utf-8', 'replace').replace('\r', '')
            if p.returncode == 0:
                return out
            last = SqlError('sqlcmd failed (%s): %s %s' % (p.returncode, out.strip()[-800:], err.strip()[-800:]))
            if 'deadlock' not in (out + err).lower():
                break
            time.sleep(0.2 * (attempt + 1))
        raise last

    def rows(self, tsql, sep='|'):
        out = self._run(['-h', '-1', '-W', '-s', sep, '-Q', 'SET NOCOUNT ON; ' + tsql])
        res = []
        for line in out.split('\n'):
            if not line.strip() or line.startswith('Changed database context') or re.match(r'^\(\d+ rows? affected\)$', line.strip()):
                continue
            res.append([None if v == 'NULL' else v for v in line.split(sep)])
        return res

    def scalar(self, tsql):
        r = self.rows(tsql)
        return r[0][0] if r else None

    def ints(self, tsql):
        return [int(r[0]) for r in self.rows(tsql)]

    def execute(self, tsql):
        return self._run(['-Q', 'SET NOCOUNT ON; ' + tsql])

    def script(self, path):
        return self._run(['-i', path])

    def json(self, select_sql):
        """Rows of a SELECT as dicts, exact values (FOR JSON PATH, INCLUDE_NULL_VALUES). The JSON is fetched as
        one NVARCHAR(MAX) value (-y 0: no truncation) and split back into lines only where sqlcmd wrapped it."""
        q = ("SET NOCOUNT ON; DECLARE @j NVARCHAR(MAX) = (%s FOR JSON PATH, INCLUDE_NULL_VALUES); "
             "SELECT ISNULL(@j, N'[]');" % select_sql)
        out = self._run(['-h', '-1', '-y', '0', '-Q', q])
        lines = [l for l in out.split('\n') if l and not l.startswith('Changed database context')]
        return json.loads(''.join(lines).strip())


# ------------------------------------------------------------------ HTTP

class Resp:
    def __init__(self, status, headers, body, url=None):
        self.status = status
        self.headers = headers
        self.text = body
        self.url = url

    @property
    def json(self):
        try:
            return json.loads(self.text) if self.text else None
        except ValueError:
            return None

    def err(self):
        j = self.json
        return j.get('error') if isinstance(j, dict) else None

    def __repr__(self):
        return 'HTTP %s %s' % (self.status, re.sub(r'\s+', ' ', self.text or '')[:300])


def http(method, url, body=None, headers=None, form=None, opener=None, timeout=90, raw=None, content_type=None):
    data = None
    h = dict(headers or {})
    if body is not None:
        data = json.dumps(body).encode('utf-8')
        h['Content-Type'] = 'application/json'
    elif form is not None:
        data = urllib.parse.urlencode(form).encode('utf-8')
        h['Content-Type'] = 'application/x-www-form-urlencoded'
    elif raw is not None:
        data = raw
        h['Content-Type'] = content_type
    req = urllib.request.Request(url, data=data, method=method, headers=h)
    op = opener or urllib.request.build_opener()
    try:
        with op.open(req, timeout=timeout) as r:
            return Resp(r.status, dict(r.headers), r.read().decode('utf-8', 'replace'), r.geturl())
    except urllib.error.HTTPError as e:
        return Resp(e.code, dict(e.headers or {}), e.read().decode('utf-8', 'replace'), url)
    except (urllib.error.URLError, ConnectionError, TimeoutError) as e:
        return Resp(0, {}, 'connection error: %s' % e, url)


def multipart(fields, files):
    boundary = '----e2e' + uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        parts.append(('--%s\r\nContent-Disposition: form-data; name="%s"\r\n\r\n%s\r\n' % (boundary, k, v)).encode())
    for k, (name, content) in files.items():
        parts.append(('--%s\r\nContent-Disposition: form-data; name="%s"; filename="%s"\r\n'
                      'Content-Type: text/csv\r\n\r\n' % (boundary, k, name)).encode() + content + b'\r\n')
    parts.append(('--%s--\r\n' % boundary).encode())
    return b''.join(parts), 'multipart/form-data; boundary=' + boundary


def wait_http(url, timeout=120):
    end = time.time() + timeout
    while time.time() < end:
        if http('GET', url, timeout=10).status:
            return True
        time.sleep(1)
    return False
