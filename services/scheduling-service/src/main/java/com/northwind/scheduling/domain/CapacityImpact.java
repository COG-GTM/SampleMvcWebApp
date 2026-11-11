package com.northwind.scheduling.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Candidates affected by a capacity change (MBA-3831, MR-04 "who notifies demoted candidates"): every booking whose
 * status changed with the change (e.g. BOOKED -> WAITLISTED demotions, ODD-02), and, when capacity drops, every
 * booking that is still BOOKED. A capacity increase without status changes affects nobody (no promotion, ODD-09).
 */
public final class CapacityImpact {

    public enum Kind {
        STATUS_CHANGED, SEAT_CONFIRMED
    }

    public record BookingState(int bookingId, String candidateNumber, BookingStatus status) {
    }

    public record Affected(int bookingId, String candidateNumber, BookingStatus previousStatus,
                           BookingStatus newStatus, Kind kind) {
    }

    private CapacityImpact() {
    }

    /** @param before / after the session's bookings before and after the change, in the same transaction */
    public static List<Affected> affected(int oldCapacity, int newCapacity, List<BookingState> before,
                                          List<BookingState> after) {
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
            } else if (newCapacity < oldCapacity && now.status() == BookingStatus.BOOKED) {
                out.add(new Affected(now.bookingId(), now.candidateNumber(), was.status(), now.status(), Kind.SEAT_CONFIRMED));
            }
        }
        return List.copyOf(out);
    }
}
