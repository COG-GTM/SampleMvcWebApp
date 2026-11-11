"""Clients for the three user-facing surfaces (portal HTML forms, partner booking-api REST, results-service REST)
and for scheduling-service itself. The portal and partner clients are the same for the legacy and the migrated
stack: Phase 2 rewires what is behind them, not their HTTP surface."""
import html
import http.cookiejar
import re
import urllib.request

from adlib import http, multipart


class Outcome:
    """Normalised result of a booking command on any channel."""

    def __init__(self, ok, booking_id=None, status=None, error=None, message=None, http_status=None, raw=None):
        self.ok, self.booking_id, self.status = ok, booking_id, status
        self.error, self.message, self.http_status, self.raw = error, message, http_status, raw

    def __repr__(self):
        if self.ok:
            return '%s %s' % (self.status or 'OK', self.booking_id or '')
        return 'ERR %s %s %s' % (self.http_status or '', self.error or '', self.message or '')


def _page_message(text):
    msgs = []
    for m in re.finditer(r'<(div|li)[^>]*class="[^"]*(alert|validation-summary-errors|field-validation-error)[^"]*"[^>]*>(.*?)</\1>',
                         text or '', re.S):
        t = html.unescape(re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', m.group(3)))).strip()
        if t and t not in msgs:
            msgs.append(t)
    return ' | '.join(msgs)


class Portal:
    """The real .NET portal over HTTP: GET the form, POST it with its antiforgery token, follow the redirect."""

    def __init__(self, url):
        self.url = url.rstrip('/')

    def _form(self, get_path, post_path, fields):
        jar = http.cookiejar.CookieJar()
        op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
        page = http('GET', self.url + get_path, opener=op)
        if page.status != 200:
            return page
        m = re.search(r'name="__RequestVerificationToken"[^>]*value="([^"]*)"', page.text)
        if m:
            fields = dict(fields, __RequestVerificationToken=m.group(1))
        return http('POST', self.url + post_path, form=fields, opener=op)

    def book(self, session_id, candidate_number, accommodation=''):
        r = self._form('/Booking/Book?sessionId=%s&candidateNumber=%s' % (session_id, candidate_number), '/Booking/Book',
                       {'SessionId': session_id, 'CandidateNumber': candidate_number, 'AccommodationCode': accommodation or ''})
        m = re.search(r'Booking (\d+) confirmed', r.text or '')
        if m:
            return Outcome(True, int(m.group(1)), 'BOOKED', http_status=r.status)
        m = re.search(r'booking (\d+) has been placed on the waitlist', r.text or '')
        if m:
            return Outcome(True, int(m.group(1)), 'WAITLISTED', http_status=r.status)
        return Outcome(False, message=_page_message(r.text) or ('HTTP %s' % r.status), http_status=r.status)

    def cancel(self, booking_id, reason='e2e', candidate_number=''):
        r = self._form('/Booking/Cancel/%s?candidateNumber=%s' % (booking_id, candidate_number), '/Booking/Cancel/%s' % booking_id,
                       {'Reason': reason, 'CandidateNumber': candidate_number})
        if re.search(r'Booking %s cancelled' % booking_id, r.text or ''):
            return Outcome(True, booking_id, 'CANCELLED', http_status=r.status)
        return Outcome(False, message=_page_message(r.text) or ('HTTP %s' % r.status), http_status=r.status)

    def reschedule(self, booking_id, new_session_id, candidate_number=''):
        r = self._form('/Booking/Reschedule/%s?candidateNumber=%s' % (booking_id, candidate_number),
                       '/Booking/Reschedule/%s' % booking_id, {'NewSessionId': new_session_id, 'CandidateNumber': candidate_number})
        m = re.search(r'New booking (\d+) \((BOOKED|WAITLISTED)\)', r.text or '')
        if m:
            return Outcome(True, int(m.group(1)), m.group(2), http_status=r.status)
        return Outcome(False, message=_page_message(r.text) or ('HTTP %s' % r.status), http_status=r.status)

    def change_capacity(self, session_id, new_capacity):
        r = self._form('/Admin/Sessions/Capacity/%s' % session_id, '/Admin/Sessions/Capacity/%s' % session_id,
                       {'newCapacity': new_capacity, 'NewCapacity': new_capacity})
        if 'Capacity updated' in (r.text or ''):
            return Outcome(True, status='UPDATED', http_status=r.status)
        return Outcome(False, message=_page_message(r.text) or ('HTTP %s' % r.status), http_status=r.status)

    def get(self, path):
        return http('GET', self.url + path)


class Partner:
    """booking-api (partner channel)."""

    def __init__(self, url):
        self.url = url.rstrip('/')

    @staticmethod
    def _outcome(r, ok_codes=(200, 201)):
        j = r.json if isinstance(r.json, dict) else {}
        if r.status in ok_codes:
            return Outcome(True, j.get('bookingId'), j.get('status'), http_status=r.status, raw=j)
        return Outcome(False, error=j.get('error'), message=j.get('message') or r.text[:200], http_status=r.status, raw=j)

    def book(self, session_id, candidate_number, accommodation=None, external_ref=None):
        body = {'sessionId': session_id, 'candidateNumber': candidate_number}
        if accommodation is not None:
            body['accommodationCode'] = accommodation
        if external_ref is not None:
            body['externalRef'] = external_ref
        return self._outcome(http('POST', self.url + '/api/v1/bookings', body=body))

    def book_raw(self, body):
        return self._outcome(http('POST', self.url + '/api/v1/bookings', body=body))

    def cancel(self, booking_id, reason='e2e'):
        o = self._outcome(http('DELETE', '%s/api/v1/bookings/%s?reason=%s' % (self.url, booking_id, reason)))
        if o.ok:
            o.booking_id, o.status = booking_id, 'CANCELLED'
        return o

    def reschedule(self, booking_id, new_session_id):
        return self._outcome(http('POST', '%s/api/v1/bookings/%s/reschedule' % (self.url, booking_id),
                                  body={'newSessionId': new_session_id}))

    def booking(self, booking_id):
        return http('GET', '%s/api/v1/bookings/%s' % (self.url, booking_id))

    def session(self, session_id):
        return http('GET', '%s/api/v1/sessions/%s' % (self.url, session_id))


class Results:
    def __init__(self, url):
        self.url = url.rstrip('/')

    def post(self, booking_id, score, scored_at=None):
        body = {'bookingId': booking_id, 'score': score}
        if scored_at:
            body['scoredAt'] = scored_at
        return http('POST', self.url + '/api/v1/results', body=body)

    def upload(self, name, content):
        data, ctype = multipart({}, {'file': (name, content)})
        return http('POST', self.url + '/api/v1/results/upload', raw=data, content_type=ctype)


class Scheduling:
    """scheduling-service (migrated stack only), docs/to-be/scheduling-service-openapi.yaml."""

    def __init__(self, url):
        self.url = url.rstrip('/')
        self.base = self.url + '/api/v1'

    def call(self, method, path, body=None, headers=None):
        return http(method, self.base + path, body=body, headers=headers)
