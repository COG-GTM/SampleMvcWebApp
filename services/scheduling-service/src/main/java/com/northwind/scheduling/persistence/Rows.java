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
                          int waitlisted, int held, int accommodationHeld) {
        public SessionCapacity seats() {
            return new SessionCapacity(capacity, accommodationCapacity, occupied, accommodationUsed, waitlisted, held,
                    accommodationHeld);
        }
    }

    public record Booking(int bookingId, int sessionId, int candidateId, String candidateNumber, String candidateName,
                          int examId, String examCode, String examTitle, String centerCode, String centerName,
                          LocalDateTime sessionStartsAt, BookingStatus status, String accommodationCode, String channel,
                          String externalRef, LocalDateTime bookedAt, LocalDateTime cancelledAt, String cancelReason,
                          Integer rescheduledFrom, Integer waitlistPosition, OfferRef offer) {
    }

    /** offer: the pending seat offer of this entry, null when none (MBA-3829) */
    public record WaitlistEntry(int position, int bookingId, String candidateNumber, String candidateName,
                                String accommodationCode, LocalDateTime addedAt, OfferRef offer) {
    }

    /** Latest seat offer of a booking (MBA-3829). */
    public record OfferRef(int offerId, String status, LocalDateTime offeredAt, LocalDateTime expiresAt) {
    }

    public record Offer(int offerId, int sessionId, int bookingId, int candidateId, String candidateNumber, int position,
                        String status, LocalDateTime offeredAt, LocalDateTime expiresAt, LocalDateTime resolvedAt,
                        String resolution, String accommodationCode) {
    }

    public record CapacityChangeRow(int id, int sessionId, int oldCapacity, int newCapacity, int demotedBookings,
                                    String reason, String changedBy, LocalDateTime changedAt) {
    }
}
