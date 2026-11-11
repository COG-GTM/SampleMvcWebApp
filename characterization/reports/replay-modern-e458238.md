# Characterization replay: target `modern`

- Transcripts captured from tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f`
- Replayed estate commit: `69f3c6faebc65b2cc1899c8dc547e494daf3e81b`
- Target: `modern` base url `http://localhost:8090`
- Options: `target=modern partner-api=v2 db-state=scheduling`

**Overall: 44/86 MATCH**

Status counts: MATCH 44, MISMATCH 29, KNOWN-DEFECT 1, SKIPPED 12, ERROR 0

KNOWN-DEFECT = differs from the legacy transcript for a reason listed in `characterization/known-defects.yaml`; counted separately (never as MATCH, never hidden).

## Totals per scenario group

| Group | Result | Mismatch | Known defect | Skipped | Error |
|---|---|---|---|---|---|
| booking | 4/9 MATCH | 3 | 0 | 2 | 0 |
| full_session | 2/2 MATCH | 0 | 0 | 0 | 0 |
| duplicate_closed | 6/6 MATCH | 0 | 0 | 0 | 0 |
| cancellation | 4/9 MATCH | 5 | 0 | 0 | 0 |
| reschedule | 7/10 MATCH | 3 | 0 | 0 | 0 |
| capacity_increase | 1/3 MATCH | 1 | 0 | 1 | 0 |
| capacity_decrease | 1/8 MATCH | 5 | 1 | 1 | 0 |
| retake_eligibility_portal | 10/10 MATCH | 0 | 0 | 0 | 0 |
| retake_eligibility_partner | 3/10 MATCH | 7 | 0 | 0 | 0 |
| accommodation | 6/11 MATCH | 5 | 0 | 0 | 0 |
| mixed_traffic | 0/4 MATCH | 0 | 0 | 4 | 0 |
| contracts | 0/4 MATCH | 0 | 0 | 4 | 0 |

## Per scenario

