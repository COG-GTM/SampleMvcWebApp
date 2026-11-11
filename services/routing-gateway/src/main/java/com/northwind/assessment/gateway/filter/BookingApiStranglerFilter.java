package com.northwind.assessment.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.northwind.assessment.gateway.config.RoutingProperties;
import com.northwind.assessment.gateway.resolve.ResolveKey;
import com.northwind.assessment.gateway.resolve.ResolveUnavailableException;
import com.northwind.assessment.gateway.routing.RouteDecision;
import com.northwind.assessment.gateway.routing.RoutingService;
import com.northwind.assessment.gateway.translate.V1Shapes;

import reactor.core.publisher.Mono;

/**
 * booking-api route: decides legacy|modern per request. Legacy continues the chain to booking-api unchanged;
 * modern is translated to scheduling-service {@code /api/v2} and the answer is mapped back to the exact v1 shape.
 */
@Component
public class BookingApiStranglerFilter implements GatewayFilter {

    static final String DEFAULT_CANCEL_REASON = "Cancelled by partner";
    static final String PARTNER_CHANNEL = "PARTNER";

    private static final Pattern SESSION = Pattern.compile("^/api/v1/sessions/(\\d+)$");
    private static final Pattern BOOKING = Pattern.compile("^/api/v1/bookings/(\\d+)$");
    private static final Pattern RESCHEDULE = Pattern.compile("^/api/v1/bookings/(\\d+)/reschedule$");

    private enum Shape { SESSION, SESSION_LIST, BOOKING, BOOKING_RESPONSE }

    private final RoutingService routing;
    private final WebClient modern;
    private final String modernBase;

    public BookingApiStranglerFilter(RoutingService routing, WebClient.Builder builder, RoutingProperties props) {
        this.routing = routing;
        this.modern = builder.build();
        String base = props.getSchedulingServiceUrl().toString();
        this.modernBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        HttpMethod method = exchange.getRequest().getMethod();
        String query = exchange.getRequest().getURI().getRawQuery();
        Matcher m;

        if (HttpMethod.GET.equals(method) && path.equals("/api/v1/sessions")) {
            String center = exchange.getRequest().getQueryParams().getFirst("centerCode");
            if (center == null || center.isBlank()) {
                return chain.filter(exchange);
            }
            return route(exchange, chain, ResolveKey.centerCode(center), null, false,
                    () -> call(HttpMethod.GET, "/api/v2/sessions" + (query == null ? "" : "?" + query), null),
                    Shape.SESSION_LIST, false);
        }
        if (HttpMethod.GET.equals(method) && (m = SESSION.matcher(path)).matches()) {
            String id = m.group(1);
            return route(exchange, chain, ResolveKey.session(id), null, false,
                    () -> call(HttpMethod.GET, "/api/v2/sessions/" + id, null), Shape.SESSION, false);
        }
        if ((m = BOOKING.matcher(path)).matches() && (HttpMethod.GET.equals(method) || HttpMethod.DELETE.equals(method))) {
            String id = m.group(1);
            Long bookingId = parseLong(id);
            if (HttpMethod.GET.equals(method)) {
                return route(exchange, chain, ResolveKey.booking(id), bookingId, false,
                        () -> call(HttpMethod.GET, "/api/v2/bookings/" + id, null), Shape.BOOKING, false);
            }
            String reason = exchange.getRequest().getQueryParams().getFirst("reason");
            String v2 = UriComponentsBuilder.fromPath("/api/v2/bookings/" + id)
                    .queryParam("reason", reason == null || reason.isBlank() ? DEFAULT_CANCEL_REASON : reason)
                    .encode().build().toUriString();
            return route(exchange, chain, ResolveKey.booking(id), bookingId, true,
                    () -> call(HttpMethod.DELETE, v2, null), Shape.BOOKING, false);
        }
        if (HttpMethod.POST.equals(method) && path.equals("/api/v1/bookings")) {
            return withBody(exchange, (ex, body) -> {
                JsonNode req = parseOrNull(body);
                if (req == null || !req.hasNonNull("sessionId") || !req.get("sessionId").canConvertToLong()) {
                    return chain.filter(ex);
                }
                ObjectNode v2 = JsonNodeFactory.instance.objectNode();
                v2.set("sessionId", req.get("sessionId"));
                for (String f : new String[] {"candidateNumber", "accommodationCode", "externalRef"}) {
                    v2.set(f, req.hasNonNull(f) ? req.get(f) : JsonNodeFactory.instance.nullNode());
                }
                v2.put("channel", PARTNER_CHANNEL);
                return route(ex, chain, ResolveKey.session(req.get("sessionId").asLong()), null, true,
                        () -> call(HttpMethod.POST, "/api/v2/bookings", V1Shapes.write(v2)), Shape.BOOKING_RESPONSE, false);
            });
        }
        if (HttpMethod.POST.equals(method) && (m = RESCHEDULE.matcher(path)).matches()) {
            String id = m.group(1);
            return withBody(exchange, (ex, body) -> {
                JsonNode req = parseOrNull(body);
                ObjectNode v2 = JsonNodeFactory.instance.objectNode();
                v2.set("newSessionId", req != null && req.has("newSessionId") ? req.get("newSessionId")
                        : JsonNodeFactory.instance.nullNode());
                // v1 reschedule answers 200 (plain @RequestMapping), v2 answers 201
                return route(ex, chain, ResolveKey.booking(id), parseLong(id), true,
                        () -> call(HttpMethod.POST, "/api/v2/bookings/" + id + "/reschedule", V1Shapes.write(v2)),
                        Shape.BOOKING_RESPONSE, true);
            });
        }
        return chain.filter(exchange);
    }

