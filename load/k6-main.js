// Load test for SampleWebApp through the load balancer.
// Env: BASE_URL (default http://lb), HEALTH_PATH, VUS, DURATION, P95_MAX_MS, P99_MAX_MS, ERR_RATE_MAX, OUT (summary json path), LABEL
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://lb';
const LABEL = __ENV.LABEL || 'run';
const OUT = __ENV.OUT || `/out/${LABEL}.json`;

const routes = [
  { name: 'home',     path: '/' },
  { name: 'posts',    path: '/Posts' },
  { name: 'blogs',    path: '/Blogs' },
  { name: 'tags',     path: '/Tags' },
  { name: 'numposts', path: '/Posts/NumPosts' },
  { name: 'health',   path: __ENV.HEALTH_PATH || '/healthz' },  // nginx exposes /healthz; against the ALB use HEALTH_PATH=/Home/Health
];

const routeTrend = Object.fromEntries(routes.map(r => [r.name, new Trend(`route_${r.name}`, true)]));
const errors = new Rate('errors');

export const options = {
  scenarios: {
    steady: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 20),
      duration: __ENV.DURATION || '60s',
      gracefulStop: '5s',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  thresholds: {
    http_req_duration: [
      `p(95)<${__ENV.P95_MAX_MS || 1500}`,
      `p(99)<${__ENV.P99_MAX_MS || 3000}`,
    ],
    errors: [`rate<${__ENV.ERR_RATE_MAX || 0.01}`],
    checks: ['rate>0.99'],
  },
  tags: { service: 'samplewebapp', env: 'demo', label: LABEL },
};

export default function () {
  for (const r of routes) {
    group(r.name, () => {
      const res = http.get(`${BASE}${r.path}`, { tags: { route: r.name } });
      const ok = check(res, { 'status 200': (x) => x.status === 200 });
      errors.add(!ok);
      routeTrend[r.name].add(res.timings.duration);
    });
  }
  sleep(0.2);
}

export function handleSummary(data) {
  const m = data.metrics;
  const dur = m.http_req_duration.values;
  const perRoute = {};
  for (const r of routes) {
    const v = (m[`route_${r.name}`] || { values: {} }).values;
    perRoute[r.name] = { p50_ms: round(v.med), p95_ms: round(v['p(95)']), p99_ms: round(v['p(99)']), avg_ms: round(v.avg) };
  }
  const summary = {
    label: LABEL,
    generated_at: new Date().toISOString(),
    base_url: BASE,
    vus: Number(__ENV.VUS || 20),
    duration: __ENV.DURATION || '60s',
    requests: m.http_reqs.values.count,
    rps: round(m.http_reqs.values.rate),
    p50_ms: round(dur.med),
    p95_ms: round(dur['p(95)']),
    p99_ms: round(dur['p(99)']),
    avg_ms: round(dur.avg),
    max_ms: round(dur.max),
    error_rate: round(m.errors ? m.errors.values.rate : 0, 4),
    failed_requests: m.http_req_failed ? m.http_req_failed.values.passes : 0,
    thresholds: Object.fromEntries(Object.entries(m)
      .filter(([, v]) => v.thresholds)
      .map(([k, v]) => [k, Object.fromEntries(Object.entries(v.thresholds).map(([t, s]) => [t, s.ok ? 'pass' : 'FAIL']))])),
    per_route: perRoute,
  };
  const out = {};
  out[OUT] = JSON.stringify(summary, null, 2) + '\n';
  out.stdout = textSummary(summary);
  return out;
}

function round(x, d = 1) { return x === undefined ? null : Number(Number(x).toFixed(d)); }

function textSummary(s) {
  const lines = [
    '',
    `== k6 summary [${s.label}] ${s.base_url} (${s.vus} VUs, ${s.duration}) ==`,
    `requests=${s.requests} rps=${s.rps} error_rate=${s.error_rate}`,
    `latency ms: p50=${s.p50_ms} p95=${s.p95_ms} p99=${s.p99_ms} avg=${s.avg_ms} max=${s.max_ms}`,
    'thresholds: ' + JSON.stringify(s.thresholds),
    '',
  ];
  return lines.join('\n');
}
