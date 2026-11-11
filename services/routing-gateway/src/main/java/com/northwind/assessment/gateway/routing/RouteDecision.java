package com.northwind.assessment.gateway.routing;

/** Outcome of {@link RouteDecider}: where the request goes, for which center, and why. */
public record RouteDecision(Route route, String centerCode, String reason) {

    public static RouteDecision legacy(String centerCode, String reason) {
        return new RouteDecision(Route.LEGACY, centerCode, reason);
    }

    public static RouteDecision modern(String centerCode, String reason) {
        return new RouteDecision(Route.MODERN, centerCode, reason);
    }

    public boolean isModern() {
        return route == Route.MODERN;
    }

    public String centerLabel() {
        return centerCode == null ? "-" : centerCode;
    }
}