    private record V2Result(HttpStatus status, byte[] body) { }

    private Mono<Void> route(ServerWebExchange exchange, GatewayFilterChain chain, ResolveKey key, Long bookingId,
            boolean write, java.util.function.Supplier<Mono<V2Result>> v2Call, Shape shape, boolean createdAsOk) {
        return routing.decide(key, bookingId, write)
                .flatMap(decision -> {
                    RouteAttributes.set(exchange, decision);
                    if (!decision.isModern()) {
                        return chain.filter(exchange);
                    }
                    return v2Call.get()
                            .onErrorResume(e -> Mono.just(new V2Result(HttpStatus.BAD_GATEWAY, null)))
                            .flatMap(r -> respond(exchange, r, shape, createdAsOk));
                })
                .onErrorResume(ResolveUnavailableException.class, e -> {
                    RouteAttributes.set(exchange, RouteDecision.legacy(null, "resolve-unavailable"));
                    return Responses.json(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            V1Shapes.error("ROUTING_UNAVAILABLE", "Center resolution is unavailable, please retry"));
                });
    }

    private Mono<Void> respond(ServerWebExchange exchange, V2Result r, Shape shape, boolean createdAsOk) {
        JsonNode body = parseOrNull(r.body());
        if (body == null) {
            return Responses.json(exchange, HttpStatus.BAD_GATEWAY,
                    V1Shapes.error("UPSTREAM_ERROR", "scheduling-service answered " + r.status().value()));
        }
        if (r.status().isError()) {
            return Responses.json(exchange, r.status(), V1Shapes.project(body, V1Shapes.ERROR_FIELDS));
        }
        HttpStatus status = createdAsOk && r.status() == HttpStatus.CREATED ? HttpStatus.OK : r.status();
        JsonNode v1 = switch (shape) {
            case SESSION -> V1Shapes.project(body, V1Shapes.SESSION_FIELDS);
            case SESSION_LIST -> V1Shapes.sessionList(body);
            case BOOKING -> V1Shapes.project(body, V1Shapes.BOOKING_FIELDS);
            case BOOKING_RESPONSE -> V1Shapes.project(body, V1Shapes.BOOKING_RESPONSE_FIELDS);
        };
        return Responses.json(exchange, status, v1);
    }

    private Mono<V2Result> call(HttpMethod method, String uri, byte[] body) {
        WebClient.RequestBodySpec spec = modern.method(method).uri(java.net.URI.create(modernBase + uri)).accept(MediaType.APPLICATION_JSON);
        WebClient.RequestHeadersSpec<?> req = body == null ? spec
                : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
        return req.exchangeToMono(resp -> resp.bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                .map(b -> new V2Result(HttpStatus.valueOf(resp.statusCode().value()), b)));
    }

    private interface BodyHandler {
        Mono<Void> handle(ServerWebExchange exchange, byte[] body);
    }

    /** Reads the request body once and keeps it replayable for the legacy route. */
    private static Mono<Void> withBody(ServerWebExchange exchange, BodyHandler handler) {
        return ServerWebExchangeUtils.cacheRequestBody(exchange, cachedRequest -> {
            DataBuffer buf = exchange.getAttribute(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR);
            byte[] bytes = buf == null ? new byte[0]
                    : buf.toString(buf.readPosition(), buf.readableByteCount(), StandardCharsets.UTF_8)
                            .getBytes(StandardCharsets.UTF_8);
            return handler.handle(exchange.mutate().request(cachedRequest).build(), bytes);
        });
    }

    private static JsonNode parseOrNull(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            return V1Shapes.parse(body);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
