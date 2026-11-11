package com.northwind.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** scheduling-service (MBA-3821): Java 21 / Spring Boot 3, own DB "Scheduling", /api/v2 and the /app UI on :8090. */
@SpringBootApplication
public class SchedulingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SchedulingServiceApplication.class, args);
    }
}
