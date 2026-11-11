package com.northwind.assessment.scheduling.web;

import com.northwind.assessment.scheduling.domain.EligibilityDecision;
import com.northwind.assessment.scheduling.domain.SeatFormulas;
import com.northwind.assessment.scheduling.persistence.Views;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** /api/v2 JSON shapes. Field names follow booking-api v1 SessionInfo / BookingInfo (integration contract item 5). */
public final class Json {

    /** booking-api v1 DateUtils.ISO_DATE_TIME. */
    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private Json() {
    }

    static String fmt(LocalDateTime t) {
        return t == null ? null : DATE_TIME.format(t);
    }

    static String fmt(LocalDate d) {
        return d == null ? null : d.toString();
    }

    public record Session(int sessionId, String examCode, String examTitle, String centerCode, String centerName,
                          String roomName, String startsAt, int capacity, int accommodationCapacity, int seatsAvailable,
                          String status, Integer bookedSeats, int accommodationUsed, int accommodationSeatsAvailable,
                          int waitlistCount) {
        static Session of(Views.SessionView v) {
            return new Session(v.sessionId(), v.examCode(), v.examTitle(), v.centerCode(), v.centerName(), v.roomName(),
                    fmt(v.startsAt()), v.capacity(), v.accommodationCapacity(),
                    SeatFormulas.seatsAvailable(v.capacity(), v.bookedSeats()), v.status(), v.bookedSeats(),
                    v.accommodationUsed(),
                    SeatFormulas.accommodationSeatsAvailable(v.accommodationCapacity(), v.accommodationUsed()),
                    v.waitlistCount());
        }
    }

    public record Booking(long bookingId, int sessionId, String candidateNumber, String examCode, String sessionStartsAt,
                          String status, String accommodationCode, String channel, String externalRef, String bookedAt,
                          String cancelledAt, String cancelReason, String origin, Integer legacyBookingId,
                          Integer waitlistPosition) {
        static Booking of(Views.BookingView v) {
            return new Booking(v.bookingId(), v.sessionId(), v.candidateNumber(), v.examCode(), fmt(v.sessionStartsAt()),
                    v.status(), v.accommodationCode(), v.channel(), v.externalRef(), fmt(v.bookedAt()),
                    fmt(v.cancelledAt()), v.cancelReason(), v.origin(), v.legacyBookingId(), v.waitlistPosition());
        }
    }

    public record WaitlistEntry(int position, long bookingId, String candidateNumber, String candidateName, String addedAt) {
        static WaitlistEntry of(Views.WaitlistView v) {
            return new WaitlistEntry(v.position(), v.bookingId(), v.candidateNumber(), v.candidateName(), fmt(v.addedAt()));
        }
    }

    public record Eligibility(boolean eligible, String reason, String earliestRetakeDate) {
        static Eligibility of(EligibilityDecision d) {
            return new Eligibility(d.eligible(), d.reason(), fmt(d.earliestRetakeDate()));
        }
    }

    public record Center(int centerId, String centerCode, String name, String city, String region, String timeZone) {
        static Center of(Views.CenterView v) {
            return new Center(v.centerId(), v.centerCode(), v.name(), v.city(), v.region(), v.timeZone());
        }
    }

    public record Exam(String examCode, String title, int durationMinutes, int retakeWaitDays) {
        static Exam of(Views.ExamView v) {
            return new Exam(v.examCode(), v.title(), v.durationMinutes(), v.retakeWaitDays());
        }
    }

    public record Candidate(String candidateNumber, String firstName, String lastName, String defaultAccommodationCode) {
        static Candidate of(Views.CandidateView v) {
            return new Candidate(v.candidateNumber(), v.firstName(), v.lastName(), v.defaultAccommodationCode());
        }
    }

    public record CapacityRequest(Integer newCapacity, String actor) {
    }

    public record CapacityResponse(Session session, java.util.List<Long> bumpedBookingIds) {
    }

    public record RescheduleRequest(Integer newSessionId, String channel) {
    }

    public record Route(String centerCode, int centerId, Integer sessionId, Long bookingId, String bookingOrigin) {
        static Route of(Views.RouteView v) {
            return new Route(v.centerCode(), v.centerId(), v.sessionId(), v.bookingId(), v.bookingOrigin());
        }
    }
}
