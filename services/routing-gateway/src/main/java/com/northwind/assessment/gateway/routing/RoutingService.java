package com.northwind.assessment.gateway.routing;

import java.util.Optional;

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
        AllowList list = allowList.get();
        if (decider.isModernBookingId(bookingId)) {
            return resolver.resolve(key)
                    .onErrorResume(e -> Mono.just(Optional.empty()))
                    .map(r -> decider.decide(r.map(ResolvedCenter::centerCode).orElse(null), list.modernCenters(),
                            bookingId, null));
        }
        if (list.isEmpty() || key == null) {
            return Mono.just(decider.decide(null, list.modernCenters(), bookingId, null));
        }
        return resolver.resolve(key)
                .map(r -> r.map(c -> decider.decide(c.centerCode(), list.modernCenters(), bookingId, c.bookingOrigin()))
                        .orElseGet(() -> RouteDecision.legacy(null, "unknown-to-modern")))
                .onErrorResume(ResolveUnavailableException.class,
                        e -> write ? Mono.error(e) : Mono.just(RouteDecision.legacy(null, "resolve-unavailable")));
    }
}
