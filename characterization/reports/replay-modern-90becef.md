# Characterization replay: target `modern`

- commit: `90becef`
- portal: http://localhost:8000  api: http://localhost:8000  sync: http://localhost:8000
- reseed: `command`  compare: `all`  SEED_AS_OF: `2026-11-10`
- result: **16/64 transcripts match**

| Scenario | Channel | Oddity | Result |
|---|---|---|---|
| `BK-01-portal-book-open-session` | portal | ODD-01 | FAIL |
| `BK-02-api-book-open-session` | api | ODD-01 | FAIL |
| `BK-03-portal-book-full-session` | portal |  | FAIL |
| `BK-04-api-book-full-session` | api |  | FAIL |
| `BK-05-portal-duplicate-booking` | portal | ODD-12 | FAIL |
| `BK-06-api-duplicate-booking` | api | ODD-12 | FAIL |
| `BK-07-db-duplicate-and-closed-error-codes` | db | ODD-11 | FAIL |
| `BK-08-portal-book-closed-session` | portal | ODD-12 | FAIL |
| `BK-09-api-book-closed-session` | api | ODD-12 | PASS |
| `BK-10-portal-book-cancelled-session` | portal | ODD-12 | FAIL |
| `BK-11-api-book-cancelled-session` | api | ODD-12 | PASS |
| `CN-01-portal-cancel-booked` | portal | ODD-01 | ERROR |
| `CN-02-api-cancel-booked-partner` | api | ODD-01, ODD-13 | FAIL |
| `CN-03-portal-cancel-waitlisted` | portal |  | ERROR |
| `CN-04-api-cancel-waitlisted` | api |  | FAIL |
| `CN-05-portal-cancel-on-full-session-no-promotion` | portal | ODD-09 | FAIL |
| `CN-06-api-cancel-on-full-session-no-promotion` | api | ODD-09 | FAIL |
| `CN-07-portal-cancel-twice` | portal | ODD-12 | ERROR |
| `CN-08-api-cancel-twice` | api | ODD-12 | FAIL |
| `RS-01-portal-reschedule-same-exam` | portal | ODD-05 | ERROR |
| `RS-02-api-reschedule-same-exam` | api | ODD-05, ODD-01 | FAIL |
| `RS-03-portal-reschedule-different-exam` | portal | ODD-12 | ERROR |
| `RS-04-api-reschedule-different-exam` | api | ODD-12 | FAIL |
| `RS-05-portal-reschedule-same-session` | portal | ODD-08 | ERROR |
| `RS-06-api-reschedule-same-session` | api | ODD-08 | FAIL |
| `RS-07-db-reschedule-different-exam-error` | db | ODD-11 | ERROR |
| `RS-08-portal-reschedule-partner-booking` | portal | ODD-10, ODD-13 | FAIL |
| `RS-09-api-reschedule-to-full-session` | api | ODD-05 | FAIL |
| `RS-10-portal-reschedule-to-full-session` | portal | ODD-05 | ERROR |
| `CP-01-portal-capacity-increase` | portal |  | FAIL |
| `CP-02-portal-capacity-decrease-above-active` | portal |  | FAIL |
| `CP-03-portal-capacity-decrease-below-active` | portal | ODD-02 | FAIL |
| `CP-04-portal-capacity-raise-then-lower` | portal | ODD-02 | FAIL |
| `CP-05-portal-capacity-below-accommodation` | portal | ODD-06, ODD-12 | FAIL |
| `CP-06-db-capacity-below-accommodation-error` | db | ODD-06, ODD-11 | FAIL |
| `CP-07-portal-capacity-decrease-demotes-newest-mixed-channels` | portal | ODD-02, ODD-01 | FAIL |
| `CP-08-portal-capacity-equal-to-accommodation` | portal | ODD-02 | FAIL |
| `CP-09-db-capacity-decrease-below-active` | db | ODD-02 | FAIL |
| `RT-01-eligibility-nw00000113-2026-11-20` | both |  | PASS |
| `RT-02-eligibility-nw00000113-2026-12-06` | both |  | PASS |
| `RT-03-eligibility-nw00000113-2026-12-07` | both | ODD-04 | PASS |
| `RT-04-eligibility-nw00000113-2026-12-12` | both | ODD-04 | PASS |
| `RT-05-eligibility-nw00000113-2026-12-17` | both | ODD-04 | PASS |
| `RT-06-eligibility-nw00000113-2026-12-18` | both |  | PASS |
| `RT-07-eligibility-nw00000113-2026-12-21` | both |  | PASS |
| `RT-08-eligibility-nw00000950-2026-11-27` | both |  | PASS |
| `RT-09-eligibility-nw00000950-2026-11-29` | both | ODD-04 | PASS |
| `RT-10-eligibility-nw00000950-2026-11-30` | both | ODD-04 | PASS |
| `RT-11-eligibility-nw00000950-2026-12-11` | both |  | PASS |
| `RT-12-eligibility-nw00000362-2026-12-21` | both |  | PASS |
| `RT-13-eligibility-nw00000901-2026-11-20` | both |  | PASS |
| `RT-14-portal-book-retake-between-boundaries` | portal | ODD-04 | FAIL |
| `RT-15-api-book-retake-between-boundaries` | api | ODD-04 | FAIL |
| `AC-01-portal-extra-time-over-allocation` | portal | ODD-12 | FAIL |
| `AC-02-api-extra-time-over-allocation` | api | ODD-01, ODD-12 | FAIL |
| `AC-03-portal-sr-rb-do-not-consume-accommodation` | portal |  | FAIL |
| `AC-04-api-sr-rb-do-not-consume-accommodation` | api | ODD-01, ODD-13 | FAIL |
| `AC-05-portal-blank-accommodation-uses-profile` | portal | ODD-07 | FAIL |
| `AC-06-api-blank-accommodation-is-none` | api | ODD-07 | FAIL |
| `AC-07-portal-extra-time-on-zero-allocation` | portal | ODD-12 | FAIL |
| `AC-08-api-extra-time-on-zero-allocation` | api | ODD-12 | PASS |
| `MX-01-mixed-traffic-then-capacity-sync` | both | ODD-01, ODD-03, ODD-13 | FAIL |
| `MX-02-capacity-sync-on-fresh-seed` | sync | ODD-03 | FAIL |
| `MX-03-capacity-sync-with-accommodation-bookings` | both | ODD-03, ODD-01 | FAIL |

