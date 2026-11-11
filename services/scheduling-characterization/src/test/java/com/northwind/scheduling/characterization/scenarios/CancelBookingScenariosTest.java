package com.northwind.scheduling.characterization.scenarios;

import static com.northwind.scheduling.characterization.target.ErrorCondition.BOOKING_NOT_CANCELLABLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.Scenario;
import com.northwind.scheduling.characterization.target.Model.AuditEntry;
import com.northwind.scheduling.characterization.target.Model.BookingState;
import com.northwind.scheduling.characterization.target.Outcome;

/** db/procs/021_usp_CancelBooking.sql */
@DisplayName("021 usp_CancelBooking")
class CancelBookingScenariosTest extends CharacterizationTest {

    @Test
    @Scenario(id = "CB-01", covers = "021 BOOKED -> CANCELLED (CancelledAt, CancelReason), SeatsAvailable +1, CANCEL audit with reason/actor")
    void cancelBookedReleasesSeat() {
        int s = openSession(3);
        int b = bookOk(s, candidate());
        assertThat(seats(s)).isEqualTo(2);
        Outcome o = target.cancel(b, "candidate request", "portal");
        assertThat(o.isSuccess()).isTrue();
        BookingState st = bk(b);
        assertThat(st.status()).isEqualTo("CANCELLED");
        assertThat(st.cancelledAt()).isNotNull();
        assertThat(st.cancelReason()).isEqualTo("candidate request");
        assertThat(seats(s)).isEqualTo(3);
        assertThat(target.audit("Booking", b)).extracting(AuditEntry::action, AuditEntry::detail, AuditEntry::actor)
                .endsWith(tuple("CANCEL", "candidate request", "portal"));
    }

    @Test
    @Scenario(id = "CB-02", covers = "021 WAITLISTED -> CANCELLED, Waitlist row deleted, SeatsAvailable unchanged, remaining positions not renumbered", refs = "HB-06, MR-12")
    void cancelWaitlistedDeletesWaitlistRow() {
        int s = openSession(1);
        bookOk(s, candidate());
        Outcome w1 = book(s, candidate());
        Outcome w2 = book(s, candidate());
        Outcome w3 = book(s, candidate());
        assertThat(target.cancel(w2.bookingId(), null, "characterization").isSuccess()).isTrue();
        assertThat(bk(w2.bookingId()).status()).isEqualTo("CANCELLED");
        assertThat(seats(s)).isZero();
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position())
                .containsExactly(w1.bookingId() + ":1", w3.bookingId() + ":3");
    }

    @Test
    @Scenario(id = "CB-03", covers = "021 freeing a seat never promotes the waitlist: seat free while candidates stay WAITLISTED", refs = "HB-06, MR-12")
    void cancelBookedDoesNotPromote() {
        int s = openSession(1);
        int b = bookOk(s, candidate());
        Outcome w = book(s, candidate());
        assertThat(target.cancel(b, null, null).isSuccess()).isTrue();
        assertThat(seats(s)).isEqualTo(1);
        assertThat(bk(w.bookingId()).status()).isEqualTo("WAITLISTED");
        assertThat(positions(s)).containsExactly(1);
        // the next booker jumps the queue
        assertStatus(book(s, candidate()), "BOOKED");
        assertThat(bk(w.bookingId()).status()).isEqualTo("WAITLISTED");
    }

    @Test
    @Scenario(id = "CB-04", covers = "021 state 4: already CANCELLED -> 50004, no change, no audit row")
    void cancelCancelled() {
        int s = openSession(2);
        int b = bookOk(s, candidate());
        target.cancel(b, "first", "characterization");
        int auditRows = target.audit("Booking", b).size();
        assertError(target.cancel(b, "second", "characterization"), BOOKING_NOT_CANCELLABLE, 50004);
        assertThat(seats(s)).isEqualTo(2);
        assertThat(bk(b).cancelReason()).isEqualTo("first");
        assertThat(target.audit("Booking", b)).hasSize(auditRows);
    }

    @Test
    @Scenario(id = "CB-05", covers = "021 state 4: ATTENDED -> 50004")
    void cancelAttended() {
        int s = openSession(2);
        int b = bookOk(s, candidate());
        target.markAttended(b);
        assertError(target.cancel(b, null, null), BOOKING_NOT_CANCELLABLE, 50004);
        assertThat(bk(b).status()).isEqualTo("ATTENDED");
        assertThat(seats(s)).isEqualTo(1);
    }

    @Test
    @Scenario(id = "CB-06", covers = "021 state 4: unknown booking id -> 50004")
    void cancelUnknown() {
        assertError(target.cancel(999_999_999, null, null), BOOKING_NOT_CANCELLABLE, 50004);
    }

    @Test
    @Scenario(id = "CB-07", covers = "021 no session status check: cancelling on a DELIVERED session increments its counter", refs = "HB-22")
    void cancelOnDeliveredSession() {
        int s = openSession(2);
        int b = bookOk(s, candidate());
        raw().setSessionStatus(s, "DELIVERED");
        assertThat(target.cancel(b, null, null).isSuccess()).isTrue();
        assertThat(sess(s).status()).isEqualTo("DELIVERED");
        assertThat(seats(s)).isEqualTo(2);
    }

    @Test
    @Scenario(id = "CB-08", covers = "021 gives back exactly 1 seat: partner book (-2, HB-01) + cancel (+1) leaks a seat", refs = "HB-01, HB-02")
    void partnerBookCancelLeaksSeat() {
        int s = openSession(4);
        int b = raw().partnerBook(s, candidate(), null);
        assertThat(seats(s)).isEqualTo(2);
        assertThat(target.cancel(b, "partner cancel", "booking-api").isSuccess()).isTrue();
        assertThat(seats(s)).isEqualTo(3);
        expectDrift(s, 1, "HB-01/HB-02 partner double decrement, cancel gives back one");
    }

    @Test
    @Scenario(id = "CB-09", covers = "021 audit actor defaults to the SQL login (SUSER_SNAME) when @Actor is NULL", refs = "HB-17")
    void auditActorDefaultsToLogin() {
        requireLegacySql();
        int s = openSession(2);
        int b = bookOk(s, candidate());
        target.cancel(b, null, null);
        assertThat(target.audit("Booking", b)).extracting(AuditEntry::action, AuditEntry::actor)
                .containsExactly(tuple("BOOK", "ad_app"), tuple("CANCEL", "ad_app"));
    }
}
