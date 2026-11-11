package com.northwind.assessment.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
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
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import com.northwind.assessment.gateway.resolve.ResolveKey;
import com.northwind.assessment.gateway.resolve.ResolveUnavailableException;
import com.northwind.assessment.gateway.routing.RouteDecision;
import com.northwind.assessment.gateway.routing.RoutingService;

import reactor.core.publisher.Mono;

/**
 * Portal route: booking/admin pages of an allow-listed center redirect to the modern UI deep links under
 * {@code /app} (served by scheduling-service, proxied through this gateway). Everything else, and every page of a
 * non-allow-listed center, is proxied to the MVC 5 portal unchanged. Paths match case-insensitively like MVC routing.
 */
@Component
public class PortalStranglerFilter implements GatewayFilter {

    private static final Pattern BOOKING_INDEX = Pattern.compile("^/booking(/index)?/?$");
    private static final Pattern BOOK = Pattern.compile("^/booking/book/?$");
    private static final Pattern MY_BOOKINGS = Pattern.compile("^/booking/mybookings/?$");
    private static final Pattern CANCEL = Pattern.compile("^/booking/cancel/(\\d+)/?$");
    private static final Pattern RESCHEDULE = Pattern.compile("^/booking/reschedule/(\\d+)/?$");
    private static final Pattern ADMIN_SESSIONS = Pattern.compile("^/admin/sessions/?$");
    private static final Pattern CAPACITY = Pattern.compile("^/admin/sessions/capacity/(\\d+)/?$");

    private final RoutingService routing;

    public PortalStranglerFilter(RoutingService routing) {
        this.routing = routing;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath().toLowerCase(Locale.ROOT);
        HttpMethod method = exchange.getRequest().getMethod();
        if (!path.startsWith("/booking") && !path.startsWith("/admin/sessions")) {
            return chain.filter(exchange);
        }
        boolean post = HttpMethod.POST.equals(method);
        boolean form = post && MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(
                exchange.getRequest().getHeaders().getContentType());
        if (!post && !HttpMethod.GET.equals(method)) {
            return chain.filter(exchange);
        }
        if (!form) {
            return dispatch(exchange, chain, path, post, params(exchange.getRequest().getQueryParams(), null));
        }
        return ServerWebExchangeUtils.cacheRequestBody(exchange, cachedRequest -> {
            DataBuffer buf = exchange.getAttribute(ServerWebExchangeUtils.CACHED_REQUEST_BODY_ATTR);
            String body = buf == null ? "" : buf.toString(buf.readPosition(), buf.readableByteCount(), StandardCharsets.UTF_8);
            ServerWebExchange ex = exchange.mutate().request(cachedRequest).build();
            return dispatch(ex, chain, path, true, params(exchange.getRequest().getQueryParams(), body));
        });
    }

    private Mono<Void> dispatch(ServerWebExchange exchange, GatewayFilterChain chain, String path, boolean post,
            Map<String, String> p) {
        Matcher m;
        if (BOOKING_INDEX.matcher(path).matches() && has(p, "centercode")) {
            return route(exchange, chain, ResolveKey.centerCode(p.get("centercode")), null, post,
                    d -> link("/app").queryParam("centerCode", d.centerCode())
                            .queryParam("examCode", p.getOrDefault("examcode", ""))
                            .queryParam("date", p.getOrDefault("from", "")));
        }
        if (BOOK.matcher(path).matches() && isNumber(p.get("sessionid"))) {
            return route(exchange, chain, ResolveKey.session(p.get("sessionid")), null, post,
                    d -> withCandidate(link("/app/book").queryParam("sessionId", p.get("sessionid")), p));
        }
        if (MY_BOOKINGS.matcher(path).matches() && has(p, "centercode")) {
            return route(exchange, chain, ResolveKey.centerCode(p.get("centercode")), null, post,
                    d -> withCandidate(link("/app/my-bookings"), p));
        }
        if ((m = CANCEL.matcher(path)).matches()) {
            String id = m.group(1);
            return route(exchange, chain, ResolveKey.booking(id), Long.valueOf(id), post,
                    d -> withCandidate(link("/app/my-bookings"), p));
        }
        if ((m = RESCHEDULE.matcher(path)).matches()) {
            String id = m.group(1);
            return route(exchange, chain, ResolveKey.booking(id), Long.valueOf(id), post,
                    d -> withCandidate(link("/app/reschedule/" + id), p));
        }
        if ((m = CAPACITY.matcher(path)).matches()) {
            String id = m.group(1);
            return route(exchange, chain, ResolveKey.session(id), null, post,
                    d -> link("/app/admin/sessions/" + id));
        }
        if (ADMIN_SESSIONS.matcher(path).matches() && isNumber(p.get("centerid"))) {
            return route(exchange, chain, ResolveKey.centerId(p.get("centerid")), null, post,
                    d -> link("/app/admin/centers/" + d.centerCode()));
        }
        return chain.filter(exchange);
    }

    private Mono<Void> route(ServerWebExchange exchange, GatewayFilterChain chain, ResolveKey key, Long bookingId,
            boolean post, java.util.function.Function<RouteDecision, UriComponentsBuilder> target) {
        return routing.decide(key, bookingId, post)
                .flatMap(d -> {
                    RouteAttributes.set(exchange, d);
                    if (!d.isModern()) {
                        return chain.filter(exchange);
                    }
                    return Responses.redirect(exchange, post, target.apply(d).encode().build().toUriString());
                })
                .onErrorResume(ResolveUnavailableException.class, e -> {
                    RouteAttributes.set(exchange, RouteDecision.legacy(null, "resolve-unavailable"));
                    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
                    return exchange.getResponse().setComplete();
                });
    }

    private static UriComponentsBuilder link(String path) {
        return UriComponentsBuilder.fromPath(path);
    }

    private static UriComponentsBuilder withCandidate(UriComponentsBuilder b, Map<String, String> p) {
        return has(p, "candidatenumber") ? b.queryParam("candidateNumber", p.get("candidatenumber")) : b;
    }

    /** Query string plus form fields, keys lower-cased (MVC model binding is case-insensitive). */
    static Map<String, String> params(MultiValueMap<String, String> query, String formBody) {
        Map<String, String> out = new TreeMap<>();
        query.forEach((k, v) -> {
            if (!v.isEmpty() && v.get(0) != null) {
                out.putIfAbsent(k.toLowerCase(Locale.ROOT), v.get(0));
            }
        });
        if (formBody != null && !formBody.isEmpty()) {
            UriComponentsBuilder.newInstance().query(formBody).build().getQueryParams().forEach((k, v) -> {
                if (!v.isEmpty() && v.get(0) != null) {
                    out.putIfAbsent(decode(k).toLowerCase(Locale.ROOT), decode(v.get(0)));
                }
            });
        }
        return out;
    }

    private static String decode(String s) {
        return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static boolean has(Map<String, String> p, String key) {
        String v = p.get(key);
        return v != null && !v.isBlank();
    }

    private static boolean isNumber(String s) {
        return s != null && s.matches("\\d{1,18}");
    }
}
