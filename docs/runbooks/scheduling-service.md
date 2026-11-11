# Runbook: scheduling-service

On-call guide for `services/scheduling-service` (Java 21 / Spring Boot 3, port 8090, own `Scheduling` DB, read-only
snapshot of `AssessmentDelivery` through login `ad_sched_import`). Legacy behaviour referenced below is documented in
[`docs/as-is/`](../as-is/README.md); this runbook does not re-derive it.

Start/stop locally: [`scripts/run-scheduling-service.sh`](../../scripts/run-scheduling-service.sh) (`start|status|stop|e2e`,
log in `.run/scheduling-service.log`).

## 1. Is it healthy? (first 2 minutes)

| Check | Command | Healthy |
| --- | --- | --- |
| Liveness (JVM is serving) | `curl -s localhost:8090/actuator/health/liveness` | `200 {"status":"UP"}` |
| Readiness (can take bookings) | `curl -s localhost:8090/actuator/health/readiness` | `200 {"status":"UP",...}` |
| Per-component readiness | `curl -s localhost:8090/actuator/health/readiness \| jq .components` | every component `UP` |
| Metrics scrape | `curl -s localhost:8090/actuator/prometheus \| grep ^scheduling_booking` | samples present after the first booking |

Readiness components (`management.endpoint.health.group.readiness.include`, override with
`SCHEDULING_READINESS_INCLUDE`):

| Component | DOWN means | First action |
| --- | --- | --- |
| `db` | `Scheduling` DB unreachable (Hikari connection timeout `SCHEDULING_DB_CONNECTION_TIMEOUT_MS`, default 5 s) | §4 |
| `schedulingSchema` | Flyway has not finished, or migrations are pending | §4 |
| `legacySource` | `AssessmentDelivery` read-only source (`ad_sched_import`) unreachable; snapshot import/reference reads fail | §4 |
| `readinessState` | Spring application not yet (or no longer) accepting traffic, e.g. during start-up/shutdown | wait / restart |

Liveness only contains `livenessState`, so a dependency outage takes the instance **out of rotation (readiness 503)
without restarting it** (liveness stays 200). Do not wire `db` into liveness. Component details are hidden by default;
set `SCHEDULING_HEALTH_SHOW_DETAILS=always` on an internal-only instance to see error messages.

## 2. Metrics and dashboard queries

Scraped from `GET /actuator/prometheus`. All series also carry `application="scheduling-service"`.

| Metric | Type | Tags |
| --- | --- | --- |
| `scheduling_booking_duration_seconds` | histogram (`_bucket`, `_count`, `_sum`; buckets 25 ms .. 10 s) | `operation=book\|cancel\|reschedule\|capacity`, `outcome=success\|business_error\|error`, `centerCode` |
| `scheduling_booking_errors_total` | counter | same + `code` (`ErrorCode` name for `business_error`, exception class for `error`) |
| `http_server_requests_seconds` | Spring MVC timer | `uri`, `method`, `status`, `outcome` |
| `hikaricp_connections_active` / `_pending` / `_timeout_total` | Hikari pool | `pool` |
| `scheduling_outbox_lag_seconds`, `scheduling_outbox_pending` | gauges | **after M4** (outbox not in this service yet) |

`outcome=business_error` is an expected rule rejection (session full/closed, duplicate booking, retake window, invalid
capacity ... see [ADR 0002](../adr/0002-ported-rule-decisions.md)); `outcome=error` is an unexpected failure (DB, bug).
`centerCode="unknown"` means the centre could not be resolved (unknown id or DB down).

Dashboard panels (PromQL):

