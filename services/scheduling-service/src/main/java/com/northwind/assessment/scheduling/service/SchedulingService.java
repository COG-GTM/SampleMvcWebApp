package com.northwind.assessment.scheduling.service;

import com.northwind.assessment.scheduling.domain.AccommodationCodes;
import com.northwind.assessment.scheduling.domain.AccommodationFloorPolicy;
import com.northwind.assessment.scheduling.domain.BookingChannel;
import com.northwind.assessment.scheduling.domain.BookingStatus;
import com.northwind.assessment.scheduling.domain.CapacityChange;
import com.northwind.assessment.scheduling.domain.EligibilityDecision;
import com.northwind.assessment.scheduling.domain.PendingOffer;
import com.northwind.assessment.scheduling.domain.Reservation;
import com.northwind.assessment.scheduling.domain.ReschedulePolicy;
import com.northwind.assessment.scheduling.domain.RetakeEligibilityPolicy;
import com.northwind.assessment.scheduling.domain.RuleViolation;
import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.domain.SessionCapacity;
import com.northwind.assessment.scheduling.domain.WaitlistSlot;
import com.northwind.assessment.scheduling.outbox.OutboxEventType;
import com.northwind.assessment.scheduling.outbox.OutboxWriter;
import com.northwind.assessment.scheduling.persistence.BookingRepository;
import com.northwind.assessment.scheduling.persistence.ReferenceRepository;
import com.northwind.assessment.scheduling.persistence.SessionRepository;
import com.northwind.assessment.scheduling.persistence.Views.BookingView;
import com.northwind.assessment.scheduling.persistence.Views.CandidateView;
import com.northwind.assessment.scheduling.persistence.Views.ExamView;
import com.northwind.assessment.scheduling.persistence.Views.SessionView;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking, cancellation, reschedule and capacity use cases. Orchestrates the plain-Java domain
 * ({@link SessionCapacity}, {@link RetakeEligibilityPolicy}, {@link ReschedulePolicy}) and persists the
 * outcome; checks run in booking-api v1 order so error codes match. Every change appends its outbox event
 * ({@link OutboxWriter}) in the same transaction (MBA-3823).
 */
@Service
public class SchedulingService {

    static final String DEFAULT_CANCEL_REASON = "Cancelled";
    private static final int MAX_EXTERNAL_REF = 40;
    private static final int MAX_REASON = 200;
    private static final int MAX_ACTOR = 60;

    private final SessionRepository sessions;
    private final BookingRepository bookings;
    private final ReferenceRepository reference;
    private final RetakeEligibilityPolicy retakePolicy;
    private final AccommodationFloorPolicy floorPolicy;
    private final OutboxWriter outbox;
    private final SeatOfferService offers;
    private final Clock clock;

    public SchedulingService(SessionRepository sessions, BookingRepository bookings, ReferenceRepository reference,
                             RetakeEligibilityPolicy retakePolicy, AccommodationFloorPolicy floorPolicy,
                             OutboxWriter outbox, SeatOfferService offers, Clock clock) {
        this.outbox = outbox;
        this.offers = offers;
        this.sessions = sessions;
        this.bookings = bookings;
        this.reference = reference;
        this.retakePolicy = retakePolicy;
        this.floorPolicy = floorPolicy;
        this.clock = clock;
    }

    @Transactional
    public BookingResult book(BookingCommand cmd) {
        if (cmd == null || cmd.sessionId() == null || cmd.candidateNumber() == null || cmd.candidateNumber().isBlank()) {
            throw invalid("sessionId and candidateNumber are required");
        }
        String acc = AccommodationCodes.normalize(cmd.accommodationCode());
        if (!AccommodationCodes.isKnown(acc)) {
            throw invalid("Unknown accommodationCode " + acc);
        }
        String externalRef = blankToNull(cmd.externalRef());
        if (externalRef != null && externalRef.length() > MAX_EXTERNAL_REF) {
            throw invalid("externalRef is limited to 40 characters");
        }
        BookingChannel channel = parseChannel(cmd.channel());
        CandidateView candidate = candidate(cmd.candidateNumber().trim());
        SessionCapacity capacity = sessions.lockForUpdate(cmd.sessionId())
                .orElseThrow(() -> sessionNotFound(cmd.sessionId()));
        BookingResult result = bookLocked(capacity, candidate, acc, externalRef, channel);
        OutboxEventType type = BookingStatus.WAITLISTED.name().equals(result.status())
                ? OutboxEventType.BookingWaitlisted : OutboxEventType.BookingCreated;
        outbox.append(type, result.sessionId(), result.bookingId(), List.of(), List.of(result.bookingId()),
                channel.name().toLowerCase(Locale.ROOT), Map.of("channel", channel.name()), now());
        return result;
    }

