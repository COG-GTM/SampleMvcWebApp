package com.northwind.scheduling.observability;

import java.time.Duration;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Booking meters scraped from /actuator/prometheus (runbook: docs/runbooks/scheduling-service.md):
 * {@code scheduling_booking_duration_seconds} (histogram) and {@code scheduling_booking_errors_total}.
 */
@Component
public class BookingMetrics {

    public static final String DURATION = "scheduling.booking.duration";
    public static final String ERRORS = "scheduling.booking.errors";

    public static final String SUCCESS = "success";
    public static final String BUSINESS_ERROR = "business_error";
    public static final String ERROR = "error";

    static final Duration[] BUCKETS = {Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(100),
            Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofMillis(2500),
            Duration.ofSeconds(5), Duration.ofSeconds(10)};

    private final MeterRegistry registry;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * @param operation book | cancel | reschedule | capacity
     * @param outcome   success | business_error | error
     * @param errorCode ErrorCode name (business_error) or exception class (error); ignored on success
     */
    public void record(String operation, String outcome, String centerCode, String errorCode, long nanos) {
        Timer.builder(DURATION)
                .description("Duration of scheduling-service booking commands")
                .tags("operation", operation, "outcome", outcome, "centerCode", centerCode)
                .serviceLevelObjectives(BUCKETS)
                .register(registry)
                .record(Duration.ofNanos(nanos));
        if (!SUCCESS.equals(outcome)) {
            Counter.builder(ERRORS)
                    .description("Failed scheduling-service booking commands")
                    .tags("operation", operation, "outcome", outcome, "centerCode", centerCode, "code", errorCode)
                    .register(registry)
                    .increment();
        }
    }
}