## BK-01-portal-book-open-session (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000901' -> '/app/book?sessionId=154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Ryan Pennington', 'Candidate no.': 'NW00000901', 'Email': 'ryan.pennington901@example.net', 'Default accomm...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit: length 1 -> 0
$.after.bookings: length 11 -> 10
$.after.sessions[0].BookedOrAttended: 10 -> 9
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

## BK-02-api-book-open-session (FAIL)

```
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-1001' -> 'outbox seq=171 event=0cb585e9-e670-4fbf-a4a1-3cc6b9951e88 BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

## BK-03-portal-book-full-session (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000904' -> '/app/book?sessionId=153'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Chloe Okafor', 'Candidate no.': 'NW00000904', 'Email': 'chloe.okafor904@example.net', 'Default accommodatio...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.after.audit: length 1 -> 0
$.after.bookings: length 26 -> 25
$.after.sessions[0].Waitlisted: 4 -> 3
$.after.waitlist: length 4 -> 3
```

## BK-04-api-book-full-session (FAIL)

```
$.after.audit[0].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=153 status=WAITLISTED channel=PARTNER' -> 'outbox seq=174 event=16a66b29-4258-4aec-a070-2566b308d864 BookingWaitlisted channel=PARTNER'
```

## BK-05-portal-duplicate-booking (FAIL)

```
$.before.audit: length 1 -> 0
$.before.bookings: length 11 -> 10
$.before.sessions[0].BookedOrAttended: 10 -> 9
$.before.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.before.sessions[0].SeatsAvailable: 10 -> 11
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Centre': 'Bayside Assessment Centre (SFO01)', 'Room': 'Roo...)
$.steps[0].response.rendered.heading: 'Book a seat Session #154' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit: length 1 -> 0
$.after.bookings: length 11 -> 10
$.after.sessions[0].BookedOrAttended: 10 -> 9
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

## BK-06-api-duplicate-booking (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=177 event=ea998c55-3361-46df-89f3-422ed9beadcd BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=177 event=ea998c55-3361-46df-89f3-422ed9beadcd BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

## BK-07-db-duplicate-and-closed-error-codes (FAIL)

```
$.before.audit: length 1 -> 0
$.before.bookings: length 13 -> 12
$.before.bookings[10].BookedAt: '<now>' -> '2026-11-09T19:51:00'
$.before.bookings[10].BookingId: '<booking:1>' -> 2217
$.before.bookings[10].CandidateNumber: 'NW00000908' -> 'NW00000201'
$.before.bookings[10].LastModified: '<now>' -> '2026-11-09T19:51:00'
$.before.bookings[10].SessionId: 154 -> 263
$.before.bookings[11].BookedAt: '2026-11-09T19:51:00' -> '2026-11-10T05:22:00'
$.before.bookings[11].BookingId: 2217 -> 2218
$.before.bookings[11].CandidateNumber: 'NW00000201' -> 'NW00000610'
$.before.bookings[11].Channel: 'PORTAL' -> 'PARTNER'
$.before.bookings[11].ExternalRef: None -> 'PTR-0026302'
$.before.bookings[11].LastModified: '2026-11-09T19:51:00' -> '2026-11-10T05:22:00'
$.before.sessions[0].BookedOrAttended: 10 -> 9
$.before.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.before.sessions[0].SeatsAvailable: 10 -> 11
$.steps[0].response.errors[0].message: 'Candidate 908 already has an active booking on session 154.' -> "Could not find stored procedure 'dbo.usp_BookSeat'."
$.steps[0].response.errors[0].number: 50000 -> 2812
$.steps[0].response.errors[0].state: 2 -> 62
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.steps[1].response.errors[0].message: 'Session 263 is not open for booking.' -> "Could not find stored procedure 'dbo.usp_BookSeat'."
$.steps[1].response.errors[0].number: 50000 -> 2812
$.steps[1].response.errors[0].state: 1 -> 62
$.steps[1].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit: length 1 -> 0
$.after.bookings: length 13 -> 12
$.after.bookings[10].BookedAt: '<now>' -> '2026-11-09T19:51:00'
$.after.bookings[10].BookingId: '<booking:1>' -> 2217
$.after.bookings[10].CandidateNumber: 'NW00000908' -> 'NW00000201'
$.after.bookings[10].LastModified: '<now>' -> '2026-11-09T19:51:00'
$.after.bookings[10].SessionId: 154 -> 263
$.after.bookings[11].BookedAt: '2026-11-09T19:51:00' -> '2026-11-10T05:22:00'
$.after.bookings[11].BookingId: 2217 -> 2218
$.after.bookings[11].CandidateNumber: 'NW00000201' -> 'NW00000610'
$.after.bookings[11].Channel: 'PORTAL' -> 'PARTNER'
$.after.bookings[11].ExternalRef: None -> 'PTR-0026302'
$.after.bookings[11].LastModified: '2026-11-09T19:51:00' -> '2026-11-10T05:22:00'
$.after.sessions[0].BookedOrAttended: 10 -> 9
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

