package com.northwind.assessment.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Test names cite the legacy proc / trigger behaviour each rule replaces. */
class SessionCapacityTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 9, 0);

    private static BookingSlot slot(long id, int candidate, BookingStatus status, String acc, int minutes) {
        return new BookingSlot(id, candidate, status, acc, T0.plusMinutes(minutes));
    }

    private static SessionCapacity session(SessionStatus status, int capacity, int accCap, List<BookingSlot> bookings,
                                           List<WaitlistSlot> waitlist) {
        return new SessionCapacity(7, 3, status, capacity, accCap, bookings, waitlist);
    }

    private static SessionCapacity open(int capacity, int accCap, BookingSlot... bookings) {
        return session(SessionStatus.OPEN, capacity, accCap, List.of(bookings), List.of());
    }

    private static void assertViolation(Runnable r, RuleViolation v, Integer legacyCode) {
        assertThatThrownBy(r::run)
                .isInstanceOfSatisfying(SchedulingRuleException.class, e -> {
                    assertThat(e.violation()).isEqualTo(v);
                    assertThat(e.legacyCode()).isEqualTo(legacyCode);
                });
    }

    @Test
    void seatDrift_bookedAndAttendedHoldSeats_cancelledAndWaitlistedDoNot() {
        SessionCapacity s = open(10, 2,
                slot(1, 1, BookingStatus.BOOKED, null, 0),
                slot(2, 2, BookingStatus.ATTENDED, null, 1),
                slot(3, 3, BookingStatus.CANCELLED, null, 2),
                slot(4, 4, BookingStatus.WAITLISTED, null, 3));
        assertThat(s.seatsHeld()).isEqualTo(2);
        assertThat(s.seatsAvailable()).isEqualTo(8);
        assertThat(s.sessionId()).isEqualTo(7);
        assertThat(s.examId()).isEqualTo(3);
        assertThat(s.status()).isEqualTo(SessionStatus.OPEN);
        assertThat(s.capacity()).isEqualTo(10);
        assertThat(s.accommodationCapacity()).isEqualTo(2);
        assertThat(s.bookings()).hasSize(4);
    }

    @Test
    void usp_BookSeat_booksWhileSeatsFree_andSeatsAvailableDropsByExactlyOne() {
        SessionCapacity s = open(3, 1, slot(1, 1, BookingStatus.BOOKED, null, 0));
        int before = s.seatsAvailable();
        Reservation r = s.reserve(42, null);
        assertThat(r.status()).isEqualTo(BookingStatus.BOOKED);
        assertThat(r.waitlistPosition()).isNull();
        s.record(50_000_001L, 42, r, T0.plusHours(1));
        assertThat(s.seatsAvailable()).isEqualTo(before - 1);
        assertThat(s.waitlistCount()).isZero();
    }

    @Test
    void usp_BookSeat_rejectsWhenSessionNotOpen_50001() {
        for (SessionStatus st : List.of(SessionStatus.CLOSED, SessionStatus.DELIVERED, SessionStatus.CANCELLED)) {
            SessionCapacity s = session(st, 5, 0, List.of(), List.of());
            assertViolation(() -> s.reserve(1, null), RuleViolation.SESSION_NOT_OPEN, 50001);
        }
    }

    @Test
    void usp_BookSeat_rejectsDuplicateActiveBooking_50002() {
        SessionCapacity booked = open(5, 0, slot(1, 9, BookingStatus.BOOKED, null, 0));
        assertViolation(() -> booked.reserve(9, null), RuleViolation.DUPLICATE_BOOKING, 50002);
        SessionCapacity waitlisted = session(SessionStatus.OPEN, 1, 0,
                List.of(slot(1, 8, BookingStatus.BOOKED, null, 0), slot(2, 9, BookingStatus.WAITLISTED, null, 1)),
                List.of(new WaitlistSlot(2, 1)));
        assertViolation(() -> waitlisted.reserve(9, null), RuleViolation.DUPLICATE_BOOKING, 50002);
    }

    @Test
    void usp_BookSeat_allowsRebookAfterCancelledOrAttended() {
        SessionCapacity s = open(5, 0,
                slot(1, 9, BookingStatus.CANCELLED, null, 0),
                slot(2, 10, BookingStatus.ATTENDED, null, 0));
        assertThat(s.reserve(9, null).status()).isEqualTo(BookingStatus.BOOKED);
        assertThat(s.reserve(10, null).status()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void usp_BookSeat_rejectsExtraTimeWhenAccommodationSeatsUsed_50003() {
        SessionCapacity s = open(10, 2,
                slot(1, 1, BookingStatus.BOOKED, "ET25", 0),
                slot(2, 2, BookingStatus.ATTENDED, "et50", 1));
        assertThat(s.accommodationSeatsUsed()).isEqualTo(2);
        assertThat(s.accommodationSeatsAvailable()).isZero();
        assertViolation(() -> s.reserve(3, "ET25"), RuleViolation.NO_ACCOMMODATION_SEAT, 50003);
        assertViolation(() -> s.reserve(3, " et50 "), RuleViolation.NO_ACCOMMODATION_SEAT, 50003);
    }

    @Test
    void usp_BookSeat_separateRoomAndRestBreaksDoNotNeedAccommodationSeat() {
        SessionCapacity s = open(10, 1, slot(1, 1, BookingStatus.BOOKED, "ET25", 0));
        Reservation sr = s.reserve(3, "sr");
        assertThat(sr.status()).isEqualTo(BookingStatus.BOOKED);
        assertThat(sr.accommodationCode()).isEqualTo("SR");
        assertThat(s.reserve(4, "RB").status()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void usp_BookSeat_extraTimeAllowedWhileAccommodationSeatFree_cancelledEtDoesNotCount() {
        SessionCapacity s = open(10, 1,
                slot(1, 1, BookingStatus.CANCELLED, "ET25", 0),
                slot(2, 2, BookingStatus.WAITLISTED, "ET50", 1));
        assertThat(s.accommodationSeatsUsed()).isZero();
        assertThat(s.reserve(3, "ET50").status()).isEqualTo(BookingStatus.BOOKED);
    }

    @Test
    void usp_BookSeat_waitlistsWhenFull_withNextPosition() {
        SessionCapacity s = session(SessionStatus.OPEN, 2, 0,
                new ArrayList<>(List.of(slot(1, 1, BookingStatus.BOOKED, null, 0),
                        slot(2, 2, BookingStatus.BOOKED, null, 1),
                        slot(3, 3, BookingStatus.WAITLISTED, null, 2))),
                List.of(new WaitlistSlot(3, 4)));
        assertThat(s.seatsAvailable()).isZero();
        Reservation r = s.reserve(5, null);
        assertThat(r.status()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(r.waitlistPosition()).isEqualTo(5);
        s.record(50_000_002L, 5, r, T0.plusHours(2));
        assertThat(s.waitlistCount()).isEqualTo(2);
        assertThat(s.reserve(6, null).waitlistPosition()).isEqualTo(6);
        assertThat(s.waitlist()).extracting(WaitlistSlot::position).containsExactly(4, 5);
    }

    @Test
    void usp_BookSeat_firstWaitlistPositionIsOne() {
        SessionCapacity s = open(1, 0, slot(1, 1, BookingStatus.BOOKED, null, 0));
        assertThat(s.nextWaitlistPosition()).isEqualTo(1);
        assertThat(s.reserve(2, null)).isEqualTo(Reservation.waitlisted(1, null));
    }

    @Test
    void usp_BookSeat_extraTimeWaitlistedWhenFullButAccommodationSeatFree() {
        SessionCapacity s = open(1, 1, slot(1, 1, BookingStatus.BOOKED, null, 0));
        Reservation r = s.reserve(2, "ET25");
        assertThat(r.status()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(r.accommodationCode()).isEqualTo("ET25");
    }

    @Test
    void trg_Booking_SeatSync_partnerBookingTakesExactlyOneSeat_noDoubleDecrement() {
        SessionCapacity s = open(12, 0);
        Reservation portal = s.reserve(1, null);
        s.record(50_000_001L, 1, portal, T0);
        Reservation partner = s.reserve(2, null);
        s.record(50_000_002L, 2, partner, T0.plusMinutes(1));
        assertThat(s.seatsAvailable()).isEqualTo(10);
    }

    @Test
    void usp_CancelBooking_bookedCancellationFreesSeat_noAutoPromotion() {
        SessionCapacity s = session(SessionStatus.OPEN, 1, 0,
                List.of(slot(1, 1, BookingStatus.BOOKED, null, 0), slot(2, 2, BookingStatus.WAITLISTED, null, 1)),
                List.of(new WaitlistSlot(2, 1)));
        assertThat(s.cancel(1)).isEqualTo(BookingStatus.BOOKED);
        assertThat(s.seatsAvailable()).isEqualTo(1);
        assertThat(s.waitlistCount()).isEqualTo(1);
        assertThat(s.bookings()).filteredOn(b -> b.bookingId() == 2)
                .extracting(BookingSlot::status).containsExactly(BookingStatus.WAITLISTED);
    }

    @Test
    void usp_CancelBooking_waitlistedCancellationRemovesWaitlistRow_positionsNotCompacted() {
        SessionCapacity s = session(SessionStatus.OPEN, 0, 0,
                List.of(slot(2, 2, BookingStatus.WAITLISTED, null, 1), slot(3, 3, BookingStatus.WAITLISTED, null, 2)),
                List.of(new WaitlistSlot(2, 1), new WaitlistSlot(3, 2)));
        assertThat(s.cancel(2)).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(s.waitlist()).containsExactly(new WaitlistSlot(3, 2));
        assertThat(s.nextWaitlistPosition()).isEqualTo(3);
    }

    @Test
    void usp_CancelBooking_rejectsCancelledOrAttended_50004() {
        SessionCapacity s = open(5, 0, slot(1, 1, BookingStatus.CANCELLED, null, 0), slot(2, 2, BookingStatus.ATTENDED, null, 0));
        assertViolation(() -> s.cancel(1), RuleViolation.NOT_CANCELLABLE, 50004);
        assertViolation(() -> s.cancel(2), RuleViolation.NOT_CANCELLABLE, 50004);
    }

    @Test
    void cancel_unknownBookingIsAProgrammingError() {
        SessionCapacity s = open(5, 0);
        assertThatThrownBy(() -> s.cancel(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usp_ChangeCapacity_rejectsBelowAllocatedAccommodationCapacity_50005() {
        SessionCapacity s = open(10, 3);
        assertViolation(() -> s.changeCapacity(2, AccommodationFloorPolicy.ALLOCATED),
                RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR, 50005);
        assertThat(s.capacity()).isEqualTo(10);
        assertThat(s.changeCapacity(3, AccommodationFloorPolicy.ALLOCATED).newCapacity()).isEqualTo(3);
    }

    /**
     * Guardrail for on-call defect #57 (the 30 -> 24 shape): ATTENDED still count in the ALLOCATED floor
     * (4 accommodation seats + 2 ATTENDED + 19 BOOKED = 25), even though only BOOKED count for the waitlist bump.
     */
    @Test
    void onCallDefect57_floorStillCountsAttended_30To24Rejected_bumpCountUnaffected() {
        BookingSlot[] slots = new BookingSlot[21];
        for (int i = 0; i < 21; i++) {
            slots[i] = slot(i + 1, i + 1, i < 2 ? BookingStatus.ATTENDED : BookingStatus.BOOKED, null, i);
        }
        SessionCapacity s = open(30, 4, slots);
        assertViolation(() -> s.changeCapacity(24, AccommodationFloorPolicy.ALLOCATED),
                RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR, 50005);
        assertThat(s.capacity()).isEqualTo(30);
        assertThat(s.changeCapacity(25, AccommodationFloorPolicy.ALLOCATED).bumpedBookingIds()).isEmpty();
    }

    @Test
    void usp_ChangeCapacity_allocatedFloorKeepsAccommodationSeatsForExtraTime_50005() {
        SessionCapacity s = open(12, 2,
                slot(1, 1, BookingStatus.BOOKED, null, 0),
                slot(2, 2, BookingStatus.BOOKED, null, 1),
                slot(3, 3, BookingStatus.BOOKED, "SR", 2),
                slot(4, 4, BookingStatus.ATTENDED, null, 3),
                slot(5, 5, BookingStatus.BOOKED, "ET25", 4));
        assertViolation(() -> s.changeCapacity(5, AccommodationFloorPolicy.ALLOCATED),
                RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR, 50005);
        assertThat(s.capacity()).isEqualTo(12);
        CapacityChange c = s.changeCapacity(6, AccommodationFloorPolicy.ALLOCATED);
        assertThat(c.newCapacity()).isEqualTo(6);
        assertThat(c.bumped()).isEmpty();
    }

    @Test
    void usp_ChangeCapacity_rejectsNegativeCapacity() {
        SessionCapacity s = open(10, 0);
        assertViolation(() -> s.changeCapacity(-1, AccommodationFloorPolicy.ALLOCATED), RuleViolation.VALIDATION, 50005);
    }

    @Test
    void pr51_bookedExtraTimeFloor_allowsBelowAllocationButNotBelowBookedEtSeats() {
        SessionCapacity s = open(10, 4, slot(1, 1, BookingStatus.BOOKED, "ET25", 0), slot(2, 2, BookingStatus.BOOKED, "ET50", 1));
        assertThat(s.changeCapacity(2, AccommodationFloorPolicy.BOOKED_EXTRA_TIME).bumped()).isEmpty();
        assertViolation(() -> s.changeCapacity(1, AccommodationFloorPolicy.BOOKED_EXTRA_TIME),
                RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR, 50005);
    }

    @Test
    void usp_ChangeCapacity_increaseAddsSeats_noWaitlistPromotion() {
        SessionCapacity s = session(SessionStatus.OPEN, 1, 0,
                List.of(slot(1, 1, BookingStatus.BOOKED, null, 0), slot(2, 2, BookingStatus.WAITLISTED, null, 1)),
                List.of(new WaitlistSlot(2, 1)));
        CapacityChange c = s.changeCapacity(3, AccommodationFloorPolicy.ALLOCATED);
        assertThat(c.oldCapacity()).isEqualTo(1);
        assertThat(c.bumpedBookingIds()).isEmpty();
        assertThat(s.seatsAvailable()).isEqualTo(2);
        assertThat(s.waitlistCount()).isEqualTo(1);
    }

    @Test
    void trg_ExamSession_Update_decreaseWithinFreeSeatsBumpsNobody() {
        SessionCapacity s = open(10, 0, slot(1, 1, BookingStatus.BOOKED, null, 0));
        CapacityChange c = s.changeCapacity(4, AccommodationFloorPolicy.ALLOCATED);
        assertThat(c.bumped()).isEmpty();
        assertThat(s.seatsAvailable()).isEqualTo(3);
    }

    @Test
    void trg_ExamSession_Update_bumpsLatestBookedToWaitlist() {
        SessionCapacity s = session(SessionStatus.OPEN, 4, 0,
                List.of(slot(10, 1, BookingStatus.BOOKED, null, 0),
                        slot(11, 2, BookingStatus.BOOKED, null, 10),
                        slot(12, 3, BookingStatus.BOOKED, null, 20),
                        slot(13, 4, BookingStatus.BOOKED, null, 20),
                        slot(14, 5, BookingStatus.WAITLISTED, null, 30)),
                List.of(new WaitlistSlot(14, 1)));
        CapacityChange c = s.changeCapacity(2, AccommodationFloorPolicy.ALLOCATED);
        // newest first by BookedAt DESC, BookingId DESC -> 13 then 12; appended oldest-first after position 1
        assertThat(c.bumped()).containsExactly(new WaitlistSlot(12, 2), new WaitlistSlot(13, 3));
        assertThat(c.bumpedBookingIds()).containsExactly(12L, 13L);
        assertThat(s.seatsAvailable()).isZero();
        assertThat(s.seatsHeld()).isEqualTo(2);
        assertThat(s.waitlist()).extracting(WaitlistSlot::bookingId).containsExactly(14L, 12L, 13L);
    }

    @Test
    void trg_ExamSession_Update_attendedHoldSeatsButAreNeverBumped() {
        SessionCapacity s = open(3,
                0,
                slot(1, 1, BookingStatus.ATTENDED, null, 50),
                slot(2, 2, BookingStatus.ATTENDED, null, 60),
                slot(3, 3, BookingStatus.BOOKED, null, 0));
        CapacityChange c = s.changeCapacity(0, AccommodationFloorPolicy.ALLOCATED);
        assertThat(c.bumpedBookingIds()).containsExactly(3L);
        assertThat(s.seatsAvailable()).isZero();
    }

    @Test
    void trg_ExamSession_Update_onlyBookedCountAgainstNewCapacity_deliveredSessionWithAttendedBumpsNobody() {
        // capacity_decrease_delivered_with_attended_admin: 2 ATTENDED + 1 BOOKED, capacity 3 -> 1; BOOKED (1) fits in 1.
        SessionCapacity s = open(3,
                0,
                slot(1, 1, BookingStatus.ATTENDED, null, 50),
                slot(2, 2, BookingStatus.ATTENDED, null, 60),
                slot(3, 3, BookingStatus.BOOKED, null, 0));
        CapacityChange c = s.changeCapacity(1, AccommodationFloorPolicy.ALLOCATED);
        assertThat(c.bumpedBookingIds()).isEmpty();
        assertThat(s.waitlist()).isEmpty();
        assertThat(s.seatsAvailable()).isZero();
    }

    @Test
    void trg_ExamSession_Update_capacityZeroBumpsEveryBooked() {
        SessionCapacity s = open(2, 0, slot(1, 1, BookingStatus.BOOKED, null, 0), slot(2, 2, BookingStatus.BOOKED, null, 5));
        assertThat(s.changeCapacity(0, AccommodationFloorPolicy.ALLOCATED).bumpedBookingIds()).containsExactly(1L, 2L);
        assertThat(s.waitlist()).extracting(WaitlistSlot::position).containsExactly(1, 2);
    }

    @Test
    void seatsAvailableNeverNegative() {
        SessionCapacity s = open(1, 0, slot(1, 1, BookingStatus.ATTENDED, null, 0), slot(2, 2, BookingStatus.ATTENDED, null, 0));
        assertThat(s.seatsAvailable()).isZero();
    }

    @Test
    void attend_bookedBecomesAttended_stillHoldsSeat_repeatIsNoOp() {
        SessionCapacity s = open(3, 1, slot(1, 1, BookingStatus.BOOKED, null, 0));
        int before = s.seatsAvailable();
        assertThat(s.attend(1)).isTrue();
        assertThat(s.attend(1)).isFalse();
        assertThat(s.seatsAvailable()).isEqualTo(before);
    }

    @Test
    void attend_cancelledOrWaitlisted_rejectedAsNotActive() {
        SessionCapacity s = open(3, 1, slot(1, 1, BookingStatus.CANCELLED, null, 0), slot(2, 2, BookingStatus.WAITLISTED, null, 1));
        assertViolation(() -> s.attend(1), RuleViolation.BOOKING_NOT_ACTIVE, null);
        assertViolation(() -> s.attend(2), RuleViolation.BOOKING_NOT_ACTIVE, null);
    }

    // ---- MBA-3829 / ADR 0003: pending seat offers hold seats ----

    private static SessionCapacity fullWithOffer() {
        List<BookingSlot> bookings = List.of(
                slot(1, 1, BookingStatus.BOOKED, null, 0),
                slot(2, 2, BookingStatus.WAITLISTED, null, 1),
                slot(3, 3, BookingStatus.WAITLISTED, null, 2),
                slot(4, 4, BookingStatus.WAITLISTED, null, 3));
        return new SessionCapacity(7, 3, SessionStatus.OPEN, 2, 0, bookings,
                List.of(new WaitlistSlot(2, 1), new WaitlistSlot(3, 2), new WaitlistSlot(4, 3)),
                List.of(new PendingOffer(11, 2, SeatKind.STANDARD, T0, T0.plusHours(24))));
    }

    @Test
    void heldSeat_pendingOfferCountsAgainstSeatsAvailable_andReserveWaitlistsOthers() {
        SessionCapacity s = fullWithOffer();
        assertThat(s.seatsHeld()).isEqualTo(1);
        assertThat(s.seatsOffered()).isEqualTo(1);
        assertThat(s.seatsAvailable()).isZero();
        assertThat(SeatFormulas.seatsAvailable(2, s.bookings(), 1)).isZero();
        assertThat(SeatFormulas.seatsAvailable(2, 1, 1)).isZero();
        assertThat(SeatFormulas.seatsAvailable(2, 2, 1)).as("never below zero").isZero();
        Reservation r = s.reserve(99, null);
        assertThat(r.status()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(r.waitlistPosition()).isEqualTo(4);
        assertThat(s.pendingOffers()).hasSize(1);
        assertThat(s.pendingOffer(2)).isPresent();
        assertThat(s.pendingOffer(3)).isEmpty();
    }

    @Test
    void acceptOffer_booksTheCandidate_removesTheEntry_andCompactsLaterPositions() {
        SessionCapacity s = fullWithOffer();
        List<WaitlistSlot> shifted = s.acceptOffer(2, T0.plusHours(23));
        assertThat(shifted).containsExactly(new WaitlistSlot(3, 1), new WaitlistSlot(4, 2));
        assertThat(s.waitlist()).containsExactly(new WaitlistSlot(3, 1), new WaitlistSlot(4, 2));
        assertThat(s.bookings()).filteredOn(b -> b.bookingId() == 2).singleElement()
                .extracting(BookingSlot::status).isEqualTo(BookingStatus.BOOKED);
        assertThat(s.seatsOffered()).isZero();
        assertThat(s.seatsHeld()).isEqualTo(2);
        assertThat(s.seatsAvailable()).isZero();
    }

    @Test
    void acceptOffer_afterExpiresAt_isRejectedEvenBeforeTheExpiryJobRan() {
        SessionCapacity s = fullWithOffer();
        assertViolation(() -> s.acceptOffer(2, T0.plusHours(24)), RuleViolation.OFFER_EXPIRED, null);
        assertThat(s.pendingOffer(2)).isPresent();
        assertThat(s.pendingOffers().get(0).isExpiredAt(T0.plusHours(24).minusSeconds(1))).isFalse();
    }

    @Test
    void offerTransitions_withoutAPendingOffer_areOfferNotPending() {
        SessionCapacity s = fullWithOffer();
        assertViolation(() -> s.acceptOffer(3, T0), RuleViolation.OFFER_NOT_PENDING, null);
        assertViolation(() -> s.giveUpOffer(3), RuleViolation.OFFER_NOT_PENDING, null);
        assertViolation(() -> s.withdrawOffer(3), RuleViolation.OFFER_NOT_PENDING, null);
    }

    @Test
    void addOffer_rejectsNonWaitlistedAndDuplicateOffers() {
        SessionCapacity s = fullWithOffer();
        assertThatThrownBy(() -> s.addOffer(new PendingOffer(12, 2, SeatKind.STANDARD, T0, T0.plusHours(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.addOffer(new PendingOffer(12, 1, SeatKind.STANDARD, T0, T0.plusHours(1))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> s.addOffer(new PendingOffer(12, 404, SeatKind.STANDARD, T0, T0.plusHours(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancellingAWaitlistedBookingWithAPendingOffer_releasesTheHeldSeat() {
        SessionCapacity s = fullWithOffer();
        assertThat(s.cancel(2)).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(s.seatsOffered()).isZero();
        assertThat(s.seatsAvailable()).isEqualTo(1);
        assertThat(s.waitlist()).extracting(WaitlistSlot::position).as("plain cancel leaves a gap").containsExactly(2, 3);
    }

    @Test
    void withdrawOffer_keepsTheWaitlistPosition() {
        SessionCapacity s = fullWithOffer();
        PendingOffer o = s.withdrawOffer(2);
        assertThat(o.offerId()).isEqualTo(11);
        assertThat(s.waitlist()).extracting(WaitlistSlot::bookingId).containsExactly(2L, 3L, 4L);
        assertThat(s.seatsAvailable()).isEqualTo(1);
    }

    @Test
    void capacityDecrease_withdrawsNewestOffersTheReducedCapacityCannotHold() {
        List<BookingSlot> bookings = List.of(
                slot(1, 1, BookingStatus.BOOKED, null, 0),
                slot(2, 2, BookingStatus.WAITLISTED, null, 1),
                slot(3, 3, BookingStatus.WAITLISTED, null, 2));
        SessionCapacity s = new SessionCapacity(7, 3, SessionStatus.OPEN, 3, 0, bookings,
                List.of(new WaitlistSlot(2, 1), new WaitlistSlot(3, 2)),
                List.of(new PendingOffer(11, 2, SeatKind.STANDARD, T0, T0.plusHours(24)),
                        new PendingOffer(12, 3, SeatKind.STANDARD, T0.plusMinutes(5), T0.plusHours(24))));
        CapacityChange change = s.changeCapacity(2, AccommodationFloorPolicy.ALLOCATED);
        assertThat(change.withdrawnOffers()).extracting(PendingOffer::offerId).containsExactly(12L);
        assertThat(change.bumped()).isEmpty();
        assertThat(s.pendingOffers()).extracting(PendingOffer::offerId).containsExactly(11L);
        assertThat(s.seatsAvailable()).isZero();

        CapacityChange toOne = s.changeCapacity(1, AccommodationFloorPolicy.ALLOCATED);
        assertThat(toOne.withdrawnOffers()).extracting(PendingOffer::offerId).containsExactly(11L);
        assertThat(s.waitlist()).extracting(WaitlistSlot::position).containsExactly(1, 2);

        CapacityChange up = s.changeCapacity(4, AccommodationFloorPolicy.ALLOCATED);
        assertThat(up.withdrawnOffers()).isEmpty();
        assertThat(s.seatsAvailable()).isEqualTo(3);
    }
}
