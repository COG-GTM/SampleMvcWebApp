// Headless browser verification of MBA-3829 (waitlist auto-offer, AC7). Usage:
//   BASE_URL=http://localhost:8090 CENTER_CODE=BOS01 node waitlist-offer.mjs
// Sets up a full session with waitlist positions 1-3 through the API, cancels candidate A's booking in
// /app/my-bookings, then accepts the seat offer as waitlisted candidate B and checks the admin waitlist.
// Screenshots go to ./screenshots/waitlist-offer[-<SHOT_PREFIX>]/. Exits non-zero on the first failed step.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE_URL ?? 'http://localhost:8090';
const CENTER = process.env.CENTER_CODE ?? 'BOS01';
const OUT = new URL(`./screenshots/waitlist-offer${process.env.SHOT_PREFIX ? `-${process.env.SHOT_PREFIX}` : ''}/`, import.meta.url).pathname;
mkdirSync(OUT, { recursive: true });

const today = new Date().toISOString().slice(0, 10);
const api = async (path, init) => {
  const r = await fetch(BASE + path, init);
  if (!r.ok) throw new Error(`${init?.method ?? 'GET'} ${path} -> ${r.status} ${await r.text()}`);
  const text = await r.text();
  return text ? JSON.parse(text) : null;
};
const send = (method, path, body) =>
  api(path, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
const expect = (cond, msg) => { if (!cond) throw new Error(`Assertion failed: ${msg}`); };

/** An OPEN session with nobody waiting, shrunk to one free seat; then A books it and B, C, D are waitlisted 1-3. */
async function setUpFullSessionWithWaitlist() {
  const sessions = (await api(`/api/v2/centers/${CENTER}/sessions?from=${today}`))
    .filter((s) => s.status === 'OPEN' && s.waitlistCount === 0 && (s.offeredSeats ?? 0) === 0 && s.seatsAvailable >= 1
      && s.bookedSeats + 1 >= s.accommodationCapacity && new Date(s.startsAt) > new Date(Date.now() + 2 * 86400_000));
  const offset = Number(process.env.CANDIDATE_OFFSET ?? Math.floor(Math.random() * 80));
  for (const s of sessions) {
    const picked = [];
    for (let i = 0; i < 100 && picked.length < 4; i++) {
      const n = `NW${String(900 + ((offset + i) % 100) + 1).padStart(8, '0')}`;
      const active = (await api(`/api/v2/candidates/${n}/bookings`)).filter((b) => b.status === 'BOOKED' || b.status === 'WAITLISTED');
      if (active.some((b) => b.examCode === s.examCode)) continue;
      const elig = await api(`/api/v2/candidates/${n}/eligibility?examCode=${s.examCode}&date=${s.startsAt.slice(0, 10)}`);
      if (elig.eligible) picked.push(n);
    }
    if (picked.length < 4) continue;
    await send('PUT', `/api/v2/sessions/${s.sessionId}/capacity`, { newCapacity: s.bookedSeats + 1, actor: 'e2e-waitlist-offer' });
    const results = [];
    for (const n of picked) {
      results.push(await send('POST', '/api/v2/bookings', { sessionId: s.sessionId, candidateNumber: n, accommodationCode: null, channel: 'PORTAL' }));
    }
    expect(results[0].status === 'BOOKED', `A booked, got ${results[0].status}`);
    expect(results.slice(1).every((r, i) => r.status === 'WAITLISTED' && r.waitlistPosition === i + 1), 'B, C, D waitlisted 1-3');
    const [a, b, c, d] = picked.map((n, i) => ({ candidateNumber: n, bookingId: results[i].bookingId }));
    return { session: await api(`/api/v2/sessions/${s.sessionId}`), a, b, c, d };
  }
  throw new Error('No OPEN session with an empty waitlist and four eligible candidates found');
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
  const sc = await setUpFullSessionWithWaitlist();
  const sid = sc.session.sessionId;
  console.log(`Scenario: session ${sid} (${sc.session.examCode}), A=${sc.a.candidateNumber} B=${sc.b.candidateNumber} `
    + `C=${sc.c.candidateNumber} D=${sc.d.candidateNumber}`);

  await step('1 admin sees a full session with waitlist positions 1-3', async () => {
    await page.goto(`${BASE}/app/admin/sessions/${sid}`);
    await page.getByTestId('waitlist-row').nth(2).waitFor();
    expect((await page.getByTestId('available').innerText()).trim() === '0', 'seats available 0');
    expect(await page.getByTestId('waitlist-offer-state').count() === 0, 'no offers yet');
    await shot('01-admin-full-session-waitlist');
  });

  await step('2 candidate A cancels the booking in My bookings', async () => {
    await page.goto(`${BASE}/app/my-bookings?candidateNumber=${sc.a.candidateNumber}`);
    const row = page.locator(`[data-testid=booking-row][data-booking-id="${sc.a.bookingId}"][data-status=BOOKED]`);
    await row.waitFor();
    await shot('02a-candidate-a-my-bookings');
    await row.getByTestId('cancel-button').click();
    await page.getByTestId('cancel-confirm').waitFor();
    await shot('02b-candidate-a-cancel-confirm');
    await page.getByTestId('cancel-confirm').click();
    await page.locator(`[data-testid=booking-row][data-booking-id="${sc.a.bookingId}"][data-status=CANCELLED]`).waitFor();
    await shot('02c-candidate-a-cancelled');
  });

  await step('3 admin sees position 1 offered and the seat held (AC1)', async () => {
    await page.goto(`${BASE}/app/admin/sessions/${sid}`);
    const first = page.locator(`[data-testid=waitlist-row][data-booking-id="${sc.b.bookingId}"][data-offer-state=OFFERED]`);
    await first.waitFor();
    expect((await first.getByTestId('waitlist-offer-state').innerText()).startsWith('Offered, expires'), 'offered label');
    expect((await page.getByTestId('available').innerText()).trim() === '0', 'seat held: available stays 0');
    expect((await page.getByTestId('offered').innerText()).trim() === '1', 'one seat held for offers');
    const s = await api(`/api/v2/sessions/${sid}`);
    expect(s.seatsAvailable === 0 && s.offeredSeats === 1, `API seatsAvailable ${s.seatsAvailable} offeredSeats ${s.offeredSeats}`);
    await shot('03-admin-position-1-offered');
  });

  await step('4 candidate B sees the pending offer with its expiry', async () => {
    await page.goto(`${BASE}/app/my-bookings?candidateNumber=${sc.b.candidateNumber}`);
    const card = page.locator(`[data-testid=offer-card][data-booking-id="${sc.b.bookingId}"]`);
    await card.waitFor();
    expect((await card.getByTestId('offer-expires').innerText()).length > 0, 'expiry shown');
    await shot('04-candidate-b-offer');
  });

  await step('5 candidate B accepts and is BOOKED (AC2)', async () => {
    await page.locator(`[data-testid=offer-card][data-booking-id="${sc.b.bookingId}"]`).getByTestId('offer-accept').click();
    await page.locator(`[data-testid=booking-row][data-booking-id="${sc.b.bookingId}"][data-status=BOOKED]`).waitFor();
    expect(await page.getByTestId('offer-card').count() === 0, 'offer card gone');
    await shot('05-candidate-b-booked');
  });

  await step('6 admin waitlist shows positions 2-3 shifted to 1-2', async () => {
    await page.goto(`${BASE}/app/admin/sessions/${sid}`);
    await page.locator(`[data-testid=waitlist-row][data-booking-id="${sc.d.bookingId}"]`).waitFor();
    const wl = await api(`/api/v2/sessions/${sid}/waitlist`);
    expect(wl.length === 2 && wl[0].bookingId === sc.c.bookingId && wl[0].position === 1
      && wl[1].bookingId === sc.d.bookingId && wl[1].position === 2, `waitlist ${JSON.stringify(wl.map((w) => [w.position, w.bookingId]))}`);
    expect(wl.every((w) => w.offerState === 'WAITING'), 'nobody else offered: no seat free');
    expect(await page.getByTestId('waitlist-row').count() === 2, 'two rows in the drawer');
    expect((await page.getByTestId('available').innerText()).trim() === '0', 'session full again');
    await shot('06-admin-waitlist-compacted');
  });
} catch {
  exitCode = 1;
} finally {
  await browser.close();
  console.log(`\n${steps.join('\n')}\n${steps.filter((s) => s.startsWith('PASS')).length}/${TOTAL} steps passed`);
  process.exit(exitCode);
}