| Scenario | Group | Channel | Oddity | Result | Note |
|---|---|---|---|---|---|
| book_inactive_center_partner | booking | PARTNER | yes | MISMATCH |  |
| book_inactive_center_portal | booking | PORTAL | yes | SKIPPED | PORTAL has no search operation on target modern |
| book_open_session_partner | booking | PARTNER | yes | MISMATCH | ADR 0002 - seat count is derived (Capacity - active bookings); no double decrement. |
| book_open_session_portal | booking | PORTAL |  | MATCH |  |
| book_passed_candidate_partner | booking | PARTNER |  | MATCH |  |
| book_passed_candidate_portal | booking | PORTAL |  | MATCH |  |
| book_passed_candidate_proc_db | booking | DB | yes | SKIPPED | sql steps act on the legacy estate directly; no equivalent on target modern |
| last_seat_negative_counter_partner | booking | PARTNER | yes | MISMATCH |  |
| last_seat_portal | booking | PORTAL |  | MATCH |  |
| book_full_session_partner | full_session | PARTNER |  | MATCH |  |
| book_full_session_portal | full_session | PORTAL |  | MATCH |  |
| book_cancelled_session_partner | duplicate_closed | PARTNER |  | MATCH |  |
| book_cancelled_session_portal | duplicate_closed | PORTAL |  | MATCH |  |
| book_closed_session_partner | duplicate_closed | PARTNER |  | MATCH |  |
| book_closed_session_portal | duplicate_closed | PORTAL |  | MATCH |  |
| book_duplicate_partner | duplicate_closed | PARTNER |  | MATCH |  |
| book_duplicate_portal | duplicate_closed | PORTAL |  | MATCH |  |
| cancel_booked_on_full_session_partner | cancellation | PARTNER | yes | MATCH |  |
| cancel_booked_on_full_session_portal | cancellation | PORTAL | yes | MATCH |  |
| cancel_on_delivered_session_partner | cancellation | PARTNER | yes | MISMATCH |  |
| cancel_on_delivered_session_portal | cancellation | PORTAL | yes | MISMATCH |  |
| cancel_other_candidates_booking_portal | cancellation | PORTAL | yes | MISMATCH |  |
| cancel_then_new_booker_gets_seat_partner | cancellation | PARTNER | yes | MISMATCH |  |
| cancel_then_new_booker_gets_seat_portal | cancellation | PORTAL | yes | MISMATCH |  |
| cancel_waitlisted_partner | cancellation | PARTNER |  | MATCH |  |
| cancel_waitlisted_portal | cancellation | PORTAL |  | MATCH |  |
| reschedule_different_exam_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_different_exam_portal | reschedule | PORTAL |  | MATCH |  |
| reschedule_same_exam_partner | reschedule | PARTNER | yes | MISMATCH | ADR 0002 - derived seat count, no double decrement on the rebooking. |
| reschedule_same_exam_portal | reschedule | PORTAL |  | MISMATCH |  |
| reschedule_same_session_partner | reschedule | PARTNER | yes | MATCH |  |
| reschedule_same_session_portal | reschedule | PORTAL | yes | MISMATCH | ADR 0002 section 6b - every channel rejects with 409 SAME_SESSION. |
| reschedule_to_delivered_session_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_to_delivered_session_portal | reschedule | PORTAL |  | MATCH |  |
| reschedule_to_full_session_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_to_full_session_portal | reschedule | PORTAL |  | MATCH |  |
| capacity_9999_admin | capacity_increase | ADMIN | yes | MATCH |  |
| capacity_direct_update_increase_db | capacity_increase | DB | yes | SKIPPED | sql steps act on the legacy estate directly; no equivalent on target modern |
| capacity_increase_admin | capacity_increase | ADMIN |  | MISMATCH | ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat). |
| capacity_below_accommodation_floor_admin | capacity_decrease | ADMIN |  | MATCH |  |
| capacity_decrease_above_active_admin | capacity_decrease | ADMIN |  | MISMATCH | ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat). |
| capacity_decrease_below_active_admin | capacity_decrease | ADMIN | yes | MISMATCH | ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat). |
| capacity_decrease_delivered_with_attended_admin | capacity_decrease | ADMIN | yes | MISMATCH |  |
| capacity_decrease_full_session_with_waitlist_admin | capacity_decrease | ADMIN | yes | KNOWN-DEFECT | on-call defect #57 - scheduling-service rejects the reduction with 409 CAPACITY_BELOW_ACCOMMODATION_FLOOR (50005); legacy accepts it and bumps the two newest bookings |
| capacity_direct_update_decrease_db | capacity_decrease | DB | yes | SKIPPED | sql steps act on the legacy estate directly; no equivalent on target modern |
| capacity_negative_admin | capacity_decrease | ADMIN |  | MISMATCH |  |
| capacity_raise_then_lower_admin | capacity_decrease | ADMIN | yes | MISMATCH | ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat). |
| retake_already_passed_portal | retake_eligibility_portal | PORTAL |  | MATCH |  |
| retake_calendar_boundary_portal | retake_eligibility_portal | PORTAL | yes | MATCH |  |
| retake_day_before_calendar_boundary_portal | retake_eligibility_portal | PORTAL | yes | MATCH |  |
| retake_first_attempt_portal | retake_eligibility_portal | PORTAL |  | MATCH |  |
| retake_inside_window_portal | retake_eligibility_portal | PORTAL | yes | MATCH |  |
| retake_month_end_boundary_portal | retake_eligibility_portal | PORTAL | yes | MATCH |  |
| retake_no_show_not_counted_portal | retake_eligibility_portal | PORTAL |  | MATCH |  |
| retake_outside_window_portal | retake_eligibility_portal | PORTAL | yes | MATCH |  |
| retake_weekend_span_between_rules_portal | retake_eligibility_portal | PORTAL | yes | MATCH |  |
| retake_working_day_boundary_portal | retake_eligibility_portal | PORTAL | yes | MATCH |  |
| retake_already_passed_partner | retake_eligibility_partner | PARTNER |  | MATCH |  |
| retake_calendar_boundary_partner | retake_eligibility_partner | PARTNER | yes | MISMATCH | ADR 0002 - one retake rule for both channels; the losing channel mismatches. |
| retake_day_before_calendar_boundary_partner | retake_eligibility_partner | PARTNER | yes | MISMATCH | ADR 0002 - one retake rule for both channels; the losing channel's earliestRetakeDate mismatches. |
| retake_first_attempt_partner | retake_eligibility_partner | PARTNER |  | MATCH |  |
| retake_inside_window_partner | retake_eligibility_partner | PARTNER | yes | MISMATCH | ADR 0002 - one retake rule for both channels; the losing channel's earliestRetakeDate mismatches. |
| retake_month_end_boundary_partner | retake_eligibility_partner | PARTNER | yes | MISMATCH | ADR 0002 - one retake rule for both channels; the losing channel mismatches. |
| retake_no_show_not_counted_partner | retake_eligibility_partner | PARTNER |  | MATCH |  |
| retake_outside_window_partner | retake_eligibility_partner | PARTNER | yes | MISMATCH | ADR 0002 - one retake rule for both channels; the losing channel's earliestRetakeDate mismatches. |
| retake_weekend_span_between_rules_partner | retake_eligibility_partner | PARTNER | yes | MISMATCH | ADR 0002 - one retake rule for both channels; the losing channel mismatches. |
| retake_working_day_boundary_partner | retake_eligibility_partner | PARTNER | yes | MISMATCH | ADR 0002 - one retake rule for both channels; the losing channel's earliestRetakeDate mismatches. |
| accommodation_blank_uses_default_partner | accommodation | PARTNER | yes | MISMATCH | ADR 0002 section 1 - derived seat count, no double decrement. |
| accommodation_blank_uses_default_portal | accommodation | PORTAL | yes | MISMATCH | ADR 0002 section 6a - the API books exactly what it is sent (the modern UI pre-fills the default instead). |
| accommodation_capacity_exhausted_partner | accommodation | PARTNER | yes | MISMATCH | ADR 0002 - derived seat count, no double decrement. |
| accommodation_capacity_exhausted_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_capacity_two_seats_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_pool_not_held_back_partner | accommodation | PARTNER | yes | MATCH |  |
| accommodation_pool_not_held_back_portal | accommodation | PORTAL | yes | MATCH |  |
| accommodation_sr_rb_not_counted_partner | accommodation | PARTNER | yes | MISMATCH | ADR 0002 - derived seat count, no double decrement. |
| accommodation_sr_rb_not_counted_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_unknown_code_partner | accommodation | PARTNER |  | MATCH |  |
| accommodation_unknown_code_portal | accommodation | PORTAL |  | MISMATCH |  |
| capacity_sync_fresh_seed_system | mixed_traffic | SYSTEM | yes | SKIPPED | capacity-sync has no equivalent on target modern |
| capacity_sync_with_accommodation_bookings | mixed_traffic | SYSTEM | yes | SKIPPED | capacity-sync has no equivalent on target modern |
| mixed_traffic_then_capacity_sync | mixed_traffic | SYSTEM | yes | SKIPPED | mixed traffic needs portal + v1 endpoints (use the routing gateway) |
| result_ingest_marks_attended_system | mixed_traffic | SYSTEM | yes | SKIPPED | result steps act on the legacy estate directly; no equivalent on target modern |
| direct_sql_cancel_and_import_db | contracts | DB | yes | SKIPPED | sql steps act on the legacy estate directly; no equivalent on target modern |
| proc_error_contract_db | contracts | DB |  | SKIPPED | sql steps act on the legacy estate directly; no equivalent on target modern |
| session_search_portal | contracts | PORTAL |  | SKIPPED | PORTAL has no search operation on target modern |
| sessions_contract_partner | contracts | PARTNER |  | SKIPPED | PARTNER has no sessions operation on target modern |

