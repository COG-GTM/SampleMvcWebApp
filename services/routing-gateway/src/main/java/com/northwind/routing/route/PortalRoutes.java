package com.northwind.routing.route;

import com.northwind.routing.route.Lookup.Kind;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The legacy MVC 5 booking/admin pages (routes from AssessmentDelivery.Web/App_Start/RouteConfig.cs at
 * {@code legacy-baseline}, flows in docs/SYSTEM_OVERVIEW.md) and their modern UI deep links under {@code /app}
 * (services/scheduling-service/frontend/src/App.tsx). Since MBA-3824 (M5) the portal no longer serves these pages,
 * so every match is redirected whatever the centre, and a missing centre key still matches (see
 * docs/adr/0004-retire-portal-booking-slice.md). MVC routing and model binding are case-insensitive, so paths and
 * parameter names are matched case-insensitively here too.
 */
public final class PortalRoutes {

    public enum Page { FIND, BOOK, CANCEL, RESCHEDULE, MY_BOOKINGS, ADMIN_SESSIONS, ADMIN_CAPACITY }

    /** {@code needsForm}: the centre key is in the urlencoded form body (POST /Booking/Book, field SessionId). */
    public record Match(Page page, Lookup lookup, String id, boolean needsForm) {

        public Match withLookup(Lookup l) {
            return new Match(page, l, id, false);
        }
    }

    private PortalRoutes() {
    }

    public static Optional<Match> match(String method, String rawPath, MultiValueMap<String, String> query) {
        String path = normalise(rawPath);
        boolean get = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        boolean post = "POST".equalsIgnoreCase(method);
        if (!get && !post) {
            return Optional.empty();
        }
        String[] seg = path.substring(1).split("/");
        if (seg.length >= 1 && seg[0].equals("booking")) {
            String action = seg.length >= 2 ? seg[1] : "index";
            String id = seg.length == 3 ? seg[2] : null;
            if (seg.length <= 2 && action.equals("index") && get) {
                return retired(Page.FIND, Kind.CENTER_CODE, param(query, "CenterCode"), null);
            }
            if (seg.length == 2 && action.equals("book")) {
                return get ? retired(Page.BOOK, Kind.SESSION_ID, param(query, "sessionId"), null)
                        : Optional.of(new Match(Page.BOOK, Lookup.of(Kind.SESSION_ID, null), null, true));
            }
            if (seg.length == 2 && action.equals("mybookings") && get) {
                return retired(Page.MY_BOOKINGS, Kind.CENTER_CODE, param(query, "centerCode"), null);
            }
            if (id != null && isId(id) && action.equals("cancel")) {
                return retired(Page.CANCEL, Kind.BOOKING_ID, id, id);
            }
            if (id != null && isId(id) && action.equals("reschedule")) {
                return retired(Page.RESCHEDULE, Kind.BOOKING_ID, id, id);
            }
            return Optional.empty();
        }
        if (seg.length == 2 && seg[0].equals("admin") && seg[1].equals("sessions") && get) {
            return withKey(Page.ADMIN_SESSIONS, Kind.CENTER_ID, param(query, "centerId"), null);
        }
        if (seg.length == 4 && seg[0].equals("admin") && seg[1].equals("sessions") && seg[2].equals("capacity") && isId(seg[3])) {
            return withKey(Page.ADMIN_CAPACITY, Kind.SESSION_ID, seg[3], seg[3]);
        }
        return Optional.empty();
    }

    /** Modern UI location (proxied by this gateway); {@code center} is the resolved centre code or null. */
    public static String redirect(Match m, String center, MultiValueMap<String, String> query) {
        UriComponentsBuilder b = UriComponentsBuilder.newInstance();
        switch (m.page()) {
            case FIND -> {
                b.path("/app/");
                addIfPresent(b, "center", center);
                addIfPresent(b, "exam", param(query, "ExamCode"));
                addIfPresent(b, "from", param(query, "From"));
                addIfPresent(b, "to", param(query, "To"));
            }
            case BOOK -> {
                if (m.lookup().present()) {
                    b.path("/app/book").queryParam("sessionId", m.lookup().value());
                } else {
                    b.path("/app/");
                }
            }
            case CANCEL -> b.path("/app/my-bookings");
            case MY_BOOKINGS -> {
                b.path("/app/my-bookings");
                addIfPresent(b, "candidateNumber", param(query, "candidateNumber"));
            }
            case RESCHEDULE -> b.path("/app/reschedule/").path(m.id());
            case ADMIN_SESSIONS -> {
                if (center != null && !center.isBlank()) {
                    b.path("/app/admin/centers/").path(center);
                } else {
                    b.path("/app/");
                }
            }
            case ADMIN_CAPACITY -> b.path("/app/admin/sessions/").path(m.id());
            default -> throw new IllegalArgumentException(m.page().name());
        }
        return b.encode().build().toUriString();
    }

    /** Case-insensitive lookup in query or form parameters (first non-blank value). */
    public static String param(MultiValueMap<String, String> params, String name) {
        if (params == null) {
            return null;
        }
        for (Map.Entry<String, java.util.List<String>> e : params.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                for (String v : e.getValue()) {
                    if (v != null && !v.isBlank()) {
                        return v.trim();
                    }
                }
            }
        }
        return null;
    }

    static String normalise(String path) {
        String p = path == null || path.isEmpty() ? "/" : path.toLowerCase(Locale.ROOT);
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    static boolean isId(String s) {
        return !s.isEmpty() && s.length() <= 10 && s.chars().allMatch(Character::isDigit);
    }

    private static Optional<Match> retired(Page page, Kind kind, String value, String id) {
        return Optional.of(new Match(page, Lookup.of(kind, value), id, false));
    }

    private static Optional<Match> withKey(Page page, Kind kind, String value, String id) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Match(page, Lookup.of(kind, value), id, false));
    }

    private static void addIfPresent(UriComponentsBuilder b, String name, String value) {
        if (value != null && !value.isBlank()) {
            b.queryParam(name, value);
        }
    }
}
