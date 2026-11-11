package com.northwind.assessment.scheduling.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Seat inventory of one exam session: replaces the counter logic spread over usp_BookSeat,
 * usp_CancelBooking, usp_ChangeCapacity, trg_Booking_SeatSync and trg_ExamSession_Update, and
 * booking-api's manual {@code SeatsAvailable} decrement.
 *
 * <p>Seats available are always derived from the bookings (formula A, {@link SeatFormulas}); there is
 * no denormalised counter, so the legacy partner double decrement cannot happen (ADR 0002).
 * Not thread-safe; callers load it under a session row lock.
 */
public final class SessionCapacity {

    private final int sessionId;
    private final int examId;
    private final SessionStatus status;
    private int capacity;
    private final int accommodationCapacity;
    private final List<BookingSlot> bookings;
    private final List<WaitlistSlot> waitlist;

    public SessionCapacity(int sessionId, int examId, SessionStatus status, int capacity, int accommodationCapacity,
                           List<BookingSlot> bookings, List<WaitlistSlot> waitlist) {
        this.sessionId = sessionId;
        this.examId = examId;
        this.status = Objects.requireNonNull(status, "status");
        this.capacity = capacity;
        this.accommodationCapacity = accommodationCapacity;
        this.bookings = new ArrayList<>(bookings);
        this.waitlist = new ArrayList<>(waitlist);
    }

    public int sessionId() {
        return sessionId;
    }

    public int examId() {
        return examId;
    }

    public SessionStatus status() {
        return status;
    }

    public int capacity() {
        return capacity;
    }

    public int accommodationCapacity() {
        return accommodationCapacity;
    }

    public List<BookingSlot> bookings() {
        return List.copyOf(bookings);
    }

    public List<WaitlistSlot> waitlist() {
        return waitlist.stream().sorted(Comparator.comparingInt(WaitlistSlot::position)).toList();
    }

    public int seatsHeld() {
        return SeatFormulas.seatsHeld(bookings);
    }

    public int seatsAvailable() {
        return SeatFormulas.seatsAvailable(capacity, bookings);
    }

    public int accommodationSeatsUsed() {
        return SeatFormulas.accommodationSeatsUsed(bookings);
    }

    public int accommodationSeatsAvailable() {
        return Math.max(0, accommodationCapacity - accommodationSeatsUsed());
    }

    public int waitlistCount() {
        return waitlist.size();
    }

    /** usp_BookSeat: {@code ISNULL(MAX(Position), 0) + 1}. */
    public int nextWaitlistPosition() {
        return waitlist.stream().mapToInt(WaitlistSlot::position).max().orElse(0) + 1;
    }

    /**
     * Decides a new booking (db/procs/020_usp_BookSeat.sql; booking-api {@code BookingService.book}).
     * Checks, in legacy order: session OPEN (50001), no active booking of the candidate on the session
     * (50002), an accommodation seat for ET25/ET50 (50003); then BOOKED while a seat is free, else
     * WAITLISTED at the next position. Does not change state; call {@link #record} once the id is known.
     */
    public Reservation reserve(int candidateId, String accommodationCode) {
        String code = AccommodationCodes.normalize(accommodationCode);
        if (status != SessionStatus.OPEN) {
            throw new SchedulingRuleException(RuleViolation.SESSION_NOT_OPEN,
                    "Session " + sessionId + " is not open for booking (status " + status + ")");
        }
        boolean duplicate = bookings.stream()
                .anyMatch(b -> b.candidateId() == candidateId && b.status().isActive());
        if (duplicate) {
            throw new SchedulingRuleException(RuleViolation.DUPLICATE_BOOKING,
                    "Candidate already has an active booking on session " + sessionId);
        }
        if (AccommodationCodes.requiresAccommodationSeat(code) && accommodationSeatsUsed() >= accommodationCapacity) {
            throw new SchedulingRuleException(RuleViolation.NO_ACCOMMODATION_SEAT,
                    "No accommodation seat available on session " + sessionId);
        }
        if (seatsAvailable() > 0) {
            return Reservation.booked(code);
        }
        return Reservation.waitlisted(nextWaitlistPosition(), code);
    }

