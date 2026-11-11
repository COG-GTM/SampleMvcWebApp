// Functional test of MBA-3832 (operability) against the running jar. Usage (dev only - stops/starts SQL Server):
//   scripts/run-scheduling-service.sh start
//   node services/scheduling-service/e2e/operability.mjs
// Env: BASE_URL (http://localhost:8090), CENTER_CODE (BOS01), LOG_FILE (<repo>/.run/scheduling-service.log),
//      MSSQL_CONTAINER (mssql), SKIP_DB_STOP=1 to leave the database alone.
// Steps: health groups UP -> booking traffic (book, duplicate 409, reschedule, cancel, capacity, 404) with trace
// headers -> Prometheus series -> JSON log lines with trace context and no PII -> DB container stopped: readiness
// 503 / liveness UP / scrape still 200 -> DB started: readiness back to 200. Exits non-zero on the first failure.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { randomBytes } from 'node:crypto';

const BASE = process.env.BASE_URL ?? 'http://localhost:8090';
const CENTER = process.env.CENTER_CODE ?? 'BOS01';
const LOG_FILE = process.env.LOG_FILE ?? new URL('../../../.run/scheduling-service.log', import.meta.url).pathname;
const CONTAINER = process.env.MSSQL_CONTAINER ?? 'mssql';
const today = new Date().toISOString().slice(0, 10);
const runId = `func-${Date.now().toString(36)}`;

const expect = (cond, msg) => { if (!cond) throw new Error(`Assertion failed: ${msg}`); };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const call = async (method, path, body, headers = {}) => {
  const r = await fetch(BASE + path, {
    method,
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(15000),
  });
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* prometheus text */ }
  return { status: r.status, headers: r.headers, text, json };
};
const ok = async (method, path, body, headers) => {
  const r = await call(method, path, body, headers);
  if (r.status >= 300) throw new Error(`${method} ${path} -> ${r.status} ${r.text}`);
  return r;
};
const waitFor = async (what, timeoutMs, fn) => {
  const t = Date.now();
  let last;
  while (Date.now() - t < timeoutMs) {
    try { if (await fn()) return Date.now() - t; } catch (e) { last = e; }
    await sleep(500);
  }
  throw new Error(`timed out after ${timeoutMs} ms waiting for ${what}${last ? `: ${last.message}` : ''}`);
};

async function pickScenario() {
  const sessions = (await ok('GET', `/api/v2/centers/${CENTER}/sessions?from=${today}`)).json
    .filter((s) => s.status === 'OPEN' && s.seatsAvailable >= 3 && s.capacity - 1 >= s.accommodationCapacity);
  const byExam = sessions.reduce((m, s) => ({ ...m, [s.examCode]: [...(m[s.examCode] ?? []), s] }), {});
  const offset = Math.floor(Math.random() * 80);
  for (let i = 0; i < 100; i++) {
    const candidateNumber = `NW${String(900 + ((offset + i) % 100) + 1).padStart(8, '0')}`;
    const active = (await ok('GET', `/api/v2/candidates/${candidateNumber}/bookings`)).json
      .filter((b) => b.status === 'BOOKED' || b.status === 'WAITLISTED');
    for (const [examCode, list] of Object.entries(byExam)) {
      if (list.length < 2 || active.some((b) => b.examCode === examCode)) continue;
      const [a, b] = list;
      const elig = (await ok('GET', `/api/v2/candidates/${candidateNumber}/eligibility?examCode=${examCode}&date=${a.startsAt.slice(0, 10)}`)).json;
      if (elig.eligible) return { candidateNumber, a, b };
    }
  }
  throw new Error('No bookable scenario found');
}

const series = (scrape, metric, labels) => scrape.split('\n').filter((l) => l.startsWith(`${metric}{`)
  && Object.entries(labels).every(([k, v]) => l.includes(`${k}="${v}"`)));
const value = (line) => Number(line.slice(line.lastIndexOf(' ') + 1));

const results = [];
const step = async (name, fn) => {
  const t = Date.now();
  try {
    const detail = await fn();
    results.push(`PASS ${name} (${Date.now() - t} ms)${detail ? ` - ${detail}` : ''}`);
    console.log(results.at(-1));
  } catch (e) {
    console.error(`FAIL ${name}: ${e.message}`);
    console.log(`\n${results.length} passed, 1 failed`);
    process.exit(1);
  }
};

const logStart = existsSync(LOG_FILE) ? statSync(LOG_FILE).size : 0;
const traceId = randomBytes(16).toString('hex');
const ctx = {};

