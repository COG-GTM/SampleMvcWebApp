package com.northwind.assessment.gateway.routing;

public enum Route {
    LEGACY, MODERN;

    public String header() {
        return name().toLowerCase();
    }
}
