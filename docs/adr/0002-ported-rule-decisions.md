# ADR 0002: Seat-inventory rules ported into scheduling-service

- Status: accepted (M1 transcripts captured; see `Transcripts:` lines and `characterization/reports/`)
- Story: MBA-3821 (M2), epic MBA-3819
- Date: 2026-11-11

## Context

Seat inventory in the legacy estate is owned by four codebases at once: the `usp_*` procedures and
two triggers in `db/`, the Java 8 `booking-api` (partner channel), the Java 8 `capacity-sync` job,
and the .NET portal (`AssessmentDelivery.Business`, `AssessmentDelivery.Services`). M2 moves all of
it into one tested codebase, `services/scheduling-service`, whose `domain/` package is plain Java.

M1 (MBA-3820, characterization transcripts) was not available when this story started, so the
rules were ported by reading the legacy code. Each section below names the legacy behaviour per
channel, the decision for the modern service, and the reason. The `Transcripts:` lines are filled in
once M1 is merged and `characterization/replay --target modern` has been run.

Domain classes cite their legacy sources in Javadoc; unit tests are named after the legacy
procedure/trigger behaviour they replace (`SessionCapacityTest`, `RulesTest`).

Rules that are configurable (so the alternative can be switched on without a code change) live in
`application.yml` under `scheduling.rules`.

## 1. Partner double decrement

**Legacy behaviour**

| Channel | What happens to `ExamSession.SeatsAvailable` on a successful booking |
|---|---|
| Portal (.NET) | `BookingService.Book` calls `usp_BookSeat`, which decrements by 1. `trg_Booking_SeatSync` skips `Channel = 'PORTAL'`. Net -1. |
| Partner (booking-api) | `BookingService.book` inserts the `Booking` row itself and decrements by 1; `trg_Booking_SeatSync` then decrements again because `Channel <> 'PORTAL'`. Net -2. |
| capacity-sync | Every run rewrites `SeatsAvailable` for OPEN sessions with its own formula (section 3), which "heals" the drift until the next partner booking. |

Sources: `db/procs/020_usp_BookSeat.sql`, `db/triggers/032_trg_Booking_SeatSync.sql`,
`services/booking-api/.../BookingService.java`, `services/capacity-sync/.../CapacitySyncService.java`,
`db/checks/seat-drift.sql`.

**Decision: deliberately changed.** scheduling-service has no stored seat counter. Seats available
are derived from the bookings on every read and every write (`SessionCapacity`, `SeatFormulas.seatsAvailable`),
so every booking takes exactly one seat whatever its channel (`PORTAL` or `PARTNER`).

**Why.** The double decrement is a bug, not a business rule: it makes partner bookings consume two
seats until capacity-sync runs, and `db/checks/seat-drift.sql` exists to detect it. Deriving the
count removes the class of drift entirely. Test:
`trg_Booking_SeatSync_partnerBookingTakesExactlyOneSeat_noDoubleDecrement`.

Transcripts: `book_open_session_partner`, `last_seat_negative_counter_partner`, `cancel_then_new_booker_gets_seat_partner`, `accommodation_sr_rb_not_counted_partner`, `book_inactive_center_partner`, `reschedule_same_exam_partner` (characterization/transcripts; legacy replay `characterization/reports/replay-legacy-e458238.md`)

## 2. Capacity decrease bumping booked candidates to the waitlist

**Legacy behaviour.** All channels change capacity through `usp_ChangeCapacity` (portal
`Admin/Capacity`; there is no partner capacity endpoint). The procedure moves `SeatsAvailable` by
the same delta (floored at 0); `trg_ExamSession_Update` then, when capacity drops below the number
of BOOKED bookings, moves the most recently booked ones (`BookedAt DESC, BookingId DESC`) to
`WAITLISTED`, appends them to the waitlist after the existing entries (in `BookedAt ASC` order) and
recomputes `SeatsAvailable = Capacity - BOOKED`. ATTENDED bookings are never bumped. Raising capacity
does not promote anyone from the waitlist; cancelling a booking does not promote either.

