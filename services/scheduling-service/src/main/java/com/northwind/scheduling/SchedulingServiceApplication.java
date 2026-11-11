package com.northwind.scheduling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** scheduling-service (MBA-3821): Java 21 / Spring Boot 3, own DB "Scheduling", /api/v2 and the /app UI on :8090. */
@SpringBootApplication
public class SchedulingServiceApplication {
    public static void main(String[] args) {
        if (args.length > 0 && "reconcile".equals(args[0])) {
            System.exit(com.northwind.scheduling.reconciliation.ReconcileCommand.run(SchedulingServiceApplication.class, args));
        }
        SpringApplication.run(SchedulingServiceApplication.class, args);
    }
}
