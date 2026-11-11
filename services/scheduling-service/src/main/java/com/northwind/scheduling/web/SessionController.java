package com.northwind.scheduling.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.northwind.scheduling.api.ApiException;
import com.northwind.scheduling.core.ReadModels;
import com.northwind.scheduling.core.SchedulingCommands;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    public record CapacityRequest(Integer newCapacity) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateSessionRequest(Integer examId, Integer testCenterId, Integer roomId, LocalDateTime startsAt,
                                       Integer capacity, Integer accommodationCapacity, Integer seatsAvailable, String status) {
    }

    private final ReadModels reads;
    private final SchedulingCommands commands;
    private final Idempotency idempotency;

    public SessionController(ReadModels reads, SchedulingCommands commands, Idempotency idempotency) {
        this.reads = reads;
        this.commands = commands;
        this.idempotency = idempotency;
    }

    @GetMapping
    public List<ReadModels.Session> search(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String examCode,
                                           @RequestParam(required = false) Integer examId,
                                           @RequestParam(required = false) String centerCode,
                                           @RequestParam(required = false) Integer testCenterId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startsAfter,
                                           @RequestParam(required = false) Integer excludeSessionId,
                                           @RequestParam(required = false) Integer minSeatsAvailable,
                                           @RequestParam(required = false) String include,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(required = false) Integer limit,
                                           @RequestParam(required = false) Integer offset) {
        return reads.sessions(new ReadModels.SessionQuery(status, examCode, examId, centerCode, testCenterId, from, to, startsAfter,
                excludeSessionId, minSeatsAvailable, sort, limit, offset));
    }

    @GetMapping("/summary")
    public List<ReadModels.CenterSummary> summary(@RequestParam(defaultValue = "testCenter") String groupBy) {
        if (!"testCenter".equals(groupBy)) {
            throw ApiException.badRequest("VALIDATION", "groupBy must be testCenter");
        }
        return reads.summaryByTestCenter();
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ReadModels.Session> get(@PathVariable int sessionId) {
        ReadModels.Session s = reads.requireSession(sessionId);
        return ResponseEntity.ok().eTag(s.etag()).body(s);
    }

    /** Contract addition: create a session (scheduling admin / import tooling). */
    @PostMapping
    public ResponseEntity<ReadModels.Session> create(@RequestBody CreateSessionRequest r) {
        if (r.examId() == null || r.testCenterId() == null || r.roomId() == null || r.startsAt() == null || r.capacity() == null) {
            throw ApiException.badRequest("VALIDATION", "examId, testCenterId, roomId, startsAt and capacity are required");
        }
        int id = commands.createSession(new SchedulingCommands.NewSession(r.examId(), r.testCenterId(), r.roomId(), r.startsAt(),
                r.capacity(), r.accommodationCapacity(), r.seatsAvailable(), r.status()));
        ReadModels.Session s = reads.requireSession(id);
        return ResponseEntity.status(HttpStatus.CREATED).eTag(s.etag()).body(s);
    }

    @PostMapping("/{sessionId}/capacity")
    public ResponseEntity<?> capacity(@PathVariable int sessionId, @RequestBody CapacityRequest body,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                      @RequestHeader(value = "X-Client-Id", required = false) String client,
                                      @RequestHeader(value = "X-Actor", required = false) String actor,
                                      @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        if (body == null || body.newCapacity() == null) {
            throw ApiException.badRequest("VALIDATION", "newCapacity is required");
        }
        return idempotency.execute(key, client, "capacity:" + sessionId + ":" + body.newCapacity() + ":" + ifMatch,
                () -> ResponseEntity.ok(commands.changeCapacity(sessionId, body.newCapacity(), actor, ifMatch)));
    }

    @GetMapping("/capacity-audit")
    public ReadModels.AuditPage capacityAudit(@RequestParam(required = false) Integer testCenterId,
                                              @RequestParam(required = false) Integer sessionId,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "50") int pageSize) {
        // usp_GetCapacityAudit: @From inclusive, @To exclusive, both DATETIME (a bare date means midnight).
        return reads.capacityAudit(testCenterId, sessionId, dateTime("from", from), dateTime("to", to), page, pageSize);
    }

    private static java.time.LocalDateTime dateTime(String name, String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return v.length() == 10 ? java.time.LocalDate.parse(v).atStartOfDay() : java.time.LocalDateTime.parse(v);
        } catch (java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest("VALIDATION", name + " must be yyyy-MM-dd'T'HH:mm:ss (or yyyy-MM-dd)");
        }
    }

    @GetMapping("/{sessionId}/waitlist")
    public List<ReadModels.WaitlistEntry> waitlist(@PathVariable int sessionId) {
        return reads.waitlist(sessionId);
    }

    @GetMapping("/{sessionId}/roster")
    public ResponseEntity<?> roster(@PathVariable int sessionId, @RequestParam(defaultValue = "json") String format) {
        ReadModels.Roster r = reads.roster(sessionId);
        if (!"csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok(r);
        }
        StringBuilder csv = new StringBuilder("CandidateNumber,LastName,FirstName,Email,BookingStatus,AccommodationCode,Channel,BookedAt,WaitlistPosition\r\n");
        for (ReadModels.RosterLine l : r.lines()) {
            csv.append(String.join(",", List.of(c(l.candidateNumber()), c(l.lastName()), c(l.firstName()), c(l.email()),
                    c(l.bookingStatus()), c(l.accommodationCode()), c(l.channel()), c(l.bookedAt() == null ? null : l.bookedAt().toString()),
                    l.waitlistPosition() == null ? "" : l.waitlistPosition().toString()))).append("\r\n");
        }
        return ResponseEntity.ok().contentType(new MediaType("text", "csv"))
                .header("Content-Disposition", "attachment; filename=roster-" + sessionId + ".csv").body(csv.toString());
    }

    private static String c(String v) {
        if (v == null) {
            return "";
        }
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v; // neutralise spreadsheet formulas (CSV injection)
        }
        return v.contains(",") || v.contains("\"") || v.contains("\n") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

}
