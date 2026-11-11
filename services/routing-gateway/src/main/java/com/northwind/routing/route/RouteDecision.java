package com.northwind.routing.route;

/** Outcome of {@link RouteDecider}: where the request goes, which centre it belongs to (null if unknown) and why. */
public record RouteDecision(Target target, String center, String reason) {

    public enum Target {
        LEGACY, MODERN;

        public String header() {
            return name().toLowerCase();
        }
    }

    public boolean modern() {
        return target == Target.MODERN;
    }

    public String centerOrDash() {
        return center == null || center.isBlank() ? "-" : center;
    }

    public static RouteDecision legacy(String center, String reason) {
        return new RouteDecision(Target.LEGACY, center, reason);
    }

    public static RouteDecision modern(String center, String reason) {
        return new RouteDecision(Target.MODERN, center, reason);
    }
}
