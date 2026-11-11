package com.northwind.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.northwind.scheduling.domain.CapacityImpact.Affected;
import com.northwind.scheduling.domain.CapacityImpact.BookingState;
import com.northwind.scheduling.domain.CapacityImpact.Kind;

class CapacityImpactTest {

    static List<BookingState> booked(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> new BookingState(i, String.format("NW%08d", 900 + i),
                BookingStatus.BOOKED)).toList();
    }

    static List<BookingState> with(List<BookingState> rows, int bookingId, BookingStatus status) {
        List<BookingState> out = new ArrayList<>();
        for (BookingState r : rows) {
            out.add(r.bookingId() == bookingId ? new BookingState(r.bookingId(), r.candidateNumber(), status) : r);
        }
        return out;
    }

    @Test
    void reductionWithTwoDemotionsGivesTwoStatusChangesAndEightSeatConfirmations() {
        List<BookingState> before = booked(10);
        List<BookingState> after = with(with(before, 9, BookingStatus.WAITLISTED), 10, BookingStatus.WAITLISTED);

        List<Affected> affected = CapacityImpact.affected(20, 8, before, after);

        assertThat(affected).hasSize(10);
        assertThat(affected).filteredOn(a -> a.kind() == Kind.STATUS_CHANGED)
                .containsExactly(new Affected(9, "NW00000909", BookingStatus.BOOKED, BookingStatus.WAITLISTED, Kind.STATUS_CHANGED),
                        new Affected(10, "NW00000910", BookingStatus.BOOKED, BookingStatus.WAITLISTED, Kind.STATUS_CHANGED));
        assertThat(affected).filteredOn(a -> a.kind() == Kind.SEAT_CONFIRMED).hasSize(8)
                .allSatisfy(a -> {
                    assertThat(a.previousStatus()).isEqualTo(BookingStatus.BOOKED);
                    assertThat(a.newStatus()).isEqualTo(BookingStatus.BOOKED);
                    assertThat(a.bookingId()).isBetween(1, 8);
                });
    }

    @Test
    void reductionWithoutDemotionsConfirmsEveryRemainingBookedSeatOnly() {
        List<BookingState> before = new ArrayList<>(booked(3));
        before.add(new BookingState(4, "NW00000904", BookingStatus.WAITLISTED));
        before.add(new BookingState(5, "NW00000905", BookingStatus.CANCELLED));
        before.add(new BookingState(6, "NW00000906", BookingStatus.ATTENDED));

        List<Affected> affected = CapacityImpact.affected(20, 10, before, before);

        assertThat(affected).extracting(Affected::bookingId).containsExactly(1, 2, 3);
        assertThat(affected).extracting(Affected::kind).containsOnly(Kind.SEAT_CONFIRMED);
    }

    @Test
    void increaseWithoutStatusChangesNotifiesNobody() {
        List<BookingState> before = with(booked(5), 5, BookingStatus.WAITLISTED);
        assertThat(CapacityImpact.affected(8, 12, before, before)).isEmpty();
        assertThat(CapacityImpact.affected(8, 8, before, before)).isEmpty();
    }

    @Test
    void increaseWithAStatusChangeNotifiesThatCandidate() {
        // e.g. an A4 waitlist offer made in the same transaction: WAITLISTED -> BOOKED
        List<BookingState> before = with(booked(5), 5, BookingStatus.WAITLISTED);
        List<BookingState> after = with(before, 5, BookingStatus.BOOKED);

        assertThat(CapacityImpact.affected(4, 5, before, after)).containsExactly(
                new Affected(5, "NW00000905", BookingStatus.WAITLISTED, BookingStatus.BOOKED, Kind.STATUS_CHANGED));
    }

    @Test
    void increaseThatMakesSeatOffersNotifiesTheOfferedWaitlistedCandidatesOnly() {
        List<BookingState> state = with(with(booked(5), 4, BookingStatus.WAITLISTED), 5, BookingStatus.WAITLISTED);

        assertThat(CapacityImpact.affected(3, 5, state, state, Set.of(), Set.of(4, 5)))
                .containsExactly(new Affected(4, "NW00000904", BookingStatus.WAITLISTED, BookingStatus.WAITLISTED, Kind.SEAT_OFFERED),
                        new Affected(5, "NW00000905", BookingStatus.WAITLISTED, BookingStatus.WAITLISTED, Kind.SEAT_OFFERED));
        // an offer that was already pending is not news
        assertThat(CapacityImpact.affected(3, 4, state, state, Set.of(4), Set.of(4))).isEmpty();
    }

    @Test
    void reductionThatWithdrawsOffersNotifiesWithdrawalsDemotionsAndConfirmations() {
        List<BookingState> before = with(booked(4), 4, BookingStatus.WAITLISTED);
        List<BookingState> after = with(before, 3, BookingStatus.WAITLISTED);

        assertThat(CapacityImpact.affected(4, 2, before, after, Set.of(4), Set.of()))
                .extracting(Affected::bookingId, Affected::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1, Kind.SEAT_CONFIRMED),
                        org.assertj.core.groups.Tuple.tuple(2, Kind.SEAT_CONFIRMED),
                        org.assertj.core.groups.Tuple.tuple(3, Kind.STATUS_CHANGED),
                        org.assertj.core.groups.Tuple.tuple(4, Kind.OFFER_WITHDRAWN));
    }

    @Test
    void bookingsUnknownBeforeTheChangeAreIgnored() {
        List<BookingState> after = booked(2);
        assertThat(CapacityImpact.affected(5, 3, after.subList(0, 1), after)).extracting(Affected::bookingId).containsExactly(1);
    }
}
