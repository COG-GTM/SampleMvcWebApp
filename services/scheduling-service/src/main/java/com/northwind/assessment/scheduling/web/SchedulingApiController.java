package com.northwind.assessment.scheduling.web;

import com.northwind.assessment.scheduling.domain.RuleViolation;
import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.domain.WaitlistOfferState;
import com.northwind.assessment.scheduling.persistence.BookingRepository;
import com.northwind.assessment.scheduling.persistence.ReferenceRepository;
import com.northwind.assessment.scheduling.persistence.SessionRepository;
import com.northwind.assessment.scheduling.persistence.Views;
import com.northwind.assessment.scheduling.service.BookingCommand;
import com.northwind.assessment.scheduling.service.BookingResult;
import com.northwind.assessment.scheduling.service.NotFoundException;
import com.northwind.assessment.scheduling.service.SchedulingService;
import com.northwind.assessment.scheduling.service.SeatOfferService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** /api/v2 - booking, cancellation, reschedule, capacity and the reads the SPA needs. No auth (kiosk-era estate). */
@RestController
@RequestMapping("/api/v2")
public class SchedulingApiController {

    private final SchedulingService service;
    private final SessionRepository sessions;
    private final BookingRepository bookings;
    private final ReferenceRepository reference;
    private final SeatOfferService offers;
    private final Clock clock;

    public SchedulingApiController(SchedulingService service, SessionRepository sessions, BookingRepository bookings,
                                   ReferenceRepository reference, SeatOfferService offers, Clock clock) {
        this.service = service;
        this.clock = clock;
        this.offers = offers;
        this.sessions = sessions;
        this.bookings = bookings;
        this.reference = reference;
    }

    @GetMapping("/centers")
    public List<Json.Center> centers() {
        return reference.centers().stream().filter(Views.CenterView::active).map(Json.Center::of).toList();
    }

    @GetMapping("/exams")
    public List<Json.Exam> exams() {
        return reference.exams().stream().map(Json.Exam::of).toList();
    }

    /** booking-api v1 search semantics: {@code from} inclusive, {@code to} exclusive (yyyy-MM-dd), max 500, by start. */
    @GetMapping("/centers/{centerCode}/sessions")
    public List<Json.Session> centerSessions(@PathVariable String centerCode,
                                             @RequestParam(required = false) String examCode,
                                             @RequestParam(required = false) String from,
                                             @RequestParam(required = false) String to) {
        Views.CenterView center = reference.centerByCode(centerCode)
                .orElseThrow(() -> new NotFoundException("CENTER_NOT_FOUND", "Test center " + centerCode + " not found"));
        LocalDateTime fromTs = parseDate("from", from);
        LocalDateTime toTs = parseDate("to", to);
        Integer examId = null;
        if (examCode != null && !examCode.isBlank()) {
            Optional<Views.ExamView> exam = reference.examByCode(examCode.trim());
            if (exam.isEmpty()) {
                return List.of();
            }
            examId = exam.get().examId();
        }
        return sessions.search(center.centerId(), examId, fromTs, toTs).stream().map(Json.Session::of).toList();
    }

    @GetMapping("/sessions/{sessionId}")
    public Json.Session session(@PathVariable int sessionId) {
        return Json.Session.of(sessions.find(sessionId).orElseThrow(() -> sessionNotFound(sessionId)));
    }

    @GetMapping("/sessions/{sessionId}/waitlist")
    public List<Json.WaitlistEntry> waitlist(@PathVariable int sessionId) {
        sessions.find(sessionId).orElseThrow(() -> sessionNotFound(sessionId));
        Map<Long, WaitlistOfferState> states = offers.waitlistStates(sessionId);
        return bookings.waitlist(sessionId).stream().map(w -> Json.WaitlistEntry.of(w, states.get(w.bookingId()))).toList();
    }

    @PutMapping("/sessions/{sessionId}/capacity")
    public Json.CapacityResponse changeCapacity(@PathVariable int sessionId, @RequestBody Json.CapacityRequest body) {
        SchedulingService.CapacityResult r = service.changeCapacity(sessionId, body.newCapacity(), body.actor());
        return new Json.CapacityResponse(Json.Session.of(r.session()), r.bumpedBookingIds());
    }

    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResult book(@RequestBody BookingCommand body) {
        return service.book(body);
    }

    @GetMapping("/bookings/{bookingId}")
    public Json.Booking booking(@PathVariable long bookingId) {
        return Json.Booking.of(bookings.find(service.resolve(bookingId)).orElseThrow());
    }

