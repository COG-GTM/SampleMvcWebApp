package com.northwind.assessment.scheduling.service;

import com.northwind.assessment.scheduling.domain.AccommodationCodes;
import com.northwind.assessment.scheduling.domain.AccommodationFloorPolicy;
import com.northwind.assessment.scheduling.domain.BookingChannel;
import com.northwind.assessment.scheduling.domain.BookingStatus;
import com.northwind.assessment.scheduling.domain.CapacityChange;
import com.northwind.assessment.scheduling.domain.EligibilityDecision;
import com.northwind.assessment.scheduling.domain.Reservation;
import com.northwind.assessment.scheduling.domain.ReschedulePolicy;
import com.northwind.assessment.scheduling.domain.RetakeEligibilityPolicy;
import com.northwind.assessment.scheduling.domain.RuleViolation;
import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.domain.SessionCapacity;
import com.northwind.assessment.scheduling.domain.WaitlistSlot;
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
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking, cancellation, reschedule and capacity use cases. Orchestrates the plain-Java domain
 * ({@link SessionCapacity}, {@link RetakeEligibilityPolicy}, {@link ReschedulePolicy}) and persists the
 * outcome; checks run in booking-api v1 order so error codes match.
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
    private final Clock clock;

    public SchedulingService(SessionRepository sessions, BookingRepository bookings, ReferenceRepository reference,
                             RetakeEligibilityPolicy retakePolicy, AccommodationFloorPolicy floorPolicy, Clock clock) {
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
        return bookLocked(capacity, candidate, acc, externalRef, channel);
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
    public BookingView cancel(long bookingId, String reason) {
        int sessionId = bookings.sessionIdOf(bookingId).orElseThrow(() -> bookingNotFound(bookingId));
        SessionCapacity capacity = sessions.lockForUpdate(sessionId).orElseThrow(() -> sessionNotFound(sessionId));
        String why = blankToNull(reason) == null ? DEFAULT_CANCEL_REASON : reason.trim();
        if (why.length() > MAX_REASON) {
            throw invalid("reason is limited to 200 characters");
        }
        cancelLocked(capacity, bookingId, why);
        return bookings.find(bookingId).orElseThrow();
    }

    private void cancelLocked(SessionCapacity capacity, long bookingId, String reason) {
        BookingStatus previous = capacity.cancel(bookingId);
        LocalDateTime now = now();
        bookings.markCancelled(bookingId, reason, now);
        if (previous == BookingStatus.WAITLISTED) {
            bookings.deleteWaitlistEntry(bookingId);
        }
        sessions.markLocallyModified(capacity.sessionId(), now);
    }

    /**
     * usp_RescheduleBooking / booking-api reschedule: same exam only, then cancel ("Rescheduled") + book in one
     * transaction; if the new booking is rejected the cancellation rolls back. Both session rows are locked
     * in ascending id order to avoid deadlocks.
     */
    @Transactional
    public BookingResult reschedule(long bookingId, Integer newSessionId) {
        if (newSessionId == null) {
            throw invalid("newSessionId is required");
        }
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

        cancelLocked(from, bookingId, ReschedulePolicy.CANCEL_REASON);
        CandidateView candidate = reference.candidateByNumber(booking.candidateNumber())
                .orElseThrow(() -> new NotFoundException("CANDIDATE_NOT_FOUND", "Candidate of booking " + bookingId + " not found"));
        BookingChannel channel = BookingChannel.valueOf(booking.channel()) == BookingChannel.IMPORT
                ? BookingChannel.PORTAL : BookingChannel.valueOf(booking.channel());
        return bookLocked(to, candidate, booking.accommodationCode(), booking.externalRef(), channel);
    }

    public record CapacityResult(SessionView session, List<Long> bumpedBookingIds) {
    }

    /** usp_ChangeCapacity + trg_ExamSession_Update, as explicit domain code. */
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
        sessions.insertCapacityChange(sessionId, change.oldCapacity(), newCapacity, change.bumpedBookingIds(), who, now);
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
