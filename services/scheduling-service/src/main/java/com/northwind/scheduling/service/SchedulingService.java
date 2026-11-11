package com.northwind.scheduling.service;

import com.northwind.scheduling.outbox.OutboxEventType;
import com.northwind.scheduling.outbox.OutboxRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.northwind.scheduling.domain.AccommodationCodes;
import com.northwind.scheduling.domain.AccommodationFloorPolicy;
import com.northwind.scheduling.domain.BookingRules;
import com.northwind.scheduling.domain.BookingStatus;
import com.northwind.scheduling.domain.CapacityChange;
import com.northwind.scheduling.domain.EligibilityDecision;
import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.ReschedulePolicy;
import com.northwind.scheduling.domain.RetakeEligibilityPolicy;
import com.northwind.scheduling.domain.SchedulingException;
import com.northwind.scheduling.persistence.Rows;
import com.northwind.scheduling.persistence.SchedulingRepository;

/** Application service: transactions, locking and persistence around the domain rules. */
@Service
public class SchedulingService {

    public record BookCommand(Integer sessionId, String candidateNumber, String accommodationCode, String externalRef,
                              String channel) {
    }

    public record BookingOutcome(int bookingId, BookingStatus status, Integer waitlistPosition, int sessionId) {
    }

    public record CapacityOutcome(CapacityChange.Plan plan, Rows.Session session) {
    }

    private final SchedulingRepository repo;
    private final RetakeEligibilityPolicy retakePolicy;
    private final AccommodationFloorPolicy floorPolicy;
    private final Clock clock;
    private final String actor;
    private final OutboxRepository outbox;

