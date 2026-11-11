package com.northwind.scheduling.notification;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * MBA-3831 test/operator view of the notification stub, outside /api/v2 and the public OpenAPI spec (like
 * /internal/reconciliation); the routing gateway does not route /internal/** to scheduling-service. Lookups are per
 * candidate only: there is deliberately no bulk listing of other candidates' notifications.
 */
@RestController
public class NotificationController {

    private final LoggingNotificationAdapter stub;

    public NotificationController(LoggingNotificationAdapter stub) {
        this.stub = stub;
    }

    @GetMapping("/internal/notifications")
    public List<CandidateCapacityImpactNotified> notifications(@RequestParam String candidateNumber) {
        if (candidateNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidateNumber is required");
        }
        return stub.sent(candidateNumber);
    }
}
