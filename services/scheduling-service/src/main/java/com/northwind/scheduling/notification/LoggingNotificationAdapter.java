package com.northwind.scheduling.notification;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stub {@link NotificationPort} (MBA-3831): no email/SMS. Each notification is written as one structured log line
 * and kept in memory (newest {@code scheduling.notifications.stub.max-records}) for
 * {@code GET /internal/notifications}. A repeated eventId is ignored.
 */
@Component
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    private final Map<UUID, CandidateCapacityImpactNotified> sent;

    public LoggingNotificationAdapter(@Value("${scheduling.notifications.stub.max-records:10000}") int maxRecords) {
        this.sent = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, CandidateCapacityImpactNotified> eldest) {
                return size() > maxRecords;
            }
        };
    }

    @Override
    public synchronized boolean notify(CandidateCapacityImpactNotified n) {
        if (sent.containsKey(n.eventId())) {
            log.debug("duplicate notification event {} ignored", n.eventId());
            return false;
        }
        sent.put(n.eventId(), n);
        log.info("candidate notification {}", n.impact(), kv("event", "CandidateCapacityImpactNotified"),
                kv("eventId", n.eventId()), kv("impact", n.impact()), kv("candidateNumber", n.candidateNumber()),
                kv("bookingId", n.bookingId()), kv("sessionId", n.sessionId()), kv("centerCode", n.centerCode()),
                kv("examCode", n.examCode()), kv("startsAt", String.valueOf(n.startsAt())),
                kv("oldCapacity", n.oldCapacity()), kv("newCapacity", n.newCapacity()),
                kv("previousStatus", n.previousStatus()), kv("newStatus", n.newStatus()));
        return true;
    }

    /** Recorded notifications in delivery order; candidateNumber null/blank = all. */
    public synchronized List<CandidateCapacityImpactNotified> sent(String candidateNumber) {
        List<CandidateCapacityImpactNotified> out = new ArrayList<>();
        for (CandidateCapacityImpactNotified n : sent.values()) {
            if (candidateNumber == null || candidateNumber.isBlank()
                    || n.candidateNumber().equalsIgnoreCase(candidateNumber.trim())) {
                out.add(n);
            }
        }
        return out;
    }
}
