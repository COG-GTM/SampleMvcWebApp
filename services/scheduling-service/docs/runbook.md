# scheduling-service runbook

Canonical operability runbook for [`services/scheduling-service`](../README.md) (port 8090, own `Scheduling`
database, single writer of bookings since MBA-3823). Owner: `scheduling-service-oncall`. SLOs, alert thresholds and
escalation are in [docs/oncall/scheduling-service.md](../../../docs/oncall/scheduling-service.md); this page is the
"what do I look at and what do I do" part. Pointer for tooling that expects the repo-wide path:
[docs/runbooks/scheduling-service.md](../../../docs/runbooks/scheduling-service.md).

## 1. Is it healthy? (first 2 minutes)

| Check | Command | Healthy |
|---|---|---|
| Liveness (JVM up, no DB involved) | `curl -si localhost:8090/actuator/health/liveness` | `200 {"status":"UP"}` |
| Readiness (can take traffic) | `curl -s localhost:8090/actuator/health/readiness \| jq` | `200`, `UP`; components `readinessState`, `schedulingDb`, `legacySource` all `UP` |
| Metrics scrape | `curl -s localhost:8090/actuator/prometheus \| grep ^scheduling_` | booking, error and outbox series present |
| Through the gateway | `curl -si localhost:8000/api/v2/centers \| head -1` | `200` |

Readiness answers **503** while any of these is DOWN, liveness stays **UP** (an orchestrator must not restart the
JVM because a database is down):

- `schedulingDb` - `SELECT 1` on the `Scheduling` database and Flyway history at the latest classpath migration
  (`details.flywayVersion`); DOWN with `applied=<v>, expected=<v>` while migrations have not finished.
- `legacySource` - `SELECT 1` through the read-only `ad_sched_import` pool used by `JdbcLegacyReferenceSource`.
- `readinessState` - the application has finished starting.

Every probe has a JDBC query timeout and a wall-clock timeout (`SCHEDULING_HEALTH_TIMEOUT_MS`, default 2000 ms), so
readiness answers within about 2 s even when SQL Server hangs; `details.error` says why (e.g.
`no answer within 2000 ms`, `SQLServerException: Login failed ...`, `earlier probes are still blocked`).

## 2. Metrics and dashboard queries

