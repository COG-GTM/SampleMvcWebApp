package com.northwind.assessment.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RulesTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 2, 9, 0);

    @Test
    void accommodationCodes_portalAccommodationCodesCs() {
        assertThat(AccommodationCodes.normalize(null)).isNull();
        assertThat(AccommodationCodes.normalize("  ")).isNull();
        assertThat(AccommodationCodes.normalize(" et25 ")).isEqualTo("ET25");
        assertThat(AccommodationCodes.isKnown(null)).isTrue();
        assertThat(AccommodationCodes.isKnown("SR")).isTrue();
        assertThat(AccommodationCodes.isKnown("XX")).isFalse();
        assertThat(AccommodationCodes.requiresAccommodationSeat("ET25")).isTrue();
        assertThat(AccommodationCodes.requiresAccommodationSeat("et50")).isTrue();
        assertThat(AccommodationCodes.requiresAccommodationSeat("SR")).isFalse();
        assertThat(AccommodationCodes.requiresAccommodationSeat("RB")).isFalse();
        assertThat(AccommodationCodes.requiresAccommodationSeat(null)).isFalse();
    }

    @Test
    void bookingStatus_seatAndActiveRules() {
        assertThat(BookingStatus.BOOKED.holdsSeat()).isTrue();
        assertThat(BookingStatus.ATTENDED.holdsSeat()).isTrue();
        assertThat(BookingStatus.WAITLISTED.holdsSeat()).isFalse();
        assertThat(BookingStatus.CANCELLED.holdsSeat()).isFalse();
        assertThat(BookingStatus.WAITLISTED.isActive()).isTrue();
        assertThat(BookingStatus.ATTENDED.isActive()).isFalse();
    }

    @Test
    void bookingOrigin_disjointIdRangeFrom50000000() {
        assertThat(BookingOrigin.ofBookingId(3587)).isEqualTo(BookingOrigin.LEGACY_IMPORT);
        assertThat(BookingOrigin.ofBookingId(49_999_999)).isEqualTo(BookingOrigin.LEGACY_IMPORT);
        assertThat(BookingOrigin.ofBookingId(50_000_000)).isEqualTo(BookingOrigin.MODERN);
        assertThat(BookingOrigin.ofBookingId(50_000_001)).isEqualTo(BookingOrigin.MODERN);
    }

    @Test
    void ruleViolation_legacyCodesMatchUspRaiserrorStates() {
        assertThat(RuleViolation.SESSION_NOT_OPEN.legacyCode()).isEqualTo(50001);
        assertThat(RuleViolation.DUPLICATE_BOOKING.legacyCode()).isEqualTo(50002);
        assertThat(RuleViolation.NO_ACCOMMODATION_SEAT.legacyCode()).isEqualTo(50003);
        assertThat(RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR.legacyCode()).isEqualTo(50005);
        assertThat(RuleViolation.DIFFERENT_EXAM.legacyCode()).isEqualTo(50006);
        assertThat(RuleViolation.SAME_SESSION.legacyCode()).isNull();
        assertThat(RuleViolation.VALIDATION.kind()).isEqualTo(RuleViolation.Kind.INVALID);
        assertThat(RuleViolation.NOT_ELIGIBLE.kind()).isEqualTo(RuleViolation.Kind.CONFLICT);
        assertThat(RuleViolation.DIFFERENT_EXAM.errorCode()).isEqualTo("DIFFERENT_EXAM");
    }

    @Test
    void seatFormulas_procFormulaVsCapacitySyncFormulaDisagreeWithAccommodationSeats() {
        List<BookingSlot> bookings = List.of(
                new BookingSlot(1, 1, BookingStatus.BOOKED, null, T),
                new BookingSlot(2, 2, BookingStatus.BOOKED, "ET25", T),
                new BookingSlot(3, 3, BookingStatus.ATTENDED, "SR", T),
                new BookingSlot(4, 4, BookingStatus.WAITLISTED, "ET50", T));
        assertThat(SeatFormulas.seatsHeld(bookings)).isEqualTo(3);
        assertThat(SeatFormulas.accommodationSeatsUsed(bookings)).isEqualTo(1);
        // formula A (procs / seat-drift): 12 - 3 = 9
        assertThat(SeatFormulas.seatsAvailable(12, bookings)).isEqualTo(9);
        // formula B (capacity-sync): 12 - 2 - (3 - 1) = 8
        assertThat(SeatFormulas.capacitySyncSeatsAvailable(12, 2, bookings)).isEqualTo(8);
        assertThat(SeatFormulas.capacitySyncSeatsAvailable(2, 2, bookings)).isZero();
        assertThat(SeatFormulas.seatsAvailable(1, bookings)).isZero();
    }

    @Test
    void retakeDays_calendarIsPortalAddDays() {
        assertThat(RetakeDayCounting.CALENDAR.add(LocalDate.of(2026, 10, 1), 30)).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void retakeDays_workingDaysIsBookingApiAddWorkingDays() {
        // Thu 2026-10-01 + 30 working days = Thu 2026-11-12 (6 weekends skipped)
        assertThat(RetakeDayCounting.WORKING_DAYS.add(LocalDate.of(2026, 10, 1), 30)).isEqualTo(LocalDate.of(2026, 11, 12));
        // Fri + 1 working day = Mon
        assertThat(RetakeDayCounting.WORKING_DAYS.add(LocalDate.of(2026, 10, 2), 1)).isEqualTo(LocalDate.of(2026, 10, 5));
        // zero days keeps a weekend date, as DateUtils does
        assertThat(RetakeDayCounting.WORKING_DAYS.add(LocalDate.of(2026, 10, 3), 0)).isEqualTo(LocalDate.of(2026, 10, 3));
    }

    @Test
    void retakeEligibility_firstAttempt() {
        RetakeEligibilityPolicy p = new RetakeEligibilityPolicy(RetakeDayCounting.CALENDAR);
        assertThat(p.dayCounting()).isEqualTo(RetakeDayCounting.CALENDAR);
        assertThat(p.evaluate(List.of(), 30, LocalDate.of(2026, 11, 10)))
                .isEqualTo(new EligibilityDecision(true, "FIRST_ATTEMPT", null));
        assertThat(p.evaluate(null, 30, LocalDate.of(2026, 11, 10)).reason()).isEqualTo("FIRST_ATTEMPT");
    }

    @Test
    void retakeEligibility_alreadyPassedEvenIfLaterFail() {
        RetakeEligibilityPolicy p = new RetakeEligibilityPolicy(RetakeDayCounting.CALENDAR);
        List<ExamAttempt> attempts = List.of(new ExamAttempt(LocalDate.of(2026, 9, 1), true),
                new ExamAttempt(LocalDate.of(2026, 10, 1), false));
        assertThat(p.evaluate(attempts, 30, LocalDate.of(2027, 1, 1)))
                .isEqualTo(new EligibilityDecision(false, "ALREADY_PASSED", null));
    }

    @Test
    void retakeEligibility_insideOnAndOutsideWindow_calendar() {
        RetakeEligibilityPolicy p = new RetakeEligibilityPolicy(RetakeDayCounting.CALENDAR);
        List<ExamAttempt> attempts = List.of(new ExamAttempt(LocalDate.of(2026, 8, 1), false),
                new ExamAttempt(LocalDate.of(2026, 10, 1), false));
        LocalDate earliest = LocalDate.of(2026, 10, 31);
        assertThat(p.evaluate(attempts, 30, LocalDate.of(2026, 10, 30)))
                .isEqualTo(new EligibilityDecision(false, "RETAKE_WAIT_NOT_ELAPSED", earliest));
        assertThat(p.evaluate(attempts, 30, earliest)).isEqualTo(new EligibilityDecision(true, "RETAKE_ALLOWED", earliest));
        assertThat(p.evaluate(attempts, 30, LocalDate.of(2026, 11, 20)).eligible()).isTrue();
    }

    @Test
    void retakeEligibility_workingDaysVariantMatchesBookingApi() {
        RetakeEligibilityPolicy p = new RetakeEligibilityPolicy(RetakeDayCounting.WORKING_DAYS);
        List<ExamAttempt> attempts = List.of(new ExamAttempt(LocalDate.of(2026, 10, 1), false));
        // calendar would allow 2026-11-05; working days still wait until 2026-11-12
        assertThat(p.evaluate(attempts, 30, LocalDate.of(2026, 11, 5)))
                .isEqualTo(new EligibilityDecision(false, "RETAKE_WAIT_NOT_ELAPSED", LocalDate.of(2026, 11, 12)));
    }

    @Test
    void retakeEligibility_requiresDate() {
        RetakeEligibilityPolicy p = new RetakeEligibilityPolicy(RetakeDayCounting.CALENDAR);
        assertThatThrownBy(() -> p.evaluate(List.of(), 30, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ExamAttempt(null, false)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void usp_RescheduleBooking_rejectsDifferentExam_50006() {
        assertThatThrownBy(() -> ReschedulePolicy.check(1, 3, 2, 4))
                .isInstanceOfSatisfying(SchedulingRuleException.class, e -> {
                    assertThat(e.violation()).isEqualTo(RuleViolation.DIFFERENT_EXAM);
                    assertThat(e.legacyCode()).isEqualTo(50006);
                });
    }

    @Test
    void bookingApiReschedule_rejectsSameSession() {
        assertThatThrownBy(() -> ReschedulePolicy.check(1, 3, 1, 3))
                .isInstanceOfSatisfying(SchedulingRuleException.class,
                        e -> assertThat(e.violation()).isEqualTo(RuleViolation.SAME_SESSION));
    }

    @Test
    void usp_RescheduleBooking_sameExamOtherSessionIsAllowed() {
        ReschedulePolicy.check(1, 3, 2, 3);
        assertThat(ReschedulePolicy.CANCEL_REASON).isEqualTo("Rescheduled");
    }

    @Test
    void bookingSlotNormalisesCodeAndRequiresFields() {
        assertThat(new BookingSlot(1, 1, BookingStatus.BOOKED, " et25", T).accommodationCode()).isEqualTo("ET25");
        assertThatThrownBy(() -> new BookingSlot(1, 1, null, null, T)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BookingSlot(1, 1, BookingStatus.BOOKED, null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void schedulingRuleExceptionCarriesViolation() {
        SchedulingRuleException e = new SchedulingRuleException(RuleViolation.NOT_ELIGIBLE, "nope");
        assertThat(e.getMessage()).isEqualTo("nope");
        assertThat(e.legacyCode()).isNull();
        assertThat(BookingChannel.valueOf("PARTNER")).isEqualTo(BookingChannel.PARTNER);
    }
}
