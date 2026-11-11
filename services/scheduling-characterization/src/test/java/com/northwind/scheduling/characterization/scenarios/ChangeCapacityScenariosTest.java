package com.northwind.scheduling.characterization.scenarios;

import static com.northwind.scheduling.characterization.spi.ErrorCondition.INVALID_CAPACITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.Scenario;
import com.northwind.scheduling.characterization.spi.Model.AuditEntry;
import com.northwind.scheduling.characterization.spi.Model.ExamSessionState;
import com.northwind.scheduling.characterization.spi.Model.SessionSpec;
import com.northwind.scheduling.characterization.spi.Outcome;

/** db/procs/022_usp_ChangeCapacity.sql (delta math, zero floor, validation). Demotion: CapacityDemotionScenariosTest. */
@DisplayName("022 usp_ChangeCapacity")
class ChangeCapacityScenariosTest extends CharacterizationTest {

    @Test
    @Scenario(id = "CC-01", covers = "022 raise: SeatsAvailable += delta, CAPACITY audit 'old -> new' with actor, waitlist not promoted", refs = "HB-06, MR-04")
    void raiseCapacity() {
        int s = openSession(1);
        bookOk(s, candidate());
        Outcome w = book(s, candidate());
        assertThat(target.changeCapacity(s, 3, "portal-admin").isSuccess()).isTrue();
        ExamSessionState st = sess(s);
        assertThat(st.capacity()).isEqualTo(3);
        assertThat(st.seatsAvailable()).isEqualTo(2);
        assertThat(bk(w.bookingId()).status()).isEqualTo("WAITLISTED");
        assertThat(positions(s)).containsExactly(1);
        assertThat(target.audit("ExamSession", s)).extracting(AuditEntry::action, AuditEntry::detail, AuditEntry::actor)
                .containsExactly(tuple("CAPACITY", "1 -> 3", "portal-admin"));
    }

    @Test
    @Scenario(id = "CC-02", covers = "022 lower, still >= BOOKED: SeatsAvailable += (new - old), no demotion")
    void lowerAboveBooked() {
        int s = openSession(5);
        bookOk(s, candidate());
        bookOk(s, candidate());
        assertThat(target.changeCapacity(s, 3, "characterization").isSuccess()).isTrue();
        assertThat(sess(s).capacity()).isEqualTo(3);
        assertThat(seats(s)).isEqualTo(1);
        assertThat(countStatus(s, "BOOKED")).isEqualTo(2);
        assertThat(waitlist(s)).isEmpty();
    }

    @Test
    @Scenario(id = "CC-03", covers = "022 lower to exactly BOOKED: SeatsAvailable 0, no demotion")
    void lowerToBooked() {
        int s = openSession(4);
        bookOk(s, candidate());
        bookOk(s, candidate());
        assertThat(target.changeCapacity(s, 2, null).isSuccess()).isTrue();
        assertThat(seats(s)).isZero();
        assertThat(countStatus(s, "BOOKED")).isEqualTo(2);
    }

    @Test
    @Scenario(id = "CC-04", covers = "022 zero floor: SeatsAvailable + delta < 0 without demotion (ATTENDED rows) -> 0; drift -1", refs = "HB-05, HB-03, MR-02")
    void zeroFloorWithAttended() {
        int s = openSession(4);
        int b1 = bookOk(s, candidate());
        int b2 = bookOk(s, candidate());
        target.markAttended(b1);
        target.markAttended(b2);
        assertThat(seats(s)).isEqualTo(2);
        assertThat(target.changeCapacity(s, 1, null).isSuccess()).isTrue();
        assertThat(sess(s).capacity()).isEqualTo(1);
        assertThat(seats(s)).isZero(); // 2 + (1 - 4) = -1 -> floored to 0
        assertThat(countStatus(s, "ATTENDED")).isEqualTo(2);
        expectDrift(s, -1, "HB-05/HB-03 capacity below occupied seats: demotion/recount ignore ATTENDED");
    }

