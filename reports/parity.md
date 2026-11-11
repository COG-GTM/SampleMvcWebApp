# MBA-3825 parity: legacy estate vs. modern slice through the routing gateway

- **Base SHA** (`origin/assessment-delivery-demo`, fetched right before branching): `e45ee7a21c89eee0366dea6032dcd75b69d2af59`
- Legacy estate: tag `legacy-baseline` = `e458238da39c991b51d8f670f6d0c463b51b372f`, started from a separate worktree
- Modern estate: base + this branch (`devin/mba-3825-zero-mismatch`, runtime code at `0d73ab1`; later commits change only docs, reports,
  the classification file and the gateway functional script): routing-gateway :8000 -> scheduling-service :8090, M4 outbox relay
  draining into `AssessmentDelivery`, M5-trimmed portal :8080 behind the gateway. Partner traffic uses partner API v1 through the
  gateway translation; DB state is read from `AssessmentDelivery` after the relay settles (`db-state=legacy`).
- Full per-scenario evidence (request, expected, actual, business-field and normalized DB diffs for every non-MATCH):
  [`characterization/reports/parity-e45ee7a.md`](../characterization/reports/parity-e45ee7a.md). The previous run on `e16030c`
  (57/86, 7 MISMATCH) stays in [`characterization/reports/parity-e16030c.md`](../characterization/reports/parity-e16030c.md).

| Run | Command | Result |
|---|---|---|
| (a) legacy at `legacy-baseline` | `characterization/replay --target legacy --estate-dir ../../SampleMvcWebApp-legacy` (estate from the tag worktree) | **86/86 MATCH** ([report](../characterization/reports/replay-legacy-e458238.md)) |
| (b) modern via :8000 | `characterization/replay --target modern --base-url http://localhost:8000 --report parity-e45ee7a.md` | **63/86 MATCH** |

