// Headless browser verification of MBA-3826 (session roster CSV export). Usage:
//   BASE_URL=http://localhost:8080 AD_SA_PASSWORD=... node run.mjs
// Needs the portal on BASE_URL and sqlcmd on PATH (or SQLCMD) against a seeded AssessmentDelivery DB.
// Screenshots and the downloaded CSV go to ./screenshots/. Exits non-zero on the first failed step.
import { chromium } from 'playwright';
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const SQL_SERVER = process.env.SQL_SERVER ?? 'localhost,1433';
const SQLCMD = process.env.SQLCMD ?? 'sqlcmd';
const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(HERE, 'screenshots');
mkdirSync(OUT, { recursive: true });

const HEADER = ['CandidateNumber', 'LastName', 'FirstName', 'Email', 'BookingStatus', 'AccommodationCode', 'Channel', 'BookedAt', 'WaitlistPosition'];
// fixture.sql rows in roster order: booked group by name, then waitlist group by name; Baker (CANCELLED) left out
const EXPECTED = [
  ['NW90000002', 'Abbott', 'Ann', 'ann.abbott@example.com', 'ATTENDED', '', 'PARTNER', '2027-05-02 11:15', ''],
  ['NW90000001', "O'Neil", '"Jo"', 'jo.oneil@example.com', 'BOOKED', '', 'PORTAL', '2027-05-01 10:00', ''],
  ['NW90000003', 'Zimmer', 'Zoe', 'zoe.zimmer@example.com', 'BOOKED', 'ET25', 'IMPORT', '2027-05-03 12:30', ''],
  ['NW90000006', 'Carter', 'Cy', 'cy.carter@example.com', 'WAITLISTED', 'RB', 'PARTNER', '2027-05-05 08:05', '1'],
  ['NW90000005', 'Young', 'Yan', 'yan.young@example.com', 'WAITLISTED', '', 'PORTAL', '2027-05-05 16:20', '2'],
];

const sqlcmd = (args) => execFileSync(SQLCMD,
  ['-S', SQL_SERVER, '-U', process.env.SQL_USER ?? 'sa', '-C', '-b', '-d', 'AssessmentDelivery', '-h', '-1', '-W', ...args],
  { encoding: 'utf8', env: { ...process.env, SQLCMDPASSWORD: process.env.AD_SA_PASSWORD ?? 'Str0ng!Passw0rd' } })
  .split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
const query = (q) => sqlcmd(['-Q', `SET NOCOUNT ON; ${q}`]);
const expect = (cond, msg) => { if (!cond) throw new Error(`Assertion failed: ${msg}`); };
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/** AuditLog count/max id and usp_* execution counts - must not move during the export */
function dbSnapshot() {
  const [audit] = query("SELECT 'AUDIT|' + CAST(COUNT_BIG(*) AS VARCHAR(20)) + '|' + CAST(ISNULL(MAX(AuditId), 0) AS VARCHAR(20)) FROM dbo.AuditLog");
  const procs = query(`SELECT 'PROC|' + OBJECT_NAME(object_id, database_id) + '|' + CAST(SUM(execution_count) AS VARCHAR(20))
      FROM sys.dm_exec_procedure_stats WHERE database_id = DB_ID('AssessmentDelivery') AND OBJECT_NAME(object_id, database_id) LIKE 'usp[_]%'
     GROUP BY object_id, database_id ORDER BY 1`).filter((l) => l.startsWith('PROC|'));
  return { audit, procs };
}

/** Plain RFC 4180 reader, independent of the portal's writer */
function parseCsv(text) {
  const rows = [];
  let row = [], field = '', quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') { field += '"'; i++; }
      else if (c === '"') quoted = false;
      else field += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { row.push(field); field = ''; }
    else if (c === '\r' || c === '\n') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field); rows.push(row); row = []; field = '';
    } else field += c;
  }
  if (field !== '' || row.length) { row.push(field); rows.push(row); }
  return rows;
}

const escapeHtml = (s) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

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
const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1360, height: 900 } });
const page = await context.newPage();
let exitCode = 0;

