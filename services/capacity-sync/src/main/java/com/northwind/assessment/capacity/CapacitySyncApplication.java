package com.northwind.assessment.capacity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Nightly housekeeping: re-syncs ExamSession.SeatsAvailable from the Booking table
 * so the counter does not wander after manual fixes in the admin screens.
 */
@SpringBootApplication
@EnableScheduling
public class CapacitySyncApplication {

    public static void main(String[] args) {
        SpringApplication.run(CapacitySyncApplication.class, args);
    }
}