    public SchedulingService(SchedulingRepository repo, RetakeEligibilityPolicy retakePolicy,
                             AccommodationFloorPolicy floorPolicy, Clock clock,
                             @Value("${scheduling.actor:scheduling-service}") String actor, OutboxRepository outbox) {
        this.repo = repo;
        this.outbox = outbox;
        this.retakePolicy = retakePolicy;
        this.floorPolicy = floorPolicy;
        this.clock = clock;
        this.actor = actor;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ------------------------------------------------------------------ queries

    public List<Rows.Center> centers() {
        return repo.centers();
    }

    public List<Rows.Exam> exams() {
        return repo.exams();
    }

    public Rows.Center center(String code) {
        return repo.center(code).orElseThrow(
                () -> new SchedulingException(ErrorCode.CENTER_NOT_FOUND, "Center " + code + " not found"));
    }

    public List<Rows.Session> sessions(String centerCode, String examCode, LocalDate from, LocalDate to) {
        return repo.sessions(centerCode, examCode, from, to);
    }

    public Rows.Session session(int sessionId) {
        return repo.session(sessionId).orElseThrow(
                () -> new SchedulingException(ErrorCode.SESSION_NOT_FOUND, "Session " + sessionId + " not found"));
    }

    public Rows.Booking booking(int bookingId) {
        return repo.booking(bookingId).orElseThrow(
                () -> new SchedulingException(ErrorCode.BOOKING_NOT_FOUND, "Booking " + bookingId + " not found"));
    }

    public Rows.Candidate candidate(String number) {
        return repo.candidate(number).orElseThrow(
                () -> new SchedulingException(ErrorCode.CANDIDATE_NOT_FOUND, "Candidate " + number + " not found"));
    }

    public List<Rows.Booking> bookingsFor(String candidateNumber) {
        return repo.bookingsForCandidate(candidate(candidateNumber).id());
    }

    public List<Rows.WaitlistEntry> waitlist(int sessionId) {
        session(sessionId);
        return repo.waitlist(sessionId);
    }

    public List<Rows.CapacityChangeRow> capacityChanges(int sessionId) {
        session(sessionId);
        return repo.capacityChanges(sessionId);
    }

    /** booking-api CandidateController.eligibility: candidate, then exam (EXAM_NOT_FOUND), date defaults to today. */
    public EligibilityDecision eligibility(String candidateNumber, String examCode, LocalDate onDate) {
        if (examCode == null || examCode.isBlank()) {
            throw new SchedulingException(ErrorCode.VALIDATION, "examCode is required");
        }
        Rows.Candidate c = candidate(candidateNumber);
        Rows.Exam e = repo.exam(examCode).orElseThrow(
                () -> new SchedulingException(ErrorCode.EXAM_NOT_FOUND, "Exam " + examCode + " not found"));
        return retakePolicy.evaluate(repo.attempts(c.id(), e.id()), e.retakeWaitDays(), onDate == null ? today() : onDate);
    }

    // ------------------------------------------------------------------ commands

    /** usp_BookSeat / booking-api BookingService.book, validation order as booking-api. */
    @Transactional
    public BookingOutcome book(BookCommand cmd) {
        return book(cmd, null);
    }

    private BookingOutcome book(BookCommand cmd, Integer rescheduledFrom) {
        if (cmd == null || cmd.sessionId() == null || cmd.candidateNumber() == null || cmd.candidateNumber().isBlank()) {
            throw new SchedulingException(ErrorCode.VALIDATION, "sessionId and candidateNumber are required");
        }
        String acc = AccommodationCodes.normalize(cmd.accommodationCode());
        if (cmd.externalRef() != null && cmd.externalRef().length() > 40) {
            throw new SchedulingException(ErrorCode.VALIDATION, "externalRef is limited to 40 characters");
        }
        Rows.Candidate cand = candidate(cmd.candidateNumber().trim());
        int sessionId = cmd.sessionId();
        if (!repo.lockSession(sessionId)) {
            throw new SchedulingException(ErrorCode.SESSION_NOT_FOUND, "Session " + sessionId + " not found");
        }
        Rows.Session s = session(sessionId);
        EligibilityDecision eligibility = retakePolicy.evaluate(repo.attempts(cand.id(), s.examId()),
                repo.exam(s.examCode()).map(Rows.Exam::retakeWaitDays).orElse(30), s.startsAt().toLocalDate());
        BookingRules.Outcome outcome = BookingRules.decide(sessionId, cand.number(), s.status(), s.seats(),
                repo.hasActiveBooking(sessionId, cand.id()), acc, eligibility, s.examCode());

        LocalDateTime now = now();
        BookingStatus status = outcome == BookingRules.Outcome.BOOKED ? BookingStatus.BOOKED : BookingStatus.WAITLISTED;
        String channel = cmd.channel() == null || cmd.channel().isBlank() ? "PORTAL" : cmd.channel();
        int bookingId = repo.insertBooking(sessionId, cand.id(), status, acc, channel, cmd.externalRef(), now,
                rescheduledFrom);
        Integer position = null;
        if (status == BookingStatus.WAITLISTED) {
            position = BookingRules.nextWaitlistPosition(repo.maxWaitlistPosition(sessionId));
            repo.insertWaitlist(bookingId, position, now);
        }
        if (rescheduledFrom == null) {
            outbox.append(status == BookingStatus.BOOKED ? OutboxEventType.BookingCreated : OutboxEventType.BookingWaitlisted,
                    bookingId, List.of(bookingId), List.of(sessionId), actor, "channel=" + channel);
        }
        return new BookingOutcome(bookingId, status, position, sessionId);
    }

    /** usp_CancelBooking: frees the seat (derived count), drops the waitlist row, no promotion (ODD-09). */
    @Transactional
    public Rows.Booking cancel(int bookingId, String reason) {
        Rows.Booking b = booking(bookingId);
        repo.lockSession(b.sessionId());
        b = booking(bookingId);
        BookingRules.checkCancellable(bookingId, b.status());
        repo.markCancelled(bookingId, reason, now());
        if (b.status() == BookingStatus.WAITLISTED) {
            repo.deleteWaitlistEntry(bookingId);
        }
        outbox.append(OutboxEventType.BookingCancelled, bookingId, List.of(bookingId), List.of(b.sessionId()), actor,
                "reason=" + reason);
        return booking(bookingId);
    }

    /**
     * MBA-3823: results-service reports attendance here instead of updating dbo.Booking. BOOKED becomes ATTENDED
     * (one AttendanceRecorded event); an already ATTENDED booking is a no-op, so ingestion retries are safe.
     */
    @Transactional
    public Rows.Booking recordAttendance(int bookingId) {
        Rows.Booking b = booking(bookingId);
        repo.lockSession(b.sessionId());
        b = booking(bookingId);
        if (b.status() == BookingStatus.ATTENDED) {
            return b;
        }
        if (b.status() != BookingStatus.BOOKED) {
            // reuses NOT_CANCELLABLE ("booking is not BOOKED") so the published error vocabulary stays unchanged
            throw new SchedulingException(ErrorCode.NOT_CANCELLABLE,
                    "Booking " + bookingId + " is " + b.status() + ", cannot record attendance");
        }
        repo.markAttended(bookingId, now());
        outbox.append(OutboxEventType.AttendanceRecorded, bookingId, List.of(bookingId), List.of(b.sessionId()), actor, null);
        return booking(bookingId);
    }

    /**
     * usp_RescheduleBooking / booking-api reschedule: cancel + book in one transaction, same exam only,
     * same session rejected (ODD-08), one seat moves (ODD-05), channel/externalRef/accommodation kept (ODD-10).
     * If the new booking fails the cancellation rolls back too.
     */
    @Transactional
    public BookingOutcome reschedule(int bookingId, Integer newSessionId) {
        if (newSessionId == null) {
            throw new SchedulingException(ErrorCode.VALIDATION, "newSessionId is required");
        }
        Rows.Booking old = booking(bookingId);
        int newExam = repo.sessionExamId(newSessionId).orElseThrow(
                () -> new SchedulingException(ErrorCode.SESSION_NOT_FOUND, "Session " + newSessionId + " not found"));
        // lock both sessions in id order so concurrent moves cannot deadlock
        repo.lockSession(Math.min(old.sessionId(), newSessionId));
        repo.lockSession(Math.max(old.sessionId(), newSessionId));
        old = booking(bookingId);
        ReschedulePolicy.check(bookingId, old.status(), old.sessionId(), old.examId(), newSessionId, newExam);
        repo.markCancelled(bookingId, ReschedulePolicy.CANCEL_REASON, now());
        if (old.status() == BookingStatus.WAITLISTED) {
            repo.deleteWaitlistEntry(bookingId);
        }
        BookingOutcome moved = book(new BookCommand(newSessionId, old.candidateNumber(), old.accommodationCode(),
                old.externalRef(), old.channel()), bookingId);
        outbox.append(OutboxEventType.BookingRescheduled, moved.bookingId(), List.of(bookingId, moved.bookingId()),
                List.of(old.sessionId(), newSessionId), actor, "from=" + bookingId + " to=" + moved.bookingId());
        return moved;
    }

    /** usp_ChangeCapacity + trg_ExamSession_Update demotion (ODD-02 kept, ODD-06 floor per ADR 0002). */
    @Transactional
    public CapacityOutcome changeCapacity(int sessionId, Integer newCapacity, String reason) {
        if (newCapacity == null) {
            throw new SchedulingException(ErrorCode.VALIDATION, "capacity is required");
        }
        if (!repo.lockSession(sessionId)) {
            throw new SchedulingException(ErrorCode.SESSION_NOT_FOUND, "Session " + sessionId + " not found");
        }
        Rows.Session s = session(sessionId);
        CapacityChange.Plan plan = CapacityChange.plan(sessionId, newCapacity, s.seats(), repo.bookedSeats(sessionId),
                repo.maxWaitlistPosition(sessionId), floorPolicy);
        LocalDateTime now = now();
        repo.updateCapacity(sessionId, plan.newCapacity(), plan.newAccommodationCapacity(), now);
        for (CapacityChange.Demotion d : plan.demotions()) {
            repo.markWaitlisted(d.bookingId(), now);
            repo.insertWaitlist(d.bookingId(), d.position(), now);
        }
        repo.insertCapacityChange(plan, reason, actor, now);
        outbox.append(OutboxEventType.CapacityChanged, sessionId,
                plan.demotions().stream().map(CapacityChange.Demotion::bookingId).toList(), List.of(sessionId), actor,
                "capacity=" + plan.newCapacity() + " demoted=" + plan.demotions().size()
                        + (reason == null ? "" : " reason=" + reason));
        return new CapacityOutcome(plan, session(sessionId));
    }
}