## BK-08-portal-book-closed-session (FAIL)

```
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=263'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'HPI-ACC1 – Foundations of Financial Accounting', 'Centre': 'Midtown Assessment Suites (NYC02)', 'Room': 'Su...)
$.steps[0].response.rendered.heading: 'Book a seat Session #263' -> None
```

## BK-10-portal-book-cancelled-session (FAIL)

```
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=162'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'BCB-AGL – Agile Delivery Essentials', 'Centre': 'Peachtree Exam Centre (ATL01)', 'Room': 'Room 102', 'Start...)
$.steps[0].response.rendered.heading: 'Book a seat Session #162' -> None
```

## CN-01-portal-cancel-booked (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 136, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 114, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## CN-02-api-cancel-booked-partner (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-2001' -> 'outbox seq=185 event=39fc0951-2e32-46e6-ae40-3c604ca4abfa BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-2001' -> 'outbox seq=185 event=39fc0951-2e32-46e6-ae40-3c604ca4abfa BookingCreated channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'partner-cancel' -> 'outbox seq=186 event=dec760cc-44d6-4736-a2c5-f4945b0a5abd BookingCancelled reason=partner-cancel'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

## CN-03-portal-cancel-waitlisted (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=153', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 136, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 114, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=153', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## CN-04-api-cancel-waitlisted (FAIL)

```
$.before.audit[0].Action: 'BOOK' -> 'WAITLIST'
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=153 status=WAITLISTED channel=PARTNER' -> 'outbox seq=189 event=17700995-d85d-4218-badd-b994178ef364 BookingWaitlisted channel=PARTNER'
$.after.audit[0].Action: 'BOOK' -> 'WAITLIST'
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=153 status=WAITLISTED channel=PARTNER' -> 'outbox seq=189 event=17700995-d85d-4218-badd-b994178ef364 BookingWaitlisted channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'Cancelled by partner' -> 'outbox seq=190 event=73584dae-cd28-49ec-a0d0-07a0bc8da602 BookingCancelled reason=Cancelled by partner'
```

## CN-05-portal-cancel-on-full-session-no-promotion (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000001' -> '/app/my-bookings'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Noah Nakamura', 'Candidate no.': 'NW00000001', 'Email': 'noah.nakamura1@example.net', 'Default accommodatio...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 1 -> 0
$.after.audit: length 1 -> 0
$.after.bookings[22].CancelReason: 'characterization' -> None
$.after.bookings[22].CancelledAt: '<now>' -> None
$.after.bookings[22].LastModified: '<now>' -> '2026-11-09T01:12:00'
$.after.bookings[22].Status: 'CANCELLED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 21 -> 22
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 1 -> 0
```

## CN-06-api-cancel-on-full-session-no-promotion (FAIL)

```
$.after.audit: length 1 -> 2
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'Cancelled by partner' -> 'outbox seq=193 event=1145bb1e-5f3c-4e47-b9a8-a4b7550ba29e BookingCancelled reason=Cancelled by partner'
$.after.bookings[23].LastModified: '2026-11-09T10:43:00' -> '<now>'
```

## CN-07-portal-cancel-twice (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 136, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 114, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## CN-08-api-cancel-twice (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=197 event=d19691f9-2294-41a7-a38a-61648a2feade BookingCreated channel=PARTNER'
$.before.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[1].Detail: 'Cancelled by partner' -> 'outbox seq=198 event=3b87c0a2-29be-4edf-bb1b-69ff15dfb47b BookingCancelled reason=Cancelled by partner'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 10 -> 11
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=197 event=d19691f9-2294-41a7-a38a-61648a2feade BookingCreated channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'Cancelled by partner' -> 'outbox seq=198 event=3b87c0a2-29be-4edf-bb1b-69ff15dfb47b BookingCancelled reason=Cancelled by partner'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

## RS-01-portal-reschedule-same-exam (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 136, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 114, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-02-api-reschedule-same-exam (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-3001' -> 'outbox seq=201 event=5b3c0f67-7ba5-4a32-b102-98c08692023b BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.steps[0].sessions_after[1].SeatsAvailable: 3 -> 4
$.after.audit: length 3 -> 2
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-3001' -> 'outbox seq=201 event=5b3c0f67-7ba5-4a32-b102-98c08692023b BookingCreated channel=PARTNER'
$.after.audit[1].Action: 'CANCEL' -> 'RESCHEDULE'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'Rescheduled' -> 'outbox seq=202 event=9368e01f-111c-469a-bcb7-ce051ac1f069 BookingRescheduled from=<booking:1> to=<booking:2>'
$.after.audit[1].EntityId: '<booking:1>' -> '<booking:2>'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 10 -> 11
$.after.sessions[1].SeatDrift: -1 -> 0
$.after.sessions[1].SeatsAvailable: 3 -> 4
```