await step('liveness and readiness UP', async () => {
  const live = await call('GET', '/actuator/health/liveness');
  expect(live.status === 200 && live.json.status === 'UP', `liveness ${live.status} ${live.text}`);
  const ready = await call('GET', '/actuator/health/readiness');
  expect(ready.status === 200 && ready.json.status === 'UP', `readiness ${ready.status} ${ready.text}`);
  const c = ready.json.components;
  for (const k of ['readinessState', 'schedulingDb', 'legacySource']) expect(c[k]?.status === 'UP', `${k} UP: ${ready.text}`);
  expect(c.schedulingDb.details.flywayVersion, 'flywayVersion detail');
  return `components ${Object.keys(c).join(',')}, flywayVersion=${c.schedulingDb.details.flywayVersion}`;
});

await step('booking traffic incl. business errors', async () => {
  const { candidateNumber, a, b } = await pickScenario();
  Object.assign(ctx, { candidateNumber, a, b });
  const booked = await ok('POST', '/api/v2/bookings', { sessionId: a.sessionId, candidateNumber },
    { traceparent: `00-${traceId}-00f067aa0ba902b7-01` });
  expect(booked.status === 201, 'book 201');
  expect(booked.headers.get('x-request-id') === traceId, `X-Request-Id echoes the traceparent trace id, got ${booked.headers.get('x-request-id')}`);
  ctx.bookingId = booked.json.bookingId;
  const dup = await call('POST', '/api/v2/bookings', { sessionId: a.sessionId, candidateNumber }, { 'X-Request-Id': `${runId}-dup` });
  expect(dup.status === 409 && dup.headers.get('x-request-id') === `${runId}-dup`, `duplicate 409 + echoed X-Request-Id, got ${dup.status}`);
  await ok('POST', `/api/v2/bookings/${ctx.bookingId}/reschedule`, { newSessionId: b.sessionId }, { 'X-Request-Id': `${runId}-res` });
  await ok('DELETE', `/api/v2/bookings/${ctx.bookingId}?reason=functional-test`, null, { 'X-Request-Id': `${runId}-can` });
  const s = (await ok('GET', `/api/v2/sessions/${a.sessionId}`)).json;
  await ok('PUT', `/api/v2/sessions/${a.sessionId}/capacity`, { newCapacity: s.capacity + 1, actor: 'functional-test' });
  await ok('PUT', `/api/v2/sessions/${a.sessionId}/capacity`, { newCapacity: s.capacity, actor: 'functional-test' });
  const missing = await call('DELETE', '/api/v2/bookings/999999999');
  expect(missing.status === 404, `unknown booking 404, got ${missing.status}`);
  const generated = await call('GET', `/api/v2/sessions/${a.sessionId}`);
  expect(/^[0-9a-f]{32}$/.test(generated.headers.get('x-request-id') ?? ''), 'generated X-Request-Id');
  return `candidate ${candidateNumber}, booking ${ctx.bookingId}: session ${a.sessionId} -> ${b.sessionId}, center ${CENTER}`;
});

await step('Prometheus exposes booking and outbox series', async () => {
  let scrape = '';
  await waitFor('outbox gauges refreshed', 15000, async () => {
    scrape = (await ok('GET', '/actuator/prometheus')).text;
    return scrape.includes('scheduling_outbox_pending{') && scrape.includes('scheduling_outbox_lag_seconds{');
  });
  expect(scrape.includes('# TYPE scheduling_booking_duration_seconds histogram'), 'histogram type');
  for (const operation of ['book', 'cancel', 'reschedule', 'capacity']) {
    const lines = series(scrape, 'scheduling_booking_duration_seconds_bucket', { centerCode: CENTER, operation, outcome: 'success' });
    expect(lines.length > 1, `${operation} success buckets for ${CENTER}`);
  }
  const dupErr = series(scrape, 'scheduling_booking_errors_total', { centerCode: CENTER, operation: 'book', outcome: 'business_error' });
  expect(dupErr.length === 1 && value(dupErr[0]) >= 1, 'book business_error counter');
  const nf = series(scrape, 'scheduling_booking_errors_total', { centerCode: 'unknown', operation: 'cancel', outcome: 'business_error' });
  expect(nf.length === 1 && value(nf[0]) >= 1, 'cancel business_error counter tagged unknown');
  const pending = series(scrape, 'scheduling_outbox_pending', {})[0];
  const lag = series(scrape, 'scheduling_outbox_lag_seconds', {})[0];
  expect(Number.isFinite(value(pending)) && Number.isFinite(value(lag)), 'outbox gauge values');
  const centers = new Set(scrape.split('\n').filter((l) => l.startsWith('scheduling_booking_duration_seconds_count{'))
    .map((l) => /centerCode="([^"]*)"/.exec(l)[1]));
  return `${scrape.split('\n').filter((l) => l.startsWith('scheduling_')).length} scheduling_* samples; centerCode values {${[...centers].join(',')}}; `
    + `${dupErr[0]}; ${pending}; ${lag}`;
});

