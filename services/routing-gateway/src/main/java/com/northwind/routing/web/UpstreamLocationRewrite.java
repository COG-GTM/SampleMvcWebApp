package com.northwind.routing.web;

import java.net.URI;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Keeps redirects from scheduling-service on the gateway (MBA-3825). Tomcat answers {@code /app} with an absolute
 * {@code Location} built from the upstream host ({@code http://localhost:8090/app/}), which would take the browser
 * off :8000. Absolute http(s) locations are turned into origin-relative ones before the response is committed.
 */
public final class UpstreamLocationRewrite implements GatewayFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        exchange.getResponse().beforeCommit(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            String location = headers.getFirst(HttpHeaders.LOCATION);
            if (location != null) {
                headers.set(HttpHeaders.LOCATION, relative(location));
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }

    static String relative(String location) {
        URI uri;
        try {
            uri = URI.create(location);
        } catch (IllegalArgumentException e) {
            return location;
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getRawAuthority() == null) {
            return location;
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
    }
}
