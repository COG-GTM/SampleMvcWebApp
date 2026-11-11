# Characterization replay: target `modern`

- commit: `e373d7e`
- portal: http://localhost:8090  api: http://localhost:8090  sync: http://localhost:8090
- reseed: `command`  compare: `http`  SEED_AS_OF: `2026-11-10`
- result: **19/64 transcripts match**

| Scenario | Channel | Oddity | Result |
|---|---|---|---|
| `BK-01-portal-book-open-session` | portal | ODD-01 | FAIL |
| `BK-02-api-book-open-session` | api | ODD-01 | PASS |
| `BK-03-portal-book-full-session` | portal |  | FAIL |
| `BK-04-api-book-full-session` | api |  | PASS |
| `BK-05-portal-duplicate-booking` | portal | ODD-12 | FAIL |
| `BK-06-api-duplicate-booking` | api | ODD-12 | PASS |
| `BK-07-db-duplicate-and-closed-error-codes` | db | ODD-11 | FAIL |
| `BK-08-portal-book-closed-session` | portal | ODD-12 | FAIL |
| `BK-09-api-book-closed-session` | api | ODD-12 | PASS |
| `BK-10-portal-book-cancelled-session` | portal | ODD-12 | FAIL |
| `BK-11-api-book-cancelled-session` | api | ODD-12 | PASS |
| `CN-01-portal-cancel-booked` | portal | ODD-01 | ERROR |
| `CN-02-api-cancel-booked-partner` | api | ODD-01, ODD-13 | PASS |
| `CN-03-portal-cancel-waitlisted` | portal |  | ERROR |
| `CN-04-api-cancel-waitlisted` | api |  | PASS |
| `CN-05-portal-cancel-on-full-session-no-promotion` | portal | ODD-09 | FAIL |
| `CN-06-api-cancel-on-full-session-no-promotion` | api | ODD-09 | PASS |
| `CN-07-portal-cancel-twice` | portal | ODD-12 | ERROR |
| `CN-08-api-cancel-twice` | api | ODD-12 | PASS |
| `RS-01-portal-reschedule-same-exam` | portal | ODD-05 | ERROR |
| `RS-02-api-reschedule-same-exam` | api | ODD-05, ODD-01 | PASS |
| `RS-03-portal-reschedule-different-exam` | portal | ODD-12 | ERROR |
| `RS-04-api-reschedule-different-exam` | api | ODD-12 | PASS |
| `RS-05-portal-reschedule-same-session` | portal | ODD-08 | ERROR |
| `RS-06-api-reschedule-same-session` | api | ODD-08 | PASS |
| `RS-07-db-reschedule-different-exam-error` | db | ODD-11 | ERROR |
| `RS-08-portal-reschedule-partner-booking` | portal | ODD-10, ODD-13 | FAIL |
| `RS-09-api-reschedule-to-full-session` | api | ODD-05 | PASS |
| `RS-10-portal-reschedule-to-full-session` | portal | ODD-05 | ERROR |
| `CP-01-portal-capacity-increase` | portal |  | FAIL |
| `CP-02-portal-capacity-decrease-above-active` | portal |  | FAIL |
| `CP-03-portal-capacity-decrease-below-active` | portal | ODD-02 | FAIL |
| `CP-04-portal-capacity-raise-then-lower` | portal | ODD-02 | FAIL |
| `CP-05-portal-capacity-below-accommodation` | portal | ODD-06, ODD-12 | FAIL |
| `CP-06-db-capacity-below-accommodation-error` | db | ODD-06, ODD-11 | PASS |
| `CP-07-portal-capacity-decrease-demotes-newest-mixed-channels` | portal | ODD-02, ODD-01 | FAIL |
| `CP-08-portal-capacity-equal-to-accommodation` | portal | ODD-02 | FAIL |
| `CP-09-db-capacity-decrease-below-active` | db | ODD-02 | PASS |
| `RT-01-eligibility-nw00000113-2026-11-20` | both |  | FAIL |
| `RT-02-eligibility-nw00000113-2026-12-06` | both |  | FAIL |
| `RT-03-eligibility-nw00000113-2026-12-07` | both | ODD-04 | FAIL |
| `RT-04-eligibility-nw00000113-2026-12-12` | both | ODD-04 | FAIL |
| `RT-05-eligibility-nw00000113-2026-12-17` | both | ODD-04 | FAIL |
| `RT-06-eligibility-nw00000113-2026-12-18` | both |  | FAIL |
| `RT-07-eligibility-nw00000113-2026-12-21` | both |  | FAIL |
| `RT-08-eligibility-nw00000950-2026-11-27` | both |  | FAIL |
| `RT-09-eligibility-nw00000950-2026-11-29` | both | ODD-04 | FAIL |
| `RT-10-eligibility-nw00000950-2026-11-30` | both | ODD-04 | FAIL |
| `RT-11-eligibility-nw00000950-2026-12-11` | both |  | FAIL |
| `RT-12-eligibility-nw00000362-2026-12-21` | both |  | FAIL |
| `RT-13-eligibility-nw00000901-2026-11-20` | both |  | FAIL |
| `RT-14-portal-book-retake-between-boundaries` | portal | ODD-04 | FAIL |
| `RT-15-api-book-retake-between-boundaries` | api | ODD-04 | FAIL |
| `AC-01-portal-extra-time-over-allocation` | portal | ODD-12 | FAIL |
| `AC-02-api-extra-time-over-allocation` | api | ODD-01, ODD-12 | PASS |
| `AC-03-portal-sr-rb-do-not-consume-accommodation` | portal |  | FAIL |
| `AC-04-api-sr-rb-do-not-consume-accommodation` | api | ODD-01, ODD-13 | PASS |
| `AC-05-portal-blank-accommodation-uses-profile` | portal | ODD-07 | FAIL |
| `AC-06-api-blank-accommodation-is-none` | api | ODD-07 | PASS |
| `AC-07-portal-extra-time-on-zero-allocation` | portal | ODD-12 | FAIL |
| `AC-08-api-extra-time-on-zero-allocation` | api | ODD-12 | PASS |
| `MX-01-mixed-traffic-then-capacity-sync` | both | ODD-01, ODD-03, ODD-13 | FAIL |
| `MX-02-capacity-sync-on-fresh-seed` | sync | ODD-03 | FAIL |
| `MX-03-capacity-sync-with-accommodation-bookings` | both | ODD-03, ODD-01 | FAIL |

