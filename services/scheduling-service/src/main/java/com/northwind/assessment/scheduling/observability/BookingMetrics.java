package com.northwind.assessment.scheduling.observability;

import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.service.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ValidationException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Times the booking use cases as {@code scheduling.booking.duration} (Prometheus
 * {@code scheduling_booking_duration_seconds}, histogram) and counts failures as {@code scheduling.booking.errors}
 * ({@code scheduling_booking_errors_total}). Tags: {@code operation}, {@code outcome}, {@code centerCode}. The center
 * code is the one the operation resolved from the Scheduling DB ({@link LogContext#centerCode}), otherwise
 * {@value LogContext#UNKNOWN_CENTER}, so its cardinality is bounded by the number of test centers.
 */
@Component
public class BookingMetrics {

    public static final String DURATION = "scheduling.booking.duration";

    /** Fixed histogram buckets (le=0.01 .. 10 s): enough for the runbook's p95/p99 queries, bounded series count. */
    static final Duration[] BUCKETS = Stream.of(10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000)
            .map(Duration::ofMillis).toArray(Duration[]::new);
    public static final String ERRORS = "scheduling.booking.errors";

    public static final String BOOK = "book";
    public static final String CANCEL = "cancel";
    public static final String RESCHEDULE = "reschedule";
    public static final String CAPACITY = "capacity";

    public static final String SUCCESS = "success";
    public static final String BUSINESS_ERROR = "business_error";
    public static final String ERROR = "error";

    private final MeterRegistry registry;

    public BookingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Runs {@code action}, recording its duration and outcome. Exceptions are rethrown unchanged. */
    public <T> T record(String operation, Supplier<T> action) {
        LogContext.clearOperation();
        long start = System.nanoTime();
        String outcome = SUCCESS;
        try {
            return action.get();
        } catch (RuntimeException | Error e) {
            outcome = outcome(e);
            throw e;
        } finally {
            long elapsed = System.nanoTime() - start;
            String center = LogContext.centerCodeOrUnknown();
            timer(operation, outcome, center).record(elapsed, TimeUnit.NANOSECONDS);
            if (!SUCCESS.equals(outcome)) {
                Counter.builder(ERRORS)
                        .description("Booking operations that did not succeed")
                        .tags("operation", operation, "outcome", outcome, "centerCode", center)
                        .register(registry)
                        .increment();
            }
        }
    }

    /** Rule violations, unknown ids and validation failures are business errors; anything else is an error. */
    public static String outcome(Throwable e) {
        if (e instanceof SchedulingRuleException || e instanceof NotFoundException || e instanceof ValidationException) {
            return BUSINESS_ERROR;
        }
        return ERROR;
    }

    private Timer timer(String operation, String outcome, String center) {
        return Timer.builder(DURATION)
                .description("Duration of booking, cancellation, reschedule and capacity operations (incl. commit)")
                .tags("operation", operation, "outcome", outcome, "centerCode", center)
                .serviceLevelObjectives(BUCKETS)
                .register(registry);
    }
}
