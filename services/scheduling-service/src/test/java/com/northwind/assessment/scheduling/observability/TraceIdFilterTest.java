package com.northwind.assessment.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdFilterTest {

    private static final String TRACE = "4bf92f3577b34da6a3ce929d0e0e4736";
    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private record Seen(String traceId, String centerCode, MockHttpServletResponse response) {
    }

    private Seen run(MockHttpServletRequest request) throws Exception {
        AtomicReference<String> trace = new AtomicReference<>();
        AtomicReference<String> center = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                trace.set(MDC.get(LogContext.TRACE_ID));
                center.set(MDC.get(LogContext.CENTER_CODE));
                LogContext.centerCode("BOS01");
                LogContext.bookingId(50000001L);
            }
        });
        return new Seen(trace.get(), center.get(), response);
    }

    @Test
    void w3cTraceparent_isPropagated() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v2/bookings");
        req.addHeader("traceparent", "00-" + TRACE + "-00f067aa0ba902b7-01");

        Seen s = run(req);

        assertThat(s.traceId()).isEqualTo(TRACE);
        assertThat(s.response().getHeader("X-Request-Id")).isEqualTo(TRACE);
    }

    @Test
    void requestId_isUsedAndEchoedWhenNoTraceparent() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/centers");
        req.addHeader("X-Request-Id", "gw-123.abc");

        Seen s = run(req);

        assertThat(s.traceId()).isEqualTo("gw-123.abc");
        assertThat(s.response().getHeader("X-Request-Id")).isEqualTo("gw-123.abc");
    }

    @Test
    void bothHeaders_traceparentWinsAndCallerRequestIdIsEchoed() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/centers");
        req.addHeader("traceparent", "00-" + TRACE + "-00f067aa0ba902b7-01");
        req.addHeader("X-Request-Id", "gw-1");

        Seen s = run(req);

        assertThat(s.traceId()).isEqualTo(TRACE);
        assertThat(s.response().getHeader("X-Request-Id")).isEqualTo("gw-1");
    }

    @Test
    void missingOrInvalidHeaders_generateATraceId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v2/centers");
        req.addHeader("traceparent", "00-00000000000000000000000000000000-00f067aa0ba902b7-01");
        req.addHeader("X-Request-Id", "bad id\nwith newline");

        Seen s = run(req);

        assertThat(s.traceId()).matches("[0-9a-f]{32}").isNotEqualTo("00000000000000000000000000000000");
        assertThat(s.response().getHeader("X-Request-Id")).isEqualTo(s.traceId());
        assertThat(run(new MockHttpServletRequest("GET", "/x")).traceId()).isNotEqualTo(s.traceId());
    }

    @Test
    void mdcIsClearedAfterTheRequest_andStaleKeysDoNotLeakIn() throws Exception {
        MDC.put(LogContext.CENTER_CODE, "STALE");
        MDC.put(LogContext.TRACE_ID, "stale-trace");

        Seen s = run(new MockHttpServletRequest("GET", "/api/v2/centers"));

        assertThat(s.centerCode()).isNull();
        assertThat(s.traceId()).isNotEqualTo("stale-trace");
        assertThat(MDC.get(LogContext.TRACE_ID)).isNull();
        assertThat(MDC.get(LogContext.CENTER_CODE)).isNull();
        assertThat(MDC.get(LogContext.BOOKING_ID)).isNull();
    }

    @Test
    void completionLine_recordsEchoedRequestId_and500WhenTheChainThrows() {
        Logger logger = (Logger) LoggerFactory.getLogger(TraceIdFilter.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v2/bookings");
            req.addHeader("traceparent", "00-" + TRACE + "-00f067aa0ba902b7-01");
            req.addHeader("X-Request-Id", "gw-9");
            assertThatThrownBy(() -> filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
                    throw new IllegalStateException("boom");
                }
            })).isInstanceOf(IllegalStateException.class);
            assertThat(events.list).hasSize(1);
            ILoggingEvent line = events.list.get(0);
            assertThat(line.getMDCPropertyMap()).containsEntry(LogContext.TRACE_ID, TRACE);
            assertThat(Arrays.stream(line.getArgumentArray()).map(String::valueOf))
                    .contains("status=500", "requestId=gw-9");
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        } finally {
            logger.detachAppender(events);
        }
    }

    @Test
    void traceIdParsing() {
        assertThat(TraceIdFilter.traceId("00-" + TRACE.toUpperCase() + "-00F067AA0BA902B7-01", null)).isEqualTo(TRACE);
        assertThat(TraceIdFilter.traceId("ff-" + TRACE + "-00f067aa0ba902b7-01", "r1")).isEqualTo("r1");
        assertThat(TraceIdFilter.traceId("garbage", "r1")).isEqualTo("r1");
        assertThat(TraceIdFilter.safeRequestId(" ok-1 ")).isEqualTo("ok-1");
        assertThat(TraceIdFilter.safeRequestId("x".repeat(65))).isNull();
        assertThat(TraceIdFilter.safeRequestId(null)).isNull();
    }
}
