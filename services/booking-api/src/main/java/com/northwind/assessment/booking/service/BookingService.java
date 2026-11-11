package com.northwind.assessment.booking.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.northwind.assessment.booking.dao.BookingDao;
import com.northwind.assessment.booking.dao.CandidateDao;
import com.northwind.assessment.booking.dao.SessionDao;
import com.northwind.assessment.booking.model.Candidate;
import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.model.BookingInfo;
import com.northwind.assessment.booking.model.BookingRequest;
import com.northwind.assessment.booking.model.BookingResponse;
import com.northwind.assessment.booking.web.ApiException;

/**
 * Partner bookings (v1 contract). Since MBA-3823 scheduling-service is the only writer of bookings and seats:
 * booking-api validates the request shape, forwards book / cancel / reschedule through the routing layer
 * ({@link SchedulingClient}) and answers with the AssessmentDelivery BookingId once the outbox relay has
 * projected the change (normally well under a second), so v1 ids, URLs and Result.BookingId keep working.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    static final List<String> ACCOMMODATION_CODES = Arrays.asList("ET25", "ET50", "SR", "RB");
    static final List<String> EXTRA_TIME_CODES = Arrays.asList("ET25", "ET50");

    @Autowired
    private BookingDao bookingDao;

    @Autowired
    private SchedulingClient scheduling;

    @Autowired
    private CandidateDao candidateDao;

    @Autowired
    private SessionDao sessionDao;

    @Autowired
    private RetakeEligibilityService retakeEligibilityService;

    @Value("${booking.channel:PARTNER}")
    private String channel;

    @Value("${booking.relay-wait-ms:3000}")
    private long relayWaitMs;

    public BookingResponse book(BookingRequest req) {
        if (req == null || req.getSessionId() == null || StringUtils.isBlank(req.getCandidateNumber())) {
            throw ApiException.badRequest("VALIDATION", "sessionId and candidateNumber are required");
        }
        String acc = StringUtils.trimToNull(req.getAccommodationCode());
        if (acc != null) {
            acc = acc.toUpperCase();
            if (!ACCOMMODATION_CODES.contains(acc)) {
                throw ApiException.badRequest("VALIDATION", "Unknown accommodationCode " + acc);
            }
        }
        if (req.getExternalRef() != null && req.getExternalRef().length() > 40) {
            throw ApiException.badRequest("VALIDATION", "externalRef is limited to 40 characters");
        }
        checkRetakeEligibility(req.getCandidateNumber().trim(), req.getSessionId().intValue());
        Map<String, Object> r = scheduling.book(req.getSessionId().intValue(), req.getCandidateNumber().trim(), acc,
                req.getExternalRef(), channel);
        int bookingId = legacyId(r);
        String status = String.valueOf(r.get("status"));
        log.info("Partner booking {} for {} on session {} -> {}", bookingId, req.getCandidateNumber(), req.getSessionId(),
                status);
        return new BookingResponse(bookingId, status);
    }

    /**
     * Retake rules against the live dbo.Result (results-service writes it), not scheduling-service's import-time
     * snapshot, so a score ingested since the last import is honoured. Unknown candidate/session: scheduling answers.
     */
    void checkRetakeEligibility(String candidateNumber, int sessionId) {
        Candidate cand = candidateDao.findByNumber(candidateNumber);
        Integer examId = sessionDao.findExamId(sessionId);
        if (cand == null || examId == null) {
            return;
        }
        EligibilityResult elig = retakeEligibilityService.check(cand.getCandidateId(), examId.intValue(),
                sessionDao.findStartsAt(sessionId));
        if (!elig.isEligible()) {
            String msg = "Candidate " + cand.getCandidateNumber() + " is not eligible to book "
                    + sessionDao.findById(sessionId).getExamCode() + " (" + elig.getReason() + ")";
            if (elig.getEarliestRetakeDate() != null) {
                msg += ", earliest retake date " + elig.getEarliestRetakeDate();
            }
            throw ApiException.conflict("NOT_ELIGIBLE", msg);
        }
    }

    public void cancel(int bookingId, String reason) {
        BookingInfo b = bookingDao.findById(bookingId);
        if (b == null) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        if (!"BOOKED".equals(b.getStatus()) && !"WAITLISTED".equals(b.getStatus())) {
            throw ApiException.conflict("NOT_CANCELLABLE", "Booking " + bookingId + " is " + b.getStatus());
        }
        scheduling.cancel(bookingId, StringUtils.defaultIfBlank(reason, "Cancelled by partner"));
        awaitLegacyStatus(bookingId, "CANCELLED");
    }

    /** Same checks as before (booking exists, same exam, different session), then scheduling-service reschedules. */
    public BookingResponse reschedule(int bookingId, Integer newSessionId) {
        if (newSessionId == null) {
            throw ApiException.badRequest("VALIDATION", "newSessionId is required");
        }
        BookingInfo old = bookingDao.findById(bookingId);
        if (old == null) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        Map<String, Object> r = scheduling.reschedule(bookingId, newSessionId.intValue());
        awaitLegacyStatus(bookingId, "CANCELLED");
        return new BookingResponse(legacyId(r), String.valueOf(r.get("status")));
    }

    /**
     * AssessmentDelivery BookingId for a scheduling-service booking: assigned by the relay on first projection.
     * Falls back to the scheduling id (also accepted by every v1 endpoint) if the relay is slower than relay-wait-ms.
     */
    int legacyId(Map<String, Object> booking) {
        long id = ((Number) booking.get("bookingId")).longValue();
        Object legacy = booking.get("legacyBookingId");
        long deadline = System.currentTimeMillis() + relayWaitMs;
        while (legacy == null && System.currentTimeMillis() < deadline) {
            sleep();
            legacy = scheduling.find(id).get("legacyBookingId");
        }
        if (legacy == null) {
            log.warn("relay has not projected scheduling booking {} within {} ms, answering with the scheduling id", id,
                    relayWaitMs);
            return (int) id;
        }
        return ((Number) legacy).intValue();
    }

    /** Waits until the relay has projected the change, so the v1 read that follows sees it. */
    void awaitLegacyStatus(int bookingId, String status) {
        long deadline = System.currentTimeMillis() + relayWaitMs;
        while (System.currentTimeMillis() < deadline) {
            BookingInfo b = bookingDao.findById(bookingId);
            if (b == null || status.equals(b.getStatus())) {
                return;
            }
            sleep();
        }
        log.warn("relay has not projected {} for booking {} within {} ms", status, bookingId, relayWaitMs);
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
