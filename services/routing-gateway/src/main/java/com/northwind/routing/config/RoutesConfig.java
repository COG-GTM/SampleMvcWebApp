package com.northwind.routing.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Upstream routes. Which requests leave legacy is decided per request by
 * {@link com.northwind.routing.web.StranglerFilter}; these routes only say where each family of paths lives.
 */
@Configuration
public class RoutesConfig {

    public static final String MODERN = "modern";
    public static final String RESULTS = "results-service";
    public static final String CAPACITY_SYNC = "capacity-sync";
    public static final String BOOKING_API = "booking-api";
    public static final String PORTAL = "portal";

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, RoutingProperties props) {
        return builder.routes()
                .route(MODERN, r -> r.order(0).path("/app", "/app/**", "/api/v2/**").uri(props.getSchedulingServiceUrl()))
                .route(RESULTS, r -> r.order(1).path("/api/v1/results", "/api/v1/results/**").uri(props.getResultsServiceUrl()))
                .route(CAPACITY_SYNC, r -> r.order(2).path("/sync/**").uri(props.getCapacitySyncUrl()))
                .route(BOOKING_API, r -> r.order(3).path("/api/v1/**").uri(props.getBookingApiUrl()))
                .route(PORTAL, r -> r.order(100).path("/**").uri(props.getPortalUrl()))
                .build();
    }
}
