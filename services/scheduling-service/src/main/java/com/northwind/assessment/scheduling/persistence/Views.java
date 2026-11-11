package com.northwind.assessment.scheduling.persistence;

import java.time.LocalDateTime;

/** Read models of the Scheduling database (all joins stay inside Scheduling). */
public final class Views {

    private Views() {
    }

    public record CenterView(int centerId, String centerCode, String name, String city, String region,
                             String timeZone, boolean active) {
    }

    public record ExamView(int examId, String examCode, String title, int durationMinutes, int retakeWaitDays) {
    }

    public record CandidateView(int candidateId, String candidateNumber, String firstName, String lastName,
                                String defaultAccommodationCode) {
        public String displayName() {
            return firstName + " " + lastName;
        }
    }

    public record SessionView(int sessionId, int examId, String examCode, String examTitle, int centerId,
                              String centerCode, String centerName, String roomName, LocalDateTime startsAt,
                              int capacity, int accommodationCapacity, String status, int bookedSeats,
                              int accommodationUsed, int waitlistCount) {
    }

    public record BookingView(long bookingId, int sessionId, int candidateId, String candidateNumber, String examCode,
                              LocalDateTime sessionStartsAt, String status, String accommodationCode, String channel,
                              String externalRef, LocalDateTime bookedAt, LocalDateTime cancelledAt,
                              String cancelReason, String origin, Integer legacyBookingId, Integer waitlistPosition) {
    }

    public record WaitlistView(int position, long bookingId, String candidateNumber, String candidateName,
                               LocalDateTime addedAt) {
    }

    /** Owner of a session or booking, for the routing resolver. */
    public record RouteView(int centerId, String centerCode, Integer sessionId, Long bookingId, String bookingOrigin) {
    }
}
