package com.northwind.assessment.scheduling.observability;

import java.time.LocalDateTime;

/** Reads the outbox backlog for {@link OutboxMetrics}. */
@FunctionalInterface
public interface OutboxBacklogReader {

    record Backlog(long pending, LocalDateTime oldestCreatedAt) {
        static final Backlog EMPTY = new Backlog(0, null);
    }

    Backlog read();
}
