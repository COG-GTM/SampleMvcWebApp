package com.northwind.scheduling.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.OfferStatus;
import com.northwind.scheduling.domain.SchedulingException;
import com.northwind.scheduling.domain.SessionCapacity;
import com.northwind.scheduling.domain.WaitlistOfferRules;
import com.northwind.scheduling.domain.WaitlistSlot;
import com.northwind.scheduling.outbox.OutboxEventType;
import com.northwind.scheduling.outbox.OutboxRepository;
import com.northwind.scheduling.persistence.Rows;
import com.northwind.scheduling.persistence.SchedulingRepository;

/**
 * Waitlist auto-offer (MBA-3829, ADR 0004). Callers hold the session lock (SchedulingRepository.lockSession) and
 * a transaction, so offers, the change that freed the seat and their outbox events commit together and two
 * concurrent seat releases cannot offer the same seat twice.
 */
@Component
public class WaitlistOfferPolicy {

    private final SchedulingRepository repo;
    private final OutboxRepository outbox;
    private final Clock clock;
    private final Duration ttl;
    private final String actor;

    public WaitlistOfferPolicy(SchedulingRepository repo, OutboxRepository outbox, Clock clock,
                               @Value("${scheduling.waitlist.offer-ttl:PT24H}") Duration ttl,
                               @Value("${scheduling.actor:scheduling-service}") String actor) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("scheduling.waitlist.offer-ttl must be positive");
        }
        this.repo = repo;
        this.outbox = outbox;
        this.clock = clock;
        this.ttl = ttl;
        this.actor = actor;
    }

    public Duration ttl() {
        return ttl;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private Rows.Session session(int sessionId) {
        return repo.session(sessionId).orElseThrow(
                () -> new SchedulingException(ErrorCode.SESSION_NOT_FOUND, "Session " + sessionId + " not found"));
    }

    /** Offers every free, unheld seat of the session to the next eligible waitlist entries; returns the offer ids. */
    public List<Integer> offerFreedSeats(int sessionId, String trigger) {
        Rows.Session s = session(sessionId);
        List<WaitlistSlot> waitlist = repo.unofferedWaitlist(sessionId).stream()
                .map(w -> new WaitlistSlot(w.bookingId(), w.position(), w.accommodationCode())).toList();
        List<WaitlistSlot> chosen = WaitlistOfferRules.select(s.status(), s.seats(), waitlist);
        List<Integer> offerIds = new ArrayList<>();
        LocalDateTime now = now();
        LocalDateTime expiresAt = now.plus(ttl);
        for (WaitlistSlot w : chosen) {
            int offerId = repo.insertOffer(sessionId, w.bookingId(), w.position(), now, expiresAt);
            outbox.append(OutboxEventType.SeatOffered, w.bookingId(), List.of(w.bookingId()), List.of(sessionId), actor,
                    "offer=" + offerId + " position=" + w.position() + " expires=" + expiresAt + " trigger=" + trigger);
            offerIds.add(offerId);
        }
        return offerIds;
    }

    /**
     * After a capacity reduction: withdraws the newest pending offers until the held seats fit again (seats and
     * accommodation seats). The candidates keep their waitlist place.
     */
    public List<Integer> withdrawExcess(int sessionId, String reason) {
        List<Integer> withdrawn = new ArrayList<>();
        while (true) {
            SessionCapacity c = session(sessionId).seats();
            boolean seatExcess = c.heldSeats() > 0 && c.occupiedSeats() + c.heldSeats() > c.capacity();
            boolean accommodationExcess = c.accommodationHeld() > 0
                    && c.accommodationUsed() + c.accommodationHeld() > c.accommodationCapacity();
            if (!seatExcess && !accommodationExcess) {
                return withdrawn;
            }
            Rows.Offer victim = repo.pendingOffers(sessionId).stream()
                    .filter(o -> seatExcess || new WaitlistSlot(o.bookingId(), o.position(), o.accommodationCode())
                            .needsAccommodationSeat())
                    .findFirst().orElseThrow();
            withdraw(victim, reason);
            withdrawn.add(victim.offerId());
        }
    }

    /** PENDING -> WITHDRAWN; the booking keeps its WAITLISTED state and position. */
    public void withdraw(Rows.Offer offer, String reason) {
        if (repo.resolveOffer(offer.offerId(), OfferStatus.WITHDRAWN.name(), reason, now())) {
            outbox.append(OutboxEventType.SeatOfferWithdrawn, offer.bookingId(), List.of(offer.bookingId()),
                    List.of(offer.sessionId()), actor, "offer=" + offer.offerId() + " reason=" + reason);
        }
    }
}