Sources: `db/procs/022_usp_ChangeCapacity.sql`, `db/triggers/033_trg_ExamSession_Update.sql`,
`db/procs/021_usp_CancelBooking.sql`.

**Decision: kept.** `SessionCapacity.changeCapacity` reproduces the bump order and waitlist append
order exactly and returns the bumped booking ids (`PUT /api/v2/sessions/{id}/capacity` ->
`bumpedBookingIds`). No automatic promotion on capacity increase or cancellation. Every change is
audited in `capacity_change` (old/new capacity, bumped ids, actor).

**Why.** Test-center staff rely on this behaviour and there is no requirement to change it in M2;
automatic promotion would be a product decision with candidate-notification consequences. The admin
UI shows the number of bookings that will be bumped in the confirmation dialog before applying.
Tests: `trg_ExamSession_Update_bumpsLatestBookedToWaitlist`,
`trg_ExamSession_Update_attendedHoldSeatsButAreNeverBumped`,
`trg_ExamSession_Update_capacityZeroBumpsEveryBooked`,
`trg_ExamSession_Update_decreaseWithinFreeSeatsBumpsNobody`,
`usp_ChangeCapacity_increaseAddsSeats_noWaitlistPromotion`,
`usp_CancelBooking_bookedCancellationFreesSeat_noAutoPromotion`.

Transcripts: `capacity_decrease_below_active_admin`, `capacity_raise_then_lower_admin`, `capacity_decrease_full_session_with_waitlist_admin`, `capacity_direct_update_decrease_db`, `capacity_decrease_delivered_with_attended_admin`

## 3. The two seat formulas

**Legacy behaviour.**

- Formula A, `Capacity - count(BOOKED + ATTENDED)`: `usp_BookSeat` (via the counter),
  `trg_ExamSession_Update`, `db/checks/seat-drift.sql`, booking-api `SeatCounter`, portal
  `AssessmentDelivery.Business/Rules/SeatCounter.cs`.
- Formula B, `Capacity - AccommodationCapacity - count(BOOKED + ATTENDED without ET25/ET50)`,
  floored at 0: `capacity-sync` `CapacitySyncService`, which writes it back into `SeatsAvailable`.

The two disagree whenever a session has accommodation seats: formula B hides the accommodation
allocation from the general count, so after a capacity-sync run portal and partner see fewer seats.

**Decision: formula A, everywhere.** Seats available = `max(0, capacity - (BOOKED + ATTENDED))`.
Accommodation (ET25/ET50) seats are a sub-allocation inside capacity and are reported separately
(`accommodationCapacity`, `accommodationUsed`). Formula B is kept as
`SeatFormulas.capacitySyncSeatsAvailable` for the replay comparison only and is not used by the API.

**Why.** Formula A is the one the booking procedure, both channels' code and the drift check agree
on; formula B is only the batch job's view and is what makes the job "fix" counts in the wrong
direction. Test: `seatFormulas_procFormulaVsCapacitySyncFormulaDisagreeWithAccommodationSeats`,
`seatDrift_bookedAndAttendedHoldSeats_cancelledAndWaitlistedDoNot`.

Transcripts: `mixed_traffic_then_capacity_sync`, `capacity_sync_with_accommodation_bookings`, `capacity_sync_fresh_seed_system`, `result_ingest_marks_attended_system`

## 4. Working vs calendar retake days

**Legacy behaviour.** After a FAIL the candidate must wait `Exam.RetakeWaitDays` from the date of
their last attempt; a PASS makes them ineligible for that exam.

| Channel | Earliest retake date |
|---|---|
| Portal (.NET) | `AssessmentDelivery.Business/Rules/RetakeRules.cs`: `lastAttempt.AddDays(retakeWaitDays)` (calendar days). |
| Partner (booking-api) | `RetakeEligibilityService` + `DateUtils.addWorkingDays(lastAttempt, retakeWaitDays)` (Mon-Fri, no holiday calendar). |

