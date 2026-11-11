package com.northwind.routing.web;

import com.northwind.routing.config.RoutingProperties;
import com.northwind.routing.policy.AllowList;
import com.northwind.routing.resolve.CenterResolver;
import com.northwind.routing.route.ApiRoutes;
import com.northwind.routing.route.Lookup;
import com.northwind.routing.route.Lookup.Kind;
import com.northwind.routing.route.RouteDecider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Keeps the modern side from writing seat inventory it does not own: a v2 write (book, cancel, reschedule,
 * capacity change) that reaches scheduling-service through the gateway is refused with
 * {@code 403 CENTER_NOT_MODERN} when its centre is not allow-listed. Bookings at or above the modern id floor
 * are always the modern side's own. Unresolvable keys are passed through for scheduling-service to validate.
 */
@Component
public class ModernApiWriteGuard {

    public static final String CENTER_NOT_MODERN = "CENTER_NOT_MODERN";

    private final AllowList allowList;
    private final CenterResolver resolver;
    private final long floor;

    public ModernApiWriteGuard(AllowList allowList, CenterResolver resolver, RoutingProperties props) {
        this.allowList = allowList;
        this.resolver = resolver;
        this.floor = props.getModernBookingIdFloor();
    }

    /** The first centre this write touches that is not allow-listed, if any. */
    public Mono<Optional<String>> deniedCenter(ApiRoutes.V2Write write, Map<String, Object> body) {
        List<Lookup> checks = new ArrayList<>();
        switch (write.op()) {
            case BOOK -> checks.add(Lookup.of(Kind.SESSION_ID, number(body, "sessionId")));
            case CANCEL -> checks.add(Lookup.of(Kind.BOOKING_ID, write.id()));
            case RESCHEDULE -> {
                checks.add(Lookup.of(Kind.BOOKING_ID, write.id()));
                checks.add(Lookup.of(Kind.SESSION_ID, number(body, "newSessionId")));
            }
            case CAPACITY -> checks.add(Lookup.of(Kind.SESSION_ID, write.id()));
            default -> throw new IllegalArgumentException(write.op().name());
        }
        return Flux.fromIterable(checks)
                .concatMap(this::denied)
                .filter(Optional::isPresent)
                .next()
                .defaultIfEmpty(Optional.empty());
    }

    private Mono<Optional<String>> denied(Lookup l) {
        if (!l.present() || RouteDecider.modernOwned(l.bookingId(), floor)) {
            return Mono.just(Optional.empty());
        }
        return resolver.resolve(l).map(c -> c.filter(code -> !allowList.contains(code)));
    }

    static String number(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        if (v instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        return v instanceof String s && !s.isBlank() ? s.trim() : null;
    }
}