    /** results-service: marks a BOOKED booking ATTENDED (idempotent); the relay projects it to AssessmentDelivery. */
    @PostMapping("/bookings/{bookingId}/attendance")
    public Json.Booking attendance(@PathVariable long bookingId) {
        return Json.Booking.of(service.recordAttendance(bookingId));
    }

    @DeleteMapping("/bookings/{bookingId}")
    public Json.Booking cancel(@PathVariable long bookingId, @RequestParam(required = false) String reason) {
        return Json.Booking.of(service.cancel(bookingId, reason));
    }

    @PostMapping("/bookings/{bookingId}/reschedule")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResult reschedule(@PathVariable long bookingId, @RequestBody Json.RescheduleRequest body) {
        return service.reschedule(bookingId, body.newSessionId(), body.channel());
    }

    @GetMapping("/candidates/{candidateNumber}")
    public Json.Candidate candidate(@PathVariable String candidateNumber) {
        return Json.Candidate.of(service.candidate(candidateNumber));
    }

    @GetMapping("/candidates/{candidateNumber}/bookings")
    public List<Json.Booking> candidateBookings(@PathVariable String candidateNumber) {
        int candidateId = service.candidate(candidateNumber).candidateId();
        Map<Long, Views.OfferView> pending = new HashMap<>();
        offers.byCandidate(candidateId).stream().filter(o -> "PENDING".equals(o.status()))
                .forEach(o -> pending.put(o.bookingId(), o));
        return bookings.byCandidate(candidateId).stream().map(b -> Json.Booking.of(b, pending.get(b.bookingId()), now())).toList();
    }

    /** Every seat offer the candidate received, newest first (ADR 0003). */
    @GetMapping("/candidates/{candidateNumber}/offers")
    public List<Json.Offer> candidateOffers(@PathVariable String candidateNumber) {
        return offers.byCandidate(service.candidate(candidateNumber).candidateId()).stream().map(o -> Json.Offer.of(o, now())).toList();
    }

    @GetMapping("/offers/{offerId}")
    public Json.Offer offer(@PathVariable long offerId) {
        return Json.Offer.of(offers.find(offerId), now());
    }

    @PostMapping("/offers/{offerId}/accept")
    public Json.Offer acceptOffer(@PathVariable long offerId, @RequestBody(required = false) Json.OfferDecisionRequest body) {
        return Json.Offer.of(offers.accept(offerId, body == null ? null : body.actor()), now());
    }

    @PostMapping("/offers/{offerId}/decline")
    public Json.Offer declineOffer(@PathVariable long offerId, @RequestBody(required = false) Json.OfferDecisionRequest body) {
        return Json.Offer.of(offers.decline(offerId, body == null ? null : body.actor()), now());
    }

    /** Alias of {@code /offers/{offerId}/accept} for the booking's pending offer. */
    @PostMapping("/bookings/{bookingId}/offer/accept")
    public Json.Offer acceptBookingOffer(@PathVariable long bookingId,
                                         @RequestBody(required = false) Json.OfferDecisionRequest body) {
        long offerId = offers.pendingForBooking(service.resolve(bookingId)).offerId();
        return acceptOffer(offerId, body);
    }

    /** Alias of {@code /offers/{offerId}/decline} for the booking's pending offer. */
    @PostMapping("/bookings/{bookingId}/offer/decline")
    public Json.Offer declineBookingOffer(@PathVariable long bookingId,
                                          @RequestBody(required = false) Json.OfferDecisionRequest body) {
        long offerId = offers.pendingForBooking(service.resolve(bookingId)).offerId();
        return declineOffer(offerId, body);
    }

    @GetMapping("/candidates/{candidateNumber}/eligibility")
    public Json.Eligibility eligibility(@PathVariable String candidateNumber,
                                        @RequestParam(required = false) String examCode,
                                        @RequestParam(required = false) String date) {
        return Json.Eligibility.of(service.eligibility(candidateNumber, examCode, date));
    }

    private static LocalDateTime parseDate(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (RuntimeException e) {
            throw new SchedulingRuleException(RuleViolation.VALIDATION, "Invalid " + name + " '" + value + "', expected yyyy-MM-dd");
        }
    }

    private static NotFoundException sessionNotFound(int sessionId) {
        return new NotFoundException("SESSION_NOT_FOUND", "Session " + sessionId + " not found");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
