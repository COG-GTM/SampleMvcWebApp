package com.northwind.assessment.booking.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.northwind.assessment.booking.client.SchedulingClient;
import com.northwind.assessment.booking.client.SchedulingException;
import com.northwind.assessment.booking.dao.BookingDao;
import com.northwind.assessment.booking.dao.CandidateDao;
import com.northwind.assessment.booking.dao.SessionDao;
import com.northwind.assessment.booking.model.BookingInfo;
import com.northwind.assessment.booking.model.BookingRequest;
import com.northwind.assessment.booking.model.BookingResponse;
import com.northwind.assessment.booking.model.Candidate;
import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.model.SessionInfo;
import com.northwind.assessment.booking.util.DateUtils;
import com.northwind.assessment.booking.web.ApiException;

/**
 * Partner bookings. Every write goes through scheduling-service (POST /bookings, /cancel, /reschedule), the single
 * writer of Booking/Waitlist/SeatsAvailable. The partner pre-checks run here in the legacy order and with the
 * legacy partner messages (session OPEN, duplicate, accommodation seat, retake eligibility), because the service
 * does not check retake eligibility (HB-08). The service re-checks the first three under its session lock; if it
 * rejects, its error code is mapped back to the same partner message.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    static final List<String> ACCOMMODATION_CODES = Arrays.asList("ET25", "ET50", "SR", "RB");
    static final List<String> EXTRA_TIME_CODES = Arrays.asList("ET25", "ET50");

    @Autowired
    private BookingDao bookingDao;

    @Autowired
    private SessionDao sessionDao;

    @Autowired
    private CandidateDao candidateDao;

    @Autowired
    private RetakeEligibilityService retakeEligibilityService;

    @Autowired
    private SchedulingClient scheduling;

    @Value("${booking.channel:PARTNER}")
    private String channel;

    @Value("${booking.audit-actor:booking-api}")
    private String auditActor;

    public BookingResponse book(BookingRequest req) {
        if (req == null || req.getSessionId() == null || StringUtils.isBlank(req.getCandidateNumber())) {
            throw ApiException.badRequest("VALIDATION", "sessionId and candidateNumber are required");
        }
        String acc = normaliseAccommodation(req.getAccommodationCode());
        if (req.getExternalRef() != null && req.getExternalRef().length() > 40) {
            throw ApiException.badRequest("VALIDATION", "externalRef is limited to 40 characters");
        }
        Candidate cand = candidateDao.findByNumber(req.getCandidateNumber().trim());
        if (cand == null) {
            throw ApiException.notFound("CANDIDATE_NOT_FOUND", "Candidate " + req.getCandidateNumber() + " not found");
        }
        int sessionId = req.getSessionId().intValue();
        JsonNode session = sessionDao.findRaw(sessionId);
        if (session == null) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session " + sessionId + " not found");
        }
        precheck(cand, session, acc);

        JsonNode result;
        try {
            result = scheduling.book(sessionId, cand.getCandidateId(), acc, channel, req.getExternalRef(), auditActor);
        } catch (SchedulingException e) {
            throw translate(e, cand, sessionId, null);
        }
        int bookingId = result.path("bookingId").asInt();
        String status = result.path("status").asText();
        log.info("Partner booking {} for {} on session {} -> {}", bookingId, cand.getCandidateNumber(), sessionId, status);
        return new BookingResponse(bookingId, status);
    }

    public void cancel(int bookingId, String reason) {
        BookingInfo b = bookingDao.findById(bookingId);
        if (b == null) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        if (!"BOOKED".equals(b.getStatus()) && !"WAITLISTED".equals(b.getStatus())) {
            throw ApiException.conflict("NOT_CANCELLABLE", "Booking " + bookingId + " is " + b.getStatus());
        }
        try {
            scheduling.cancel(bookingId, StringUtils.defaultIfBlank(reason, "Cancelled by partner"), auditActor);
        } catch (SchedulingException e) {
            if (e.getHttpStatus() == 404) {
                throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
            }
            if (e.getHttpStatus() == 409) {
                throw ApiException.conflict("NOT_CANCELLABLE", e.getMessage());
            }
            throw e;
        }
    }

    /**
     * Partner reschedule (HB-11): same checks and messages as before, then one POST /bookings/{id}/reschedule
     * with channel=PARTNER, so the new booking keeps PARTNER and the externalRef. The service cancels and books
     * atomically; any rejection leaves the original booking untouched, as the old @Transactional did.
     */
    public BookingResponse reschedule(int bookingId, Integer newSessionId) {
        if (newSessionId == null) {
            throw ApiException.badRequest("VALIDATION", "newSessionId is required");
        }
        BookingInfo old = bookingDao.findById(bookingId);
        if (old == null) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        JsonNode oldSession = sessionDao.findRaw(old.getSessionId());
        JsonNode target = sessionDao.findRaw(newSessionId.intValue());
        if (target == null) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session " + newSessionId + " not found");
        }
        if (oldSession == null || oldSession.path("examId").asInt() != target.path("examId").asInt()) {
            throw ApiException.conflict("DIFFERENT_EXAM", "Booking " + bookingId + " cannot be moved to session "
                    + newSessionId + " (different exam)");
        }
        if (newSessionId.intValue() == old.getSessionId()) {
            throw ApiException.conflict("SAME_SESSION", "Booking " + bookingId + " is already on session " + newSessionId);
        }
        if (!"BOOKED".equals(old.getStatus()) && !"WAITLISTED".equals(old.getStatus())) {
            throw ApiException.conflict("NOT_CANCELLABLE", "Booking " + bookingId + " is " + old.getStatus());
        }
        Candidate cand = candidateDao.findByNumber(old.getCandidateNumber());
        if (cand == null) {
            throw ApiException.notFound("CANDIDATE_NOT_FOUND", "Candidate " + old.getCandidateNumber() + " not found");
        }
        precheck(cand, target, normaliseAccommodation(old.getAccommodationCode()));

        JsonNode result;
        try {
            result = scheduling.reschedule(bookingId, newSessionId.intValue(), channel, auditActor);
        } catch (SchedulingException e) {
            throw translate(e, cand, newSessionId.intValue(), Integer.valueOf(bookingId));
        }
        return new BookingResponse(result.path("bookingId").asInt(), result.path("status").asText());
    }

    // -------------------------------------------------------------------------

    private static String normaliseAccommodation(String code) {
        String acc = StringUtils.trimToNull(code);
        if (acc == null) {
            return null;
        }
        acc = acc.toUpperCase();
        if (!ACCOMMODATION_CODES.contains(acc)) {
            throw ApiException.badRequest("VALIDATION", "Unknown accommodationCode " + acc);
        }
        return acc;
    }

    /** Legacy partner check order: OPEN, duplicate, accommodation seat, then retake eligibility. */
    private void precheck(Candidate cand, JsonNode session, String acc) {
        int sessionId = session.path("sessionId").asInt();
        if (!"OPEN".equals(session.path("status").asText())) {
            throw sessionNotOpen(sessionId);
        }
        if (bookingDao.hasActiveBooking(sessionId, cand.getCandidateId())) {
            throw duplicate(cand, sessionId);
        }
        if (acc != null && EXTRA_TIME_CODES.contains(acc)
                && bookingDao.countAccommodationSeatsUsed(sessionId) >= session.path("accommodationCapacity").asInt()) {
            throw noAccommodationSeat(sessionId);
        }
        // retake rules are checked against the date the candidate would sit the exam
        Date sitting = parseDateTime(session.path("startsAt").asText());
        EligibilityResult elig = retakeEligibilityService.check(cand.getCandidateId(), session.path("examId").asInt(), sitting);
        if (!elig.isEligible()) {
            String msg = "Candidate " + cand.getCandidateNumber() + " is not eligible to book "
                    + session.path("examCode").asText() + " (" + elig.getReason() + ")";
            if (elig.getEarliestRetakeDate() != null) {
                msg += ", earliest retake date " + elig.getEarliestRetakeDate();
            }
            throw ApiException.conflict("NOT_ELIGIBLE", msg);
        }
    }

    /** Maps a service rejection (e.g. lost a race after the pre-check) to the partner error it always returned. */
    private RuntimeException translate(SchedulingException e, Candidate cand, int sessionId, Integer bookingId) {
        String code = e.getError() == null ? "" : e.getError();
        if ("SESSION_NOT_OPEN".equals(code)) {
            return sessionNotOpen(sessionId);
        }
        if ("DUPLICATE_BOOKING".equals(code)) {
            return duplicate(cand, sessionId);
        }
        if ("NO_ACCOMMODATION_SEAT".equals(code)) {
            return noAccommodationSeat(sessionId);
        }
        if ("SESSION_NOT_FOUND".equals(code)) {
            return ApiException.notFound("SESSION_NOT_FOUND", "Session " + sessionId + " not found");
        }
        if ("CANDIDATE_NOT_FOUND".equals(code)) {
            return ApiException.notFound("CANDIDATE_NOT_FOUND", "Candidate " + cand.getCandidateNumber() + " not found");
        }
        if (bookingId != null && "BOOKING_NOT_FOUND".equals(code)) {
            return ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        if (bookingId != null && ("NOT_CANCELLABLE".equals(code) || "DIFFERENT_EXAM".equals(code)
                || "SAME_SESSION".equals(code))) {
            return ApiException.conflict(code, e.getMessage());
        }
        return e;
    }

    private static ApiException sessionNotOpen(int sessionId) {
        return ApiException.conflict("SESSION_NOT_OPEN", "Session " + sessionId + " is not open for booking");
    }

    private static ApiException duplicate(Candidate cand, int sessionId) {
        return ApiException.conflict("DUPLICATE_BOOKING",
                "Candidate " + cand.getCandidateNumber() + " already has an active booking on session " + sessionId);
    }

    private static ApiException noAccommodationSeat(int sessionId) {
        return ApiException.conflict("NO_ACCOMMODATION_SEAT", "No accommodation seat available on session " + sessionId);
    }

    static Date parseDateTime(String s) {
        try {
            return new SimpleDateFormat(DateUtils.ISO_DATE_TIME).parse(s);
        } catch (ParseException e) {
            throw new IllegalStateException("scheduling-service returned an unparseable startsAt: " + s, e);
        }
    }
}