## RS-03-portal-reschedule-different-exam (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 136, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 114, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-04-api-reschedule-different-exam (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=205 event=64c3cb86-6cc9-473f-a374-659c3caf47e8 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=205 event=64c3cb86-6cc9-473f-a374-659c3caf47e8 BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

## RS-05-portal-reschedule-same-session (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 136, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 114, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-06-api-reschedule-same-session (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=208 event=4f108ee6-9fed-4d0e-a024-33f8c97af816 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=208 event=4f108ee6-9fed-4d0e-a024-33f8c97af816 BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

## RS-07-db-reschedule-different-exam-error (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 136, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 114, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=154', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-08-portal-reschedule-partner-booking (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-3008' -> 'outbox seq=211 event=0798ed64-99db-4483-8d7a-e4f12daacfcb BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 9 -> 10
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000927' -> '/app/reschedule/<booking:1>'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Ethan Kearney', 'Candidate no.': 'NW00000927', 'Email': 'ethan.kearney927@example.com', 'Default accommodat...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[1].SeatsAvailable: 4 -> 5
$.after.audit: length 3 -> 1
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER ref=PX-3008' -> 'outbox seq=211 event=0798ed64-99db-4483-8d7a-e4f12daacfcb BookingCreated channel=PARTNER'
$.after.bookings: length 15 -> 14
$.after.bookings[10].CancelReason: 'Rescheduled' -> None
$.after.bookings[10].CancelledAt: '<now>' -> None
$.after.bookings[10].Status: 'CANCELLED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 9 -> 10
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[1].BookedOrAttended: 4 -> 3
$.after.sessions[1].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[1].SeatsAvailable: 4 -> 5
```

## RS-09-api-reschedule-to-full-session (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=158 status=BOOKED channel=PARTNER ref=PX-3009' -> 'outbox seq=213 event=b6c05c4f-16b5-4b20-bb7b-85f7aecaf053 BookingCreated channel=PARTNER'
$.before.sessions[1].SeatDrift: -1 -> 0
$.before.sessions[1].SeatsAvailable: 5 -> 6
$.steps[0].sessions_after[1].SeatsAvailable: 6 -> 7
$.after.audit: length 3 -> 2
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=158 status=BOOKED channel=PARTNER ref=PX-3009' -> 'outbox seq=213 event=b6c05c4f-16b5-4b20-bb7b-85f7aecaf053 BookingCreated channel=PARTNER'
$.after.audit[1].Action: 'CANCEL' -> 'RESCHEDULE'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'Rescheduled' -> 'outbox seq=214 event=460f2bba-6665-429c-a0ed-8cbbf91eb570 BookingRescheduled from=<booking:1> to=<booking:2>'
$.after.audit[1].EntityId: '<booking:1>' -> '<booking:2>'
$.after.sessions[1].SeatDrift: -1 -> 0
$.after.sessions[1].SeatsAvailable: 6 -> 7
```

## RS-10-portal-reschedule-to-full-session (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=158', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 136, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ~~~~~~~~~~^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "C:\Users\Administrator\repos\SampleMvcWebApp\characterization\harness\runner.py", line 114, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 200, 'path': '/app/book?sessionId=158', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## CP-01-portal-capacity-increase (FAIL)

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 25 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 16 -> 11
$.after.audit: length 1 -> 0
$.after.sessions[0].Capacity: 25 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 16 -> 11
```

## CP-02-portal-capacity-decrease-above-active (FAIL)

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 12 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 3 -> 11
$.after.audit: length 1 -> 0
$.after.sessions[0].Capacity: 12 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 3 -> 11
```

## CP-03-portal-capacity-decrease-below-active (FAIL)

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 6 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 0 -> 11
$.after.audit: length 1 -> 0
$.after.bookings[7].LastModified: '<now>' -> '2026-11-09T10:59:00'
$.after.bookings[7].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[8].LastModified: '<now>' -> '2026-11-09T20:30:00'
$.after.bookings[8].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[9].LastModified: '<now>' -> '2026-11-10T04:21:00'
$.after.bookings[9].Status: 'WAITLISTED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 6 -> 9
$.after.sessions[0].Capacity: 6 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 0 -> 11
$.after.sessions[0].Waitlisted: 3 -> 0
$.after.waitlist: length 3 -> 0
```

## CP-04-portal-capacity-raise-then-lower (FAIL)

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 24 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 15 -> 11
$.steps[1].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[1].response.rendered.alerts: length 1 -> 0
$.steps[1].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[1].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[1].sessions_after[0].Capacity: 10 -> 20
$.steps[1].sessions_after[0].SeatsAvailable: 1 -> 11
$.steps[2].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[2].response.rendered.alerts: length 1 -> 0
$.steps[2].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[2].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[2].sessions_after[0].Capacity: 7 -> 20
$.steps[2].sessions_after[0].SeatsAvailable: 0 -> 11
$.after.audit: length 3 -> 0
$.after.bookings[8].LastModified: '<now>' -> '2026-11-09T20:30:00'
$.after.bookings[8].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[9].LastModified: '<now>' -> '2026-11-10T04:21:00'
$.after.bookings[9].Status: 'WAITLISTED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 7 -> 9
$.after.sessions[0].Capacity: 7 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 0 -> 11
$.after.sessions[0].Waitlisted: 2 -> 0
$.after.waitlist: length 2 -> 0
```

