import { expect, test, type Page } from '@playwright/test';

// Seed facts (characterization/README.md): SFO01 sessions 154 and 333 are open MHL-RN1 sessions with free seats;
// BOS01 session 153 is a full BCB-AGL session with 3 waitlisted bookings; NW00000990 has no bookings.
const CENTER = 'SFO01';
const EXAM = 'MHL-RN1';
const SESSION = 154;
const TARGET = 333;
const CANDIDATE = 'NW00000990';
const shot = (page: Page, name: string) => page.screenshot({ path: `screenshots/${name}.png`, fullPage: true });

async function seatsLeft(page: Page, sessionId: number) {
  return Number(await page.getByTestId(`session-card-${sessionId}`).getByTestId('seats-left').evaluate((el) => el.firstChild?.textContent));
}

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ request }) => {
  // Restore the Scheduling DB inventory to the imported seed (no writes to AssessmentDelivery).
  const r = await request.post('/api/v2/internal/import/sessions?mode=reset');
  expect(r.ok()).toBeTruthy();
});

test('steps 1-5: find, book, my bookings, reschedule, cancel', async ({ page }) => {
  // 1. choose a centre and find a session
  await page.goto('/app');
  await page.getByTestId('center-select').selectOption(CENTER);
  await page.getByTestId('exam-select').selectOption(EXAM);
  await expect(page.getByTestId(`session-card-${SESSION}`)).toBeVisible();
  const before = await seatsLeft(page, SESSION);
  await shot(page, '01-find-sessions');

  // 2. book a seat and see seats left decrease by exactly one
  await page.getByTestId(`book-${SESSION}`).click();
  await page.getByTestId('confirm-book').click();
  await expect(page.getByTestId('candidate-error')).toBeVisible(); // inline validation
  await page.getByTestId('candidate-input').fill(CANDIDATE);
  await shot(page, '02-book-form');
  await page.getByTestId('confirm-book').click();
  await expect(page.getByTestId('toast-success')).toContainText('Booked');
  await page.goto(`/app/?center=${CENTER}&exam=${EXAM}`);
  await expect(page.getByTestId(`session-card-${SESSION}`)).toBeVisible();
  expect(await seatsLeft(page, SESSION)).toBe(before - 1);
  await shot(page, '02-seats-decreased');

  // 3. the booking appears under My bookings
  await page.getByRole('link', { name: 'My bookings' }).click();
  const list = page.getByTestId('booking-list');
  await expect(list.locator('li').first()).toContainText(`Session #${SESSION}`);
  await expect(list.locator('li').first()).toContainText('BOOKED');
  await shot(page, '03-my-bookings');

  // 4. reschedule to another session of the same exam
  await list.locator('li').first().getByRole('link', { name: 'Reschedule' }).click();
  await page.getByTestId(`target-${TARGET}`).check();
  await shot(page, '04-reschedule');
  await page.getByTestId('confirm-reschedule').click();
  await expect(page.getByTestId('toast-success')).toContainText(`Moved to session #${TARGET}`);
  await expect(list.locator('li').first()).toContainText(`Session #${TARGET}`);
  await expect(list.locator('li').first()).toContainText('BOOKED');
  await expect(list.locator('li').nth(1)).toContainText('CANCELLED');
  await shot(page, '04-rescheduled');

  // 5. cancel it
  await list.locator('li').first().getByRole('button', { name: 'Cancel' }).click();
  await page.getByTestId('confirm-cancel').click();
  await expect(page.getByTestId('toast-success')).toContainText('cancelled');
  await expect(list.locator('li').first()).toContainText('CANCELLED');
  await expect(list.getByRole('button', { name: 'Cancel' })).toHaveCount(0);
  await shot(page, '05-cancelled');
});

test('steps 6-7: admin lowers and raises capacity, opens the waitlist drawer', async ({ page }) => {
  // 6. lower then raise capacity inline, with confirmation
  await page.goto(`/app/admin/centers/${CENTER}`);
  const cap = page.getByTestId(`capacity-${SESSION}`);
  const original = Number(await cap.textContent());
  const lowered = original - 5;
  await shot(page, '06-admin-grid');
  for (const value of [lowered, original]) {
    await page.getByTestId(`capacity-edit-${SESSION}`).click();
    await page.getByTestId(`capacity-input-${SESSION}`).fill(String(value));
    await page.getByTestId(`capacity-save-${SESSION}`).click();
    await expect(page.getByRole('dialog', { name: 'Confirm capacity change' })).toBeVisible();
    if (value === lowered) await shot(page, '06-capacity-confirm');
    await page.getByTestId('confirm-capacity').click();
    await expect(page.getByTestId(`capacity-${SESSION}`)).toHaveText(String(value));
    const booked = Number(await page.getByTestId(`booked-${SESSION}`).textContent());
    await expect(page.getByTestId(`available-${SESSION}`)).toHaveText(String(Math.max(0, value - booked)));
    if (value === lowered) await shot(page, '06-capacity-lowered');
  }
  await shot(page, '06-capacity-raised');

  // 7. waitlist drawer of a full session
  await page.goto('/app/admin/centers/BOS01');
  await expect(page.getByTestId('available-153')).toHaveText('0');
  await page.getByTestId('waitlist-153').click();
  const drawer = page.getByTestId('drawer');
  await expect(drawer).toBeVisible();
  await expect(drawer.getByTestId('waitlist-row')).toHaveCount(3);
  await expect(drawer.getByTestId('waitlist-row').first()).toContainText('NW');
  await shot(page, '07-waitlist-drawer');
});