So the same candidate can be refused on the partner channel and accepted on the portal for the same
date.

**Decision: calendar days** (`scheduling.rules.retake-days: calendar`). The working-day variant is
implemented (`RetakeDayCounting.WORKING_DAYS`, matching `DateUtils.addWorkingDays`) and can be
selected with `retake-days: working`. Reasons and the response shape stay as v1:
`FIRST_ATTEMPT`, `ALREADY_PASSED`, `RETAKE_WAIT_NOT_ELAPSED`, `RETAKE_ALLOWED`, plus
`earliestRetakeDate`.

**Why.** The portal is the majority channel and the column is named `RetakeWaitDays`, not
working days; the Java variant has no holiday calendar, so it is not a faithful "business days"
rule either. Partners whose candidates were refused under the working-day rule will now see the
earlier (calendar) date: this is a deliberate behaviour change for the partner channel and must be
confirmed against the M1 eligibility transcripts. Tests:
`retakeEligibility_insideOnAndOutsideWindow_calendar`,
`retakeEligibility_workingDaysVariantMatchesBookingApi`, `retakeDays_calendarIsPortalAddDays`,
`retakeDays_workingDaysIsBookingApiAddWorkingDays`.

Transcripts: `retake_*_portal` / `retake_*_partner` (inside window, boundary, outside, weekend and month-end windows, already passed, first attempt), `retake_no_show_not_counted_*`

## 5. Accommodation floor on capacity reductions (`usp_ChangeCapacity`, PR #51)

**Legacy behaviour on `assessment-delivery-demo`.** `usp_ChangeCapacity` rejects (RAISERROR state
50005) a new capacity below the session's allocated `AccommodationCapacity`, even when none of the
accommodation seats are booked.

**Pending PR #51** (`devin/1794377539-northwind-capacity-accommodation-guard`, not merged) relaxes
this: the allocation is kept as set, and only the number of accommodation seats already booked
(ET25/ET50, BOOKED or ATTENDED) blocks a reduction.

**Decision: port the rule currently on `assessment-delivery-demo`**
(`scheduling.rules.accommodation-floor: allocated`): `newCapacity < accommodationCapacity` -> 409
`CAPACITY_BELOW_ACCOMMODATION_FLOOR`, `legacyCode` 50005. The PR #51 rule is implemented as
`AccommodationFloorPolicy.BOOKED_EXTRA_TIME` and is one property away
(`accommodation-floor: booked-extra-time`) once PR #51 is merged.

**Why.** M2 must match the legacy behaviour that is live today; PR #51 is still under review. Tests:
`usp_ChangeCapacity_rejectsBelowAllocatedAccommodationCapacity_50005`,
`pr51_bookedExtraTimeFloor_allowsBelowAllocationButNotBelowBookedEtSeats`, and the integration
test `trg_ExamSession_Update_capacityDecreaseBumpsNewestBookedToWaitlist_andFloorRejectsWith50005`.

