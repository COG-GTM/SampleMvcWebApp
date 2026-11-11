// Headless browser verification of MBA-3821 steps 1-7. Usage:
//   BASE_URL=http://localhost:8090 CENTER_CODE=BOS01 node run.mjs
// Screenshots go to ./screenshots/. Exits non-zero on the first failed step.
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';

const BASE = process.env.BASE_URL ?? 'http://localhost:8090';
const CENTER = process.env.CENTER_CODE ?? 'BOS01';
const OUT = new URL('./screenshots/', import.meta.url).pathname;
mkdirSync(OUT, { recursive: true });

const today = new Date().toISOString().slice(0, 10);
const api = async (path, init) => {
  const r = await fetch(BASE + path, init);
  if (!r.ok) throw new Error(`${init?.method ?? 'GET'} ${path} -> ${r.status} ${await r.text()}`);
  return r.json();
};
const expect = (cond, msg) => { if (!cond) throw new Error(`Assertion failed: ${msg}`); };

/** Picks two open sessions of one exam (A, B) with free seats and a candidate who may book both. */
async function pickScenario() {
  const sessions = (await api(`/api/v2/centers/${CENTER}/sessions?from=${today}`))
    .filter((s) => s.status === 'OPEN' && s.seatsAvailable >= 2 && s.capacity - 1 >= s.accommodationCapacity);
  const byExam = sessions.reduce((m, s) => ({ ...m, [s.examCode]: [...(m[s.examCode] ?? []), s] }), {});
  const offset = Number(process.env.CANDIDATE_OFFSET ?? Math.floor(Math.random() * 80));
  for (let i = 0; i < 100; i++) {
    const candidateNumber = `NW${String(900 + ((offset + i) % 100) + 1).padStart(8, '0')}`;
    const active = (await api(`/api/v2/candidates/${candidateNumber}/bookings`))
      .filter((b) => b.status === 'BOOKED' || b.status === 'WAITLISTED');
    for (const [examCode, list] of Object.entries(byExam)) {
      if (list.length < 2 || active.some((b) => b.examCode === examCode)) continue;
      const [a, b] = list;
      const elig = await api(`/api/v2/candidates/${candidateNumber}/eligibility?examCode=${examCode}&date=${a.startsAt.slice(0, 10)}`);
      if (elig.eligible) return { candidateNumber, examCode, a, b };
    }
  }
  throw new Error('No bookable scenario found');
}