## BK-01-portal-book-open-session (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000901' -> '/Booking/Book?sessionId=154&candidateNumber=NW00000901'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Ryan Pennington', 'Candidate no.': 'NW00000901', 'Email': 'ryan.pennington901@example.net', 'Default accomm...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].response.status: 200 -> 404
```

## BK-03-portal-book-full-session (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000904' -> '/Booking/Book?sessionId=153&candidateNumber=NW00000904'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Chloe Okafor', 'Candidate no.': 'NW00000904', 'Email': 'chloe.okafor904@example.net', 'Default accommodatio...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].response.status: 200 -> 404
```

## BK-05-portal-duplicate-booking (FAIL)

```
$.setup[0].status: 200 -> 404
$.steps[0].response.path: '/Booking/Book' -> '/Booking/Book?sessionId=154&candidateNumber=NW00000906'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Centre': 'Bayside Assessment Centre (SFO01)', 'Room': 'Roo...)
$.steps[0].response.rendered.heading: 'Book a seat Session #154' -> None
$.steps[0].response.status: 200 -> 404
```

## BK-07-db-duplicate-and-closed-error-codes (FAIL)

```
$.setup[0].status: 200 -> 404
$.steps[0].response.errors: length 1 -> 0
$.steps[0].response.rows[0]: 'NULL' -> 'BOOKED'
```

## BK-08-portal-book-closed-session (FAIL)

