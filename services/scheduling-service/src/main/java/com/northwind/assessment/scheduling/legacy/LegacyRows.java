package com.northwind.assessment.scheduling.legacy;

import java.time.LocalDateTime;

/** Row shapes read from AssessmentDelivery. Field names follow the legacy columns. */
public final class LegacyRows {

    private LegacyRows() {
    }

    public record TestCenter(int testCenterId, String centerCode, String name, String city, String region,
                             String timeZone, boolean active) {
    }

    public record Room(int roomId, int testCenterId, String roomName, int seatCount, int accommodationSeats,
                       boolean active) {
    }

    public record Exam(int examId, String examCode, String title, int durationMinutes, int passingScore,
                       int retakeWaitDays, boolean published) {
    }

    public record Candidate(int candidateId, String candidateNumber, String firstName, String lastName, String email,
                            String defaultAccommodationCode) {
    }

    /** A Result with the StartsAt of its session (the date the retake wait counts from). */
    public record Result(int resultId, int candidateId, int examId, int sessionId, LocalDateTime sessionStartsAt,
                         int score, boolean passed) {
    }

    public record ExamSession(int sessionId, int examId, int testCenterId, int roomId, LocalDateTime startsAt,
                              int capacity, int accommodationCapacity, String status, LocalDateTime lastModified) {
    }

    public record Booking(int bookingId, int sessionId, int candidateId, String status, String accommodationCode,
                          String channel, String externalRef, LocalDateTime bookedAt, LocalDateTime cancelledAt,
                          String cancelReason, LocalDateTime lastModified) {
    }

    public record Waitlist(int waitlistId, int sessionId, int candidateId, int bookingId, int position,
                           LocalDateTime addedAt) {
    }
}
