# Reconciliation: Scheduling vs AssessmentDelivery

Generated 2026-11-11T20:51:22 by scheduling-service `reconcile` (MBA-3823).

| check | result |
|---|---|
| sessions compared | 462 |
| bookings compared | 3627 |
| session field mismatches | 2 |
| booking mismatches | 0 |
| **total mismatches** | **2** |
| `db/checks/seat-drift.sql` rows | 2 |

Per session: Capacity, SeatsAvailable (Scheduling derives Capacity - (BOOKED + ATTENDED), floored at 0; legacy is the stored counter), BOOKED / ATTENDED / WAITLISTED counts and accommodation seats used (BOOKED + ATTENDED with ET25/ET50). Per booking: session, status and accommodation code, keyed on the legacy BookingId (`booking.legacy_booking_id`).

## Session mismatches

| SessionId | field | Scheduling | AssessmentDelivery |
|---|---|---|---|
| 155 | seatsAvailable | 8 | 1 |
| 158 | seatsAvailable | 4 | 0 |

## Booking mismatches

None.

## `db/checks/seat-drift.sql`

Source: `db/checks/seat-drift.sql`. 2 rows:

| SessionId | ExamCode | CenterCode | StartsAt | Capacity | SeatsAvailable | ActiveBookings | Drift |
|---|---|---|---|---|---|---|---|
| 155 | HPI-ACC2 | DUB01 | 2026-11-12T13:30 | 16 | 1 | 8 | 7 |
| 158 | BCB-AGL | LDS01 | 2026-11-14T09:00 | 11 | 0 | 7 | 4 |

