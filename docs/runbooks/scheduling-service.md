# Runbook: scheduling-service

The canonical runbook lives with the service:
**[services/scheduling-service/docs/runbook.md](../../services/scheduling-service/docs/runbook.md)**.

It covers health/readiness (`/actuator/health/liveness`, `/actuator/health/readiness`), the Prometheus metrics
(`scheduling_booking_duration_seconds`, `scheduling_booking_errors_total`, `scheduling_outbox_pending`,
`scheduling_outbox_lag_seconds`) with dashboard queries, JSON logs, and the playbooks for high booking latency, error
spikes, outbox lag, a non-zero reconciliation report and rolling a center back to legacy via the routing allow-list
(`routing.modern-centers` in `services/routing-gateway/config/routing.properties`). SLOs, alerts and escalation:
[docs/oncall/scheduling-service.md](../oncall/scheduling-service.md).
