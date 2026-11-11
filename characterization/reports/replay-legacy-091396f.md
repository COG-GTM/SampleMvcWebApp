# Characterization replay: target `legacy`

- commit: `091396f`
- portal: http://localhost:8080  api: http://localhost:8081  sync: http://localhost:8083
- reseed: `restore`  compare: `all`  SEED_AS_OF: `2026-11-10`
- result: **64/64 transcripts match**

| Scenario | Channel | Oddity | Result |
|---|---|---|---|
| `BK-01-portal-book-open-session` | portal | ODD-01 | PASS |
| `BK-02-api-book-open-session` | api | ODD-01 | PASS |
| `BK-03-portal-book-full-session` | portal |  | PASS |
| `BK-04-api-book-full-session` | api |  | PASS |
| `BK-05-portal-duplicate-booking` | portal | ODD-12 | PASS |
| `BK-06-api-duplicate-booking` | api | ODD-12 | PASS |
| `BK-07-db-duplicate-and-closed-error-codes` | db | ODD-11 | PASS |
| `BK-08-portal-book-closed-session` | portal | ODD-12 | PASS |
| `BK-09-api-book-closed-session` | api | ODD-12 | PASS |
| `BK-10-portal-book-cancelled-session` | portal | ODD-12 | PASS |
| `BK-11-api-book-cancelled-session` | api | ODD-12 | PASS |
| `CN-01-portal-cancel-booked` | portal | ODD-01 | PASS |
| `CN-02-api-cancel-booked-partner` | api | ODD-01, ODD-13 | PASS |
| `CN-03-portal-cancel-waitlisted` | portal |  | PASS |
| `CN-04-api-cancel-waitlisted` | api |  | PASS |
| `CN-05-portal-cancel-on-full-session-no-promotion` | portal | ODD-09 | PASS |
| `CN-06-api-cancel-on-full-session-no-promotion` | api | ODD-09 | PASS |
| `CN-07-portal-cancel-twice` | portal | ODD-12 | PASS |
| `CN-08-api-cancel-twice` | api | ODD-12 | PASS |
| `RS-01-portal-reschedule-same-exam` | portal | ODD-05 | PASS |
| `RS-02-api-reschedule-same-exam` | api | ODD-05, ODD-01 | PASS |
| `RS-03-portal-reschedule-different-exam` | portal | ODD-12 | PASS |
| `RS-04-api-reschedule-different-exam` | api | ODD-12 | PASS |
| `RS-05-portal-reschedule-same-session` | portal | ODD-08 | PASS |
| `RS-06-api-reschedule-same-session` | api | ODD-08 | PASS |
| `RS-07-db-reschedule-different-exam-error` | db | ODD-11 | PASS |
| `RS-08-portal-reschedule-partner-booking` | portal | ODD-10, ODD-13 | PASS |
| `RS-09-api-reschedule-to-full-session` | api | ODD-05 | PASS |
| `RS-10-portal-reschedule-to-full-session` | portal | ODD-05 | PASS |
| `CP-01-portal-capacity-increase` | portal |  | PASS |
| `CP-02-portal-capacity-decrease-above-active` | portal |  | PASS |
| `CP-03-portal-capacity-decrease-below-active` | portal | ODD-02 | PASS |
| `CP-04-portal-capacity-raise-then-lower` | portal | ODD-02 | PASS |
| `CP-05-portal-capacity-below-accommodation` | portal | ODD-06, ODD-12 | PASS |
| `CP-06-db-capacity-below-accommodation-error` | db | ODD-06, ODD-11 | PASS |
| `CP-07-portal-capacity-decrease-demotes-newest-mixed-channels` | portal | ODD-02, ODD-01 | PASS |
| `CP-08-portal-capacity-equal-to-accommodation` | portal | ODD-02 | PASS |
| `CP-09-db-capacity-decrease-below-active` | db | ODD-02 | PASS |
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
| `RT-14-portal-book-retake-between-boundaries` | portal | ODD-04 | PASS |
| `RT-15-api-book-retake-between-boundaries` | api | ODD-04 | PASS |
| `AC-01-portal-extra-time-over-allocation` | portal | ODD-12 | PASS |
| `AC-02-api-extra-time-over-allocation` | api | ODD-01, ODD-12 | PASS |
| `AC-03-portal-sr-rb-do-not-consume-accommodation` | portal |  | PASS |
| `AC-04-api-sr-rb-do-not-consume-accommodation` | api | ODD-01, ODD-13 | PASS |
| `AC-05-portal-blank-accommodation-uses-profile` | portal | ODD-07 | PASS |
| `AC-06-api-blank-accommodation-is-none` | api | ODD-07 | PASS |
| `AC-07-portal-extra-time-on-zero-allocation` | portal | ODD-12 | PASS |
| `AC-08-api-extra-time-on-zero-allocation` | api | ODD-12 | PASS |
| `MX-01-mixed-traffic-then-capacity-sync` | both | ODD-01, ODD-03, ODD-13 | PASS |
| `MX-02-capacity-sync-on-fresh-seed` | sync | ODD-03 | PASS |
| `MX-03-capacity-sync-with-accommodation-bookings` | both | ODD-03, ODD-01 | PASS |
