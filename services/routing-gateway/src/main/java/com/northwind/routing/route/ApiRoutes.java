package com.northwind.routing.route;

import com.northwind.routing.route.Lookup.Kind;
import java.util.Optional;
import org.springframework.util.MultiValueMap;

/**
 * booking-api v1 calls the strangler can move (docs/SYSTEM_OVERVIEW.md, services/booking-api) and the
 * scheduling-service v2 writes guarded by {@code ModernApiWriteGuard}.
 */
public final class ApiRoutes {

    public enum V1Op { SESSIONS, SESSION, BOOK, GET_BOOKING, CANCEL, RESCHEDULE }

    public enum V2Op { BOOK, CANCEL, RESCHEDULE, CAPACITY }

    /** {@code needsBody}: the centre key is {@code sessionId} in the JSON body (POST /api/v1/bookings). */
    public record V1(V1Op op, Lookup lookup, String id, boolean needsBody) {
    }

    /** {@code id} is the booking or session id from the path, if any. */
    public record V2Write(V2Op op, String id) {
    }

    private ApiRoutes() {
    }

    public static Optional<V1> matchV1(String method, String rawPath, MultiValueMap<String, String> query) {
        String[] s = segments(rawPath, "/api/v1/");
        if (s == null) {
            return Optional.empty();
        }
        String m = method.toUpperCase();
        if (s[0].equals("sessions") && m.equals("GET")) {
            if (s.length == 1) {
                String center = PortalRoutes.param(query, "centerCode");
                return center == null ? Optional.empty() : Optional.of(new V1(V1Op.SESSIONS, Lookup.of(Kind.CENTER_CODE, center), null, false));
            }
            if (s.length == 2 && PortalRoutes.isId(s[1])) {
                return Optional.of(new V1(V1Op.SESSION, Lookup.of(Kind.SESSION_ID, s[1]), s[1], false));
            }
            return Optional.empty();
        }
        if (!s[0].equals("bookings")) {
            return Optional.empty();
        }
        if (s.length == 1 && m.equals("POST")) {
            return Optional.of(new V1(V1Op.BOOK, Lookup.of(Kind.SESSION_ID, null), null, true));
        }
        if (s.length >= 2 && PortalRoutes.isId(s[1])) {
            Lookup booking = Lookup.of(Kind.BOOKING_ID, s[1]);
            if (s.length == 2 && m.equals("GET")) {
                return Optional.of(new V1(V1Op.GET_BOOKING, booking, s[1], false));
            }
            if (s.length == 2 && m.equals("DELETE")) {
                return Optional.of(new V1(V1Op.CANCEL, booking, s[1], false));
            }
            if (s.length == 3 && s[2].equals("reschedule") && m.equals("POST")) {
                return Optional.of(new V1(V1Op.RESCHEDULE, booking, s[1], false));
            }
        }
        return Optional.empty();
    }

    public static Optional<V2Write> matchV2Write(String method, String rawPath) {
        String[] s = segments(rawPath, "/api/v2/");
        if (s == null) {
            return Optional.empty();
        }
        String m = method.toUpperCase();
        if (s[0].equals("bookings")) {
            if (s.length == 1 && m.equals("POST")) {
                return Optional.of(new V2Write(V2Op.BOOK, null));
            }
            if (s.length == 2 && m.equals("DELETE") && PortalRoutes.isId(s[1])) {
                return Optional.of(new V2Write(V2Op.CANCEL, s[1]));
            }
            if (s.length == 3 && m.equals("POST") && s[2].equals("reschedule") && PortalRoutes.isId(s[1])) {
                return Optional.of(new V2Write(V2Op.RESCHEDULE, s[1]));
            }
        }
        if (s[0].equals("sessions") && s.length == 3 && s[2].equals("capacity") && m.equals("PUT") && PortalRoutes.isId(s[1])) {
            return Optional.of(new V2Write(V2Op.CAPACITY, s[1]));
        }
        return Optional.empty();
    }

    private static String[] segments(String rawPath, String prefix) {
        String p = PortalRoutes.normalise(rawPath);
        if (!p.startsWith(prefix) || p.length() == prefix.length()) {
            return null;
        }
        return p.substring(prefix.length()).split("/");
    }
}
