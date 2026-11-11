package com.northwind.assessment.gateway.routing;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.northwind.assessment.gateway.config.RoutingProperties;
import com.northwind.assessment.gateway.resolve.CenterResolver;
import com.northwind.assessment.gateway.resolve.ResolveKey;
import com.northwind.assessment.gateway.resolve.ResolvedCenter;
import com.northwind.assessment.gateway.resolve.ResolveUnavailableException;

import reactor.core.publisher.Mono;

/**
 * Glue between the pure {@link RouteDecider}, the live allow-list and the center resolver.
 * <ul>
 *   <li>Booking ids in the modern range go to modern without any lookup.</li>
 *   <li>Writes use {@code modern-centers} plus {@code modern-write-centers}, reads only {@code modern-centers}.</li>
 *   <li>With an empty allow-list nothing else is looked up, so the gateway is transparent even when
 *       scheduling-service is down.</li>
 *   <li>If the lookup fails: reads fall back to legacy, writes fail closed ({@link ResolveUnavailableException})
 *       so a booking is never written to the wrong system.</li>
 * </ul>
 */
@Component
public class RoutingService {

    private final AllowListHolder allowList;
    private final CenterResolver resolver;
    private final RouteDecider decider;

    public RoutingService(AllowListHolder allowList, CenterResolver resolver, RoutingProperties props) {
        this.allowList = allowList;
        this.resolver = resolver;
        this.decider = new RouteDecider(props.getModernBookingIdFloor());
    }

    public Mono<RouteDecision> decide(ResolveKey key, Long bookingId, boolean write) {
        Set<String> centers = allowList.get().centersFor(write);
        if (decider.isModernBookingId(bookingId)) {
            return resolver.resolve(key)
                    .onErrorResume(e -> Mono.just(Optional.empty()))
                    .map(r -> decider.decide(r.map(ResolvedCenter::centerCode).orElse(null), centers,
                            bookingId, null));
        }
        if (centers.isEmpty() || key == null) {
            return Mono.just(decider.decide(null, centers, bookingId, null));
        }
        return resolver.resolve(key)
                .map(r -> r.map(c -> decider.decide(c.centerCode(), centers, bookingId, c.bookingOrigin()))
                        .orElseGet(() -> RouteDecision.legacy(null, "unknown-to-modern")))
                .onErrorResume(ResolveUnavailableException.class,
                        e -> write ? Mono.error(e) : Mono.just(RouteDecision.legacy(null, "resolve-unavailable")));
    }
}
