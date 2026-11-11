package com.northwind.scheduling.characterization.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.extension.ExtendWith;

import com.northwind.scheduling.characterization.target.Capability;
import com.northwind.scheduling.characterization.target.ErrorCondition;
import com.northwind.scheduling.characterization.target.Model.BookingState;
import com.northwind.scheduling.characterization.target.Model.ExamSessionState;
import com.northwind.scheduling.characterization.target.Model.SessionSpec;
import com.northwind.scheduling.characterization.target.Model.WaitlistEntry;
import com.northwind.scheduling.characterization.target.Outcome;
import com.northwind.scheduling.characterization.target.RawStorageAccess;
import com.northwind.scheduling.characterization.target.SchedulingTarget;
import com.northwind.scheduling.characterization.target.SchedulingTargets;

/** Base class: target selection, fixtures, assertion helpers and the after-each seat-drift invariant. */
@ExtendWith(SeatDriftInvariantExtension.class)
public abstract class CharacterizationTest {

    protected final SchedulingTarget target = SchedulingTargets.current();

    // ---- fixtures -------------------------------------------------------------------------------

    protected int exam() {
        return target.fixtures().newExam();
    }

    protected int candidate() {
        return target.fixtures().newCandidate();
    }

    protected int[] candidates(int n) {
        int[] c = new int[n];
        for (int i = 0; i < n; i++) {
            c[i] = candidate();
        }
        return c;
    }

    protected int session(SessionSpec spec) {
        return target.fixtures().newSession(spec);
    }

    /** OPEN session of a new exam with the given capacity and no accommodation seats. */
    protected int openSession(int capacity) {
        return session(SessionSpec.open(exam(), capacity));
    }

    protected RawStorageAccess raw() {
        assumeTrue(target.supports(Capability.RAW_STORAGE), "target does not support RAW_STORAGE");
        return target.rawStorage().orElseThrow();
    }

    protected void requireLegacySql() {
        assumeTrue(target.supports(Capability.LEGACY_SQL_SEMANTICS), "target does not support LEGACY_SQL_SEMANTICS");
    }

    /** Declares characterized legacy drift for a session (checked exactly by the after-each invariant). */
    protected void expectDrift(int sessionId, int drift, String reason) {
        DriftLedger.expect(sessionId, drift, reason);
    }

    // ---- shortcuts ------------------------------------------------------------------------------

    protected Outcome book(int sessionId, int candidateId) {
        return target.book(sessionId, candidateId, null);
    }

    protected int bookOk(int sessionId, int candidateId) {
        Outcome o = book(sessionId, candidateId);
        assertThat(o.isSuccess()).as("book %s", o).isTrue();
        return o.bookingId();
    }

    protected ExamSessionState sess(int sessionId) {
        return target.session(sessionId);
    }

    protected int seats(int sessionId) {
        return sess(sessionId).seatsAvailable();
    }

    protected BookingState bk(int bookingId) {
        return target.booking(bookingId);
    }

    protected List<WaitlistEntry> waitlist(int sessionId) {
        return target.waitlist(sessionId);
    }

    protected long countStatus(int sessionId, String status) {
        return target.bookings(sessionId).stream().filter(b -> status.equals(b.status())).count();
    }

    protected List<Integer> positions(int sessionId) {
        return waitlist(sessionId).stream().map(WaitlistEntry::position).toList();
    }

    protected List<Integer> waitlistBookingIds(int sessionId) {
        return waitlist(sessionId).stream().map(WaitlistEntry::bookingId).toList();
    }

    // ---- assertions -----------------------------------------------------------------------------

    protected static void assertStatus(Outcome o, String status) {
        assertThat(o.isSuccess()).as("expected %s but got %s", status, o).isTrue();
        assertThat(o.status()).isEqualTo(status);
        assertThat(o.bookingId()).isNotNull().isPositive();
    }

    protected static void assertError(Outcome o, ErrorCondition condition, int legacyCode) {
        assertThat(o.isSuccess()).as("expected error %s but got %s", condition, o).isFalse();
        assertThat(o.condition()).as("condition of %s", o).isEqualTo(condition);
        assertThat(o.errorCode()).as("legacy code of %s", o).isEqualTo(legacyCode);
        assertThat(condition.legacyCode()).isEqualTo(legacyCode);
    }
}
