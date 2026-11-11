package com.northwind.scheduling.characterization.scenarios;

import static com.northwind.scheduling.characterization.spi.ErrorCondition.BOOKING_NOT_CANCELLABLE;
import static com.northwind.scheduling.characterization.spi.ErrorCondition.DUPLICATE_ACTIVE_BOOKING;
import static com.northwind.scheduling.characterization.spi.ErrorCondition.NO_ACCOMMODATION_SEAT;
import static com.northwind.scheduling.characterization.spi.ErrorCondition.RESCHEDULE_NOT_ALLOWED;
import static com.northwind.scheduling.characterization.spi.ErrorCondition.SESSION_NOT_OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.Scenario;
import com.northwind.scheduling.characterization.spi.Model.AuditEntry;
import com.northwind.scheduling.characterization.spi.Model.BookingState;
import com.northwind.scheduling.characterization.spi.Model.SessionSpec;
import com.northwind.scheduling.characterization.spi.Outcome;

/** db/procs/023_usp_RescheduleBooking.sql (cancel + new booking in one transaction). */
@DisplayName("023 usp_RescheduleBooking")
class RescheduleScenariosTest extends CharacterizationTest {

    @Test
    @Scenario(id = "RS-01", covers = "023 same exam, target has seats: old -> CANCELLED 'Rescheduled', new BOOKED id on target (PORTAL, accommodation copied), counters -1/+1", refs = "HB-10, MR-09")
    void moveToSessionWithSeats() {
        int e = exam();
        int from = session(SessionSpec.open(e, 3).withAccommodation(1));
        int to = session(SessionSpec.open(e, 3).withAccommodation(1));
        int c = candidate();
        Outcome orig = target.book(from, c, "ET25");
        Outcome moved = target.reschedule(orig.bookingId(), to);
        assertStatus(moved, "BOOKED");
        assertThat(moved.bookingId()).isNotEqualTo(orig.bookingId());
        BookingState old = bk(orig.bookingId());
        assertThat(old.status()).isEqualTo("CANCELLED");
        assertThat(old.cancelReason()).isEqualTo("Rescheduled");
        assertThat(old.cancelledAt()).isNotNull();
        BookingState nb = bk(moved.bookingId());
        assertThat(nb.sessionId()).isEqualTo(to);
        assertThat(nb.candidateId()).isEqualTo(c);
        assertThat(nb.channel()).isEqualTo("PORTAL");
        assertThat(nb.accommodationCode()).isEqualTo("ET25");
        assertThat(seats(from)).isEqualTo(3);
        assertThat(seats(to)).isEqualTo(2);
        assertThat(target.audit("Booking", orig.bookingId())).extracting(AuditEntry::action, AuditEntry::detail)
                .containsExactly(tuple("BOOK", "session=" + from + " status=BOOKED"), tuple("CANCEL", "Rescheduled"));
        assertThat(target.audit("Booking", moved.bookingId())).extracting(AuditEntry::detail)
                .containsExactly("session=" + to + " status=BOOKED");
    }

    @Test
    @Scenario(id = "RS-02", covers = "023 target full: confirmed seat is given up, new booking WAITLISTED (no error)", refs = "HB-10, MR-09")
    void moveIntoFullSessionLosesSeat() {
        int e = exam();
        int from = session(SessionSpec.open(e, 3));
        int to = session(SessionSpec.open(e, 1));
        bookOk(to, candidate());
        int orig = bookOk(from, candidate());
        Outcome moved = target.reschedule(orig, to);
        assertStatus(moved, "WAITLISTED");
        assertThat(bk(orig).status()).isEqualTo("CANCELLED");
        assertThat(seats(from)).isEqualTo(3);
        assertThat(seats(to)).isZero();
        assertThat(waitlist(to)).singleElement().satisfies(w -> {
            assertThat(w.bookingId()).isEqualTo(moved.bookingId());
            assertThat(w.position()).isEqualTo(1);
        });
    }

