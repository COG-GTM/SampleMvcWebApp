package com.northwind.assessment.gateway.filter;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.UriComponentsBuilder;

import com.northwind.assessment.gateway.resolve.ResolveKey;

/**
 * Legacy portal booking/capacity URLs whose .NET pages were retired (MBA-3824) and their modern UI equivalents.
 * These always go to {@code /app}, for every center; the resolve key only labels the route log with the center.
 * Paths are lower-cased by the caller, params are the lower-cased query/form fields.
 */
final class RetiredPortalLinks {

    private static final Pattern ANY_BOOKING = Pattern.compile("^/booking(/.*)?$");
    private static final Pattern BOOKING_INDEX = Pattern.compile("^/booking(/index)?/?$");
    private static final Pattern BOOK = Pattern.compile("^/booking/book/?$");
    private static final Pattern MY_BOOKINGS = Pattern.compile("^/booking/mybookings/?$");
    private static final Pattern CANCEL = Pattern.compile("^/booking/cancel/(\\d{1,18})/?$");
    private static final Pattern RESCHEDULE = Pattern.compile("^/booking/reschedule/(\\d{1,18})/?$");
    private static final Pattern CAPACITY = Pattern.compile("^/admin/sessions/capacity/(\\d{1,18})/?$");

    /** Where a retired page goes: the lookup key (may be null), the center named in the request, the deep link. */
    record Target(ResolveKey key, String centerHint, Function<String, String> location) {
    }

    private RetiredPortalLinks() {
    }

    static Optional<Target> match(String path, Map<String, String> p) {
        Matcher m;
        String center = has(p, "centercode") ? p.get("centercode").trim().toUpperCase() : null;
        ResolveKey centerKey = center == null ? null : ResolveKey.centerCode(center);
        if ((m = CAPACITY.matcher(path)).matches()) {
            String id = m.group(1);
            return target(ResolveKey.session(id), null, c -> link("/app/admin/sessions/" + id));
        }
        if (!ANY_BOOKING.matcher(path).matches()) {
            return Optional.empty();
        }
        if (BOOKING_INDEX.matcher(path).matches()) {
            return target(centerKey, center, c -> optional(optional(optional(link("/app"),
                    "centerCode", c), "examCode", p.get("examcode")), "date", p.get("from")));
        }
        if (BOOK.matcher(path).matches() && isNumber(p.get("sessionid"))) {
            return target(ResolveKey.session(p.get("sessionid")), center,
                    c -> withCandidate(link("/app/book").queryParam("sessionId", p.get("sessionid")), p));
        }
        if (MY_BOOKINGS.matcher(path).matches()) {
            return target(centerKey, center, c -> withCandidate(link("/app/my-bookings"), p));
        }
        if ((m = CANCEL.matcher(path)).matches()) {
            return target(ResolveKey.booking(m.group(1)), center, c -> withCandidate(link("/app/my-bookings"), p));
        }
        if ((m = RESCHEDULE.matcher(path)).matches()) {
            String id = m.group(1);
            return target(ResolveKey.booking(id), center, c -> withCandidate(link("/app/reschedule/" + id), p));
        }
        // any other old booking URL (e.g. /Booking/Book without a session): the modern session search
        return target(centerKey, center, c -> withCandidate(optional(link("/app"), "centerCode", c), p));
    }

    private static Optional<Target> target(ResolveKey key, String hint, Function<String, UriComponentsBuilder> b) {
        return Optional.of(new Target(key, hint, c -> b.apply(c).encode().build().toUriString()));
    }

    private static UriComponentsBuilder link(String path) {
        return UriComponentsBuilder.fromPath(path);
    }

    private static UriComponentsBuilder optional(UriComponentsBuilder b, String name, String value) {
        return value == null || value.isBlank() ? b : b.queryParam(name, value);
    }

    private static UriComponentsBuilder withCandidate(UriComponentsBuilder b, Map<String, String> p) {
        return optional(b, "candidateNumber", p.get("candidatenumber"));
    }

    private static boolean has(Map<String, String> p, String key) {
        String v = p.get(key);
        return v != null && !v.isBlank();
    }

    private static boolean isNumber(String s) {
        return s != null && s.matches("\\d{1,18}");
    }
}
