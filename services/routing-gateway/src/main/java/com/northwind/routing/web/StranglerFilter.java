package com.northwind.routing.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.northwind.routing.config.RoutesConfig;
import com.northwind.routing.config.RoutingProperties;
import com.northwind.routing.policy.AllowList;
import com.northwind.routing.resolve.CenterResolver;
import com.northwind.routing.route.ApiRoutes;
import com.northwind.routing.route.Lookup;
import com.northwind.routing.route.PortalRoutes;
import com.northwind.routing.route.RouteDecider;
import com.northwind.routing.route.RouteDecision;
import com.northwind.routing.translate.ModernV1Client;
import com.northwind.routing.translate.V1Shapes;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The strangler (MBA-3822). For every request: works out the test centre where the route is one the
 * migration can move, decides legacy vs modern ({@link RouteDecider}), then either forwards to the legacy
 * upstream unchanged, redirects a portal page to the modern UI ({@link PortalRoutes}), or serves a v1 partner
 * call from scheduling-service in the v1 shape ({@link ModernV1Client}). With an empty allow-list no lookups are
 * made and no bodies are read, so the gateway is a transparent proxy. Every request is logged
 * {@code route=legacy|modern center=<code>} and answered with an {@code X-Route} header.
 */
@Component
public class StranglerFilter implements GlobalFilter, Ordered {

    static final String CENTER_IS_MODERN = "CENTER_IS_MODERN";

