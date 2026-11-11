package com.northwind.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.boot.actuate.health.Status;
import org.springframework.dao.DataAccessResourceFailureException;

import com.northwind.scheduling.domain.ErrorCode;
import com.northwind.scheduling.domain.SchedulingException;
import com.northwind.scheduling.legacy.LegacyReferenceSource;
import com.northwind.scheduling.persistence.SchedulingRepository;
import com.northwind.scheduling.service.SchedulingService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ObservabilityUnitTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    private static SchedulingService instrumented(SchedulingService target, SchedulingRepository repo, SimpleMeterRegistry reg) {
        AspectJProxyFactory f = new AspectJProxyFactory(target);
        f.setProxyTargetClass(true);
        f.addAspect(new BookingOperationsAspect(new BookingMetrics(reg), repo));
        return f.getProxy();
    }

    @Test
    void infrastructureFailureIsOutcomeErrorAndCountsAnError() {
        SchedulingService target = mock(SchedulingService.class);
        SchedulingRepository repo = mock(SchedulingRepository.class);
        when(repo.sessionCenterCode(154)).thenReturn(Optional.of("SFO01"));
        when(target.book(any())).thenThrow(new DataAccessResourceFailureException("db down"));
        SimpleMeterRegistry reg = new SimpleMeterRegistry();

        assertThatThrownBy(() -> instrumented(target, repo, reg)
                .book(new SchedulingService.BookCommand(154, "NW00000920", null, null, null)))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(reg.get(BookingMetrics.DURATION).tags("operation", "book", "outcome", "error", "centerCode", "SFO01")
                .timer().count()).isEqualTo(1);
        assertThat(reg.get(BookingMetrics.ERRORS).tags("operation", "book", "outcome", "error", "centerCode", "SFO01",
                "code", "DataAccessResourceFailureException").counter().count()).isEqualTo(1);
        assertThat(MDC.getCopyOfContextMap()).as("no MDC leak outside a request").isNullOrEmpty();
    }

    @Test
    void ruleViolationIsBusinessErrorAndUnknownCentreWhenLookupFails() {
        SchedulingService target = mock(SchedulingService.class);
        SchedulingRepository repo = mock(SchedulingRepository.class);
        when(repo.booking(77)).thenThrow(new DataAccessResourceFailureException("db down"));
        when(target.cancel(77, null)).thenThrow(new SchedulingException(ErrorCode.NOT_CANCELLABLE, "no"));
        SimpleMeterRegistry reg = new SimpleMeterRegistry();

        assertThatThrownBy(() -> instrumented(target, repo, reg).cancel(77, null)).isInstanceOf(SchedulingException.class);

        assertThat(reg.get(BookingMetrics.ERRORS).tags("operation", "cancel", "outcome", "business_error",
                "centerCode", "unknown", "code", "NOT_CANCELLABLE").counter().count()).isEqualTo(1);
    }

    @Test
    void successIsTimedWithoutAnErrorCount() {
        SchedulingService target = mock(SchedulingService.class);
        SchedulingRepository repo = mock(SchedulingRepository.class);
        when(repo.sessionCenterCode(163)).thenReturn(Optional.of("SFO01"));
        when(target.reschedule(5, 163)).thenReturn(new SchedulingService.BookingOutcome(9, null, 1, 163));
        SimpleMeterRegistry reg = new SimpleMeterRegistry();

        SchedulingService service = instrumented(target, repo, reg);
        service.reschedule(5, 163);
        service.reschedule(5, 163);

        assertThat(reg.get(BookingMetrics.DURATION).tags("operation", "reschedule", "outcome", "success",
                "centerCode", "SFO01").timer().count()).isEqualTo(2);
        assertThat(reg.find(BookingMetrics.ERRORS).counters()).isEmpty();
        org.mockito.Mockito.verify(repo, org.mockito.Mockito.times(1)).sessionCenterCode(163);
    }

    @Test
    void schemaIndicatorIsDownUntilFlywayFinishedAndWhilePending() {
        SchedulingSchemaHealthIndicator schema = new SchedulingSchemaHealthIndicator();
        assertThat(schema.health().getStatus()).isEqualTo(Status.DOWN);

        Flyway flyway = mock(Flyway.class);
        MigrationInfoService info = mock(MigrationInfoService.class);
        MigrationInfo current = mock(MigrationInfo.class);
        when(current.getVersion()).thenReturn(MigrationVersion.fromVersion("1"));
        when(flyway.info()).thenReturn(info);
        when(info.current()).thenReturn(current);
        when(info.pending()).thenReturn(new MigrationInfo[] {mock(MigrationInfo.class)});
        schema.migrate(flyway);
        assertThat(schema.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(schema.health().getDetails()).containsEntry("pending", 1);

        when(info.pending()).thenReturn(new MigrationInfo[0]);
        schema.migrate(flyway);
        assertThat(schema.health().getStatus()).isEqualTo(Status.UP);
        assertThat(schema.health().getDetails()).containsEntry("version", "1");
    }

    @Test
    void legacyIndicatorFollowsPing() {
        LegacyReferenceSource ok = mock(LegacyReferenceSource.class);
        assertThat(new LegacySourceHealthIndicator(ok).health().getStatus()).isEqualTo(Status.UP);
        LegacyReferenceSource down = mock(LegacyReferenceSource.class);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("refused")).when(down).ping();
        assertThat(new LegacySourceHealthIndicator(down).health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void requestPathsPopulateMdc() {
        RequestLoggingFilter.putFromPath("/api/v2/sessions/154/capacity");
        assertThat(MDC.get("sessionId")).isEqualTo("154");
        RequestLoggingFilter.clearMdc();
        RequestLoggingFilter.putFromPath("/api/v1/bookings/12/reschedule");
        assertThat(MDC.get("bookingId")).isEqualTo("12");
        RequestLoggingFilter.putFromPath("/api/v2/centers/SFO01/sessions");
        assertThat(MDC.get("centerCode")).isEqualTo("SFO01");
        RequestLoggingFilter.putFromPath("/api/v2/candidates/NW00000920/bookings");
        assertThat(MDC.get("candidateNumber")).isEqualTo("NW00000920");
        RequestLoggingFilter.clearMdc();
        RequestLoggingFilter.putFromPath("/api/v2/sessionsX/1");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
