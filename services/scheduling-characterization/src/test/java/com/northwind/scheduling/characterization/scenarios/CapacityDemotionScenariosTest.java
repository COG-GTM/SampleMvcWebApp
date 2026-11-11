package com.northwind.scheduling.characterization.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.Scenario;
import com.northwind.scheduling.characterization.spi.Model.AuditEntry;
import com.northwind.scheduling.characterization.spi.Model.BookingState;
import com.northwind.scheduling.characterization.spi.Model.SessionSpec;
import com.northwind.scheduling.characterization.spi.Outcome;

/**
 * db/triggers/033_trg_ExamSession_Update.sql demotion, reached through usp_ChangeCapacity (the path the portal admin
 * uses). Phase 1 turns this into explicit service code; these scenarios are its spec.
 */
@DisplayName("033 capacity-reduction demotion via usp_ChangeCapacity")
class CapacityDemotionScenariosTest extends CharacterizationTest {

    private int[] bookAll(int s, int n) {
        int[] ids = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = bookOk(s, candidate());
        }
        return ids;
    }

    @Test
    @Scenario(id = "TD-01", covers = "033 cut below BOOKED: newest BOOKED (BookedAt DESC) -> WAITLISTED + Waitlist rows; SeatsAvailable = Capacity - BOOKED; one CAPACITY audit, none per booking", refs = "HB-05, MR-04")
    void demotesNewest() {
        int s = openSession(5);
        int[] b = bookAll(s, 5);
        assertThat(target.changeCapacity(s, 3, "portal-admin").isSuccess()).isTrue();
        assertThat(target.bookings(s)).extracting(BookingState::status)
                .containsExactly("BOOKED", "BOOKED", "BOOKED", "WAITLISTED", "WAITLISTED");
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position())
                .containsExactly(b[3] + ":1", b[4] + ":2");
        assertThat(sess(s).capacity()).isEqualTo(3);
        assertThat(seats(s)).isZero();
        assertThat(target.audit("ExamSession", s)).extracting(AuditEntry::action, AuditEntry::detail)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("CAPACITY", "5 -> 3"));
        assertThat(target.audit("Booking", b[4])).extracting(AuditEntry::action).containsExactly("BOOK");
    }

    @Test
    @Scenario(id = "TD-02", covers = "033 demoted rows are appended after the existing waitlist (MAX(Position)+n), oldest demoted first", refs = "HB-05")
    void appendsAfterExistingWaitlist() {
        int s = openSession(3);
        int[] b = bookAll(s, 3);
        Outcome w1 = book(s, candidate());
        Outcome w2 = book(s, candidate());
        assertThat(target.changeCapacity(s, 1, null).isSuccess()).isTrue();
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position())
                .containsExactly(w1.bookingId() + ":1", w2.bookingId() + ":2", b[1] + ":3", b[2] + ":4");
        assertThat(countStatus(s, "BOOKED")).isEqualTo(1);
        assertThat(bk(b[0]).status()).isEqualTo("BOOKED");
    }

    @Test
    @Scenario(id = "TD-03", covers = "033 selection orders by BookedAt DESC (not BookingId): latest BookedAt demoted, Position by BookedAt ASC", refs = "HB-05")
    void ordersByBookedAtNotId() {
        int s = openSession(4);
        int[] b = bookAll(s, 4);
        LocalDateTime base = LocalDateTime.of(2030, 6, 1, 9, 0);
        for (int i = 0; i < 4; i++) {
            raw().setBookedAt(b[i], base.plusHours(3 - i)); // b0 latest ... b3 earliest
        }
        assertThat(target.changeCapacity(s, 2, null).isSuccess()).isTrue();
        assertThat(bk(b[0]).status()).isEqualTo("WAITLISTED");
        assertThat(bk(b[1]).status()).isEqualTo("WAITLISTED");
        assertThat(bk(b[2]).status()).isEqualTo("BOOKED");
        assertThat(bk(b[3]).status()).isEqualTo("BOOKED");
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position())
                .containsExactly(b[1] + ":1", b[0] + ":2");
    }

    @Test
    @Scenario(id = "TD-04", covers = "033 BookedAt tie: BookingId DESC breaks the tie for selection, BookingId ASC for Position", refs = "HB-05")
    void tieBreakOnBookingId() {
        int s = openSession(4);
        int[] b = bookAll(s, 4);
        LocalDateTime same = LocalDateTime.of(2030, 6, 1, 9, 0);
        for (int id : b) {
            raw().setBookedAt(id, same);
        }
        assertThat(target.changeCapacity(s, 2, null).isSuccess()).isTrue();
        assertThat(target.bookings(s)).extracting(BookingState::status)
                .containsExactly("BOOKED", "BOOKED", "WAITLISTED", "WAITLISTED");
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position())
                .containsExactly(b[2] + ":1", b[3] + ":2");
    }

    @Test
    @Scenario(id = "TD-05", covers = "033 raising capacity afterwards does not restore demoted bookings; counter += delta", refs = "MR-04, HB-06")
    void raiseDoesNotRestore() {
        int s = openSession(3);
        int[] b = bookAll(s, 3);
        target.changeCapacity(s, 1, null);
        assertThat(target.changeCapacity(s, 3, null).isSuccess()).isTrue();
        assertThat(seats(s)).isEqualTo(2);
        assertThat(bk(b[1]).status()).isEqualTo("WAITLISTED");
        assertThat(bk(b[2]).status()).isEqualTo("WAITLISTED");
        assertThat(positions(s)).containsExactly(1, 2);
    }

    @Test
    @Scenario(id = "TD-06", covers = "033 decision and recount count BOOKED only: with ATTENDED rows the recount leaves drift -1", refs = "HB-03, HB-05, MR-02")
    void attendedNotCounted() {
        int s = openSession(4);
        int[] b = bookAll(s, 4);
        target.markAttended(b[0]);
        assertThat(target.changeCapacity(s, 2, null).isSuccess()).isTrue();
        // BOOKED = 3 > 2 -> demote 1 (newest); recount 2 - 2 BOOKED = 0 though 3 seats are occupied
        assertThat(target.bookings(s)).extracting(BookingState::status)
                .containsExactly("ATTENDED", "BOOKED", "BOOKED", "WAITLISTED");
        assertThat(seats(s)).isZero();
        expectDrift(s, -1, "HB-03/HB-05 trigger 033 recount Capacity - BOOKED ignores ATTENDED");
    }

    @Test
    @Scenario(id = "TD-07", covers = "033 cut to 0: every BOOKED row demoted, positions in BookedAt order")
    void cutToZero() {
        int s = openSession(3);
        int[] b = bookAll(s, 3);
        assertThat(target.changeCapacity(s, 0, null).isSuccess()).isTrue();
        assertThat(countStatus(s, "BOOKED")).isZero();
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position())
                .containsExactly(b[0] + ":1", b[1] + ":2", b[2] + ":3");
        assertThat(seats(s)).isZero();
    }

    @Test
    @Scenario(id = "TD-08", covers = "033 demoted booking keeps id/channel, CancelledAt NULL; cancelling it later deletes its Waitlist row, counter unchanged")
    void demotedBookingLifecycle() {
        int s = openSession(2);
        int[] b = bookAll(s, 2);
        target.changeCapacity(s, 1, null);
        BookingState d = bk(b[1]);
        assertThat(d.status()).isEqualTo("WAITLISTED");
        assertThat(d.channel()).isEqualTo("PORTAL");
        assertThat(d.cancelledAt()).isNull();
        assertThat(target.cancel(b[1], "no longer needed", null).isSuccess()).isTrue();
        assertThat(waitlist(s)).isEmpty();
        assertThat(seats(s)).isZero();
    }

    @Test
    @Scenario(id = "TD-09", covers = "033 demoting an ET booking frees the accommodation pool (pool counts BOOKED/ATTENDED only)")
    void demotedExtraTimeFreesPool() {
        int s = session(SessionSpec.open(exam(), 2).withAccommodation(1));
        bookOk(s, candidate());
        Outcome et = target.book(s, candidate(), "ET25");
        assertStatus(et, "BOOKED");
        target.changeCapacity(s, 1, null);
        assertThat(bk(et.bookingId()).status()).isEqualTo("WAITLISTED");
        assertStatus(target.book(s, candidate(), "ET50"), "WAITLISTED");
    }

    @Test
    @Scenario(id = "TD-10", covers = "033 cut with WAITLISTED but BOOKED <= new capacity: no demotion, waitlist untouched")
    void noDemotionWhenBookedFits() {
        int s = openSession(2);
        int b = bookOk(s, candidate());
        int b2 = bookOk(s, candidate());
        Outcome w = book(s, candidate());
        target.cancel(b2, null, null);
        assertThat(target.changeCapacity(s, 1, null).isSuccess()).isTrue();
        assertThat(bk(b).status()).isEqualTo("BOOKED");
        assertThat(waitlistBookingIds(s)).containsExactly(w.bookingId());
        assertThat(seats(s)).isZero();
    }
}