```
$.steps[0].response.path: '/Booking/Book' -> '/Booking/Book?sessionId=263&candidateNumber=NW00000909'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'HPI-ACC1 – Foundations of Financial Accounting', 'Centre': 'Midtown Assessment Suites (NYC02)', 'Room': 'Su...)
$.steps[0].response.rendered.heading: 'Book a seat Session #263' -> None
$.steps[0].response.status: 200 -> 404
```

## BK-10-portal-book-cancelled-session (FAIL)

```
$.steps[0].response.path: '/Booking/Book' -> '/Booking/Book?sessionId=162&candidateNumber=NW00000911'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'BCB-AGL – Agile Delivery Essentials', 'Centre': 'Peachtree Exam Centre (ATL01)', 'Room': 'Room 102', 'Start...)
$.steps[0].response.rendered.heading: 'Book a seat Session #162' -> None
$.steps[0].response.status: 200 -> 404
```

## CN-01-portal-cancel-booked (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000913', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 129, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 107, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000913', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## CN-03-portal-cancel-waitlisted (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=153&candidateNumber=NW00000915', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 129, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 107, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=153&candidateNumber=NW00000915', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## CN-05-portal-cancel-on-full-session-no-promotion (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000001' -> '/Booking/Cancel/1602?candidateNumber=NW00000001'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Noah Nakamura', 'Candidate no.': 'NW00000001', 'Email': 'noah.nakamura1@example.net', 'Default accommodatio...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].response.status: 200 -> 404
```

## CN-07-portal-cancel-twice (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000917', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 129, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 107, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000917', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-01-portal-reschedule-same-exam (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000919', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 129, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 107, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000919', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-03-portal-reschedule-different-exam (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000921', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 129, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 107, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000921', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-05-portal-reschedule-same-session (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000924', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 129, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 107, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000924', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-07-db-reschedule-different-exam-error (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000926', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 129, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 107, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=154&candidateNumber=NW00000926', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## RS-08-portal-reschedule-partner-booking (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000927' -> '/Booking/Reschedule/<booking:1>?candidateNumber=NW00000927'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Ethan Kearney', 'Candidate no.': 'NW00000927', 'Email': 'ethan.kearney927@example.com', 'Default accommodat...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].response.status: 200 -> 404
```

## RS-10-portal-reschedule-to-full-session (ERROR)

```
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=158&candidateNumber=NW00000929', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 129, in run
    kind, res = self._exec(st, vars_, sessions, candidates)
                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
  File "/home/ubuntu/repos/SampleMvcWebApp/characterization/harness/runner.py", line 107, in _exec
    raise RuntimeError(f"step {kind} produced no id for save_as={st['save_as']}: {res['response']}")
RuntimeError: step portal_book produced no id for save_as=b1: {'status': 404, 'path': '/Booking/Book?sessionId=158&candidateNumber=NW00000929', 'rendered': {'heading': None, 'alerts': [], 'field_errors': []}}
```

## CP-01-portal-capacity-increase (FAIL)

```
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].response.status: 200 -> 404
```

## CP-02-portal-capacity-decrease-above-active (FAIL)

```
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].response.status: 200 -> 404
```

## CP-03-portal-capacity-decrease-below-active (FAIL)

```
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].response.status: 200 -> 404
```

## CP-04-portal-capacity-raise-then-lower (FAIL)

```
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.rendered.alerts: length 1 -> 0
$.steps[1].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[1].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[1].response.status: 200 -> 404
$.steps[2].response.rendered.alerts: length 1 -> 0
$.steps[2].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[2].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[2].response.status: 200 -> 404
```

## CP-05-portal-capacity-below-accommodation (FAIL)

```
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].response.status: 200 -> 404
```

## CP-07-portal-capacity-decrease-demotes-newest-mixed-channels (FAIL)

```
$.setup[0].status: 200 -> 404
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].response.status: 200 -> 404
```

## CP-08-portal-capacity-equal-to-accommodation (FAIL)

```
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Centre': 'Bayside Assessment Centre', 'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Room': 'Room North'...)
$.steps[0].response.rendered.heading: 'Capacity administration Session #154' -> None
$.steps[0].response.status: 200 -> 404
```

