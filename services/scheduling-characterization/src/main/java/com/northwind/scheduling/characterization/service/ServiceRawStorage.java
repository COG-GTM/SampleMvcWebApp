package com.northwind.scheduling.characterization.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.northwind.scheduling.characterization.spi.RawStorageAccess;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct writers against the service's storage through its legacy direct-write compatibility API (/admin/storage),
 * which applies the former trigger behaviour explicitly. partnerBook goes through the real PARTNER channel.
 */
final class ServiceRawStorage implements RawStorageAccess {

    private final SchedulingServiceTarget t;

    ServiceRawStorage(SchedulingServiceTarget t) {
        this.t = t;
    }

    private static Map<String, Object> row(int sessionId, int candidateId, String status, String channel, String acc) {
        Map<String, Object> m = new HashMap<>();
        m.put("sessionId", sessionId);
        m.put("candidateId", candidateId);
        m.put("status", status);
        m.put("channel", channel);
        m.put("accommodationCode", acc);
        return m;
    }

    private List<Integer> insert(List<Map<String, Object>> rows) {
        JsonNode r = t.http.require(t.http.send("POST", "/admin/storage/bookings", Map.of("rows", rows), null), "insert bookings").body();
        List<Integer> ids = new ArrayList<>();
        r.get("bookingIds").forEach(n -> ids.add(n.asInt()));
        return ids;
    }

    @Override
    public int insertBooking(int sessionId, int candidateId, String status, String channel, String accommodationCode) {
        return insert(List.of(row(sessionId, candidateId, status, channel, accommodationCode))).get(0);
    }

    @Override
    public List<Integer> insertBookings(List<int[]> sessionAndCandidate, String status, String channel) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int[] sc : sessionAndCandidate) {
            rows.add(row(sc[0], sc[1], status, channel, null));
        }
        return insert(rows);
    }

    private void patchBooking(int bookingId, Map<String, Object> body) {
        t.http.require(t.http.send("PATCH", "/admin/storage/bookings/" + bookingId, body, null), "patch booking");
    }

    @Override
    public void updateBookingStatus(int bookingId, String status) {
        patchBooking(bookingId, Map.of("status", status));
    }

    @Override
    public void deleteBooking(int bookingId) {
        t.http.require(t.http.send("DELETE", "/admin/storage/bookings/" + bookingId, null, null), "delete booking");
    }

    @Override
    public void setBookedAt(int bookingId, LocalDateTime bookedAt) {
        patchBooking(bookingId, Map.of("bookedAt", bookedAt));
    }

    @Override
    public void adjustSeatsAvailable(int sessionId, int delta) {
        t.http.require(t.http.send("POST", "/admin/storage/sessions/" + sessionId + "/seats", Map.of("delta", delta), null), "seats");
    }

    private void patchSessions(List<Map<String, Object>> patches) {
        t.http.require(t.http.send("PATCH", "/admin/storage/sessions", patches, null), "patch sessions");
    }

    @Override
    public void updateCapacity(int sessionId, int capacity) {
        patchSessions(List.of(Map.of("sessionId", sessionId, "capacity", capacity)));
    }

    @Override
    public void updateCapacities(Map<Integer, Integer> capacityBySession) {
        List<Map<String, Object>> patches = new ArrayList<>();
        capacityBySession.forEach((s, c) -> patches.add(Map.of("sessionId", s, "capacity", c)));
        patchSessions(patches);
    }

    @Override
    public void setSessionStatus(int sessionId, String status) {
        patchSessions(List.of(Map.of("sessionId", sessionId, "status", status)));
    }

    @Override
    public void setLastModified(int sessionId, LocalDateTime value) {
        patchSessions(List.of(Map.of("sessionId", sessionId, "lastModified", value)));
    }

    @Override
    public int partnerBook(int sessionId, int candidateId, String accommodationCode) {
        t.syncReference();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", sessionId);
        body.put("candidateId", candidateId);
        body.put("accommodationCode", accommodationCode);
        body.put("channel", "PARTNER");
        body.put("externalRef", "CHR-" + sessionId + "-" + candidateId);
        return t.http.require(t.http.send("POST", "/bookings", body, "booking-api"), "partner book").body().get("bookingId").asInt();
    }
}
