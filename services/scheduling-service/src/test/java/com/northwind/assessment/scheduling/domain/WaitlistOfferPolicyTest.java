package com.northwind.assessment.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** MBA-3829 / ADR 0003: waitlist auto-offer rules. */
class WaitlistOfferPolicyTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 9, 0);
    private static final WaitlistOfferPolicy POLICY = new WaitlistOfferPolicy(WaitlistOfferPolicy.DEFAULT_TTL);

    /** Full session: bookings 1..capacity BOOKED, then waitlisted bookings 101.. at positions 1.. with the given codes. */
    private static SessionCapacity fullWithWaitlist(SessionStatus status, int capacity, int accCap, String... waitlistCodes) {
        List<BookingSlot> bookings = new ArrayList<>();
        for (int i = 1; i <= capacity; i++) {
            bookings.add(new BookingSlot(i, i, BookingStatus.BOOKED, null, T0.plusMinutes(i)));
        }
        List<WaitlistSlot> waitlist = new ArrayList<>();
        for (int p = 1; p <= waitlistCodes.length; p++) {
            long id = 100 + p;
            bookings.add(new BookingSlot(id, (int) id, BookingStatus.WAITLISTED, waitlistCodes[p - 1], T0.plusHours(1).plusMinutes(p)));
            waitlist.add(new WaitlistSlot(id, p));
        }
        return new SessionCapacity(7, 3, status, capacity, accCap, bookings, waitlist);
    }

    private static SessionCapacity fullWithWaitlist(int capacity, String... waitlistCodes) {
        return fullWithWaitlist(SessionStatus.OPEN, capacity, 0, waitlistCodes);
    }

    private static void offerAll(SessionCapacity s, WaitlistOfferPolicy.Plan plan) {
        long id = 900 + s.pendingOffers().size();
        for (WaitlistOfferPolicy.Offer o : plan.offers()) {
            s.addOffer(new PendingOffer(++id, o.bookingId(), o.seatKind(), o.offeredAt(), o.expiresAt()));
        }
    }

    private static List<Long> offeredBookings(WaitlistOfferPolicy.Plan plan) {
        return plan.offers().stream().map(WaitlistOfferPolicy.Offer::bookingId).toList();
    }

    @Test
    void ac1_cancelOnFullSession_offersPositionOne_andHoldsTheSeat() {
        SessionCapacity s = fullWithWaitlist(3, null, null, null);
        s.cancel(2);
        assertThat(s.seatsAvailable()).isEqualTo(1);

        WaitlistOfferPolicy.Plan plan = POLICY.plan(s, T0);
        assertThat(offeredBookings(plan)).containsExactly(101L);
        WaitlistOfferPolicy.Offer offer = plan.offers().get(0);
        assertThat(offer.position()).isEqualTo(1);
        assertThat(offer.candidateId()).isEqualTo(101);
        assertThat(offer.seatKind()).isEqualTo(SeatKind.STANDARD);
        assertThat(offer.offeredAt()).isEqualTo(T0);
        assertThat(offer.expiresAt()).isEqualTo(T0.plusHours(24));
        offerAll(s, plan);

        assertThat(s.seatsAvailable()).isZero();
        assertThat(s.seatsOffered()).isEqualTo(1);
        assertThat(s.reserve(555, null).status()).isEqualTo(BookingStatus.WAITLISTED);
        assertThat(POLICY.plan(s, T0).offers()).as("no double offer for a held seat").isEmpty();
    }

    @Test
    void walksTheWaitlistInPositionOrder_notInsertionOrder() {
        List<BookingSlot> bookings = List.of(
                new BookingSlot(1, 1, BookingStatus.BOOKED, null, T0),
                new BookingSlot(50, 50, BookingStatus.WAITLISTED, null, T0.plusMinutes(1)),
                new BookingSlot(60, 60, BookingStatus.WAITLISTED, null, T0.plusMinutes(2)));
        SessionCapacity s = new SessionCapacity(7, 3, SessionStatus.OPEN, 2, 0, bookings,
                List.of(new WaitlistSlot(50, 9), new WaitlistSlot(60, 4)));
        assertThat(offeredBookings(POLICY.plan(s, T0))).containsExactly(60L);
    }

    @Test
    void ac4_et25WithoutFreeAccommodationSeat_isSkippedAndKeepsPosition_nextStandardIsOffered() {
        SessionCapacity s = fullWithWaitlist(SessionStatus.OPEN, 3, 0, "ET25", null, "ET50");
        s.cancel(1);
        WaitlistOfferPolicy.Plan plan = POLICY.plan(s, T0);
        assertThat(offeredBookings(plan)).containsExactly(102L);
        assertThat(plan.skipped()).containsExactly(new WaitlistOfferPolicy.Skip(101, 1, "ET25"));
        offerAll(s, plan);

        assertThat(s.waitlist()).extracting(WaitlistSlot::position).containsExactly(1, 2, 3);
        List<WaitlistOfferState> states = POLICY.describe(s);
        assertThat(states).extracting(WaitlistOfferState::state).containsExactly(
                WaitlistOfferState.State.SKIPPED, WaitlistOfferState.State.OFFERED, WaitlistOfferState.State.WAITING);
        assertThat(states.get(0).skipReason()).isEqualTo("Needs ET25 accommodation seat");
        assertThat(states.get(0).offerExpiresAt()).isNull();
        assertThat(states.get(1).offerExpiresAt()).isEqualTo(T0.plusHours(24));
        assertThat(states.get(1).skipReason()).isNull();
        assertThat(states.get(2).accommodationCode()).isEqualTo("ET50");
    }

    @Test
    void et25WithFreeAccommodationSeat_getsAnAccommodationOffer_thatHoldsTheAccommodationSeat() {
        SessionCapacity s = fullWithWaitlist(SessionStatus.OPEN, 3, 1, "ET25", "ET50", null);
        s.cancel(3);
        WaitlistOfferPolicy.Plan plan = POLICY.plan(s, T0);
        assertThat(plan.offers()).singleElement().satisfies(o -> {
            assertThat(o.bookingId()).isEqualTo(101L);
            assertThat(o.seatKind()).isEqualTo(SeatKind.ACCOMMODATION);
        });
        offerAll(s, plan);
        assertThat(s.accommodationSeatsOffered()).isEqualTo(1);
        assertThat(s.accommodationSeatsAvailable()).isZero();
        assertThatThrownBy(() -> s.reserve(777, "ET50"))
                .isInstanceOfSatisfying(SchedulingRuleException.class,
                        e -> assertThat(e.violation()).isEqualTo(RuleViolation.NO_ACCOMMODATION_SEAT));
    }

    @Test
    void ac5_twoSeatsFree_twoOffersInPositionOrder() {
        SessionCapacity s = fullWithWaitlist(4, null, null, null);
        s.cancel(1);
        s.cancel(4);
        WaitlistOfferPolicy.Plan plan = POLICY.plan(s, T0);
        assertThat(plan.offers()).extracting(WaitlistOfferPolicy.Offer::bookingId, WaitlistOfferPolicy.Offer::position)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(101L, 1), org.assertj.core.groups.Tuple.tuple(102L, 2));
        offerAll(s, plan);
        assertThat(s.seatsAvailable()).isZero();
        assertThat(POLICY.plan(s, T0).offers()).isEmpty();
    }

    @Test
    void ac3_noEligibleCandidateLeft_seatStaysAvailable() {
        SessionCapacity s = fullWithWaitlist(SessionStatus.OPEN, 2, 0, "ET25");
        s.cancel(1);
        WaitlistOfferPolicy.Plan plan = POLICY.plan(s, T0);
        assertThat(plan.offers()).isEmpty();
        assertThat(plan.skipped()).hasSize(1);
        assertThat(s.seatsAvailable()).isEqualTo(1);
        assertThat(POLICY.describe(s)).extracting(WaitlistOfferState::state)
                .containsExactly(WaitlistOfferState.State.WAITING);

        SessionCapacity empty = fullWithWaitlist(2);
        empty.cancel(2);
        assertThat(POLICY.plan(empty, T0).offers()).isEmpty();
        assertThat(empty.seatsAvailable()).isEqualTo(1);
    }

    @Test
    void ac3_declineOrExpiry_movesToTheNextCandidate_andCompactsPositions() {
        SessionCapacity s = fullWithWaitlist(3, null, null, null);
        s.cancel(1);
        offerAll(s, POLICY.plan(s, T0));

        List<WaitlistSlot> shifted = s.giveUpOffer(101);
        assertThat(shifted).containsExactly(new WaitlistSlot(102, 1), new WaitlistSlot(103, 2));
        assertThat(s.bookings()).filteredOn(b -> b.bookingId() == 101).singleElement()
                .extracting(BookingSlot::status).isEqualTo(BookingStatus.CANCELLED);
        assertThat(s.seatsAvailable()).isEqualTo(1);

        WaitlistOfferPolicy.Plan next = POLICY.plan(s, T0.plusHours(24));
        assertThat(offeredBookings(next)).containsExactly(102L);
        offerAll(s, next);

        s.giveUpOffer(102);
        offerAll(s, POLICY.plan(s, T0.plusHours(48)));
        s.giveUpOffer(103);
        assertThat(POLICY.plan(s, T0.plusHours(72)).offers()).isEmpty();
        assertThat(s.waitlist()).isEmpty();
        assertThat(s.seatsAvailable()).as("nobody left: seat becomes available").isEqualTo(1);
    }

    @Test
    void closedOrCancelledSessions_neverOffer() {
        for (SessionStatus status : List.of(SessionStatus.CLOSED, SessionStatus.CANCELLED)) {
            SessionCapacity s = fullWithWaitlist(status, 2, 0, (String) null);
            s.cancel(1);
            assertThat(POLICY.plan(s, T0).offers()).as(status.name()).isEmpty();
        }
    }

    @Test
    void ttlIsConfigurable_andMustBePositive() {
        WaitlistOfferPolicy twoHours = new WaitlistOfferPolicy(Duration.ofHours(2));
        assertThat(twoHours.ttl()).isEqualTo(Duration.ofHours(2));
        assertThat(twoHours.expiresAt(T0)).isEqualTo(T0.plusHours(2));
        assertThatThrownBy(() -> new WaitlistOfferPolicy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaitlistOfferPolicy(Duration.ofMinutes(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaitlistOfferPolicy(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void seatKindFollowsTheAccommodationCode() {
        assertThat(SeatKind.forAccommodationCode("et25")).isEqualTo(SeatKind.ACCOMMODATION);
        assertThat(SeatKind.forAccommodationCode("ET50")).isEqualTo(SeatKind.ACCOMMODATION);
        assertThat(SeatKind.forAccommodationCode("SR")).isEqualTo(SeatKind.STANDARD);
        assertThat(SeatKind.forAccommodationCode(null)).isEqualTo(SeatKind.STANDARD);
    }
}
