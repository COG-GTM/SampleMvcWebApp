package com.northwind.scheduling.outbox;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxEvent(long seq, UUID eventId, OutboxEventType type, int aggregateId, OutboxPayload payload,
                          String actor, LocalDateTime createdAt) {
}
