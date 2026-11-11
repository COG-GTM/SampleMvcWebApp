package com.northwind.scheduling.legacy;

import java.time.LocalDateTime;

/** Rows read from AssessmentDelivery (read-only). {@code sourceVersion} = BINARY_CHECKSUM of the source row. */
public final class LegacyRows {
    private LegacyRows() {
    }

    public record Center(int id, String code, String name, String city, String region, String timeZone,
                         boolean active, long sourceVersion) {
    }

    public record Room(int id, int centerId, String name, int seatCount, int accommodationSeats, boolean active,
                       long sourceVersion) {
    }

    public record Exam(int id, String code, String title, String owner, int durationMinutes, int passingScore,
                       int retakeWaitDays, boolean published, long sourceVersion) {
    }

    public record Candidate(int id, String number, String firstName, String lastName, String email,
                            String defaultAccommodationCode, long sourceVersion) {
    }

    public record Result(int id, int candidateId, int examId, int sessionId, LocalDateTime sessionStartsAt, int score,
                         boolean passed, LocalDateTime scoredAt, long sourceVersion) {
    }

    public record Session(int id, int examId, int centerId, int roomId, LocalDateTime startsAt, int capacity,
                          int accommodationCapacity, String status) {
    }

    public record Booking(int id, int sessionId, int candidateId, String status, String accommodationCode,
                          String channel, String externalRef, LocalDateTime bookedAt, LocalDateTime cancelledAt,
                          String cancelReason) {
    }

    public record Waitlist(int id, int sessionId, int candidateId, int bookingId, int position, LocalDateTime addedAt) {
    }
}
