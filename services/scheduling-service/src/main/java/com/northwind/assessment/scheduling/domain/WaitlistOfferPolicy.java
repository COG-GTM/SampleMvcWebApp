package com.northwind.assessment.scheduling.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Waitlist auto-offer (MBA-3829, ADR 0003): decides which waitlisted bookings get a seat offer when seats
 * are free on an OPEN session.
 *
 * <ul>
 *   <li>Free seats = capacity - BOOKED - ATTENDED - PENDING offers ({@link SessionCapacity#seatsAvailable}).</li>
 *   <li>The waitlist is walked in position order; bookings that already hold a pending offer are passed over.</li>
 *   <li>ET25/ET50 candidates need a free accommodation seat; without one they are skipped and keep their
 *       position, and the walk goes on to the next candidate.</li>
 *   <li>N free seats produce at most N offers. When nobody is eligible the seat stays bookable.</li>
 * </ul>
 * Pure decision: the caller persists the offers and registers them with {@link SessionCapacity#addOffer}.
 */
public final class WaitlistOfferPolicy {

    public static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final Duration ttl;

    public WaitlistOfferPolicy(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("offer ttl must be positive, was " + ttl);
        }
        this.ttl = ttl;
    }

    public Duration ttl() {
        return ttl;
    }

    public LocalDateTime expiresAt(LocalDateTime offeredAt) {
        return offeredAt.plus(ttl);
    }

    /** One offer to make. */
    public record Offer(long bookingId, int candidateId, int position, SeatKind seatKind, LocalDateTime offeredAt,
                        LocalDateTime expiresAt) {
    }

    /** A candidate passed over because no accommodation seat is free. */
    public record Skip(long bookingId, int position, String accommodationCode) {
    }

    public record Plan(List<Offer> offers, List<Skip> skipped) {
        public Plan {
            offers = List.copyOf(offers);
            skipped = List.copyOf(skipped);
        }
    }

    public Plan plan(SessionCapacity session, LocalDateTime now) {
        if (session.status() != SessionStatus.OPEN) {
            return new Plan(List.of(), List.of());
        }
        int free = session.seatsAvailable();
        int freeAccommodation = session.accommodationSeatsAvailable();
        Map<Long, BookingSlot> bookings = bookingsById(session);
        List<Offer> offers = new ArrayList<>();
        List<Skip> skipped = new ArrayList<>();
        for (WaitlistSlot w : session.waitlist()) {
            if (free <= 0) {
                break;
            }
            BookingSlot b = bookings.get(w.bookingId());
            if (b == null || b.status() != BookingStatus.WAITLISTED || session.pendingOffer(w.bookingId()).isPresent()) {
                continue;
            }
            SeatKind kind = SeatKind.forAccommodationCode(b.accommodationCode());
            if (kind == SeatKind.ACCOMMODATION) {
                if (freeAccommodation <= 0) {
                    skipped.add(new Skip(b.bookingId(), w.position(), b.accommodationCode()));
                    continue;
                }
                freeAccommodation--;
            }
            free--;
            offers.add(new Offer(b.bookingId(), b.candidateId(), w.position(), kind, now, expiresAt(now)));
        }
        return new Plan(offers, skipped);
    }

    /**
     * Offer state of every waitlist position, for the admin drawer. A position is SKIPPED when it needs an
     * accommodation seat, none is free, and a later position holds an offer.
     */
    public List<WaitlistOfferState> describe(SessionCapacity session) {
        Map<Long, BookingSlot> bookings = bookingsById(session);
        List<WaitlistSlot> waitlist = session.waitlist();
        boolean accommodationFree = session.accommodationSeatsAvailable() > 0;
        List<WaitlistOfferState> states = new ArrayList<>();
        for (int i = 0; i < waitlist.size(); i++) {
            WaitlistSlot w = waitlist.get(i);
            BookingSlot b = bookings.get(w.bookingId());
            String code = b == null ? null : b.accommodationCode();
            PendingOffer offer = session.pendingOffer(w.bookingId()).orElse(null);
            WaitlistOfferState.State state;
            if (offer != null) {
                state = WaitlistOfferState.State.OFFERED;
            } else if (!accommodationFree && AccommodationCodes.requiresAccommodationSeat(code)
                    && waitlist.subList(i + 1, waitlist.size()).stream()
                            .anyMatch(later -> session.pendingOffer(later.bookingId()).isPresent())) {
                state = WaitlistOfferState.State.SKIPPED;
            } else {
                state = WaitlistOfferState.State.WAITING;
            }
            states.add(new WaitlistOfferState(w.bookingId(), w.position(), state, code, offer));
        }
        return states;
    }

    private static Map<Long, BookingSlot> bookingsById(SessionCapacity session) {
        return session.bookings().stream().collect(Collectors.toMap(BookingSlot::bookingId, Function.identity()));
    }
}
