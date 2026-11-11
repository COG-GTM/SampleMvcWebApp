package com.northwind.routing;

import com.northwind.routing.config.RoutingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Strangler routing layer (MBA-3822): fronts the legacy portal and booking-api on :8000. */
@SpringBootApplication
@EnableConfigurationProperties(RoutingProperties.class)
public class RoutingGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RoutingGatewayApplication.class, args);
    }
}
