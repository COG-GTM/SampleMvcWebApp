package com.northwind.assessment.scheduling.web;

import com.northwind.assessment.scheduling.domain.RuleViolation;
import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.importer.ImportSummary;
import com.northwind.assessment.scheduling.importer.SnapshotImporter;
import com.northwind.assessment.scheduling.persistence.BookingRepository;
import com.northwind.assessment.scheduling.persistence.ReferenceRepository;
import com.northwind.assessment.scheduling.persistence.Views;
import com.northwind.assessment.scheduling.service.NotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the routing gateway (M3) and for bootstrapping from AssessmentDelivery. */
@RestController
public class InternalController {

    private final ReferenceRepository reference;
    private final BookingRepository bookings;
    private final SnapshotImporter importer;

    public InternalController(ReferenceRepository reference, BookingRepository bookings, SnapshotImporter importer) {
        this.reference = reference;
        this.bookings = bookings;
        this.importer = importer;
    }

    /** Integration contract item 3: exactly one of sessionId, bookingId, centerId, centerCode. */
    @GetMapping("/internal/routing/resolve")
    public ResponseEntity<Json.Route> resolve(@RequestParam(required = false) String sessionId,
                                              @RequestParam(required = false) String bookingId,
                                              @RequestParam(required = false) String centerId,
                                              @RequestParam(required = false) String centerCode) {
        long given = Stream.of(sessionId, bookingId, centerId, centerCode).filter(v -> v != null && !v.isBlank()).count();
        if (given != 1) {
            throw new SchedulingRuleException(RuleViolation.VALIDATION,
                    "Exactly one of sessionId, bookingId, centerId, centerCode is required");
        }
        CacheControl cacheable = CacheControl.maxAge(Duration.ofSeconds(60));
        if (bookingId != null && !bookingId.isBlank()) {
            long id = parse("bookingId", bookingId);
            Views.RouteView r = bookings.routeOfBooking(id)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Booking " + id + " not found"));
            return ResponseEntity.ok(Json.Route.of(r));
        }
        if (sessionId != null && !sessionId.isBlank()) {
            int id = parseInt("sessionId", sessionId);
            Views.RouteView r = bookings.routeOfSession(id)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Session " + id + " not found"));
            return ResponseEntity.ok().cacheControl(cacheable).body(Json.Route.of(r));
        }
        Optional<Views.CenterView> center = centerId != null && !centerId.isBlank()
                ? reference.centerById(parseInt("centerId", centerId))
                : reference.centerByCode(centerCode.trim());
        Views.CenterView c = center.orElseThrow(() -> new NotFoundException("NOT_FOUND",
                "Test center " + (centerId != null && !centerId.isBlank() ? centerId : centerCode) + " not found"));
        return ResponseEntity.ok().cacheControl(cacheable).body(new Json.Route(c.centerCode(), c.centerId(), null, null, null));
    }

    /** Integration contract item 4: idempotent bootstrap import (all centers when centerCode is omitted). */
    @PostMapping("/internal/import/sessions")
    public ImportSummary importSessions(@RequestParam(required = false) String centerCode) {
        return importer.importSessions(centerCode);
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new SchedulingRuleException(RuleViolation.VALIDATION, name + " must be a number in the int range");
        }
    }

    private static long parse(String name, String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw new SchedulingRuleException(RuleViolation.VALIDATION, name + " must be a number");
        }
    }
}
