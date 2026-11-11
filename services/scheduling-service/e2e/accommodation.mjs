// Headless browser verification of MBA-3830 (A5, accommodation booking). Usage:
//   BASE_URL=http://localhost:8090 node accommodation.mjs
// Picks an open session with free accommodation seats, books all but one of them through the API, then:
// an ET25 candidate books the last accommodation seat in /app, a second extra-time candidate sees the session
// unavailable, the API refuses another ET booking with 409 NO_ACCOMMODATION_SEAT without consuming a general seat,
// and an ET reschedule to that session is disabled in /app and refused by the API with the original booking intact.
// Screenshots go to ./screenshots/a5-*.png. Exits non-zero on the first failed step.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE_URL ?? 'http://localhost:8090';
const OUT = new URL('./screenshots/', import.meta.url).pathname;
mkdirSync(OUT, { recursive: true });

const today = new Date().toISOString().slice(0, 10);
const call = async (method, path, body) => {
  const r = await fetch(BASE + path, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  return { status: r.status, body: text ? JSON.parse(text) : null };
};
const api = async (path) => {
  const r = await call('GET', path);
  if (r.status !== 200) throw new Error(`GET ${path} -> ${r.status} ${JSON.stringify(r.body)}`);
  return r.body;
};
const book = (sessionId, candidateNumber, accommodationCode) =>
  call('POST', '/api/v2/bookings', { sessionId, candidateNumber, accommodationCode, externalRef: null, channel: 'PORTAL' });
const expect = (cond, msg) => { if (!cond) throw new Error(`Assertion failed: ${msg}`); };

const candidateNumbers = Array.from({ length: 100 }, (_, i) => `NW${String(901 + i).padStart(8, '0')}`);

async function canBook(candidateNumber, session, others = []) {
  const active = (await api(`/api/v2/candidates/${candidateNumber}/bookings`))
    .filter((b) => b.status === 'BOOKED' || b.status === 'WAITLISTED');
  if (active.some((b) => b.examCode === session.examCode || [session.sessionId, ...others].includes(b.sessionId))) return false;
  const e = await api(`/api/v2/candidates/${candidateNumber}/eligibility?examCode=${session.examCode}&date=${session.startsAt.slice(0, 10)}`);
  return e.eligible;
}

/**
 * Session S (accommodation seats free, room for more general seats), a second session R of the same exam with a free
 * accommodation seat (reschedule source), an ET25-default candidate and a second extra-time-default candidate.
 */
async function pickScenario() {
  const profiles = [];
  for (const n of candidateNumbers) profiles.push(await api(`/api/v2/candidates/${n}`));
  const et25 = profiles.filter((c) => c.defaultAccommodationCode === 'ET25').map((c) => c.candidateNumber);
  const etAny = profiles.filter((c) => c.defaultAccommodationCode === 'ET25' || c.defaultAccommodationCode === 'ET50')
    .map((c) => c.candidateNumber);
  const plain = profiles.filter((c) => !c.defaultAccommodationCode).map((c) => c.candidateNumber);
  for (const center of await api('/api/v2/centers')) {
    const sessions = (await api(`/api/v2/centers/${center.centerCode}/sessions?from=${today}`)).filter((s) => s.status === 'OPEN');
    for (const s of sessions) {
      if (s.accommodationSeatsAvailable < 1 || s.seatsAvailable < s.accommodationSeatsAvailable + 3) continue;
      const r = sessions.find((x) => x.examCode === s.examCode && x.sessionId !== s.sessionId
        && x.accommodationSeatsAvailable >= 1 && x.seatsAvailable >= 2);
      if (!r) continue;
      const first = await findCandidate(et25, s, [r.sessionId]);
      if (!first) continue;
      const second = await findCandidate(etAny.filter((n) => n !== first), s, [r.sessionId]);
      if (!second) continue;
      const fillers = [];
      for (const n of plain) {
        if (fillers.length === s.accommodationSeatsAvailable - 1) break;
        if (await canBook(n, s)) fillers.push(n);
      }
      if (fillers.length < s.accommodationSeatsAvailable - 1) continue;
      return { center, s, r, first, second, fillers };
    }
  }
  throw new Error('No session with free accommodation seats and suitable extra-time candidates found');
}

async function findCandidate(list, s, others) {
  for (const n of list) if (await canBook(n, s, others)) return n;
  return null;
}

const steps = [];
const step = async (name, fn) => {
  const t = Date.now();
  try {
    await fn();
    steps.push(`PASS ${name} (${Date.now() - t} ms)`);
    console.log(`PASS ${name}`);
  } catch (e) {
    steps.push(`FAIL ${name}: ${e.message}`);
    console.error(`FAIL ${name}: ${e.message}`);
    throw e;
  }
};

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1360, height: 900 } });
const shot = (name) => page.screenshot({ path: `${OUT}${name}.png`, fullPage: true });
const TOTAL = 6;
let exitCode = 0;