## CP-05-portal-capacity-below-accommodation (FAIL)

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
```

## CP-06-db-capacity-below-accommodation-error (FAIL)

```
$.steps[0].response.errors[0].message: 'Invalid capacity 1 for session 154.' -> "Could not find stored procedure 'dbo.usp_ChangeCapacity'."
$.steps[0].response.errors[0].number: 50000 -> 2812
$.steps[0].response.errors[0].state: 5 -> 62
```

## CP-07-portal-capacity-decrease-demotes-newest-mixed-channels (FAIL)

```
$.before.audit: length 2 -> 1
$.before.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=154 status=BOOKED' -> 'outbox seq=223 event=e05caf8a-e4d3-4c90-bc13-92eba051b2e4 BookingCreated channel=PARTNER'
$.before.bookings: length 12 -> 11
$.before.bookings[10].CandidateNumber: 'NW00000930' -> 'NW00000931'
$.before.bookings[10].Channel: 'PORTAL' -> 'PARTNER'
$.before.bookings[10].ExternalRef: None -> 'PX-4007'
$.before.sessions[0].BookedOrAttended: 11 -> 10
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 8 -> 10
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 10 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 0 -> 10
$.after.audit: length 3 -> 1
$.after.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED' -> 'outbox seq=223 event=e05caf8a-e4d3-4c90-bc13-92eba051b2e4 BookingCreated channel=PARTNER'
$.after.bookings: length 12 -> 11
$.after.bookings[10].CandidateNumber: 'NW00000930' -> 'NW00000931'
$.after.bookings[10].Channel: 'PORTAL' -> 'PARTNER'
$.after.bookings[10].ExternalRef: None -> 'PX-4007'
$.after.sessions[0].Capacity: 10 -> 20
$.after.sessions[0].SeatsAvailable: 0 -> 10
$.after.sessions[0].Waitlisted: 1 -> 0
$.after.waitlist: length 1 -> 0
```

## CP-08-portal-capacity-equal-to-accommodation (FAIL)

```
$.steps[0].response.path: '/Admin/Sessions/Capacity/154' -> '/app/admin/sessions/154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].sessions_after[0].Capacity: 2 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 0 -> 11
$.after.audit: length 1 -> 0
$.after.bookings[2].LastModified: '<now>' -> '2026-11-07T08:04:00'
$.after.bookings[2].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[3].LastModified: '<now>' -> '2026-11-07T17:35:00'
$.after.bookings[3].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[4].LastModified: '<now>' -> '2026-11-08T03:06:00'
$.after.bookings[4].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[6].LastModified: '<now>' -> '2026-11-09T01:28:00'
$.after.bookings[6].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[7].LastModified: '<now>' -> '2026-11-09T10:59:00'
$.after.bookings[7].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[8].LastModified: '<now>' -> '2026-11-09T20:30:00'
$.after.bookings[8].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[9].LastModified: '<now>' -> '2026-11-10T04:21:00'
$.after.bookings[9].Status: 'WAITLISTED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 2 -> 9
$.after.sessions[0].Capacity: 2 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 0 -> 11
$.after.sessions[0].Waitlisted: 7 -> 0
$.after.waitlist: length 7 -> 0
```

## CP-09-db-capacity-decrease-below-active (FAIL)

```
$.steps[0].response.errors: length 0 -> 1
$.steps[0].sessions_after[0].Capacity: 6 -> 20
$.steps[0].sessions_after[0].SeatsAvailable: 0 -> 11
$.after.audit: length 1 -> 0
$.after.bookings[7].LastModified: '<now>' -> '2026-11-09T10:59:00'
$.after.bookings[7].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[8].LastModified: '<now>' -> '2026-11-09T20:30:00'
$.after.bookings[8].Status: 'WAITLISTED' -> 'BOOKED'
$.after.bookings[9].LastModified: '<now>' -> '2026-11-10T04:21:00'
$.after.bookings[9].Status: 'WAITLISTED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 6 -> 9
$.after.sessions[0].Capacity: 6 -> 20
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 0 -> 11
$.after.sessions[0].Waitlisted: 3 -> 0
$.after.waitlist: length 3 -> 0
```

## RT-14-portal-book-retake-between-boundaries (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000113' -> '/app/book?sessionId=216'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Adam Gallagher', 'Candidate no.': 'NW00000113', 'Email': 'adam.gallagher113@example.org', 'Default accommod...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 8 -> 9
$.after.audit: length 1 -> 0
$.after.bookings: length 13 -> 12
$.after.bookings[10].BookedAt: '<now>' -> '2026-11-10T16:07:00'
$.after.bookings[10].BookingId: '<booking:1>' -> 3497
$.after.bookings[10].Channel: 'PORTAL' -> 'PARTNER'
$.after.bookings[10].ExternalRef: None -> 'PTR-R221113'
$.after.bookings[10].LastModified: '<now>' -> '2026-11-10T16:07:00'
$.after.bookings[10].SessionId: 216 -> 221
$.after.bookings[11].BookedAt: '2026-11-10T16:07:00' -> '2026-11-06T23:54:00'
$.after.bookings[11].BookingId: 3497 -> 3178
$.after.bookings[11].CancelReason: None -> 'Illness'
$.after.bookings[11].CancelledAt: None -> '2026-11-08T23:54:00'
$.after.bookings[11].Channel: 'PARTNER' -> 'PORTAL'
$.after.bookings[11].ExternalRef: 'PTR-R221113' -> None
$.after.bookings[11].LastModified: '2026-11-10T16:07:00' -> '2026-11-08T23:54:00'
$.after.bookings[11].SessionId: 221 -> 446
$.after.bookings[11].Status: 'BOOKED' -> 'CANCELLED'
$.after.sessions[0].BookedOrAttended: 8 -> 7
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 8 -> 9
```