## Mismatches

### accommodation_blank_uses_default_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - derived seat count, no double decrement.

Request (per step):

```json
[
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000903",
      "externalRef": "NAN-REF-0030",
      "sessionId": 462
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.seatsAvailable` | `13` | `14` |
| `db_state.examSession.462.SeatsAvailable` | `13` | `14` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 14,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 13,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### accommodation_blank_uses_default_portal (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 6a - the API books exactly what it is sent (the modern UI pre-fills the default instead).

Request (per step):

```json
[
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000903",
      "SessionId": "462"
    },
    "method": "POST",
    "path": "/Booking/Book"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `db_state.bookings.462[1].accommodation` | `"ET50"` | `null` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 14,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 14,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### accommodation_capacity_exhausted_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - derived seat count, no double decrement.

Request (per step):

```json
[
  {
    "json": {
      "accommodationCode": "ET25",
      "candidateNumber": "NW00000901",
      "externalRef": null,
      "sessionId": 462
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "json": {
      "accommodationCode": "ET50",
      "candidateNumber": "NW00000902",
      "externalRef": null,
      "sessionId": 462
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.seatsAvailable` | `13` | `14` |
| `step2.business.seatsAvailable` | `13` | `14` |
| `db_state.examSession.462.SeatsAvailable` | `13` | `14` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 14,
      "waitlistPosition": null
    },
    {
      "error": "NO_ACCOMMODATION_SEAT",
      "legacyCode": 50003,
      "outcome": "ERROR",
      "seatsAvailable": 14,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 13,
      "waitlistPosition": null
    },
    {
      "error": "NO_ACCOMMODATION_SEAT",
      "legacyCode": 50003,
      "outcome": "ERROR",
      "seatsAvailable": 13,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### accommodation_sr_rb_not_counted_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - derived seat count, no double decrement.

Request (per step):

```json
[
  {
    "json": {
      "accommodationCode": "SR",
      "candidateNumber": "NW00000901",
      "externalRef": null,
      "sessionId": 462
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "json": {
      "accommodationCode": "RB",
      "candidateNumber": "NW00000902",
      "externalRef": null,
      "sessionId": 462
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "json": {
      "accommodationCode": "ET25",
      "candidateNumber": "NW00000904",
      "externalRef": null,
      "sessionId": 462
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.seatsAvailable` | `13` | `14` |
| `step2.business.seatsAvailable` | `11` | `13` |
| `step3.business.seatsAvailable` | `9` | `12` |
| `db_state.examSession.462.SeatsAvailable` | `9` | `12` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 14,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 13,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#3",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 12,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 13,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 11,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#3",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### accommodation_unknown_code_portal (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "form": {
      "AccommodationCode": "XX",
      "CandidateNumber": "NW00000901",
      "SessionId": "462"
    },
    "method": "POST",
    "path": "/Booking/Book"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.error` | `"PORTAL_ERROR"` | `"VALIDATION"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "error": "VALIDATION",
      "legacyCode": null,
      "outcome": "ERROR",
      "seatsAvailable": 15,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "error": "PORTAL_ERROR",
      "legacyCode": null,
      "outcome": "ERROR",
      "seatsAvailable": 15,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### book_inactive_center_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000901",
      "externalRef": "NAN-REF-0046",
      "sessionId": 205
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.seatsAvailable` | `0` | `1` |
| `db_state.examSession.205.SeatsAvailable` | `0` | `1` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 1,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 0,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### book_open_session_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - seat count is derived (Capacity - active bookings); no double decrement.

Request (per step):

```json
[
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000901",
      "externalRef": "NAN-REF-0001",
      "sessionId": 462
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.seatsAvailable` | `13` | `14` |
| `db_state.examSession.462.SeatsAvailable` | `13` | `14` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 14,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 13,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### last_seat_negative_counter_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000901",
      "externalRef": "NAN-REF-0041",
      "sessionId": 235
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000902",
      "externalRef": "NAN-REF-0042",
      "sessionId": 235
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.seatsAvailable` | `-1` | `0` |
| `step2.business.seatsAvailable` | `-1` | `0` |
| `db_state.examSession.235.SeatsAvailable` | `-1` | `0` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 0,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "WAITLISTED",
      "seatsAvailable": 0,
      "waitlistPosition": 1
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": -1,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "WAITLISTED",
      "seatsAvailable": -1,
      "waitlistPosition": 1
    }
  ]
}
```

</details>

### cancel_on_delivered_session_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "method": "DELETE",
    "path": "/api/v1/bookings/3319"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `db_state.bookings.147[10].cancelReason` | `"Cancelled by partner"` | `"Cancelled"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    }
  ],
  "expected": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    }
  ]
}
```

</details>

### cancel_on_delivered_session_portal (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "form": {
      "CandidateNumber": "",
      "Reason": ""
    },
    "method": "POST",
    "path": "/Booking/Cancel/3319"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `db_state.bookings.147[10].cancelReason` | `null` | `"Cancelled"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    }
  ],
  "expected": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    }
  ]
}
```

</details>

### cancel_other_candidates_booking_portal (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "form": {
      "CandidateNumber": "NW00000901",
      "Reason": ""
    },
    "method": "POST",
    "path": "/Booking/Cancel/3586"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `db_state.bookings.462[0].cancelReason` | `null` | `"Cancelled"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    }
  ],
  "expected": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    }
  ]
}
```

</details>

### cancel_then_new_booker_gets_seat_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "method": "DELETE",
    "path": "/api/v1/bookings/1602"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000901",
      "externalRef": "NAN-REF-0040",
      "sessionId": 153
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step2.business.seatsAvailable` | `-1` | `0` |
| `db_state.bookings.153[21].cancelReason` | `"Cancelled by partner"` | `"Cancelled"` |
| `db_state.examSession.153.SeatsAvailable` | `-1` | `0` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    },
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 0,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    },
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": -1,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### cancel_then_new_booker_gets_seat_portal (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "form": {
      "CandidateNumber": "",
      "Reason": ""
    },
    "method": "POST",
    "path": "/Booking/Cancel/1602"
  },
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000901",
      "SessionId": "153"
    },
    "method": "POST",
    "path": "/Booking/Book"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `db_state.bookings.153[21].cancelReason` | `null` | `"Cancelled"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    },
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 0,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "error": null,
      "legacyCode": null,
      "outcome": "CANCELLED"
    },
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 0,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### capacity_decrease_above_active_admin (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat).

Request (per step):

```json
[
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000901",
      "SessionId": "191"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000902",
      "SessionId": "191"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000904",
      "externalRef": "NAN-REF-0010",
      "sessionId": 191
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000905",
      "externalRef": "NAN-REF-0011",
      "sessionId": 191
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "form": {
      "NewCapacity": "8"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/191"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step3.business.seatsAvailable` | `6` | `7` |
| `step4.business.seatsAvailable` | `4` | `6` |
| `step5.business.seatsAvailable` | `0` | `2` |
| `db_state.examSession.191.SeatsAvailable` | `0` | `2` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 8,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#3",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 7,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#4",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 6,
      "waitlistPosition": null
    },
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 2
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 8,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#3",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 6,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#4",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 4,
      "waitlistPosition": null
    },
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 0
    }
  ]
}
```

</details>

### capacity_decrease_below_active_admin (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat).

Request (per step):

```json
[
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000901",
      "SessionId": "191"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000902",
      "SessionId": "191"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000904",
      "externalRef": "NAN-REF-0010",
      "sessionId": 191
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000905",
      "externalRef": "NAN-REF-0011",
      "sessionId": 191
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "form": {
      "NewCapacity": "4"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/191"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step3.business.seatsAvailable` | `6` | `7` |
| `step4.business.seatsAvailable` | `4` | `6` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 8,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#3",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 7,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#4",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 6,
      "waitlistPosition": null
    },
    {
      "bumped": [
        "new#3",
        "new#4"
      ],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 0
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 8,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#3",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 6,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#4",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 4,
      "waitlistPosition": null
    },
    {
      "bumped": [
        "new#3",
        "new#4"
      ],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 0
    }
  ]
}
```

</details>

### capacity_decrease_delivered_with_attended_admin (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "form": {
      "NewCapacity": "8"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/147"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.bumped` | `[]` | `[1534, 1535, 1536]` |