await step('stdout is JSON with trace context and no candidate PII', async () => {
  expect(existsSync(LOG_FILE), `log file ${LOG_FILE}`);
  await sleep(500);
  const lines = readFileSync(LOG_FILE, 'utf8').slice(logStart).split('\n').filter((l) => l.trim());
  const parsed = lines.map((l) => { try { return JSON.parse(l); } catch { return null; } });
  const bad = lines.filter((_, i) => parsed[i] === null);
  expect(bad.length === 0, `non-JSON stdout lines: ${bad.slice(0, 2).join(' | ')}`);
  const mine = parsed.filter((j) => j.traceId === traceId);
  const done = mine.find((j) => j.message === 'request completed');
  expect(done, `request completed line for traceId ${traceId}`);
  expect(done.centerCode === CENTER && done.sessionId === String(ctx.a.sessionId) && done.bookingId === String(ctx.bookingId)
    && done.candidateNumber === ctx.candidateNumber && done.status === 201, `context fields: ${JSON.stringify(done)}`);
  const res = parsed.find((j) => j.traceId === `${runId}-res` && j.message === 'request completed');
  expect(res?.bookingId === String(ctx.bookingId) && res?.centerCode === CENTER, `reschedule line: ${JSON.stringify(res)}`);
  const candidate = (await ok('GET', `/api/v2/candidates/${ctx.candidateNumber}`)).json;
  const pii = [candidate.firstName, candidate.lastName, candidate.email, candidate.dateOfBirth, candidate.fullName, candidate.name]
    .filter((v) => typeof v === 'string' && v.length > 2);
  const leaks = lines.filter((l) => pii.some((p) => l.includes(p)));
  expect(leaks.length === 0, `PII in logs: ${leaks[0]}`);
  const keys = new Set(parsed.flatMap((j) => Object.keys(j)));
  expect(!['email', 'firstName', 'lastName', 'dateOfBirth', 'candidateName'].some((k) => keys.has(k)), `PII keys: ${[...keys]}`);
  return `${lines.length} new lines, all JSON; sample: ${JSON.stringify({ ...done, '@timestamp': undefined, '@version': undefined, level_value: undefined, thread_name: undefined })}; `
    + `checked ${pii.length} candidate PII values -> 0 hits`;
});

if (process.env.SKIP_DB_STOP === '1') {
  results.push('SKIP database stop (SKIP_DB_STOP=1)');
} else {
  try {
    await step(`docker stop ${CONTAINER}: readiness 503, liveness UP, scrape still 200`, async () => {
      execFileSync('docker', ['stop', '-t', '5', CONTAINER], { stdio: 'pipe' });
      let down;
      const ms = await waitFor('readiness 503', 30000, async () => {
        down = await call('GET', '/actuator/health/readiness');
        return down.status === 503;
      });
      expect(down.json.status === 'DOWN' && down.json.components.schedulingDb.status === 'DOWN', `readiness body ${down.text}`);
      const t = Date.now();
      const live = await call('GET', '/actuator/health/liveness');
      expect(live.status === 200 && live.json.status === 'UP', `liveness ${live.status} ${live.text}`);
      const scrape = await call('GET', '/actuator/prometheus');
      expect(scrape.status === 200 && scrape.text.includes('scheduling_outbox_pending'), `scrape ${scrape.status}`);
      return `readiness 503 after ${ms} ms (schedulingDb: ${down.json.components.schedulingDb.details.error}; `
        + `legacySource ${down.json.components.legacySource.status}); liveness 200 UP in ${Date.now() - t} ms`;
    });
  } finally {
    execFileSync('docker', ['start', CONTAINER], { stdio: 'pipe' });
  }
  await step(`docker start ${CONTAINER}: readiness back to 200`, async () => {
    const ms = await waitFor('readiness 200', 180000, async () => (await call('GET', '/actuator/health/readiness')).status === 200);
    return `UP again ${ms} ms after start`;
  });
}

console.log(`\n${results.filter((r) => r.startsWith('PASS')).length} passed, 0 failed`);