## RT-15-api-book-retake-between-boundaries (FAIL)

```
$.steps[0].response.body.bookingId: unexpected in replay ('<booking:1>')
$.steps[0].response.body.error: missing in replay (expected 'NOT_ELIGIBLE')
$.steps[0].response.body.message: missing in replay (expected 'Candidate NW00000113 is not eligible to book HPI-ACC1 (RETAKE_WAIT_NOT_ELAPSED), earliest retake date 2026-12-18')
$.steps[0].response.body.status: unexpected in replay ('BOOKED')
$.steps[0].response.status: 409 -> 201
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 8
$.after.audit: length 0 -> 1
$.after.bookings: length 12 -> 13
$.after.bookings[10].BookedAt: '2026-11-10T16:07:00' -> '<now>'
$.after.bookings[10].BookingId: 3497 -> '<booking:1>'
$.after.bookings[10].ExternalRef: 'PTR-R221113' -> None
$.after.bookings[10].LastModified: '2026-11-10T16:07:00' -> '<now>'
$.after.bookings[10].SessionId: 221 -> 216
$.after.bookings[11].BookedAt: '2026-11-06T23:54:00' -> '2026-11-10T16:07:00'
$.after.bookings[11].BookingId: 3178 -> 3497
$.after.bookings[11].CancelReason: 'Illness' -> None
$.after.bookings[11].CancelledAt: '2026-11-08T23:54:00' -> None
$.after.bookings[11].Channel: 'PORTAL' -> 'PARTNER'
$.after.bookings[11].ExternalRef: None -> 'PTR-R221113'
$.after.bookings[11].LastModified: '2026-11-08T23:54:00' -> '2026-11-10T16:07:00'
$.after.bookings[11].SessionId: 446 -> 221
$.after.bookings[11].Status: 'CANCELLED' -> 'BOOKED'
$.after.sessions[0].BookedOrAttended: 7 -> 8
$.after.sessions[0].LastModified: '2026-11-10T18:00:00' -> '<now>'
$.after.sessions[0].SeatsAvailable: 9 -> 8
```

## AC-01-portal-extra-time-over-allocation (FAIL)

```
$.before.audit: length 1 -> 0
$.before.bookings: length 4 -> 3
$.before.sessions[0].BookedOrAttended: 4 -> 3
$.before.sessions[0].ExtraTimeBooked: 1 -> 0
$.before.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.before.sessions[0].SeatsAvailable: 4 -> 5
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=163'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Centre': 'Thameside Assessment Centre (LDN01)', 'Room': 'S...)
$.steps[0].response.rendered.heading: 'Book a seat Session #163' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 4 -> 5
$.after.audit: length 1 -> 0
$.after.bookings: length 4 -> 3
$.after.sessions[0].BookedOrAttended: 4 -> 3
$.after.sessions[0].ExtraTimeBooked: 1 -> 0
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 4 -> 5
```

