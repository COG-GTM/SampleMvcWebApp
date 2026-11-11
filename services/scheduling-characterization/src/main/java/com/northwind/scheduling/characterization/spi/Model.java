package com.northwind.scheduling.characterization.spi;

import java.time.LocalDateTime;

/** State records read back from a target. Column names follow db/schema/010_tables.sql. */
public final class Model {
    private Model() {
    }

    public record ExamSessionState(int sessionId, int examId, int capacity, int accommodationCapacity,
                                   int seatsAvailable, String status, LocalDateTime lastModified) {
    }

    public record BookingState(int bookingId, int sessionId, int candidateId, String status, String accommodationCode,
                               String channel, String externalRef, LocalDateTime bookedAt, LocalDateTime cancelledAt,
                               String cancelReason) {
    }

    public record WaitlistEntry(int sessionId, int candidateId, int bookingId, int position) {
    }

    public record AuditEntry(String entityName, int entityId, String action, String detail, String actor) {
    }

    /** One row of db/checks/seat-drift.sql: Drift = (Capacity - BOOKED - ATTENDED) - SeatsAvailable. */
    public record SeatDrift(int sessionId, int capacity, int seatsAvailable, int activeBookings, int drift) {
    }

    /**
     * Fixture session. {@code seatsAvailable == null} means "= capacity" (an empty, drift-free session).
     */
    public record SessionSpec(int examId, int capacity, int accommodationCapacity, Integer seatsAvailable,
                              String status, LocalDateTime startsAt) {
        public static SessionSpec open(int examId, int capacity) {
            return new SessionSpec(examId, capacity, 0, null, "OPEN", null);
        }

        public SessionSpec withAccommodation(int accCap) {
            return new SessionSpec(examId, capacity, accCap, seatsAvailable, status, startsAt);
        }

        public SessionSpec withSeatsAvailable(int seats) {
            return new SessionSpec(examId, capacity, accommodationCapacity, seats, status, startsAt);
        }

        public SessionSpec withStatus(String s) {
            return new SessionSpec(examId, capacity, accommodationCapacity, seatsAvailable, s, startsAt);
        }
    }
}
