package com.northwind.assessment.booking.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.northwind.assessment.booking.client.SchedulingClient;
import com.northwind.assessment.booking.model.BookingInfo;

/**
 * Booking reads for the partner channel, served by scheduling-service (the owner of Booking and Waitlist).
 *
 * Partner bookings used to be INSERTed here directly (not through usp_BookSeat) followed by a separate
 * SeatsAvailable decrement, which together with trg_Booking_SeatSync double-decremented seats (HB-01). All
 * writes now go through scheduling-service (see BookingService); this class only reads.
 */
@Repository
public class BookingDao {

    @Autowired
    private SchedulingClient scheduling;

    public BookingInfo findById(int bookingId) {
        JsonNode n = scheduling.getBooking(bookingId);
        return n == null ? null : toBookingInfo(n);
    }

    /** BookedAt DESC, BookingId DESC */
    public List<BookingInfo> findByCandidate(int candidateId) {
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("candidateId", candidateId);
        q.put("sort", "bookedAt,desc");
        return toList(scheduling.searchBookings(q));
    }

    public boolean hasActiveBooking(int sessionId, int candidateId) {
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("sessionId", sessionId);
        q.put("candidateId", candidateId);
        q.put("status", "BOOKED,WAITLISTED");
        return scheduling.searchBookings(q).size() > 0;
    }

    /** BOOKED + ATTENDED bookings holding an extra-time (ET25/ET50) accommodation seat */
    public int countAccommodationSeatsUsed(int sessionId) {
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("sessionId", sessionId);
        q.put("status", "BOOKED,ATTENDED");
        int used = 0;
        for (JsonNode n : scheduling.searchBookings(q)) {
            String code = SessionDao.text(n, "accommodationCode");
            if ("ET25".equals(code) || "ET50".equals(code)) {
                used++;
            }
        }
        return used;
    }

    private static List<BookingInfo> toList(JsonNode array) {
        List<BookingInfo> list = new ArrayList<BookingInfo>();
        for (JsonNode n : array) {
            list.add(toBookingInfo(n));
        }
        return list;
    }

    static BookingInfo toBookingInfo(JsonNode n) {
        BookingInfo b = new BookingInfo();
        b.setBookingId(n.path("bookingId").asInt());
        b.setSessionId(n.path("sessionId").asInt());
        b.setCandidateNumber(SessionDao.text(n, "candidateNumber"));
        b.setExamCode(SessionDao.text(n, "examCode"));
        b.setSessionStartsAt(SessionDao.text(n, "sessionStartsAt"));
        b.setStatus(SessionDao.text(n, "status"));
        b.setAccommodationCode(SessionDao.text(n, "accommodationCode"));
        b.setChannel(SessionDao.text(n, "channel"));
        b.setExternalRef(SessionDao.text(n, "externalRef"));
        b.setBookedAt(SessionDao.text(n, "bookedAt"));
        b.setCancelledAt(SessionDao.text(n, "cancelledAt"));
        b.setCancelReason(SessionDao.text(n, "cancelReason"));
        return b;
    }
}