## AC-02-api-extra-time-over-allocation (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=244 event=df17462f-bfd4-446f-a1cb-711091973a8c BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 3 -> 4
$.steps[0].sessions_after[0].SeatsAvailable: 3 -> 4
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=244 event=df17462f-bfd4-446f-a1cb-711091973a8c BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 3 -> 4
```

## AC-03-portal-sr-rb-do-not-consume-accommodation (FAIL)

```
$.before.audit: length 1 -> 0
$.before.bookings: length 4 -> 3
$.before.sessions[0].BookedOrAttended: 4 -> 3
$.before.sessions[0].ExtraTimeBooked: 1 -> 0
$.before.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.before.sessions[0].SeatsAvailable: 4 -> 5
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000923' -> '/app/book?sessionId=163'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Arjun Whitlock', 'Candidate no.': 'NW00000923', 'Email': 'arjun.whitlock923@example.org', 'Default accommod...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 3 -> 5
$.steps[1].response.path: '/Booking/MyBookings?candidateNumber=NW00000943' -> '/app/book?sessionId=163'
$.steps[1].response.rendered.alerts: length 1 -> 0
$.steps[1].response.rendered.cells: missing in replay (expected {'Name': 'Oliver Castell', 'Candidate no.': 'NW00000943', 'Email': 'oliver.castell943@example.net', 'Default accommod...)
$.steps[1].response.rendered.heading: 'My bookings' -> None
$.steps[1].sessions_after[0].SeatsAvailable: 2 -> 5
$.after.audit: length 3 -> 0
$.after.bookings: length 6 -> 3
$.after.sessions[0].BookedOrAttended: 6 -> 3
$.after.sessions[0].ExtraTimeBooked: 1 -> 0
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 2 -> 5
```

## AC-04-api-sr-rb-do-not-consume-accommodation (FAIL)

```
$.before.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=247 event=d3352dd7-b48e-4621-a15b-af2f706b1bc3 BookingCreated channel=PARTNER'
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 3 -> 4
$.steps[0].sessions_after[0].SeatsAvailable: 1 -> 3
$.steps[1].sessions_after[0].SeatsAvailable: -1 -> 2
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=247 event=d3352dd7-b48e-4621-a15b-af2f706b1bc3 BookingCreated channel=PARTNER'
$.after.audit[1].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[1].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=248 event=6e92a08f-66af-417b-84e0-e5600d6a6231 BookingCreated channel=PARTNER'
$.after.audit[2].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[2].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'outbox seq=249 event=a7bef394-0787-4210-9c89-4f533b4d63aa BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -3 -> 0
$.after.sessions[0].SeatsAvailable: -1 -> 2
```

## AC-05-portal-blank-accommodation-uses-profile (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000003' -> '/app/book?sessionId=154'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Arjun Pryce', 'Candidate no.': 'NW00000003', 'Email': 'arjun.pryce3@example.com', 'Default accommodation': ...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].sessions_after[0].SeatsAvailable: 10 -> 11
$.after.audit: length 1 -> 0
$.after.bookings: length 12 -> 11
$.after.bookings[10].BookedAt: '<now>' -> '2026-11-09T00:27:00'
$.after.bookings[10].BookingId: '<booking:1>' -> 2114
$.after.bookings[10].LastModified: '<now>' -> '2026-11-09T00:27:00'
$.after.bookings[10].SessionId: 154 -> 245
$.after.sessions[0].BookedOrAttended: 10 -> 9
$.after.sessions[0].ExtraTimeBooked: 1 -> 0
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatsAvailable: 10 -> 11
```

## AC-06-api-blank-accommodation-is-none (FAIL)

```
$.steps[0].sessions_after[0].SeatsAvailable: 9 -> 10
$.after.audit[0].Actor: 'booking-api' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=154 status=BOOKED channel=PARTNER' -> 'outbox seq=252 event=f3b8c613-b4a2-4877-bc4a-8aa72edd6650 BookingCreated channel=PARTNER'
$.after.sessions[0].SeatDrift: -1 -> 0
$.after.sessions[0].SeatsAvailable: 9 -> 10
```

## AC-07-portal-extra-time-on-zero-allocation (FAIL)

```
$.steps[0].response.path: '/Booking/Book' -> '/app/book?sessionId=155'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'HPI-ACC2 – Management Accounting', 'Centre': 'Liffey Assessment Rooms (DUB01)', 'Room': 'Room 1', 'Start': ...)
$.steps[0].response.rendered.heading: 'Book a seat Session #155' -> None
```

## MX-01-mixed-traffic-then-capacity-sync (FAIL)

```
$.steps[1].response.calls: length 46 -> 43
$.steps[1].response.calls[0].alerts: length 1 -> 0
$.steps[1].response.calls[1].body.bookingId: '<booking:2>' -> '<booking:1>'
$.steps[1].response.calls[2].alerts: length 1 -> 0
$.steps[1].response.calls[3].body.bookingId: '<booking:4>' -> '<booking:2>'
$.steps[1].response.calls[4].alerts: length 1 -> 0
$.steps[1].response.calls[5].body.bookingId: '<booking:6>' -> '<booking:3>'
$.steps[1].response.calls[6].alerts: length 1 -> 0
$.steps[1].response.calls[7].body.bookingId: '<booking:8>' -> '<booking:4>'
$.steps[1].response.calls[8].alerts: length 1 -> 0
$.steps[1].response.calls[9].body.bookingId: '<booking:10>' -> '<booking:5>'
$.steps[1].response.calls[10].alerts: length 1 -> 0
$.steps[1].response.calls[11].body.bookingId: '<booking:12>' -> '<booking:6>'
$.steps[1].response.calls[12].alerts: length 1 -> 0
$.steps[1].response.calls[13].body.bookingId: '<booking:14>' -> '<booking:7>'
$.steps[1].response.calls[14].alerts: length 1 -> 0
$.steps[1].response.calls[15].body.bookingId: '<booking:16>' -> '<booking:8>'
$.steps[1].response.calls[15].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[16].alerts: length 1 -> 0
$.steps[1].response.calls[17].body.bookingId: '<booking:18>' -> '<booking:9>'
$.steps[1].response.calls[18].alerts: length 1 -> 0
$.steps[1].response.calls[19].body.bookingId: '<booking:20>' -> '<booking:10>'
$.steps[1].response.calls[19].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[20].alerts: length 1 -> 0
$.steps[1].response.calls[21].body.bookingId: '<booking:22>' -> '<booking:11>'
$.steps[1].response.calls[22].alerts: length 1 -> 0
$.steps[1].response.calls[23].body.bookingId: '<booking:24>' -> '<booking:12>'
$.steps[1].response.calls[24].alerts: length 1 -> 0
$.steps[1].response.calls[25].body.bookingId: '<booking:26>' -> '<booking:13>'
$.steps[1].response.calls[25].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[26].alerts: length 1 -> 0
$.steps[1].response.calls[27].body.bookingId: '<booking:28>' -> '<booking:14>'
$.steps[1].response.calls[28].alerts: length 1 -> 0
$.steps[1].response.calls[29].body.bookingId: '<booking:30>' -> '<booking:15>'
$.steps[1].response.calls[29].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[30].alerts: length 1 -> 0
$.steps[1].response.calls[31].body.bookingId: '<booking:32>' -> '<booking:16>'
$.steps[1].response.calls[32].alerts: length 1 -> 0
$.steps[1].response.calls[33].body.bookingId: '<booking:34>' -> '<booking:17>'
$.steps[1].response.calls[33].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[34].alerts: length 1 -> 0
$.steps[1].response.calls[35].body.bookingId: '<booking:36>' -> '<booking:18>'
$.steps[1].response.calls[36].alerts: length 1 -> 0
$.steps[1].response.calls[37].body.bookingId: '<booking:38>' -> '<booking:19>'
$.steps[1].response.calls[37].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[38].alerts: length 1 -> 0
$.steps[1].response.calls[39].body.bookingId: '<booking:40>' -> '<booking:20>'
$.steps[1].response.calls[40].alerts: missing in replay (expected ['Booking <booking:1> cancelled.'])
$.steps[1].response.calls[40].op: 'portal_cancel' -> 'api_cancel'
$.steps[1].response.calls[41].alerts: missing in replay (expected ['Booking <booking:3> cancelled.'])
```

## MX-02-capacity-sync-on-fresh-seed (FAIL)

```
$.steps[1].response.body.message: None -> "capacity.sync.enabled=false: SeatsAvailable is written by scheduling-service's relay; use GET /api/v2/internal/recon...
$.steps[1].response.body.sessionsChecked: 285 -> 0
$.steps[1].response.body.sessionsUpdated: 112 -> 0
$.steps[1].response.body.status: 'COMPLETED' -> 'SKIPPED'
$.steps[1].sessions_after[0].SeatsAvailable: 14 -> 16
$.steps[1].sessions_after[1].SeatsAvailable: 9 -> 11
$.steps[2].response.drifting_sessions: 112 -> 0
$.steps[2].response.rows: length 112 -> 0
$.steps[2].response.total_drift: 177 -> 0
$.steps[2].sessions_after[0].SeatsAvailable: 14 -> 16
$.steps[2].sessions_after[1].SeatsAvailable: 9 -> 11
$.after.audit[0].Detail: 'trigger=MANUAL status=COMPLETED checked=285 updated=112' -> 'trigger=MANUAL status=SKIPPED checked=0 updated=0 reason=capacity.sync.enabled=false'
$.after.sessions[0].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[0].SeatDrift: -2 -> 0
$.after.sessions[0].SeatsAvailable: 14 -> 16
$.after.sessions[1].LastModified: '<now>' -> '2026-11-10T18:00:00'
$.after.sessions[1].SeatDrift: -2 -> 0
$.after.sessions[1].SeatsAvailable: 9 -> 11
```

## MX-03-capacity-sync-with-accommodation-bookings (FAIL)

```
$.before.audit: length 3 -> 1
$.before.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.before.audit[0].Detail: 'session=163 status=BOOKED' -> 'outbox seq=282 event=8e3c7666-1da3-442c-881e-7837cab9d3fe BookingCreated channel=PARTNER'
$.before.bookings: length 6 -> 4
$.before.bookings[3].AccommodationCode: 'ET25' -> None
$.before.bookings[3].CandidateNumber: 'NW00000941' -> 'NW00000942'
$.before.bookings[3].Channel: 'PORTAL' -> 'PARTNER'
$.before.sessions[0].BookedOrAttended: 6 -> 4
$.before.sessions[0].ExtraTimeBooked: 1 -> 0
$.before.sessions[0].SeatDrift: -1 -> 0
$.before.sessions[0].SeatsAvailable: 1 -> 4
$.steps[0].response.drifting_sessions: 1 -> 0
$.steps[0].response.rows: length 1 -> 0
$.steps[0].response.total_drift: 1 -> 0
$.steps[0].sessions_after[0].SeatsAvailable: 1 -> 4
$.steps[1].response.body.message: None -> "capacity.sync.enabled=false: SeatsAvailable is written by scheduling-service's relay; use GET /api/v2/internal/recon...
$.steps[1].response.body.sessionsChecked: 285 -> 0
$.steps[1].response.body.sessionsUpdated: 112 -> 0
$.steps[1].response.body.status: 'COMPLETED' -> 'SKIPPED'
$.steps[1].sessions_after[0].SeatsAvailable: 2 -> 4
$.steps[2].response.drifting_sessions: 111 -> 0
$.steps[2].response.rows: length 111 -> 0
$.steps[2].response.total_drift: 176 -> 0
$.steps[2].sessions_after[0].SeatsAvailable: 2 -> 4
$.after.audit: length 4 -> 2
$.after.audit[0].Actor: 'ad_app' -> 'scheduling-service'
$.after.audit[0].Detail: 'session=163 status=BOOKED' -> 'outbox seq=282 event=8e3c7666-1da3-442c-881e-7837cab9d3fe BookingCreated channel=PARTNER'
$.after.audit[1].Action: 'BOOK' -> 'CAPACITY_SYNC'
$.after.audit[1].Actor: 'booking-api' -> 'capacity-sync'
$.after.audit[1].Detail: 'session=163 status=BOOKED channel=PARTNER' -> 'trigger=MANUAL status=SKIPPED checked=0 updated=0 reason=capacity.sync.enabled=false'
$.after.audit[1].EntityId: '<booking:2>' -> 0
$.after.audit[1].EntityName: 'Booking' -> 'ExamSession'
$.after.bookings: length 6 -> 4
$.after.bookings[3].AccommodationCode: 'ET25' -> None
$.after.bookings[3].CandidateNumber: 'NW00000941' -> 'NW00000942'
$.after.bookings[3].Channel: 'PORTAL' -> 'PARTNER'
$.after.sessions[0].BookedOrAttended: 6 -> 4
$.after.sessions[0].ExtraTimeBooked: 1 -> 0
$.after.sessions[0].SeatsAvailable: 2 -> 4
```