**Parity line (modern, 86 transcripts): 63/86 MATCH; 17 ADR-APPROVED DIVERGENCE; 1 KNOWN_ONCALL_DEFECT (#57); 0 MISMATCH; 5 NOT REPLAYABLE (retired legacy surface); 0 SKIPPED; 0 ERROR.**

Before every scenario the harness restores `AssessmentDelivery` from the pristine seeded copy and resets the modern side
(deterministic seed, "now" = 2026-11-10). None of the 57 transcripts that matched on `e16030c` regressed; the 6 new MATCH rows
and the new ADR row are listed under "Changes since e16030c".

MATCH = same business fields and the same Booking/Waitlist/ExamSession state after normalization. The legacy run also compares
the raw HTTP status and response. The modern run does not (`characterization/chartool/compare.py`), because the M1 portal
transcripts are HTML form posts and redirects while the modern path answers JSON (for example 302 vs 201/409). The outcome is
compared through the business fields instead (`modern_business`). Every other category is counted separately and never as MATCH.

## Per scenario group (modern via :8000)

| Group | Result | ADR-approved | Known defect #57 | Mismatch | Not replayable | Skipped | Error |
|---|---|---|---|---|---|---|---|
| booking | 5/9 MATCH | 3 | 0 | 0 | 1 | 0 | 0 |
| full_session | 2/2 MATCH | 0 | 0 | 0 | 0 | 0 | 0 |
| duplicate_closed | 6/6 MATCH | 0 | 0 | 0 | 0 | 0 | 0 |
| cancellation | 8/9 MATCH | 1 | 0 | 0 | 0 | 0 | 0 |
| reschedule | 8/10 MATCH | 2 | 0 | 0 | 0 | 0 | 0 |
| capacity_increase | 1/3 MATCH | 1 | 0 | 0 | 1 | 0 | 0 |
| capacity_decrease | 3/8 MATCH | 3 | 1 | 0 | 1 | 0 | 0 |
| retake_eligibility_portal | 10/10 MATCH | 0 | 0 | 0 | 0 | 0 | 0 |
| retake_eligibility_partner | 10/10 MATCH | 0 | 0 | 0 | 0 | 0 | 0 |
| accommodation | 7/11 MATCH | 4 | 0 | 0 | 0 | 0 | 0 |
| mixed_traffic | 1/4 MATCH | 3 | 0 | 0 | 0 | 0 | 0 |
| contracts | 2/4 MATCH | 0 | 0 | 0 | 2 | 0 | 0 |

## ADR-APPROVED DIVERGENCE (by transcript id, ADR section cited): 17

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
| `mixed_traffic_then_capacity_sync` | mixed_traffic | ADR 0002 section 7 (transcript listed): Mixed portal/partner traffic then a capacity-sync run: on legacy the partner double decrement (HB-01) made sessions 154/157 look full, so their last bookings were waitlisted, and formula B (HB-03) left drift (reports/drift-before-after.md: 112 before, 0 after). On modern those bookings are seated, nothing is waitlisted and seat-drift.sql reports 0. Only booking status in 154/157, their waitlists, seat counters and the drift counters may differ. |

## KNOWN_ONCALL_DEFECT (#57): 1

| Transcript | Group | Signature |
|---|---|---|
| `capacity_decrease_full_session_with_waitlist_admin` | capacity_decrease | on-call defect #57 - scheduling-service rejects the reduction with 409 CAPACITY_BELOW_ACCOMMODATION_FLOOR (50005); legacy accepts it and bumps the two newest bookings |

## MISMATCH: 0

None.

## NOT REPLAYABLE (retired legacy surface): 5

| Transcript | Group | Reason and modern coverage |
|---|---|---|
| `book_passed_candidate_proc_db` | booking | Calls dbo.usp_BookSeat directly. MBA-3824 (M5) dropped the legacy booking procs (db/procs/090_retire_booking_procs.sql) and db/security/040_single_writer.sql denies the legacy logins any Booking write; scheduling-service is the only writer and applies the retake rules on every channel. Covered by: book_passed_candidate_portal, book_passed_candidate_partner, retake_already_passed_portal, retake_already_passed_partner. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| `capacity_direct_update_increase_db` | capacity_increase | Plain UPDATE raising ExamSession.Capacity. Same as capacity_direct_update_decrease_db: the column is denied to the legacy logins (db/security/040_single_writer.sql) and capacity changes go through PUT /api/v2/sessions/{id}/capacity. Covered by: capacity_increase_admin, capacity_raise_then_lower_admin. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| `capacity_direct_update_decrease_db` | capacity_decrease | Plain UPDATE of AssessmentDelivery.dbo.ExamSession.Capacity. After M4 capacity is owned by the Scheduling database and projected by the relay; db/security/040_single_writer.sql DENYs UPDATE of ExamSession(Capacity, SeatsAvailable, AccommodationCapacity) to every legacy login, so there is no direct-SQL capacity path left to replay. Covered by: capacity_decrease_below_active_admin, capacity_raise_then_lower_admin, capacity_decrease_delivered_with_attended_admin. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| `direct_sql_cancel_and_import_db` | contracts | Direct UPDATE/INSERT on AssessmentDelivery.dbo.Booking (cancel and IMPORT rows behind every application). db/security/040_single_writer.sql DENYs INSERT/UPDATE/DELETE on Booking to the legacy logins; bookings and cancellations are written by scheduling-service only and the reconciliation job reports any out-of-band row. Covered by: cancel_booked_on_full_session_portal, cancel_booked_on_full_session_partner, mixed_traffic_then_capacity_sync. (sql steps act on the legacy estate directly; no equivalent on target modern) |
| `proc_error_contract_db` | contracts | Raises each usp_* error directly (usp_BookSeat, usp_ChangeCapacity, usp_RescheduleBooking). The procs were dropped in MBA-3824 (db/procs/090_retire_booking_procs.sql); the modern API carries the same error numbers as `legacyCode` (50001-50006) on its 409 responses. Covered by: book_full_session_portal, book_duplicate_portal, book_closed_session_portal, accommodation_capacity_exhausted_portal, capacity_below_accommodation_floor_admin, reschedule_different_exam_portal. (sql steps act on the legacy estate directly; no equivalent on target modern) |

## Changes since e16030c (7 former MISMATCH rows)

| Transcript | Was | Now | Why |
|---|---|---|---|
| `cancel_on_delivered_session_portal`, `cancel_other_candidates_booking_portal`, `cancel_then_new_booker_gets_seat_portal` | MISMATCH | MATCH | scheduling-service stores a blank/whitespace cancel reason as NULL, as `usp_CancelBooking @Reason = NULL` did for the portal (`db/procs/021_usp_CancelBooking.sql` at `legacy-baseline`). Partner v1 keeps its own default: booking-api sends `StringUtils.defaultIfBlank(reason, "Cancelled by partner")` (`services/booking-api/.../BookingService.java` at `legacy-baseline`), and the gateway translation still does. Reschedule keeps `'Rescheduled'`. |
| `reschedule_same_exam_portal` | MISMATCH | MATCH | The replacement booking copies `ExternalRef` only on the partner channel: `usp_RescheduleBooking` (portal) inserted it without one, booking-api's v1 reschedule passed the old ref to the new booking. `POST /api/v2/bookings/{id}/reschedule` takes an optional `channel` (default `PORTAL`); the gateway and booking-api send `PARTNER`. `reschedule_same_exam_partner` stays as it was (ADR section 1 seats only). |
| `capacity_negative_admin` | MISMATCH | MATCH | Negative capacity is request validation (`AdminService.ChangeCapacity` rejected it before calling `usp_ChangeCapacity`), so the modern 400 `VALIDATION` no longer carries `legacyCode 50005`. Portal-channel adapter mapping below turns the uncoded 400 into the portal's generic error. |
| `accommodation_unknown_code_portal` | MISMATCH | MATCH | Portal-channel adapter mapping: an uncoded 400 `VALIDATION` on a portal/admin form is reported as `PORTAL_ERROR`, the vocabulary the legacy portal adapter records for every `PortalException` page error. Partner and coded errors are unchanged (see the PR description for the legacy citations). |
| `mixed_traffic_then_capacity_sync` | MISMATCH | ADR-APPROVED DIVERGENCE | ADR 0002 section 7 (new): HB-01 + HB-03 made sessions 154/157 look full on legacy; modern seats those bookings, waitlists nobody and reports 0 drift. Allowed fields: the 6 drift counters, `db_state.bookings.154[*].status`, `db_state.bookings.157[*].status`, `db_state.waitlist.154/157`, `db_state.examSession.*.SeatsAvailable`. |

## Notes

- ADR divergences are only applied to the diff fields the cited section explains (`characterization/modern-classification.yaml`);
  any other differing field keeps the scenario a MISMATCH. No comparator change in this PR.
- KNOWN_ONCALL_DEFECT (#57) is unchanged: `AccommodationFloorPolicy` and the `floorPolicy.check(...)` arguments are untouched.
