package com.northwind.routing.web;

import com.northwind.routing.config.RoutingProperties;
import com.northwind.routing.policy.AllowList;
import com.northwind.routing.resolve.CenterResolver;
import com.northwind.routing.route.ApiRoutes;
import com.northwind.routing.route.Lookup;
import com.northwind.routing.route.Lookup.Kind;
import com.northwind.routing.route.RouteDecider;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Keeps the modern side from writing seat inventory it does not own: a v2 write (book, cancel, reschedule,
 * capacity change) that reaches scheduling-service through the gateway is refused with
 * {@code 403 CENTER_NOT_MODERN} when its centre is not allow-listed. Bookings at or above the modern id floor
 * are always the modern side's own. Keys that cannot be resolved
 * (lookup outage or unknown id) are refused: the guard fails closed.
 */
@Component
public class ModernApiWriteGuard {

    public static final String CENTER_NOT_MODERN = "CENTER_NOT_MODERN";
    public static final String UNRESOLVED = "UNRESOLVED";

    private final AllowList allowList;
    private final CenterResolver resolver;
    private final long floor;

    public ModernApiWriteGuard(AllowList allowList, CenterResolver resolver, RoutingProperties props) {
        this.allowList = allowList;
        this.resolver = resolver;
        this.floor = props.getModernBookingIdFloor();
    }

    /** The first centre this write touches that is not allow-listed ({@link #UNRESOLVED} if a key cannot be resolved). */
    public Mono<Optional<String>> deniedCenter(ApiRoutes.V2Write write, Map<String, Object> body) {
        return switch (write.op()) {
            case BOOK -> denied(Lookup.of(Kind.SESSION_ID, number(body, "sessionId")));
            case CANCEL -> denied(Lookup.of(Kind.BOOKING_ID, write.id()));
            case CAPACITY -> denied(Lookup.of(Kind.SESSION_ID, write.id()));
            case RESCHEDULE -> deniedReschedule(Lookup.of(Kind.BOOKING_ID, write.id()),
                    Lookup.of(Kind.SESSION_ID, number(body, "newSessionId")));
        };
    }

    public static String message(String center) {
        return UNRESOLVED.equals(center)
                ? "The centre for this write could not be resolved; refused until it can be checked against routing.modern-centers"
                : "Centre " + center + " is not on routing.modern-centers; its seat inventory is still owned by legacy";
    }

    /**
     * A modern-owned booking may still move within its own centre after that centre is rolled back (ownership by
     * origin); any other destination must be allow-listed, so the modern side never takes a legacy-owned seat.
     */
    private Mono<Optional<String>> deniedReschedule(Lookup booking, Lookup destination) {
        if (!booking.present() || !destination.present()) {
            return Mono.just(Optional.empty());
        }
        boolean owned = RouteDecider.modernOwned(booking.bookingId(), floor);
        return resolver.resolve(booking).zipWith(resolver.resolve(destination)).map(t -> {
            Optional<String> from = t.getT1();
            Optional<String> to = t.getT2();
            if (!owned && from.isEmpty()) {
                return Optional.of(UNRESOLVED);
            }
            if (!owned && !allowList.contains(from.get())) {
                return from;
            }
            if (to.isEmpty()) {
                return Optional.of(UNRESOLVED);
            }
            boolean ownCentre = owned && to.equals(from);
            return ownCentre || allowList.contains(to.get()) ? Optional.<String>empty() : to;
        });
    }

    private Mono<Optional<String>> denied(Lookup l) {
        if (!l.present() || RouteDecider.modernOwned(l.bookingId(), floor)) {
            return Mono.just(Optional.empty());
        }
        return resolver.resolve(l).map(c -> c.isEmpty()
                ? Optional.of(UNRESOLVED)
                : c.filter(code -> !allowList.contains(code)));
    }

    static String number(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        return v instanceof String s && !s.isBlank() ? s.trim() : null;
    }
}