try {
  let fx, before, csvText, csvFile;

  await step('1 apply the roster fixture (direct inserts, no procs)', async () => {
    const line = sqlcmd(['-i', path.join(HERE, 'fixture.sql')]).find((l) => l.startsWith('FIXTURE|'));
    expect(line, 'fixture.sql printed no FIXTURE line');
    const [, sessionId, centerId, centerCode, examCode] = line.split('|');
    fx = { sessionId: Number(sessionId), centerId: Number(centerId), centerCode, examCode };
    const counts = Object.fromEntries(query(`SELECT Status + '|' + CAST(COUNT(*) AS VARCHAR(10)) FROM dbo.Booking WHERE SessionId = ${fx.sessionId} GROUP BY Status`)
      .map((l) => l.split('|')).map(([s, n]) => [s, Number(n)]));
    fx.expectedRows = (counts.BOOKED ?? 0) + (counts.ATTENDED ?? 0) + (counts.WAITLISTED ?? 0);
    expect(counts.BOOKED && counts.ATTENDED && counts.CANCELLED && counts.WAITLISTED, `all four statuses present: ${JSON.stringify(counts)}`);
    console.log(`Fixture: session ${fx.sessionId} centre ${fx.centerCode} (#${fx.centerId}) exam ${fx.examCode} bookings ${JSON.stringify(counts)}`);
  });

  await step('2 snapshot AuditLog and usp_* execution counts', async () => {
    before = dbSnapshot();
    console.log(`  ${before.audit}; ${before.procs.join(', ') || 'no usp_* plans cached'}`);
  });

  const rowOf = () => page.locator('table.nw-grid tbody tr')
    .filter({ has: page.locator('td:first-child', { hasText: new RegExp(`^#${fx.sessionId}$`) }) });

  await step('3 "Download roster (CSV)" link on /Admin/Sessions?centerId=', async () => {
    await page.goto(`${BASE}/Admin/Sessions?centerId=${fx.centerId}`);
    const row = rowOf();
    expect(await row.count() === 1, `one row for session #${fx.sessionId}`);
    const link = row.getByRole('link', { name: 'Download roster (CSV)' });
    expect(await link.count() === 1, 'roster link on the fixture row');
    const href = await link.getAttribute('href');
    expect(href === `/Admin/Sessions/Roster/${fx.sessionId}?format=csv`, `href ${href}`);
    await row.scrollIntoViewIfNeeded();
    await row.evaluate((tr) => { tr.style.outline = '3px solid #e8a33d'; });
    await link.hover();
    await page.screenshot({ path: path.join(OUT, '01-sessions-roster-link.png'), fullPage: true });
    await page.screenshot({ path: path.join(OUT, '01b-sessions-roster-link-viewport.png') });
  });

  await step('4 download the CSV and check name, BOM, header, rows, waitlist and O\'Neil', async () => {
    const [download] = await Promise.all([page.waitForEvent('download'), rowOf().getByRole('link', { name: 'Download roster (CSV)' }).click()]);
    const name = download.suggestedFilename();
    expect(new RegExp(`^roster-${fx.centerCode}-${fx.examCode}-\\d{8}-\\d{4}\\.csv$`).test(name), `file name ${name}`);
    csvFile = path.join(OUT, name);
    await download.saveAs(csvFile);
    const bytes = readFileSync(csvFile);
    expect(bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf, 'UTF-8 BOM');
    csvText = bytes.subarray(3).toString('utf8');
    expect(csvText.endsWith('\r\n') && !/[^\r]\n/.test(csvText), 'CRLF line endings');
    const rows = parseCsv(csvText);
    expect(same(rows[0], HEADER), `header ${rows[0]}`);
    const data = rows.slice(1);
    expect(data.length === fx.expectedRows, `rows ${data.length} vs BOOKED+ATTENDED+WAITLISTED ${fx.expectedRows}`);
    expect(!data.some((r) => r[4] === 'CANCELLED'), 'no CANCELLED rows');
    expect(data.filter((r) => r[4] === 'WAITLISTED').map((r) => `${r[1]}:${r[8]}`).join() === 'Carter:1,Young:2', 'waitlist positions');
    const oneil = data.find((r) => r[0] === 'NW90000001');
    expect(oneil && oneil[1] === "O'Neil" && oneil[2] === '"Jo"', `O'Neil row parses back: ${JSON.stringify(oneil)}`);
    expect(csvText.includes(`NW90000001,O'Neil,"""Jo""",`), 'O\'Neil row escaped per RFC 4180');
    expect(same(data, EXPECTED), `rows/order\n${JSON.stringify(data, null, 1)}`);
    console.log(`  ${name}: ${data.length} rows`);
  });

  await step('5 unknown session -> 404 without a file; unknown format -> 400', async () => {
    const r = await fetch(`${BASE}/Admin/Sessions/Roster/99999999?format=csv`, { redirect: 'manual' });
    expect(r.status === 404, `status ${r.status}`);
    expect(!r.headers.get('content-disposition'), `content-disposition ${r.headers.get('content-disposition')}`);
    const bad = await fetch(`${BASE}/Admin/Sessions/Roster/${fx.sessionId}?format=xlsx`, { redirect: 'manual' });
    expect(bad.status === 400 && !bad.headers.get('content-disposition'), `format=xlsx -> ${bad.status}`);
  });

  await step('6 nothing written: AuditLog and usp_* counts unchanged', async () => {
    const after = dbSnapshot();
    expect(after.audit === before.audit, `AuditLog ${before.audit} -> ${after.audit}`);
    expect(same(after.procs, before.procs), `procs ${before.procs.join(', ')} -> ${after.procs.join(', ')}`);
    console.log(`  ${after.audit}; procs unchanged (${after.procs.length} cached)`);
  });

  // render the downloaded file for the evidence screenshot (raw text + parsed grid)
  const rows = parseCsv(csvText);
  await page.setViewportSize({ width: 1360, height: 700 });
  await page.setContent(`<html><body style="font-family:Segoe UI,Arial,sans-serif;margin:24px">
    <h3 style="margin:0 0 8px">${escapeHtml(path.basename(csvFile))}</h3>
    <div style="color:#555;margin-bottom:6px">Raw file (UTF-8 with BOM, CRLF)</div>
    <pre style="background:#f6f8fa;border:1px solid #d0d7de;padding:12px;font-size:13px">${escapeHtml(csvText)}</pre>
    <div style="color:#555;margin:14px 0 6px">Parsed (RFC 4180)</div>
    <table style="border-collapse:collapse;font-size:13px">${rows.map((r, i) => `<tr>${r.map((c) =>
      `<${i ? 'td' : 'th'} style="border:1px solid #ccc;padding:4px 8px;text-align:left;${i ? '' : 'background:#eef1f5'}">${escapeHtml(c)}</${i ? 'td' : 'th'}>`).join('')}</tr>`).join('')}</table>
  </body></html>`);
  await page.screenshot({ path: path.join(OUT, '02-roster-csv.png'), fullPage: true });
} catch {
  exitCode = 1;
} finally {
  await browser.close();
  console.log(`\n${steps.join('\n')}\n${steps.filter((s) => s.startsWith('PASS')).length}/6 steps passed`);
  process.exit(exitCode);
}