    /** Applies a {@link Reservation} for the persisted booking id. */
    public BookingSlot record(long bookingId, int candidateId, Reservation reservation, LocalDateTime bookedAt) {
        BookingSlot slot = new BookingSlot(bookingId, candidateId, reservation.status(), reservation.accommodationCode(), bookedAt);
        bookings.add(slot);
        if (reservation.status() == BookingStatus.WAITLISTED) {
            waitlist.add(new WaitlistSlot(bookingId, reservation.waitlistPosition()));
        }
        return slot;
    }

    /**
     * db/procs/021_usp_CancelBooking.sql: only BOOKED / WAITLISTED can be cancelled (state 4). A booked
     * cancellation frees its seat; a waitlisted one leaves the waitlist. Nobody is promoted
     * automatically and remaining positions are not compacted (legacy behaviour, kept).
     *
     * @return the status the booking had before cancelling
     */
    public BookingStatus cancel(long bookingId) {
        int idx = indexOf(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking " + bookingId + " is not on session " + sessionId));
        BookingSlot b = bookings.get(idx);
        if (!b.status().isActive()) {
            throw new SchedulingRuleException(RuleViolation.NOT_CANCELLABLE,
                    "Booking " + bookingId + " cannot be cancelled (status " + b.status() + ")");
        }
        bookings.set(idx, b.withStatus(BookingStatus.CANCELLED));
        waitlist.removeIf(w -> w.bookingId() == bookingId);
        return b.status();
    }

    /**
     * db/procs/022_usp_ChangeCapacity.sql + db/triggers/033_trg_ExamSession_Update.sql.
     *
     * <p>Validates the new capacity against {@code floorPolicy}. On a decrease below the seats held, the
     * newest BOOKED bookings ({@code BookedAt DESC, BookingId DESC}) move to the waitlist, appended
     * after the current last position in booking order (oldest bumped booking first), exactly like the
     * trigger. ATTENDED bookings hold a seat but are never bumped. Raising capacity does not promote
     * anyone from the waitlist (legacy behaviour, kept).
     */
    public CapacityChange changeCapacity(int newCapacity, AccommodationFloorPolicy floorPolicy) {
        if (newCapacity < 0) {
            throw new SchedulingRuleException(RuleViolation.VALIDATION, "newCapacity must be zero or more",
                    RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR.legacyCode());
        }
        floorPolicy.check(newCapacity, accommodationCapacity, accommodationSeatsUsed());
        int old = capacity;
        capacity = newCapacity;
        int excess = seatsHeld() - newCapacity;
        if (newCapacity >= old || excess <= 0) {
            return new CapacityChange(old, newCapacity, List.of());
        }
        List<BookingSlot> toBump = bookings.stream()
                .filter(b -> b.status() == BookingStatus.BOOKED)
                .sorted(Comparator.comparing(BookingSlot::bookedAt).thenComparingLong(BookingSlot::bookingId).reversed())
                .limit(excess)
                .sorted(Comparator.comparing(BookingSlot::bookedAt).thenComparingLong(BookingSlot::bookingId))
                .toList();
        int next = nextWaitlistPosition();
        List<WaitlistSlot> bumped = new ArrayList<>();
        for (BookingSlot b : toBump) {
            int idx = indexOf(b.bookingId()).orElseThrow();
            bookings.set(idx, b.withStatus(BookingStatus.WAITLISTED));
            WaitlistSlot slot = new WaitlistSlot(b.bookingId(), next++);
            waitlist.add(slot);
            bumped.add(slot);
        }
        return new CapacityChange(old, newCapacity, bumped);
    }

    private Optional<Integer> indexOf(long bookingId) {
        for (int i = 0; i < bookings.size(); i++) {
            if (bookings.get(i).bookingId() == bookingId) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
