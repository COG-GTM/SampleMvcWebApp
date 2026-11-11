package com.northwind.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** One nested class per ported rule; transcript ids refer to characterization/transcripts. */
class RulesTest {

    static final SessionCapacity OPEN_154 = new SessionCapacity(20, 2, 9, 0, 0);
    static final EligibilityDecision OK = new EligibilityDecision(true, "FIRST_ATTEMPT", "No previous attempts", null);

    static ErrorCode codeOf(Runnable r) {
        try {
            r.run();
        } catch (SchedulingException e) {
            return e.code();
        }
        return null;
    }

    @Nested
    class Booking {
        @Test
        void booksWhenSeatFree() { // BK-02
            assertThat(BookingRules.decide(154, "NW00000901", SessionStatus.OPEN, OPEN_154, false, null, OK, "MHL-RN1"))
                    .isEqualTo(BookingRules.Outcome.BOOKED);
        }

        @Test
        void waitlistsWhenFull() { // BK-05/BK-06, session 153
            SessionCapacity full = new SessionCapacity(12, 1, 12, 0, 3);
            assertThat(BookingRules.decide(153, "NW00000901", SessionStatus.OPEN, full, false, null, OK, "BCB-AGL"))
                    .isEqualTo(BookingRules.Outcome.WAITLISTED);
            assertThat(BookingRules.nextWaitlistPosition(3)).isEqualTo(4);
            assertThat(BookingRules.nextWaitlistPosition(null)).isEqualTo(1);
        }

        @Test
        void rejectsNonOpenSession() { // BK closed/cancelled sessions 263/162
            for (SessionStatus st : List.of(SessionStatus.CLOSED, SessionStatus.CANCELLED, SessionStatus.DELIVERED)) {
                assertThatThrownBy(() -> BookingRules.decide(263, "NW1", st, OPEN_154, false, null, OK, "X"))
                        .isInstanceOf(SchedulingException.class)
                        .hasMessage("Session 263 is not open for booking");
            }
        }

        @Test
        void rejectsDuplicateActiveBooking() {
            assertThatThrownBy(() -> BookingRules.decide(154, "NW00000903", SessionStatus.OPEN, OPEN_154, true, null, OK, "X"))
                    .hasMessage("Candidate NW00000903 already has an active booking on session 154");
            assertThat(codeOf(() -> BookingRules.decide(154, "N", SessionStatus.OPEN, OPEN_154, true, null, OK, "X")))
                    .isEqualTo(ErrorCode.DUPLICATE_BOOKING);
        }

        @Test
        void extraTimeNeedsAccommodationSeatButSrRbDoNot() { // AC-01..AC-04, session 163 (acc cap 1)
            SessionCapacity used = new SessionCapacity(8, 1, 4, 1, 0);
            for (String et : List.of("ET25", "ET50")) {
                assertThat(codeOf(() -> BookingRules.decide(163, "N", SessionStatus.OPEN, used, false, et, OK, "X")))
                        .isEqualTo(ErrorCode.NO_ACCOMMODATION_SEAT);
            }
            for (String other : List.of("SR", "RB")) {
                assertThat(BookingRules.decide(163, "N", SessionStatus.OPEN, used, false, other, OK, "X"))
                        .isEqualTo(BookingRules.Outcome.BOOKED);
            }
            assertThat(ErrorCode.NO_ACCOMMODATION_SEAT.legacyCode()).isEqualTo(50003);
        }

        @Test
        void ineligibleCandidateRejectedWithEarliestDate() {
            EligibilityDecision wait = new EligibilityDecision(false, "RETAKE_WAIT_NOT_ELAPSED", "m", LocalDate.of(2026, 12, 7));
            assertThatThrownBy(() -> BookingRules.decide(1, "NW1", SessionStatus.OPEN, OPEN_154, false, null, wait, "HPI-ACC1"))
                    .hasMessage("Candidate NW1 is not eligible to book HPI-ACC1 (RETAKE_WAIT_NOT_ELAPSED), earliest retake date 2026-12-07");
            EligibilityDecision passed = new EligibilityDecision(false, "ALREADY_PASSED", "m", null);
            assertThatThrownBy(() -> BookingRules.decide(1, "NW1", SessionStatus.OPEN, OPEN_154, false, null, passed, "E"))
                    .hasMessage("Candidate NW1 is not eligible to book E (ALREADY_PASSED)");
            assertThat(BookingRules.decide(1, "NW1", SessionStatus.OPEN, OPEN_154, false, null, null, "E"))
                    .isEqualTo(BookingRules.Outcome.BOOKED);
        }
    }

