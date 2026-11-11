package com.northwind.scheduling.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.northwind.scheduling.domain.EligibilityDecision;
import com.northwind.scheduling.persistence.Rows;

/** JSON shapes of /api/v2. */
public final class Dtos {
    private Dtos() {
    }

    public record CenterDto(String centerCode, String name, String city, String region, String timeZone) {
        static CenterDto of(Rows.Center c) {
            return new CenterDto(c.code(), c.name(), c.city(), c.region(), c.timeZone());
        }
    }

    public record ExamDto(String examCode, String title, int durationMinutes, int retakeWaitDays) {
        static ExamDto of(Rows.Exam e) {
            return new ExamDto(e.code(), e.title(), e.durationMinutes(), e.retakeWaitDays());
        }
    }

    public record SessionDto(int sessionId, String examCode, String examTitle, String centerCode, String centerName,
                             String roomName, LocalDateTime startsAt, String status, int capacity,
                             int accommodationCapacity, int bookedSeats, int seatsAvailable, int accommodationUsed,
                             int accommodationAvailable, int waitlistCount, int heldSeats) {
        static SessionDto of(Rows.Session s) {
            return new SessionDto(s.sessionId(), s.examCode(), s.examTitle(), s.centerCode(), s.centerName(), s.roomName(),
                    s.startsAt(), s.status().name(), s.capacity(), s.accommodationCapacity(), s.occupied(),
                    s.seats().seatsAvailable(), s.accommodationUsed(), s.seats().accommodationAvailable(), s.waitlisted(),
                    s.held());
        }
    }

    public record BookingDto(int bookingId, int sessionId, String candidateNumber, String candidateName, String examCode,
                             String examTitle, String centerCode, String centerName, LocalDateTime sessionStartsAt,
                             String status, String accommodationCode, String channel, String externalRef,
                             LocalDateTime bookedAt, LocalDateTime cancelledAt, String cancelReason,
                             Integer waitlistPosition, Integer rescheduledFrom, Integer offerId, String offerStatus,
                             LocalDateTime offerExpiresAt) {
        static BookingDto of(Rows.Booking b) {
            Rows.OfferRef o = b.offer();
            return new BookingDto(b.bookingId(), b.sessionId(), b.candidateNumber(), b.candidateName(), b.examCode(),
                    b.examTitle(), b.centerCode(), b.centerName(), b.sessionStartsAt(), b.status().name(),
                    b.accommodationCode(), b.channel(), b.externalRef(), b.bookedAt(), b.cancelledAt(), b.cancelReason(),
                    b.waitlistPosition(), b.rescheduledFrom(), o == null ? null : o.offerId(),
                    o == null ? null : o.status(), o == null ? null : o.expiresAt());
        }
    }

    /** MBA-3829: a seat offered to a waitlisted booking (ADR 0005). */
    public record OfferDto(int offerId, int sessionId, int bookingId, String candidateNumber, int position, String status,
                           LocalDateTime offeredAt, LocalDateTime expiresAt, LocalDateTime resolvedAt) {
        static OfferDto of(Rows.Offer o) {
            return new OfferDto(o.offerId(), o.sessionId(), o.bookingId(), o.candidateNumber(), o.position(), o.status(),
                    o.offeredAt(), o.expiresAt(), o.resolvedAt());
        }
    }

    /** POST /offers/{offerId}/accept|decline: the resolved offer plus the live booking and session. */
    public record OfferResultDto(OfferDto offer, BookingDto booking, SessionDto session) {
    }

    /** POST /bookings and /reschedule: legacy {bookingId, status} plus the live session and booking. */
    public record BookingResultDto(int bookingId, String status, Integer waitlistPosition, BookingDto booking,
                                   SessionDto session) {
    }

    public record CandidateDto(String candidateNumber, String firstName, String lastName, String email,
                               String defaultAccommodationCode) {
        static CandidateDto of(Rows.Candidate c) {
            return new CandidateDto(c.number(), c.firstName(), c.lastName(), c.email(), c.defaultAccommodationCode());
        }
    }

    public record EligibilityDto(String candidateNumber, String examCode, LocalDate date, boolean eligible, String reason,
                                 String message, LocalDate earliestRetakeDate, String dayCounting) {
        static EligibilityDto of(String cand, String exam, LocalDate date, EligibilityDecision d, String counting) {
            return new EligibilityDto(cand, exam, date, d.eligible(), d.reason(), d.message(), d.earliestRetakeDate(),
                    counting);
        }
    }

    public record WaitlistEntryDto(int position, int bookingId, String candidateNumber, String candidateName,
                                   String accommodationCode, LocalDateTime addedAt, Integer offerId, String offerStatus,
                                   LocalDateTime offerExpiresAt) {
        static WaitlistEntryDto of(Rows.WaitlistEntry w) {
            Rows.OfferRef o = w.offer();
            return new WaitlistEntryDto(w.position(), w.bookingId(), w.candidateNumber(), w.candidateName(),
                    w.accommodationCode(), w.addedAt(), o == null ? null : o.offerId(), o == null ? null : o.status(),
                    o == null ? null : o.expiresAt());
        }
    }

    public record CapacityResultDto(SessionDto session, int oldCapacity, int newCapacity, List<Integer> demotedBookingIds) {
    }

    public record CapacityChangeDto(int id, int oldCapacity, int newCapacity, int demotedBookings, String reason,
                                    String changedBy, LocalDateTime changedAt) {
        static CapacityChangeDto of(Rows.CapacityChangeRow r) {
            return new CapacityChangeDto(r.id(), r.oldCapacity(), r.newCapacity(), r.demotedBookings(), r.reason(),
                    r.changedBy(), r.changedAt());
        }
    }

    public record BookRequest(Integer sessionId, String candidateNumber, String accommodationCode, String externalRef,
                              String channel) {
    }

    public record RescheduleRequest(Integer newSessionId) {
    }

    public record CapacityRequest(Integer capacity, String reason) {
    }

    public record RoutingDto(String centerCode, String target, String reason) {
    }
}