    @Test
    @Scenario(id = "RS-03", covers = "023 same session accepted: old CANCELLED, new BOOKED id, counter net unchanged", refs = "HB-10")
    void sameSession() {
        int s = openSession(2);
        int orig = bookOk(s, candidate());
        Outcome moved = target.reschedule(orig, s);
        assertStatus(moved, "BOOKED");
        assertThat(moved.bookingId()).isNotEqualTo(orig);
        assertThat(bk(orig).status()).isEqualTo("CANCELLED");
        assertThat(seats(s)).isEqualTo(1);
    }

    @Test
    @Scenario(id = "RS-04", covers = "023 state 6 (same-exam guard): target session of a different exam -> 50006, nothing changes")
    void differentExam() {
        int from = openSession(3);
        int to = openSession(3);
        int orig = bookOk(from, candidate());
        assertError(target.reschedule(orig, to), RESCHEDULE_NOT_ALLOWED, 50006);
        assertThat(bk(orig).status()).isEqualTo("BOOKED");
        assertThat(seats(from)).isEqualTo(2);
        assertThat(seats(to)).isEqualTo(3);
        assertThat(target.bookings(to)).isEmpty();
    }

    @Test
    @Scenario(id = "RS-05", covers = "023 state 6: unknown booking -> 50006 (not 50004)")
    void unknownBooking() {
        assertError(target.reschedule(999_999_999, openSession(2)), RESCHEDULE_NOT_ALLOWED, 50006);
    }

    @Test
    @Scenario(id = "RS-06", covers = "023 state 6: unknown target session -> 50006")
    void unknownTarget() {
        int s = openSession(2);
        int orig = bookOk(s, candidate());
        assertError(target.reschedule(orig, 999_999_999), RESCHEDULE_NOT_ALLOWED, 50006);
        assertThat(bk(orig).status()).isEqualTo("BOOKED");
    }

    @Test
    @Scenario(id = "RS-07", covers = "023 nested 021 state 4: ATTENDED booking -> 50004, whole transaction rolled back", refs = "HB-15")
    void attendedBookingCannotMove() {
        int e = exam();
        int from = session(SessionSpec.open(e, 3));
        int to = session(SessionSpec.open(e, 3));
        int orig = bookOk(from, candidate());
        target.markAttended(orig);
        assertError(target.reschedule(orig, to), BOOKING_NOT_CANCELLABLE, 50004);
        assertThat(bk(orig).status()).isEqualTo("ATTENDED");
        assertThat(target.bookings(to)).isEmpty();
        assertThat(seats(to)).isEqualTo(3);
    }

    @Test
    @Scenario(id = "RS-08", covers = "023 nested 021 state 4: CANCELLED booking -> 50004, no new booking", refs = "HB-15")
    void cancelledBookingCannotMove() {
        int e = exam();
        int from = session(SessionSpec.open(e, 3));
        int to = session(SessionSpec.open(e, 3));
        int orig = bookOk(from, candidate());
        target.cancel(orig, null, null);
        assertError(target.reschedule(orig, to), BOOKING_NOT_CANCELLABLE, 50004);
        assertThat(target.bookings(to)).isEmpty();
    }

    @Test
    @Scenario(id = "RS-09", covers = "023 nested 020 state 1: target not OPEN -> 50001, the cancel is rolled back (old booking still BOOKED)", refs = "HB-15")
    void targetNotOpenRollsBackCancel() {
        int e = exam();
        int from = session(SessionSpec.open(e, 3));
        int to = session(SessionSpec.open(e, 3).withStatus("CLOSED"));
        int orig = bookOk(from, candidate());
        assertError(target.reschedule(orig, to), SESSION_NOT_OPEN, 50001);
        BookingState old = bk(orig);
        assertThat(old.status()).isEqualTo("BOOKED");
        assertThat(old.cancelledAt()).isNull();
        assertThat(seats(from)).isEqualTo(2);
        assertThat(target.audit("Booking", orig)).extracting(AuditEntry::action).containsExactly("BOOK");
    }

