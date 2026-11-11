# scheduling-service events

All events ride the M4 transactional outbox (`outbox_event`, [ADR 0003](../../../docs/adr/0003-single-writer-outbox-relay.md)):
they are written in the same transaction as the change and processed by `RelayWorker` in `event_seq` order.
State-transfer events (`BookingCreated` ... `InventoryBaselined`, `OutboxEventType`) are projected into
`AssessmentDelivery` by `LegacyRelay`. Notification events are not projected into legacy; the relay hands them to
`NotificationPort` instead.

## CandidateCapacityImpactNotified (MBA-3831)

Schema: [`schemas/candidate-capacity-impact-notified.schema.json`](schemas/candidate-capacity-impact-notified.schema.json)
(JSON Schema 2020-12, enforced by `CandidateCapacityImpactSchemaTest` and `CapacityImpactNotificationIT`).

**When.** `PUT /api/v2/sessions/{sessionId}/capacity` (`SchedulingService.changeCapacity`) emits one event per
affected candidate, in the same transaction as the `CapacityChanged` event and the `capacity_change` row
(`domain/CapacityImpact`):

| impact | who | previousStatus -> newStatus |
|---|---|---|
| `STATUS_CHANGED` | every booking of the session whose status changed with the change: the newest BOOKED bookings demoted to the waitlist when capacity drops below the booked seats ([HB-05](../../../docs/as-is/hidden-behaviours.md), [ODD-02](../../../characterization/ODDITIES.md)) | e.g. `BOOKED` -> `WAITLISTED` |
| `SEAT_OFFERED` | a waitlisted booking that the A4 auto-offer ([ADR 0005](../../../docs/adr/0005-waitlist-auto-offer.md)) offered a freed seat in this transaction, e.g. after a capacity increase; the booking stays `WAITLISTED` until the offer is accepted | `WAITLISTED` -> `WAITLISTED` |
| `OFFER_WITHDRAWN` | a waitlisted booking whose pending offer was withdrawn because capacity dropped (`WaitlistOfferPolicy.withdrawExcess`); it keeps its waitlist place | `WAITLISTED` -> `WAITLISTED` |
| `SEAT_CONFIRMED` | capacity reduced: every booking still `BOOKED` after the change ("capacity reduced, your seat is confirmed") | `BOOKED` -> `BOOKED` |

A capacity increase never promotes a waitlisted booking directly ([HB-06](../../../docs/as-is/hidden-behaviours.md),
ODD-09); with A4 it may make seat offers (`SEAT_OFFERED`), otherwise it emits no event. Legacy never notified anyone ([MR-04](../../../docs/as-is/migration-risks.md)
"who notifies demoted candidates?"); this event is the modern answer. `ATTENDED`, `CANCELLED` and `WAITLISTED`
bookings whose status does not change are not notified, unless a seat offer was made or withdrawn for them.

**Payload.** `outbox_event.payload` is the usual `OutboxPayload` (`type`, `detail`, `bookings: []`,
`sessions: []`) plus `notification`, which is the event:

| field | type | meaning |
|---|---|---|
| `eventId` | uuid | equals `outbox_event.event_id`; idempotency key for `NotificationPort` |
| `sessionId` | int | exam session |
| `centerCode` | string | test centre code, e.g. `SFO01` |
| `examCode` | string | exam code, e.g. `MHL-RN1` |
| `startsAt` | local date-time | session start in the centre's local time (as `ExamSession.StartsAt`), no offset |
| `oldCapacity` / `newCapacity` | int | session capacity before / after the change |
| `candidateNumber` | string | `NW` + 8 digits |
| `bookingId` | int | affected booking |
| `previousStatus` / `newStatus` | `BOOKED`, `WAITLISTED`, `CANCELLED`, `ATTENDED` | booking status before / after the change |
| `impact` | `STATUS_CHANGED`, `SEAT_OFFERED`, `OFFER_WITHDRAWN`, `SEAT_CONFIRMED` | why the candidate is notified (see above) |

```json
{
  "eventId": "8a7b3c52-5f43-4c0e-9f6e-2f1d0c9b7a11",
  "sessionId": 154, "centerCode": "SFO01", "examCode": "MHL-RN1", "startsAt": "2026-11-12T13:30:00",
  "oldCapacity": 20, "newCapacity": 8,
  "candidateNumber": "NW00000909", "bookingId": 9,
  "previousStatus": "BOOKED", "newStatus": "WAITLISTED", "impact": "STATUS_CHANGED"
}
```

**Delivery.** `RelayWorker` -> `NotificationDelivery` -> `NotificationPort.notify`. Delivery is at least once (a crash
between delivery and `markRelayed` redelivers the event), so ports deduplicate on `eventId`. Nothing is written to
`AssessmentDelivery` (no `AuditLog`, no `RelayCheckpoint`), so reconciliation is unaffected.

**Stub.** `LoggingNotificationAdapter` is the only `NotificationPort`: no email/SMS. It writes one JSON log line
(`"event":"CandidateCapacityImpactNotified"` plus every field above; with the `plain-logs` profile the message itself
carries impact, candidateNumber, bookingId, sessionId, previous/new status and eventId as `key=value`) and keeps the newest
`scheduling.notifications.stub.max-records` (default 10000) in memory, readable at
`GET /internal/notifications?candidateNumber=NW...` on :8090 (`candidateNumber` is required, 400 without it, so there
is no bulk listing; not in the public OpenAPI spec; the routing gateway does not route `/internal/**`). The store is per process and empty after a restart.
