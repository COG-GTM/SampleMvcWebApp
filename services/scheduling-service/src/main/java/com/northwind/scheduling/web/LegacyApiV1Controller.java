package com.northwind.scheduling.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northwind.scheduling.domain.EligibilityDecision;
import com.northwind.scheduling.persistence.Rows;
import com.northwind.scheduling.service.SchedulingService;

/**
 * Partner-compatible facade with booking-api's /api/v1 contract (BookingController, SessionController,
 * CandidateController) on top of the modern rules. It exists so the M1 api transcripts can be replayed
 * against :8090 and so the M3 gateway can route partners here without a contract change.
 * Bookings made here are channel PARTNER; behaviour follows ADR 0002, not the legacy oddities.
 */
@RestController
@RequestMapping("/api/v1")
public class LegacyApiV1Controller {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final SchedulingService service;

    public LegacyApiV1Controller(SchedulingService service) {
        this.service = service;
    }

    static String ts(LocalDateTime t) {
        return t == null ? null : TS.format(t);
    }

    static Map<String, Object> session(Rows.Session s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", s.sessionId());
        m.put("examCode", s.examCode());
        m.put("examTitle", s.examTitle());
        m.put("centerCode", s.centerCode());
        m.put("centerName", s.centerName());
        m.put("roomName", s.roomName());
        m.put("startsAt", ts(s.startsAt()));
        m.put("capacity", s.capacity());
        m.put("accommodationCapacity", s.accommodationCapacity());
        m.put("seatsAvailable", s.seats().seatsAvailable());
        m.put("status", s.status().name());
        m.put("bookedSeats", s.occupied());
        return m;
    }

    static Map<String, Object> booking(Rows.Booking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", b.bookingId());
        m.put("sessionId", b.sessionId());
        m.put("candidateNumber", b.candidateNumber());
        m.put("examCode", b.examCode());
        m.put("sessionStartsAt", ts(b.sessionStartsAt()));
        m.put("status", b.status().name());
        m.put("accommodationCode", b.accommodationCode());
        m.put("channel", b.channel());
        m.put("externalRef", b.externalRef());
        m.put("bookedAt", ts(b.bookedAt()));
        m.put("cancelledAt", ts(b.cancelledAt()));
        m.put("cancelReason", b.cancelReason());
        return m;
    }

    static Map<String, Object> outcome(SchedulingService.BookingOutcome o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", o.bookingId());
        m.put("status", o.status().name());
        return m;
    }

    @GetMapping("/sessions")
    public List<Map<String, Object>> sessions(@RequestParam(required = false) String examCode,
                                              @RequestParam(required = false) String centerCode,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return service.sessions(centerCode, examCode, SchedulingApiController.date(from), SchedulingApiController.date(to))
                .stream().map(LegacyApiV1Controller::session).toList();
    }

    @GetMapping("/sessions/{sessionId}")
    public Map<String, Object> session(@PathVariable int sessionId) {
        return session(service.session(sessionId));
    }

    @PostMapping("/bookings")
    public ResponseEntity<Map<String, Object>> book(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Object sid = b.get("sessionId");
        Integer sessionId = sid instanceof Number n ? n.intValue() : null;
        SchedulingService.BookingOutcome o = service.book(new SchedulingService.BookCommand(sessionId,
                (String) b.get("candidateNumber"), (String) b.get("accommodationCode"), (String) b.get("externalRef"),
                "PARTNER"));
        return ResponseEntity.status(HttpStatus.CREATED).body(outcome(o));
    }

    @GetMapping("/bookings/{bookingId}")
    public Map<String, Object> booking(@PathVariable int bookingId) {
        return booking(service.booking(bookingId));
    }

    @DeleteMapping("/bookings/{bookingId}")
    public Map<String, Object> cancel(@PathVariable int bookingId, @RequestParam(required = false) String reason) {
        return booking(service.cancel(bookingId, reason == null || reason.isBlank() ? "Cancelled by partner" : reason));
    }

    @PostMapping("/bookings/{bookingId}/reschedule")
    public Map<String, Object> reschedule(@PathVariable int bookingId,
                                          @RequestBody(required = false) Map<String, Object> body) {
        Object v = body == null ? null : body.get("newSessionId");
        return outcome(service.reschedule(bookingId, v instanceof Number n ? n.intValue() : null));
    }

    @GetMapping("/candidates/{candidateNumber}/bookings")
    public List<Map<String, Object>> bookings(@PathVariable String candidateNumber) {
        return service.bookingsFor(candidateNumber).stream().map(LegacyApiV1Controller::booking).toList();
    }

    @GetMapping("/candidates/{candidateNumber}/eligibility")
    public Map<String, Object> eligibility(@PathVariable String candidateNumber,
                                           @RequestParam(required = false) String examCode,
                                           @RequestParam(required = false) String date) {
        if (examCode == null || examCode.isBlank()) {
            service.eligibility(candidateNumber, examCode, null);
        }
        LocalDate d = SchedulingApiController.date(date);
        EligibilityDecision e = service.eligibility(candidateNumber, examCode, d);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eligible", e.eligible());
        m.put("reason", e.reason());
        m.put("earliestRetakeDate", e.earliestRetakeDate() == null ? null : e.earliestRetakeDate().toString());
        return m;
    }
}