## RT-01-eligibility-nw00000113-2026-11-20 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '07 Dec 2026' -> None
$.steps[0].response.eligibility.eligible: 'NO' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period applies until 07 Dec 2026' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000113', 'Exam': 'HPI-ACC1', 'Proposed date': '20 Nov 2026', 'Eligible': 'NO', 'Reason': 'Retake w...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
```

## RT-02-eligibility-nw00000113-2026-12-06 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '07 Dec 2026' -> None
$.steps[0].response.eligibility.eligible: 'NO' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period applies until 07 Dec 2026' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000113', 'Exam': 'HPI-ACC1', 'Proposed date': '06 Dec 2026', 'Eligible': 'NO', 'Reason': 'Retake w...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
```

## RT-03-eligibility-nw00000113-2026-12-07 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '07 Dec 2026' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period has elapsed' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000113', 'Exam': 'HPI-ACC1', 'Proposed date': '07 Dec 2026', 'Eligible': 'YES', 'Reason': 'Retake ...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.body.eligible: False -> True
$.steps[1].response.body.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'RETAKE_ALLOWED'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.eligibility.eligible: False -> True
$.steps[1].response.eligibility.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'RETAKE_ALLOWED'
```

## RT-04-eligibility-nw00000113-2026-12-12 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '07 Dec 2026' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period has elapsed' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000113', 'Exam': 'HPI-ACC1', 'Proposed date': '12 Dec 2026', 'Eligible': 'YES', 'Reason': 'Retake ...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.body.eligible: False -> True
$.steps[1].response.body.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'RETAKE_ALLOWED'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.eligibility.eligible: False -> True
$.steps[1].response.eligibility.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'RETAKE_ALLOWED'
```

## RT-05-eligibility-nw00000113-2026-12-17 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '07 Dec 2026' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period has elapsed' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000113', 'Exam': 'HPI-ACC1', 'Proposed date': '17 Dec 2026', 'Eligible': 'YES', 'Reason': 'Retake ...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.body.eligible: False -> True
$.steps[1].response.body.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'RETAKE_ALLOWED'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.eligibility.eligible: False -> True
$.steps[1].response.eligibility.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'RETAKE_ALLOWED'
```

## RT-06-eligibility-nw00000113-2026-12-18 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '07 Dec 2026' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period has elapsed' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000113', 'Exam': 'HPI-ACC1', 'Proposed date': '18 Dec 2026', 'Eligible': 'YES', 'Reason': 'Retake ...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
```

## RT-07-eligibility-nw00000113-2026-12-21 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '07 Dec 2026' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period has elapsed' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000113', 'Exam': 'HPI-ACC1', 'Proposed date': '21 Dec 2026', 'Eligible': 'YES', 'Reason': 'Retake ...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-18' -> '2026-12-07'
```

## RT-08-eligibility-nw00000950-2026-11-27 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '29 Nov 2026' -> None
$.steps[0].response.eligibility.eligible: 'NO' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period applies until 29 Nov 2026' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000950', 'Exam': 'BCB-PM200', 'Proposed date': '27 Nov 2026', 'Eligible': 'NO', 'Reason': 'Retake ...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-11' -> None
$.steps[1].response.body.eligible: False -> True
$.steps[1].response.body.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'FIRST_ATTEMPT'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-11' -> None
$.steps[1].response.eligibility.eligible: False -> True
$.steps[1].response.eligibility.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'FIRST_ATTEMPT'
```

## RT-09-eligibility-nw00000950-2026-11-29 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '29 Nov 2026' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period has elapsed' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000950', 'Exam': 'BCB-PM200', 'Proposed date': '29 Nov 2026', 'Eligible': 'YES', 'Reason': 'Retake...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-11' -> None
$.steps[1].response.body.eligible: False -> True
$.steps[1].response.body.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'FIRST_ATTEMPT'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-11' -> None
$.steps[1].response.eligibility.eligible: False -> True
$.steps[1].response.eligibility.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'FIRST_ATTEMPT'
```

## RT-10-eligibility-nw00000950-2026-11-30 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '29 Nov 2026' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period has elapsed' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000950', 'Exam': 'BCB-PM200', 'Proposed date': '30 Nov 2026', 'Eligible': 'YES', 'Reason': 'Retake...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-11' -> None
$.steps[1].response.body.eligible: False -> True
$.steps[1].response.body.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'FIRST_ATTEMPT'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-11' -> None
$.steps[1].response.eligibility.eligible: False -> True
$.steps[1].response.eligibility.reason: 'RETAKE_WAIT_NOT_ELAPSED' -> 'FIRST_ATTEMPT'
```

