# Characterization replay: target `legacy`

- Transcripts captured from tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f`
- Replayed estate commit: `8f484e23e3befc5f5fc9893d40bd1323b7c439a9`
- Target: `legacy`
- Options: `target=legacy`

**Overall: 86/86 MATCH**

Status counts: MATCH 86, MISMATCH 0, KNOWN-DEFECT 0, SKIPPED 0, ERROR 0

KNOWN-DEFECT = differs from the legacy transcript for a reason listed in `characterization/known-defects.yaml`; counted separately (never as MATCH, never hidden).

## Totals per scenario group

| Group | Result | Mismatch | Known defect | Skipped | Error |
|---|---|---|---|---|---|
| booking | 9/9 MATCH | 0 | 0 | 0 | 0 |
| full_session | 2/2 MATCH | 0 | 0 | 0 | 0 |
| duplicate_closed | 6/6 MATCH | 0 | 0 | 0 | 0 |
| cancellation | 9/9 MATCH | 0 | 0 | 0 | 0 |
| reschedule | 10/10 MATCH | 0 | 0 | 0 | 0 |
| capacity_increase | 3/3 MATCH | 0 | 0 | 0 | 0 |
| capacity_decrease | 8/8 MATCH | 0 | 0 | 0 | 0 |
| retake_eligibility_portal | 10/10 MATCH | 0 | 0 | 0 | 0 |
| retake_eligibility_partner | 10/10 MATCH | 0 | 0 | 0 | 0 |
| accommodation | 11/11 MATCH | 0 | 0 | 0 | 0 |
| mixed_traffic | 4/4 MATCH | 0 | 0 | 0 | 0 |
| contracts | 4/4 MATCH | 0 | 0 | 0 | 0 |

## Per scenario

| Scenario | Group | Channel | Oddity | Result | Note |
|---|---|---|---|---|---|
| book_inactive_center_partner | booking | PARTNER | yes | MATCH |  |
| book_inactive_center_portal | booking | PORTAL | yes | MATCH |  |
| book_open_session_partner | booking | PARTNER | yes | MATCH |  |
| book_open_session_portal | booking | PORTAL |  | MATCH |  |
| book_passed_candidate_partner | booking | PARTNER |  | MATCH |  |
| book_passed_candidate_portal | booking | PORTAL |  | MATCH |  |
| book_passed_candidate_proc_db | booking | DB | yes | MATCH |  |
| last_seat_negative_counter_partner | booking | PARTNER | yes | MATCH |  |
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
| cancel_on_delivered_session_portal | cancellation | PORTAL | yes | MATCH |  |
| cancel_other_candidates_booking_portal | cancellation | PORTAL | yes | MATCH |  |
| cancel_then_new_booker_gets_seat_partner | cancellation | PARTNER | yes | MATCH |  |
| cancel_then_new_booker_gets_seat_portal | cancellation | PORTAL | yes | MATCH |  |
| cancel_waitlisted_partner | cancellation | PARTNER |  | MATCH |  |
| cancel_waitlisted_portal | cancellation | PORTAL |  | MATCH |  |
| reschedule_different_exam_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_different_exam_portal | reschedule | PORTAL |  | MATCH |  |
| reschedule_same_exam_partner | reschedule | PARTNER | yes | MATCH |  |
| reschedule_same_exam_portal | reschedule | PORTAL |  | MATCH |  |
| reschedule_same_session_partner | reschedule | PARTNER | yes | MATCH |  |
| reschedule_same_session_portal | reschedule | PORTAL | yes | MATCH |  |
| reschedule_to_delivered_session_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_to_delivered_session_portal | reschedule | PORTAL |  | MATCH |  |
| reschedule_to_full_session_partner | reschedule | PARTNER |  | MATCH |  |
| reschedule_to_full_session_portal | reschedule | PORTAL |  | MATCH |  |
| capacity_9999_admin | capacity_increase | ADMIN | yes | MATCH |  |
| capacity_direct_update_increase_db | capacity_increase | DB | yes | MATCH |  |
| capacity_increase_admin | capacity_increase | ADMIN |  | MATCH |  |
| capacity_below_accommodation_floor_admin | capacity_decrease | ADMIN |  | MATCH |  |
| capacity_decrease_above_active_admin | capacity_decrease | ADMIN |  | MATCH |  |
| capacity_decrease_below_active_admin | capacity_decrease | ADMIN | yes | MATCH |  |
| capacity_decrease_delivered_with_attended_admin | capacity_decrease | ADMIN | yes | MATCH |  |
| capacity_decrease_full_session_with_waitlist_admin | capacity_decrease | ADMIN | yes | MATCH |  |
| capacity_direct_update_decrease_db | capacity_decrease | DB | yes | MATCH |  |
| capacity_negative_admin | capacity_decrease | ADMIN |  | MATCH |  |
| capacity_raise_then_lower_admin | capacity_decrease | ADMIN | yes | MATCH |  |
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
| accommodation_blank_uses_default_partner | accommodation | PARTNER | yes | MATCH |  |
| accommodation_blank_uses_default_portal | accommodation | PORTAL | yes | MATCH |  |
| accommodation_capacity_exhausted_partner | accommodation | PARTNER | yes | MATCH |  |
| accommodation_capacity_exhausted_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_capacity_two_seats_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_pool_not_held_back_partner | accommodation | PARTNER | yes | MATCH |  |
| accommodation_pool_not_held_back_portal | accommodation | PORTAL | yes | MATCH |  |
| accommodation_sr_rb_not_counted_partner | accommodation | PARTNER | yes | MATCH |  |
| accommodation_sr_rb_not_counted_portal | accommodation | PORTAL |  | MATCH |  |
| accommodation_unknown_code_partner | accommodation | PARTNER |  | MATCH |  |
| accommodation_unknown_code_portal | accommodation | PORTAL |  | MATCH |  |
| capacity_sync_fresh_seed_system | mixed_traffic | SYSTEM | yes | MATCH |  |
| capacity_sync_with_accommodation_bookings | mixed_traffic | SYSTEM | yes | MATCH |  |
| mixed_traffic_then_capacity_sync | mixed_traffic | SYSTEM | yes | MATCH |  |
| result_ingest_marks_attended_system | mixed_traffic | SYSTEM | yes | MATCH |  |
| direct_sql_cancel_and_import_db | contracts | DB | yes | MATCH |  |
| proc_error_contract_db | contracts | DB |  | MATCH |  |
| session_search_portal | contracts | PORTAL |  | MATCH |  |
| sessions_contract_partner | contracts | PARTNER |  | MATCH |  |
