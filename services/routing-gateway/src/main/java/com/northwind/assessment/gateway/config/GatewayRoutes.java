package com.northwind.assessment.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.northwind.assessment.gateway.filter.BookingApiStranglerFilter;
import com.northwind.assessment.gateway.filter.PortalStranglerFilter;
import com.northwind.assessment.gateway.filter.RouteAttributes;
import com.northwind.assessment.gateway.routing.RouteDecision;

/**
 * Route table. Order matters: specific upstreams first, the portal catch-all last.
 * <pre>
 *   /app, /app/**, /api/v2/**   -> scheduling-service (modern UI + API, always X-Route: modern)
 *   /api/v1/results/**          -> results-service   (unchanged)
 *   /sync/**                    -> capacity-sync     (unchanged)
 *   /api/v1/**                  -> booking-api       (strangler: allow-listed centers translated to /api/v2)
 *   /**                         -> portal            (strangler: allow-listed booking/admin pages -> /app)
 * </pre>
 */
@Configuration
public class GatewayRoutes {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, RoutingProperties props,
            BookingApiStranglerFilter bookingApi, PortalStranglerFilter portal) {
        return builder.routes()
                .route("modern-ui-and-api", r -> r.path("/app", "/app/**", "/api/v2/**")
                        .filters(f -> f.filter((exchange, chain) -> {
                            String center = exchange.getRequest().getQueryParams().getFirst("centerCode");
                            RouteAttributes.set(exchange, RouteDecision.modern(center, "modern-path"));
                            return chain.filter(exchange);
                        }))
                        .uri(props.getSchedulingServiceUrl()))
                .route("results-service", r -> r.path("/api/v1/results", "/api/v1/results/**")
                        .uri(props.getResultsServiceUrl()))
                .route("capacity-sync", r -> r.path("/sync", "/sync/**")
                        .uri(props.getCapacitySyncUrl()))
                .route("booking-api", r -> r.path("/api/v1/**")
                        .filters(f -> f.filter(bookingApi))
                        .uri(props.getBookingApiUrl()))
                .route("portal", r -> r.path("/**")
                        .filters(f -> f.filter(portal))
                        .uri(props.getPortalUrl()))
                .build();
    }
}