async function findWaitlistedSession() {
  for (const c of await api('/api/v2/centers')) {
    const s = (await api(`/api/v2/centers/${c.centerCode}/sessions?from=${today}`)).find((x) => x.waitlistCount > 0);
    if (s) return s;
  }
  throw new Error('No upcoming session with a waitlist');
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
const seatsOf = async (loc) => Number((await loc.innerText()).match(/\d+/)?.[0]);
let exitCode = 0;

try {
  const sc = await pickScenario();
  console.log(`Scenario: ${sc.candidateNumber} ${sc.examCode} sessions ${sc.a.sessionId} -> ${sc.b.sessionId}`);
  let newBookingId;

  await step('1 choose a center and find a session', async () => {
    await page.goto(`${BASE}/app`);
    await page.getByTestId('center-select').selectOption(CENTER);
    await page.getByTestId('exam-select').selectOption(sc.examCode);
    await page.locator(`[data-testid=session-card][data-session-id="${sc.a.sessionId}"]`).waitFor();
    await shot('01-find-sessions');
  });

  await step('2 book a seat and see seats left decrease', async () => {
    await page.locator(`[data-testid=session-card][data-session-id="${sc.a.sessionId}"] [data-testid=book-link]`).click();
    const before = await seatsOf(page.getByTestId('summary-seats-left'));
    await page.getByTestId('candidate-input').fill(sc.candidateNumber);
    await page.getByTestId('eligibility').waitFor();
    await page.getByTestId('accommodation-select').selectOption('');
    await page.getByTestId('book-submit').click();
    await page.getByTestId('booking-confirmation').waitFor();
    const after = await seatsOf(page.getByTestId('summary-seats-left'));
    expect(after === before - 1, `seats left ${before} -> ${after}`);
    const fresh = await api(`/api/v2/sessions/${sc.a.sessionId}`);
    expect(fresh.seatsAvailable === before - 1, `API seatsAvailable ${fresh.seatsAvailable}`);
    await shot('02-booked-seats-decreased');
  });

  await step('3 see the booking under My bookings', async () => {
    await page.getByTestId('go-my-bookings').click();
    const row = page.locator(`[data-testid=booking-row][data-status=BOOKED]`).filter({ hasText: `session ${sc.a.sessionId}` });
    await row.waitFor();
    await shot('03-my-bookings');
  });

  await step('4 reschedule it to another session of the same exam', async () => {
    await page.locator(`[data-testid=booking-row][data-status=BOOKED]`).filter({ hasText: `session ${sc.a.sessionId}` })
      .getByTestId('reschedule-link').click();
    await page.locator(`[data-testid=reschedule-option][data-session-id="${sc.b.sessionId}"]`).click();
    await shot('04a-reschedule-confirm');
    await page.getByTestId('reschedule-confirm').click();
    const row = page.locator(`[data-testid=booking-row][data-status=BOOKED]`).filter({ hasText: `session ${sc.b.sessionId}` });
    await row.waitFor();
    newBookingId = Number(await row.getAttribute('data-booking-id'));
    expect(newBookingId >= 50_000_000, `modern booking id ${newBookingId}`);
    await page.locator(`[data-testid=booking-row][data-status=CANCELLED]`).filter({ hasText: `session ${sc.a.sessionId}` }).waitFor();
    await shot('04b-rescheduled');
  });

  await step('5 cancel it', async () => {
    await page.locator(`[data-testid=booking-row][data-booking-id="${newBookingId}"]`).getByTestId('cancel-button').click();
    await page.getByTestId('cancel-confirm').click();
    await page.locator(`[data-testid=booking-row][data-booking-id="${newBookingId}"][data-status=CANCELLED]`).waitFor();
    await shot('05-cancelled');
  });

  await step('6 admin lowers and raises capacity', async () => {
    await page.goto(`${BASE}/app/admin/centers/${CENTER}?examCode=${sc.examCode}`);
    const row = page.locator(`[data-testid=session-row][data-session-id="${sc.a.sessionId}"]`);
    await row.waitFor();
    const original = (await api(`/api/v2/sessions/${sc.a.sessionId}`)).capacity;
    for (const [target, label] of [[original - 1, '06a-capacity-lowered'], [original, '06b-capacity-raised']]) {
      await row.getByTestId('capacity-edit').click();
      await row.getByTestId('capacity-input').fill(String(target));
      await row.getByTestId('capacity-save').click();
      if (label === '06a-capacity-lowered') await shot('06-capacity-confirm-dialog');
      await page.getByTestId('capacity-confirm').click();
      await row.getByTestId('capacity-edit').filter({ hasText: String(target) }).waitFor();
      expect((await api(`/api/v2/sessions/${sc.a.sessionId}`)).capacity === target, `capacity ${target}`);
      await shot(label);
    }
  });

  await step('7 open the waitlist drawer of a full session', async () => {
    const s = await findWaitlistedSession();
    await page.goto(`${BASE}/app/admin/centers/${s.centerCode}?from=${s.startsAt.slice(0, 10)}`);
    await page.locator(`[data-testid=session-row][data-session-id="${s.sessionId}"] [data-testid=waitlist-button]`).click();
    await page.getByTestId('waitlist-row').first().waitFor();
    const rows = await page.getByTestId('waitlist-row').count();
    expect(rows === s.waitlistCount, `drawer rows ${rows} vs waitlistCount ${s.waitlistCount}`);
    await page.waitForTimeout(400);
    await shot('07-waitlist-drawer');
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(`${BASE}/app?centerCode=${CENTER}&examCode=${sc.examCode}`);
  await page.getByTestId('session-card').first().waitFor();
  await shot('08-mobile-find-sessions');
} catch {
  exitCode = 1;
} finally {
  await browser.close();
  console.log(`\n${steps.join('\n')}\n${steps.filter((s) => s.startsWith('PASS')).length}/7 steps passed`);
  process.exit(exitCode);
}
