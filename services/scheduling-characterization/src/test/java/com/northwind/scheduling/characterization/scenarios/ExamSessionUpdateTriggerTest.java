package com.northwind.scheduling.characterization.scenarios;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.Scenario;

/** db/triggers/033_trg_ExamSession_Update.sql reached by direct UPDATEs (any writer, not only the proc). Needs RAW_STORAGE. */
@DisplayName("033 trg_ExamSession_Update (direct writers)")
class ExamSessionUpdateTriggerTest extends CharacterizationTest {

    @Test
    @Scenario(id = "T33-01", covers = "033 every UPDATE overwrites LastModified with GETDATE() (explicit value discarded)", refs = "HB-16, MR-14")
    void lastModifiedOverwritten() {
        int s = openSession(3);
        LocalDateTime explicit = LocalDateTime.of(2020, 1, 1, 0, 0);
        raw().setLastModified(s, explicit);
        assertThat(sess(s).lastModified()).isAfter(LocalDateTime.of(2025, 1, 1, 0, 0));
    }

    @Test
    @Scenario(id = "T33-02", covers = "033 direct UPDATE Capacity below BOOKED demotes newest and recounts (no proc, no audit)", refs = "HB-05, MR-04")
    void directCapacityCutDemotes() {
        int s = openSession(3);
        int b1 = bookOk(s, candidate());
        int b2 = bookOk(s, candidate());
        int b3 = bookOk(s, candidate());
        raw().updateCapacity(s, 1);
        assertThat(bk(b1).status()).isEqualTo("BOOKED");
        assertThat(waitlist(s)).extracting(w -> w.bookingId() + ":" + w.position()).containsExactly(b2 + ":1", b3 + ":2");
        assertThat(seats(s)).isZero();
        assertThat(target.audit("ExamSession", s)).isEmpty();
    }

    @Test
    @Scenario(id = "T33-03", covers = "033 multi-row UPDATE: demotion and positions computed per session", refs = "HB-05")
    void multiSessionUpdate() {
        int s1 = openSession(3);
        int s2 = openSession(2);
        int s3 = openSession(2);
        for (int i = 0; i < 3; i++) {
            bookOk(s1, candidate());
        }
        bookOk(s2, candidate());
        bookOk(s2, candidate());
        book(s2, candidate()); // waitlisted at position 1
        bookOk(s3, candidate());
        Map<Integer, Integer> caps = new LinkedHashMap<>();
        caps.put(s1, 1);
        caps.put(s2, 1);
        caps.put(s3, 4);
        raw().updateCapacities(caps);
        assertThat(countStatus(s1, "BOOKED")).isEqualTo(1);
        assertThat(positions(s1)).containsExactly(1, 2);
        assertThat(countStatus(s2, "BOOKED")).isEqualTo(1);
        assertThat(positions(s2)).containsExactly(1, 2);
        assertThat(seats(s1)).isZero();
        assertThat(seats(s2)).isZero();
        // s3 was raised by a direct UPDATE: no demotion and no counter change -> drift (4 - 1) - 1 = +2
        assertThat(seats(s3)).isEqualTo(1);
        expectDrift(s3, 2, "MR-01 direct Capacity raise leaves SeatsAvailable untouched (only the proc applies the delta)");
    }

    @Test
    @Scenario(id = "T33-04", covers = "033 direct capacity raise: counter untouched -> drift +delta", refs = "MR-01, HB-03")
    void directRaiseLeavesCounter() {
        int s = openSession(5);
        raw().updateCapacity(s, 7);
        assertThat(seats(s)).isEqualTo(5);
        expectDrift(s, 2, "MR-01 direct Capacity raise not reflected in SeatsAvailable");
    }

    @Test
    @Scenario(id = "T33-05", covers = "033 direct cut that needs no demotion: counter untouched -> negative drift", refs = "MR-01, HB-03")
    void directCutWithoutDemotion() {
        int s = openSession(5);
        bookOk(s, candidate());
        raw().updateCapacity(s, 3);
        assertThat(seats(s)).isEqualTo(4);
        expectDrift(s, -2, "MR-01 direct Capacity cut (no demotion) not reflected in SeatsAvailable");
    }

    @Test
    @Scenario(id = "T33-06", covers = "033 recursion guard: the trigger's own ExamSession UPDATEs do not re-run demotion (single pass)", refs = "HB-05")
    void singlePass() {
        int s = openSession(4);
        for (int i = 0; i < 4; i++) {
            bookOk(s, candidate());
        }
        raw().updateCapacity(s, 2);
        assertThat(countStatus(s, "WAITLISTED")).isEqualTo(2);
        assertThat(waitlist(s)).hasSize(2);
        assertThat(seats(s)).isZero();
    }
}