```promql
# p95 / p99 latency per operation
histogram_quantile(0.95, sum by (le, operation) (rate(scheduling_booking_duration_seconds_bucket[5m])))
histogram_quantile(0.99, sum by (le, operation) (rate(scheduling_booking_duration_seconds_bucket[5m])))

# throughput per operation and outcome
sum by (operation, outcome) (rate(scheduling_booking_duration_seconds_count[5m]))

# unexpected error ratio (page on this)
sum(rate(scheduling_booking_errors_total{outcome="error"}[5m]))
  / sum(rate(scheduling_booking_duration_seconds_count[5m]))

# business rejections by code and centre (investigate spikes, do not page)
sum by (code, centerCode) (rate(scheduling_booking_errors_total{outcome="business_error"}[15m]))

# worst centres by p95 booking latency
topk(5, histogram_quantile(0.95, sum by (le, centerCode) (rate(scheduling_booking_duration_seconds_bucket{operation="book"}[15m]))))

# readiness (Prometheus blackbox or k8s): kube_pod_status_ready{container="scheduling-service"}

# after M4
max(scheduling_outbox_lag_seconds)
max(scheduling_outbox_pending)
```

Suggested alerts: error ratio > 1 % for 5 m (page); p95 `book` > 1 s for 10 m (page); readiness 503 for > 2 m (page);
`business_error` rate 3x the 7-day baseline for one centre (ticket); after M4, outbox lag > 60 s for 5 m (page).

## 3. Logs

Logs are one JSON object per line (logstash encoder, `logback-spring.xml`; `SPRING_PROFILES_ACTIVE=plain-logs` for
human-readable local output). Fields: `@timestamp`, `level`, `logger_name`, `message`, `traceId`, `spanId`, `service`,
and where known `centerCode`, `sessionId`, `bookingId`, `candidateNumber`. Booking command lines (logger
`com.northwind.scheduling.BookingOperations`) add `operation`, `outcome`, `code`, `durationMs`, `newBookingId`; request
lines (logger `com.northwind.scheduling.Requests`) add `method`, `path`, `status`, `durationMs`. Only an allow-list of
MDC keys is emitted, and no candidate PII other than `candidateNumber` is logged (no names, e-mail, DOB, request bodies or
query strings). Every `/api` response carries `X-Trace-Id`; ask the caller for it.

```bash
# everything about one request / booking / session / centre
jq -c 'select(.traceId=="<id>")'            .run/scheduling-service.log
jq -c 'select(.bookingId=="1234")'          .run/scheduling-service.log
jq -c 'select(.sessionId=="154")'           .run/scheduling-service.log
# unexpected failures with stack traces
jq -c 'select(.outcome=="error" or .level=="ERROR") | {ts:."@timestamp",operation,code,centerCode,traceId,stack_trace}' .run/scheduling-service.log
# slowest booking commands
jq -c 'select(.operation) | {durationMs,operation,centerCode,sessionId,traceId}' .run/scheduling-service.log | sort -t: -k2 -n -r | head
```

## 4. Scenarios

### High booking latency (p95 `book` > 1 s)

1. Scope it: all operations or one (`operation`), all centres or one (`centerCode`)? Compare with
   `http_server_requests_seconds` to see if time is spent in the command or around it.
2. Pool saturation: `hikaricp_connections_pending > 0` or rising `hikaricp_connections_timeout_total` means the
   `Scheduling` DB is slow or the pool is too small.
3. Lock contention: every command takes an `UPDLOCK` on its session row (reschedule locks two, in id order), so a burst on
   one popular session serialises. Look for one `sessionId` dominating the slow log lines. In SQL Server:
   `SELECT * FROM sys.dm_exec_requests WHERE blocking_session_id <> 0;` against the `Scheduling` DB.
4. If one centre is affected and the cause is not quickly fixable, roll that centre back to legacy (§5).

### Error spike

1. Split by outcome. `business_error` only: usually demand (sessions filling, retake window) or a client sending bad
   data; check `code` and `centerCode`, compare with legacy rejection behaviour in `docs/as-is/` before treating as a
   bug. No page unless a centre cannot book at all.
2. `outcome="error"`: check readiness components (§1) and the `code` tag (exception class). Pull stack traces with the
   `jq` query in §3 and correlate by `traceId`.
3. `db` DOWN: check the SQL Server container/host (`docker ps`, `docker logs mssql`), credentials
   (`SCHEDULING_DB_USER`/`SCHEDULING_DB_PASSWORD`) and network. The instance recovers by itself once the DB is back;
   readiness returns to UP within one probe.
