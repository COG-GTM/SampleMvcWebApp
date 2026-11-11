package com.northwind.scheduling.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.scheduling.api.ApiException;
import com.northwind.scheduling.core.Channels;
import com.northwind.scheduling.core.ReadModels;
import com.northwind.scheduling.core.SchedulingCommands;
import com.northwind.scheduling.reference.ReferenceData;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    public record BookRequest(Integer sessionId, Integer candidateId, String candidateNumber, String accommodationCode,
                              String channel, String externalRef) {
    }

    public record CancelRequest(String reason) {
    }

    public record RescheduleRequest(Integer newSessionId, String channel) {
    }

    public record AttendanceResult(Integer resultId, Integer score, Boolean passed, LocalDateTime scoredAt) {
    }

    public record AttendanceRequest(AttendanceResult result) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BookingResult(int bookingId, String status, Integer waitlistPosition, Integer cancelledBookingId) {
        static BookingResult of(SchedulingCommands.BookOutcome o) {
            return new BookingResult(o.bookingId(), o.status(), o.waitlistPosition(), o.cancelledBookingId());
        }
    }

    private final SchedulingCommands commands;
    private final ReadModels reads;
    private final ReferenceData reference;
    private final Idempotency idempotency;
    private final ObjectMapper mapper;

    public BookingController(SchedulingCommands commands, ReadModels reads, ReferenceData reference, Idempotency idempotency,
                             ObjectMapper mapper) {
        this.commands = commands;
        this.reads = reads;
        this.reference = reference;
        this.idempotency = idempotency;
        this.mapper = mapper;
    }

    @GetMapping
    public List<ReadModels.Booking> search(@RequestParam(required = false) Integer candidateId,
                                           @RequestParam(required = false) String candidateNumber,
                                           @RequestParam(required = false) Integer sessionId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(required = false) Integer limit) {
        return reads.bookings(candidateId, candidateNumber, sessionId, status, sort, limit);
    }

    @GetMapping("/{bookingId}")
    public ReadModels.Booking get(@PathVariable int bookingId) {
        return reads.requireBooking(bookingId);
    }

    @PostMapping
    public ResponseEntity<?> book(@RequestBody BookRequest r,
                                  @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                  @RequestHeader(value = "X-Client-Id", required = false) String client,
                                  @RequestHeader(value = "X-Actor", required = false) String actor) {
        String channel = channel(r == null ? null : r.channel());
        if (r.sessionId() == null || (r.candidateId() == null && (r.candidateNumber() == null || r.candidateNumber().isBlank()))) {
            throw ApiException.badRequest("VALIDATION", "sessionId and candidateId or candidateNumber are required");
        }
        String acc = r.accommodationCode();
        if (Channels.PARTNER.equals(channel)) {
            // booking-api normalises and validates; the portal resolves its default before calling (HB-09)
            acc = acc == null || acc.isBlank() ? null : acc.trim().toUpperCase(Locale.ROOT);
            if (acc != null && !Channels.ACCOMMODATION_CODES.contains(acc)) {
                throw ApiException.badRequest("VALIDATION", "Unknown accommodationCode " + acc);
            }
        } else if (acc != null && acc.length() > 10) {
            throw ApiException.badRequest("VALIDATION", "accommodationCode is limited to 10 characters");
        }
        if (r.externalRef() != null && r.externalRef().length() > 40) {
            throw ApiException.badRequest("VALIDATION", "externalRef is limited to 40 characters");
        }
        String finalAcc = acc;
        return idempotency.execute(key, client, "book:" + json(r), () -> {
            int candidateId = r.candidateId() != null ? r.candidateId() : resolveCandidate(r.candidateNumber().trim());
            if (Channels.PARTNER.equals(channel) && reads.session(r.sessionId()).isEmpty()) {
                throw ApiException.notFound("SESSION_NOT_FOUND", "Session " + r.sessionId() + " not found");
            }
            SchedulingCommands.BookOutcome o = commands.book(r.sessionId(), candidateId, finalAcc, channel, r.externalRef(), actor);
            return ResponseEntity.status(HttpStatus.CREATED).body(BookingResult.of(o));
        });
    }

    private int resolveCandidate(String number) {
        Optional<Integer> id = reference.candidateIdByNumber(number);
        if (id.isEmpty()) {
            reference.catchUp();
            id = reference.candidateIdByNumber(number);
        }
        return id.orElseThrow(() -> ApiException.notFound("CANDIDATE_NOT_FOUND", "Candidate " + number + " not found"));
    }

    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable int bookingId, @RequestBody(required = false) CancelRequest r,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                    @RequestHeader(value = "X-Client-Id", required = false) String client,
                                    @RequestHeader(value = "X-Actor", required = false) String actor) {
        String reason = r == null ? null : r.reason();
        if (reason != null && reason.length() > 200) {
            throw ApiException.badRequest("VALIDATION", "reason is limited to 200 characters");
        }
        return idempotency.execute(key, client, "cancel:" + bookingId + ":" + reason, () -> {
            commands.cancel(bookingId, reason, actor);
            return ResponseEntity.ok(reads.requireBooking(bookingId));
        });
    }

    @PostMapping("/{bookingId}/reschedule")
    public ResponseEntity<?> reschedule(@PathVariable int bookingId, @RequestBody RescheduleRequest r,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        @RequestHeader(value = "X-Client-Id", required = false) String client,
                                        @RequestHeader(value = "X-Actor", required = false) String actor) {
        String channel = channel(r == null ? null : r.channel());
        if (r.newSessionId() == null) {
            throw ApiException.badRequest("VALIDATION", "newSessionId is required");
        }
        return idempotency.execute(key, client, "reschedule:" + bookingId + ":" + r.newSessionId() + ":" + channel, () -> {
            SchedulingCommands.BookOutcome o = Channels.PARTNER.equals(channel)
                    ? commands.reschedulePartner(bookingId, r.newSessionId(), actor)
                    : commands.reschedulePortal(bookingId, r.newSessionId(), actor);
            return ResponseEntity.status(HttpStatus.CREATED).body(BookingResult.of(o));
        });
    }

    @PostMapping("/{bookingId}/attendance")
    public Map<String, Object> attendance(@PathVariable int bookingId, @RequestBody(required = false) AttendanceRequest r) {
        SchedulingCommands.AttemptResult result = null;
        if (r != null && r.result() != null) {
            AttendanceResult a = r.result();
            if (a.resultId() == null || a.score() == null || a.passed() == null) {
                throw ApiException.badRequest("VALIDATION", "result.resultId, result.score and result.passed are required");
            }
            result = new SchedulingCommands.AttemptResult(a.resultId(), a.score(), a.passed(), a.scoredAt());
        }
        SchedulingCommands.AttendOutcome o = commands.markAttended(bookingId, result);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new LinkedHashMap<>(mapper.convertValue(reads.requireBooking(bookingId), Map.class));
        body.put("transitioned", o.transitioned());
        return body;
    }

    private static String channel(String c) {
        if (c == null || !Channels.ALL.contains(c)) {
            throw ApiException.badRequest("VALIDATION", "channel must be one of PORTAL, PARTNER, IMPORT");
        }
        return c;
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }
}
