"""Shared helpers for the functional-test stubs (stdlib only)."""
import json
import os
import subprocess
import urllib.request
import urllib.error

SQLCMD = os.environ.get("SQLCMD", "/opt/mssql-tools18/bin/sqlcmd")
DB_USER = os.environ.get("AD_DB_USER", "ad_app")
DB_PASSWORD = os.environ.get("AD_DB_PASSWORD", "Northw1nd!Dev2011")
BOOKING_API = os.environ.get("BOOKING_API_URL", "http://localhost:8081")


def sql_rows(query):
    """Run a read-only query against AssessmentDelivery, return rows as lists of strings."""
    out = subprocess.run(
        [SQLCMD, "-C", "-S", "localhost,1433", "-U", DB_USER, "-P", DB_PASSWORD, "-d", "AssessmentDelivery",
         "-h", "-1", "-W", "-s", "|", "-Q", "SET NOCOUNT ON; " + query],
        check=True, capture_output=True, text=True).stdout
    return [line.split("|") for line in out.splitlines() if line.strip()]


def http(method, url, body=None, headers=None):
    data = None if body is None else (body if isinstance(body, bytes) else json.dumps(body).encode())
    req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
    if body is not None and "Content-Type" not in (headers or {}):
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