| `db_state.bookings.147[6].status` | `"BOOKED"` | `"WAITLISTED"` |
| `db_state.bookings.147[7].status` | `"BOOKED"` | `"WAITLISTED"` |
| `db_state.bookings.147[8].status` | `"BOOKED"` | `"WAITLISTED"` |
| `db_state.waitlist.147` | `[]` | `[{"position": 1, "bookingId": 1534, "candidate": "NW00000833"}, {"position": 2, "bookingId": 1535, "candidate": "NW00000361"}, {"position": 3, "bookingId": 1536, "candidate": "NW00000770"}]` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bumped": [
        1534,
        1535,
        1536
      ],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 0
    }
  ],
  "expected": [
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 0
    }
  ]
}
```

</details>

### capacity_decrease_full_session_with_waitlist_admin (KNOWN-DEFECT)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Error: `on-call defect #57 - scheduling-service rejects the reduction with 409 CAPACITY_BELOW_ACCOMMODATION_FLOOR (50005); legacy accepts it and bumps the two newest bookings`

Request (per step):

```json
[
  {
    "form": {
      "NewCapacity": "20"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/153"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.bumped` | `[1601, 1602]` | `[]` |
| `step1.business.error` | `null` | `"<missing>"` |
| `step1.business.legacyCode` | `null` | `50005` |
| `step1.business.outcome` | `"UPDATED"` | `"ERROR"` |
| `db_state.bookings.153[20].status` | `"WAITLISTED"` | `"BOOKED"` |
| `db_state.bookings.153[21].status` | `"WAITLISTED"` | `"BOOKED"` |
| `db_state.examSession.153.Capacity` | `20` | `22` |
| `db_state.waitlist.153` | `[{"bookingId": 1603, "candidate": "NW00000410", "position": 1}, {"bookingId": 1604, "candidate": "NW00000819", "position": 2}, {"bookingId": 1605, "candidate": "NW00000347", "position": 3}, {"bookingId": 1601, "candidate": "NW00000473", "position": 4}, {"bookingId": 1602, "candidate": "NW00000001", "position": 5}]` | `[{"position": 1, "bookingId": 1603, "candidate": "NW00000410"}, {"position": 2, "bookingId": 1604, "candidate": "NW00000819"}, {"position": 3, "bookingId": 1605, "candidate": "NW00000347"}]` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bumped": [],
      "error": "CAPACITY_BELOW_ACCOMMODATION_FLOOR",
      "legacyCode": 50005,
      "outcome": "ERROR",
      "seatsAvailable": 0
    }
  ],
  "expected": [
    {
      "bumped": [
        1601,
        1602
      ],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 0
    }
  ]
}
```

</details>

### capacity_increase_admin (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat).

Request (per step):

```json
[
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000901",
      "SessionId": "191"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000902",
      "SessionId": "191"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000904",
      "externalRef": "NAN-REF-0010",
      "sessionId": 191
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000905",
      "externalRef": "NAN-REF-0011",
      "sessionId": 191
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "form": {
      "NewCapacity": "14"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/191"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step3.business.seatsAvailable` | `6` | `7` |
| `step4.business.seatsAvailable` | `4` | `6` |
| `step5.business.seatsAvailable` | `6` | `8` |
| `db_state.examSession.191.SeatsAvailable` | `6` | `8` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 8,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#3",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 7,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#4",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 6,
      "waitlistPosition": null
    },
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 8
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 8,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#3",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 6,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#4",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 4,
      "waitlistPosition": null
    },
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 6
    }
  ]
}
```

</details>

### capacity_negative_admin (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "form": {
      "NewCapacity": "-1"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/191"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.error` | `"PORTAL_ERROR"` | `"<missing>"` |