    private BookingResult bookLocked(SessionCapacity capacity, CandidateView candidate, String acc, String externalRef,
                                     BookingChannel channel) {
        Reservation reservation = capacity.reserve(candidate.candidateId(), acc);
        SessionView session = sessions.find(capacity.sessionId()).orElseThrow();
        checkEligibility(candidate, capacity.examId(), session);

        LocalDateTime now = now();
        long bookingId = bookings.nextModernId();
        capacity.record(bookingId, candidate.candidateId(), reservation, now);
        bookings.insertModern(bookingId, capacity.sessionId(), candidate.candidateId(), reservation.status().name(),
                reservation.accommodationCode(), channel.name(), externalRef, now);
        if (reservation.status() == BookingStatus.WAITLISTED) {
            bookings.insertWaitlistEntry(capacity.sessionId(), bookingId, candidate.candidateId(),
                    reservation.waitlistPosition(), now);
        }
        sessions.markLocallyModified(capacity.sessionId(), now);
        return new BookingResult(bookingId, reservation.status().name(), capacity.sessionId(), candidate.candidateNumber(),
                reservation.accommodationCode(), channel.name(), reservation.waitlistPosition(), capacity.seatsAvailable());
    }

    /** Booking-time retake check against the date of the sitting (booking-api BookingService). */
    private void checkEligibility(CandidateView candidate, int examId, SessionView session) {
        int waitDays = reference.examById(examId).map(ExamView::retakeWaitDays).orElse(30);
        EligibilityDecision d = retakePolicy.evaluate(reference.attempts(candidate.candidateId(), examId), waitDays,
                session.startsAt().toLocalDate());
        if (!d.eligible()) {
            String msg = "Candidate " + candidate.candidateNumber() + " is not eligible to book " + session.examCode()
                    + " (" + d.reason() + ")";
            if (d.earliestRetakeDate() != null) {
                msg += ", earliest retake date " + d.earliestRetakeDate();
            }
            throw new SchedulingRuleException(RuleViolation.NOT_ELIGIBLE, msg);
        }
    }