    @Nested
    class Cancel {
        @Test
        void onlyBookedOrWaitlistedCanBeCancelled() { // CN-01..CN-06
            BookingRules.checkCancellable(1, BookingStatus.BOOKED);
            BookingRules.checkCancellable(1, BookingStatus.WAITLISTED);
            assertThatThrownBy(() -> BookingRules.checkCancellable(7, BookingStatus.CANCELLED))
                    .hasMessage("Booking 7 is CANCELLED");
            assertThat(codeOf(() -> BookingRules.checkCancellable(7, BookingStatus.ATTENDED)))
                    .isEqualTo(ErrorCode.NOT_CANCELLABLE);
            assertThat(ErrorCode.NOT_CANCELLABLE.legacyCode()).isEqualTo(50004);
        }

        @Test
        void statusSemantics() {
            assertThat(BookingStatus.BOOKED.occupiesSeat()).isTrue();
            assertThat(BookingStatus.ATTENDED.occupiesSeat()).isTrue();
            assertThat(BookingStatus.WAITLISTED.occupiesSeat()).isFalse();
            assertThat(BookingStatus.CANCELLED.isActive()).isFalse();
            assertThat(SessionStatus.OPEN.acceptsBookings()).isTrue();
            assertThat(SessionStatus.CLOSED.acceptsBookings()).isFalse();
        }
    }

    @Nested
    class Reschedule {
        @Test
        void sameExamDifferentSessionAllowed() {
            ReschedulePolicy.check(1, BookingStatus.BOOKED, 154, 5, 163, 5);
        }

        @Test
        void differentExamRejected() { // RS-04/RS-05
            assertThatThrownBy(() -> ReschedulePolicy.check(9, BookingStatus.BOOKED, 154, 5, 155, 6))
                    .hasMessage("Booking 9 cannot be moved to session 155 (different exam)");
            assertThat(ErrorCode.DIFFERENT_EXAM.legacyCode()).isEqualTo(50006);
        }

        @Test
        void sameSessionRejectedOnEveryChannel() { // RS-06 (api) vs RS-07 (portal), ODD-08
            assertThatThrownBy(() -> ReschedulePolicy.check(9, BookingStatus.BOOKED, 154, 5, 154, 5))
                    .hasMessage("Booking 9 is already on session 154");
            assertThat(ErrorCode.SAME_SESSION.legacyCode()).isNull();
        }

        @Test
        void cancelledBookingCannotMove() {
            assertThat(codeOf(() -> ReschedulePolicy.check(9, BookingStatus.CANCELLED, 154, 5, 163, 5)))
                    .isEqualTo(ErrorCode.NOT_CANCELLABLE);
            assertThat(ReschedulePolicy.CANCEL_REASON).isEqualTo("Rescheduled");
        }
    }

    @Nested
    class Capacity {
        final LocalDateTime t = LocalDateTime.of(2026, 11, 1, 9, 0);
        final List<CapacityChange.BookedSeat> nine = java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(i -> new CapacityChange.BookedSeat(100 + i, t.plusMinutes(i))).toList();

        @Test
        void raiseOrLowerAboveBookedDemotesNobody() { // CP-01/CP-02
            CapacityChange.Plan up = CapacityChange.plan(154, 25, OPEN_154, nine, null, AccommodationFloorPolicy.ALLOCATED);
            assertThat(up.demotions()).isEmpty();
            assertThat(up.oldCapacity()).isEqualTo(20);
            assertThat(up.newCapacity()).isEqualTo(25);
            assertThat(CapacityChange.plan(154, 10, OPEN_154, nine, null, AccommodationFloorPolicy.ALLOCATED).demotions()).isEmpty();
        }

        @Test
        void lowerBelowBookedDemotesNewestAndAppendsToWaitlist() { // CP-03/CP-04, ODD-02
            CapacityChange.Plan p = CapacityChange.plan(154, 6, OPEN_154, nine, 2, AccommodationFloorPolicy.ALLOCATED);
            assertThat(p.demotions()).containsExactly(
                    new CapacityChange.Demotion(107, 3), new CapacityChange.Demotion(108, 4),
                    new CapacityChange.Demotion(109, 5));
            assertThat(p.newAccommodationCapacity()).isEqualTo(2);
        }