| `step1.business.legacyCode` | `null` | `50005` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bumped": [],
      "error": "VALIDATION",
      "legacyCode": 50005,
      "outcome": "ERROR",
      "seatsAvailable": 10
    }
  ],
  "expected": [
    {
      "bumped": [],
      "error": "PORTAL_ERROR",
      "legacyCode": null,
      "outcome": "ERROR",
      "seatsAvailable": 10
    }
  ]
}
```

</details>

### capacity_raise_then_lower_admin (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat).

Request (per step):

```json
[
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000901",
      "SessionId": "191"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "json": {
      "accommodationCode": null,
      "candidateNumber": "NW00000904",
      "externalRef": "NAN-REF-0010",
      "sessionId": 191
    },
    "method": "POST",
    "path": "/api/v1/bookings"
  },
  {
    "form": {
      "NewCapacity": "14"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/191"
  },
  {
    "form": {
      "NewCapacity": "3"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/191"
  },
  {
    "form": {
      "NewCapacity": "12"
    },
    "method": "POST",
    "path": "/Admin/Sessions/Capacity/191"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step2.business.seatsAvailable` | `7` | `8` |
| `step3.business.seatsAvailable` | `9` | `10` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 8,
      "waitlistPosition": null
    },
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 10
    },
    {
      "bumped": [
        "new#2"
      ],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 0
    },
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 9
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    },
    {
      "bookingId": "new#2",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 7,
      "waitlistPosition": null
    },
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 9
    },
    {
      "bumped": [
        "new#2"
      ],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 0
    },
    {
      "bumped": [],
      "error": null,
      "legacyCode": null,
      "outcome": "UPDATED",
      "seatsAvailable": 9
    }
  ]
}
```

</details>

### reschedule_same_exam_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - derived seat count, no double decrement on the rebooking.

Request (per step):

```json
[
  {
    "json": {
      "newSessionId": 258
    },
    "method": "POST",
    "path": "/api/v1/bookings/3586/reschedule"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.seatsAvailable` | `9` | `10` |
| `db_state.examSession.258.SeatsAvailable` | `9` | `10` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 10,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 9,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### reschedule_same_exam_portal (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Request (per step):

```json
[
  {
    "form": {
      "CandidateNumber": "",
      "NewSessionId": "258"
    },
    "method": "POST",
    "path": "/Booking/Reschedule/3586"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `db_state.bookings.258[13].externalRef` | `null` | `"PTR-U00703"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 10,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 10,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### reschedule_same_session_portal (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 6b - every channel rejects with 409 SAME_SESSION.

Request (per step):

```json
[
  {
    "form": {
      "CandidateNumber": "",
      "NewSessionId": "462"
    },
    "method": "POST",
    "path": "/Booking/Reschedule/3586"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.bookingId` | `"new#1"` | `"<missing>"` |
| `step1.business.error` | `null` | `"SAME_SESSION"` |
| `step1.business.outcome` | `"BOOKED"` | `"ERROR"` |
| `db_state.bookings.462` | `[{"accommodation": null, "bookingId": 3586, "cancelReason": "Rescheduled", "candidate": "NW00000900", "externalRef": "PTR-U00703", "status": "CANCELLED"}, {"accommodation": null, "bookingId": "new#1", "cancelReason": null, "candidate": "NW00000900", "externalRef": null, "status": "BOOKED"}]` | `[{"bookingId": 3586, "candidate": "NW00000900", "status": "BOOKED", "accommodation": null, "externalRef": "PTR-U00703", "cancelReason": null}]` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "error": "SAME_SESSION",
      "legacyCode": null,
      "outcome": "ERROR",
      "seatsAvailable": 15,
      "waitlistPosition": null
    }
  ],
  "expected": [
    {
      "bookingId": "new#1",
      "error": null,
      "legacyCode": null,
      "outcome": "BOOKED",
      "seatsAvailable": 15,
      "waitlistPosition": null
    }
  ]
}
```

</details>

### retake_calendar_boundary_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - one retake rule for both channels; the losing channel mismatches.

Request (per step):

```json
[
  {
    "method": "GET",
    "path": "/api/v1/candidates/NW00000952/eligibility",
    "query": {
      "date": "2026-11-29",
      "examCode": "CTA-NET1"
    }
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.earliestRetakeDate` | `"2026-12-11"` | `"2026-11-29"` |
| `step1.business.eligible` | `false` | `true` |
| `step1.business.reason` | `"RETAKE_WAIT_NOT_ELAPSED"` | `"RETAKE_ALLOWED"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "earliestRetakeDate": "2026-11-29",
      "eligible": true,
      "reason": "RETAKE_ALLOWED"
    }
  ],
  "expected": [
    {
      "earliestRetakeDate": "2026-12-11",
      "eligible": false,
      "reason": "RETAKE_WAIT_NOT_ELAPSED"
    }
  ]
}
```

</details>

### retake_day_before_calendar_boundary_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - one retake rule for both channels; the losing channel's earliestRetakeDate mismatches.

Request (per step):

```json
[
  {
    "method": "GET",
    "path": "/api/v1/candidates/NW00000951/eligibility",
    "query": {
      "date": "2026-11-28",
      "examCode": "CTA-NET1"
    }
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.earliestRetakeDate` | `"2026-12-11"` | `"2026-11-29"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "earliestRetakeDate": "2026-11-29",
      "eligible": false,
      "reason": "RETAKE_WAIT_NOT_ELAPSED"
    }
  ],
  "expected": [
    {
      "earliestRetakeDate": "2026-12-11",
      "eligible": false,
      "reason": "RETAKE_WAIT_NOT_ELAPSED"
    }
  ]
}
```

</details>

### retake_inside_window_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - one retake rule for both channels; the losing channel's earliestRetakeDate mismatches.

Request (per step):

```json
[
  {
    "method": "GET",
    "path": "/api/v1/candidates/NW00000950/eligibility",
    "query": {
      "date": "2026-11-20",
      "examCode": "CTA-NET1"
    }
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.earliestRetakeDate` | `"2026-12-11"` | `"2026-11-29"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "earliestRetakeDate": "2026-11-29",
      "eligible": false,
      "reason": "RETAKE_WAIT_NOT_ELAPSED"
    }
  ],
  "expected": [
    {
      "earliestRetakeDate": "2026-12-11",
      "eligible": false,
      "reason": "RETAKE_WAIT_NOT_ELAPSED"
    }
  ]
}
```

</details>

### retake_month_end_boundary_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - one retake rule for both channels; the losing channel mismatches.

Request (per step):

```json
[
  {
    "method": "GET",
    "path": "/api/v1/candidates/NW00000956/eligibility",
    "query": {
      "date": "2026-10-31",
      "examCode": "CTA-NET1"
    }
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.earliestRetakeDate` | `"2026-11-12"` | `"2026-10-31"` |
| `step1.business.eligible` | `false` | `true` |
| `step1.business.reason` | `"RETAKE_WAIT_NOT_ELAPSED"` | `"RETAKE_ALLOWED"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "earliestRetakeDate": "2026-10-31",
      "eligible": true,
      "reason": "RETAKE_ALLOWED"
    }
  ],
  "expected": [
    {
      "earliestRetakeDate": "2026-11-12",
      "eligible": false,
      "reason": "RETAKE_WAIT_NOT_ELAPSED"
    }
  ]
}
```

</details>

### retake_outside_window_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - one retake rule for both channels; the losing channel's earliestRetakeDate mismatches.

Request (per step):

```json
[
  {
    "method": "GET",
    "path": "/api/v1/candidates/NW00000955/eligibility",
    "query": {
      "date": "2027-01-15",
      "examCode": "CTA-NET1"
    }
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.earliestRetakeDate` | `"2026-12-11"` | `"2026-11-29"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "earliestRetakeDate": "2026-11-29",
      "eligible": true,
      "reason": "RETAKE_ALLOWED"
    }
  ],
  "expected": [
    {
      "earliestRetakeDate": "2026-12-11",
      "eligible": true,
      "reason": "RETAKE_ALLOWED"
    }
  ]
}
```

</details>

### retake_weekend_span_between_rules_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - one retake rule for both channels; the losing channel mismatches.

Request (per step):

```json
[
  {
    "method": "GET",
    "path": "/api/v1/candidates/NW00000953/eligibility",
    "query": {
      "date": "2026-12-10",
      "examCode": "CTA-NET1"
    }
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.earliestRetakeDate` | `"2026-12-11"` | `"2026-11-29"` |
| `step1.business.eligible` | `false` | `true` |
| `step1.business.reason` | `"RETAKE_WAIT_NOT_ELAPSED"` | `"RETAKE_ALLOWED"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "earliestRetakeDate": "2026-11-29",
      "eligible": true,
      "reason": "RETAKE_ALLOWED"
    }
  ],
  "expected": [
    {
      "earliestRetakeDate": "2026-12-11",
      "eligible": false,
      "reason": "RETAKE_WAIT_NOT_ELAPSED"
    }
  ]
}
```

</details>

### retake_working_day_boundary_partner (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - one retake rule for both channels; the losing channel's earliestRetakeDate mismatches.

Request (per step):

```json
[
  {
    "method": "GET",
    "path": "/api/v1/candidates/NW00000954/eligibility",
    "query": {
      "date": "2026-12-11",
      "examCode": "CTA-NET1"
    }
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step1.business.earliestRetakeDate` | `"2026-12-11"` | `"2026-11-29"` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "earliestRetakeDate": "2026-11-29",
      "eligible": true,
      "reason": "RETAKE_ALLOWED"
    }
  ],
  "expected": [
    {
      "earliestRetakeDate": "2026-12-11",
      "eligible": true,
      "reason": "RETAKE_ALLOWED"
    }
  ]
}
```

</details>