**The floor is the allocation only.** Standard (non ET25/ET50) seats already held do not raise it: a cut
below the seats held but at or above `AccommodationCapacity` is accepted and `trg_ExamSession_Update` bumps the
newest BOOKED bookings (section 2). scheduling-service@1.4.0 added the standard seats held to the floor
(BOS01 session #198: 23 -> 9 rejected with floor 2 + 8 = 10) and was rolled back to this rule in incident
run-463f5cbfbee6; regression tests `usp_ChangeCapacity_allocatedFloorIgnoresStandardSeatsHeld_session198_23To9Accepted`,
`usp_ChangeCapacity_allocatedFloorAllowsCutBelowSeatsHeld_bumpsNewestBooked` and
`usp_ChangeCapacity_reductionOnAccommodationSession_floorIsAllocationOnly_notPlusStandardSeats`.

Transcripts: `capacity_below_accommodation_floor_admin` (strict rule confirmed at `legacy-baseline`: 50005), `proc_error_contract_db`, `capacity_decrease_full_session_with_waitlist_admin` (no longer a known defect after run-463f5cbfbee6)

## 6. Smaller channel differences found while porting

These are not on the story's list of M1 oddities but the channels disagree, so the choice is
recorded here.

### 6a. Blank accommodation code

Portal `BookingService.Book` substitutes the candidate's `DefaultAccommodationCode` when the
request leaves it blank; booking-api and `usp_BookSeat` book without an accommodation.
**Decision:** the API books exactly what it is sent (no implicit default), so partner requests
behave as today; the modern UI pre-fills the candidate's default in the form, so portal users keep
the convenience and can see and change it.

Transcripts: `accommodation_blank_uses_default_portal`, `accommodation_blank_uses_default_partner`

### 6b. Reschedule to the same session

booking-api rejects it (409 `SAME_SESSION`); `usp_RescheduleBooking` (portal) does not check and
would cancel and re-book the same seat. **Decision:** reject with 409 `SAME_SESSION` for every
channel (`ReschedulePolicy`). Different-exam reschedules are rejected for every channel as
`DIFFERENT_EXAM` / 50006, as in both legacy paths. Reschedule is one transaction (cancel with reason
`Rescheduled` + book), so a failed booking leaves the original intact.

Transcripts: `reschedule_same_session_portal`, `reschedule_same_session_partner`

## 7. Mixed portal/partner traffic followed by a capacity-sync run

**Legacy behaviour.** Transcript `mixed_traffic_then_capacity_sync` runs `scripts/mixed-traffic.ps1|.sh`
(fixed seed; portal and partner bookings, cancels and reschedules) and then `POST /sync/run`. Two hidden
behaviours compound on the legacy estate:

- **HB-01** (section 1, `docs/as-is/hidden-behavior.md#hb-01`): every partner booking decrements
  `SeatsAvailable` twice (booking-api + `trg_Booking_SeatSync`), and a cancel gives back one seat. The
  busiest sessions of the workload therefore look full while seats are still free, so later bookings in
  the same run are **waitlisted** instead of seated.
- **HB-03** (section 3, `docs/as-is/hidden-behavior.md#hb-03`): capacity-sync rewrites `SeatsAvailable`
  with formula B, so `db/checks/seat-drift.sql` (formula A) reports drift on every OPEN session with
  accommodation seats.

The drift evidence is `reports/drift-before-after.md`: the same mixed workload plus a capacity-sync run
leaves **112** drifted sessions at `legacy-baseline` and **0** on the modern stack (reconciliation: 0
mismatches).

**Decision.** This is the intended outcome of sections 1 and 3, not a new rule: on the modern stack every
booking takes exactly one seat and seats are always derived with formula A, so the bookings that legacy
waitlisted are seated, nothing is waitlisted, the sync run is a no-op and there is no drift.

**What may differ in the replay** (`characterization/modern-classification.yaml` restricts the
ADR-APPROVED DIVERGENCE to these fields only): the status of the bookings the legacy run waitlisted, the
waitlist rows themselves (sessions 154 and 157), the session seat counters, and the drift counters that
`db/checks/seat-drift.sql` returns after the workload and after the sync run. Every other business field
and every other table still has to match.

Transcripts: `mixed_traffic_then_capacity_sync`

## Consequences

- One codebase owns seat inventory; no trigger or batch job rewrites counts behind it.
- Partner seat counts and partner retake dates change (sections 1 and 4): call these out to the
  gateway (M3) and partner-facing release notes.
- M1 transcript ids are filled in above. `characterization/replay --target modern --base-url <url>`
  writes `characterization/reports/replay-modern-<sha>.md`; link every failing transcript to a section
  here, to `characterization/known-defects.yaml`, or to a follow-up bug (M6).
