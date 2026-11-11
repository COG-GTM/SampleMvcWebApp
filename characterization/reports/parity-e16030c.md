# Characterization replay: target `modern`

- Transcripts captured from tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f`
- Replayed estate commit: `0eb755080da015166395ca4d61636783719fa3d3`
- Target: `modern` base url `http://localhost:8000`
- Options: `target=modern partner-api=v1 db-state=legacy`

**Overall: 57/86 MATCH**

Status counts: MATCH 57, ADR-APPROVED DIVERGENCE 16, KNOWN_ONCALL_DEFECT (#57) 1, MISMATCH 7, NOT REPLAYABLE (retired legacy surface) 5, SKIPPED (unexplained) 0, ERROR 0

KNOWN_ONCALL_DEFECT (#57) = differs from the legacy transcript for a reason listed in `characterization/known-defects.yaml`; ADR-APPROVED DIVERGENCE = a difference ADR 0002 records (entry in `characterization/modern-classification.yaml`, section cited); NOT REPLAYABLE = the legacy entry point was retired and has no modern equivalent (reason and modern coverage stated). All counted separately, never as MATCH, never hidden.

## Totals per scenario group

| Group | Result | ADR-approved | Known defect #57 | Mismatch | Not replayable | Skipped | Error |
|---|---|---|---|---|---|---|---|
| booking | 5/9 MATCH | 3 | 0 | 0 | 1 | 0 | 0 |
| full_session | 2/2 MATCH | 0 | 0 | 0 | 0 | 0 | 0 |
| duplicate_closed | 6/6 MATCH | 0 | 0 | 0 | 0 | 0 | 0 |
| cancellation | 5/9 MATCH | 1 | 0 | 3 | 0 | 0 | 0 |
| reschedule | 7/10 MATCH | 2 | 0 | 1 | 0 | 0 | 0 |
| capacity_increase | 1/3 MATCH | 1 | 0 | 0 | 1 | 0 | 0 |
| capacity_decrease | 2/8 MATCH | 3 | 1 | 1 | 1 | 0 | 0 |
| retake_eligibility_portal | 10/10 MATCH | 0 | 0 | 0 | 0 | 0 | 0 |
| retake_eligibility_partner | 10/10 MATCH | 0 | 0 | 0 | 0 | 0 | 0 |
| accommodation | 6/11 MATCH | 4 | 0 | 1 | 0 | 0 | 0 |
| mixed_traffic | 1/4 MATCH | 2 | 0 | 1 | 0 | 0 | 0 |
| contracts | 2/4 MATCH | 0 | 0 | 0 | 2 | 0 | 0 |

## Per scenario

| Scenario | Group | Channel | Oddity | Result | Note |
|---|---|---|---|---|---|
| book_inactive_center_partner | booking | PARTNER | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| book_inactive_center_portal | booking | PORTAL | yes | MATCH |  |
| book_open_session_partner | booking | PARTNER | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| book_open_session_portal | booking | PORTAL |  | MATCH |  |
| book_passed_candidate_partner | booking | PARTNER |  | MATCH |  |
| book_passed_candidate_portal | booking | PORTAL |  | MATCH |  |
| book_passed_candidate_proc_db | booking | DB | yes | NOT REPLAYABLE (retired legacy surface) | Calls dbo.usp_BookSeat directly. MBA-3824 (M5) dropped the legacy booking procs (db/procs/090_retire_booking_procs.sql) and db/security/040_single_writer.sql denies the legacy logins any Booking write; scheduling-service is the only writer and applies the retake rules on every channel. Covered by: book_passed_candidate_portal, book_passed_candidate_partner, retake_already_passed_portal, retake_already_passed_partner. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| last_seat_negative_counter_partner | booking | PARTNER | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
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
| cancel_on_delivered_session_partner | cancellation | PARTNER | yes | MATCH |  |
| cancel_on_delivered_session_portal | cancellation | PORTAL | yes | MISMATCH | root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match. |
| cancel_other_candidates_booking_portal | cancellation | PORTAL | yes | MISMATCH | root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match. |
| cancel_then_new_booker_gets_seat_partner | cancellation | PARTNER | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| cancel_then_new_booker_gets_seat_portal | cancellation | PORTAL | yes | MISMATCH | root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match. |
| cancel_waitlisted_partner | cancellation | PARTNER |  | MATCH |  |
| cancel_waitlisted_portal | cancellation | PORTAL |  | MATCH |  |
| reschedule_different_exam_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_different_exam_portal | reschedule | PORTAL |  | MATCH |  |
| reschedule_same_exam_partner | reschedule | PARTNER | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| reschedule_same_exam_portal | reschedule | PORTAL |  | MISMATCH | root cause: The modern reschedule copies the original booking's ExternalRef onto the replacement booking; the legacy portal path (usp_RescheduleBooking) inserted the new booking without one. Not recorded in ADR 0002. |
| reschedule_same_session_partner | reschedule | PARTNER | yes | MATCH |  |
| reschedule_same_session_portal | reschedule | PORTAL | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 6b (transcript listed): Same-session reschedule is rejected with 409 SAME_SESSION for every channel; usp_RescheduleBooking re-booked the same seat. |
| reschedule_to_delivered_session_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_to_delivered_session_portal | reschedule | PORTAL |  | MATCH |  |
| reschedule_to_full_session_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_to_full_session_portal | reschedule | PORTAL |  | MATCH |  |
| capacity_9999_admin | capacity_increase | ADMIN | yes | MATCH |  |
| capacity_direct_update_increase_db | capacity_increase | DB | yes | NOT REPLAYABLE (retired legacy surface) | Plain UPDATE raising ExamSession.Capacity. Same as capacity_direct_update_decrease_db: the column is denied to the legacy logins (db/security/040_single_writer.sql) and capacity changes go through PUT /api/v2/sessions/{id}/capacity. Covered by: capacity_increase_admin, capacity_raise_then_lower_admin. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| capacity_increase_admin | capacity_increase | ADMIN |  | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| capacity_below_accommodation_floor_admin | capacity_decrease | ADMIN |  | MATCH |  |
| capacity_decrease_above_active_admin | capacity_decrease | ADMIN |  | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| capacity_decrease_below_active_admin | capacity_decrease | ADMIN | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| capacity_decrease_delivered_with_attended_admin | capacity_decrease | ADMIN | yes | MATCH |  |
| capacity_decrease_full_session_with_waitlist_admin | capacity_decrease | ADMIN | yes | KNOWN_ONCALL_DEFECT (#57) | on-call defect #57 - scheduling-service rejects the reduction with 409 CAPACITY_BELOW_ACCOMMODATION_FLOOR (50005); legacy accepts it and bumps the two newest bookings |
| capacity_direct_update_decrease_db | capacity_decrease | DB | yes | NOT REPLAYABLE (retired legacy surface) | Plain UPDATE of AssessmentDelivery.dbo.ExamSession.Capacity. After M4 capacity is owned by the Scheduling database and projected by the relay; db/security/040_single_writer.sql DENYs UPDATE of ExamSession(Capacity, SeatsAvailable, AccommodationCapacity) to every legacy login, so there is no direct-SQL capacity path left to replay. Covered by: capacity_decrease_below_active_admin, capacity_raise_then_lower_admin, capacity_decrease_delivered_with_attended_admin. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| capacity_negative_admin | capacity_decrease | ADMIN |  | MISMATCH | root cause: Both estates reject capacity -1 and change nothing; the legacy portal showed a generic error (PORTAL_ERROR), the modern API answers 400 VALIDATION with legacyCode 50005 (usp_ChangeCapacity's state for an invalid capacity). Error vocabulary only, not recorded in ADR 0002. |
| capacity_raise_then_lower_admin | capacity_decrease | ADMIN | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
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
| retake_calendar_boundary_partner | retake_eligibility_partner | PARTNER | yes | MATCH |  |
| retake_day_before_calendar_boundary_partner | retake_eligibility_partner | PARTNER | yes | MATCH |  |
| retake_first_attempt_partner | retake_eligibility_partner | PARTNER |  | MATCH |  |
| retake_inside_window_partner | retake_eligibility_partner | PARTNER | yes | MATCH |  |
| retake_month_end_boundary_partner | retake_eligibility_partner | PARTNER | yes | MATCH |  |
| retake_no_show_not_counted_partner | retake_eligibility_partner | PARTNER |  | MATCH |  |
| retake_outside_window_partner | retake_eligibility_partner | PARTNER | yes | MATCH |  |
| retake_weekend_span_between_rules_partner | retake_eligibility_partner | PARTNER | yes | MATCH |  |
| retake_working_day_boundary_partner | retake_eligibility_partner | PARTNER | yes | MATCH |  |
| accommodation_blank_uses_default_partner | accommodation | PARTNER | yes | ADR-APPROVED DIVERGENCE | ADR 0002 sections 6a + 1 (transcript listed in 6a): Partner blank accommodation books with no accommodation on both estates (6a); the only difference is the derived seat count (1). |
| accommodation_blank_uses_default_portal | accommodation | PORTAL | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 6a (transcript listed): The API books exactly what it is sent; the legacy portal substituted the candidate DefaultAccommodationCode server-side (the modern UI pre-fills it in the form instead, which an API-level replay does not exercise). |
| accommodation_capacity_exhausted_partner | accommodation | PARTNER | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| accommodation_capacity_exhausted_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_capacity_two_seats_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_pool_not_held_back_partner | accommodation | PARTNER | yes | MATCH |  |
| accommodation_pool_not_held_back_portal | accommodation | PORTAL | yes | MATCH |  |
| accommodation_sr_rb_not_counted_partner | accommodation | PARTNER | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| accommodation_sr_rb_not_counted_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_unknown_code_partner | accommodation | PARTNER |  | MATCH |  |
| accommodation_unknown_code_portal | accommodation | PORTAL |  | MISMATCH | root cause: Both estates reject the unknown code and book nothing; the legacy portal showed a generic error page (PORTAL_ERROR, no code), the modern API answers 400 VALIDATION. Error vocabulary only, not recorded in ADR 0002. |
| capacity_sync_fresh_seed_system | mixed_traffic | SYSTEM | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 3 (transcript listed): Formula A everywhere; capacity-sync no longer rewrites seats with formula B, so the sync run reports no drift (and section 1 removes the partner drift it used to heal). |
| capacity_sync_with_accommodation_bookings | mixed_traffic | SYSTEM | yes | ADR-APPROVED DIVERGENCE | ADR 0002 section 3 (transcript listed): Formula A everywhere; capacity-sync no longer rewrites seats with formula B, so the sync run reports no drift (and section 1 removes the partner drift it used to heal). |
| mixed_traffic_then_capacity_sync | mixed_traffic | SYSTEM | yes | MISMATCH | root cause: Consequence of ADR sections 1 and 3: in the legacy run the partner double decrement made sessions 154/157 look full, so later bookings were waitlisted and capacity-sync reported drift (112 sessions); on the modern stack those bookings are seated, nothing is waitlisted and there is no drift. Booking/waitlist state differs, which the ADR does not list for this transcript, so it stays a MISMATCH. |
| result_ingest_marks_attended_system | mixed_traffic | SYSTEM | yes | MATCH |  |
| direct_sql_cancel_and_import_db | contracts | DB | yes | NOT REPLAYABLE (retired legacy surface) | Direct UPDATE/INSERT on AssessmentDelivery.dbo.Booking (cancel and IMPORT rows behind every application). db/security/040_single_writer.sql DENYs INSERT/UPDATE/DELETE on Booking to the legacy logins; bookings and cancellations are written by scheduling-service only and the reconciliation job reports any out-of-band row. Covered by: cancel_booked_on_full_session_portal, cancel_booked_on_full_session_partner, mixed_traffic_then_capacity_sync. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| proc_error_contract_db | contracts | DB |  | NOT REPLAYABLE (retired legacy surface) | Raises each usp_* error directly (usp_BookSeat, usp_ChangeCapacity, usp_RescheduleBooking). The procs were dropped in MBA-3824 (db/procs/090_retire_booking_procs.sql); the modern API carries the same error numbers as `legacyCode` (50001-50006) on its 409 responses. Covered by: book_full_session_portal, book_duplicate_portal, book_closed_session_portal, accommodation_capacity_exhausted_portal, capacity_below_accommodation_floor_admin, reschedule_different_exam_portal. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| session_search_portal | contracts | PORTAL |  | MATCH |  |
| sessions_contract_partner | contracts | PARTNER |  | MATCH |  |

## Mismatches

### accommodation_blank_uses_default_partner (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - derived seat count, no double decrement.

Error: `ADR 0002 sections 6a + 1 (transcript listed in 6a): Partner blank accommodation books with no accommodation on both estates (6a); the only difference is the derived seat count (1).`

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
| `business.seatsAvailable` | `13` | `14` |
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

### accommodation_blank_uses_default_portal (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 6a - the API books exactly what it is sent (the modern UI pre-fills the default instead).

Error: `ADR 0002 section 6a (transcript listed): The API books exactly what it is sent; the legacy portal substituted the candidate DefaultAccommodationCode server-side (the modern UI pre-fills it in the form instead, which an API-level replay does not exercise).`

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

### accommodation_capacity_exhausted_partner (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - derived seat count, no double decrement.

Error: `ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference.`

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
| `business.seatsAvailable` | `13` | `14` |
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

### accommodation_sr_rb_not_counted_partner (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - derived seat count, no double decrement.

Error: `ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking.`

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
| `business.seatsAvailable` | `9` | `12` |
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

Error: `root cause: Both estates reject the unknown code and book nothing; the legacy portal showed a generic error page (PORTAL_ERROR, no code), the modern API answers 400 VALIDATION. Error vocabulary only, not recorded in ADR 0002.`

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
| `business.error` | `"PORTAL_ERROR"` | `"VALIDATION"` |

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

### book_inactive_center_partner (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Error: `ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking.`

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
| `business.seatsAvailable` | `0` | `1` |
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

### book_open_session_partner (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - seat count is derived (Capacity - active bookings); no double decrement.

Error: `ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking.`

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
| `business.seatsAvailable` | `13` | `14` |
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

### last_seat_negative_counter_partner (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Error: `ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking.`

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
| `business.seatsAvailable` | `-1` | `0` |
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

### cancel_on_delivered_session_portal (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Error: `root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match.`

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

Error: `root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match.`

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

### cancel_then_new_booker_gets_seat_partner (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Error: `ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking.`

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
| `business.seatsAvailable` | `-1` | `0` |
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

Error: `root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match.`

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

### capacity_decrease_above_active_admin (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat).

Error: `ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference.`

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
| `business.seatsAvailable` | `0` | `2` |
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

### capacity_decrease_below_active_admin (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat).

Error: `ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference.`

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

### capacity_decrease_full_session_with_waitlist_admin (KNOWN_ONCALL_DEFECT (#57))

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
| `business.bumped` | `[1601, 1602]` | `[]` |
| `business.error` | `null` | `"<missing>"` |
| `business.legacyCode` | `null` | `50005` |
| `business.outcome` | `"UPDATED"` | `"ERROR"` |
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

### capacity_increase_admin (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat).

Error: `ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference.`

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
| `business.seatsAvailable` | `6` | `8` |
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

Error: `root cause: Both estates reject capacity -1 and change nothing; the legacy portal showed a generic error (PORTAL_ERROR), the modern API answers 400 VALIDATION with legacyCode 50005 (usp_ChangeCapacity's state for an invalid capacity). Error vocabulary only, not recorded in ADR 0002.`

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
| `business.error` | `"PORTAL_ERROR"` | `"<missing>"` |
| `business.legacyCode` | `null` | `50005` |

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

### capacity_raise_then_lower_admin (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 1 - seat count is derived, no partner double decrement (setup partner bookings leave one more seat).

Error: `ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference.`

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

### capacity_sync_fresh_seed_system (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Error: `ADR 0002 section 3 (transcript listed): Formula A everywhere; capacity-sync no longer rewrites seats with formula B, so the sync run reports no drift (and section 1 removes the partner drift it used to heal).`

Request (per step):

```json
[
  {
    "sql": "db/checks/seat-drift.sql"
  },
  {
    "method": "POST",
    "path": "/sync/run"
  },
  {
    "sql": "db/checks/seat-drift.sql"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step3.business.driftSessions` | `112` | `0` |
| `step3.business.totalDrift` | `177` | `0` |
| `business.driftSessions` | `112` | `0` |
| `business.totalDrift` | `177` | `0` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "driftSessions": 0,
      "totalDrift": 0
    },
    {
      "outcome": "OK"
    },
    {
      "driftSessions": 0,
      "totalDrift": 0
    }
  ],
  "expected": [
    {
      "driftSessions": 0,
      "totalDrift": 0
    },
    {
      "outcome": "OK"
    },
    {
      "driftSessions": 112,
      "totalDrift": 177
    }
  ]
}
```

</details>

### capacity_sync_with_accommodation_bookings (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: none

Error: `ADR 0002 section 3 (transcript listed): Formula A everywhere; capacity-sync no longer rewrites seats with formula B, so the sync run reports no drift (and section 1 removes the partner drift it used to heal).`

Request (per step):

```json
[
  {
    "form": {
      "AccommodationCode": "ET25",
      "CandidateNumber": "NW00000901",
      "SessionId": "198"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "form": {
      "AccommodationCode": "ET50",
      "CandidateNumber": "NW00000902",
      "SessionId": "198"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "form": {
      "AccommodationCode": "",
      "CandidateNumber": "NW00000904",
      "SessionId": "198"
    },
    "method": "POST",
    "path": "/Booking/Book"
  },
  {
    "sql": "db/checks/seat-drift.sql"
  },
  {
    "method": "POST",
    "path": "/sync/run"
  },
  {
    "sql": "db/checks/seat-drift.sql"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step6.business.driftSessions` | `111` | `0` |
| `step6.business.totalDrift` | `175` | `0` |
| `business.driftSessions` | `111` | `0` |
| `business.totalDrift` | `175` | `0` |

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
    },
    {
      "driftSessions": 0,
      "totalDrift": 0
    },
    {
      "outcome": "OK"
    },
    {
      "driftSessions": 0,
      "totalDrift": 0
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
    },
    {
      "driftSessions": 0,
      "totalDrift": 0
    },
    {
      "outcome": "OK"
    },
    {
      "driftSessions": 111,
      "totalDrift": 175
    }
  ]
}
```

</details>

### mixed_traffic_then_capacity_sync (MISMATCH)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: Not replayable against scheduling-service directly (no capacity-sync); needs the gateway for v1 + portal.

Error: `root cause: Consequence of ADR sections 1 and 3: in the legacy run the partner double decrement made sessions 154/157 look full, so later bookings were waitlisted and capacity-sync reported drift (112 sessions); on the modern stack those bookings are seated, nothing is waitlisted and there is no drift. Booking/waitlist state differs, which the ADR does not list for this transcript, so it stays a MISMATCH.`

Request (per step):

```json
[
  {
    "sql": "db/checks/seat-drift.sql"
  },
  {
    "command": "scripts/mixed-traffic",
    "env": {
      "CANCELS": "3",
      "PARTNER_BOOKINGS": "20",
      "PORTAL_BOOKINGS": "20",
      "SESSIONS": "4"
    }
  },
  {
    "sql": "db/checks/seat-drift.sql"
  },
  {
    "method": "POST",
    "path": "/sync/run"
  },
  {
    "sql": "db/checks/seat-drift.sql"
  }
]
```

| Field | Expected | Actual |
|---|---|---|
| `step3.business.driftSessions` | `2` | `0` |
| `step3.business.totalDrift` | `9` | `0` |
| `step5.business.driftSessions` | `112` | `0` |
| `step5.business.totalDrift` | `177` | `0` |
| `business.driftSessions` | `112` | `0` |
| `business.totalDrift` | `177` | `0` |
| `db_state.bookings.154[16].status` | `"WAITLISTED"` | `"BOOKED"` |
| `db_state.bookings.154[17].status` | `"WAITLISTED"` | `"BOOKED"` |
| `db_state.bookings.154[18].status` | `"WAITLISTED"` | `"BOOKED"` |
| `db_state.bookings.154[19].status` | `"WAITLISTED"` | `"BOOKED"` |
| `db_state.bookings.157[15].status` | `"WAITLISTED"` | `"BOOKED"` |
| `db_state.bookings.157[16].status` | `"WAITLISTED"` | `"BOOKED"` |
| `db_state.examSession.152.SeatsAvailable` | `6` | `8` |
| `db_state.examSession.154.SeatsAvailable` | `5` | `3` |
| `db_state.examSession.157.SeatsAvailable` | `3` | `1` |
| `db_state.waitlist.154` | `[{"bookingId": "new#26", "candidate": "NW00000927", "position": 1}, {"bookingId": "new#30", "candidate": "NW00000931", "position": 2}, {"bookingId": "new#34", "candidate": "NW00000935", "position": 3}, {"bookingId": "new#38", "candidate": "NW00000939", "position": 4}]` | `[]` |
| `db_state.waitlist.157` | `[{"bookingId": "new#16", "candidate": "NW00000917", "position": 1}, {"bookingId": "new#20", "candidate": "NW00000921", "position": 2}, {"bookingId": "new#24", "candidate": "NW00000925", "position": 3}, {"bookingId": "new#28", "candidate": "NW00000929", "position": 4}, {"bookingId": "new#32", "candidate": "NW00000933", "position": 5}, {"bookingId": "new#36", "candidate": "NW00000937", "position": 6}, {"bookingId": "new#40", "candidate": "NW00000941", "position": 7}]` | `[{"position": 1, "bookingId": "new#24", "candidate": "NW00000925"}, {"position": 2, "bookingId": "new#28", "candidate": "NW00000929"}, {"position": 3, "bookingId": "new#32", "candidate": "NW00000933"}, {"position": 4, "bookingId": "new#36", "candidate": "NW00000937"}, {"position": 5, "bookingId": "new#40", "candidate": "NW00000941"}]` |

<details><summary>Expected vs actual business fields</summary>

```json
{
  "actual": [
    {
      "driftSessions": 0,
      "totalDrift": 0
    },
    {
      "outcome": "OK"
    },
    {
      "driftSessions": 0,
      "totalDrift": 0
    },
    {
      "outcome": "OK"
    },
    {
      "driftSessions": 0,
      "totalDrift": 0
    }
  ],
  "expected": [
    {
      "driftSessions": 0,
      "totalDrift": 0
    },
    {
      "outcome": "OK"
    },
    {
      "driftSessions": 2,
      "totalDrift": 9
    },
    {
      "outcome": "OK"
    },
    {
      "driftSessions": 112,
      "totalDrift": 177
    }
  ]
}
```

</details>

### reschedule_same_exam_partner (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 - derived seat count, no double decrement on the rebooking.

Error: `ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking.`

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
| `business.seatsAvailable` | `9` | `10` |
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

Error: `root cause: The modern reschedule copies the original booking's ExternalRef onto the replacement booking; the legacy portal path (usp_RescheduleBooking) inserted the new booking without one. Not recorded in ADR 0002.`

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

### reschedule_same_session_portal (ADR-APPROVED DIVERGENCE)

Baseline SHA `e458238da39c991b51d8f670f6d0c463b51b372f`; expected mismatch per ADR 0002: ADR 0002 section 6b - every channel rejects with 409 SAME_SESSION.

Error: `ADR 0002 section 6b (transcript listed): Same-session reschedule is rejected with 409 SAME_SESSION for every channel; usp_RescheduleBooking re-booked the same seat.`

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
| `business.bookingId` | `"new#1"` | `"<missing>"` |
| `business.error` | `null` | `"SAME_SESSION"` |
| `business.outcome` | `"BOOKED"` | `"ERROR"` |
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

