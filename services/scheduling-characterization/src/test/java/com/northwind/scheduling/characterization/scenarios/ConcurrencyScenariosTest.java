package com.northwind.scheduling.characterization.scenarios;

import static com.northwind.scheduling.characterization.spi.ErrorCondition.DUPLICATE_ACTIVE_BOOKING;
import static com.northwind.scheduling.characterization.spi.ErrorCondition.NO_ACCOMMODATION_SEAT;
import static com.northwind.scheduling.characterization.spi.ErrorCondition.SYSTEM_ERROR;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.northwind.scheduling.characterization.support.CharacterizationTest;
import com.northwind.scheduling.characterization.support.Race;
import com.northwind.scheduling.characterization.support.Scenario;
import com.northwind.scheduling.characterization.spi.Capability;
import com.northwind.scheduling.characterization.spi.Model.BookingState;
import com.northwind.scheduling.characterization.spi.Model.SessionSpec;
import com.northwind.scheduling.characterization.spi.Outcome;
import com.northwind.scheduling.characterization.spi.StateDerivedSeatDriftCheck;

/**
 * Concurrency scenarios implied by {@code WITH (UPDLOCK, HOLDLOCK)} on ExamSession in 020/022 and on Booking in 021.
 * Each scenario repeats {@code -Dconcurrency.iterations} times (default 25) with {@code -Dconcurrency.threads}
 * parallel callers (default 8) released by a barrier; every iteration checks its own sessions for seat drift.
 */
@DisplayName("Concurrency (UPDLOCK/HOLDLOCK serialisation)")
class ConcurrencyScenariosTest extends CharacterizationTest {

    private static final int N = Race.THREADS;
    private static final int ITER = Race.ITERATIONS;

    private void assertNoDrift(Integer... sessionIds) {
        assertThat(new StateDerivedSeatDriftCheck(target, () -> List.of(sessionIds)).findDrift()).isEmpty();
    }

    private void assertWaitlistDenseAndConsistent(int s) {
        List<Integer> pos = positions(s);
        assertThat(pos).containsExactlyElementsOf(IntStream.rangeClosed(1, pos.size()).boxed().toList());
        Set<Integer> waitlisted = new HashSet<>();
        for (BookingState b : target.bookings(s)) {
            if ("WAITLISTED".equals(b.status())) {
                waitlisted.add(b.bookingId());
            }
        }
        assertThat(new HashSet<>(waitlistBookingIds(s))).isEqualTo(waitlisted);
    }

