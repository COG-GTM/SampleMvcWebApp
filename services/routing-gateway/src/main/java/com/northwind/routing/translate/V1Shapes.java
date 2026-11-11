package com.northwind.routing.translate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps scheduling-service {@code /api/v2} payloads back to the booking-api v1 shapes partners already parse
 * (field names and order of booking-api's SessionDto / Booking / BookingResponse and its
 * {@code {"error","message"}} error body, see docs/SYSTEM_OVERVIEW.md and services/booking-api).
 */
public final class V1Shapes {

    private V1Shapes() {
    }

    public static Map<String, Object> session(Map<String, Object> v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sessionId", v2.get("sessionId"));
        m.put("examCode", v2.get("examCode"));
        m.put("examTitle", v2.get("examTitle"));
        m.put("centerCode", v2.get("centerCode"));
        m.put("centerName", v2.get("centerName"));
        m.put("roomName", v2.get("roomName"));
        m.put("startsAt", ts(v2.get("startsAt")));
        m.put("capacity", v2.get("capacity"));
        m.put("accommodationCapacity", v2.get("accommodationCapacity"));
        m.put("seatsAvailable", v2.get("seatsAvailable"));
        m.put("status", v2.get("status"));
        m.put("bookedSeats", v2.get("bookedSeats"));
        return m;
    }

    public static List<Map<String, Object>> sessions(List<Map<String, Object>> v2) {
        return v2.stream().map(V1Shapes::session).toList();
    }

    public static Map<String, Object> booking(Map<String, Object> v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", v2.get("bookingId"));
        m.put("sessionId", v2.get("sessionId"));
        m.put("candidateNumber", v2.get("candidateNumber"));
        m.put("examCode", v2.get("examCode"));
        m.put("sessionStartsAt", ts(v2.get("sessionStartsAt")));
        m.put("status", v2.get("status"));
        m.put("accommodationCode", v2.get("accommodationCode"));
        m.put("channel", v2.get("channel"));
        m.put("externalRef", v2.get("externalRef"));
        m.put("bookedAt", ts(v2.get("bookedAt")));
        m.put("cancelledAt", ts(v2.get("cancelledAt")));
        m.put("cancelReason", v2.get("cancelReason"));
        return m;
    }

    /** v2 BookingResultDto -> v1 BookingResponse {bookingId, status}. */
    public static Map<String, Object> outcome(Map<String, Object> v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", v2.get("bookingId"));
        m.put("status", v2.get("status"));
        return m;
    }

    /** v2 error {error, message, legacyCode} -> booking-api {error, message}. */
    public static Map<String, Object> error(Map<String, Object> v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", v2 == null ? "ERROR" : v2.getOrDefault("error", "ERROR"));
        m.put("message", v2 == null ? null : v2.get("message"));
        return m;
    }

    public static Map<String, Object> error(String error, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("message", message);
        return m;
    }

    /** booking-api serialises timestamps as yyyy-MM-dd'T'HH:mm:ss (no fraction, no zone). */
    static Object ts(Object v) {
        if (!(v instanceof String s) || s.isEmpty()) {
            return v;
        }
        int dot = s.indexOf('.');
        String t = dot > 0 ? s.substring(0, dot) : s;
        return t.length() == 16 ? t + ":00" : t;
    }
}