    @Transactional
    public BookingView cancel(long requestedId, String reason) {
        long bookingId = resolve(requestedId);
        int sessionId = bookings.sessionIdOf(bookingId).orElseThrow(() -> bookingNotFound(bookingId));
        SessionCapacity capacity = sessions.lockForUpdate(sessionId).orElseThrow(() -> sessionNotFound(sessionId));
        String why = blankToNull(reason) == null ? DEFAULT_CANCEL_REASON : reason.trim();
        if (why.length() > MAX_REASON) {
            throw invalid("reason is limited to 200 characters");
        }
        LocalDateTime now = now();
        Long withdrawnOfferId = cancelLocked(capacity, bookingId, why, now);
        List<SeatOfferService.MadeOffer> made = offers.offerFreeSeats(capacity, now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", why);
        if (withdrawnOfferId != null) {
            detail.put("withdrawnOfferId", withdrawnOfferId);
        }
        outbox.append(OutboxEventType.BookingCancelled, sessionId, bookingId, List.of(), List.of(bookingId), "scheduling-service",
                detail, now);
        offers.appendOffered(sessionId, made, "cancel", now);
        return bookings.find(bookingId).orElseThrow();
    }

    /**
     * Cancels under the session lock. A waitlisted booking holding a pending seat offer gives it up (WITHDRAWN);
     * the caller then offers the freed seat on ({@link SeatOfferService#offerFreeSeats}).
     *
     * @return the id of the withdrawn offer, if any
     */
    private Long cancelLocked(SessionCapacity capacity, long bookingId, String reason, LocalDateTime now) {
        Optional<PendingOffer> pending = capacity.pendingOffer(bookingId);
        BookingStatus previous = capacity.cancel(bookingId);
        bookings.markCancelled(bookingId, reason, now);
        if (previous == BookingStatus.WAITLISTED) {
            bookings.deleteWaitlistEntry(bookingId);
        }
        pending.ifPresent(o -> offers.withdraw(o, now));
        sessions.markLocallyModified(capacity.sessionId(), now);
        return pending.map(PendingOffer::offerId).orElse(null);
    }

    /**
     * usp_RescheduleBooking / booking-api reschedule: same exam only, then cancel ("Rescheduled") + book in one
     * transaction; if the new booking is rejected the cancellation rolls back. Both session rows are locked
     * in ascending id order to avoid deadlocks.
     */
    @Transactional
    public BookingResult reschedule(long requestedId, Integer newSessionId) {
        if (newSessionId == null) {
            throw invalid("newSessionId is required");
        }
        long bookingId = resolve(requestedId);
        BookingView booking = bookings.find(bookingId).orElseThrow(() -> bookingNotFound(bookingId));
        int newExam = sessions.examIdOf(newSessionId).orElseThrow(() -> sessionNotFound(newSessionId));
        int oldExam = sessions.examIdOf(booking.sessionId()).orElseThrow();
        ReschedulePolicy.check(booking.sessionId(), oldExam, newSessionId, newExam);

        int first = Math.min(booking.sessionId(), newSessionId);
        int second = Math.max(booking.sessionId(), newSessionId);
        SessionCapacity a = sessions.lockForUpdate(first).orElseThrow();
        SessionCapacity b = sessions.lockForUpdate(second).orElseThrow();
        SessionCapacity from = a.sessionId() == booking.sessionId() ? a : b;
        SessionCapacity to = from == a ? b : a;

        LocalDateTime now = now();
        cancelLocked(from, bookingId, ReschedulePolicy.CANCEL_REASON, now);
        CandidateView candidate = reference.candidateByNumber(booking.candidateNumber())
                .orElseThrow(() -> new NotFoundException("CANDIDATE_NOT_FOUND", "Candidate of booking " + bookingId + " not found"));
        BookingChannel channel = BookingChannel.valueOf(booking.channel()) == BookingChannel.IMPORT
                ? BookingChannel.PORTAL : BookingChannel.valueOf(booking.channel());
        BookingResult result = bookLocked(to, candidate, booking.accommodationCode(), booking.externalRef(), channel);
        List<SeatOfferService.MadeOffer> made = offers.offerFreeSeats(from, now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fromBookingId", bookingId);
        detail.put("toBookingId", result.bookingId());
        detail.put("fromSessionId", from.sessionId());
        detail.put("toSessionId", to.sessionId());
        outbox.append(OutboxEventType.BookingRescheduled, to.sessionId(), result.bookingId(), List.of(from.sessionId()),
                List.of(bookingId, result.bookingId()), "scheduling-service", detail, now);
        offers.appendOffered(from.sessionId(), made, "reschedule", now);
        return result;
    }

    /**
     * results-service ingestion (MBA-3823): the booking becomes ATTENDED here first; the relay then projects it
     * into AssessmentDelivery. Idempotent: a booking that is already ATTENDED is returned unchanged, no event.
     */
    @Transactional
    public BookingView recordAttendance(long requestedId) {
        long bookingId = resolve(requestedId);
        int sessionId = bookings.sessionIdOf(bookingId).orElseThrow(() -> bookingNotFound(bookingId));
        SessionCapacity capacity = sessions.lockForUpdate(sessionId).orElseThrow(() -> sessionNotFound(sessionId));
        if (capacity.attend(bookingId)) {
            LocalDateTime now = now();
            bookings.markAttended(bookingId, now);
            sessions.markLocallyModified(sessionId, now);
            outbox.append(OutboxEventType.AttendanceRecorded, sessionId, bookingId, List.of(), List.of(bookingId),
                    "results-service", Map.of(), now);
        }
        return bookings.find(bookingId).orElseThrow();
    }

    /** Scheduling id for a Scheduling or AssessmentDelivery booking id ({@link BookingRepository#resolve}). */
    public long resolve(long requestedId) {
        return bookings.resolve(requestedId).orElseThrow(() -> bookingNotFound(requestedId));
    }

    public record CapacityResult(SessionView session, List<Long> bumpedBookingIds) {
    }

    /**
     * usp_ChangeCapacity + trg_ExamSession_Update, as explicit domain code. A cut withdraws the pending seat offers
     * it has no room for; an increase offers the new seats to the waitlist (ADR 0003).
     */
    @Transactional
    public CapacityResult changeCapacity(int sessionId, Integer newCapacity, String actor) {
        if (newCapacity == null) {
            throw invalid("newCapacity is required");
        }
        String who = blankToNull(actor) == null ? "admin" : actor.trim();
        if (who.length() > MAX_ACTOR) {
            throw invalid("actor is limited to 60 characters");
        }
        SessionCapacity capacity = sessions.lockForUpdate(sessionId).orElseThrow(() -> sessionNotFound(sessionId));
        CapacityChange change = capacity.changeCapacity(newCapacity, floorPolicy);
        LocalDateTime now = now();
        sessions.updateCapacity(sessionId, newCapacity, now);
        for (WaitlistSlot w : change.bumped()) {
            bookings.markWaitlisted(w.bookingId(), now);
            bookings.insertWaitlistEntry(sessionId, w.bookingId(), bookings.candidateIdOf(w.bookingId()), w.position(), now);
        }
        change.withdrawnOffers().forEach(o -> offers.withdraw(o, now));
        List<SeatOfferService.MadeOffer> made = offers.offerFreeSeats(capacity, now);
        sessions.insertCapacityChange(sessionId, change.oldCapacity(), newCapacity, change.bumpedBookingIds(), who, now);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("oldCapacity", change.oldCapacity());
        detail.put("newCapacity", newCapacity);
        detail.put("bumpedBookingIds", change.bumpedBookingIds());
        List<Long> touched = new ArrayList<>(change.bumpedBookingIds());
        if (!change.withdrawnOffers().isEmpty()) {
            List<Long> withdrawn = change.withdrawnOffers().stream().map(PendingOffer::bookingId).toList();
            detail.put("withdrawnOfferBookingIds", withdrawn);
            touched.addAll(withdrawn);
        }
        outbox.append(OutboxEventType.CapacityChanged, sessionId, null, List.of(), touched, who, detail, now);
        offers.appendOffered(sessionId, made, "capacity", now);
        return new CapacityResult(sessions.find(sessionId).orElseThrow(), change.bumpedBookingIds());
    }

    public EligibilityDecision eligibility(String candidateNumber, String examCode, String date) {
        if (examCode == null || examCode.isBlank()) {
            throw invalid("examCode is required");
        }
        LocalDate onDate;
        try {
            onDate = date == null || date.isBlank() ? LocalDate.now(clock) : LocalDate.parse(date.trim());
        } catch (RuntimeException e) {
            throw invalid("Invalid date '" + date + "', expected yyyy-MM-dd");
        }
        CandidateView candidate = candidate(candidateNumber);
        ExamView exam = reference.examByCode(examCode.trim())
                .orElseThrow(() -> new NotFoundException("EXAM_NOT_FOUND", "Exam " + examCode + " not found"));
        return retakePolicy.evaluate(reference.attempts(candidate.candidateId(), exam.examId()), exam.retakeWaitDays(), onDate);
    }

    public CandidateView candidate(String candidateNumber) {
        return reference.candidateByNumber(candidateNumber)
                .orElseThrow(() -> new NotFoundException("CANDIDATE_NOT_FOUND", "Candidate " + candidateNumber + " not found"));
    }

    private static BookingChannel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return BookingChannel.PORTAL;
        }
        String c = channel.trim().toUpperCase(Locale.ROOT);
        if (!c.equals("PORTAL") && !c.equals("PARTNER")) {
            throw invalid("channel must be PORTAL or PARTNER");
        }
        return BookingChannel.valueOf(c);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static SchedulingRuleException invalid(String message) {
        return new SchedulingRuleException(RuleViolation.VALIDATION, message);
    }

    static NotFoundException sessionNotFound(int sessionId) {
        return new NotFoundException("SESSION_NOT_FOUND", "Session " + sessionId + " not found");
    }

    static NotFoundException bookingNotFound(long bookingId) {
        return new NotFoundException("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
    }
}
