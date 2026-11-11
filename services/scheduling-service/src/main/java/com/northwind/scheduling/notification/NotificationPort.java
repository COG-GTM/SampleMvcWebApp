package com.northwind.scheduling.notification;

/**
 * Outbound candidate notifications (MBA-3831). The outbox relay calls it once per CandidateCapacityImpactNotified
 * event and may call it again for the same event after a retry, so implementations must be idempotent on
 * {@link CandidateCapacityImpactNotified#eventId()}.
 */
public interface NotificationPort {

    /** @return true if the notification was sent now, false if this eventId was already handled */
    boolean notify(CandidateCapacityImpactNotified notification);
}
