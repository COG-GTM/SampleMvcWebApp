package com.northwind.scheduling.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Candidates affected by a capacity change (MBA-3831, MR-04 "who notifies demoted candidates"): every booking whose
 * status changed with the change (e.g. BOOKED -> WAITLISTED demotions, ODD-02), every waitlisted booking that got a
 * seat offer or lost its pending offer in the change (A4 auto-offer, ADR 0005; the booking stays WAITLISTED), and,
 * when capacity drops, every booking that is still BOOKED. A capacity increase that changes no status and makes no
 * offer affects nobody.
 */
public final class CapacityImpact {

    public enum Kind {
        STATUS_CHANGED, SEAT_OFFERED, OFFER_WITHDRAWN, SEAT_CONFIRMED
    }

    public record BookingState(int bookingId, String candidateNumber, BookingStatus status) {
    }

    public record Affected(int bookingId, String candidateNumber, BookingStatus previousStatus,
                           BookingStatus newStatus, Kind kind) {
    }

    private CapacityImpact() {
    }

    public static List<Affected> affected(int oldCapacity, int newCapacity, List<BookingState> before,
                                          List<BookingState> after) {
        return affected(oldCapacity, newCapacity, before, after, Set.of(), Set.of());
    }

    /**
     * @param before / after the session's bookings before and after the change, in the same transaction
     * @param offeredBefore / offeredAfter booking ids with a PENDING seat offer before and after the change
     */
    public static List<Affected> affected(int oldCapacity, int newCapacity, List<BookingState> before,
                                          List<BookingState> after, Set<Integer> offeredBefore,
                                          Set<Integer> offeredAfter) {
        Map<Integer, BookingState> prev = before.stream()
                .collect(Collectors.toMap(BookingState::bookingId, Function.identity(), (a, b) -> a));
        List<Affected> out = new ArrayList<>();
        for (BookingState now : after.stream().sorted(Comparator.comparingInt(BookingState::bookingId)).toList()) {
            BookingState was = prev.get(now.bookingId());
            if (was == null) {
                continue;
            }
            if (was.status() != now.status()) {
                out.add(new Affected(now.bookingId(), now.candidateNumber(), was.status(), now.status(), Kind.STATUS_CHANGED));
            } else if (offeredAfter.contains(now.bookingId()) && !offeredBefore.contains(now.bookingId())) {
                out.add(new Affected(now.bookingId(), now.candidateNumber(), was.status(), now.status(), Kind.SEAT_OFFERED));
            } else if (offeredBefore.contains(now.bookingId()) && !offeredAfter.contains(now.bookingId())) {
                out.add(new Affected(now.bookingId(), now.candidateNumber(), was.status(), now.status(), Kind.OFFER_WITHDRAWN));
            } else if (newCapacity < oldCapacity && now.status() == BookingStatus.BOOKED) {
                out.add(new Affected(now.bookingId(), now.candidateNumber(), was.status(), now.status(), Kind.SEAT_CONFIRMED));
            }
        }
        return List.copyOf(out);
    }
}
