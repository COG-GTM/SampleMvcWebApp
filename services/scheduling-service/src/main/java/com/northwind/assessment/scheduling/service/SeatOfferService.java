package com.northwind.assessment.scheduling.service;

import com.northwind.assessment.scheduling.domain.PendingOffer;
import com.northwind.assessment.scheduling.domain.RuleViolation;
import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.domain.SeatOfferStatus;
import com.northwind.assessment.scheduling.domain.SessionCapacity;
import com.northwind.assessment.scheduling.domain.WaitlistOfferPolicy;
import com.northwind.assessment.scheduling.domain.WaitlistOfferState;
import com.northwind.assessment.scheduling.domain.WaitlistSlot;
import com.northwind.assessment.scheduling.outbox.OutboxEventType;
import com.northwind.assessment.scheduling.outbox.OutboxWriter;
import com.northwind.assessment.scheduling.persistence.BookingRepository;
import com.northwind.assessment.scheduling.persistence.SeatOfferRepository;
import com.northwind.assessment.scheduling.persistence.SessionRepository;
import com.northwind.assessment.scheduling.persistence.Views.OfferView;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Waitlist auto-offer use cases (MBA-3829, ADR 0003). Every decision runs under the session row lock taken by
 * {@link SessionRepository#lockForUpdate}, in the caller's transaction, so concurrent cancellations, capacity
 * changes, accepts, declines and expiries of one session serialise and a freed seat is offered exactly once.
 */
@Service
public class SeatOfferService {

    private static final Logger log = LoggerFactory.getLogger(SeatOfferService.class);
    static final String DECLINED_REASON = "Seat offer declined";
    static final String EXPIRED_REASON = "Seat offer expired";
    static final String ACTOR = "scheduling-service";

    private final SessionRepository sessions;
    private final BookingRepository bookings;
    private final SeatOfferRepository offers;
    private final WaitlistOfferPolicy policy;
    private final OutboxWriter outbox;
    private final Clock clock;

    public SeatOfferService(SessionRepository sessions, BookingRepository bookings, SeatOfferRepository offers,
                            WaitlistOfferPolicy policy, OutboxWriter outbox, Clock clock) {
        this.sessions = sessions;
        this.bookings = bookings;
        this.offers = offers;
        this.policy = policy;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** An offer made by {@link #offerFreeSeats}, to be announced with {@link #appendOffered}. */
    public record MadeOffer(long offerId, long bookingId, int position, String seatKind, LocalDateTime expiresAt) {
    }

    /**
     * Offers every free seat of the (locked) session to the next eligible waitlisted bookings and persists the
     * offers. Call {@link #appendOffered} after the triggering event so the relay applies them in order.
     */
    List<MadeOffer> offerFreeSeats(SessionCapacity capacity, LocalDateTime now) {
        WaitlistOfferPolicy.Plan plan = policy.plan(capacity, now);
        List<MadeOffer> made = new ArrayList<>();
        for (WaitlistOfferPolicy.Offer o : plan.offers()) {
            Integer entryId = bookings.waitlistEntryIdOf(o.bookingId()).orElse(null);
            long offerId = offers.insertPending(capacity.sessionId(), o.bookingId(), entryId, o.candidateId(), o.position(),
                    o.seatKind(), o.offeredAt(), o.expiresAt());
            capacity.addOffer(new PendingOffer(offerId, o.bookingId(), o.seatKind(), o.offeredAt(), o.expiresAt()));
            made.add(new MadeOffer(offerId, o.bookingId(), o.position(), o.seatKind().name(), o.expiresAt()));
        }
        if (!made.isEmpty()) {
            sessions.markLocallyModified(capacity.sessionId(), now);
        }
        if (!plan.skipped().isEmpty()) {
            log.info("session {}: skipped waitlisted bookings {} (no accommodation seat free)", capacity.sessionId(),
                    plan.skipped().stream().map(WaitlistOfferPolicy.Skip::bookingId).toList());
        }
        return made;
    }

    void appendOffered(int sessionId, List<MadeOffer> made, String trigger, LocalDateTime now) {
        for (MadeOffer m : made) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("offerId", m.offerId());
            detail.put("position", m.position());
            detail.put("seatKind", m.seatKind());
            detail.put("expiresAt", m.expiresAt().toString());
            detail.put("trigger", trigger);
            outbox.append(OutboxEventType.SeatOffered, sessionId, m.bookingId(), List.of(), List.of(m.bookingId()), ACTOR,
                    detail, now);
        }
    }

    /** Marks a pending offer WITHDRAWN (its booking was cancelled or the seat went away with a capacity cut). */
    void withdraw(PendingOffer offer, LocalDateTime now) {
        offers.decide(offer.offerId(), SeatOfferStatus.WITHDRAWN, ACTOR, now);
    }

    public OfferView find(long offerId) {
        return offers.find(offerId).orElseThrow(() -> offerNotFound(offerId));
    }

    public List<OfferView> byCandidate(int candidateId) {
        return offers.byCandidate(candidateId);
    }

    public List<OfferView> pendingForSession(int sessionId) {
        return offers.pendingForSession(sessionId);
    }

    /** Offer state of each waitlist position (OFFERED / SKIPPED / WAITING), keyed by booking id. */
    public Map<Long, WaitlistOfferState> waitlistStates(int sessionId) {
        Map<Long, WaitlistOfferState> states = new LinkedHashMap<>();
        sessions.snapshot(sessionId).ifPresent(c -> policy.describe(c).forEach(st -> states.put(st.bookingId(), st)));
        return states;
    }

    public OfferView pendingForBooking(long bookingId) {
        return offers.pendingForBooking(bookingId).orElseThrow(() -> new NotFoundException("OFFER_NOT_FOUND",
                "Booking " + bookingId + " has no pending seat offer"));
    }

    /**
     * Accept: the booking becomes BOOKED, its waitlist entry goes and later positions move up by one. Rejected
     * with OFFER_EXPIRED from {@code expires_at} on, even when the expiry job has not run yet.
     */
    @Transactional
    public OfferView accept(long offerId, String actor) {
        LocalDateTime now = now();
        Locked l = lockPending(offerId, now);
        List<WaitlistSlot> shifted = l.capacity.acceptOffer(l.offer.bookingId(), now);
        bookings.markBooked(l.offer.bookingId(), now);
        bookings.deleteWaitlistEntry(l.offer.bookingId());
        persistShift(shifted);
        offers.decide(offerId, SeatOfferStatus.ACCEPTED, actorOr(actor), now);
        sessions.markLocallyModified(l.capacity.sessionId(), now);
        outbox.append(OutboxEventType.SeatOfferAccepted, l.capacity.sessionId(), l.offer.bookingId(), List.of(),
                touched(l.offer.bookingId(), shifted), actorOr(actor), decisionDetail(offerId, shifted), now);
        return find(offerId);
    }

    /** Decline: the waitlisted booking is cancelled, later positions move up and the seat goes to the next candidate. */
    @Transactional
    public OfferView decline(long offerId, String actor) {
        LocalDateTime now = now();
        Locked l = lockPending(offerId, now);
        giveUp(l.capacity, l.offer, SeatOfferStatus.DECLINED, OutboxEventType.SeatOfferDeclined, DECLINED_REASON,
                actorOr(actor), now);
        appendOffered(l.capacity.sessionId(), offerFreeSeats(l.capacity, now), "decline", now);
        return find(offerId);
    }

    /**
     * Expiry job, one session per transaction: every PENDING offer of the session whose {@code expires_at} has
     * passed expires (like a decline) and the freed seats are offered on.
     *
     * @return the number of offers expired
     */
    @Transactional
    public int expireDue(int sessionId) {
        LocalDateTime now = now();
        SessionCapacity capacity = sessions.lockForUpdate(sessionId).orElse(null);
        if (capacity == null) {
            return 0;
        }
        List<PendingOffer> due = capacity.pendingOffers().stream().filter(o -> o.isExpiredAt(now)).toList();
        for (PendingOffer o : due) {
            giveUp(capacity, o, SeatOfferStatus.EXPIRED, OutboxEventType.SeatOfferExpired, EXPIRED_REASON, ACTOR, now);
        }
        if (!due.isEmpty()) {
            appendOffered(sessionId, offerFreeSeats(capacity, now), "expiry", now);
        }
        return due.size();
    }

    public List<Integer> sessionsWithExpiredOffers() {
        return offers.sessionsWithExpiredOffers(now());
    }

    private void giveUp(SessionCapacity capacity, PendingOffer offer, SeatOfferStatus status, OutboxEventType type,
                        String reason, String actor, LocalDateTime now) {
        List<WaitlistSlot> shifted = capacity.giveUpOffer(offer.bookingId());
        bookings.markCancelled(offer.bookingId(), reason, now);
        bookings.deleteWaitlistEntry(offer.bookingId());
        persistShift(shifted);
        offers.decide(offer.offerId(), status, actor, now);
        sessions.markLocallyModified(capacity.sessionId(), now);
        Map<String, Object> detail = decisionDetail(offer.offerId(), shifted);
        detail.put("reason", reason);
        outbox.append(type, capacity.sessionId(), offer.bookingId(), List.of(), touched(offer.bookingId(), shifted), actor,
                detail, now);
    }

    private record Locked(SessionCapacity capacity, PendingOffer offer) {
    }

    private Locked lockPending(long offerId, LocalDateTime now) {
        int sessionId = offers.sessionIdOf(offerId).orElseThrow(() -> offerNotFound(offerId));
        SessionCapacity capacity = sessions.lockForUpdate(sessionId)
                .orElseThrow(() -> SchedulingService.sessionNotFound(sessionId));
        OfferView view = find(offerId);
        PendingOffer offer = capacity.pendingOffers().stream().filter(o -> o.offerId() == offerId).findFirst()
                .orElseThrow(() -> new SchedulingRuleException(RuleViolation.OFFER_NOT_PENDING,
                        "Seat offer " + offerId + " is " + view.status()));
        if (offer.isExpiredAt(now)) {
            throw new SchedulingRuleException(RuleViolation.OFFER_EXPIRED,
                    "Seat offer " + offerId + " expired at " + offer.expiresAt());
        }
        return new Locked(capacity, offer);
    }

    private void persistShift(List<WaitlistSlot> shifted) {
        for (WaitlistSlot w : shifted) {
            bookings.updateWaitlistPosition(w.bookingId(), w.position());
        }
    }

    private static List<Long> touched(long bookingId, List<WaitlistSlot> shifted) {
        List<Long> ids = new ArrayList<>();
        ids.add(bookingId);
        shifted.forEach(w -> ids.add(w.bookingId()));
        return ids;
    }

    private static Map<String, Object> decisionDetail(long offerId, List<WaitlistSlot> shifted) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("offerId", offerId);
        detail.put("shiftedBookingIds", shifted.stream().map(WaitlistSlot::bookingId).toList());
        return detail;
    }

    private static String actorOr(String actor) {
        if (actor == null || actor.isBlank()) {
            return "candidate";
        }
        String a = actor.trim();
        return a.length() > 60 ? a.substring(0, 60) : a;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    static NotFoundException offerNotFound(long offerId) {
        return new NotFoundException("OFFER_NOT_FOUND", "Seat offer " + offerId + " not found");
    }
}
