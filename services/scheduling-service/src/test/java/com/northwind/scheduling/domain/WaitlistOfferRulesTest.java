package com.northwind.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** MBA-3829 / ADR 0004: who gets a freed seat. */
class WaitlistOfferRulesTest {

    static final List<WaitlistSlot> THREE = List.of(new WaitlistSlot(13, 1, null), new WaitlistSlot(14, 2, null),
            new WaitlistSlot(15, 3, "SR"));

    @Test
    void oneFreedSeatGoesToPositionOne() {
        assertThat(WaitlistOfferRules.select(SessionStatus.OPEN, new SessionCapacity(3, 1, 2, 1, 3), THREE))
                .extracting(WaitlistSlot::bookingId).containsExactly(13);
    }

    @Test
    void twoFreedSeatsAreOfferedInPositionOrderEvenWhenTheListIsUnsorted() {
        List<WaitlistSlot> unsorted = List.of(THREE.get(2), THREE.get(1), THREE.get(0));
        assertThat(WaitlistOfferRules.select(SessionStatus.OPEN, new SessionCapacity(5, 1, 3, 0, 3), unsorted))
                .extracting(WaitlistSlot::bookingId).containsExactly(13, 14);
    }

    @Test
    void heldSeatsAreNotOfferedAgain() {
        // capacity 3, 2 occupied, 1 held by a pending offer -> nothing free
        assertThat(WaitlistOfferRules.select(SessionStatus.OPEN, new SessionCapacity(3, 1, 2, 0, 3, 1, 0), THREE)).isEmpty();
    }

    @Test
    void extraTimeEntryIsSkippedWhileNoAccommodationSeatIsFree() {
        List<WaitlistSlot> etFirst = List.of(new WaitlistSlot(20, 1, "ET25"), new WaitlistSlot(21, 2, "ET50"),
                new WaitlistSlot(22, 3, null));
        // accommodation seat used by a BOOKED ET25 candidate
        assertThat(WaitlistOfferRules.select(SessionStatus.OPEN, new SessionCapacity(3, 1, 2, 1, 3), etFirst))
                .extracting(WaitlistSlot::bookingId).containsExactly(22);
        // accommodation seat held by a pending ET offer
        assertThat(WaitlistOfferRules.select(SessionStatus.OPEN, new SessionCapacity(4, 1, 2, 0, 3, 1, 1), etFirst))
                .extracting(WaitlistSlot::bookingId).containsExactly(22);
    }

    @Test
    void onlyOneExtraTimeEntryPerFreeAccommodationSeat() {
        List<WaitlistSlot> etFirst = List.of(new WaitlistSlot(20, 1, "ET25"), new WaitlistSlot(21, 2, "ET50"),
                new WaitlistSlot(22, 3, "RB"));
        assertThat(WaitlistOfferRules.select(SessionStatus.OPEN, new SessionCapacity(5, 1, 2, 0, 3), etFirst))
                .extracting(WaitlistSlot::bookingId).containsExactly(20, 22);
    }

    @Test
    void nobodyEligibleLeavesTheSeatFree() {
        assertThat(WaitlistOfferRules.select(SessionStatus.OPEN, new SessionCapacity(3, 1, 2, 1, 1),
                List.of(new WaitlistSlot(20, 1, "ET25")))).isEmpty();
        assertThat(WaitlistOfferRules.select(SessionStatus.OPEN, new SessionCapacity(3, 1, 2, 0, 0), List.of())).isEmpty();
    }

    @Test
    void onlyOpenSessionsMakeOffers() {
        for (SessionStatus s : List.of(SessionStatus.CLOSED, SessionStatus.DELIVERED, SessionStatus.CANCELLED)) {
            assertThat(WaitlistOfferRules.select(s, new SessionCapacity(3, 1, 0, 0, 3), THREE)).isEmpty();
        }
    }

    @Test
    void heldSeatsReduceAvailabilityForNewBookings() {
        SessionCapacity held = new SessionCapacity(3, 1, 2, 0, 2, 1, 1);
        assertThat(held.seatsAvailable()).isZero();
        assertThat(held.isFull()).isTrue();
        assertThat(held.accommodationAvailable()).isZero();
        assertThat(held.hasSeatFor("ET25")).isFalse();
    }
}
