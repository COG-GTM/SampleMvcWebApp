package com.northwind.assessment.booking.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.northwind.assessment.booking.client.SchedulingClient;
import com.northwind.assessment.booking.model.SessionInfo;
import com.northwind.assessment.booking.util.DateUtils;

/**
 * ExamSession reads, served by scheduling-service (the owner of ExamSession). No SQL: the old direct
 * SeatsAvailable read/decrement is gone, scheduling-service does the seat accounting on POST /bookings.
 */
@Repository
public class SessionDao {

    /** booking-api has always capped session searches at 500 rows (was SELECT TOP 500) */
    static final int MAX_ROWS = 500;

    @Autowired
    private SchedulingClient scheduling;

    /** No status filter (HB-13); 'to' is inclusive of the whole day; StartsAt, SessionId order. */
    public List<SessionInfo> find(String examCode, String centerCode, Date from, Date to) {
        Map<String, Object> q = new LinkedHashMap<String, Object>();
        q.put("examCode", examCode);
        q.put("centerCode", centerCode);
        q.put("from", DateUtils.formatIsoDate(from));
        q.put("to", DateUtils.formatIsoDate(to));
        q.put("sort", "startsAt,sessionId");
        q.put("limit", MAX_ROWS);
        List<SessionInfo> list = new ArrayList<SessionInfo>();
        for (JsonNode n : scheduling.searchSessions(q)) {
            list.add(toSessionInfo(n, false));
        }
        return list;
    }

    /** Session with bookedSeats (BOOKED + ATTENDED), or null. */
    public SessionInfo findById(int sessionId) {
        JsonNode n = scheduling.getSession(sessionId);
        return n == null ? null : toSessionInfo(n, true);
    }

    /** Raw service view of a session (examId, startsAt, ...), or null. */
    public JsonNode findRaw(int sessionId) {
        return scheduling.getSession(sessionId);
    }

    static SessionInfo toSessionInfo(JsonNode n, boolean withCounts) {
        SessionInfo s = new SessionInfo();
        s.setSessionId(n.path("sessionId").asInt());
        s.setExamCode(text(n, "examCode"));
        s.setExamTitle(text(n, "examTitle"));
        s.setCenterCode(text(n, "centerCode"));
        s.setCenterName(text(n, "centerName"));
        s.setRoomName(text(n, "roomName"));
        s.setStartsAt(text(n, "startsAt"));
        s.setCapacity(n.path("capacity").asInt());
        s.setAccommodationCapacity(n.path("accommodationCapacity").asInt());
        s.setSeatsAvailable(n.path("seatsAvailable").asInt());
        s.setStatus(text(n, "status"));
        if (withCounts && n.hasNonNull("bookedSeats")) {
            s.setBookedSeats(Integer.valueOf(n.get("bookedSeats").asInt()));
        }
        return s;
    }

    static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