    @Test
    @Scenario(id = "RS-10", covers = "023 nested 020 state 2: candidate already active on target -> 50002, rolled back", refs = "HB-15, HB-21")
    void duplicateOnTargetRollsBack() {
        int e = exam();
        int from = session(SessionSpec.open(e, 3));
        int to = session(SessionSpec.open(e, 3));
        int c = candidate();
        int orig = bookOk(from, c);
        bookOk(to, c);
        assertError(target.reschedule(orig, to), DUPLICATE_ACTIVE_BOOKING, 50002);
        assertThat(bk(orig).status()).isEqualTo("BOOKED");
        assertThat(seats(from)).isEqualTo(2);
        assertThat(seats(to)).isEqualTo(2);
    }

    @Test
    @Scenario(id = "RS-11", covers = "023 nested 020 state 3: ET booking to a target without accommodation seat -> 50003, rolled back", refs = "HB-15")
    void extraTimeTargetPoolRollsBack() {
        int e = exam();
        int from = session(SessionSpec.open(e, 3).withAccommodation(1));
        int to = session(SessionSpec.open(e, 3));
        Outcome orig = target.book(from, candidate(), "ET50");
        assertError(target.reschedule(orig.bookingId(), to), NO_ACCOMMODATION_SEAT, 50003);
        assertThat(bk(orig.bookingId()).status()).isEqualTo("BOOKED");
        assertThat(seats(from)).isEqualTo(2);
    }

    @Test
    @Scenario(id = "RS-12", covers = "023 WAITLISTED booking: old Waitlist row deleted (no seat released), new booking on target", refs = "HB-06")
    void moveWaitlistedBooking() {
        int e = exam();
        int from = session(SessionSpec.open(e, 1));
        int to = session(SessionSpec.open(e, 3));
        bookOk(from, candidate());
        Outcome w = book(from, candidate());
        Outcome moved = target.reschedule(w.bookingId(), to);
        assertStatus(moved, "BOOKED");
        assertThat(waitlist(from)).isEmpty();
        assertThat(seats(from)).isZero();
        assertThat(seats(to)).isEqualTo(2);
    }

    @Test
    @Scenario(id = "RS-13", covers = "023 partner booking: comes back as PORTAL with ExternalRef dropped; source keeps HB-01 drift", refs = "HB-10, HB-01, MR-09")
    void partnerBookingBecomesPortal() {
        int e = exam();
        int from = session(SessionSpec.open(e, 4));
        int to = session(SessionSpec.open(e, 4));
        int orig = raw().partnerBook(from, candidate(), null);
        assertThat(bk(orig).externalRef()).isNotNull();
        Outcome moved = target.reschedule(orig, to);
        assertStatus(moved, "BOOKED");
        BookingState nb = bk(moved.bookingId());
        assertThat(nb.channel()).isEqualTo("PORTAL");
        assertThat(nb.externalRef()).isNull();
        assertThat(seats(from)).isEqualTo(3);
        expectDrift(from, 1, "HB-01 partner double decrement; reschedule cancel gives back one seat");
    }

    @Test
    @Scenario(id = "RS-14", covers = "023 legacy SQL contract: Msg 50000 state 6 / re-raised nested state 4; cancel audit actor is the SQL login", refs = "HB-14, HB-15, HB-17")
    void legacyErrorContract() {
        requireLegacySql();
        int e = exam();
        int from = session(SessionSpec.open(e, 3));
        int to = session(SessionSpec.open(e, 3));
        int other = openSession(3);
        int orig = bookOk(from, candidate());
        Outcome wrongExam = target.reschedule(orig, other);
        assertThat(wrongExam.error().nativeNumber()).isEqualTo(50000);
        assertThat(wrongExam.error().nativeState()).isEqualTo(6);
        assertThat(wrongExam.error().message()).isEqualTo("Booking " + orig + " cannot be moved to session " + other + ".");
        int attended = bookOk(from, candidate());
        target.markAttended(attended);
        Outcome nested = target.reschedule(attended, to);
        assertThat(nested.error().nativeNumber()).isEqualTo(50000);
        assertThat(nested.error().nativeState()).isEqualTo(4);
        assertThat(nested.error().message()).isEqualTo("Booking " + attended + " cannot be cancelled.");
        assertThat(target.reschedule(orig, to).isSuccess()).isTrue();
        assertThat(target.audit("Booking", orig)).extracting(AuditEntry::action, AuditEntry::actor).last()
                .isEqualTo(tuple("CANCEL", "ad_app"));
    }
}
