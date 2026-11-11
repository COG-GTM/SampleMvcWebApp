package com.northwind.assessment.gateway.filter;

import org.springframework.web.server.ServerWebExchange;

import com.northwind.assessment.gateway.routing.RouteDecision;

public final class RouteAttributes {

    public static final String DECISION = RouteAttributes.class.getName() + ".decision";
    public static final String X_ROUTE = "X-Route";

    private RouteAttributes() {
    }

    public static void set(ServerWebExchange exchange, RouteDecision decision) {
        exchange.getAttributes().put(DECISION, decision);
    }

    public static RouteDecision get(ServerWebExchange exchange) {
        RouteDecision d = exchange.getAttribute(DECISION);
        return d != null ? d : RouteDecision.legacy(null, "pass-through");
    }
}
