package com.northwind.scheduling.notification;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MBA-3831 test/operator view of the notification stub, outside /api/v2 and the public OpenAPI spec (like
 * /internal/reconciliation); the routing gateway does not route /internal/** to scheduling-service.
 */
@RestController
public class NotificationController {

    private final LoggingNotificationAdapter stub;

    public NotificationController(LoggingNotificationAdapter stub) {
        this.stub = stub;
    }

    @GetMapping("/internal/notifications")
    public List<CandidateCapacityImpactNotified> notifications(@RequestParam(required = false) String candidateNumber) {
        return stub.sent(candidateNumber);
    }
}
