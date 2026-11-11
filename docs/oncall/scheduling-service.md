# On-call runbook: scheduling-service

Owner: `scheduling-service-oncall`. Code: [`services/scheduling-service`](../../services/scheduling-service/README.md)
(Java 21 / Spring Boot 3, port 8090, own `Scheduling` database). It sits behind the
[`routing-gateway`](../../services/routing-gateway/README.md) (port 8000), which sends booking and capacity traffic
for allow-listed test centers to scheduling-service and everything else to the legacy estate
(portal :8080, booking-api :8081). Rule decisions ported from the legacy procedures and triggers are in
[ADR 0002](../adr/0002-ported-rule-decisions.md).

Step-by-step playbooks, PromQL dashboard queries and health/readiness details are in the canonical runbook
[services/scheduling-service/docs/runbook.md](../../services/scheduling-service/docs/runbook.md).

## SLOs (28-day window)

| SLI | Objective | Measured from |
|---|---|---|
| `/api/v2` availability (non-5xx share of requests) | 99.9 % | gateway `route=modern` log lines, `status` field |
| Booking write latency, `POST /api/v2/bookings` | p95 < 300 ms | `scheduling_booking_duration_seconds{operation="book"}` (histogram) |
| Capacity change success, `PUT /api/v2/sessions/{id}/capacity` | >= 98 % not rejected with 409 (7-day baseline ~0.3 % rejected) | `scheduling_booking_errors_total{operation="capacity",outcome="business_error"}` / `scheduling_booking_duration_seconds_count{operation="capacity"}`; error code in the JSON log / response `error` |
| Routing resolve, `GET /internal/routing/resolve` | 99.95 % non-5xx, p99 < 100 ms | gateway; a failure makes writes fail closed with `503 ROUTING_UNAVAILABLE` |
| Seat consistency | 0 sessions where `seatsAvailable != max(0, capacity - (BOOKED + ATTENDED))` | `db/checks/seat-drift.sql` (legacy), API session reads (modern) |

## Alerts

| Alert | Condition | First check |
|---|---|---|
| Capacity change rejections | > 2 % of capacity changes rejected with `409` over 15 min | which `error` code: `CAPACITY_BELOW_ACCOMMODATION_FLOOR` (50005) vs `VALIDATION`; compare with the floor rule in ADR 0002 §5 and `scheduling.rules.accommodation-floor` |
| `/api/v2` 5xx rate | > 1 % over 5 min (`scheduling_booking_errors_total{outcome="error"}`) | `DB_ERROR` bodies -> `/actuator/health/readiness` (`schedulingDb`, `legacySource`); JVM up (`/actuator/health/liveness`) |
| Gateway `ROUTING_UNAVAILABLE` | any `503 ROUTING_UNAVAILABLE` for 5 min | scheduling-service reachable from the gateway; resolve endpoint latency |
| Booking latency | `scheduling_booking_duration_seconds` p95 > 300 ms for 10 min | DB locks on `exam_session` (`lockForUpdate`), slow queries |
| Outbox lag | `scheduling_outbox_lag_seconds` > 60 for 5 min | `/internal/relay/status`, failing event in `outbox_event.last_error` |
| Seat drift | seat-drift check returns rows | legacy channel or capacity-sync touching a modern-owned center |

## Dashboards and logs

- **scheduling-service overview** (scraped from `/actuator/prometheus`; queries in the
  [runbook](../../services/scheduling-service/docs/runbook.md#2-metrics-and-dashboard-queries)): rate and p95/p99 of
  `scheduling_booking_duration_seconds` by `operation` / `centerCode`, `scheduling_booking_errors_total` by `outcome`,
  `scheduling_outbox_pending` / `scheduling_outbox_lag_seconds`, JVM and connection pool (`hikaricp_*`).
- **routing-gateway**: `X-Route` split legacy vs modern per center, `reason`, 403 `CENTER_NOT_MODERN`, 503
  `ROUTING_UNAVAILABLE`, allow-list reloads.
- Logs: gateway logger `routing` (`route=<legacy|modern> center=<code> reason=... status=...`); scheduling-service
  writes JSON lines (logger `com.northwind.assessment.scheduling`) with `traceId` (also returned as `X-Request-Id`),
  `centerCode`, `sessionId`, `bookingId`, `candidateNumber`. Every capacity change is audited in the `capacity_change` table
  (old/new capacity, bumped booking ids, actor).

## Triage

1. Health: `curl -s localhost:8090/actuator/health/readiness` (503 + failing component when a database is down),
   `curl -s localhost:8090/actuator/health/liveness`, `curl -si localhost:8000/api/v2/centers | head -1`.
2. Reproduce the failing call directly and through the gateway, e.g.
   `curl -si -X PUT localhost:8090/api/v2/sessions/<id>/capacity -H 'Content-Type: application/json' -d '{"newCapacity":24,"actor":"oncall"}'`.
   Errors are `{"error","message","legacyCode"}`.
3. Compare with legacy behaviour (`git show legacy-baseline:db/procs/022_usp_ChangeCapacity.sql` - retired in MBA-3824, `db/triggers/033_trg_ExamSession_Update.sql`)
   and the decision in ADR 0002. Run `mvn -B verify -DskipFrontend=true` in `services/scheduling-service` for the
   domain unit tests and the SQL Server integration tests.
4. Fix forward with a regression test in `SessionCapacityTest` / `SchedulingApiIT`, PR into `assessment-delivery-demo`.

## Rollback: routing allow-list

Moving a test center back to legacy needs no deploy and no restart.

1. Edit `services/routing-gateway/config/routing.properties` and remove the center code from
   `routing.modern-centers` (e.g. `routing.modern-centers=BOS01,SEA01` -> `routing.modern-centers=SEA01`). Save.
2. The gateway re-reads the file within `routing.watch-interval` (1 s); or force it with
   `curl -X POST localhost:8000/actuator/refresh`.
3. Verify: requests for that center now answer `X-Route: legacy` and the `routing` log shows `route=legacy center=<code>`.
4. Bookings created by scheduling-service (ids >= 50,000,000, `origin=MODERN`) always stay on modern, so candidates
   can still view, cancel and reschedule them after a rollback.
5. Only test centers (`routing.test-centers`, default `BOS01,SEA01,AKL01`) may be allow-listed until the M4 outbox
   relay is live (`routing.coexistence-mode=relay`); a refresh that adds any other center is rejected and the previous
   list stays in force.

Rule-level fallback: `scheduling.rules.accommodation-floor` (`allocated` | `booked-extra-time`) and
`scheduling.rules.retake-days` (`calendar` | `working`) switch a ported rule without a code change (restart required).

## Escalation

Page `scheduling-service-oncall`; for gateway routing incidents also the migration team (epic MBA-3819).
Post the root cause and fix PR in the alert thread. Never merge or deploy a fix from the incident session.