    @Test
    @Scenario(id = "CC-05", covers = "022 zero floor masks partner drift: counter -1 floored to 0 equals the true value", refs = "HB-01")
    void zeroFloorMasksPartnerDrift() {
        int s = openSession(4);
        raw().partnerBook(s, candidate(), null);
        assertThat(seats(s)).isEqualTo(2);
        assertThat(target.changeCapacity(s, 1, null).isSuccess()).isTrue();
        assertThat(seats(s)).isZero();
        assertThat(countStatus(s, "BOOKED")).isEqualTo(1);
    }

    @Test
    @Scenario(id = "CC-06", covers = "022 state 5: new capacity < AccommodationCapacity -> 50005, nothing changes")
    void belowAccommodationCapacity() {
        int s = session(SessionSpec.open(exam(), 5).withAccommodation(2));
        assertError(target.changeCapacity(s, 1, null), INVALID_CAPACITY, 50005);
        assertThat(sess(s).capacity()).isEqualTo(5);
        assertThat(seats(s)).isEqualTo(5);
        assertThat(target.audit("ExamSession", s)).isEmpty();
    }

    @Test
    @Scenario(id = "CC-07", covers = "022 state 5: negative capacity -> 50005")
    void negativeCapacity() {
        int s = openSession(5);
        assertError(target.changeCapacity(s, -1, null), INVALID_CAPACITY, 50005);
        assertThat(sess(s).capacity()).isEqualTo(5);
    }

    @Test
    @Scenario(id = "CC-08", covers = "022 state 5: unknown session -> 50005")
    void unknownSession() {
        assertError(target.changeCapacity(999_999_999, 5, null), INVALID_CAPACITY, 50005);
    }

    @Test
    @Scenario(id = "CC-09", covers = "022 boundary: capacity == AccommodationCapacity is accepted")
    void equalToAccommodationCapacity() {
        int s = session(SessionSpec.open(exam(), 5).withAccommodation(2));
        assertThat(target.changeCapacity(s, 2, null).isSuccess()).isTrue();
        assertThat(sess(s).capacity()).isEqualTo(2);
        assertThat(seats(s)).isEqualTo(2);
    }

    @Test
    @Scenario(id = "CC-10", covers = "022 same capacity: no counter change, audit '5 -> 5' still written")
    void sameCapacity() {
        int s = openSession(5);
        bookOk(s, candidate());
        assertThat(target.changeCapacity(s, 5, "characterization").isSuccess()).isTrue();
        assertThat(seats(s)).isEqualTo(4);
        assertThat(target.audit("ExamSession", s)).extracting(AuditEntry::detail).containsExactly("5 -> 5");
    }

    @Test
    @Scenario(id = "CC-11", covers = "022 no status check: capacity of a CLOSED session can be changed")
    void closedSession() {
        int s = session(SessionSpec.open(exam(), 5).withStatus("CLOSED"));
        assertThat(target.changeCapacity(s, 8, null).isSuccess()).isTrue();
        assertThat(sess(s).capacity()).isEqualTo(8);
        assertThat(seats(s)).isEqualTo(8);
    }

    @Test
    @Scenario(id = "CC-12", covers = "022 no upper bound: capacity 9999 accepted (exceeds room seats)", refs = "HB-23")
    void noUpperBound() {
        int s = openSession(5);
        assertThat(target.changeCapacity(s, 9999, null).isSuccess()).isTrue();
        assertThat(seats(s)).isEqualTo(9999);
    }

    @Test
    @Scenario(id = "CC-13", covers = "022 legacy SQL contract: Msg 50000 state 5, NULL actor audited as the SQL login", refs = "HB-14, HB-17")
    void legacyErrorAndActor() {
        requireLegacySql();
        int s = session(SessionSpec.open(exam(), 5).withAccommodation(2));
        Outcome o = target.changeCapacity(s, 1, null);
        assertThat(o.error().nativeNumber()).isEqualTo(50000);
        assertThat(o.error().nativeState()).isEqualTo(5);
        assertThat(o.error().message()).isEqualTo("Invalid capacity 1 for session " + s + ".");
        assertThat(target.changeCapacity(s, 6, null).isSuccess()).isTrue();
        assertThat(target.audit("ExamSession", s)).extracting(AuditEntry::actor).containsExactly("ad_app");
    }
}