    private static void report(String id, String text) {
        String line = id + ": " + ITER + " iterations x " + N + " parallel callers; " + text;
        System.out.println("[concurrency] " + line);
        try {
            Path f = Path.of("target", "concurrency-summary.txt");
            Files.createDirectories(f.getParent());
            Files.writeString(f, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // report only
        }
    }

    @Test
    @Scenario(id = "CX-01", covers = "020 UPDLOCK/HOLDLOCK last-seat race: N bookers, 1 seat -> exactly 1 BOOKED, N-1 WAITLISTED with distinct positions 1..N-1, counter 0", refs = "MR-05, HB-29")
    void lastSeatRace() {
        try (Race race = new Race(N)) {
            for (int it = 0; it < ITER; it++) {
                int s = openSession(3);
                bookOk(s, candidate());
                bookOk(s, candidate());
                int[] c = candidates(N);
                List<Outcome> out = race.run(i -> book(s, c[i]));
                assertThat(out).allSatisfy(o -> assertThat(o.isSuccess()).as("%s", o).isTrue());
                assertThat(out.stream().filter(o -> "BOOKED".equals(o.status()))).hasSize(1);
                assertThat(out.stream().filter(o -> "WAITLISTED".equals(o.status()))).hasSize(N - 1);
                assertThat(seats(s)).isZero();
                assertThat(countStatus(s, "BOOKED")).isEqualTo(3);
                assertThat(positions(s)).containsExactlyElementsOf(IntStream.rangeClosed(1, N - 1).boxed().toList());
                assertWaitlistDenseAndConsistent(s);
                assertNoDrift(s);
            }
        }
        report("CX-01", "always 1 BOOKED + " + (N - 1) + " WAITLISTED, positions 1.." + (N - 1));
    }

    @Test
    @Scenario(id = "CX-02", covers = "020 waitlist race on a full session: positions 1..N, no duplicates (contrast: partner path HB-29)", refs = "HB-29, MR-05")
    void waitlistPositionRace() {
        try (Race race = new Race(N)) {
            for (int it = 0; it < ITER; it++) {
                int s = openSession(1);
                bookOk(s, candidate());
                int[] c = candidates(N);
                List<Outcome> out = race.run(i -> book(s, c[i]));
                assertThat(out).allSatisfy(o -> assertStatus(o, "WAITLISTED"));
                assertThat(positions(s)).containsExactlyElementsOf(IntStream.rangeClosed(1, N).boxed().toList());
                assertNoDrift(s);
            }
        }
        report("CX-02", "always WAITLISTED with positions 1.." + N + " (no duplicates)");
    }

    @Test
    @Scenario(id = "CX-03", covers = "020 same candidate N times in parallel: exactly 1 BOOKED, N-1 x 50002 (duplicate check under the session lock)", refs = "HB-21")
    void duplicateRace() {
        try (Race race = new Race(N)) {
            for (int it = 0; it < ITER; it++) {
                int s = openSession(5);
                int c = candidate();
                List<Outcome> out = race.run(i -> book(s, c));
                assertThat(out.stream().filter(Outcome::isSuccess)).singleElement().satisfies(o -> assertStatus(o, "BOOKED"));
                assertThat(out.stream().filter(o -> !o.isSuccess()))
                        .hasSize(N - 1)
                        .allSatisfy(o -> assertError(o, DUPLICATE_ACTIVE_BOOKING, 50002));
                assertThat(target.bookings(s)).hasSize(1);
                assertThat(seats(s)).isEqualTo(4);
                assertNoDrift(s);
            }
        }
        report("CX-03", "always 1 BOOKED + " + (N - 1) + " x 50002");
    }

    @Test
    @Scenario(id = "CX-04", covers = "020 accommodation race: pool of 1, N parallel ET25 bookers -> 1 BOOKED, N-1 x 50003")
    void accommodationRace() {
        try (Race race = new Race(N)) {
            for (int it = 0; it < ITER; it++) {
                int s = session(SessionSpec.open(exam(), N + 2).withAccommodation(1));
                int[] c = candidates(N);
                List<Outcome> out = race.run(i -> target.book(s, c[i], "ET25"));
                assertThat(out.stream().filter(Outcome::isSuccess)).singleElement().satisfies(o -> assertStatus(o, "BOOKED"));
                assertThat(out.stream().filter(o -> !o.isSuccess()))
                        .hasSize(N - 1)
                        .allSatisfy(o -> assertError(o, NO_ACCOMMODATION_SEAT, 50003));
                assertThat(seats(s)).isEqualTo(N + 1);
                assertNoDrift(s);
            }
        }
        report("CX-04", "always 1 BOOKED + " + (N - 1) + " x 50003");
    }

    @Test
    @Scenario(id = "CX-05", covers = "020 vs 022/033 booking during a capacity cut (10 -> 5, 9 booked): serialised; always 5 BOOKED, counter 0, dense waitlist", refs = "HB-05, MR-04, MR-05")
    void bookingDuringCapacityCut() {
        int bookedBeforeCut = 0;
        try (Race race = new Race(N)) {
            for (int it = 0; it < ITER; it++) {
                int s = openSession(10);
                for (int i = 0; i < 9; i++) {
                    bookOk(s, candidate());
                }
                int[] c = candidates(N - 1);
                List<Outcome> out = race.run(i -> i == 0 ? target.changeCapacity(s, 5, "characterization") : book(s, c[i - 1]));
                assertThat(out).allSatisfy(o -> assertThat(o.isSuccess()).as("%s", o).isTrue());
                long bookedOutcomes = out.stream().skip(1).filter(o -> "BOOKED".equals(o.status())).count();
                assertThat(bookedOutcomes).isLessThanOrEqualTo(1);
                bookedBeforeCut += (int) bookedOutcomes;
                assertThat(sess(s).capacity()).isEqualTo(5);
                assertThat(countStatus(s, "BOOKED")).isEqualTo(5);
                assertThat(countStatus(s, "WAITLISTED")).isEqualTo(9 + (N - 1) - 5);
                assertThat(seats(s)).isZero();
                assertWaitlistDenseAndConsistent(s);
                assertNoDrift(s);
            }
        }
        report("CX-05", "always 5 BOOKED, counter 0, dense waitlist; the free seat went to a booker before the cut in "
                + bookedBeforeCut + "/" + ITER + " iterations");
    }

    @Test
    @Scenario(id = "CX-06", covers = "021 vs 020 cancel racing bookers on a full session: freed seat goes to at most one booker, no promotion, counter consistent", refs = "HB-06")
    void cancelVersusBook() {
        try (Race race = new Race(N)) {
            for (int it = 0; it < ITER; it++) {
                int s = openSession(3);
                int b1 = bookOk(s, candidate());
                bookOk(s, candidate());
                bookOk(s, candidate());
                int[] c = candidates(N - 1);
                List<Outcome> out = race.run(i -> i == 0 ? target.cancel(b1, "race", "characterization") : book(s, c[i - 1]));
                assertThat(out).allSatisfy(o -> assertThat(o.isSuccess()).as("%s", o).isTrue());
                long bookedOutcomes = out.stream().skip(1).filter(o -> "BOOKED".equals(o.status())).count();
                assertThat(bookedOutcomes).isLessThanOrEqualTo(1);
                assertThat(countStatus(s, "BOOKED")).isEqualTo(2 + bookedOutcomes);
                assertThat(seats(s)).isEqualTo(1 - (int) bookedOutcomes);
                assertWaitlistDenseAndConsistent(s);
                assertNoDrift(s);
            }
        }
        report("CX-06", "freed seat taken by at most one booker; counter = Capacity - BOOKED every time");
    }

    @Test
    @Scenario(id = "CX-07", covers = "022 raise racing bookers on a full session: at most 2 new BOOKED, waitlisted never promoted, counter consistent", refs = "HB-06")
    void capacityRaiseVersusBook() {
        try (Race race = new Race(N)) {
            for (int it = 0; it < ITER; it++) {
                int s = openSession(2);
                bookOk(s, candidate());
                bookOk(s, candidate());
                Outcome w = book(s, candidate());
                int[] c = candidates(N - 1);
                List<Outcome> out = race.run(i -> i == 0 ? target.changeCapacity(s, 4, null) : book(s, c[i - 1]));
                assertThat(out).allSatisfy(o -> assertThat(o.isSuccess()).as("%s", o).isTrue());
                long bookedOutcomes = out.stream().skip(1).filter(o -> "BOOKED".equals(o.status())).count();
                assertThat(bookedOutcomes).isLessThanOrEqualTo(2);
                assertThat(bk(w.bookingId()).status()).isEqualTo("WAITLISTED");
                assertThat(seats(s)).isEqualTo(2 - (int) bookedOutcomes);
                assertWaitlistDenseAndConsistent(s);
                assertNoDrift(s);
            }
        }
        report("CX-07", "at most 2 new BOOKED; earlier waitlisted booking never promoted");
    }

    @Test
    @Scenario(id = "CX-08", covers = "023 crossing reschedules A->B / B->A: each is atomic; deadlock victims surface as 50000 system errors (HB-14) with nothing written", refs = "HB-14, HB-15, MR-13")
    void crossingReschedules() {
        int deadlocks = 0;
        int moved = 0;
        java.util.Map<Integer, Integer> nativeNumbers = new java.util.TreeMap<>();
        try (Race race = new Race(N)) {
            for (int it = 0; it < ITER; it++) {
                int e = exam();
                int a = session(SessionSpec.open(e, N));
                int b = session(SessionSpec.open(e, N));
                int[] orig = new int[N];
                int[] cand = new int[N];
                for (int i = 0; i < N; i++) {
                    cand[i] = candidate();
                    orig[i] = bookOk(i % 2 == 0 ? a : b, cand[i]);
                }
                List<Outcome> out = race.run(i -> target.reschedule(orig[i], i % 2 == 0 ? b : a));
                for (int i = 0; i < N; i++) {
                    Outcome o = out.get(i);
                    int to = i % 2 == 0 ? b : a;
                    int ci = cand[i];
                    List<BookingState> onTarget = new ArrayList<>(target.bookings(to).stream().filter(x -> x.candidateId() == ci).toList());
                    if (o.isSuccess()) {
                        moved++;
                        assertThat(o.status()).isEqualTo("BOOKED");
                        assertThat(bk(orig[i]).status()).isEqualTo("CANCELLED");
                        assertThat(onTarget).extracting(BookingState::bookingId).containsExactly(o.bookingId());
                    } else {
                        deadlocks++;
                        assertError(o, SYSTEM_ERROR, 50000);
                        assertThat(o.error().message()).containsIgnoringCase("deadlock");
                        if (target.supports(Capability.LEGACY_SQL_SEMANTICS)) {
                            // usually the CATCH re-raise turns 1205 into Msg 50000 state 51 (HB-14); the raw 1205 has
                            // also been observed reaching the caller
                            assertThat(o.error().nativeNumber()).isIn(50000, 1205);
                            nativeNumbers.merge(o.error().nativeNumber(), 1, Integer::sum);
                        }
                        assertThat(bk(orig[i]).status()).isEqualTo("BOOKED");
                        assertThat(onTarget).isEmpty();
                    }
                }
                assertNoDrift(a, b);
            }
        }
        report("CX-08", moved + " reschedules committed, " + deadlocks + " deadlock victims (all rolled back cleanly; native SQL error numbers " + nativeNumbers + ")");
    }
}
