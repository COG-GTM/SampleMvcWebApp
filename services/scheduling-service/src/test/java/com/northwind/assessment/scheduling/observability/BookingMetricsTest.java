package com.northwind.assessment.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.northwind.assessment.scheduling.domain.RuleViolation;
import com.northwind.assessment.scheduling.domain.SchedulingRuleException;
import com.northwind.assessment.scheduling.service.NotFoundException;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessResourceFailureException;

class BookingMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BookingMetrics metrics = new BookingMetrics(registry);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void success_recordsTimerWithResolvedCenterAndNoErrorCounter() {
        String result = metrics.record(BookingMetrics.BOOK, () -> {
            LogContext.centerCode("BOS01");
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        Timer t = registry.find(BookingMetrics.DURATION)
                .tags("operation", "book", "outcome", "success", "centerCode", "BOS01").timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1);
        assertThat(registry.find(BookingMetrics.ERRORS).counters()).isEmpty();
    }

    @Test
    void ruleViolation_isBusinessErrorAndCounted() {
        assertThatThrownBy(() -> metrics.record(BookingMetrics.CAPACITY, () -> {
            LogContext.centerCode("SEA01");
            throw new SchedulingRuleException(RuleViolation.VALIDATION, "newCapacity is required");
        })).isInstanceOf(SchedulingRuleException.class);

        assertThat(registry.find(BookingMetrics.DURATION)
                .tags("operation", "capacity", "outcome", "business_error", "centerCode", "SEA01").timer().count()).isEqualTo(1);
        assertThat(registry.find(BookingMetrics.ERRORS)
                .tags("operation", "capacity", "outcome", "business_error", "centerCode", "SEA01").counter().count()).isEqualTo(1);
    }

    @Test
    void notFound_withoutResolvedCenter_isBusinessErrorTaggedUnknown() {
        assertThatThrownBy(() -> metrics.record(BookingMetrics.CANCEL, () -> {
            throw new NotFoundException("BOOKING_NOT_FOUND", "Booking 1 not found");
        })).isInstanceOf(NotFoundException.class);

        assertThat(registry.find(BookingMetrics.ERRORS)
                .tags("operation", "cancel", "outcome", "business_error", "centerCode", "unknown").counter().count()).isEqualTo(1);
    }

    @Test
    void unexpectedException_isErrorAndRethrownUnchanged() {
        DataAccessResourceFailureException boom = new DataAccessResourceFailureException("db down");
        assertThatThrownBy(() -> metrics.record(BookingMetrics.RESCHEDULE, () -> {
            throw boom;
        })).isSameAs(boom);

        assertThat(registry.find(BookingMetrics.ERRORS)
                .tags("operation", "reschedule", "outcome", "error", "centerCode", "unknown").counter().count()).isEqualTo(1);
        assertThat(registry.find(BookingMetrics.DURATION).tags("outcome", "error").timer().count()).isEqualTo(1);
    }

    @Test
    void outcomeClassification() {
        assertThat(BookingMetrics.outcome(new SchedulingRuleException(RuleViolation.SESSION_NOT_OPEN, "x"))).isEqualTo("business_error");
        assertThat(BookingMetrics.outcome(new NotFoundException("SESSION_NOT_FOUND", "x"))).isEqualTo("business_error");
        assertThat(BookingMetrics.outcome(new ValidationException("x"))).isEqualTo("business_error");
        assertThat(BookingMetrics.outcome(new IllegalStateException("x"))).isEqualTo("error");
        assertThat(BookingMetrics.outcome(new OutOfMemoryError("x"))).isEqualTo("error");
    }

    @Test
    void staleCenterFromAPreviousOperationIsNotReused() {
        MDC.put(LogContext.CENTER_CODE, "AKL01");
        MDC.put(LogContext.BOOKING_ID, "7");
        metrics.record(BookingMetrics.BOOK, () -> "ok");

        assertThat(registry.find(BookingMetrics.DURATION).tags("centerCode", "unknown").timer()).isNotNull();
        assertThat(registry.find(BookingMetrics.DURATION).tags("centerCode", "AKL01").timer()).isNull();
        assertThat(MDC.get(LogContext.BOOKING_ID)).isNull();
    }

    @Test
    void prometheusNamesAndHistogramBuckets() {
        PrometheusMeterRegistry prometheus = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        BookingMetrics m = new BookingMetrics(prometheus);
        m.record(BookingMetrics.BOOK, () -> {
            LogContext.centerCode("BOS01");
            return "ok";
        });
        assertThatThrownBy(() -> m.record(BookingMetrics.CANCEL, () -> {
            throw new NotFoundException("BOOKING_NOT_FOUND", "x");
        }));

        String scrape = prometheus.scrape();
        assertThat(scrape).contains("# TYPE scheduling_booking_duration_seconds histogram")
                .contains("scheduling_booking_duration_seconds_bucket{centerCode=\"BOS01\",operation=\"book\","
                        + "outcome=\"success\",le=\"0.25\"} 1")
                .contains("scheduling_booking_duration_seconds_bucket{centerCode=\"BOS01\",operation=\"book\","
                        + "outcome=\"success\",le=\"+Inf\"} 1")
                .contains("scheduling_booking_errors_total{centerCode=\"unknown\",operation=\"cancel\",outcome=\"business_error\"} 1");
        assertThat(scrape.lines().filter(l -> l.startsWith("scheduling_booking_duration_seconds_bucket{centerCode=\"BOS01\"")))
                .hasSize(BookingMetrics.BUCKETS.length + 1);
    }
}
