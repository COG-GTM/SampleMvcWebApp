package com.northwind.assessment.gateway.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.northwind.assessment.gateway.resolve.ResolveKey;
import com.northwind.assessment.gateway.resolve.ResolveUnavailableException;
import com.northwind.assessment.gateway.routing.RouteDecision;
import com.northwind.assessment.gateway.routing.RoutingService;

import reactor.core.publisher.Mono;

/**
 * Direct {@code /api/v2} writes through the gateway (the modern UI's own calls) are only forwarded when the same
 * routing decision as for v1 says modern: allow-listed center or modern-owned booking. Otherwise a production
 * center could be written on the modern side without the relay, bypassing the allow-list guard.
 */
@Component
public class ModernApiWriteGuard implements GatewayFilter {

    private static final Pattern BOOKING = Pattern.compile("^/api/v2/bookings/(\\d+)(/reschedule)?$");
    private static final Pattern CAPACITY = Pattern.compile("^/api/v2/sessions/(\\d+)/capacity$");

    private final RoutingService routing;

    public ModernApiWriteGuard(RoutingService routing) {
        this.routing = routing;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        HttpMethod method = exchange.getRequest().getMethod();
        Matcher m;
        if (HttpMethod.POST.equals(method) && path.equals("/api/v2/bookings")) {
            return BookingApiStranglerFilter.withBody(exchange, (ex, body) -> {
                JsonNode req = BookingApiStranglerFilter.parseOrNull(body);
                if (req == null || !req.hasNonNull("sessionId") || !req.get("sessionId").canConvertToLong()) {
                    return chain.filter(ex); // scheduling-service answers the validation error
                }
                return guard(ex, chain, ResolveKey.session(req.get("sessionId").asLong()), null);
            });
        }
        if ((m = BOOKING.matcher(path)).matches()
                && (HttpMethod.DELETE.equals(method) || (HttpMethod.POST.equals(method) && m.group(2) != null))) {
            return guard(exchange, chain, ResolveKey.booking(m.group(1)), Long.valueOf(m.group(1)));
        }
        if (HttpMethod.PUT.equals(method) && (m = CAPACITY.matcher(path)).matches()) {
            return guard(exchange, chain, ResolveKey.session(m.group(1)), null);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> guard(ServerWebExchange exchange, GatewayFilterChain chain, ResolveKey key, Long bookingId) {
        return routing.decide(key, bookingId, true)
                .flatMap(decision -> {
                    if (decision.isModern()) {
                        RouteAttributes.set(exchange, decision);
                        return chain.filter(exchange);
                    }
                    RouteAttributes.set(exchange, RouteDecision.legacy(decision.centerCode(), "v2-write-rejected"));
                    return Responses.json(exchange, HttpStatus.FORBIDDEN, error("CENTER_NOT_MODERN",
                            "Center " + decision.centerLabel() + " is not routed to scheduling-service"));
                })
                .onErrorResume(ResolveUnavailableException.class, e -> {
                    RouteAttributes.set(exchange, RouteDecision.legacy(null, "resolve-unavailable"));
                    return Responses.json(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            error("ROUTING_UNAVAILABLE", "Center resolution is unavailable, please retry"));
                });
    }

    private static ObjectNode error(String code, String message) {
        ObjectNode n = JsonNodeFactory.instance.objectNode();
        n.put("error", code);
        n.put("message", message);
        n.putNull("legacyCode");
        return n;
    }
}