All series carry `application="scheduling-service"`. `centerCode` is the center the operation resolved (the
session's center; for reschedule, the center the booking moves away from) or `unknown` when it could not be resolved
(e.g. booking/session not found), so cardinality is bounded by the number of centers.

| Metric | Type | Labels | Meaning |
|---|---|---|---|
| `scheduling_booking_duration_seconds` | histogram (`_bucket`, `_count`, `_sum`, buckets 10 ms .. 10 s) | `operation`=`book`\|`cancel`\|`reschedule`\|`capacity`, `outcome`=`success`\|`business_error`\|`error`, `centerCode` | wall time of `SchedulingService.book/cancel/reschedule/changeCapacity` incl. commit |
| `scheduling_booking_errors_total` | counter | `operation`, `outcome` (`business_error`\|`error`), `centerCode` | non-successful operations |
| `scheduling_outbox_pending` | gauge | - | `outbox_event` rows with `relayed_at IS NULL` |
| `scheduling_outbox_lag_seconds` | gauge | - | age of the oldest pending event, 0 when none |
| `scheduling_outbox_refresh_failures_total` | counter | - | failed refreshes of the two outbox gauges (they keep their last value) |

`business_error` = the request was refused by a rule or referenced something that does not exist
(`SchedulingRuleException` -> 409/400, `NotFoundException` -> 404, validation). `error` = anything else (database,
bug) -> 5xx. The outbox gauges are refreshed every `SCHEDULING_OUTBOX_METRICS_REFRESH_MS` (5 s) by a background
query; a scrape never touches the database, so `/actuator/prometheus` keeps answering during a DB outage (watch
`scheduling_outbox_refresh_failures_total` then - the gauges are stale).

Dashboard panels (PromQL):

```promql
# Throughput by operation and outcome
sum by (operation, outcome) (rate(scheduling_booking_duration_seconds_count[5m]))

# p95 / p99 latency by operation (SLO: book p95 < 300 ms)
histogram_quantile(0.95, sum by (le, operation) (rate(scheduling_booking_duration_seconds_bucket{outcome="success"}[5m])))
histogram_quantile(0.99, sum by (le, operation) (rate(scheduling_booking_duration_seconds_bucket[5m])))

# p95 booking latency per center (find the hot center)
histogram_quantile(0.95, sum by (le, centerCode) (rate(scheduling_booking_duration_seconds_bucket{operation="book"}[5m])))

# Share of book calls under 250 ms (SLO burn)
sum(rate(scheduling_booking_duration_seconds_bucket{operation="book",le="0.25"}[30m]))
  / sum(rate(scheduling_booking_duration_seconds_count{operation="book"}[30m]))

# Error ratio by outcome (technical errors should be ~0; business errors follow demand)
sum by (outcome) (rate(scheduling_booking_errors_total[5m]))
  / ignoring(outcome) group_left sum(rate(scheduling_booking_duration_seconds_count[5m]))

# Technical errors by operation and center
sum by (operation, centerCode) (increase(scheduling_booking_errors_total{outcome="error"}[15m]))

# Capacity changes rejected (replaces scheduling_service.capacity_change.rejected)
sum(rate(scheduling_booking_errors_total{operation="capacity",outcome="business_error"}[15m]))
  / sum(rate(scheduling_booking_duration_seconds_count{operation="capacity"}[15m]))

# Outbox backlog and lag
scheduling_outbox_pending
scheduling_outbox_lag_seconds
increase(scheduling_outbox_refresh_failures_total[10m])

# Readiness (needs the blackbox / kube probe metrics of your platform), or simply:
up{job="scheduling-service"}
```

Suggested alerts: `book` p95 > 0.3 s for 10 m; `outcome="error"` ratio > 1 % for 5 m;
`scheduling_outbox_lag_seconds > 60` for 5 m; any `scheduling_outbox_refresh_failures_total` increase for 10 m;
readiness 503 for 2 m.

## 3. Logs

stdout is one JSON object per line (logstash encoder): `@timestamp`, `level`, `logger_name`, `thread_name`,
`message`, `service`, and, when known, `traceId`, `centerCode`, `sessionId`, `bookingId`, `candidateNumber`.
`traceId` is the trace id of a valid W3C `traceparent` header, else a safe `X-Request-Id`, else generated; it is
echoed as `X-Request-Id` on every response, so a user-reported request id finds its log lines. Every `/api` and
`/internal` request ends with a `request completed` line with `method`, `path`, `status`, `durationMs`.

```bash
# everything for one request / one booking / one center
jq -c 'select(.traceId=="<id>")'          .run/scheduling-service.log
jq -c 'select(.bookingId=="50000012")'    .run/scheduling-service.log
jq -c 'select(.centerCode=="BOS01" and .level!="INFO")' .run/scheduling-service.log
# slow requests
jq -c 'select(.message=="request completed" and .durationMs>300) | {traceId,path,status,durationMs}' .run/scheduling-service.log
```

No names, e-mail addresses or dates of birth are logged; `candidateNumber` is the only candidate identifier.
For readable local logs start with `SPRING_PROFILES_ACTIVE=plain-logs`.

## 4. Playbooks

### High booking latency

1. Which operation and center: p95 by `operation`, then by `centerCode` (queries above). One center -> usually a
   hot session (`lockForUpdate` on `exam_session` serializes bookings of the same session); all centers -> the
   database.
2. Slow request lines: `jq` query above; pick a `traceId` and read its lines.
3. Database: readiness `schedulingDb` details; blocking sessions
   `SELECT session_id, blocking_session_id, wait_type, wait_time, command FROM sys.dm_exec_requests WHERE database_id = DB_ID('Scheduling') AND blocking_session_id <> 0;`
   and the Hikari pool (`hikaricp_connections_pending`, `hikaricp_connections_active` on the same scrape).
4. Outbox: `scheduling_outbox_pending` growing at the same time points to the relay competing for the database
   (see "Outbox lag").
5. If it is one modern center and not fixable quickly: roll that center back to legacy (section 5).

### Error spikes

1. `outcome`: `business_error` spikes follow demand or a rule change (409 `SESSION_FULL`, `DUPLICATE_BOOKING`,
   `CAPACITY_BELOW_ACCOMMODATION_FLOOR`, 404s from stale clients) - compare with ADR 0002 and
   `scheduling.rules.*`; `error` means 5xx.
2. `error` by `operation` / `centerCode`; then logs `jq -c 'select(.level=="ERROR")'` - `Database error` lines carry
   the `traceId`, `centerCode`, `sessionId` and `bookingId` of the failed call.
3. Readiness DOWN at the same time -> database outage: SQL Server container/host, `Scheduling` DB state, login.
   The service stays live and recovers by itself when the database is back (pool reconnects); do not restart it in a
   loop.
4. Errors limited to modern centers and impacting candidates -> roll the centers back (section 5) while fixing
   forward.

### Outbox lag

The relay (`relay` package, `RelayScheduler` every `SCHEDULING_RELAY_POLL_MS`) projects `outbox_event` rows into
AssessmentDelivery as `ad_sched_relay`.

1. `scheduling_outbox_lag_seconds` rising with `scheduling_outbox_pending` > 0:
   `curl -s localhost:8090/internal/relay/status | jq` (pending, last event / last relayed event, p95/max lag).
2. Relay enabled? `SCHEDULING_RELAY_ENABLED=true`; log line `outbox relay enabled: ...` at startup; `relay: applied=...`
   lines while it works.
3. A poison event: `SELECT TOP 20 event_id, event_type, session_id, booking_id, attempts, last_error FROM outbox_event WHERE relayed_at IS NULL ORDER BY event_id;`
   `relay: event <id> (<type>) failed, will retry` log lines show the error. Relay order is strict, so one failing
   event blocks the ones after it. Fix the cause (legacy login/grants `db/security/040_single_writer.sql`, legacy
   DB down) and drain once with `curl -X POST localhost:8090/internal/relay/run`.
4. While events are pending, legacy channels see stale seats for those sessions; if lag stays > 5 min for a center,
   roll it back only after the backlog is drained (legacy must see every modern booking first).

### Non-zero reconciliation report

`java -jar services/scheduling-service/target/scheduling-service.jar reconcile --wait-seconds=30` (exit code 0 only
with 0 mismatches and 0 seat-drift rows) or `curl -s 'localhost:8090/internal/reconciliation?writeReport=true' | jq`.
Reports are written to `RECONCILIATION_REPORT_DIR` (default `reports/`) as `reconciliation-<timestamp>.md`
(`ReconcileCommand`, `ReconciliationService`).

1. Re-run with `--wait-seconds=30` first: mismatches on sessions with pending outbox events disappear once the relay
   catches up (check "Relay" section of the report / `scheduling_outbox_pending`).
2. `session missing/present` rows: a session exists on one side only - a new legacy session not imported yet
   (`POST /internal/import/sessions[?centerCode=]` or restart with `SCHEDULING_IMPORT_ON_STARTUP=true`) or a session created outside the
   service.
3. Count fields (`Capacity`, `SeatsAvailable`, `BOOKED`, `ATTENDED`, `WAITLISTED`, `AccommodationSeatsUsed`) or
   booking `status/session/accommodation` differences with no pending events: something wrote to AssessmentDelivery
   besides the relay - check the legacy channels did not touch a modern center (gateway `route=legacy center=<code>`
   lines for allow-listed centers) and that the single-writer DENYs are in place (`db/security/040_single_writer.sql`).
   Seat-drift rows are the HB-01 / HB-02 patterns in `docs/as-is/hidden-behavior.md`.
4. Scheduling is the source of truth for allow-listed centers; never "fix" by editing the `Scheduling` database to
   match legacy. Attach the report to the incident and fix forward (or roll the center back after the relay has
   drained, section 5).

## 5. Rollback: move a center back to legacy (routing allow-list)

No deploy, no restart. Full details in
[docs/oncall/scheduling-service.md "Rollback: routing allow-list"](../../../docs/oncall/scheduling-service.md#rollback-routing-allow-list).

1. Make sure `scheduling_outbox_pending` is 0 (or the center's events are relayed) so legacy has every modern booking.
2. Edit `services/routing-gateway/config/routing.properties` and remove the center from `routing.modern-centers`
   (e.g. `routing.modern-centers=BOS01,SEA01` -> `routing.modern-centers=SEA01`).
3. The gateway re-reads the file within `routing.watch-interval` (1 s), or `curl -X POST localhost:8000/actuator/refresh`.
4. Verify: `curl -si 'localhost:8000/api/v2/centers/BOS01/sessions' | grep -i x-route` -> `X-Route: legacy`; gateway
   log `route=legacy center=BOS01`; scheduling-service booking rate for `centerCode="BOS01"` drops to ~0 (bookings
   with ids >= 50,000,000 stay on modern for view/cancel/reschedule).
5. Re-add the center the same way once fixed; run reconciliation before and after.

## 6. Configuration

| Setting | Env | Default |
|---|---|---|
| Readiness probe timeout | `SCHEDULING_HEALTH_TIMEOUT_MS` | `2000` |
| Outbox gauge refresh | `SCHEDULING_OUTBOX_METRICS_REFRESH_MS` | `5000` |
| Plain-text logs | `SPRING_PROFILES_ACTIVE=plain-logs` | JSON |
| Exposed actuator endpoints | `management.endpoints.web.exposure.include` | `health,info,prometheus` |
