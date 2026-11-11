package com.northwind.assessment.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.northwind.assessment.gateway.routing.RouteDecision;

import reactor.core.publisher.Mono;

/** Adds {@code X-Route} to every routed response and logs {@code route=legacy|modern center=<code>}. */
@Component
public class RouteTagFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger("routing");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().set(RouteAttributes.X_ROUTE, RouteAttributes.get(exchange).route().header());
            return Mono.empty();
        });
        return chain.filter(exchange).doFinally(signal -> {
            RouteDecision d = RouteAttributes.get(exchange);
            log.info("route={} center={} reason={} method={} path={} status={}", d.route().header(), d.centerLabel(),
                    d.reason(), exchange.getRequest().getMethod(), exchange.getRequest().getURI().getRawPath()
                            + (exchange.getRequest().getURI().getRawQuery() == null ? ""
                                    : "?" + exchange.getRequest().getURI().getRawQuery()),
                    response.getStatusCode() == null ? "-" : response.getStatusCode().value());
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