try {
  const sc = await pickScenario();
  const { s, r } = sc;
  const date = s.startsAt.slice(0, 10);
  const searchUrl = (candidateNumber) =>
    `${BASE}/app?centerCode=${s.centerCode}&examCode=${s.examCode}&date=${date}&candidateNumber=${candidateNumber}`;
  const card = () => page.locator(`[data-testid=session-card][data-session-id="${s.sessionId}"]`);
  console.log(`Scenario: session ${s.sessionId} (${s.centerCode} ${s.examCode} ${date}), ET25 ${sc.first}, second ET ${sc.second}, `
    + `reschedule source ${r.sessionId}, ${sc.fillers.length} filler ET booking(s)`);
  let generalBefore;

  await step('0 leave exactly one accommodation seat on the session (API setup)', async () => {
    for (const n of sc.fillers) {
      const res = await book(s.sessionId, n, 'ET50');
      expect(res.status === 201 && res.body.status === 'BOOKED', `filler ${n}: ${res.status} ${JSON.stringify(res.body)}`);
    }
    const fresh = await api(`/api/v2/sessions/${s.sessionId}`);
    expect(fresh.accommodationSeatsAvailable === 1, `accommodationSeatsAvailable ${fresh.accommodationSeatsAvailable}`);
    generalBefore = fresh.seatsAvailable;
  });

  await step('1 ET25 candidate searches: default pre-filled, one accommodation seat left', async () => {
    await page.goto(searchUrl(sc.first));
    await page.getByTestId('search-candidate-name').waitFor();
    await card().waitFor();
    const select = page.getByTestId('search-accommodation-select');
    expect(await select.inputValue() === 'ET25', `pre-filled ${await select.inputValue()}`);
    expect(await select.isEnabled(), 'accommodation select is editable');
    await page.getByTestId('accommodation-prefilled').waitFor();
    const left = await card().getByTestId('accommodation-seats-left').innerText();
    expect(/^1 accommodation seat left/.test(left), `card says "${left}"`);
    expect(await card().getAttribute('data-unavailable') === 'false', 'session bookable for ET25');
    const all = await page.getByTestId('accommodation-seats-left').count();
    expect(all === await page.getByTestId('session-card').count(), 'every card shows accommodation seats left');
    await shot('a5-01-et25-search-one-accommodation-seat-left');
  });

  await step('2 ET25 candidate books the last accommodation seat in /app', async () => {
    await card().getByTestId('book-link').click();
    await page.getByTestId('booking-form').waitFor();
    expect(await page.getByTestId('accommodation-select').inputValue() === 'ET25', 'Book carries ET25 over');
    expect(await page.getByTestId('candidate-input').inputValue() === sc.first, 'Book carries the candidate over');
    await page.getByTestId('eligibility').waitFor();
    await shot('a5-02-et25-book-form-prefilled');
    await page.getByTestId('book-submit').click();
    await page.getByTestId('booking-confirmation').waitFor();
    const left = await page.getByTestId('summary-accommodation-left').innerText();
    expect(/^0 accommodation seats left/.test(left), `summary says "${left}"`);
    const fresh = await api(`/api/v2/sessions/${s.sessionId}`);
    expect(fresh.accommodationSeatsAvailable === 0, `API accommodationSeatsAvailable ${fresh.accommodationSeatsAvailable}`);
    expect(fresh.seatsAvailable === generalBefore - 1, `API seatsAvailable ${generalBefore} -> ${fresh.seatsAvailable}`);
    const mine = (await api(`/api/v2/candidates/${sc.first}/bookings`)).find((b) => b.sessionId === s.sessionId && b.status === 'BOOKED');
    expect(mine && mine.accommodationCode === 'ET25', `booking ${JSON.stringify(mine)}`);
    generalBefore = fresh.seatsAvailable;
    await shot('a5-03-et25-booked-last-accommodation-seat');
  });

  await step('3 second extra-time candidate sees the session unavailable', async () => {
    await page.goto(searchUrl(sc.second));
    await page.getByTestId('search-candidate-name').waitFor();
    await card().locator('[data-testid=accommodation-seats-left]', { hasText: '0 accommodation seats left' }).waitFor();
    const code = await page.getByTestId('search-accommodation-select').inputValue();
    expect(code === 'ET25' || code === 'ET50', `pre-filled ${code}`);
    expect(await card().getAttribute('data-unavailable') === 'true', 'card marked unavailable');
    expect(await card().getByTestId('book-link').isDisabled(), 'book button disabled');
    await shot('a5-04-second-et-candidate-sees-session-unavailable');
    // The code stays editable: SR does not need an accommodation seat, an explicit None sticks.
    await page.getByTestId('search-accommodation-select').selectOption('SR');
    expect(await card().getAttribute('data-unavailable') === 'false', 'SR can book the session');
    await page.getByTestId('search-accommodation-select').selectOption('');
    await page.reload();
    await card().waitFor();
    expect(await page.getByTestId('search-accommodation-select').inputValue() === '', 'explicit None survives a reload');
    expect(await card().getAttribute('data-unavailable') === 'false', 'None can book the session');
  });

  await step('4 API: another ET booking -> 409 NO_ACCOMMODATION_SEAT, general seats unchanged', async () => {
    const res = await book(s.sessionId, sc.second, 'ET50');
    expect(res.status === 409, `status ${res.status}`);
    expect(res.body.error === 'NO_ACCOMMODATION_SEAT' && res.body.legacyCode === 50003, JSON.stringify(res.body));
    const fresh = await api(`/api/v2/sessions/${s.sessionId}`);
    expect(fresh.seatsAvailable === generalBefore, `general seats ${generalBefore} -> ${fresh.seatsAvailable}`);
    expect(fresh.accommodationSeatsAvailable === 0, 'still no accommodation seat');
  });

  await step('5 ET reschedule: full session disabled in /app, API refuses it, original stays booked', async () => {
    const res = await book(r.sessionId, sc.second, 'ET50');
    expect(res.status === 201 && res.body.status === 'BOOKED', `ET booking on ${r.sessionId}: ${JSON.stringify(res.body)}`);
    const bookingId = res.body.bookingId;
    await page.goto(`${BASE}/app/reschedule/${bookingId}`);
    await page.getByTestId('reschedule-accommodation-note').waitFor();
    if (r.centerCode !== s.centerCode) await page.getByTestId('reschedule-center').selectOption(s.centerCode);
    const option = page.locator(`[data-testid=reschedule-option][data-session-id="${s.sessionId}"]`);
    await option.waitFor();
    expect(await option.getAttribute('data-unavailable') === 'true', 'target marked unavailable');
    expect(await option.isDisabled(), 'target disabled');
    await option.getByTestId('reschedule-unavailable-reason').waitFor();
    await shot('a5-05-et-reschedule-target-unavailable');
    const moved = await call('POST', `/api/v2/bookings/${bookingId}/reschedule`, { newSessionId: s.sessionId });
    expect(moved.status === 409 && moved.body.error === 'NO_ACCOMMODATION_SEAT' && moved.body.legacyCode === 50003,
      `reschedule ${moved.status} ${JSON.stringify(moved.body)}`);
    const original = await api(`/api/v2/bookings/${bookingId}`);
    expect(original.status === 'BOOKED' && original.sessionId === r.sessionId && original.accommodationCode === 'ET50',
      `original ${JSON.stringify(original)}`);
    const fresh = await api(`/api/v2/sessions/${s.sessionId}`);
    expect(fresh.seatsAvailable === generalBefore, `general seats on ${s.sessionId} unchanged`);
  });
} catch {
  exitCode = 1;
} finally {
  await browser.close();
  console.log(`\n${steps.join('\n')}\n${steps.filter((x) => x.startsWith('PASS')).length}/${TOTAL} steps passed`);
  process.exit(exitCode);
}
