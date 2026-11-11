package com.northwind.scheduling.persistence;

import java.time.LocalDateTime;

import com.northwind.scheduling.domain.BookingStatus;
import com.northwind.scheduling.domain.SessionCapacity;
import com.northwind.scheduling.domain.SessionStatus;

/** Read models of the Scheduling DB. */
public final class Rows {
    private Rows() {
    }

    public record Center(int id, String code, String name, String city, String region, String timeZone) {
    }

    public record Exam(int id, String code, String title, int durationMinutes, int retakeWaitDays) {
    }

    public record Candidate(int id, String number, String firstName, String lastName, String email,
                            String defaultAccommodationCode) {
    }

    public record Session(int sessionId, int examId, String examCode, String examTitle, int centerId, String centerCode,
                          String centerName, String roomName, LocalDateTime startsAt, int capacity,
                          int accommodationCapacity, SessionStatus status, int occupied, int accommodationUsed,
                          int waitlisted) {
        public SessionCapacity seats() {
            return new SessionCapacity(capacity, accommodationCapacity, occupied, accommodationUsed, waitlisted);
        }
    }

    public record Booking(int bookingId, int sessionId, int candidateId, String candidateNumber, String candidateName,
                          int examId, String examCode, String examTitle, String centerCode, String centerName,
                          LocalDateTime sessionStartsAt, BookingStatus status, String accommodationCode, String channel,
                          String externalRef, LocalDateTime bookedAt, LocalDateTime cancelledAt, String cancelReason,
                          Integer rescheduledFrom, Integer waitlistPosition) {
    }

    public record WaitlistEntry(int position, int bookingId, String candidateNumber, String candidateName,
                                String accommodationCode, LocalDateTime addedAt) {
    }

    public record CapacityChangeRow(int id, int sessionId, int oldCapacity, int newCapacity, int demotedBookings,
                                    String reason, String changedBy, LocalDateTime changedAt) {
    }
}
