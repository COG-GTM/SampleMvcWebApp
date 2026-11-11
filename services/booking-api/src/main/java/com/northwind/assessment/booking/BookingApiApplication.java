package com.northwind.assessment.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Partner booking channel (booking-api).
 *
 * Partners (resellers, training providers) book seats on exam sessions over REST.
 * Writes go to the same AssessmentDelivery database as the portal.
 */
@SpringBootApplication
public class BookingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApiApplication.class, args);
    }
}
