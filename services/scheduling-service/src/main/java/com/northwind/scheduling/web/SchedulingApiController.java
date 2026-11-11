package com.northwind.scheduling.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northwind.scheduling.domain.CapacityChange;
import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.RetakeEligibilityPolicy;
import com.northwind.scheduling.domain.SchedulingException;
import com.northwind.scheduling.service.SchedulingService;
import com.northwind.scheduling.web.Dtos.*;

/** Public scheduling API, base /api/v2. Errors: {error, message, legacyCode} (ApiExceptionHandler). */
@RestController
@RequestMapping("/api/v2")
public class SchedulingApiController {
    private static final Set<String> CHANNELS = Set.of("PORTAL", "PARTNER");

    private final SchedulingService service;
    private final RetakeEligibilityPolicy retakePolicy;
    private final String defaultChannel;

    public SchedulingApiController(SchedulingService service, RetakeEligibilityPolicy retakePolicy,
                                   @Value("${scheduling.default-channel:PORTAL}") String defaultChannel) {
        this.service = service;
        this.retakePolicy = retakePolicy;
        this.defaultChannel = defaultChannel;
    }

    static LocalDate date(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new SchedulingException(ErrorCode.VALIDATION, "Invalid date '" + v + "', expected yyyy-MM-dd");
        }
    }

    @GetMapping("/centers")
    public List<CenterDto> centers() {
        return service.centers().stream().map(CenterDto::of).toList();
    }

    @GetMapping("/exams")
    public List<ExamDto> exams() {
        return service.exams().stream().map(ExamDto::of).toList();
    }

    @GetMapping("/centers/{centerCode}/sessions")
    public List<SessionDto> sessions(@PathVariable String centerCode, @RequestParam(required = false) String examCode,
                                     @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        service.center(centerCode);
        return service.sessions(centerCode, examCode, date(from), date(to)).stream().map(SessionDto::of).toList();
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionDto session(@PathVariable int sessionId) {
        return SessionDto.of(service.session(sessionId));
    }

    @GetMapping("/sessions/{sessionId}/waitlist")
    public List<WaitlistEntryDto> waitlist(@PathVariable int sessionId) {
        return service.waitlist(sessionId).stream().map(WaitlistEntryDto::of).toList();
    }

    @GetMapping("/sessions/{sessionId}/capacity-changes")
    public List<CapacityChangeDto> capacityChanges(@PathVariable int sessionId) {
        return service.capacityChanges(sessionId).stream().map(CapacityChangeDto::of).toList();
    }

    @PutMapping("/sessions/{sessionId}/capacity")
    public CapacityResultDto changeCapacity(@PathVariable int sessionId, @RequestBody(required = false) CapacityRequest req) {
        SchedulingService.CapacityOutcome out = service.changeCapacity(sessionId, req == null ? null : req.capacity(),
                req == null ? null : req.reason());
        return new CapacityResultDto(SessionDto.of(out.session()), out.plan().oldCapacity(), out.plan().newCapacity(),
                out.plan().demotions().stream().map(CapacityChange.Demotion::bookingId).toList());
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResultDto> book(@RequestBody(required = false) BookRequest req) {
        String channel = req == null || req.channel() == null || req.channel().isBlank() ? defaultChannel
                : req.channel().trim().toUpperCase();
        if (!CHANNELS.contains(channel)) {
            throw new SchedulingException(ErrorCode.VALIDATION, "channel must be PORTAL or PARTNER");
        }
        SchedulingService.BookingOutcome out = service.book(req == null ? null
                : new SchedulingService.BookCommand(req.sessionId(), req.candidateNumber(), req.accommodationCode(),
                        req.externalRef(), channel));
        return ResponseEntity.status(HttpStatus.CREATED).body(result(out));
    }

    private BookingResultDto result(SchedulingService.BookingOutcome out) {
        return new BookingResultDto(out.bookingId(), out.status().name(), out.waitlistPosition(),
                BookingDto.of(service.booking(out.bookingId())), SessionDto.of(service.session(out.sessionId())));
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingDto booking(@PathVariable int bookingId) {
        return BookingDto.of(service.booking(bookingId));
    }

    @DeleteMapping("/bookings/{bookingId}")
    public BookingDto cancel(@PathVariable int bookingId, @RequestParam(required = false) String reason) {
        return BookingDto.of(service.cancel(bookingId, reason == null || reason.isBlank() ? "Cancelled by candidate" : reason));
    }

    @PostMapping("/bookings/{bookingId}/reschedule")
    public ResponseEntity<BookingResultDto> reschedule(@PathVariable int bookingId,
                                                       @RequestBody(required = false) RescheduleRequest req) {
        SchedulingService.BookingOutcome out = service.reschedule(bookingId, req == null ? null : req.newSessionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(result(out));
    }

    @GetMapping("/candidates/{candidateNumber}")
    public CandidateDto candidate(@PathVariable String candidateNumber) {
        return CandidateDto.of(service.candidate(candidateNumber));
    }

    @GetMapping("/candidates/{candidateNumber}/bookings")
    public List<BookingDto> candidateBookings(@PathVariable String candidateNumber) {
        return service.bookingsFor(candidateNumber).stream().map(BookingDto::of).toList();
    }

    @GetMapping("/candidates/{candidateNumber}/eligibility")
    public EligibilityDto eligibility(@PathVariable String candidateNumber, @RequestParam(required = false) String examCode,
                                      @RequestParam(required = false) String date) {
        LocalDate d = date(date);
        LocalDate on = d == null ? service.today() : d;
        return EligibilityDto.of(candidateNumber, examCode, on, service.eligibility(candidateNumber, examCode, on),
                retakePolicy.counting().name().toLowerCase());
    }
}
