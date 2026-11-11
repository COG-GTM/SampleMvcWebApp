"""HTTP helpers: JSON REST calls for booking-api/capacity-sync and antiforgery-aware form posts for the MVC portal."""
import html
import http.cookiejar
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request

TOKEN_RE = re.compile(r'name="__RequestVerificationToken"[^>]*value="([^"]+)"')
TAG_RE = re.compile(r"<[^>]+>")
ALERT_RE = re.compile(r'<div[^>]*class="[^"]*\balert\b[^"]*"[^>]*>(.*?)</div>', re.S)
FIELD_ERR_RE = re.compile(r'<span[^>]*class="[^"]*field-validation-error[^"]*"[^>]*>(.*?)</span>', re.S)
TH_TD_RE = re.compile(r"<th[^>]*>(.*?)</th>\s*<td[^>]*>(.*?)</td>", re.S)
DT_DD_RE = re.compile(r"<dt[^>]*>(.*?)</dt>\s*<dd[^>]*>(.*?)</dd>", re.S)
TITLE_RE = re.compile(r"<h[12][^>]*>(.*?)</h[12]>", re.S)


def text(fragment):
    return re.sub(r"\s+", " ", html.unescape(TAG_RE.sub(" ", fragment))).strip()


class Response:
    def __init__(self, status, url, body, headers=None):
        self.status, self.url, self.body, self.headers = status, url, body, headers or {}

    def json(self):
        try:
            return json.loads(self.body) if self.body.strip() else None
        except ValueError:
            return {"_raw": self.body[:500]}


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


class Client:
    def __init__(self, follow_redirects=True, timeout=60):
        self.jar = http.cookiejar.CookieJar()
        handlers = [urllib.request.HTTPCookieProcessor(self.jar)]
        if not follow_redirects:
            handlers.append(_NoRedirect())
        self.opener = urllib.request.build_opener(*handlers)
        self.timeout = timeout

    def request(self, method, url, data=None, headers=None):
        req = urllib.request.Request(url, data=data, method=method, headers=headers or {})
        try:
            with self.opener.open(req, timeout=self.timeout) as r:
                return Response(r.status, r.geturl(), r.read().decode("utf-8", "replace"), dict(r.headers))
        except urllib.error.HTTPError as e:
            return Response(e.code, url, e.read().decode("utf-8", "replace"), dict(e.headers or {}))

    def json_call(self, method, url, payload=None):
        data = json.dumps(payload).encode() if payload is not None else None
        headers = {"Accept": "application/json"}
        if data is not None:
            headers["Content-Type"] = "application/json"
        return self.request(method, url, data, headers)

    def form_post(self, form_url, post_url, fields):
        """GET the form (cookie + antiforgery token), then POST it like a browser would."""
        page = self.request("GET", form_url)
        match = TOKEN_RE.search(page.body)
        if page.status != 200 or not match:
            return page, False
        body = dict(fields)
        body["__RequestVerificationToken"] = html.unescape(match.group(1))
        data = urllib.parse.urlencode({k: "" if v is None else str(v) for k, v in body.items()}).encode()
        resp = self.request("POST", post_url, data, {"Content-Type": "application/x-www-form-urlencoded"})
        return resp, True


def rendered_fields(page_html):
    """Reduce a rendered portal page to the fields a user sees: heading, alerts, validation, key/value cells."""
    main = page_html.split("<main", 1)[-1] if "<main" in page_html else page_html
    alerts = [text(a) for a in ALERT_RE.findall(main)]
    out = {
        "heading": next((text(h) for h in TITLE_RE.findall(main) if text(h)), None),
        "alerts": [a for a in alerts if a],
        "field_errors": [t for t in (text(e) for e in FIELD_ERR_RE.findall(main)) if t],
    }
    cells = {}
    for k, v in TH_TD_RE.findall(main) + DT_DD_RE.findall(main):
        key = text(k)
        if key and key not in cells:
            cells[key] = text(v)
    if cells:
        out["cells"] = cells
    return out


def wait_until_up(url, attempts=30, delay=2.0):
    client = Client()
    last = None
    for _ in range(attempts):
        try:
            last = client.request("GET", url)
            if last.status < 500:
                return True
        except Exception as exc:  # connection refused, reset after a restore, ...
            last = exc
        time.sleep(delay)
    raise RuntimeError(f"{url} not healthy: {last if not isinstance(last, Response) else last.status}")


def wait_settled(fetch, timeout=60.0, delay=0.25, clock=time.monotonic, sleep=time.sleep):
    """Poll fetch() (a JSON dict) until its ``pending`` count is 0, e.g. an outbox relay that projects writes
    into the database the snapshots read (scheduling-service ``GET /internal/relay``)."""
    deadline = clock() + timeout
    last = None
    while True:
        try:
            last = fetch()
            if int(last.get("pending", 0)) == 0:
                return last
        except Exception as exc:  # restarting service, restore in progress, ...
            last = exc
        if clock() >= deadline:
            raise RuntimeError(f"target did not settle within {timeout:.0f}s: {last}")
        sleep(delay)