        @Test
        void tiesOnBookedAtBrokenByBookingId() {
            List<CapacityChange.BookedSeat> same = List.of(new CapacityChange.BookedSeat(5, t),
                    new CapacityChange.BookedSeat(3, t), new CapacityChange.BookedSeat(4, t));
            SessionCapacity s = new SessionCapacity(3, 0, 3, 0, 0);
            assertThat(CapacityChange.plan(1, 1, s, same, null, AccommodationFloorPolicy.ALLOCATED).demotions())
                    .containsExactly(new CapacityChange.Demotion(4, 1), new CapacityChange.Demotion(5, 2));
        }

        @Test
        void belowAccommodationCapacityRejected() { // CP-05/CP-06, ODD-06
            assertThatThrownBy(() -> CapacityChange.plan(154, 1, OPEN_154, nine, null, AccommodationFloorPolicy.ALLOCATED))
                    .isInstanceOf(SchedulingException.class)
                    .hasMessageContaining("must be at least 2, the accommodation capacity");
            assertThat(codeOf(() -> CapacityChange.plan(154, -1, OPEN_154, nine, null, AccommodationFloorPolicy.BOOKED)))
                    .isEqualTo(ErrorCode.INVALID_CAPACITY);
            assertThat(ErrorCode.INVALID_CAPACITY.legacyCode()).isEqualTo(50005);
        }

        @Test
        void bookedFloorAllowsGoingBelowAllocationAndClampsIt() {
            SessionCapacity s = new SessionCapacity(10, 3, 2, 1, 0);
            List<CapacityChange.BookedSeat> two = nine.subList(0, 2);
            assertThat(AccommodationFloorPolicy.BOOKED.minimumCapacity(s)).isEqualTo(1);
            CapacityChange.Plan p = CapacityChange.plan(9, 2, s, two, null, AccommodationFloorPolicy.BOOKED);
            assertThat(p.newAccommodationCapacity()).isEqualTo(2);
            assertThat(p.oldAccommodationCapacity()).isEqualTo(3);
            assertThatThrownBy(() -> CapacityChange.plan(9, 0, s, two, null, AccommodationFloorPolicy.BOOKED))
                    .hasMessageContaining("accommodation seats in use");
        }

        @Test
        void floorPolicyParse() {
            assertThat(AccommodationFloorPolicy.parse(null)).isEqualTo(AccommodationFloorPolicy.ALLOCATED);
            assertThat(AccommodationFloorPolicy.parse("")).isEqualTo(AccommodationFloorPolicy.ALLOCATED);
            assertThat(AccommodationFloorPolicy.parse("booked")).isEqualTo(AccommodationFloorPolicy.BOOKED);
        }
    }

    @Nested
    class Accommodation {
        @Test
        void normalizes() {
            assertThat(AccommodationCodes.normalize(null)).isNull();
            assertThat(AccommodationCodes.normalize("  ")).isNull(); // ODD-07: blank = none
            assertThat(AccommodationCodes.normalize(" et25 ")).isEqualTo("ET25");
            assertThat(AccommodationCodes.normalize("rb")).isEqualTo("RB");
            assertThatThrownBy(() -> AccommodationCodes.normalize("xx")).hasMessage("Unknown accommodationCode XX");
            assertThat(AccommodationCodes.isExtraTime("ET50")).isTrue();
            assertThat(AccommodationCodes.isExtraTime("SR")).isFalse();
            assertThat(AccommodationCodes.isExtraTime(null)).isFalse();
        }
    }

    @Nested
    class Retake {
        final RetakeEligibilityPolicy calendar = new RetakeEligibilityPolicy(RetakeDayCounting.CALENDAR);
        final RetakeEligibilityPolicy working = new RetakeEligibilityPolicy(RetakeDayCounting.WORKING);
        // NW00000113 / HPI-ACC1: failed attempt 2026-11-07, wait 30 (RT-01..RT-04)
        final List<ExamAttempt> failed = List.of(new ExamAttempt(LocalDate.of(2026, 11, 7), false));

