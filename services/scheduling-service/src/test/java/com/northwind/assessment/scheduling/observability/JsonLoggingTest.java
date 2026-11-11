package com.northwind.assessment.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The production JSON appender (logback-json-console.xml) emits the trace/booking context and nothing else from MDC. */
class JsonLoggingTest {

    private static final String CONFIG = """
            <configuration>
              <include resource="logback-json-console.xml"/>
              <root level="INFO"><appender-ref ref="JSON_CONSOLE"/></root>
            </configuration>
            """;

    @SuppressWarnings("unchecked")
    private static JsonNode encode(Map<String, String> mdc, String message) throws Exception {
        LoggerContext ctx = new LoggerContext();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(ctx);
        configurator.doConfigure(new ByteArrayInputStream(CONFIG.getBytes(StandardCharsets.UTF_8)));
        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        OutputStreamAppender<ILoggingEvent> appender = (OutputStreamAppender<ILoggingEvent>) root.getAppender("JSON_CONSOLE");
        assertThat(appender).as("JSON_CONSOLE appender").isNotNull();

        LoggingEvent event = new LoggingEvent(JsonLoggingTest.class.getName(), ctx.getLogger("test"), Level.INFO, message,
                null, null);
        event.setMDCPropertyMap(mdc);
        byte[] json = appender.getEncoder().encode(event);
        ctx.stop();
        return new ObjectMapper().readTree(json);
    }

    @Test
    void contextKeysAreTopLevelJsonFields() throws Exception {
        JsonNode line = encode(Map.of("traceId", "4bf92f3577b34da6a3ce929d0e0e4736", "centerCode", "BOS01",
                "sessionId", "1001", "bookingId", "50000001", "candidateNumber", "C000123"), "booking created");

        assertThat(line.path("message").asText()).isEqualTo("booking created");
        assertThat(line.path("level").asText()).isEqualTo("INFO");
        assertThat(line.path("service").asText()).isEqualTo("scheduling-service");
        assertThat(line.has("@timestamp")).isTrue();
        assertThat(line.path("traceId").asText()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(line.path("centerCode").asText()).isEqualTo("BOS01");
        assertThat(line.path("sessionId").asText()).isEqualTo("1001");
        assertThat(line.path("bookingId").asText()).isEqualTo("50000001");
        assertThat(line.path("candidateNumber").asText()).isEqualTo("C000123");
    }

    @Test
    void mdcKeysOutsideTheAllowListAreDropped() throws Exception {
        JsonNode line = encode(Map.of("traceId", "t1", "candidateName", "Alex Example", "email", "alex@example.test",
                "dateOfBirth", "1990-01-01"), "x");

        assertThat(line.path("traceId").asText()).isEqualTo("t1");
        assertThat(line.has("candidateName")).isFalse();
        assertThat(line.has("email")).isFalse();
        assertThat(line.has("dateOfBirth")).isFalse();
        assertThat(line.toString()).doesNotContain("Alex Example", "alex@example.test", "1990-01-01");
    }

    @Test
    void logContext_putsOnlyTheAllowedKeys() {
        org.slf4j.MDC.clear();
        LogContext.centerCode(" SEA01 ");
        LogContext.sessionId(7);
        LogContext.bookingId(50000002L);
        LogContext.candidateNumber("C1");
        assertThat(org.slf4j.MDC.getCopyOfContextMap()).containsOnlyKeys(LogContext.OPERATION_KEYS.toArray(String[]::new))
                .containsEntry("centerCode", "SEA01");
        assertThat(LogContext.centerCodeOrUnknown()).isEqualTo("SEA01");
        LogContext.centerCode(" ");
        assertThat(LogContext.centerCodeOrUnknown()).isEqualTo("unknown");
        LogContext.sessionId(null);
        LogContext.clearAll();
        assertThat(org.slf4j.MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
