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
    private final List<PendingOffer> offers;

    public SessionCapacity(int sessionId, int examId, SessionStatus status, int capacity, int accommodationCapacity,
                           List<BookingSlot> bookings, List<WaitlistSlot> waitlist) {
        this(sessionId, examId, status, capacity, accommodationCapacity, bookings, waitlist, List.of());
    }

    public SessionCapacity(int sessionId, int examId, SessionStatus status, int capacity, int accommodationCapacity,
                           List<BookingSlot> bookings, List<WaitlistSlot> waitlist, List<PendingOffer> pendingOffers) {
        this.sessionId = sessionId;
        this.examId = examId;
        this.status = Objects.requireNonNull(status, "status");
        this.capacity = capacity;
        this.accommodationCapacity = accommodationCapacity;
        this.bookings = new ArrayList<>(bookings);
        this.waitlist = new ArrayList<>(waitlist);
        this.offers = new ArrayList<>(pendingOffers);
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

    public List<PendingOffer> pendingOffers() {
        return List.copyOf(offers);
    }

    public Optional<PendingOffer> pendingOffer(long bookingId) {
        return offers.stream().filter(o -> o.bookingId() == bookingId).findFirst();
    }

    /** BOOKED + ATTENDED (pending offers are counted separately, see {@link #seatsOffered()}). */
    public int seatsHeld() {
        return SeatFormulas.seatsHeld(bookings);
    }

    /** Seats held by PENDING seat offers (ADR 0003). */
    public int seatsOffered() {
        return offers.size();
    }

    /** Capacity - BOOKED - ATTENDED - PENDING offers: a seat held for an offer is not bookable by anyone else. */
    public int seatsAvailable() {
        return SeatFormulas.seatsAvailable(capacity, bookings, offers.size());
    }

    public int accommodationSeatsUsed() {
        return SeatFormulas.accommodationSeatsUsed(bookings);
    }

    /** Accommodation seats held by PENDING offers to ET25/ET50 candidates. */
    public int accommodationSeatsOffered() {
        return (int) offers.stream().filter(o -> o.seatKind() == SeatKind.ACCOMMODATION).count();
    }

    public int accommodationSeatsAvailable() {
        return Math.max(0, accommodationCapacity - accommodationSeatsUsed() - accommodationSeatsOffered());
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
        if (AccommodationCodes.requiresAccommodationSeat(code) && accommodationSeatsAvailable() <= 0) {
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
     * cancellation frees its seat; a waitlisted one leaves the waitlist and gives up any pending offer
     * (check {@link #pendingOffer} first to withdraw it). Remaining positions are not compacted (legacy
     * behaviour, kept); offering the freed seat is {@link WaitlistOfferPolicy}'s job (ADR 0003).
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
        offers.removeIf(o -> o.bookingId() == bookingId);
        return b.status();
    }

    /** Registers a persisted PENDING offer (from {@link WaitlistOfferPolicy#plan}); it holds a seat from now on. */
    public void addOffer(PendingOffer offer) {
        int idx = indexOf(offer.bookingId())
                .orElseThrow(() -> new IllegalArgumentException("Booking " + offer.bookingId() + " is not on session " + sessionId));
        if (bookings.get(idx).status() != BookingStatus.WAITLISTED) {
            throw new IllegalStateException("Booking " + offer.bookingId() + " is not waitlisted");
        }
        if (pendingOffer(offer.bookingId()).isPresent()) {
            throw new IllegalStateException("Booking " + offer.bookingId() + " already holds a pending offer");
        }
        offers.add(offer);
    }

    /**
     * Accepts the booking's pending offer (ADR 0003): the booking becomes BOOKED, leaves the waitlist, and
     * every later position moves up by one. Rejected once {@code expiresAt} has passed, even if the expiry
     * job has not run yet.
     *
     * @return the waitlist slots whose position changed
     */
    public List<WaitlistSlot> acceptOffer(long bookingId, LocalDateTime now) {
        PendingOffer offer = requirePendingOffer(bookingId);
        if (offer.isExpiredAt(now)) {
            throw new SchedulingRuleException(RuleViolation.OFFER_EXPIRED,
                    "Seat offer " + offer.offerId() + " expired at " + offer.expiresAt());
        }
        int idx = indexOf(bookingId).orElseThrow();
        bookings.set(idx, bookings.get(idx).withStatus(BookingStatus.BOOKED));
        offers.remove(offer);
        return leaveWaitlist(bookingId);
    }

    /**
     * Ends the booking's pending offer by decline or expiry (ADR 0003): the waitlisted booking is cancelled,
     * leaves the waitlist and later positions move up by one, so the candidate is not offered the seat again.
     *
     * @return the waitlist slots whose position changed
     */
    public List<WaitlistSlot> giveUpOffer(long bookingId) {
        PendingOffer offer = requirePendingOffer(bookingId);
        int idx = indexOf(bookingId).orElseThrow();
        bookings.set(idx, bookings.get(idx).withStatus(BookingStatus.CANCELLED));
        offers.remove(offer);
        return leaveWaitlist(bookingId);
    }

    /** Drops a pending offer; the booking keeps its waitlist position. */
    public PendingOffer withdrawOffer(long bookingId) {
        PendingOffer offer = requirePendingOffer(bookingId);
        offers.remove(offer);
        return offer;
    }

    private PendingOffer requirePendingOffer(long bookingId) {
        return pendingOffer(bookingId).orElseThrow(() -> new SchedulingRuleException(RuleViolation.OFFER_NOT_PENDING,
                "Booking " + bookingId + " has no pending seat offer"));
    }

    /** Removes the booking's waitlist slot and moves every later position up by one. */
    private List<WaitlistSlot> leaveWaitlist(long bookingId) {
        WaitlistSlot gone = waitlist.stream().filter(w -> w.bookingId() == bookingId).findFirst()
                .orElseThrow(() -> new IllegalStateException("Booking " + bookingId + " is not on the waitlist"));
        waitlist.remove(gone);
        List<WaitlistSlot> shifted = new ArrayList<>();
        for (int i = 0; i < waitlist.size(); i++) {
            WaitlistSlot w = waitlist.get(i);
            if (w.position() > gone.position()) {
                WaitlistSlot moved = new WaitlistSlot(w.bookingId(), w.position() - 1);
                waitlist.set(i, moved);
                shifted.add(moved);
            }
        }
        shifted.sort(Comparator.comparingInt(WaitlistSlot::position));
        return shifted;
    }

    /**
     * results-service ingestion: a BOOKED booking becomes ATTENDED (still holds its seat). Recording attendance
     * again is a no-op; any other status is rejected like results-service's {@code BOOKING_NOT_ACTIVE}.
     *
     * @return true when the status changed
     */
    public boolean attend(long bookingId) {
        int idx = indexOf(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking " + bookingId + " is not on session " + sessionId));
        BookingSlot b = bookings.get(idx);
        if (b.status() == BookingStatus.ATTENDED) {
            return false;
        }
        if (b.status() != BookingStatus.BOOKED) {
            throw new SchedulingRuleException(RuleViolation.BOOKING_NOT_ACTIVE,
                    "Booking " + bookingId + " is " + b.status() + ", cannot record attendance");
        }
        bookings.set(idx, b.withStatus(BookingStatus.ATTENDED));
        return true;
    }

    /** trg_ExamSession_Update counts only BOOKED rows against a reduced capacity (ATTENDED are never bumped). */
    private int bumpableCount() {
        return (int) bookings.stream().filter(b -> b.status() == BookingStatus.BOOKED).count();
    }

    /**
     * db/procs/022_usp_ChangeCapacity.sql + db/triggers/033_trg_ExamSession_Update.sql.
     *
     * <p>Validates the new capacity against {@code floorPolicy}. On a decrease below the seats held, the
     * newest BOOKED bookings ({@code BookedAt DESC, BookingId DESC}) move to the waitlist, appended
     * after the current last position in booking order (oldest bumped booking first), exactly like the
     * trigger. ATTENDED bookings hold a seat but are never bumped. Pending seat offers the new capacity
     * has no room for are withdrawn first, newest first (their bookings keep their positions). Raising
     * capacity does not promote anyone here; {@link WaitlistOfferPolicy} offers the new seats (ADR 0003).
     */
    public CapacityChange changeCapacity(int newCapacity, AccommodationFloorPolicy floorPolicy) {
        if (newCapacity < 0) {
            throw new SchedulingRuleException(RuleViolation.VALIDATION, "newCapacity must be zero or more",
                    RuleViolation.CAPACITY_BELOW_ACCOMMODATION_FLOOR.legacyCode());
        }
        floorPolicy.check(newCapacity, accommodationCapacity, accommodationSeatsUsed(), seatsHeld());
        int old = capacity;
        capacity = newCapacity;
        List<PendingOffer> withdrawn = new ArrayList<>();
        int excessOffers = offers.size() - Math.max(0, newCapacity - seatsHeld());
        if (excessOffers > 0) {
            offers.stream()
                    .sorted(Comparator.comparing(PendingOffer::offeredAt).thenComparingLong(PendingOffer::offerId).reversed())
                    .limit(excessOffers)
                    .forEach(withdrawn::add);
            offers.removeAll(withdrawn);
        }
        int excess = bumpableCount() - newCapacity;
        if (newCapacity >= old || excess <= 0) {
            return new CapacityChange(old, newCapacity, List.of(), withdrawn);
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
        return new CapacityChange(old, newCapacity, bumped, withdrawn);
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