        @Test
        void firstAttempt() {
            EligibilityDecision d = calendar.evaluate(List.of(), 30, LocalDate.of(2026, 12, 1));
            assertThat(d.eligible()).isTrue();
            assertThat(d.reason()).isEqualTo("FIRST_ATTEMPT");
            assertThat(d.message()).isEqualTo("No previous attempts");
            assertThat(calendar.evaluate(null, 30, LocalDate.of(2026, 12, 1)).eligible()).isTrue();
        }

        @Test
        void alreadyPassed() {
            List<ExamAttempt> passed = List.of(new ExamAttempt(LocalDate.of(2026, 1, 1), false),
                    new ExamAttempt(LocalDate.of(2026, 2, 1), true));
            EligibilityDecision d = working.evaluate(passed, 30, LocalDate.of(2027, 1, 1));
            assertThat(d.eligible()).isFalse();
            assertThat(d.reason()).isEqualTo("ALREADY_PASSED");
            assertThat(d.earliestRetakeDate()).isNull();
        }

        @Test
        void calendarDaysInsideOnAndOutsideWindow() { // RT-02/RT-03/RT-04 portal rows
            EligibilityDecision inside = calendar.evaluate(failed, 30, LocalDate.of(2026, 12, 6));
            assertThat(inside.eligible()).isFalse();
            assertThat(inside.reason()).isEqualTo("RETAKE_WAIT_NOT_ELAPSED");
            assertThat(inside.message()).isEqualTo("Retake wait period applies until 07 Dec 2026");
            EligibilityDecision on = calendar.evaluate(failed, 30, LocalDate.of(2026, 12, 7));
            assertThat(on.eligible()).isTrue();
            assertThat(on.reason()).isEqualTo("RETAKE_ALLOWED");
            assertThat(on.message()).isEqualTo("Retake wait period has elapsed");
            assertThat(on.earliestRetakeDate()).isEqualTo(LocalDate.of(2026, 12, 7));
            assertThat(calendar.evaluate(failed, 30, LocalDate.of(2027, 1, 1)).eligible()).isTrue();
        }

        @Test
        void workingDaysSkipWeekends() { // RT api rows: 30 working days after 2026-11-07 = 2026-12-18
            assertThat(RetakeDayCounting.WORKING.earliestRetake(LocalDate.of(2026, 11, 7), 30))
                    .isEqualTo(LocalDate.of(2026, 12, 18));
            assertThat(RetakeDayCounting.WORKING.earliestRetake(LocalDate.of(2026, 11, 13), 1)) // Fri -> Mon
                    .isEqualTo(LocalDate.of(2026, 11, 16));
            EligibilityDecision d = working.evaluate(failed, 30, LocalDate.of(2026, 12, 7));
            assertThat(d.eligible()).isFalse();
            assertThat(d.earliestRetakeDate()).isEqualTo(LocalDate.of(2026, 12, 18));
            assertThat(working.counting()).isEqualTo(RetakeDayCounting.WORKING);
        }

        @Test
        void usesLatestAttempt() {
            List<ExamAttempt> two = List.of(new ExamAttempt(LocalDate.of(2026, 10, 1), false),
                    new ExamAttempt(LocalDate.of(2026, 11, 7), false));
            assertThat(calendar.evaluate(two, 30, LocalDate.of(2026, 11, 20)).earliestRetakeDate())
                    .isEqualTo(LocalDate.of(2026, 12, 7));
        }

        @Test
        void parse() {
            assertThat(RetakeDayCounting.parse(null)).isEqualTo(RetakeDayCounting.CALENDAR);
            assertThat(RetakeDayCounting.parse(" ")).isEqualTo(RetakeDayCounting.CALENDAR);
            assertThat(RetakeDayCounting.parse("working")).isEqualTo(RetakeDayCounting.WORKING);
        }
    }

    @Nested
    class Errors {
        @Test
        void httpMapping() {
            assertThat(ErrorCode.VALIDATION.httpStatus()).isEqualTo(400);
            assertThat(ErrorCode.SESSION_NOT_FOUND.httpStatus()).isEqualTo(404);
            assertThat(ErrorCode.SESSION_NOT_OPEN.httpStatus()).isEqualTo(409);
            assertThat(ErrorCode.SESSION_NOT_OPEN.legacyCode()).isEqualTo(50001);
            assertThat(ErrorCode.DUPLICATE_BOOKING.legacyState()).isEqualTo(2);
            assertThat(new SchedulingException(ErrorCode.VALIDATION, "x").code()).isEqualTo(ErrorCode.VALIDATION);
        }
    }
}
