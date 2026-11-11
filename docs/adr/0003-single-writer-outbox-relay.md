# ADR 0003: scheduling-service is the single writer; legacy tables are an outbox projection (MBA-3823, M4)

Status: accepted. Builds on [ADR 0002](0002-ported-rule-decisions.md) and the M3 routing gateway.

## Context

`ExamSession.SeatsAvailable` has several writers with different formulas: the portal stored procedures, booking-api's
double decrement (HB-01) and leaks (HB-02), capacity-sync's nightly recompute (HB-03, MR-17) and the two triggers
(HB-04, HB-05, HB-16). That is the root of the seat drift in [MR-01 / MR-02](../as-is/migration-risks.md). All
components share the high-privilege `ad_app` login (HB-28, MR-07), so nothing stops a new writer appearing.

## Decisions

1. **One writer.** Only scheduling-service decides bookings, waitlist, attendance and capacity. The gateway sends
   booking/capacity traffic for every centre to it (`routing.modern-centers=*`, `routing.coexistence-mode=relay`).
   Centre-less portal pages (`/Booking/MyBookings` without `centerCode`, candidate pages, capacity audit) and every
   legacy reader keep reading `AssessmentDelivery`.
2. **Per-component logins** (`db/security/036_component_logins.sql`): `ad_portal`, `ad_booking_api`, `ad_results`,
   `ad_capacity_sync`, `ad_sched_import` (read-only, M2) and `ad_sched_relay`; `ad_app` is no longer created and its
   database user is dropped (HB-28, MR-07). Azure configs carry placeholders only.
3. **Legacy write denial** (`db/security/040_single_writer.sql`, deliberate change to HB-01/HB-02/HB-29 paths): legacy
   logins get `DENY INSERT/UPDATE/DELETE` on `dbo.Booking` and `dbo.Waitlist`, `DENY UPDATE` on
   `ExamSession(Capacity, SeatsAvailable, AccommodationCapacity)` and `DENY EXECUTE` on `usp_BookSeat`,
   `usp_CancelBooking`, `usp_ChangeCapacity` and `usp_RescheduleBooking`. A direct legacy write now fails with SQL
   Server error 229/230; booking-api maps it to `403 LEGACY_WRITE_DENIED` (send writes through `:8000`).
4. **Transactional outbox** (`V2__outbox.sql`, `outbox_event`, `event_seq` identity = monotonic sequence): written in the
   same transaction as `book`, `cancel`, `reschedule`, `recordAttendance` and `changeCapacity`. Event types
   `BookingCreated`, `BookingWaitlisted`, `BookingCancelled`, `BookingRescheduled`, `AttendanceRecorded`,
   `CapacityChanged`, plus `InventoryBaselined` written by a `mode=reset` snapshot import so the first relay pass
   projects the derived seat counts. Each event carries the full resulting state of the touched bookings and
   sessions (state transfer, not deltas), so applying it twice is harmless.
5. **Idempotent relay** (`com.northwind.scheduling.relay`, login `ad_sched_relay`, `SESSION_CONTEXT(N'writer') =
   N'scheduling-relay'`): one legacy transaction per event upserts `Booking`, `Waitlist`, `ExamSession` and one
   `AuditLog` row, then inserts the event id into `dbo.RelayCheckpoint`; an event already checkpointed is a no-op.
   The outbox row is marked relayed afterwards, so a crash between the two only causes a no-op replay.
6. **Booking ids.** Modern booking ids are already disjoint from legacy ids (imported rows keep the legacy id, new rows
   start at `routing.modern-booking-id-floor=50000000`), so the relay writes `dbo.Booking.BookingId = booking_id`
   with `SET IDENTITY_INSERT` on first relay and stores it in `booking.legacy_booking_id`. `Result.BookingId`, v1 URLs
   and results-service attendance keep working with the same number.
7. **Trigger neutralization for relayed writes only** (deliberate change, HB-01/HB-04 in `032_trg_Booking_SeatSync`,
   HB-05/HB-16 in `033_trg_ExamSession_Update`, MR-01/MR-14): both triggers return early when the writer is
   `scheduling-relay`; `trg_ExamSession_Update` still stamps `LastModified`. Any other writer still gets the legacy
   side effects (characterized by `LegacySingleWriterIT`).
8. **Remaining legacy writers.** results-service calls `POST /api/v2/bookings/{bookingId}/attendance` instead of
   `UPDATE dbo.Booking SET Status = 'ATTENDED'`; capacity-sync's `runSync` is disabled by `capacity.sync.enabled=false`
   and only writes its `CAPACITY_SYNC` audit row with `SKIPPED` (HB-03, MR-17; deliberate change); booking-api keeps
   its v1 contract behind the gateway, which already serves v1 for modern centres.
9. **Reconciliation replaces capacity-sync.** `GET /internal/reconciliation` and `java -jar scheduling-service.jar
   reconcile` compare every session (Capacity, SeatsAvailable, BOOKED/ATTENDED/WAITLISTED counts, accommodation seats)
   and every booking (session, status, accommodation code) and include `db/checks/seat-drift.sql`; the report is
   written to `reports/reconciliation-<timestamp>.md`. Exit code 0 only with 0 mismatches and 0 drift rows. Attendance needs a service credential: header `X-Service-Token`, compared in constant time with `scheduling.attendance.service-token` (env `SCHEDULING_ATTENDANCE_SERVICE_TOKEN`). The comparison fails closed when the property is unset. results-service sends `scheduling.service-token`, and the gateway returns 404 for `/api/v2/bookings/*/attendance`. The other v2 writes stay open like the legacy no-auth model (MR-16), but attendance writes ATTENDED into legacy, so results integrity depends on it.

## Not changed

Other legacy oddities stay as documented (e.g. HB-30 destructive `db/apply.*`, HB-31 sessions never closed, HB-17
audit actor inconsistencies). The relay writes `AuditLog.Actor = scheduling-relay` for its own rows only.

## Consequences

- Legacy availability is now exactly `Capacity - (BOOKED + ATTENDED)` as decided by scheduling-service; seat drift
  is 0 after mixed traffic (see `reports/`).
- Legacy readers lag by the relay delay (poll `scheduling.relay.poll-ms=250`; p95 well under 2 s locally).
- FU-3 of ADR 0002 (retire capacity-sync and `SeatsAvailable`) is now possible.
