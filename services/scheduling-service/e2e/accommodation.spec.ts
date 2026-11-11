import { expect, test, type Page } from '@playwright/test';

// MBA-3830 browser acceptance. Seed facts (db/seed): SFO01 session 333 (MHL-RN1) has AccommodationCapacity 2 with one
// ET booking, i.e. exactly one accommodation seat left; NW00000963 (profile ET25), NW00000903 and NW00000983 (ET50)
// have no bookings.
const CENTER = 'SFO01';
const EXAM = 'MHL-RN1';
const SESSION = 333;
const OTHER = 154;
const ET25_CANDIDATE = 'NW00000963';
const ET50_CANDIDATE = 'NW00000903';
const shot = (page: Page, name: string) => page.screenshot({ path: `screenshots/${name}.png`, fullPage: true });

const firstNumber = (page: Page, sessionId: number, testId: string) =>
  page.getByTestId(`session-card-${sessionId}`).getByTestId(testId).evaluate((el) => Number(el.firstChild?.textContent));

async function search(page: Page, candidate: string) {
  await page.goto('/app');
  await page.getByTestId('find-candidate-input').fill(candidate);
  await page.getByTestId('center-select').selectOption(CENTER);
  await page.getByTestId('exam-select').selectOption(EXAM);
  await expect(page.getByTestId(`session-card-${SESSION}`)).toBeVisible();
}

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ request }) => {
  const r = await request.post('/api/v2/internal/import/sessions?mode=reset');
  expect(r.ok()).toBeTruthy();
});

test('ET25 candidate sees accommodation seats, gets ET25 pre-filled and books the last accommodation seat', async ({ page }) => {
  await search(page, ET25_CANDIDATE);
  await expect(page.getByTestId('find-accommodation-select')).toHaveValue('ET25');
  await expect(page.getByTestId('find-accommodation-default')).toContainText('ET25');
  expect(await firstNumber(page, SESSION, 'acc-left')).toBe(1);
  const seatsBefore = await firstNumber(page, SESSION, 'seats-left');
  await expect(page.getByTestId(`book-${SESSION}`)).toHaveText('Book a seat');
  await shot(page, 'a5-01-et25-search-one-accommodation-seat-left');

  await page.getByTestId(`book-${SESSION}`).click();
  await expect(page.getByTestId('accommodation-select')).toHaveValue('ET25');
  await page.getByTestId('candidate-input').fill(ET25_CANDIDATE);
  await shot(page, 'a5-02-et25-book-form-prefilled');
  await page.getByTestId('confirm-book').click();
  await expect(page.getByTestId('toast-success')).toContainText('Booked');

  await search(page, ET25_CANDIDATE);
  expect(await firstNumber(page, SESSION, 'acc-left')).toBe(0);
  expect(await firstNumber(page, SESSION, 'seats-left')).toBe(seatsBefore - 1);
});

test('a second ET candidate sees the session unavailable; the pre-filled code stays editable', async ({ page }) => {
  await search(page, ET50_CANDIDATE);
  await expect(page.getByTestId('find-accommodation-select')).toHaveValue('ET50');
  const card = page.getByTestId(`session-card-${SESSION}`);
  await expect(card).toHaveAttribute('data-unavailable', 'true');
  await expect(page.getByTestId(`acc-unavailable-${SESSION}`)).toBeVisible();
  await expect(page.getByTestId(`book-${SESSION}`)).toHaveText('Unavailable');
  await expect(page.getByTestId(`book-${SESSION}`)).toHaveAttribute('aria-disabled', 'true');
  await expect(page.getByTestId(`book-${OTHER}`)).toHaveText(/Book a seat|Join waitlist/);
  await shot(page, 'a5-03-second-et-candidate-sees-unavailable');

  await page.getByTestId('find-accommodation-select').selectOption('SR');
  await expect(card).not.toHaveAttribute('data-unavailable', 'true');
  await expect(page.getByTestId(`book-${SESSION}`)).toHaveText(/Book a seat|Join waitlist/);
});

test('POST /api/v2/bookings for another ET seat returns 409 NO_ACCOMMODATION_SEAT without consuming a seat', async ({ request }) => {
  const before = await (await request.get(`/api/v2/sessions/${SESSION}`)).json();
  const r = await request.post('/api/v2/bookings', { data: { sessionId: SESSION, candidateNumber: ET50_CANDIDATE, accommodationCode: 'ET50' } });
  expect(r.status()).toBe(409);
  expect(await r.json()).toMatchObject({ error: 'NO_ACCOMMODATION_SEAT', legacyCode: 50003 });
  const after = await (await request.get(`/api/v2/sessions/${SESSION}`)).json();
  expect(after.seatsAvailable).toBe(before.seatsAvailable);
  expect(after.accommodationAvailable).toBe(0);
});
