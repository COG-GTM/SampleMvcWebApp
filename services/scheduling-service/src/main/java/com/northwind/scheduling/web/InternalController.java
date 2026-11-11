package com.northwind.scheduling.web;

import com.northwind.scheduling.persistence.Rows;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.SchedulingException;
import com.northwind.scheduling.importer.ImportReport;
import com.northwind.scheduling.importer.SnapshotImporter;
import com.northwind.scheduling.service.SchedulingService;
import com.northwind.scheduling.web.Dtos.RoutingDto;

/** Operator/internal endpoints (not for candidates): snapshot import and the M3 routing stub. */
@RestController
@RequestMapping("/api/v2/internal")
public class InternalController {

    private final SnapshotImporter importer;
    private final SchedulingService service;
    private final Set<String> modernCenters;
    private final String defaultTarget;

    public InternalController(SnapshotImporter importer, SchedulingService service,
                              @Value("${scheduling.routing.modern-centers:}") String modernCenters,
                              @Value("${scheduling.routing.default-target:legacy}") String defaultTarget) {
        this.importer = importer;
        this.service = service;
        this.modernCenters = Arrays.stream(modernCenters.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .map(String::toUpperCase).collect(Collectors.toSet());
        this.defaultTarget = defaultTarget;
    }

    /** mode=merge (default) refreshes snapshots and adds new sessions; mode=reset re-copies the inventory (dev/replay). */
    @PostMapping("/import/sessions")
    public ImportReport importSessions(@RequestParam(required = false) String mode) {
        SnapshotImporter.Mode m;
        try {
            m = SnapshotImporter.Mode.parse(mode);
        } catch (IllegalArgumentException e) {
            throw new SchedulingException(ErrorCode.VALIDATION, "mode must be merge or reset");
        }
        return importer.run(m);
    }

    /**
     * Stub for the M3 per-test-center routing gateway: which backend serves a centre. Centres listed in
     * SCHEDULING_ROUTING_MODERN_CENTERS ("*" = all) resolve to "modern", the rest to the default target.
     * The centre can be given directly (code or id) or via a session/booking/offer id; the M3 routing-gateway
     * (services/routing-gateway) only uses the resolved centerCode and applies its own allow-list.
     */
    @GetMapping("/routing/resolve")
    public RoutingDto resolve(@RequestParam(required = false) String centerCode,
                              @RequestParam(required = false) Integer sessionId,
                              @RequestParam(required = false) Integer bookingId,
                              @RequestParam(required = false) Integer centerId,
                              @RequestParam(required = false) Integer offerId) {
        String code = centerCode;
        if ((code == null || code.isBlank()) && centerId != null) {
            code = service.centers().stream().filter(c -> c.id() == centerId).map(Rows.Center::code).findFirst()
                    .orElseThrow(() -> new SchedulingException(ErrorCode.CENTER_NOT_FOUND, "Center " + centerId + " not found"));
        }
        if ((code == null || code.isBlank()) && sessionId != null) {
            code = service.session(sessionId).centerCode();
        }
        if ((code == null || code.isBlank()) && bookingId != null) {
            code = service.booking(bookingId).centerCode();
        }
        if ((code == null || code.isBlank()) && offerId != null) {
            code = service.session(service.offer(offerId).sessionId()).centerCode();
        }
        if (code == null || code.isBlank()) {
            throw new SchedulingException(ErrorCode.VALIDATION, "centerCode, centerId, sessionId, bookingId or offerId is required");
        }
        String c = service.center(code.trim()).code();
        if (modernCenters.contains("*") || modernCenters.contains(c.toUpperCase())) {
            return new RoutingDto(c, "modern", "listed in scheduling.routing.modern-centers");
        }
        return new RoutingDto(c, defaultTarget, "default target");
    }
}
