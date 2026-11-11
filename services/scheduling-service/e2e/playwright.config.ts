import { defineConfig, devices } from '@playwright/test';

// Steps 1-7 of the MBA-3821 browser acceptance criteria, against a running service (scripts/run-scheduling-service.sh).
export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    baseURL: process.env.SCHEDULING_URL ?? 'http://localhost:8090',
    headless: true,
    viewport: { width: 1366, height: 900 },
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1366, height: 900 } } }],
});
