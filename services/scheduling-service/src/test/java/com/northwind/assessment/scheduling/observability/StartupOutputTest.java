package com.northwind.assessment.scheduling.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** With the production application.yml and logback-spring.xml, everything the application writes to stdout is JSON. */
@ExtendWith(OutputCaptureExtension.class)
class StartupOutputTest {

    @Configuration(proxyBeanMethods = false)
    static class NoBeans {
    }

    @Test
    void startupStdoutIsJsonLinesOnly(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE).run()) {
            LoggerFactory.getLogger(StartupOutputTest.class).info("started");
        }

        List<String> lines = output.getOut().lines().filter(l -> !l.isBlank()).toList();
        assertThat(lines).anyMatch(l -> l.contains("\"message\":\"started\""));
        ObjectMapper json = new ObjectMapper();
        for (String line : lines) {
            assertThat(line).as("stdout line").startsWith("{");
            assertThat(json.readTree(line).isObject()).as(line).isTrue();
        }
    }
}