    public static final String X_ROUTE = "X-Route";
    private static final Logger log = LoggerFactory.getLogger(StranglerFilter.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AllowList allowList;
    private final CenterResolver resolver;
    private final ModernV1Client v1;
    private final ModernApiWriteGuard guard;
    private final ObjectMapper json;
    private final long floor;

    public StranglerFilter(AllowList allowList, CenterResolver resolver, ModernV1Client v1, ModernApiWriteGuard guard,
                           ObjectMapper json, RoutingProperties props) {
        this.allowList = allowList;
        this.resolver = resolver;
        this.v1 = v1;
        this.guard = guard;
        this.json = json;
        this.floor = props.getModernBookingIdFloor();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String id = route == null ? "" : route.getId();
        return switch (id) {
            case RoutesConfig.PORTAL -> portal(exchange, chain);
            case RoutesConfig.BOOKING_API -> bookingApi(exchange, chain);
            case RoutesConfig.MODERN -> modern(exchange, chain);
            default -> forward(exchange, chain, RouteDecision.legacy(null, id + " pass-through"));
        };
    }

    private Mono<Void> portal(ServerWebExchange ex, GatewayFilterChain chain) {
        ServerHttpRequest req = ex.getRequest();
        Optional<PortalRoutes.Match> found = PortalRoutes.match(req.getMethod().name(), req.getPath().value(), req.getQueryParams());
        if (found.isEmpty()) {
            return forward(ex, chain, RouteDecision.legacy(null, "not a migrating route"));
        }
        PortalRoutes.Match m = found.get();
        if (!m.needsForm()) {
            return decide(m.lookup()).flatMap(d -> d.modern()
                    ? redirect(ex, PortalRoutes.redirect(m, d.center(), req.getQueryParams()), d)
                    : forward(ex, chain, d));
        }
        if (allowList.isEmpty()) {
            return forward(ex, chain, RouteDecision.legacy(null, "allow-list empty"));
        }
        return BodyCache.cache(ex).flatMap(cached -> {
            PortalRoutes.Match withKey = m.withLookup(Lookup.of(Lookup.Kind.SESSION_ID,
                    BodyCache.formField(BodyCache.bytes(cached), "SessionId")));
            return decide(withKey.lookup()).flatMap(d -> d.modern()
                    ? redirect(cached, PortalRoutes.redirect(withKey, d.center(), req.getQueryParams()), d)
                    : forward(cached, chain, d));
        });
    }

    private Mono<Void> bookingApi(ServerWebExchange ex, GatewayFilterChain chain) {
        ServerHttpRequest req = ex.getRequest();
        Optional<ApiRoutes.V1> found = ApiRoutes.matchV1(req.getMethod().name(), req.getPath().value(), req.getQueryParams());
        if (found.isEmpty()) {
            return forward(ex, chain, RouteDecision.legacy(null, "not a migrating route"));
        }
        ApiRoutes.V1 m = found.get();
        boolean needsBody = m.needsBody() || m.op() == ApiRoutes.V1Op.RESCHEDULE;
        if (!needsBody) {
            return decide(m.lookup()).flatMap(d -> d.modern() ? serveV1(ex, m, null, d) : forward(ex, chain, d));
        }
        if (allowList.isEmpty() && !RouteDecider.modernOwned(m.lookup().bookingId(), floor)) {
            return forward(ex, chain, RouteDecision.legacy(null, "allow-list empty"));
        }
        return BodyCache.cache(ex).flatMap(cached -> {
            Map<String, Object> body = parse(BodyCache.bytes(cached));
            Lookup key = m.needsBody()
                    ? Lookup.of(Lookup.Kind.SESSION_ID, ModernApiWriteGuard.number(body, "sessionId"))
                    : m.lookup();
            return decide(key).flatMap(d -> m.op() == ApiRoutes.V1Op.RESCHEDULE
                    ? reschedule(cached, chain, m, body, d)
                    : d.modern() ? serveV1(cached, m, body, d) : forward(cached, chain, d));
        });
    }

    /** A reschedule must not move a booking onto a seat the other side owns. */
    private Mono<Void> reschedule(ServerWebExchange ex, GatewayFilterChain chain, ApiRoutes.V1 m,
                                  Map<String, Object> body, RouteDecision d) {
        if (d.modern()) {
            return guard.deniedCenter(new ApiRoutes.V2Write(ApiRoutes.V2Op.RESCHEDULE, m.id()), body).flatMap(denied ->
                    denied.isPresent()
                            ? writeJson(ex, HttpStatus.FORBIDDEN.value(), V1Shapes.error(ModernApiWriteGuard.CENTER_NOT_MODERN,
                                    ModernApiWriteGuard.message(denied.get())), d)
                            : serveV1(ex, m, body, d));
        }
        Lookup destination = Lookup.of(Lookup.Kind.SESSION_ID, ModernApiWriteGuard.number(body, "newSessionId"));
        if (allowList.isEmpty() || !destination.present()) {
            return forward(ex, chain, d);
        }
        return resolver.resolve(destination).flatMap(c -> c.filter(allowList::contains).isPresent()
                ? writeJson(ex, HttpStatus.FORBIDDEN.value(), V1Shapes.error(CENTER_IS_MODERN, "Centre " + c.get()
                        + " is on routing.modern-centers; a legacy booking cannot be moved onto its sessions"), d)
                : forward(ex, chain, d));
    }

    private Mono<Void> modern(ServerWebExchange ex, GatewayFilterChain chain) {
        ServerHttpRequest req = ex.getRequest();
        if (internalPath(req.getPath().value()) || attendancePath(req.getPath().value())) {
            return writeJson(ex, HttpStatus.NOT_FOUND.value(), V1Shapes.error("NOT_FOUND",
                    "scheduling-service internal endpoints are not exposed through the gateway"),
                    RouteDecision.modern(null, "internal endpoint blocked"));
        }
        Optional<ApiRoutes.V2Write> write = ApiRoutes.matchV2Write(req.getMethod().name(), req.getPath().value());
        if (write.isEmpty()) {
            return forward(ex, chain, RouteDecision.modern(null, "modern path"));
        }
        Mono<ServerWebExchange> prepared = req.getMethod() == HttpMethod.POST ? BodyCache.cache(ex) : Mono.just(ex);
        return prepared.flatMap(cached -> {
            Map<String, Object> body = parse(BodyCache.bytes(cached));
            return guard.deniedCenter(write.get(), body).flatMap(denied -> {
                if (denied.isPresent()) {
                    RouteDecision d = RouteDecision.modern(denied.get(), ModernApiWriteGuard.CENTER_NOT_MODERN);
                    return writeJson(cached, HttpStatus.FORBIDDEN.value(), V1Shapes.error(ModernApiWriteGuard.CENTER_NOT_MODERN,
                            ModernApiWriteGuard.message(denied.get())), d);
                }
                return forward(cached, chain, RouteDecision.modern(null, "modern write"));
            });
        });
    }

    static boolean internalPath(String rawPath) {
        return normalised(rawPath).startsWith("/api/v2/internal");
    }

    /** MBA-3823: attendance is service-to-service (results-service calls :8090 directly), never from outside. */
    static boolean attendancePath(String rawPath) {
        return normalised(rawPath).matches("/api/v2/bookings/[^/]+/attendance/?");
    }

    private static String normalised(String rawPath) {
        String p = UriUtils.decode(rawPath, StandardCharsets.UTF_8).replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
        return p.toLowerCase(Locale.ROOT);
    }

    /** Resolves the request's centre (only when it can matter) and applies {@link RouteDecider}. */
    Mono<RouteDecision> decide(Lookup lookup) {
        Long bookingId = lookup.bookingId();
        boolean owned = RouteDecider.modernOwned(bookingId, floor);
        if (!owned && (allowList.isEmpty() || !lookup.present())) {
            return Mono.just(RouteDecider.decide(allowList.centers(), null, bookingId, floor));
        }
        return resolver.resolve(lookup).map(c -> RouteDecider.decide(allowList.centers(), c.orElse(null), bookingId, floor));
    }

    private Mono<Void> serveV1(ServerWebExchange ex, ApiRoutes.V1 m, Map<String, Object> body, RouteDecision d) {
        return v1.call(m, ex.getRequest().getQueryParams(), body).flatMap(r -> writeJson(ex, r.status(), r.body(), d));
    }

    private Mono<Void> forward(ServerWebExchange ex, GatewayFilterChain chain, RouteDecision d) {
        mark(ex, d);
        return chain.filter(ex);
    }

    private Mono<Void> redirect(ServerWebExchange ex, String location, RouteDecision d) {
        mark(ex, d);
        ServerHttpResponse res = ex.getResponse();
        HttpMethod method = ex.getRequest().getMethod();
        res.setStatusCode(method == HttpMethod.GET || method == HttpMethod.HEAD ? HttpStatus.FOUND : HttpStatus.SEE_OTHER);
        res.getHeaders().set("Location", location);
        return res.setComplete();
    }

    private Mono<Void> writeJson(ServerWebExchange ex, int status, Object body, RouteDecision d) {
        mark(ex, d);
        ServerHttpResponse res = ex.getResponse();
        res.setRawStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes;
        try {
            bytes = json.writeValueAsBytes(body);
        } catch (Exception e) {
            return Mono.error(e);
        }
        return res.writeWith(Mono.just(res.bufferFactory().wrap(bytes)));
    }

    private void mark(ServerWebExchange ex, RouteDecision d) {
        ex.getResponse().getHeaders().set(X_ROUTE, d.target().header());
        ServerHttpRequest req = ex.getRequest();
        log.info("route={} center={} {} {} ({})", d.target().header(), d.centerOrDash(), req.getMethod(),
                req.getURI().getRawPath(), d.reason());
    }

    private Map<String, Object> parse(byte[] body) {
        if (body.length == 0) {
            return Map.of();
        }
        try {
            Map<String, Object> m = json.readValue(body, MAP);
            return m == null ? Map.of() : m;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
