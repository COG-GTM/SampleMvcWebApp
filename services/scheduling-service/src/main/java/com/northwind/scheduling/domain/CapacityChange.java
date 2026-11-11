package com.northwind.scheduling.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Capacity change, ported from db/procs/022_usp_ChangeCapacity.sql and the demotion half of
 * db/triggers/033_trg_ExamSession_Update.sql:
 * <ul>
 * <li>negative capacity, or capacity below the accommodation floor, is rejected (state 5, ODD-06);</li>
 * <li>when the new capacity is below the BOOKED count, the newest BOOKED bookings
 * (BookedAt DESC, BookingId DESC) are demoted to WAITLISTED and appended to the waitlist in
 * BookedAt, BookingId order after the current last position (ODD-02 kept, ADR 0002);</li>
 * <li>raising capacity never promotes waitlisted candidates (legacy has no promotion, ODD-09).</li>
 * </ul>
 */
public final class CapacityChange {

    /** A BOOKED booking that could be demoted. */
    public record BookedSeat(int bookingId, LocalDateTime bookedAt) {
    }

    /** Demoted booking and the waitlist position it receives. */
    public record Demotion(int bookingId, int position) {
    }

    public record Plan(int sessionId, int oldCapacity, int newCapacity, int oldAccommodationCapacity,
                       int newAccommodationCapacity, List<Demotion> demotions) {
    }

    private CapacityChange() {
    }

    public static Plan plan(int sessionId, int newCapacity, SessionCapacity current, List<BookedSeat> booked,
                            Integer currentMaxWaitlistPosition, AccommodationFloorPolicy floor) {
        int min = floor.minimumCapacity(current);
        if (newCapacity < 0 || newCapacity < min) {
            throw new SchedulingException(ErrorCode.INVALID_CAPACITY, "Capacity " + newCapacity
                    + " is invalid for session " + sessionId + " (must be at least " + min
                    + (floor == AccommodationFloorPolicy.ALLOCATED ? ", the accommodation capacity)" : ", the accommodation seats in use)"));
        }
        List<Demotion> demotions = new ArrayList<>();
        int excess = booked.size() - newCapacity;
        if (newCapacity < current.capacity() && excess > 0) {
            Comparator<BookedSeat> oldestFirst = Comparator.comparing(BookedSeat::bookedAt)
                    .thenComparingInt(BookedSeat::bookingId);
            List<BookedSeat> newestFirst = booked.stream().sorted(oldestFirst.reversed()).limit(excess).toList();
            int pos = currentMaxWaitlistPosition == null ? 0 : currentMaxWaitlistPosition;
            for (BookedSeat b : newestFirst.stream().sorted(oldestFirst).toList()) {
                demotions.add(new Demotion(b.bookingId(), ++pos));
            }
        }
        return new Plan(sessionId, current.capacity(), newCapacity, current.accommodationCapacity(),
                floor.accommodationCapacityAfter(current, newCapacity), List.copyOf(demotions));
    }
}