## RT-11-eligibility-nw00000950-2026-12-11 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: '29 Nov 2026' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'Retake wait period has elapsed' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000950', 'Exam': 'BCB-PM200', 'Proposed date': '11 Dec 2026', 'Eligible': 'YES', 'Reason': 'Retake...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.body.earliestRetakeDate: '2026-12-11' -> None
$.steps[1].response.body.reason: 'RETAKE_ALLOWED' -> 'FIRST_ATTEMPT'
$.steps[1].response.eligibility.earliestRetakeDate: '2026-12-11' -> None
$.steps[1].response.eligibility.reason: 'RETAKE_ALLOWED' -> 'FIRST_ATTEMPT'
```

## RT-12-eligibility-nw00000362-2026-12-21 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: 'n/a' -> None
$.steps[0].response.eligibility.eligible: 'NO' -> None
$.steps[0].response.eligibility.reason: 'Candidate has already passed this exam' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000362', 'Exam': 'HPI-ACC1', 'Proposed date': '21 Dec 2026', 'Eligible': 'NO', 'Reason': 'Candidat...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
```

## RT-13-eligibility-nw00000901-2026-11-20 (FAIL)

```
$.steps[0].response.eligibility.earliestRetakeDate: 'n/a' -> None
$.steps[0].response.eligibility.eligible: 'YES' -> None
$.steps[0].response.eligibility.reason: 'No previous attempts' -> None
$.steps[0].response.rendered.cells: missing in replay (expected {'Candidate': 'NW00000901', 'Exam': 'HPI-ACC1', 'Proposed date': '20 Nov 2026', 'Eligible': 'YES', 'Reason': 'No prev...)
$.steps[0].response.rendered.heading: 'Retake eligibility' -> None
$.steps[0].response.status: 200 -> 404
```