4. `schedulingSchema` DOWN: start-up migration failed or is still running; read the Flyway lines in the start-up log.
   Never edit an applied migration; fix forward with the next `V<n>__` file.
5. `legacySource` DOWN: `ad_sched_import` login/permissions (`db/security/035_sched_import_login.sql`) or the legacy DB
   is down. Bookings already in `Scheduling` keep working; snapshot refresh (`POST /api/v2/internal/import/sessions`)
   and reference reads that need legacy fail until it is back.

### Outbox lag (after M4)

Not applicable until M4 adds the outbox and the `scheduling_outbox_lag_seconds` / `scheduling_outbox_pending` gauges.
Then: lag rising with pending rising means the relay is stuck or the legacy write-back target is down (check its
readiness and relay ERROR logs by `traceId`); lag rising with pending flat means one poison message (find the oldest
pending row and its error). While lag is high, legacy channels see stale availability: consider rolling the affected
centres back (§5) until the relay catches up.

### Non-zero reconciliation report

The legacy estate has no single seat formula and several writers drift `ExamSession.SeatsAvailable`
([MR-01, MR-02](../as-is/migration-risks.md), HB-01..HB-05, nightly capacity-sync masking partner drift MR-17), so a
difference between modern and legacy availability is expected for some sessions and is not by itself an incident.

1. Run the legacy drift check: `sqlcmd -C -S localhost -U sa -d AssessmentDelivery -i db/checks/seat-drift.sql` and
   compare the listed sessions with `GET /api/v2/sessions/{id}` (modern availability is derived from bookings,
   [ADR 0002](../adr/0002-ported-rule-decisions.md)).
2. Sessions that differ only by legacy counter drift: record them, no action on the modern side.
3. Bookings present on one side only, or a session over capacity in `Scheduling`: stop new modern bookings for that
   centre (roll back, §5), keep the evidence (`bookingId`, `traceId` from the logs), and escalate to the scheduling
   owners. Do not hand-edit `Scheduling` rows.
4. For a centre that is not (yet) served by modern, refresh its snapshot with
   `curl -X POST 'localhost:8090/api/v2/internal/import/sessions?mode=merge'` (never `mode=reset` in production: it
   re-copies the inventory).

## 5. Roll a centre back to legacy (routing allow-list, M3)

Which backend serves a centre is decided by the routing allow-list `scheduling.routing.modern-centers`
(env `SCHEDULING_ROUTING_MODERN_CENTERS`, comma-separated centre codes, `*` = all; everything else goes to
`SCHEDULING_ROUTING_DEFAULT_TARGET`, default `legacy`). The M3 routing layer consults it per request.

> The allow-list only takes traffic away from this service where the M3 routing layer is deployed in front of it:
> scheduling-service's own `/api/v1` and `/api/v2` controllers do not check it and keep accepting bookings from any
> caller that reaches them directly. Before relying on these steps, confirm that portal/partner traffic for the centre
> goes through the gateway (and that nothing calls :8090 directly); otherwise also stop or re-point those callers.

1. Check current routing: `curl -s 'localhost:8090/api/v2/internal/routing/resolve?centerCode=SFO01'`
   -> `{"centerCode":"SFO01","target":"modern",...}`.
2. Remove the centre from the list (e.g. `SFO01,AMS01` -> `AMS01`; replace `*` by the explicit list minus the centre)
   and restart/redeploy the instance (`scripts/run-scheduling-service.sh stop && SKIP_BUILD=1 scripts/run-scheduling-service.sh start`
   locally). An empty list sends every centre to legacy.
3. Verify: `routing/resolve` returns `"target":"legacy"`, and `rate(scheduling_booking_duration_seconds_count{centerCode="SFO01"}[5m])`
   drops to 0 while legacy portal/booking-api traffic for the centre resumes.
4. Bookings taken by modern for that centre stay in `Scheduling`; reconcile them (§4) before routing the centre back.
