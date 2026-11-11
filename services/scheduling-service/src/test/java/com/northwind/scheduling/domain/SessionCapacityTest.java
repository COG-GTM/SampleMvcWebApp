package com.northwind.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SessionCapacityTest {

    @Test
    void standardFormulaCountsBookedAndAttended() {
        // session 154 at seed: capacity 20, 9 active bookings -> 11 seats (BK-01/BK-02 sessions_after)
        SessionCapacity s = new SessionCapacity(20, 2, 9, 0, 0);
        assertThat(s.seatsAvailable()).isEqualTo(11);
        assertThat(s.seatsAvailable(SeatFormula.STANDARD)).isEqualTo(11);
        assertThat(s.isFull()).isFalse();
        assertThat(s.accommodationAvailable()).isEqualTo(2);
        assertThat(s.accommodationExhausted()).isFalse();
    }

    @Test
    void capacitySyncFormulaSubtractsAccommodationCapacityAndOnlyStandardBookings() {
        // CapacitySyncService: capacity - accCap - standard bookings (ODD-03)
        SessionCapacity s = new SessionCapacity(20, 2, 9, 1, 0);
        assertThat(s.seatsAvailable(SeatFormula.CAPACITY_SYNC)).isEqualTo(20 - 2 - 8);
        assertThat(new SessionCapacity(3, 2, 3, 0, 0).seatsAvailable(SeatFormula.CAPACITY_SYNC)).isZero();
    }

    @Test
    void neverNegative() {
        SessionCapacity s = new SessionCapacity(2, 0, 5, 0, 3);
        assertThat(s.seatsAvailable()).isZero();
        assertThat(s.isFull()).isTrue();
        assertThat(new SessionCapacity(4, 1, 2, 3, 0).accommodationAvailable()).isZero();
    }

    @Test
    void accommodationExhaustedWhenUsedReachesCapacity() {
        assertThat(new SessionCapacity(8, 1, 3, 1, 0).accommodationExhausted()).isTrue();
        assertThat(new SessionCapacity(8, 0, 3, 0, 0).accommodationExhausted()).isTrue();
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new SessionCapacity(-1, 0, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SessionCapacity(1, 0, 0, 0, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seatFormulaParse() {
        assertThat(SeatFormula.parse(null)).isEqualTo(SeatFormula.STANDARD);
        assertThat(SeatFormula.parse(" ")).isEqualTo(SeatFormula.STANDARD);
        assertThat(SeatFormula.parse("capacity-sync")).isEqualTo(SeatFormula.CAPACITY_SYNC);
        assertThat(SeatFormula.parse("standard")).isEqualTo(SeatFormula.STANDARD);
    }
}