## RT-14-portal-book-retake-between-boundaries (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000113' -> '/Booking/Book?sessionId=216&candidateNumber=NW00000113'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Adam Gallagher', 'Candidate no.': 'NW00000113', 'Email': 'adam.gallagher113@example.org', 'Default accommod...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].response.status: 200 -> 404
```

## RT-15-api-book-retake-between-boundaries (FAIL)

```
$.steps[0].response.body.bookingId: unexpected in replay ('<booking:1>')
$.steps[0].response.body.error: missing in replay (expected 'NOT_ELIGIBLE')
$.steps[0].response.body.message: missing in replay (expected 'Candidate NW00000113 is not eligible to book HPI-ACC1 (RETAKE_WAIT_NOT_ELAPSED), earliest retake date 2026-12-18')
$.steps[0].response.body.status: unexpected in replay ('BOOKED')
$.steps[0].response.status: 409 -> 201
```

## AC-01-portal-extra-time-over-allocation (FAIL)

```
$.setup[0].status: 200 -> 404
$.steps[0].response.path: '/Booking/Book' -> '/Booking/Book?sessionId=163&candidateNumber=NW00000903'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'MHL-RN1 – Registered Nurse Licensure - Part 1', 'Centre': 'Thameside Assessment Centre (LDN01)', 'Room': 'S...)
$.steps[0].response.rendered.heading: 'Book a seat Session #163' -> None
$.steps[0].response.status: 200 -> 404
```

## AC-03-portal-sr-rb-do-not-consume-accommodation (FAIL)

```
$.setup[0].status: 200 -> 404
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000923' -> '/Booking/Book?sessionId=163&candidateNumber=NW00000923'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Arjun Whitlock', 'Candidate no.': 'NW00000923', 'Email': 'arjun.whitlock923@example.org', 'Default accommod...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].response.status: 200 -> 404
$.steps[1].response.path: '/Booking/MyBookings?candidateNumber=NW00000943' -> '/Booking/Book?sessionId=163&candidateNumber=NW00000943'
$.steps[1].response.rendered.alerts: length 1 -> 0
$.steps[1].response.rendered.cells: missing in replay (expected {'Name': 'Oliver Castell', 'Candidate no.': 'NW00000943', 'Email': 'oliver.castell943@example.net', 'Default accommod...)
$.steps[1].response.rendered.heading: 'My bookings' -> None
$.steps[1].response.status: 200 -> 404
```

## AC-05-portal-blank-accommodation-uses-profile (FAIL)

```
$.steps[0].response.path: '/Booking/MyBookings?candidateNumber=NW00000003' -> '/Booking/Book?sessionId=154&candidateNumber=NW00000003'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Name': 'Arjun Pryce', 'Candidate no.': 'NW00000003', 'Email': 'arjun.pryce3@example.com', 'Default accommodation': ...)
$.steps[0].response.rendered.heading: 'My bookings' -> None
$.steps[0].response.status: 200 -> 404
```

## AC-07-portal-extra-time-on-zero-allocation (FAIL)

```
$.steps[0].response.path: '/Booking/Book' -> '/Booking/Book?sessionId=155&candidateNumber=NW00000939'
$.steps[0].response.rendered.alerts: length 1 -> 0
$.steps[0].response.rendered.cells: missing in replay (expected {'Exam': 'HPI-ACC2 – Management Accounting', 'Centre': 'Liffey Assessment Rooms (DUB01)', 'Room': 'Room 1', 'Start': ...)
$.steps[0].response.rendered.heading: 'Book a seat Session #155' -> None
$.steps[0].response.status: 200 -> 404
```

## MX-01-mixed-traffic-then-capacity-sync (FAIL)

```
$.steps[1].response.calls: length 46 -> 43
$.steps[1].response.calls[0].alerts: length 1 -> 0
$.steps[1].response.calls[0].status: 200 -> 404
$.steps[1].response.calls[1].body.bookingId: '<booking:2>' -> '<booking:1>'
$.steps[1].response.calls[2].alerts: length 1 -> 0
$.steps[1].response.calls[2].status: 200 -> 404
$.steps[1].response.calls[3].body.bookingId: '<booking:4>' -> '<booking:2>'
$.steps[1].response.calls[4].alerts: length 1 -> 0
$.steps[1].response.calls[4].status: 200 -> 404
$.steps[1].response.calls[5].body.bookingId: '<booking:6>' -> '<booking:3>'
$.steps[1].response.calls[6].alerts: length 1 -> 0
$.steps[1].response.calls[6].status: 200 -> 404
$.steps[1].response.calls[7].body.bookingId: '<booking:8>' -> '<booking:4>'
$.steps[1].response.calls[8].alerts: length 1 -> 0
$.steps[1].response.calls[8].status: 200 -> 404
$.steps[1].response.calls[9].body.bookingId: '<booking:10>' -> '<booking:5>'
$.steps[1].response.calls[10].alerts: length 1 -> 0
$.steps[1].response.calls[10].status: 200 -> 404
$.steps[1].response.calls[11].body.bookingId: '<booking:12>' -> '<booking:6>'
$.steps[1].response.calls[12].alerts: length 1 -> 0
$.steps[1].response.calls[12].status: 200 -> 404
$.steps[1].response.calls[13].body.bookingId: '<booking:14>' -> '<booking:7>'
$.steps[1].response.calls[14].alerts: length 1 -> 0
$.steps[1].response.calls[14].status: 200 -> 404
$.steps[1].response.calls[15].body.bookingId: '<booking:16>' -> '<booking:8>'
$.steps[1].response.calls[15].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[16].alerts: length 1 -> 0
$.steps[1].response.calls[16].status: 200 -> 404
$.steps[1].response.calls[17].body.bookingId: '<booking:18>' -> '<booking:9>'
$.steps[1].response.calls[18].alerts: length 1 -> 0
$.steps[1].response.calls[18].status: 200 -> 404
$.steps[1].response.calls[19].body.bookingId: '<booking:20>' -> '<booking:10>'
$.steps[1].response.calls[19].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[20].alerts: length 1 -> 0
$.steps[1].response.calls[20].status: 200 -> 404
$.steps[1].response.calls[21].body.bookingId: '<booking:22>' -> '<booking:11>'
$.steps[1].response.calls[22].alerts: length 1 -> 0
$.steps[1].response.calls[22].status: 200 -> 404
$.steps[1].response.calls[23].body.bookingId: '<booking:24>' -> '<booking:12>'
$.steps[1].response.calls[24].alerts: length 1 -> 0
$.steps[1].response.calls[24].status: 200 -> 404
$.steps[1].response.calls[25].body.bookingId: '<booking:26>' -> '<booking:13>'
$.steps[1].response.calls[25].body.status: 'WAITLISTED' -> 'BOOKED'
$.steps[1].response.calls[26].alerts: length 1 -> 0
$.steps[1].response.calls[26].status: 200 -> 404
$.steps[1].response.calls[27].body.bookingId: '<booking:28>' -> '<booking:14>'
$.steps[1].response.calls[28].alerts: length 1 -> 0
$.steps[1].response.calls[28].status: 200 -> 404
$.steps[1].response.calls[29].body.bookingId: '<booking:30>' -> '<booking:15>'
$.steps[1].response.calls[29].body.status: 'WAITLISTED' -> 'BOOKED'
```

## MX-02-capacity-sync-on-fresh-seed (FAIL)

```
$.steps[1].response.body.error: unexpected in replay ('Not Found')
$.steps[1].response.body.finishedAt: missing in replay (expected '<now>')
$.steps[1].response.body.message: None -> 'No static resource sync/run.'
$.steps[1].response.body.path: unexpected in replay ('/sync/run')
$.steps[1].response.body.sessionsChecked: missing in replay (expected 285)
$.steps[1].response.body.sessionsUpdated: missing in replay (expected 112)
$.steps[1].response.body.startedAt: missing in replay (expected '<now>')
$.steps[1].response.body.status: 'COMPLETED' -> 404
$.steps[1].response.body.timestamp: unexpected in replay ('<now>')
$.steps[1].response.body.trigger: missing in replay (expected 'MANUAL')
$.steps[1].response.status: 200 -> 404
$.steps[2].response.drifting_sessions: 112 -> 0
$.steps[2].response.rows: length 112 -> 0
$.steps[2].response.total_drift: 177 -> 0
```

## MX-03-capacity-sync-with-accommodation-bookings (FAIL)

```
$.setup[0].status: 200 -> 404
$.setup[2].status: 200 -> 404
$.steps[0].response.drifting_sessions: 1 -> 0
$.steps[0].response.rows: length 1 -> 0
$.steps[0].response.total_drift: 1 -> 0
$.steps[1].response.body.error: unexpected in replay ('Not Found')
$.steps[1].response.body.finishedAt: missing in replay (expected '<now>')
$.steps[1].response.body.message: None -> 'No static resource sync/run.'
$.steps[1].response.body.path: unexpected in replay ('/sync/run')
$.steps[1].response.body.sessionsChecked: missing in replay (expected 285)
$.steps[1].response.body.sessionsUpdated: missing in replay (expected 112)
$.steps[1].response.body.startedAt: missing in replay (expected '<now>')
$.steps[1].response.body.status: 'COMPLETED' -> 404
$.steps[1].response.body.timestamp: unexpected in replay ('<now>')
$.steps[1].response.body.trigger: missing in replay (expected 'MANUAL')
$.steps[1].response.status: 200 -> 404
$.steps[2].response.drifting_sessions: 111 -> 0
$.steps[2].response.rows: length 111 -> 0
$.steps[2].response.total_drift: 176 -> 0
```
