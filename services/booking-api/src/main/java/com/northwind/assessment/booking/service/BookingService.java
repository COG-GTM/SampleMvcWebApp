package com.northwind.assessment.booking.service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.northwind.assessment.booking.dao.BookingDao;
import com.northwind.assessment.booking.dao.CandidateDao;
import com.northwind.assessment.booking.dao.SessionDao;
import com.northwind.assessment.booking.model.BookingInfo;
import com.northwind.assessment.booking.model.BookingRequest;
import com.northwind.assessment.booking.model.BookingResponse;
import com.northwind.assessment.booking.model.Candidate;
import com.northwind.assessment.booking.model.EligibilityResult;
import com.northwind.assessment.booking.model.SessionInfo;
import com.northwind.assessment.booking.web.ApiException;

/**
 * Partner bookings. Mirrors the checks in usp_BookSeat (portal) so both channels
 * reject the same requests.
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

    @Value("${booking.channel:PARTNER}")
    private String channel;

    @Value("${booking.audit-actor:booking-api}")
    private String auditActor;

    @Transactional
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

        Candidate cand = candidateDao.findByNumber(req.getCandidateNumber().trim());
        if (cand == null) {
            throw ApiException.notFound("CANDIDATE_NOT_FOUND", "Candidate " + req.getCandidateNumber() + " not found");
        }
        int sessionId = req.getSessionId().intValue();
        SessionInfo session = sessionDao.findById(sessionId);
        if (session == null) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session " + sessionId + " not found");
        }
        if (!"OPEN".equals(session.getStatus())) {
            throw ApiException.conflict("SESSION_NOT_OPEN", "Session " + sessionId + " is not open for booking");
        }
        if (bookingDao.hasActiveBooking(sessionId, cand.getCandidateId())) {
            throw ApiException.conflict("DUPLICATE_BOOKING",
                    "Candidate " + cand.getCandidateNumber() + " already has an active booking on session " + sessionId);
        }
        if (acc != null && EXTRA_TIME_CODES.contains(acc)) {
            int used = bookingDao.countAccommodationSeatsUsed(sessionId);
            if (used >= session.getAccommodationCapacity()) {
                throw ApiException.conflict("NO_ACCOMMODATION_SEAT",
                        "No accommodation seat available on session " + sessionId);
            }
        }

        // retake rules are checked against the date the candidate would sit the exam
        Integer examId = sessionDao.findExamId(sessionId);
        Date sitting = sessionDao.findStartsAt(sessionId);
        EligibilityResult elig = retakeEligibilityService.check(cand.getCandidateId(), examId.intValue(), sitting);
        if (!elig.isEligible()) {
            String msg = "Candidate " + cand.getCandidateNumber() + " is not eligible to book " + session.getExamCode()
                    + " (" + elig.getReason() + ")";
            if (elig.getEarliestRetakeDate() != null) {
                msg += ", earliest retake date " + elig.getEarliestRetakeDate();
            }
            throw ApiException.conflict("NOT_ELIGIBLE", msg);
        }

        int seats = sessionDao.readSeatsAvailable(sessionId);
        int bookingId;
        String status;
        if (seats > 0) {
            bookingId = bookingDao.insertBooking(sessionId, cand.getCandidateId(), "BOOKED", acc, channel,
                    req.getExternalRef());
            sessionDao.decrementSeatsAvailable(sessionId);
            status = "BOOKED";
        } else {
            bookingId = bookingDao.insertBooking(sessionId, cand.getCandidateId(), "WAITLISTED", acc, channel,
                    req.getExternalRef());
            bookingDao.insertWaitlist(sessionId, cand.getCandidateId(), bookingId);
            status = "WAITLISTED";
        }
        bookingDao.audit("Booking", bookingId, "BOOK",
                "session=" + sessionId + " status=" + status + " channel=" + channel
                        + (req.getExternalRef() != null ? " ref=" + req.getExternalRef() : ""), auditActor);
        log.info("Partner booking {} for {} on session {} -> {}", bookingId, cand.getCandidateNumber(), sessionId, status);
        return new BookingResponse(bookingId, status);
    }

    @Transactional
    public void cancel(int bookingId, String reason) {
        BookingInfo b = bookingDao.findById(bookingId);
        if (b == null) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        if (!"BOOKED".equals(b.getStatus()) && !"WAITLISTED".equals(b.getStatus())) {
            throw ApiException.conflict("NOT_CANCELLABLE", "Booking " + bookingId + " is " + b.getStatus());
        }
        try {
            bookingDao.cancel(bookingId, StringUtils.defaultIfBlank(reason, "Cancelled by partner"), auditActor);
        } catch (DataAccessException e) {
            throw ApiException.conflict("NOT_CANCELLABLE", e.getMostSpecificCause().getMessage());
        }
    }

    /** Cancel through the proc, then book the new session through the normal partner path. */
    @Transactional
    public BookingResponse reschedule(int bookingId, Integer newSessionId) {
        if (newSessionId == null) {
            throw ApiException.badRequest("VALIDATION", "newSessionId is required");
        }
        BookingInfo old = bookingDao.findById(bookingId);
        if (old == null) {
            throw ApiException.notFound("BOOKING_NOT_FOUND", "Booking " + bookingId + " not found");
        }
        Integer oldExam = sessionDao.findExamId(old.getSessionId());
        Integer newExam = sessionDao.findExamId(newSessionId.intValue());
        if (newExam == null) {
            throw ApiException.notFound("SESSION_NOT_FOUND", "Session " + newSessionId + " not found");
        }
        if (!newExam.equals(oldExam)) {
            throw ApiException.conflict("DIFFERENT_EXAM", "Booking " + bookingId + " cannot be moved to session "
                    + newSessionId + " (different exam)");
        }
        if (newSessionId.intValue() == old.getSessionId()) {
            throw ApiException.conflict("SAME_SESSION", "Booking " + bookingId + " is already on session " + newSessionId);
        }
        cancel(bookingId, "Rescheduled");

        BookingRequest req = new BookingRequest();
        req.setSessionId(newSessionId);
        req.setCandidateNumber(old.getCandidateNumber());
        req.setAccommodationCode(old.getAccommodationCode());
        req.setExternalRef(old.getExternalRef());
        return book(req);
    }
}
