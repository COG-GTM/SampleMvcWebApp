package com.northwind.scheduling.notification;

import org.springframework.stereotype.Component;

import com.northwind.scheduling.outbox.OutboxEvent;

/**
 * Hands CandidateCapacityImpactNotified outbox events to the {@link NotificationPort} (MBA-3831). Called by the
 * outbox relay; a redelivered event (relay retry after a crash) is deduplicated by the port on the event id.
 */
@Component
public class NotificationDelivery {

    private final NotificationPort port;

    public NotificationDelivery(NotificationPort port) {
        this.port = port;
    }

    /** @return true if delivered now, false if the port had already handled this eventId */
    public boolean deliver(OutboxEvent e) {
        CandidateCapacityImpactNotified n = e.payload().notification();
        if (e.type().projectsToLegacy() || n == null) {
            throw new IllegalStateException(e.type() + " seq=" + e.seq() + " is not a notification event");
        }
        if (!n.eventId().equals(e.eventId())) {
            throw new IllegalStateException("notification eventId " + n.eventId() + " != outbox event_id " + e.eventId());
        }
        return port.notify(n);
    }
}
