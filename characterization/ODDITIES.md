# Legacy oddities pinned by the characterization transcripts (MBA-3820)

Captured from tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f` (Northwind Assessment Network
legacy estate). Everything below is recorded **as it is** and was **not fixed**. Every transcript tagged
`"oddity": true` is listed with a one-line description; the transcript file is
`transcripts/<group>/<scenario>.json`.

## The six behaviours from the story, as observed

| # | Behaviour (story item) | Observed at `legacy-baseline`? | Transcripts |
|---|---|---|---|
| 1 | Partner bookings decrement `SeatsAvailable` twice | **Yes**: portal 15 -> 14, partner 15 -> 13 on session 462 | `book_open_session_partner`, `accommodation_*_partner` |
| 2 | Lowering capacity moves booked candidates to the waitlist | **Yes**: newest BOOKED rows become WAITLISTED, appended after the existing waitlist; `SeatsAvailable` recomputed from BOOKED | `capacity_decrease_below_active_admin`, `capacity_raise_then_lower_admin`, `capacity_decrease_full_session_with_waitlist_admin` |
| 3 | Two seat formulas (capacity-sync vs `seat-drift.sql`) | **Yes**: one sync run on a fresh seed turns 2 drifting sessions into 112 | `mixed_traffic_then_capacity_sync`, `capacity_sync_with_accommodation_bookings` |
| 4 | Retake wait in working days (partner) vs calendar days (portal) | **Yes**: attempt Fri 2026-10-30 -> portal earliest 2026-11-29, partner 2026-12-11 | `retake_*` |
| 5 | Partner reschedule rebooks through the partner path | **Yes**: the target session loses two seats (258: 11 -> 9; portal 11 -> 10) | `reschedule_same_exam_partner` |
| 6 | Capacity below `AccommodationCapacity` | Baseline rule is the **strict** one (PR #51 not on the tag): `NewCapacity < AccommodationCapacity` -> `usp_ChangeCapacity` 50005 even with no ET seats booked | `capacity_below_accommodation_floor_admin` (not tagged oddity: it is the documented rule) |

Other pinned facts (not oddities): cancelling never promotes from the waitlist, cancelling a WAITLISTED booking
deletes its `Waitlist` row without renumbering the remaining positions, `SR`/`RB` never consume accommodation
seats, the portal accepts a hand-posted `NewSessionId` for a different exam and surfaces 50006.

Channel differences beyond the story list (ADR 0002 section 6): a blank accommodation code is
replaced by the candidate's default on the portal only (`accommodation_blank_uses_default_*`), and a
reschedule to the booking's own session is cancelled-and-rebooked by the portal but rejected by booking-api
with 409 `SAME_SESSION` (`reschedule_same_session_*`).

## Oddity index (one line per transcript)

| Scenario | Group | Oddity |
|---|---|---|
| `book_open_session_partner` | booking | Partner booking decrements ExamSession.SeatsAvailable twice (BookingService.book + trg_Booking_SeatSync). |
| `cancel_booked_on_full_session_portal` | cancellation | Cancelling a BOOKED seat on a full session releases the seat but nobody is promoted from the waitlist. |
| `cancel_booked_on_full_session_partner` | cancellation | Cancelling a BOOKED seat on a full session releases the seat but nobody is promoted from the waitlist. |
| `reschedule_same_exam_partner` | reschedule | Partner reschedule cancels via usp_CancelBooking then rebooks via the partner path, so the new session loses two seats. |
| `reschedule_same_session_portal` | reschedule | Portal reschedule to the booking's own session is not rejected - usp_RescheduleBooking cancels and re-books the same seat; booking-api returns 409 SAME_SESSION. |
| `reschedule_same_session_partner` | reschedule | booking-api rejects a reschedule to the same session (409 SAME_SESSION) while the portal cancels and re-books. |
| `capacity_decrease_below_active_admin` | capacity | Lowering capacity below active bookings silently moves the newest BOOKED rows to WAITLISTED (trg_ExamSession_Update), not usp_ChangeCapacity. |
| `capacity_raise_then_lower_admin` | capacity | Raise then lower below active bookings; bumped candidates stay WAITLISTED when capacity is raised again (no re-promotion). |
| `capacity_decrease_full_session_with_waitlist_admin` | capacity | Bumped bookings are appended after the existing waitlist of full session 153; SeatsAvailable is recomputed from BOOKED only. |
| `retake_inside_window_portal` | retake_eligibility | Channels agree on eligible but disagree on earliestRetakeDate (portal attempt + 30 calendar days, booking-api attempt + 30 working days). |
| `retake_inside_window_partner` | retake_eligibility | Channels agree on eligible but disagree on earliestRetakeDate (portal attempt + 30 calendar days, booking-api attempt + 30 working days). |
| `retake_day_before_calendar_boundary_portal` | retake_eligibility | Channels agree on eligible but disagree on earliestRetakeDate (portal attempt + 30 calendar days, booking-api attempt + 30 working days). |
| `retake_day_before_calendar_boundary_partner` | retake_eligibility | Channels agree on eligible but disagree on earliestRetakeDate (portal attempt + 30 calendar days, booking-api attempt + 30 working days). |
| `retake_calendar_boundary_portal` | retake_eligibility | Retake wait: portal counts 30 calendar days (RetakeRules), booking-api 30 working days (DateUtils.addWorkingDays); channels disagree on this date. |
| `retake_calendar_boundary_partner` | retake_eligibility | Retake wait: portal counts 30 calendar days (RetakeRules), booking-api 30 working days (DateUtils.addWorkingDays); channels disagree on this date. |
| `retake_weekend_span_between_rules_portal` | retake_eligibility | Retake wait: portal counts 30 calendar days (RetakeRules), booking-api 30 working days (DateUtils.addWorkingDays); channels disagree on this date. |
| `retake_weekend_span_between_rules_partner` | retake_eligibility | Retake wait: portal counts 30 calendar days (RetakeRules), booking-api 30 working days (DateUtils.addWorkingDays); channels disagree on this date. |
| `retake_working_day_boundary_portal` | retake_eligibility | Channels agree on eligible but disagree on earliestRetakeDate (portal attempt + 30 calendar days, booking-api attempt + 30 working days). |
| `retake_working_day_boundary_partner` | retake_eligibility | Channels agree on eligible but disagree on earliestRetakeDate (portal attempt + 30 calendar days, booking-api attempt + 30 working days). |
| `retake_outside_window_portal` | retake_eligibility | Channels agree on eligible but disagree on earliestRetakeDate (portal attempt + 30 calendar days, booking-api attempt + 30 working days). |
| `retake_outside_window_partner` | retake_eligibility | Channels agree on eligible but disagree on earliestRetakeDate (portal attempt + 30 calendar days, booking-api attempt + 30 working days). |
| `retake_month_end_boundary_portal` | retake_eligibility | Retake wait: portal counts 30 calendar days (RetakeRules), booking-api 30 working days (DateUtils.addWorkingDays); channels disagree on this date. |
| `retake_month_end_boundary_partner` | retake_eligibility | Retake wait: portal counts 30 calendar days (RetakeRules), booking-api 30 working days (DateUtils.addWorkingDays); channels disagree on this date. |
| `accommodation_capacity_exhausted_partner` | accommodation | Every partner booking (ET25, SR, RB alike) takes two seats from SeatsAvailable (double decrement); the portal takes one. |
| `accommodation_sr_rb_not_counted_partner` | accommodation | Every partner booking (ET25, SR, RB alike) takes two seats from SeatsAvailable (double decrement); the portal takes one. |
| `accommodation_blank_uses_default_portal` | accommodation | Portal fills a blank accommodation with the candidate's DefaultAccommodationCode (ET50 for NW00000903), so the booking consumes the accommodation seat; booking-api books it without accommodation. |
| `accommodation_blank_uses_default_partner` | accommodation | booking-api books a blank accommodation as none even when the candidate has a DefaultAccommodationCode; the portal substitutes the default (and the double decrement applies). |
| `mixed_traffic_then_capacity_sync` | mixed_traffic | Mixed portal/partner traffic leaves seat drift (partner double decrement); capacity-sync rewrites SeatsAvailable with a formula the drift check disagrees with. |
| `capacity_sync_with_accommodation_bookings` | mixed_traffic | capacity-sync subtracts AccommodationCapacity and counts only non-ET bookings, so a correct session drifts after the nightly sync. |
| `cancel_then_new_booker_gets_seat_portal` | cancellation | A seat freed on a session with a waitlist goes to the next new booker, not to waitlist position 1. |
| `cancel_then_new_booker_gets_seat_partner` | cancellation | Partner cancel frees a seat that the next partner booking takes (and double-decrements); nobody on the waitlist is promoted. |
| `last_seat_negative_counter_partner` | booking | A partner booking of the last seat drives SeatsAvailable to -1 (Java decrement + trigger); sequential stand-in for the CT-08 race. |
| `capacity_sync_fresh_seed_system` | mixed_traffic | seat-drift.sql is clean on a fresh seed; one capacity-sync run makes every OPEN session with accommodation seats drift. |
| `direct_sql_cancel_and_import_db` | contracts | A direct UPDATE to CANCELLED leaves SeatsAvailable unchanged; a direct multi-row IMPORT insert decrements per BOOKED row with no capacity, duplicate or waitlist checks. |
| `accommodation_pool_not_held_back_portal` | accommodation | Standard bookings filled full session 153 including its 2 accommodation seats, so an ET50 candidate is waitlisted. |
| `accommodation_pool_not_held_back_partner` | accommodation | booking-api also waitlists an ET50 candidate on a session whose accommodation seats were taken by standard bookings. |
| `capacity_9999_admin` | capacity | A hand-posted capacity of 9999 is accepted (Range(0, 500) on the form model does not stop it server-side); -1 is rejected. |
| `capacity_direct_update_decrease_db` | capacity | A plain UPDATE of ExamSession.Capacity (no proc, no app) still demotes the newest BOOKED rows - the trigger does it. |
| `capacity_direct_update_increase_db` | capacity | A plain UPDATE raising Capacity leaves SeatsAvailable unchanged (only usp_ChangeCapacity adds the delta) and promotes nobody. |
| `capacity_decrease_delivered_with_attended_admin` | capacity | Capacity of DELIVERED session 147 (6 ATTENDED + 5 BOOKED) can still be lowered to 8; nobody is bumped because only BOOKED rows count, SeatsAvailable floors at 0. |
| `cancel_on_delivered_session_portal` | cancellation | A booking on DELIVERED (past) session 147 can still be cancelled through the portal and its seat is released. |
| `cancel_on_delivered_session_partner` | cancellation | booking-api also cancels a booking on a DELIVERED (past) session. |
| `cancel_other_candidates_booking_portal` | cancellation | Portal cancel has no ownership check - NW00000901 cancels NW00000900's booking 3586. |
| `book_passed_candidate_proc_db` | booking | usp_BookSeat alone books a candidate who already passed the exam - retake rules live only in the apps. |
| `book_inactive_center_portal` | booking | With SEA01 marked inactive it disappears from the portal's centre filter, yet its session 205 is still bookable. |
| `book_inactive_center_partner` | booking | booking-api books session 205 of inactive centre SEA01 (and double-decrements). |
| `result_ingest_marks_attended_system` | mixed_traffic | Ingesting a result flips booking 3319 to ATTENDED as a side effect without touching SeatsAvailable; seat-drift stays clean because it counts ATTENDED as held. |
