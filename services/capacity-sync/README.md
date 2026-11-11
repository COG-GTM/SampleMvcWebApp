# capacity-sync (retired)

This Java 8 service (:8083) is retired. Its only job, `CapacitySyncService.runSync`, rewrote
`ExamSession.SeatsAvailable` for OPEN sessions nightly at 02:00 and on `POST /sync/run`. That reconciler now lives
inside `services/scheduling-service`, which owns `ExamSession`, `Booking` and `Waitlist`
(`docs/to-be/scheduling-service-contract.md` section 6, inventory rows CS-01..CS-03 in
`docs/to-be/scheduling-boundary.md`).

| Legacy | Replacement (scheduling-service :8084) |
|---|---|
| `POST /sync/run` | `POST /api/v1/reconciler/runs` (409 if a run is already in progress) |
| `GET /sync/last` | `GET /api/v1/reconciler/runs?max=1` |
| Nightly `@Scheduled` 02:00 job | the reconciler's internal schedule (`trigger=SCHEDULED`) |
| `AuditLog` row `ExamSession/0/CAPACITY_SYNC` (read by the portal capacity-audit page, P-A07) | a reconciler run row per run, read via `GET /api/v1/reconciler/runs` |

Behaviour kept for parity (legacy is the spec, defects included):

- Formula `Capacity - AccommodationCapacity - standard bookings` (BOOKED + ATTENDED without ET25/ET50), floored at 0
  (HB-03, MR-17). It disagrees with `db/checks/seat-drift.sql`, so a run can create drift on sessions with an
  accommodation allocation and mask partner double-decrement drift (HB-01) when the allocation is 0.
- OPEN sessions only (HB-20). Compare-and-set write (`... AND SeatsAvailable = <read value>`): a lost CAS is skipped
  until the next run.
- Every run is recorded, including failures, which return 200 with `status=FAILED` (HB-20).

Coverage proof: the characterization suite pins `runSync` through `SchedulingTarget.reconcileSeats` in
`SeatDriftScenariosTest` (SD-04 formula creates drift, SD-05 masks partner drift, SD-06 skips non-OPEN sessions).
The same tests run unchanged against scheduling-service:

```bash
cd services/scheduling-characterization
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B verify -Dtarget=scheduling-service
```

What was removed: the module (`pom.xml`, `src/`), its entry in the `services/pom.xml` aggregator, the `:8083`
service in `scripts/run-legacy.sh` / `scripts/run-legacy.ps1`; the `characterization/` harness `capacity_sync`
step now posts to the reconciler endpoint. The source is still in git history (`estate/legacy-baseline`).
