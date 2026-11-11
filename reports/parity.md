# MBA-3825 parity: legacy estate vs. modern slice through the routing gateway

- **Final head SHA** (`origin/assessment-delivery-demo`, fetched right before branching): `e16030c933e221a4a0fcb52c99872a2708db765f`
- Legacy estate: tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f`, started from a separate worktree
- Modern estate: final head + this branch (`devin/mba-3825-parity-proof` at `0eb7550`): routing-gateway :8000 -> scheduling-service :8090,
  M4 outbox relay draining into `AssessmentDelivery`, M5-trimmed portal :8080 behind the gateway. Partner traffic uses partner API v1 through
  the gateway translation; DB state is read from `AssessmentDelivery` after the relay settles (`db-state=legacy`).
- Full per-scenario evidence (request, expected, actual, business-field and normalized DB diffs for every non-MATCH):
  [`characterization/reports/parity-e16030c.md`](../characterization/reports/parity-e16030c.md)

| Run | Command | Result |
|---|---|---|
| (a) legacy at `legacy-baseline` | `characterization/replay --target legacy --base-url http://localhost:8080` | **86/86 MATCH** ([report](../characterization/reports/replay-legacy-e458238.md)) |
| (b) modern via :8000 on the final head | `characterization/replay --target modern --base-url http://localhost:8000 --report parity-e16030c.md` | **57/86 MATCH** |

