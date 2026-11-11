package com.northwind.assessment.scheduling.web;

import com.northwind.assessment.scheduling.domain.RuleViolation;
import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.importer.ImportSummary;
import com.northwind.assessment.scheduling.importer.SnapshotImporter;
import com.northwind.assessment.scheduling.persistence.BookingRepository;
import com.northwind.assessment.scheduling.persistence.ReferenceRepository;
import com.northwind.assessment.scheduling.persistence.SeatOfferRepository;
import com.northwind.assessment.scheduling.persistence.Views;
import com.northwind.assessment.scheduling.service.NotFoundException;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Stream;
import com.northwind.assessment.scheduling.config.SchedulingProperties;
import com.northwind.assessment.scheduling.reconciliation.ReconciliationService;
import com.northwind.assessment.scheduling.relay.OutboxRelay;
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
    private final ReconciliationService reconciliation;
    private final OutboxRelay relay;
    private final SchedulingProperties props;
    private final SeatOfferRepository offers;

    public InternalController(ReferenceRepository reference, BookingRepository bookings, SnapshotImporter importer,
                              ReconciliationService reconciliation, OutboxRelay relay, SchedulingProperties props,
                              SeatOfferRepository offers) {
        this.offers = offers;
        this.reference = reference;
        this.bookings = bookings;
        this.importer = importer;
        this.reconciliation = reconciliation;
        this.relay = relay;
        this.props = props;
    }

    /** MBA-3823: Scheduling vs AssessmentDelivery per session; {@code writeReport=true} also writes the markdown report. */
    @GetMapping("/internal/reconciliation")
    public ReconciliationService.Report reconcile(@RequestParam(defaultValue = "false") boolean writeReport) {
        return reconciliation.reconcile(writeReport);
    }

    /** Outbox relay backlog and lag (ms between outbox commit and legacy projection). */
    @GetMapping("/internal/relay/status")
    public ReconciliationService.RelayStatus relayStatus() {
        return reconciliation.relayStatus();
    }

    /** Drains the outbox now (the scheduled relay does the same every poll interval). */
    @PostMapping("/internal/relay/run")
    public OutboxRelay.RunResult runRelay() {
        return relay.drain(props.relay().batchSize());
    }

    /** Integration contract item 3: exactly one of sessionId, bookingId, centerId, centerCode. */
    @GetMapping("/internal/routing/resolve")
    public ResponseEntity<Json.Route> resolve(@RequestParam(required = false) String sessionId,
                                              @RequestParam(required = false) String bookingId,
                                              @RequestParam(required = false) String centerId,
                                              @RequestParam(required = false) String centerCode,
                                              @RequestParam(required = false) String offerId) {
        long given = Stream.of(sessionId, bookingId, centerId, centerCode, offerId).filter(v -> v != null && !v.isBlank())
                .count();
        if (given != 1) {
            throw new SchedulingRuleException(RuleViolation.VALIDATION,
                    "Exactly one of sessionId, bookingId, centerId, centerCode, offerId is required");
        }
        CacheControl cacheable = CacheControl.maxAge(Duration.ofSeconds(60));
        if (offerId != null && !offerId.isBlank()) {
            long id = parse("offerId", offerId);
            long offerBooking = offers.find(id).orElseThrow(() -> new NotFoundException("NOT_FOUND",
                    "Seat offer " + id + " not found")).bookingId();
            Views.RouteView r = bookings.routeOfBooking(offerBooking)
                    .orElseThrow(() -> new NotFoundException("NOT_FOUND", "Booking " + offerBooking + " not found"));
            return ResponseEntity.ok(Json.Route.of(r));
        }
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
