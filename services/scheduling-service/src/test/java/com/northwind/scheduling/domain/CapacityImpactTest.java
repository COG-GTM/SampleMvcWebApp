package com.northwind.scheduling.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
    void bookingsUnknownBeforeTheChangeAreIgnored() {
        List<BookingState> after = booked(2);
        assertThat(CapacityImpact.affected(5, 3, after.subList(0, 1), after)).extracting(Affected::bookingId).containsExactly(1);
    }
}
