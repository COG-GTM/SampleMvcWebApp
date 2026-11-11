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

### Outbox lag (relay, MBA-3823)

Every booking/capacity change writes an `outbox_event` row in the same transaction (`event_seq` = order). The relay
(`com.northwind.scheduling.relay`, login `ad_sched_relay`, `scheduling.relay.*` / `SCHEDULING_RELAY_*`) polls every
`scheduling.relay.poll-ms` (250 ms) and applies each event to `AssessmentDelivery` in one transaction, recording the
event id in `dbo.RelayCheckpoint` ([ADR 0003](../adr/0003-single-writer-outbox-relay.md)). Gauges:
`scheduling_outbox_pending`, `scheduling_outbox_lag_seconds`; `GET /internal/relay` shows
`total/pending/lastSeq/lastRelayedSeq/oldestPendingAt` plus applied/skipped counters.

- Lag rising with pending rising: the relay is stuck or `AssessmentDelivery` is down/locked. Check the relay WARN logs
  (`relay poll failed`, `relay event ... failed`) and SQL Server.
- Lag rising with pending flat: one poison event. `SELECT TOP 5 event_seq, event_type, attempts, last_error FROM
  outbox_event WHERE relayed_at IS NULL ORDER BY event_seq` in `Scheduling`. Fix the cause and let it retry; never
  delete outbox rows.
- Restarting the service (or killing it mid-batch) is safe: an event already checkpointed is skipped, so replays write
  nothing twice.
- While lag is high, legacy readers (portal pages, booking-api `GET`, results) show stale availability. Writes are not
  affected (scheduling-service is the only writer).

### Candidate notifications (MBA-3831)

Capacity changes emit one `CandidateCapacityImpactNotified` outbox event per affected candidate
(`services/scheduling-service/docs/events.md`). The relay delivers them to the notification stub instead of legacy, in
the same `event_seq` order, so they count towards outbox pending/lag like any other event and never touch
`AssessmentDelivery` or reconciliation. Check what a candidate was told:
`curl -s 'localhost:8090/internal/notifications?candidateNumber=NW00000909'` (direct to :8090; the gateway returns 404),
or search the logs for `"event":"CandidateCapacityImpactNotified"`. The stub keeps them in memory only (lost on restart);
there is no email/SMS yet.

### Waitlist offers stuck or not made (MBA-3829)

A seat freed in an `OPEN` session is offered to the next eligible waitlist entry and held while the offer is
`PENDING` ([ADR 0005](../adr/0005-waitlist-auto-offer.md)). Config: `scheduling.waitlist.offer-ttl`
(`SCHEDULING_WAITLIST_OFFER_TTL`, default `PT24H`), `scheduling.waitlist.expiry-enabled` (`..._EXPIRY_ENABLED`,
default `true`), `scheduling.waitlist.expiry-poll` (`..._EXPIRY_POLL`, default `PT1M`).

- Live offers: `SELECT offer_id, session_id, booking_id, position, offered_at, expires_at FROM seat_offer WHERE status =
  'PENDING' ORDER BY expires_at` in `Scheduling`. `GET /api/v2/sessions/{id}` shows `heldSeats`; the waitlist endpoint
  shows `offerStatus` per entry.
- Offers past `expires_at` still `PENDING`: the expiry job is off or failing. Check `expiry-enabled` and WARN logs
  `offer expiry failed`. Accepting such an offer already answers `409 OFFER_NOT_PENDING`, so nobody gets an expired
  seat; the job releases it on its next pass.
- Seat free but nobody offered: the waitlist only holds ET25/ET50 entries and no accommodation seat is free (they are
  skipped, by design), or the session is not `OPEN`.
- Offers do not change legacy until accepted: the offered booking stays `WAITLISTED` in `dbo.Booking`, and
  `SeatsAvailable` counts the held seat as free there. Reconciliation is expected to stay at 0 during offers.

### Non-zero reconciliation report

`GET /internal/reconciliation` (or `java -jar target/scheduling-service-*.jar reconcile`, exit 0 only when
clean) compares every session (Capacity, SeatsAvailable, BOOKED/ATTENDED/WAITLISTED, accommodation
seats used) and every booking (session, status, accommodation code) between `Scheduling` and `AssessmentDelivery`,
runs `db/checks/seat-drift.sql`, and writes `reports/reconciliation-<timestamp>.md`. It replaces capacity-sync's
nightly recompute, which is disabled (`capacity.sync.enabled=false`, HB-03, MR-17).

With the relay live and the legacy write denial in place (`db/security/040_single_writer.sql`) the expected result is
0 mismatches and 0 drift rows. If not:

1. Check relay lag first (above). Mismatches that disappear once `pending` is 0 are lag, not drift.
2. A legacy-only booking or a SeatsAvailable/count difference after the relay caught up means something wrote
   `AssessmentDelivery` outside the relay: check that `040_single_writer.sql` is applied
   (`SELECT * FROM sys.database_permissions WHERE state = 'D'`) and that no component still uses `sa` or `ad_app`.
   Legacy writers otherwise reintroduce HB-01..HB-05 drift ([MR-01, MR-02](../as-is/migration-risks.md)).
3. A modern-only booking: the relay has not applied it yet or failed on it (see poison event above).
4. Do not hand-edit either database; keep the report and escalate to the scheduling owners.

## 5. Roll a centre back to legacy (routing allow-list, M3)

> **Single writer (MBA-3823).** Once `db/security/040_single_writer.sql` is applied, routing a centre back to legacy
> only moves its *reads*: legacy booking/capacity writes stay denied for every centre, so the steps below do not restore
> legacy writes on their own. To hand writes back to legacy use §6 (it is all-or-nothing, not per centre).
>
> **M5 (MBA-3824).** The portal booking/capacity pages, the four booking procs and booking-api's write SQL are deleted
> ([ADR 0004](../adr/0004-retire-portal-booking-slice.md)); the gateway redirects the old portal URLs to `/app` for every
> centre. There is no legacy write path left to roll back to; restoring one means reverting the MBA-3824 change.

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

## 6. Single-writer cutover and rollback (MBA-3823)

> Attendance credential. `POST /api/v2/bookings/{id}/attendance` answers 401 unless `X-Service-Token` equals
> `scheduling.attendance.service-token` (`SCHEDULING_ATTENDANCE_SERVICE_TOKEN`; unset = all refused). results-service must send the
> same value (`SCHEDULING_SERVICE_TOKEN`). If they differ, result ingestion fails with 502 `SCHEDULING_UNAVAILABLE`; check with
> `curl -i -X POST -H "X-Service-Token: $SCHEDULING_SERVICE_TOKEN" http://localhost:8090/api/v2/bookings/0/attendance`
> (401 = mismatch, 404 `BOOKING_NOT_FOUND` = token accepted; booking 0 never exists, so nothing is written). The gateway returns 404 for attendance on :8000, so results-service calls :8090 directly.

Cutover (order matters: bookings legacy takes between the snapshot and the denial would be missing from `Scheduling`,
because the importer only copies bookings when the `Scheduling` DB has no sessions or on a RESET import):

1. Apply `db/security/036_component_logins.sql` and `db/security/040_single_writer.sql` (legacy writes now fail with a
   permission error; booking-api answers `403 LEGACY_WRITE_DENIED`).
2. Re-import with the legacy estate quiesced: drop/recreate the `Scheduling` DB (or run the RESET import) and start
   scheduling-service with the relay on. The import emits `InventoryBaselined`, which the relay projects once.
3. `GET /internal/reconciliation` must report 0 mismatches and 0 seat-drift rows.
4. Only then set `routing.coexistence-mode=relay` and `routing.modern-centers=*` in the gateway.

Rollback to legacy writes (all centres at once):

1. Set `routing.modern-centers=` (empty) in the gateway so no new centre-routed writes reach scheduling-service; wait
   for `GET /internal/relay` `pending` = 0 so every modern change is in `AssessmentDelivery` (same `BookingId`s).
2. Revoke the denial: for each legacy login, `REVOKE INSERT, UPDATE, DELETE ON dbo.Booking / dbo.Waitlist`,
   `REVOKE UPDATE ON dbo.ExamSession (Capacity, SeatsAvailable, AccommodationCapacity)` and
   `REVOKE EXECUTE` on the four booking procedures (the inverse of `040_single_writer.sql`).
3. Hand attendance back: set `results.scheduling.base-url=` (empty) in results-service and restart it, so
   `AttendanceRecorder` uses the legacy direct `UPDATE` again (it needs step 2).
4. Hand the modern-owned ids back: the gateway routes any `BookingId >= routing.modern-booking-id-floor` to
   scheduling-service regardless of the allow-list, so raise the floor above every id in use (e.g.
   `routing.modern-booking-id-floor=9223372036854775807`); those bookings are then served by legacy, which already holds
   them from the relay. The floor is read at gateway startup (only `routing.modern-centers` hot-reloads), so restart
   the gateway after editing it. Verify `GET :8000/api/v1/bookings/<a 5000xxxx id>` answers with `X-Route: legacy`.
5. Only now stop scheduling-service and, if seat recomputation is wanted again, set `capacity.sync.enabled=true` in
   capacity-sync. Legacy drift (HB-01..HB-05) returns with the legacy writers.
6. Before routing writes to modern again, restore `routing.modern-booking-id-floor=50000000` (gateway restart) and
   repeat the cutover from step 2: `Scheduling` must be re-imported.