**Parity line (modern, 86 transcripts): 57/86 MATCH; 16 ADR-APPROVED DIVERGENCE; 1 KNOWN_ONCALL_DEFECT (#57); 7 MISMATCH; 5 NOT REPLAYABLE (retired legacy surface); 0 SKIPPED; 0 ERROR.**

MATCH = same HTTP status, same business fields and the same Booking/Waitlist/ExamSession state after normalization. Every other
category is counted separately and never as MATCH.

## Per scenario group (modern via :8000)

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

## ADR-APPROVED DIVERGENCE (by transcript id, ADR section cited): 16

| Transcript | Group | ADR 0002 citation |
|---|---|---|
| `book_inactive_center_partner` | booking | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| `book_open_session_partner` | booking | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| `last_seat_negative_counter_partner` | booking | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| `cancel_then_new_booker_gets_seat_partner` | cancellation | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| `reschedule_same_exam_partner` | reschedule | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| `reschedule_same_session_portal` | reschedule | ADR 0002 section 6b (transcript listed): Same-session reschedule is rejected with 409 SAME_SESSION for every channel; usp_RescheduleBooking re-booked the same seat. |
| `capacity_increase_admin` | capacity_increase | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| `capacity_decrease_above_active_admin` | capacity_decrease | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| `capacity_decrease_below_active_admin` | capacity_decrease | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| `capacity_raise_then_lower_admin` | capacity_decrease | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| `accommodation_blank_uses_default_partner` | accommodation | ADR 0002 sections 6a + 1 (transcript listed in 6a): Partner blank accommodation books with no accommodation on both estates (6a); the only difference is the derived seat count (1). |
| `accommodation_blank_uses_default_portal` | accommodation | ADR 0002 section 6a (transcript listed): The API books exactly what it is sent; the legacy portal substituted the candidate DefaultAccommodationCode server-side (the modern UI pre-fills it in the form instead, which an API-level replay does not exercise). |
| `accommodation_capacity_exhausted_partner` | accommodation | ADR 0002 section 1 (rule; transcript not on the section 1 list): Setup includes a partner v1 booking; ADR rule 1 (no partner double decrement, derived seats) is the only difference. |
| `accommodation_sr_rb_not_counted_partner` | accommodation | ADR 0002 section 1 (transcript listed): Partner bookings take one seat (no double decrement): the derived count differs from the legacy SeatsAvailable counter after a partner booking. |
| `capacity_sync_fresh_seed_system` | mixed_traffic | ADR 0002 section 3 (transcript listed): Formula A everywhere; capacity-sync no longer rewrites seats with formula B, so the sync run reports no drift (and section 1 removes the partner drift it used to heal). |
| `capacity_sync_with_accommodation_bookings` | mixed_traffic | ADR 0002 section 3 (transcript listed): Formula A everywhere; capacity-sync no longer rewrites seats with formula B, so the sync run reports no drift (and section 1 removes the partner drift it used to heal). |

## KNOWN_ONCALL_DEFECT (#57): 1

| Transcript | Group | Signature |
|---|---|---|
| `capacity_decrease_full_session_with_waitlist_admin` | capacity_decrease | on-call defect #57 - scheduling-service rejects the reduction with 409 CAPACITY_BELOW_ACCOMMODATION_FLOOR (50005); legacy accepts it and bumps the two newest bookings |

## MISMATCH (root cause; none is a migration bug that can be fixed small and low-risk without changing intended behaviour): 7

| Transcript | Group | Root cause |
|---|---|---|
| `cancel_on_delivered_session_portal` | cancellation | root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match. |
| `cancel_other_candidates_booking_portal` | cancellation | root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match. |
| `cancel_then_new_booker_gets_seat_portal` | cancellation | root cause: Blank cancel reason: scheduling-service stores DEFAULT_CANCEL_REASON "Cancelled" (SchedulingService.DEFAULT_CANCEL_REASON); the legacy portal (usp_CancelBooking @Reason NULL) stored NULL. Not recorded in ADR 0002; booking status, seats and waitlist match. |
| `reschedule_same_exam_portal` | reschedule | root cause: The modern reschedule copies the original booking's ExternalRef onto the replacement booking; the legacy portal path (usp_RescheduleBooking) inserted the new booking without one. Not recorded in ADR 0002. |
| `capacity_negative_admin` | capacity_decrease | root cause: Both estates reject capacity -1 and change nothing; the legacy portal showed a generic error (PORTAL_ERROR), the modern API answers 400 VALIDATION with legacyCode 50005 (usp_ChangeCapacity's state for an invalid capacity). Error vocabulary only, not recorded in ADR 0002. |
| `accommodation_unknown_code_portal` | accommodation | root cause: Both estates reject the unknown code and book nothing; the legacy portal showed a generic error page (PORTAL_ERROR, no code), the modern API answers 400 VALIDATION. Error vocabulary only, not recorded in ADR 0002. |
| `mixed_traffic_then_capacity_sync` | mixed_traffic | root cause: Consequence of ADR sections 1 and 3: in the legacy run the partner double decrement made sessions 154/157 look full, so later bookings were waitlisted and capacity-sync reported drift (112 sessions); on the modern stack those bookings are seated, nothing is waitlisted and there is no drift. Booking/waitlist state differs, which the ADR does not list for this transcript, so it stays a MISMATCH. |

## NOT REPLAYABLE (retired legacy surface): 5

| Transcript | Group | Reason and modern coverage |
|---|---|---|
| `book_passed_candidate_proc_db` | booking | Calls dbo.usp_BookSeat directly. MBA-3824 (M5) dropped the legacy booking procs (db/procs/090_retire_booking_procs.sql) and db/security/040_single_writer.sql denies the legacy logins any Booking write; scheduling-service is the only writer and applies the retake rules on every channel. Covered by: book_passed_candidate_portal, book_passed_candidate_partner, retake_already_passed_portal, retake_already_passed_partner. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| `capacity_direct_update_increase_db` | capacity_increase | Plain UPDATE raising ExamSession.Capacity. Same as capacity_direct_update_decrease_db: the column is denied to the legacy logins (db/security/040_single_writer.sql) and capacity changes go through PUT /api/v2/sessions/{id}/capacity. Covered by: capacity_increase_admin, capacity_raise_then_lower_admin. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| `capacity_direct_update_decrease_db` | capacity_decrease | Plain UPDATE of AssessmentDelivery.dbo.ExamSession.Capacity. After M4 capacity is owned by the Scheduling database and projected by the relay; db/security/040_single_writer.sql DENYs UPDATE of ExamSession(Capacity, SeatsAvailable, AccommodationCapacity) to every legacy login, so there is no direct-SQL capacity path left to replay. Covered by: capacity_decrease_below_active_admin, capacity_raise_then_lower_admin, capacity_decrease_delivered_with_attended_admin. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| `direct_sql_cancel_and_import_db` | contracts | Direct UPDATE/INSERT on AssessmentDelivery.dbo.Booking (cancel and IMPORT rows behind every application). db/security/040_single_writer.sql DENYs INSERT/UPDATE/DELETE on Booking to the legacy logins; bookings and cancellations are written by scheduling-service only and the reconciliation job reports any out-of-band row. Covered by: cancel_booked_on_full_session_portal, cancel_booked_on_full_session_partner, mixed_traffic_then_capacity_sync. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| `proc_error_contract_db` | contracts | Raises each usp_* error directly (usp_BookSeat, usp_ChangeCapacity, usp_RescheduleBooking). The procs were dropped in MBA-3824 (db/procs/090_retire_booking_procs.sql); the modern API carries the same error numbers as `legacyCode` (50001-50006) on its 409 responses. Covered by: book_full_session_portal, book_duplicate_portal, book_closed_session_portal, accommodation_capacity_exhausted_portal, capacity_below_accommodation_floor_admin, reschedule_different_exam_portal. (sql steps act on the legacy estate directly; no equivalent on target modern) |

## Notes

- ADR divergences were checked against `docs/adr/0002-ported-rule-decisions.md` and are only applied to the diff fields the
  cited section explains (`characterization/modern-classification.yaml`); any other differing field keeps the scenario a
  MISMATCH. Section 1 rules allow only `business.seatsAvailable` / `db_state.examSession.*.SeatsAvailable` (tightened after
  Devin Review; every ADR-APPROVED scenario in this run differs only in fields the tightened rules accept, so the counts are unchanged).
- Fixed in this PR and re-run: `capacity_decrease_delivered_with_attended_admin` (now MATCH). The capacity-cut bump counted ATTENDED
  bookings; `trg_ExamSession_Update` bumps only BOOKED (ADR 0002 section 2). The on-call defect #57 floor is untouched.
- The 12 SKIPPED of the M1 sanity run (direct :8090, pre-M4/M5 build) are gone: mixed traffic and capacity-sync now run through the
  gateway with the relay, and the five direct-SQL/proc transcripts are NOT REPLAYABLE because M4/M5 dropped those procedures
  and denied the legacy logins any write.
