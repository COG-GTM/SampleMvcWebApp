# scheduling-service

Java 21 / Spring Boot 3 scheduling service for the test-centre slice (port 8090, `/api/v2`, `/api/v1` compatibility,
UI at `/app`). Run it with [`scripts/run-scheduling-service.sh`](../../scripts/run-scheduling-service.sh); build and test
with `JAVA_HOME=<JDK 21> ./mvnw -B verify` (Testcontainers SQL Server needs Docker).

- Operations: health/readiness probes, Prometheus metrics, JSON logs and on-call scenarios are in the
  [runbook](../../docs/runbooks/scheduling-service.md).
- Ported rule decisions: [ADR 0002](../../docs/adr/0002-ported-rule-decisions.md).
