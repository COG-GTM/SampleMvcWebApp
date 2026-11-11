package com.northwind.scheduling.characterization.scenarios;

import static com.northwind.scheduling.characterization.target.ErrorCondition.DUPLICATE_ACTIVE_BOOKING;
import static com.northwind.scheduling.characterization.target.ErrorCondition.NO_ACCOMMODATION_SEAT;
import static com.northwind.scheduling.characterization.target.ErrorCondition.SESSION_NOT_OPEN;
import static com.northwind.scheduling.characterization.target.ErrorCondition.SYSTEM_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.Scenario;
import com.northwind.scheduling.characterization.target.Model.AuditEntry;
import com.northwind.scheduling.characterization.target.Model.BookingState;
import com.northwind.scheduling.characterization.target.Model.SessionSpec;
import com.northwind.scheduling.characterization.target.Outcome;

/** db/procs/020_usp_BookSeat.sql */
@DisplayName("020 usp_BookSeat")
class BookSeatScenariosTest extends CharacterizationTest {

    @Test
    @Scenario(id = "BS-01", covers = "020 SeatsAvailable > 0 -> BOOKED, counter -1, PORTAL channel, BOOK audit", refs = "HB-01")
    void bookWithFreeSeat() {
        int s = openSession(5);
        int c = candidate();
        Outcome o = book(s, c);
        assertStatus(o, "BOOKED");
        assertThat(seats(s)).isEqualTo(4);
        BookingState b = bk(o.bookingId());
        assertThat(b.sessionId()).isEqualTo(s);
        assertThat(b.candidateId()).isEqualTo(c);
        assertThat(b.status()).isEqualTo("BOOKED");
        assertThat(b.channel()).isEqualTo("PORTAL");
        assertThat(b.accommodationCode()).isNull();
        assertThat(b.externalRef()).isNull();
        assertThat(b.cancelledAt()).isNull();
        assertThat(waitlist(s)).isEmpty();
        List<AuditEntry> audit = target.audit("Booking", o.bookingId());
        assertThat(audit).extracting(AuditEntry::action, AuditEntry::detail)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("BOOK", "session=" + s + " status=BOOKED"));
    }

    @Test
    @Scenario(id = "BS-02", covers = "020 last seat -> BOOKED (counter 0); next -> WAITLISTED position 1, counter stays 0")
    void lastSeatThenWaitlist() {
        int s = openSession(1);
        Outcome first = book(s, candidate());
        assertStatus(first, "BOOKED");
        assertThat(seats(s)).isZero();
        int c2 = candidate();
        Outcome second = book(s, c2);
        assertStatus(second, "WAITLISTED");
        assertThat(seats(s)).isZero();
        assertThat(bk(second.bookingId()).status()).isEqualTo("WAITLISTED");
        assertThat(waitlist(s)).singleElement().satisfies(w -> {
            assertThat(w.bookingId()).isEqualTo(second.bookingId());
            assertThat(w.candidateId()).isEqualTo(c2);
            assertThat(w.position()).isEqualTo(1);
        });
        assertThat(target.audit("Booking", second.bookingId())).extracting(AuditEntry::detail)
                .containsExactly("session=" + s + " status=WAITLISTED");
    }

    @Test
    @Scenario(id = "BS-03", covers = "020 waitlist Position = MAX(Position)+1 per session (1,2,3; other session restarts at 1)")
    void waitlistPositionsArePerSessionSequence() {
        int s = openSession(0 + 1);
        int other = openSession(1);
        bookOk(s, candidate());
        bookOk(other, candidate());
        Outcome w1 = book(s, candidate());
        Outcome w2 = book(s, candidate());
        Outcome o1 = book(other, candidate());
        Outcome w3 = book(s, candidate());
        assertThat(List.of(w1, w2, w3, o1)).allSatisfy(o -> assertStatus(o, "WAITLISTED"));
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position())
                .containsExactly(w1.bookingId() + ":1", w2.bookingId() + ":2", w3.bookingId() + ":3");
        assertThat(positions(other)).containsExactly(1);
    }

    @Test
    @Scenario(id = "BS-04", covers = "020 Position uses MAX+1, not COUNT+1: gaps from cancelled waitlist rows are kept", refs = "HB-06, MR-12")
    void waitlistPositionAfterGap() {
        int s = openSession(1);
        bookOk(s, candidate());
        Outcome w1 = book(s, candidate());
        Outcome w2 = book(s, candidate());
        assertThat(target.cancel(w1.bookingId(), "gap", "characterization").isSuccess()).isTrue();
        Outcome w3 = book(s, candidate());
        assertStatus(w3, "WAITLISTED");
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position())
                .containsExactly(w2.bookingId() + ":2", w3.bookingId() + ":3");
    }

    @Test
    @Scenario(id = "BS-05", covers = "020 decision reads only the SeatsAvailable counter: counter 0 with free seats -> WAITLISTED", refs = "HB-02, MR-01")
    void decisionUsesCounterOnly() {
        int s = session(SessionSpec.open(exam(), 3).withSeatsAvailable(0));
        expectDrift(s, 3, "HB-02 phantom-full counter (fixture counter 0, no bookings)");
        Outcome o = book(s, candidate());
        assertStatus(o, "WAITLISTED");
        assertThat(seats(s)).isZero();
        assertThat(positions(s)).containsExactly(1);
    }

    @Test
    @Scenario(id = "BS-06", covers = "020 negative counter -> WAITLISTED, counter unchanged (no floor, no repair)", refs = "HB-02")
    void negativeCounterWaitlists() {
        int s = session(SessionSpec.open(exam(), 2).withSeatsAvailable(-2));
        expectDrift(s, 4, "HB-02 negative counter (fixture counter -2, no bookings)");
        assertStatus(book(s, candidate()), "WAITLISTED");
        assertThat(seats(s)).isEqualTo(-2);
    }

    @Test
    @Scenario(id = "BS-07", covers = "020 state 1: session does not exist -> 50001, nothing written", refs = "HB-14")
    void unknownSession() {
        int c = candidate();
        Outcome o = target.book(999_999_999, c, null);
        assertError(o, SESSION_NOT_OPEN, 50001);
    }

    @ParameterizedTest(name = "status {0}")
    @ValueSource(strings = {"CLOSED", "DELIVERED", "CANCELLED"})
    @Scenario(id = "BS-08", covers = "020 state 1: session Status <> 'OPEN' (CLOSED/DELIVERED/CANCELLED) -> 50001", refs = "HB-14, HB-31")
    void sessionNotOpen(String status) {
        int s = session(SessionSpec.open(exam(), 5).withStatus(status));
        Outcome o = book(s, candidate());
        assertError(o, SESSION_NOT_OPEN, 50001);
        assertThat(seats(s)).isEqualTo(5);
        assertThat(target.bookings(s)).isEmpty();
    }

    @Test
    @Scenario(id = "BS-09", covers = "020 state 2: candidate already BOOKED on the session -> 50002, no change", refs = "HB-21")
    void duplicateBooked() {
        int s = openSession(5);
        int c = candidate();
        bookOk(s, c);
        Outcome o = book(s, c);
        assertError(o, DUPLICATE_ACTIVE_BOOKING, 50002);
        assertThat(seats(s)).isEqualTo(4);
        assertThat(target.bookings(s)).hasSize(1);
    }

    @Test
    @Scenario(id = "BS-10", covers = "020 state 2: candidate already WAITLISTED on the session -> 50002")
    void duplicateWaitlisted() {
        int s = openSession(1);
        bookOk(s, candidate());
        int c = candidate();
        assertStatus(book(s, c), "WAITLISTED");
        assertError(book(s, c), DUPLICATE_ACTIVE_BOOKING, 50002);
        assertThat(positions(s)).containsExactly(1);
    }

    @Test
    @Scenario(id = "BS-11", covers = "020 duplicate check ignores CANCELLED: re-book after cancel -> new BOOKED row")
    void rebookAfterCancel() {
        int s = openSession(5);
        int c = candidate();
        int first = bookOk(s, c);
        assertThat(target.cancel(first, null, "characterization").isSuccess()).isTrue();
        Outcome again = book(s, c);
        assertStatus(again, "BOOKED");
        assertThat(again.bookingId()).isNotEqualTo(first);
        assertThat(seats(s)).isEqualTo(4);
    }

    @Test
    @Scenario(id = "BS-12", covers = "020 duplicate check ignores ATTENDED: ATTENDED + new BOOKED on the same session", refs = "HB-08")
    void bookAfterAttended() {
        int s = openSession(5);
        int c = candidate();
        int first = bookOk(s, c);
        target.markAttended(first);
        assertStatus(book(s, c), "BOOKED");
        assertThat(target.bookings(s)).extracting(BookingState::status).containsExactly("ATTENDED", "BOOKED");
        assertThat(seats(s)).isEqualTo(3);
    }

    @Test
    @Scenario(id = "BS-13", covers = "020 duplicate scope is the session only: same candidate on two sessions of one exam", refs = "HB-21, HB-08")
    void duplicateIsPerSessionOnly() {
        int e = exam();
        int s1 = session(SessionSpec.open(e, 5));
        int s2 = session(SessionSpec.open(e, 5));
        int c = candidate();
        assertStatus(book(s1, c), "BOOKED");
        assertStatus(book(s2, c), "BOOKED");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ET25", "ET50"})
    @Scenario(id = "BS-14", covers = "020 ET25/ET50 with accommodation seat free -> BOOKED, code stored, counter -1")
    void extraTimeWithinPool(String code) {
        int s = session(SessionSpec.open(exam(), 5).withAccommodation(1));
        Outcome o = target.book(s, candidate(), code);
        assertStatus(o, "BOOKED");
        assertThat(bk(o.bookingId()).accommodationCode()).isEqualTo(code);
        assertThat(seats(s)).isEqualTo(4);
    }

    @Test
    @Scenario(id = "BS-15", covers = "020 state 3: ET pool used up by a BOOKED ET25 -> ET50 gets 50003 although seats are free")
    void extraTimePoolExhaustedByBooked() {
        int s = session(SessionSpec.open(exam(), 5).withAccommodation(1));
        assertStatus(target.book(s, candidate(), "ET25"), "BOOKED");
        Outcome o = target.book(s, candidate(), "ET50");
        assertError(o, NO_ACCOMMODATION_SEAT, 50003);
        assertThat(seats(s)).isEqualTo(4);
        assertThat(target.bookings(s)).hasSize(1);
    }

    @Test
    @Scenario(id = "BS-16", covers = "020 state 3: ATTENDED ET bookings also consume the pool")
    void extraTimePoolCountsAttended() {
        int s = session(SessionSpec.open(exam(), 5).withAccommodation(1));
        Outcome et = target.book(s, candidate(), "ET25");
        target.markAttended(et.bookingId());
        assertError(target.book(s, candidate(), "ET25"), NO_ACCOMMODATION_SEAT, 50003);
    }

    @Test
    @Scenario(id = "BS-17", covers = "020 state 3: AccommodationCapacity 0 -> any ET booking 50003 even with free seats", refs = "HB-08")
    void extraTimeWithoutPool() {
        int s = openSession(5);
        assertError(target.book(s, candidate(), "ET25"), NO_ACCOMMODATION_SEAT, 50003);
    }

    @Test
    @Scenario(id = "BS-18", covers = "020 ET check runs before the full check: ET on a full session with no pool -> 50003, not WAITLISTED", refs = "HB-08")
    void extraTimeOnFullSessionErrorsInsteadOfWaitlist() {
        int s = session(SessionSpec.open(exam(), 1).withAccommodation(1));
        assertStatus(target.book(s, candidate(), "ET25"), "BOOKED");
        assertStatus(book(s, candidate()), "WAITLISTED");
        assertError(target.book(s, candidate(), "ET50"), NO_ACCOMMODATION_SEAT, 50003);
    }

    @Test
    @Scenario(id = "BS-19", covers = "020 WAITLISTED ET bookings do not consume the pool: ET on a full session with pool left -> WAITLISTED")
    void waitlistedExtraTimeDoesNotConsumePool() {
        int s = session(SessionSpec.open(exam(), 1).withAccommodation(1));
        bookOk(s, candidate());
        assertStatus(target.book(s, candidate(), "ET25"), "WAITLISTED");
        assertStatus(target.book(s, candidate(), "ET50"), "WAITLISTED");
        assertThat(positions(s)).containsExactly(1, 2);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SR", "RB", "XYZ"})
    @Scenario(id = "BS-20", covers = "020 non-ET codes (SR, RB, unknown) skip the pool check and are stored as given", refs = "HB-08")
    void nonExtraTimeCodes(String code) {
        int s = openSession(3);
        Outcome o = target.book(s, candidate(), code);
        assertStatus(o, "BOOKED");
        assertThat(bk(o.bookingId()).accommodationCode()).isEqualTo(code);
    }

    @Test
    @Scenario(id = "BS-21", covers = "020 CATCH re-raise: unknown candidate (FK 547) -> system error surfaced as 50000, nothing written", refs = "HB-14, MR-13")
    void unknownCandidate() {
        int s = openSession(3);
        Outcome o = target.book(s, 999_999_999, null);
        assertError(o, SYSTEM_ERROR, 50000);
        assertThat(seats(s)).isEqualTo(3);
        assertThat(target.bookings(s)).isEmpty();
    }

    @Test
    @Scenario(id = "BS-22", covers = "020 legacy SQL contract: errors are Msg 50000 with RAISERROR state 1/2/3 (documented 50001-50003 never raised)", refs = "HB-14")
    void legacyErrorNumbersAreStates() {
        requireLegacySql();
        int s = session(SessionSpec.open(exam(), 1).withAccommodation(0));
        Outcome notOpen = book(session(SessionSpec.open(exam(), 1).withStatus("CLOSED")), candidate());
        int c = candidate();
        bookOk(s, c);
        Outcome dup = book(s, c);
        Outcome acc = target.book(s, candidate(), "ET25");
        Outcome fk = target.book(s, 999_999_999, null);
        assertThat(List.of(notOpen, dup, acc)).extracting(o -> o.error().nativeNumber() + "/" + o.error().nativeState())
                .containsExactly("50000/1", "50000/2", "50000/3");
        assertThat(notOpen.error().message()).matches("Session \\d+ is not open for booking\\.");
        assertThat(dup.error().message()).isEqualTo("Candidate " + c + " already has an active booking on session " + s + ".");
        assertThat(acc.error().message()).isEqualTo("No accommodation seat available on session " + s + ".");
        assertThat(fk.error().nativeNumber()).isEqualTo(50000);
        assertThat(fk.error().message()).contains("FK_Booking_Candidate");
    }
}
