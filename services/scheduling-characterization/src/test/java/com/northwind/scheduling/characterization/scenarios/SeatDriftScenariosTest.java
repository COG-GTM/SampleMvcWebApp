package com.northwind.scheduling.characterization.scenarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.DriftLedger;
import com.northwind.scheduling.characterization.support.Scenario;
import com.northwind.scheduling.characterization.target.Capability;
import com.northwind.scheduling.characterization.target.Model.SeatDrift;
import com.northwind.scheduling.characterization.target.Model.SessionSpec;
import com.northwind.scheduling.characterization.target.StateDerivedSeatDriftCheck;

/** db/checks/seat-drift.sql as an invariant (also run after every test by SeatDriftInvariantExtension). */
@DisplayName("Seat drift (db/checks/seat-drift.sql)")
class SeatDriftScenariosTest extends CharacterizationTest {

    @Test
    @Scenario(id = "SD-01", covers = "seat-drift.sql on the freshly provisioned seed (db/apply.sh incl. 049_seed_finalise) returns no rows", refs = "HB-30")
    void seedBaselineIsClean() {
        // a pre-existing database (-Dlegacy.jdbcUrl) may carry drift; the invariant then tolerates it as baseline
        assumeTrue(target.supports(Capability.FRESH_DATABASE), "target did not provision a fresh database");
        assertThat(DriftLedger.baseline()).isEmpty();
    }

    @Test
    @Scenario(id = "SD-02", covers = "target drift check (legacy: seat-drift.sql) and the state-derived check agree row for row", refs = "MR-02")
    void sqlCheckMatchesStateDerivedCheck() {
        int clean = openSession(5);
        bookOk(clean, candidate());
        int partner = openSession(5);
        raw().partnerBook(partner, candidate(), null);
        int raised = openSession(5);
        raw().updateCapacity(raised, 8);
        expectDrift(partner, 1, "HB-01 partner double decrement");
        expectDrift(raised, 3, "MR-01 direct Capacity raise");
        Set<Integer> ids = Set.of(clean, partner, raised);
        List<SeatDrift> fromTarget = target.seatDriftCheck().findDrift().stream()
                .filter(d -> ids.contains(d.sessionId())).sorted(Comparator.comparingInt(SeatDrift::sessionId)).toList();
        List<SeatDrift> derived = new StateDerivedSeatDriftCheck(target, () -> ids).findDrift().stream()
                .sorted(Comparator.comparingInt(SeatDrift::sessionId)).toList();
        assertThat(fromTarget).isEqualTo(derived).hasSize(2);
    }

    @Test
    @Scenario(id = "SD-03", covers = "seat-drift.sql row shape: ActiveBookings = BOOKED + ATTENDED, Drift = (Capacity - Active) - SeatsAvailable (+1 per partner booking)", refs = "HB-01")
    void driftRowShape() {
        int s = openSession(10);
        int b = bookOk(s, candidate());
        target.markAttended(b);
        book(s, candidate());
        raw().partnerBook(s, candidate(), null);
        expectDrift(s, 1, "HB-01 partner double decrement");
        assertThat(target.seatDriftCheck().findDrift()).filteredOn(d -> d.sessionId() == s).singleElement()
                .isEqualTo(new SeatDrift(s, 10, 6, 3, 1));
    }

    @Test
    @Scenario(id = "SD-04", covers = "capacity-sync reconciler formula Capacity - AccommodationCapacity - standard bookings rewrites a correct counter -> drift +AccCap-ET", refs = "HB-03, MR-02, MR-17")
    void reconcilerFormulaCreatesDrift() {
        int s = session(SessionSpec.open(exam(), 10).withAccommodation(2));
        for (int i = 0; i < 3; i++) {
            bookOk(s, candidate());
        }
        target.book(s, candidate(), "ET25");
        assertThat(seats(s)).isEqualTo(6);
        assertThat(target.reconcileSeats(List.of(s))).isEqualTo(1);
        assertThat(seats(s)).isEqualTo(5);
        expectDrift(s, 1, "HB-03 capacity-sync formula (Capacity - AccommodationCapacity - non-ET bookings)");
    }

    @Test
    @Scenario(id = "SD-05", covers = "capacity-sync reconciler repairs partner drift when AccommodationCapacity = 0 (masks HB-01)", refs = "HB-01, HB-03, MR-17")
    void reconcilerMasksPartnerDrift() {
        int s = openSession(4);
        raw().partnerBook(s, candidate(), null);
        assertThat(seats(s)).isEqualTo(2);
        assertThat(target.reconcileSeats(List.of(s))).isEqualTo(1);
        assertThat(seats(s)).isEqualTo(3);
    }

    @Test
    @Scenario(id = "SD-06", covers = "capacity-sync reconciler only touches OPEN sessions: drift on a CLOSED session survives", refs = "HB-03, HB-20")
    void reconcilerSkipsNonOpen() {
        int s = openSession(4);
        raw().partnerBook(s, candidate(), null);
        raw().setSessionStatus(s, "CLOSED");
        assertThat(target.reconcileSeats(List.of(s))).isZero();
        assertThat(seats(s)).isEqualTo(2);
        expectDrift(s, 1, "HB-01 partner double decrement on a session capacity-sync skips (not OPEN)");
    }
}
